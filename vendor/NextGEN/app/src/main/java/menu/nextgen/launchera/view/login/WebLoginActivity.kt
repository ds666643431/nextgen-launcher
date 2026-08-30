package menu.nextgen.launchera.view.login

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import menu.nextgen.launcher.core.env.BEnvironment
import menu.nextgen.launchera.R
import menu.nextgen.launchera.util.FacebookCookies
import androidx.appcompat.app.AppCompatActivity
import java.io.File

/**
 * Host-side WebView used for Facebook sign-in, in two modes.
 *
 * CONNECT mode ([EXTRA_FB_CONNECT]): the user logs into m.facebook.com so the host cookie
 * jar holds a session. There is no token to capture -- we watch for the c_user cookie and
 * close once it appears.
 *
 * OAUTH mode ([EXTRA_PKG] set): a guest app tried to open a Facebook authorize URL in a
 * browser. We load that URL here instead, where the cookies from CONNECT mode already live,
 * so Facebook can auto-approve. When it redirects to the app's custom scheme we hand the
 * whole redirect URI back to the guest -- its own Facebook SDK does the token exchange. We
 * never parse or store a token, and never need the app's client id.
 */
class WebLoginActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "WebLoginActivity"

        const val EXTRA_URL = "oni_url"
        const val EXTRA_FB_CONNECT = "oni_fb_connect"
        const val EXTRA_PKG = "oni_pkg"
        const val EXTRA_USER = "oni_user"

        const val FB_LOGIN_URL = "https://m.facebook.com/login.php"

        /** Where the guest polls for the captured redirect. */
        const val ONI_DIR = "__oni"
        const val REDIRECT_FILE = "fb_redirect"

        /** Facebook returns to fbconnect:// or fb<appid>://, so match the shape, not an id. */
        private val FB_SCHEME = Regex("fb\\d+")

        /** Pages where the session is not yet final -- don't close on a partial cookie. */
        private val INTERSTITIALS = listOf("checkpoint", "two_factor", "recover", "login/device")

        /** Hides the "Get Facebook" app-install banners that push users off the web flow. */
        private const val HIDE_BANNER_JS = """
            (function(){try{
              var M=['Get Facebook','Download Facebook'];
              var cap=0.6*innerWidth*innerHeight;
              var all=document.querySelectorAll('body *');
              for(var i=0;i<all.length;i++){
                var e=all[i];
                if(e.children.length)continue;
                var t=e.textContent||'',hit=false;
                for(var m=0;m<M.length;m++)if(t.indexOf(M[m])>=0){hit=true;break;}
                if(!hit)continue;
                for(var k=0;k<6&&e.parentElement;k++){
                  var p=e.parentElement;
                  if(p.querySelector('input,form'))break;
                  e=p;
                }
                if(e.querySelector('input,form'))continue;
                var r=e.getBoundingClientRect();
                if(r.width*r.height<=cap)e.style.display='none';
              }
            }catch(_){}})();
        """
    }

    private lateinit var webView: WebView

    private var fbConnect = false
    private var guestPkg: String? = null
    private var guestUser = 0
    private var done = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web_login)

        webView = findViewById(R.id.webView)

        fbConnect = intent.getBooleanExtra(EXTRA_FB_CONNECT, false)
        guestPkg = intent.getStringExtra(EXTRA_PKG)
        guestUser = intent.getIntExtra(EXTRA_USER, 0)
        val url = intent.getStringExtra(EXTRA_URL) ?: FB_LOGIN_URL

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            // Facebook serves a degraded page to WebViews. Drop the "; wv" marker and the
            // Version/x.y token so the UA reads as ordinary mobile Chrome.
            userAgentString = userAgentString
                .replace("; wv)", ")")
                .replace(Regex(" Version/\\d+\\.\\d+"), "")
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return handleRedirect(url)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                if (fbConnect && !done && FacebookCookies.isSignedIn() && !isInterstitial(url)) {
                    finishConnect()
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                view?.evaluateJavascript(HIDE_BANNER_JS, null)
            }
        }

        webView.loadUrl(url)
    }

    private fun isInterstitial(url: String?): Boolean {
        val u = url ?: return false
        return INTERSTITIALS.any { u.contains(it, ignoreCase = true) }
    }

    private fun finishConnect() {
        done = true
        CookieManager.getInstance().flush()
        Toast.makeText(this, R.string.facebook_login_success, Toast.LENGTH_SHORT).show()
        setResult(RESULT_OK)
        finish()
    }

    /**
     * @return true when we consumed the URL (a custom-scheme OAuth redirect), false to let
     *         the WebView keep loading normally.
     */
    private fun handleRedirect(url: String?): Boolean {
        val u = url ?: return false
        val scheme = u.substringBefore("://", "").lowercase()
        if (scheme == "http" || scheme == "https" || scheme.isEmpty()) return false

        val isFb = scheme == "fbconnect" || FB_SCHEME.matches(scheme)
        if (!isFb) return false

        val pkg = guestPkg
        if (pkg == null) {
            // Connect mode has no guest to hand off to; nothing to do but stop.
            finish()
            return true
        }

        return try {
            publishRedirect(pkg, u)
            done = true
            finish()
            true
        } catch (t: Throwable) {
            Log.e(TAG, "could not hand the redirect back to $pkg", t)
            false
        }
    }

    /**
     * Write the redirect where the guest's poller will find it. Written to a temp name and
     * renamed so the guest can never read a half-written URL.
     */
    private fun publishRedirect(pkg: String, url: String) {
        val dir = File(BEnvironment.getDataFilesDir(pkg, guestUser), ONI_DIR)
        if (!dir.exists()) dir.mkdirs()
        val tmp = File(dir, "$REDIRECT_FILE.tmp")
        tmp.writeText(url, Charsets.UTF_8)
        tmp.renameTo(File(dir, REDIRECT_FILE))
        Log.i(TAG, "published facebook redirect for $pkg")
    }

    override fun onDestroy() {
        runCatching { webView.destroy() }
        super.onDestroy()
    }
}

package menu.nextgen.launchera.view.login

import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import android.widget.Toast
import menu.nextgen.launchera.R
import menu.nextgen.launchera.util.GoogleAuthClient
import menu.nextgen.launchera.util.VirtualGoogleAccount
import androidx.appcompat.app.AppCompatActivity

/**
 * Signs a Google account into the CONTAINER (not the host device).
 *
 * Uses Google's EmbeddedSetup page -- the flow a real device runs during setup. The
 * "MinuteMaid" suffix on the user agent is what makes accounts.google.com serve that page
 * instead of the ordinary web sign-in; without it the oauth_token cookie is never issued.
 *
 * When sign-in completes the page drops an "oauth_token=oauth2_4/..." cookie. We poll for
 * it, trade it for a master token, and write the result into the virtual AccountManager.
 */
class GoogleLoginActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "GoogleLoginActivity"

        private const val SETUP_URL =
            "https://accounts.google.com/EmbeddedSetup?source=android" +
                "&xoauth_display_name=Android+Device&lang=en&langCountry=en_us" +
                "&hl=en-US&tmpl=new_account"

        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/125.0.0.0 Mobile Safari/537.36 MinuteMaid"

        private const val COOKIE_HOST = "https://accounts.google.com"
        private const val COOKIE_KEY = "oauth_token="
        private const val TOKEN_PREFIX = "oauth2_4/"

        /** Re-check the cookie jar this often after each page settles. */
        private const val POLL_INTERVAL_MS = 700L

        /** Keep the page hidden until Google has redirected past its interstitials. */
        private const val REVEAL_DELAY_MS = 9000L
    }

    private lateinit var webView: WebView
    private lateinit var loading: View
    private lateinit var loadingText: TextView

    /** Set once the token is captured, so polling stops and the WebView stays hidden. */
    @Volatile
    private var captured = false

    /** Bumped per navigation; used to detect when loading has settled. */
    private var pageCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_google_login)

        webView = findViewById(R.id.webView)
        loading = findViewById(R.id.loading)
        loadingText = findViewById(R.id.loadingText)
        loadingText.setText(R.string.google_login_loading)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
            userAgentString = USER_AGENT
        }

        // Start from a clean jar: a stale oauth_token from a previous run would be picked
        // up immediately and sign in the wrong account.
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
            removeAllCookies(null)
            flush()
        }
        runCatching { WebStorage.getInstance().deleteAllData() }
        webView.clearCache(true)
        webView.clearHistory()

        findViewById<View>(R.id.loginClose).setOnClickListener { finish() }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                pageCount++
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                checkForToken()
                // Reveal only once loading has settled: if no further navigation happened
                // in this window, the user is looking at a page worth interacting with.
                val seen = pageCount
                webView.postDelayed({
                    if (!captured && pageCount == seen) revealWebView()
                }, POLL_INTERVAL_MS)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                // Google sets the cookie on a redirect that may never "finish" a page.
                checkForToken()
                return false
            }
        }

        // Hard fallback in case navigation never settles.
        webView.postDelayed({ revealWebView() }, REVEAL_DELAY_MS)
        webView.loadUrl(SETUP_URL)
    }

    private fun revealWebView() {
        if (captured || webView.visibility == View.VISIBLE) return
        webView.visibility = View.VISIBLE
        loading.visibility = View.GONE
    }

    /** Look for the oauth_token cookie; when present, hand off to the exchange. */
    private fun checkForToken() {
        if (captured) return
        val cookies = CookieManager.getInstance().getCookie(COOKIE_HOST) ?: return
        val entry = cookies.split(";")
            .map { it.trim() }
            .firstOrNull { it.startsWith(COOKIE_KEY) } ?: return

        val token = entry.substringAfter(COOKIE_KEY)
        // Google sets oauth_token early with a placeholder; only the oauth2_4/ form is the
        // master-token grant we can exchange.
        if (!token.startsWith(TOKEN_PREFIX)) return

        captured = true
        webView.visibility = View.INVISIBLE
        loading.visibility = View.VISIBLE
        loadingText.setText(R.string.google_login_finishing)

        Thread({ completeLogin(token) }, "google-login-exchange").start()
    }

    private fun completeLogin(oauthToken: String) {
        try {
            val info = GoogleAuthClient.exchangeToken(oauthToken)
            VirtualGoogleAccount.write(info)
            runOnUiThread {
                Toast.makeText(
                    this,
                    getString(R.string.google_login_success, info.email),
                    Toast.LENGTH_SHORT
                ).show()
                setResult(RESULT_OK)
                finish()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "google login failed", t)
            runOnUiThread { showError(t.message ?: "?") }
        }
    }

    private fun showError(message: String) {
        Toast.makeText(
            this,
            getString(R.string.google_login_failed, message),
            Toast.LENGTH_LONG
        ).show()
        finish()
    }

    override fun onDestroy() {
        runCatching { webView.destroy() }
        super.onDestroy()
    }
}

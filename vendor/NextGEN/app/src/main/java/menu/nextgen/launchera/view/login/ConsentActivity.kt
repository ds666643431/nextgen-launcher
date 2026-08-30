package menu.nextgen.launchera.view.login

import android.os.Bundle
import android.os.Message
import android.os.Messenger
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import menu.nextgen.launchera.R
import androidx.appcompat.app.AppCompatActivity

/**
 * Hosts Google's OAuth consent page on microG's behalf.
 *
 * When a game asks for a Play Games token for the first time, Google refuses with
 * issueAdvice=consent -- the account has never approved that game's scopes. microG then
 * wants to show a consent page and post the result back. It normally launches its own
 * ConsentSignInActivity inside the container; ActivityManagerCommonProxy.redirectConsent
 * sends it here instead, with the same extras.
 *
 * The page itself is Google's. It calls back into the JS interface named OAuthConsent,
 * whose contract microG defines: setConsentResult(result), cancel(), showView(),
 * getModuleVersion(). We relay the result over the Messenger the caller supplied.
 */
class ConsentActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ConsentActivity"

        // Names fixed by microG's ConsentSignInActivityKt -- do not change.
        private const val EXTRA_CONSENT_URL = "consentUrl"
        private const val EXTRA_MESSENGER = "messenger"
        private const val EXTRA_COOKIE_PREFIX = "cookie-"
        private const val KEY_CONSENT_RESULT = "consent_result"

        private const val JS_INTERFACE = "OAuthConsent"

        /** Google's "no consent given" sentinel; treated as a cancel, not a result. */
        private const val RESULT_NONE = "CAA"
    }

    private lateinit var webView: WebView
    private lateinit var loading: View

    private var messenger: Messenger? = null

    /** Guards against sending twice, and lets onDestroy report an abandoned flow. */
    private var replied = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_consent)

        webView = findViewById(R.id.webView)
        loading = findViewById(R.id.loading)

        val consentUrl = intent?.getStringExtra(EXTRA_CONSENT_URL)
        messenger = runCatching {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra<Messenger>(EXTRA_MESSENGER)
        }.getOrNull()

        if (consentUrl.isNullOrEmpty() || messenger == null) {
            Log.w(TAG, "missing consentUrl or messenger; nothing to do")
            finish()
            return
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
        }
        webView.addJavascriptInterface(ConsentBridge(), JS_INTERFACE)
        webView.webViewClient = WebViewClient()

        // The caller hands us the session cookies as cookie-0, cookie-1, ... Without them
        // the consent page has no signed-in user and cannot grant anything.
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)
        intent.extras?.let { extras ->
            for (i in 0 until extras.size()) {
                extras.getString(EXTRA_COOKIE_PREFIX + i)?.let {
                    cookieManager.setCookie(consentUrl, it)
                }
            }
        }
        cookieManager.flush()

        webView.loadUrl(consentUrl)
    }

    /** Post the outcome back to microG, which then retries the token request. */
    private fun sendResult(result: String?) {
        if (replied) return
        try {
            val message = Message.obtain()
            message.data = Bundle().apply { putString(KEY_CONSENT_RESULT, result) }
            messenger?.send(message)
            replied = true
            Log.d(TAG, "sent consent result: ${result ?: "none"}")
        } catch (t: Throwable) {
            Log.w(TAG, "could not send consent result", t)
        }
    }

    override fun onDestroy() {
        // microG waits on the Messenger; a silent close would hang the sign-in forever.
        if (!replied) sendResult(null)
        runCatching { webView.destroy() }
        super.onDestroy()
    }

    private inner class ConsentBridge {

        @JavascriptInterface
        fun setConsentResult(result: String) {
            // "CAA" means the user granted nothing; leave it to be reported as a cancel.
            if (result != RESULT_NONE) {
                sendResult(result)
            }
            runOnUiThread { finish() }
        }

        @JavascriptInterface
        fun cancel() {
            runOnUiThread { finish() }
        }

        @JavascriptInterface
        fun showView() {
            runOnUiThread {
                loading.visibility = View.GONE
                webView.visibility = View.VISIBLE
            }
        }

        @JavascriptInterface
        fun getModuleVersion() {
            // Present only because the page calls it; microG's own stub is a no-op too.
        }
    }
}

package menu.nextgen.launchera.util

import android.webkit.CookieManager

/**
 * Facebook sign-in has no token to store -- being "signed in" just means the host WebView's
 * cookie jar holds a live Facebook session. Guest apps are pushed through that same jar, so
 * their OAuth authorize pages auto-approve without a fresh login.
 */
object FacebookCookies {

    private val DOMAINS = listOf(
        "https://m.facebook.com",
        "https://www.facebook.com",
        "https://facebook.com"
    )

    /** Set once a Facebook session exists; its presence is our "signed in" test. */
    private const val SESSION_COOKIE = "c_user="

    fun isSignedIn(): Boolean {
        val manager = CookieManager.getInstance()
        for (domain in DOMAINS) {
            val cookies = manager.getCookie(domain) ?: continue
            val hit = cookies.split(";")
                .map { it.trim() }
                .firstOrNull { it.startsWith(SESSION_COOKIE) }
            if (hit != null && hit.substringAfter(SESSION_COOKIE).isNotEmpty()) {
                return true
            }
        }
        return false
    }

    /**
     * Sign out. There is no per-domain cookie API, so this clears the whole jar -- which is
     * also what we want, since a stale Google oauth_token would otherwise linger too.
     */
    fun signOut() {
        CookieManager.getInstance().apply {
            removeAllCookies(null)
            flush()
        }
    }
}

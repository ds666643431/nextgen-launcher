package menu.nextgen.launchera.util

import android.os.Build
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Talks to Google's Android auth endpoint the way GMS does.
 *
 * The WebView login gives us a master token (the "oauth2_4/..." cookie value). That token
 * on its own is not enough to seed an account: microG wants the durable master token that
 * android.clients.google.com hands back, plus the profile fields it stores as user data.
 * This exchanges the former for the latter.
 *
 * callerSig is Google's release certificate SHA-1. The endpoint checks the caller package
 * and signature pair, so we present ourselves as com.google.android.gms -- the same value
 * IPackageManagerProxy serves for com.android.vending.
 */
object GoogleAuthClient {

    private const val TAG = "GoogleAuthClient"

    private const val AUTH_URL = "https://android.clients.google.com/auth"
    private const val USERINFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo"

    private const val GMS_PKG = "com.google.android.gms"
    private const val GOOGLE_SIG_SHA1 = "38918a453d07199354f8b19af05ec6562ced5788"

    /** Mirrors the microG build we bundle (0.3.15.250932). */
    private const val GMS_VERSION = 250932030

    private const val TIMEOUT_MS = 20000

    /** POST a form to the auth endpoint and return the raw response body. */
    private fun post(params: Map<String, String>): String {
        val body = params.entries.joinToString("&") {
            URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8")
        }
        val conn = URL(AUTH_URL).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.setRequestProperty("User-Agent", "GoogleAuth/1.4 (${Build.DEVICE} ${Build.ID})")
            conn.setRequestProperty("device", "0")
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            // The endpoint reports failures as a 4xx with a parseable Error= body, so read
            // the error stream rather than throwing -- the caller wants that message.
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            return stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
        } finally {
            conn.disconnect()
        }
    }

    /** The endpoint answers in "key=value" lines. Values may themselves contain '='. */
    private fun parse(response: String): Map<String, String> {
        val out = HashMap<String, String>()
        for (line in response.split("\n")) {
            val idx = line.indexOf('=')
            if (idx > 0) {
                out[line.substring(0, idx)] = line.substring(idx + 1)
            }
        }
        return out
    }

    /**
     * Exchange the WebView's oauth2_4 token for a master token plus profile fields.
     * Throws IllegalStateException with Google's own error text when it refuses.
     */
    fun exchangeToken(oauthToken: String): GoogleAccountInfo {
        val response = post(
            linkedMapOf(
                "androidId" to "0",
                "lang" to "en",
                "google_play_services_version" to GMS_VERSION.toString(),
                "sdk_version" to Build.VERSION.SDK_INT.toString(),
                "device_country" to "us",
                "operatorCountry" to "us",
                "service" to "ac2dm",
                "get_accountid" to "1",
                "ACCESS_TOKEN" to "1",
                "callerPkg" to GMS_PKG,
                "callerSig" to GOOGLE_SIG_SHA1,
                "add_account" to "1",
                "droidguard_results" to "null",
                "Token" to oauthToken,
                "source" to "android",
                "accountType" to "HOSTED_OR_GOOGLE",
                "has_permission" to "1"
            )
        )
        val fields = parse(response)

        val masterToken = fields["Token"]
            ?: throw IllegalStateException(
                "no master token in response: " + (fields["Error"] ?: response.take(200))
            )
        val email = fields["Email"] ?: ""

        var googleUserId = fields["GoogleUserId"]
        if (googleUserId.isNullOrEmpty() && masterToken.isNotEmpty() && email.isNotEmpty()) {
            googleUserId = runCatching { fetchGoogleUserId(masterToken, email) }.getOrNull()
        }

        return GoogleAccountInfo(
            email = email,
            masterToken = masterToken,
            googleUserId = googleUserId,
            services = fields["services"],
            firstName = fields["firstName"],
            lastName = fields["lastName"]
        )
    }

    /**
     * Some accounts come back without GoogleUserId. Mint an OpenID access token and read
     * the numeric subject from the userinfo endpoint instead.
     */
    private fun fetchGoogleUserId(masterToken: String, email: String): String? {
        val fields = parse(
            post(
                linkedMapOf(
                    "androidId" to "0",
                    "lang" to "en",
                    "google_play_services_version" to GMS_VERSION.toString(),
                    "sdk_version" to Build.VERSION.SDK_INT.toString(),
                    "device_country" to "us",
                    "service" to "oauth2:openid email profile",
                    "app" to GMS_PKG,
                    "client_sig" to GOOGLE_SIG_SHA1,
                    "callerPkg" to GMS_PKG,
                    "callerSig" to GOOGLE_SIG_SHA1,
                    "Email" to email,
                    "Token" to masterToken,
                    "get_accountid" to "1",
                    "has_permission" to "1"
                )
            )
        )

        fields["GoogleUserId"]?.takeIf { it.isNotEmpty() }?.let { return it }

        val accessToken = fields["Auth"]?.takeIf { it.isNotEmpty() } ?: return null
        val conn = URL(USERINFO_URL).openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.setRequestProperty("Authorization", "Bearer $accessToken")
            val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            JSONObject(text).optString("sub").takeIf { it.isNotEmpty() }
        } catch (t: Throwable) {
            Log.w(TAG, "userinfo lookup failed: ${t.message}")
            null
        } finally {
            conn.disconnect()
        }
    }
}

/** What the auth exchange yields, mapped onto the user-data keys GMS stores. */
data class GoogleAccountInfo(
    val email: String,
    val masterToken: String,
    val googleUserId: String?,
    val services: String?,
    val firstName: String?,
    val lastName: String?
)

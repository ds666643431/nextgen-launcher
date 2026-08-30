package menu.nextgen.launchera.util

import android.accounts.Account
import android.os.Bundle
import android.util.Log
import menu.nextgen.launcher.fake.frameworks.BAccountManager

/**
 * Writes a Google account into the CONTAINER's AccountManager (BAccountManagerService),
 * which persists it to appcore/system/accounts.conf. microG reads it from there.
 *
 * The host device's own Google accounts cannot be reused: their authenticator is
 * com.google.android.gms at a different uid, so getPassword() -- where the master token
 * lives -- is refused, and the account is not even visible to us. Hence the WebView login.
 */
object VirtualGoogleAccount {

    private const val TAG = "VirtualGoogleAccount"

    const val TYPE_GOOGLE = "com.google"

    private const val GMS_PKG = "com.google.android.gms"
    private const val VENDING_PKG = "com.android.vending"

    /** AccountManager.VISIBILITY_* are @hide on some levels, so use the literals. */
    private const val VISIBILITY_USER_MANAGED_VISIBLE = 2
    private const val VISIBILITY_VISIBLE = 1
    private const val KEY_LEGACY_VISIBLE = "android:accounts:key_legacy_visible"

    private const val MAX_ATTEMPTS = 5
    private const val RETRY_DELAY_MS = 300L

    /**
     * Add (or refresh) the account. Retries because the virtual AccountManager service may
     * still be binding when the login finishes.
     *
     * @throws IllegalStateException if the account could not be written.
     */
    fun write(info: GoogleAccountInfo) {
        val account = Account(info.email, TYPE_GOOGLE)
        val manager = BAccountManager.get()
        var last: Throwable? = null

        for (attempt in 1..MAX_ATTEMPTS) {
            try {
                if (!manager.addAccountExplicitly(account, info.masterToken, Bundle())) {
                    // Already present is fine -- fall through and refresh its data. Anything
                    // else means the service is not ready yet, so back off and retry.
                    val existing = runCatching { manager.getAccountsAsUser(account.type) }.getOrNull()
                    val present = existing?.any { it.name == account.name } == true
                    if (!present) {
                        Thread.sleep(RETRY_DELAY_MS)
                        continue
                    }
                }

                manager.setPassword(account, info.masterToken)

                // Without these, microG and the Play Store client filter the account out of
                // getAccountsByType and behave as if nobody is signed in.
                manager.setAccountVisibility(account, KEY_LEGACY_VISIBLE, VISIBILITY_USER_MANAGED_VISIBLE)
                manager.setAccountVisibility(account, GMS_PKG, VISIBILITY_VISIBLE)
                manager.setAccountVisibility(account, VENDING_PKG, VISIBILITY_VISIBLE)

                info.services?.let { manager.setUserData(account, "services", it) }
                info.googleUserId?.let { manager.setUserData(account, "GoogleUserId", it) }
                info.firstName?.let { manager.setUserData(account, "firstName", it) }
                info.lastName?.let { manager.setUserData(account, "lastName", it) }
                manager.setUserData(account, "flags", "1")
                manager.setUserData(account, "masterToken", info.masterToken)

                // Switch-account: now that THIS account is safely written, drop every OTHER
                // Google account left in the container. Pruning AFTER a successful add (never
                // before) means a failed sign-in leaves the previous account intact instead of
                // emptying the container. Without this, addAccountExplicitly just appends the
                // new account beside the old one: getAccountsByType() keeps handing BOTH to
                // microG/Play, and signedInAs() -> firstOrNull() resolves to the STALE first
                // account (persisted to accounts.conf, so it survives an app restart). This one
                // choke point also covers the SWITCH button (which never signs out first) and a
                // prior signOut() that silently failed.
                for (other in accounts()) {
                    if (other.name != account.name) {
                        try {
                            val ok = manager.removeAccountExplicitly(other)
                            Log.i(TAG, "pruned stale account ${other.name} (removed=$ok)")
                        } catch (t: Throwable) {
                            Log.w(TAG, "could not prune ${other.name}: ${t.message}")
                        }
                    }
                }

                Log.i(TAG, "wrote google account ${info.email} into the container (attempt $attempt)")
                return
            } catch (t: Throwable) {
                last = t
                try {
                    Thread.sleep(RETRY_DELAY_MS)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
        }

        throw IllegalStateException(
            "could not add the account to the container" +
                (last?.message?.let { " ($it)" } ?: "")
        )
    }

    /** True when the container already holds at least one Google account. */
    fun hasAccount(): Boolean = accounts().isNotEmpty()

    fun accounts(): List<Account> = try {
        BAccountManager.get().getAccountsAsUser(TYPE_GOOGLE)?.toList() ?: emptyList()
    } catch (t: Throwable) {
        Log.w(TAG, "account lookup failed: ${t.message}")
        emptyList()
    }

    /** Name of the signed-in account, or null when the container has none. */
    fun signedInAs(): String? = accounts().firstOrNull()?.name

    /**
     * Remove every Google account from the container.
     *
     * Needed to switch accounts: Play Games sign-in only works with an account that
     * already has a Play Games profile, so trying a different one has to be possible.
     */
    fun signOut() {
        val manager = BAccountManager.get()
        for (account in accounts()) {
            try {
                val removed = manager.removeAccountExplicitly(account)
                if (removed) {
                    Log.i(TAG, "removed ${account.name} from the container")
                } else {
                    Log.w(TAG, "removeAccountExplicitly returned false for ${account.name}")
                }
            } catch (t: Throwable) {
                Log.w(TAG, "could not remove ${account.name}: ${t.message}")
            }
        }
        // Report the truth: safely() at the VBridge layer swallows throwables, so a partial
        // sign-out must be visible in logs rather than reported as success.
        val leftover = accounts()
        if (leftover.isNotEmpty()) {
            Log.w(TAG, "signOut left ${leftover.size} account(s): ${leftover.joinToString { it.name }}")
        }
    }
}

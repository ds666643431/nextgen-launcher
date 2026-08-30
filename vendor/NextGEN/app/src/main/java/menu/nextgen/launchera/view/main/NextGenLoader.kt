package menu.nextgen.launchera.view.main

import android.app.Application
import android.content.Context
import android.util.Log
import menu.nextgen.launcher.NextGenCore
import menu.nextgen.launcher.app.configuration.AppLifecycleCallback
import menu.nextgen.launcher.app.configuration.ClientConfiguration
import menu.nextgen.launchera.app.App
import menu.nextgen.launchera.biz.cache.AppSharedPreferenceDelegate

/**
 * Headless engine bootstrap. Runs in EVERY process of the host APK -- main, :core and each
 * :pN -- so it must stay free of any UI.
 *
 * The four flags below are the engine-visible half of ClientConfiguration. Their storage is
 * AppSharedPreferenceDelegate, whose prefs-file name is that class's simpleName and whose
 * keys are these property names -- rename either and every existing install silently resets.
 * There is no settings screen any more; the native launcher sets them through VBridge.
 */
class NextGenLoader {

    private var mHideRoot by AppSharedPreferenceDelegate(App.getContext(), false)
    private var mDaemonEnable by AppSharedPreferenceDelegate(App.getContext(), false)
    private var mUseVpnNetwork by AppSharedPreferenceDelegate(App.getContext(), false)
    private var mDisableFlagSecure by AppSharedPreferenceDelegate(App.getContext(), false)

    fun hideRoot(): Boolean = read({ mHideRoot }, false)

    fun invalidHideRoot(hideRoot: Boolean) = write("hideRoot") { mHideRoot = hideRoot }

    fun disableFlagSecure(): Boolean = read({ mDisableFlagSecure }, false)

    fun invalidDisableFlagSecure(disable: Boolean) =
        write("disableFlagSecure") { mDisableFlagSecure = disable }

    fun daemonEnable(): Boolean = read({ mDaemonEnable }, false)

    fun invalidDaemonEnable(enable: Boolean) = write("daemonEnable") { mDaemonEnable = enable }

    fun useVpnNetwork(): Boolean = read({ mUseVpnNetwork }, false)

    fun invalidUseVpnNetwork(enable: Boolean) = write("useVpnNetwork") { mUseVpnNetwork = enable }

    fun getNextGenCore(): NextGenCore = NextGenCore.get()

    /**
     * The engine fans out guest-process lifecycle here. Registration has to happen in
     * attachBaseContext, because the callback list is per-process and the guest hooks fire
     * inside :pN.
     *
     * onStoragePermissionNeeded MUST keep returning false. launchApk cancels the launch when a
     * callback returns true (NextGenCore.java:1098-1123) -- "true" means "the host is handling
     * the prompt", not "the permission is needed". Returning true here kills every launch on
     * API 30+. All-files access is requested by the launcher UI, separately.
     */
    fun addLifecycleCallback() {
        try {
            NextGenCore.get().addAppLifecycleCallback(object : AppLifecycleCallback() {
                override fun beforeCreateApplication(
                    packageName: String?, processName: String?, context: Context?, userId: Int
                ) {
                    Log.d(TAG, "beforeCreateApplication: $packageName / $processName")
                }

                override fun beforeApplicationOnCreate(
                    packageName: String?, processName: String?, application: Application?, userId: Int
                ) {
                    Log.d(TAG, "beforeApplicationOnCreate: $packageName / $processName")
                }

                override fun afterApplicationOnCreate(
                    packageName: String?, processName: String?, application: Application?, userId: Int
                ) {
                    Log.d(TAG, "afterApplicationOnCreate: $packageName / $processName")
                }

                override fun onStoragePermissionNeeded(packageName: String?, userId: Int): Boolean {
                    Log.w(TAG, "Storage permission needed for launching: $packageName")
                    return false
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error adding lifecycle callback: ${e.message}")
        }
    }

    fun attachBaseContext(context: Context) {
        try {
            NextGenCore.get().doAttachBaseContext(context, object : ClientConfiguration() {
                override fun getHostPackageName(): String = try {
                    context.packageName
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting package name: ${e.message}")
                    "unknown"
                }

                override fun isHideRoot(): Boolean = read({ mHideRoot }, false)

                // Daemon ON (REQUIRED). Without the FGS keep-alive, Android's Cached Apps
                // Freezer freezes the backgrounded :core server and KILLS it on the next sync
                // binder call ("Sync transaction while frozen") — verified thrashing on device
                // (:core repeatedly killed at adj ~905), which destabilizes the whole app and
                // leaves the launcher stuck on its splash (egui loses its window under the
                // churn and never rebuilds the wgpu surface). The engine is designed around
                // this daemon; it must stay on. COST: a low-priority persistent FGS
                // notification (appcore_daemon_channel).
                override fun isEnableDaemonService(): Boolean = true

                override fun isUseVpnNetwork(): Boolean = read({ mUseVpnNetwork }, false)

                override fun isDisableFlagSecure(): Boolean = read({ mDisableFlagSecure }, false)

                // Engine default is a hardcoded Telegram chat id, which makes SimpleCrashFix
                // upload a full `logcat -d` plus the device fingerprint to a third-party
                // endpoint on every crash. Empty disables the sender.
                override fun getLogSenderChatId(): String = ""

                // Disabled: the native egui launcher shows its OWN launch modal, so we don't
                // want Bcore's gray "SplashScreen" LauncherActivity. Launch then goes straight
                // to BActivityManager.startActivity. This is safe here because cloneAndLaunch is
                // fired from our resumed foreground MainActivity (and the translucent
                // ShortcutActivity for pinned shortcuts), so the Android 10+ background-activity-
                // start foreground requirement is already met.
                override fun isEnableLauncherActivity(): Boolean = false
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error in attachBaseContext: ${e.message}")
        }
    }

    fun doOnCreate(context: Context) {
        try {
            NextGenCore.get().doCreate()
        } catch (e: Exception) {
            Log.e(TAG, "Error in doOnCreate: ${e.message}")
        }
    }

    private inline fun read(get: () -> Boolean, fallback: Boolean): Boolean = try {
        get()
    } catch (e: Exception) {
        Log.e(TAG, "Error reading flag: ${e.message}")
        fallback
    }

    private inline fun write(name: String, set: () -> Unit) {
        try {
            set()
        } catch (e: Exception) {
            Log.e(TAG, "Error setting $name: ${e.message}")
        }
    }

    companion object {
        val TAG: String = NextGenLoader::class.java.simpleName
    }
}

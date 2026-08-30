package menu.nextgen.launchera.app

import android.content.Context
import android.util.Log
import menu.nextgen.launcher.NextGenCore
import menu.nextgen.launcher.core.GmsCore
import menu.nextgen.launcher.core.env.BEnvironment
import menu.nextgen.launchera.view.main.NextGenLoader


object AppManager {
    private const val TAG = "AppManager"

    /**
     * Install microG Services (GmsCore) alongside the Play Store stand-in. On by default:
     * anything that talks to Play services -- Play Games above all -- needs it. The switch
     * exists only as an escape hatch for genuinely memory-starved devices, where the
     * Companion's FakeLicenseService alone is enough to satisfy a license check.
     */
    const val PREFS_NAME = "nextgenlauncher"
    const val KEY_FULL_MICROG = "force_full_microg"
    const val KEY_MICROG_INSTALLED = "microg_v3"

    /**
     * Turn full microG on or off and reset the one-shot install guard, so the next launch
     * redoes the setup with the new choice.
     */
    fun setFullMicrog(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_FULL_MICROG, enabled)
            .putBoolean(KEY_MICROG_INSTALLED, false)
            .apply()
    }

    fun isFullMicrog(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_FULL_MICROG, true)

    @JvmStatic
    val mNextGenLoader by lazy {
        try {
            NextGenLoader()
        } catch (e: Exception) {
            Log.e(TAG, "Error creating NextGenLoader: ${e.message}")

            NextGenLoader() 
        }
    }

    fun doAttachBaseContext(context: Context) {
        try {
            mNextGenLoader.attachBaseContext(context)
            mNextGenLoader.addLifecycleCallback()
        } catch (e: Exception) {
            Log.e(TAG, "Error in doAttachBaseContext: ${e.message}")
            
        }
    }

    fun doOnCreate(context: Context) {
        try {
            mNextGenLoader.doOnCreate(context)
            initThirdService(context)
        } catch (e: Exception) {
            Log.e(TAG, "Error in doOnCreate: ${e.message}")
            
        }
    }

    private fun initThirdService(context: Context) {
        try {
            // nextgenlauncher: clone Google Play Store + services INTO the container so the
            // guest's Play licensing/services resolve locally and the bind is proxied.
            // MUST run only in the MAIN host process: in the :pN stub/guest processes the
            // PackageManager only sees container packages, so GMS looks "not found" and
            // installGms silently skips everything (the ~5ms no-op we saw).
            val procName = try { android.app.Application.getProcessName() } catch (e: Throwable) { "" }
            if (procName.contains(":")) return  // stub/guest process -> skip
            val prefs = App.getContext().getSharedPreferences("nextgenlauncher", Context.MODE_PRIVATE)
            if (prefs.getBoolean("microg_v3", false)) return
            Thread {
                try {
                    val core = NextGenCore.get()
                    val ctx = App.getContext()
                    // Remove any real-GMS clones so microG's com.android.vending wins the
                    // service resolution (microG FakeStore's LicensingService returns
                    // LICENSED locally — makes PairIP's Play-license check pass in-sandbox).
                    for (p in listOf("com.android.vending", "com.google.android.gms")) {
                        try { if (core.isInstalled(p, 0)) core.uninstallPackageAsUser(p, 0) } catch (_: Throwable) {}
                    }
                    // Emulators used to be skipped here on the theory that stacking our
                    // GmsCore on top of the host's real GMS would push them into the
                    // lowmemorykiller. It does not: MuMu reports ~4.8GB with ~3.4GB free and
                    // runs GmsCore fine, and without it Play Games cannot work at all. So
                    // every device gets the same pair unless the user opts out.
                    val lite = !prefs.getBoolean(KEY_FULL_MICROG, true)
                    val assets = if (lite) listOf("microg_vending.apk")
                                 else listOf("microg_gms.apk", "microg_vending.apk")
                    Log.w(TAG, "nextgenlauncher microG: lite=$lite install $assets")
                    var ok = true
                    for (asset in assets) {
                        try {
                            val f = java.io.File(ctx.cacheDir, asset)
                            if (!f.exists() || f.length() == 0L) {
                                ctx.assets.open(asset).use { i -> f.outputStream().use { o -> i.copyTo(o) } }
                            }
                            Log.w(TAG, "nextgenlauncher microG: installing $asset (${f.length()} bytes)...")
                            val r = core.installPackageAsUser(f, 0)
                            Log.w(TAG, "nextgenlauncher microG: $asset success=${r.success} pkg=${r.packageName} msg=${r.msg}")
                            if (!r.success) ok = false
                        } catch (t: Throwable) {
                            Log.e(TAG, "nextgenlauncher microG: $asset error: ${t.message}"); ok = false
                        }
                    }
                    if (ok) {
                        seedMicrogSettings()
                        prefs.edit().putBoolean("microg_v3", true).apply()
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "nextgenlauncher microG: install failed", t)
                }
            }.start()
        } catch (e: Exception) {
            Log.e(TAG, "Error in initThirdService: ${e.message}")
        }
    }

    /**
     * Turn on microG's device registration and push service.
     *
     * Both default to OFF in microG. Without check-in there is no androidId, so every token
     * request goes out with androidId=0 and Play Games sign-in fails even when the account
     * and master token are perfectly good. microG's SettingsProvider is backed by the app's
     * default SharedPreferences file, which we can seed directly -- the container's data dir
     * belongs to us.
     */
    private fun seedMicrogSettings() {
        try {
            val prefsFile = java.io.File(
                java.io.File(BEnvironment.getDataDir(GmsCore.GMS_PKG, 0), "shared_prefs"),
                "com.google.android.gms_preferences.xml"
            )
            val wanted = listOf(
                // Device registration: without it every token request carries androidId=0
                // and Play Games sign-in is refused.
                "checkin_enable_service",
                "gcm_enable_mcs_service",
                // Let microG approve auth requests itself instead of putting up a consent
                // prompt for every app. This is what makes sign-in automatic once an
                // account exists, which is the behaviour users expect from real GMS.
                "auth_manager_trust_google",
                // Play Games sign-in dies without this. When the Games API answers 404 for
                // players/me -- which it does for any account that has never played this
                // title -- microG checks GameProfileSettings.getAllowCreatePlayer() and,
                // if unset, gives up instead of registering a player. With it on, microG
                // calls registerForGames and then re-requests the token as the Play Games
                // app, which is the identity the Games API can actually resolve.
                "game_allow_create_player",
                "allow_upload_game_played"
            )

            // Preserve anything microG has already written (e.g. device_profile_serial).
            val existing = if (prefsFile.exists()) prefsFile.readText() else null
            val entries = LinkedHashMap<String, String>()
            if (existing != null) {
                Regex("<(string|boolean|int|long|float)\\s+name=\"([^\"]+)\"[^>]*/?>")
                    .findAll(existing)
                    .forEach { m ->
                        val whole = m.value
                        entries[m.groupValues[2]] = whole
                    }
            }
            for (key in wanted) {
                entries[key] = "<boolean name=\"$key\" value=\"true\" />"
            }

            prefsFile.parentFile?.mkdirs()
            prefsFile.writeText(
                buildString {
                    append("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n")
                    append("<map>\n")
                    entries.values.forEach { append("    ").append(it).append("\n") }
                    append("</map>\n")
                }
            )
            Log.w(TAG, "nextgenlauncher microG: enabled check-in + gcm at ${prefsFile.absolutePath}")
        } catch (t: Throwable) {
            Log.e(TAG, "nextgenlauncher microG: could not seed settings: ${t.message}")
        }
    }

}

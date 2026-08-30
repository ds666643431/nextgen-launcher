package menu.nextgen.launchera.view.main

import android.app.Activity
import android.os.Bundle
import android.util.Log
import menu.nextgen.launchera.app.VBridge

/**
 * The clone-and-launch entry point, and -- until the native launcher lands -- also the host's
 * MAIN/LAUNCHER activity, so the app stays startable and the engine still boots.
 *
 * It has no UI on purpose. With a "pkg" extra it clones the guest if needed and launches it;
 * without one it just finishes, having already brought the engine up via App.attachBaseContext.
 *
 * Keep this class at this exact FQCN. Pinned home-screen shortcuts hard-reference
 * menu.nextgen.launchera.view.main.ShortcutActivity, and moving it orphans every one of them.
 */
class ShortcutActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pkg = intent?.getStringExtra(VBridge.EXTRA_PKG)
        val userId = intent?.getIntExtra(VBridge.EXTRA_USER_ID, 0) ?: 0

        if (pkg.isNullOrEmpty()) {
            Log.d(TAG, "no pkg extra -- engine is up, nothing to launch")
            finish()
            return
        }

        // Never on the main thread: these are blocking binder round-trips into :core, and
        // getServiceInternal sleeps a full second whenever that process is still coming up.
        Thread {
            try {
                VBridge.cloneAndLaunch(pkg, userId)
            } catch (t: Throwable) {
                Log.e(TAG, "launch of $pkg failed", t)
            }
            finish()
        }.start()
    }

    companion object {
        private const val TAG = "ShortcutActivity"
    }
}

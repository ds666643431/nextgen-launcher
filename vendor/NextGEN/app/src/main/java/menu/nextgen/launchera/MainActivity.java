package menu.nextgen.launchera;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.androidgamesdk.GameActivity;

import android.os.Bundle;
import android.os.Process;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

/**
 * Native launcher host: GameActivity that loads libloader.so (the Rust/egui UI). This
 * replaces the placeholder BootstrapActivity. The engine is bootstrapped by app/App.kt
 * (NextGenCore) in this same process; the Rust side drives it through VBridge.
 */
public class MainActivity extends GameActivity {

    private static final String WD = "NG-WATCHDOG";
    /** How long the native GameActivity.onCreate handshake may take before we treat it as
     *  deadlocked (normal is < 1s; the engine's own init runs earlier, in Application.onCreate). */
    private static final long ONCREATE_TIMEOUT_MS = 8000L;

    static {
        System.loadLibrary("loader"); // libloader.so exports android_main via winit glue
    }

    /** Set once super.onCreate() (GameActivity's native onCreate) returns — see the watchdog. */
    private volatile boolean nativeCreateReturned = false;

    /** True once this PROCESS has hosted a MainActivity. libloader's android_main is a
     *  once-per-process winit entry, so a SECOND onCreate in the same process must not proceed —
     *  it deadlocks in the GameActivity glue. See onCreate for the guard + recovery. */
    private static boolean sProcessUsed = false;

    /**
     * Startup watchdog for a rare winit/GameActivity startup-handshake deadlock: super.onCreate()
     * has been observed (via an ANR thread dump) to block on a native glue condvar while the
     * render thread (android_main) is already looping — leaving the launcher stuck on a blank
     * splash until it is force-killed. It only reproduces on a cold main-process start while the
     * engine's :black server is still alive, so it is intermittent and hard to catch.
     *
     * This daemon thread runs independently of the (blocked) main thread. If the native onCreate
     * has not returned within the timeout it (1) logs the stuck main-thread stack to logcat, so the
     * next occurrence is fully diagnosable without a dropbox/ANR pull, and (2) self-kills the
     * process, so the user's next launch is a clean cold start (proven reliable) instead of a
     * permanent hang. A healthy launch cancels it by setting nativeCreateReturned first.
     */
    private void armOnCreateWatchdog(final Thread mainThread) {
        Thread wd = new Thread(() -> {
            try {
                Thread.sleep(ONCREATE_TIMEOUT_MS);
            } catch (InterruptedException e) {
                return;
            }
            if (nativeCreateReturned) {
                return; // healthy launch — onCreate returned in time
            }
            StringBuilder sb = new StringBuilder(
                    "MainActivity.onCreate stuck > " + ONCREATE_TIMEOUT_MS
                    + "ms — GameActivity startup-handshake deadlock. Main thread stack:\n");
            try {
                for (StackTraceElement f : mainThread.getStackTrace()) {
                    sb.append("    at ").append(f).append('\n');
                }
            } catch (Throwable t) {
                sb.append("    <stack unavailable: ").append(t).append('>');
            }
            Log.e(WD, sb.toString());
            Log.e(WD, "self-killing pid " + Process.myPid()
                    + " to recover — reopen the launcher for a clean cold start");
            Process.killProcess(Process.myPid());
        }, "ng-oncreate-watchdog");
        wd.setDaemon(true);
        wd.start();
    }

    private void hideSystemUI() {
        getWindow().getAttributes().layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        View decorView = getWindow().getDecorView();
        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(getWindow(), decorView);
        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(WD, "onCreate: pid=" + Process.myPid() + " sProcessUsed=" + sProcessUsed);
        if (sProcessUsed) {
            // This PROCESS already hosted a MainActivity, so libloader's android_main (a
            // once-per-process winit entry) has already run and cannot start again. A SECOND
            // GameActivity.onCreate in the same process deadlocks in the native glue → the
            // launcher is stuck on its splash forever (reproduced by swiping the app from recents
            // while :core's foreground service keeps the process alive, then reopening). Do NOT
            // enter the deadlocking super.onCreate: kill this process so Android relaunches the
            // activity in a fresh process with a fresh android_main. :core (a separate process)
            // stays alive, so this is a fast, clean cold start.
            Log.w(WD, "reused process for MainActivity — killing so Android relaunches fresh");
            Process.killProcess(Process.myPid());
            return;
        }
        sProcessUsed = true;
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        hideSystemUI();
        armOnCreateWatchdog(Thread.currentThread());
        long t0 = android.os.SystemClock.uptimeMillis();
        Log.i(WD, "MainActivity.onCreate: entering native GameActivity.onCreate");
        super.onCreate(savedInstanceState);
        nativeCreateReturned = true;
        Log.i(WD, "MainActivity.onCreate: native onCreate returned in "
                + (android.os.SystemClock.uptimeMillis() - t0) + "ms");
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
    }

    @Override
    protected void onStop() {
        boolean fin = isFinishing();
        Log.i(WD, "onStop: finishing=" + fin + " changingConfig=" + isChangingConfigurations());
        if (fin) {
            // Task finished (e.g. swiped from recents). Kill the process BEFORE the native
            // teardown so no zombie android_main lingers and the next launch is a fresh process.
            Log.i(WD, "onStop(finishing) — killing process for a clean next start");
            Process.killProcess(Process.myPid());
            return;
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        boolean cfg = isChangingConfigurations();
        Log.i(WD, "onDestroy: finishing=" + isFinishing() + " changingConfig=" + cfg);
        if (!cfg) {
            // A real destroy (not a config-change recreate). winit's android_main does NOT exit
            // when the GameActivity is destroyed — it spins ("swap input buffers after
            // GameActivity was destroyed"), and the process (kept alive by :core's foreground
            // service) becomes a zombie that a reopen REUSES → stuck on the splash. Kill the
            // process here so the next launch is a fresh process with a fresh android_main.
            Log.i(WD, "onDestroy(real) — killing process for a clean next start");
            Process.killProcess(Process.myPid());
            return;
        }
        super.onDestroy();
    }
}

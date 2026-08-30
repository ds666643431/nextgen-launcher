package menu.nextgen.launcher;

import android.util.Log;

import java.io.File;

/**
 * nextgenlauncher: loads the custom mod .so into the guest GAME process, right before
 * the guest's Application is created. The guest APK is NEVER modified or resigned
 * — we only add an extra native library to the already-running guest process, so
 * the original Play signature (and PairIP's SignatureCheck) stays valid.
 *
 * The .so ships in the HOST app's nativeLibraryDir (read-only, executable), which
 * the guest — running under the host uid — can load.
 */
public class NgInjector {
    private static final String TAG = "NgInjector";
    private static final String MOD_LIB = "libmodmenu.so";
    private static boolean sLoaded = false;

    public static synchronized void inject(String packageName, String processName) {
        if (sLoaded) return;
        // Only inject into the GAME's main process. The microG / Google-services
        // helper apps (com.android.vending, com.google.android.gms*, ...gsf) run in
        // the SAME container and also pass through here; injecting the mod+overlay
        // into them would put the menu window in a headless process that never
        // follows the game's foreground state (the overlay-over-home bug). Guest
        // ":sub" processes are skipped too.
        if (!isTargetGameProcess(packageName, processName)) {
            Log.w(TAG, "nextgenlauncher: skip injection for " + packageName + " / " + processName);
            return;
        }
        // On emulators the overlay renders via the egui_glow (GL) path, which works on
        // MuMu's emulated GLES (wgpu's queue.submit hangs there). So the overlay is ON by
        // default; only the il2cpp Dobby hook stays OFF (anti-cheat). Both are still
        // controllable via props. If both are forced off we skip injection (pure clone).
        if (isEmulator()) {
            String ov = sysprop("debug.nextgenlauncher.overlay", "1");
            String hk = sysprop("debug.nextgenlauncher.hook", "0");
            boolean ovOn = !("0".equals(ov) || ov.isEmpty());
            boolean hkOn = !("0".equals(hk) || hk.isEmpty());
            if (!ovOn && !hkOn) {
                Log.w(TAG, "nextgenlauncher: emulator + overlay/hook off -> skip injection (pure clone)");
                return;
            }
        }
        try {
            String libDir = NextGenCore.getContext().getApplicationInfo().nativeLibraryDir;
            File so = new File(libDir, MOD_LIB);
            Log.w(TAG, "nextgenlauncher: inject target=" + packageName + " proc=" + processName
                    + " so=" + so.getAbsolutePath() + " exists=" + so.exists());
            if (so.exists()) {
                // Tell the mod where to write its encrypted trace log.
                try {
                    android.system.Os.setenv("MODMENU_LOG_DIR",
                            NextGenCore.getContext().getFilesDir().getAbsolutePath(), true);
                } catch (Throwable ignored) {
                }
                // Runtime toggle for the Dobby hook (debug.nextgenlauncher.hook, default
                // "1"); set "0" for overlay-only where the game's anti-cheat
                // (CodeStage ACTk) flags the inline il2cpp code patch.
                try {
                    String hkDef = isEmulator() ? "0" : "1";
                    String hk = hkDef;
                    try {
                        Class<?> sp = Class.forName("android.os.SystemProperties");
                        hk = (String) sp.getMethod("get", String.class, String.class)
                                .invoke(null, "debug.nextgenlauncher.hook", hkDef);
                    } catch (Throwable ignored) { }
                    if (hk == null || hk.isEmpty()) hk = hkDef;
                    android.system.Os.setenv("MODMENU_HOOK", hk, true);
                } catch (Throwable ignored) {
                }
                System.load(so.getAbsolutePath());
                sLoaded = true;
                Log.w(TAG, "nextgenlauncher: mod .so loaded into guest " + packageName);
                // Bring up the egui overlay (rendered by the mod .so). Skippable via
                // prop debug.nextgenlauncher.overlay=0 (e.g. to isolate overlay-related
                // instability on emulators with software/emulated GL).
                boolean overlayOn = true;
                try {
                    Class<?> sp = Class.forName("android.os.SystemProperties");
                    String ovDef = "1";
                    String v = (String) sp.getMethod("get", String.class, String.class)
                            .invoke(null, "debug.nextgenlauncher.overlay", ovDef);
                    if (v == null || v.isEmpty()) v = ovDef;
                    overlayOn = !"0".equals(v);
                } catch (Throwable ignored) { }
                if (overlayOn) {
                    OverlayHost.show();
                } else {
                    Log.w(TAG, "nextgenlauncher: overlay disabled (debug.nextgenlauncher.overlay=0)");
                }
            } else {
                Log.w(TAG, "nextgenlauncher: mod .so not found; skipping injection");
            }
        } catch (Throwable t) {
            Log.e(TAG, "nextgenlauncher: injection failed", t);
        }
    }

    /** x86/x86_64 host (MuMu/Nox/LDPlayer) running arm-translated: our wgpu overlay
     *  and the inline hook are unreliable on the emulated GL / under translation, so
     *  they default OFF there (a prop can still force them on). */
    private static String sysprop(String key, String def) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            String v = (String) sp.getMethod("get", String.class, String.class).invoke(null, key, def);
            return v == null ? def : v;
        } catch (Throwable t) { return def; }
    }

    private static boolean isEmulator() {
        try {
            for (String a : android.os.Build.SUPPORTED_ABIS) {
                if (a != null && a.startsWith("x86")) return true;
            }
            String hw = android.os.Build.HARDWARE == null ? "" : android.os.Build.HARDWARE;
            String fp = android.os.Build.FINGERPRINT == null ? "" : android.os.Build.FINGERPRINT;
            if (hw.contains("goldfish") || hw.contains("ranchu") || hw.contains("vbox")
                    || hw.contains("mumu") || hw.contains("cancro")
                    || fp.startsWith("generic") || fp.contains("emulator")) return true;
        } catch (Throwable ignored) { }
        return false;
    }

    private static boolean isTargetGameProcess(String pkg, String proc) {
        if (pkg == null) return false;
        if (pkg.equals("com.android.vending")
                || pkg.equals("com.google.android.gsf")
                || pkg.startsWith("com.google.android.gms")) {
            return false;
        }
        // main process only — guest sub-processes contain ':'
        if (proc != null && proc.contains(":")) return false;
        return true;
    }
}

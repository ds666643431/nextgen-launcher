package menu.nextgen.launcher;

import android.app.Activity;
import android.app.Application;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Renders the egui mod menu INSIDE the game's own Activity window: a menu-sized
 * SurfaceView added into the foreground Activity's content view, NOT a
 * TYPE_APPLICATION_OVERLAY system window. Needs no SYSTEM_ALERT_WINDOW permission,
 * so it works on MIUI/Xiaomi (which silently blocks system overlays via the
 * appop). Touches outside the small menu fall through to the game via normal
 * in-window view dispatch.
 *
 * The current guest Activity is found by reflecting ActivityThread.mActivities
 * (with retries), because inside NextGen the guest onActivityResumed may fire
 * before we register or is not dispatched to the guest Application.
 */
public class OverlayHost {
    private static final String TAG = "OverlayHost";
    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static boolean sShown = false;
    private static SurfaceView sView;
    private static boolean sAdded = false;
    private static int sAttempts = 0;

    public static native void nativeSurface(Surface surface, int width, int height);
    public static native void nativeSurfaceDestroyed();
    public static native void nativeTouch(int action, float x, float y);

    public static void show() {
        if (sShown) return;
        sShown = true;
        UI.post(() -> {
            try {
                SurfaceView sv = new SurfaceView(NextGenCore.getContext());
                sView = sv;
                sv.setZOrderOnTop(true);
                sv.getHolder().setFormat(PixelFormat.TRANSLUCENT);
                sv.getHolder().addCallback(new SurfaceHolder.Callback() {
                    @Override public void surfaceCreated(SurfaceHolder h) { }
                    @Override public void surfaceChanged(SurfaceHolder h, int f, int w, int hh) {
                        nativeSurface(h.getSurface(), w, hh);
                    }
                    @Override public void surfaceDestroyed(SurfaceHolder h) { nativeSurfaceDestroyed(); }
                });
                sv.setOnTouchListener((View v, MotionEvent e) -> {
                    nativeTouch(e.getActionMasked(), e.getX(), e.getY());
                    return true;
                });
                registerLifecycle();
                sAttempts = 0;
                tryAttach();
            } catch (Throwable t) {
                Log.e(TAG, "overlay show failed", t);
                sShown = false;
            }
        });
    }

    private static void tryAttach() {
        try {
            if (sAdded || sView == null) return;
            Activity act = currentActivity();
            if (act == null) {
                if (sAttempts++ < 60) UI.postDelayed(OverlayHost::tryAttach, 400);
                return;
            }
            if (sView.getParent() instanceof ViewGroup) {
                ((ViewGroup) sView.getParent()).removeView(sView);
            }
            DisplayMetrics dm = act.getResources().getDisplayMetrics();
            int wpx = Math.min(760, dm.widthPixels - 48);
            int hpx = Math.min(660, dm.heightPixels - 220);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(wpx, hpx);
            lp.leftMargin = 24;
            lp.topMargin = 90;
            act.addContentView(sView, lp);
            sAdded = true;
            Log.w(TAG, "overlay added into game Activity " + act.getClass().getName()
                    + " " + wpx + "x" + hpx);
        } catch (Throwable t) {
            Log.e(TAG, "tryAttach failed: " + t.getMessage());
            if (sAttempts++ < 60) UI.postDelayed(OverlayHost::tryAttach, 400);
        }
    }

    private static void removeOverlay() {
        UI.post(() -> {
            try {
                if (sView != null && sView.getParent() instanceof ViewGroup) {
                    ((ViewGroup) sView.getParent()).removeView(sView);
                }
                sAdded = false;
                Log.w(TAG, "overlay removed");
            } catch (Throwable t) { Log.e(TAG, "removeOverlay failed: " + t.getMessage()); }
        });
    }

    private static void reattach() {
        UI.post(() -> { sAttempts = 0; tryAttach(); });
    }

    private static void registerLifecycle() {
        try {
            Application app = currentGuestApp();
            if (app == null) { Log.w(TAG, "no guest Application for lifecycle"); return; }
            app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override public void onActivityResumed(Activity a) { if (!sAdded) reattach(); }
                @Override public void onActivityPaused(Activity a) { }
                @Override public void onActivityStopped(Activity a) { removeOverlay(); }
                @Override public void onActivityDestroyed(Activity a) { removeOverlay(); }
                @Override public void onActivityStarted(Activity a) { if (!sAdded) reattach(); }
                @Override public void onActivityCreated(Activity a, Bundle b) { }
                @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }
            });
            Log.w(TAG, "overlay: lifecycle registered (in-Activity content view)");
        } catch (Throwable t) {
            Log.e(TAG, "lifecycle register failed", t);
        }
    }

    private static Activity currentActivity() {
        try {
            Class<?> atClass = Class.forName("android.app.ActivityThread");
            Object at = atClass.getMethod("currentActivityThread").invoke(null);
            Field f = atClass.getDeclaredField("mActivities");
            f.setAccessible(true);
            Object obj = f.get(at);
            if (!(obj instanceof Map)) return null;
            Map<?, ?> map = (Map<?, ?>) obj;
            Activity anyAct = null;
            for (Object record : map.values()) {
                if (record == null) continue;
                Class<?> rc = record.getClass();
                Activity a;
                try {
                    Field af = rc.getDeclaredField("activity");
                    af.setAccessible(true);
                    a = (Activity) af.get(record);
                } catch (Throwable ignored) { continue; }
                if (a == null) continue;
                anyAct = a;
                boolean paused = false;
                try {
                    Field pf = rc.getDeclaredField("paused");
                    pf.setAccessible(true);
                    paused = pf.getBoolean(record);
                } catch (Throwable ignored) { }
                if (!paused) return a;
            }
            return anyAct;
        } catch (Throwable ignored) { }
        return null;
    }

    private static Application currentGuestApp() {
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object app = at.getMethod("currentApplication").invoke(null);
            if (app instanceof Application) return (Application) app;
        } catch (Throwable ignored) { }
        return null;
    }
}

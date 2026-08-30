package menu.nextgen.launcher.fake.delegate;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.util.Log;

import java.lang.reflect.Field;

import ref.android.app.BRActivity;
import ref.android.app.BRActivityThread;
import menu.nextgen.launcher.NextGenCore;
import menu.nextgen.launcher.app.BActivityThread;
import menu.nextgen.launcher.core.FacebookOAuth;
import menu.nextgen.launcher.core.GamesProfile;
import menu.nextgen.launcher.fake.hook.HookManager;
import menu.nextgen.launcher.fake.hook.IInjectHook;
import menu.nextgen.launcher.fake.service.HCallbackProxy;
import menu.nextgen.launcher.fake.service.IActivityClientProxy;
import menu.nextgen.launcher.utils.HackAppUtils;
import menu.nextgen.launcher.utils.compat.ActivityCompat;
import menu.nextgen.launcher.utils.compat.ActivityManagerCompat;
import menu.nextgen.launcher.utils.compat.ContextCompat;

public final class AppInstrumentation extends BaseInstrumentationDelegate implements IInjectHook {

    private static final String TAG = AppInstrumentation.class.getSimpleName();

    private static AppInstrumentation sAppInstrumentation;

    public static AppInstrumentation get() {
        if (sAppInstrumentation == null) {
            synchronized (AppInstrumentation.class) {
                if (sAppInstrumentation == null) {
                    sAppInstrumentation = new AppInstrumentation();
                }
            }
        }
        return sAppInstrumentation;
    }

    public AppInstrumentation() {
    }

    @Override
    public void injectHook() {
        try {
            Instrumentation mInstrumentation = getCurrInstrumentation();
            if (mInstrumentation == this || checkInstrumentation(mInstrumentation))
                return;
            mBaseInstrumentation = (Instrumentation) mInstrumentation;
            BRActivityThread.get(NextGenCore.mainThread())._set_mInstrumentation(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Instrumentation getCurrInstrumentation() {
        Object currentActivityThread = NextGenCore.mainThread();
        return BRActivityThread.get(currentActivityThread).mInstrumentation();
    }

    @Override
    public boolean isBadEnv() {
        return !checkInstrumentation(getCurrInstrumentation());
    }

    private boolean checkInstrumentation(Instrumentation instrumentation) {
        if (instrumentation instanceof AppInstrumentation) {
            return true;
        }
        Class<?> clazz = instrumentation.getClass();
        if (Instrumentation.class.equals(clazz)) {
            return false;
        }
        do {
            assert clazz != null;
            Field[] fields = clazz.getDeclaredFields();
            for (Field field : fields) {
                if (Instrumentation.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    try {
                        Object obj = field.get(instrumentation);
                        if ((obj instanceof AppInstrumentation)) {
                            return true;
                        }
                    } catch (Exception e) {
                        return false;
                    }
                }
            }
            clazz = clazz.getSuperclass();
        } while (!Instrumentation.class.equals(clazz));
        return false;
    }

    private void checkHCallback() {
        HookManager.get().checkEnv(HCallbackProxy.class);
    }

    private void checkActivity(Activity activity) {
        Log.d(TAG, "callActivityOnCreate: " + activity.getClass().getName());
        HackAppUtils.enableQQLogOutput(activity.getPackageName(), activity.getClassLoader());
        checkHCallback();
        HookManager.get().checkEnv(IActivityClientProxy.class);
        // checkEnv cannot help here: IActivityClientProxy.isBadEnv() always reports fine,
        // so a controller that was cached before injection is never replaced.
        IActivityClientProxy.ensureInstalled();
        ActivityInfo info = BRActivity.get(activity).mActivityInfo();
        ContextCompat.fix(activity);
        ActivityCompat.fix(activity);
        if (info.theme != 0) {
            activity.getTheme().applyStyle(info.theme, true);
        }
        ActivityManagerCompat.setActivityOrientation(activity, info.screenOrientation);

        // The Facebook SDK just opened its Custom Tab shim, meaning a login is in flight.
        // Start watching for the redirect the host WebView will capture for us.
        if (FacebookOAuth.isFbCustomTab(activity.getClass().getName())) {
            FacebookOAuth.armRedirectWatcher(activity);
        }

        // microG only reaches its sign-in screens when automatic sign-in failed, and the
        // usual reason is that the account has no Play Games profile. Create one.
        String activityName = activity.getClass().getName();
        if ("org.microg.gms.games.GamesSignInActivity".equals(activityName)
                || "org.microg.gms.auth.signin.AuthSignInActivity".equals(activityName)) {
            GamesProfile.ensureRegistered();
        }
    }

    /**
     * Guest apps have no browser inside the container, so a Facebook OAuth page opened via
     * ACTION_VIEW would dead-end. Route it to the host WebView, which carries the Facebook
     * session cookies, and report success so the caller believes a browser was launched.
     */
    private ActivityResult interceptOAuth(Intent intent) {
        if (FacebookOAuth.redirectOAuthBrowser(intent)) {
            return new ActivityResult(0, null);
        }
        return null;
    }

    @Override
    public ActivityResult execStartActivity(Context context, IBinder binder, IBinder binder1,
                                            Activity activity, Intent intent, int i,
                                            Bundle bundle) throws Throwable {
        ActivityResult intercepted = interceptOAuth(intent);
        if (intercepted != null) {
            return intercepted;
        }
        return super.execStartActivity(context, binder, binder1, activity, intent, i, bundle);
    }

    @Override
    public ActivityResult execStartActivity(Context context, IBinder binder, IBinder binder1,
                                            String str, Intent intent, int i,
                                            Bundle bundle) throws Throwable {
        ActivityResult intercepted = interceptOAuth(intent);
        if (intercepted != null) {
            return intercepted;
        }
        return super.execStartActivity(context, binder, binder1, str, intent, i, bundle);
    }

    @Override
    public Application newApplication(ClassLoader cl, String className, Context context) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        ContextCompat.fix(context);

        return super.newApplication(cl, className, context);
    }

    @Override
    public void callActivityOnCreate(Activity activity, Bundle icicle, PersistableBundle persistentState) {
        checkActivity(activity);
        super.callActivityOnCreate(activity, icicle, persistentState);
    }

    @Override
    public void callActivityOnCreate(Activity activity, Bundle icicle) {
        checkActivity(activity);
        super.callActivityOnCreate(activity, icicle);
    }

    @Override
    public void callApplicationOnCreate(Application app) {
        checkHCallback();
        super.callApplicationOnCreate(app);
    }

    public Activity newActivity(ClassLoader cl, String className, Intent intent) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        try {
            return super.newActivity(cl, className, intent);
        } catch (ClassNotFoundException e) {
            return mBaseInstrumentation.newActivity(cl, className, intent);
        }
    }
}

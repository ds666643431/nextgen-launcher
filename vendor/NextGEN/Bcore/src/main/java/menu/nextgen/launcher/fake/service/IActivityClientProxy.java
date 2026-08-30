package menu.nextgen.launcher.fake.service;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.os.IBinder;

import java.lang.reflect.Method;

import ref.android.app.BRActivityClient;
import ref.android.app.BRActivityClientActivityClientControllerSingleton;
import ref.android.util.BRSingleton;
import menu.nextgen.launcher.NextGenCore;
import menu.nextgen.launcher.app.BActivityThread;
import menu.nextgen.launcher.fake.frameworks.BActivityManager;
import menu.nextgen.launcher.fake.hook.ClassInvocationStub;
import menu.nextgen.launcher.fake.hook.MethodHook;
import menu.nextgen.launcher.fake.hook.ProxyMethod;
import menu.nextgen.launcher.fake.hook.ScanClass;
import menu.nextgen.launcher.utils.Slog;
import menu.nextgen.launcher.utils.compat.TaskDescriptionCompat;


@ScanClass(ActivityManagerCommonProxy.class)
public class IActivityClientProxy extends ClassInvocationStub {
    public static final String TAG = "IActivityClientProxy";
    private final Object who;

    public IActivityClientProxy(Object who) {
        this.who = who;
    }

    @Override
    protected Object getWho() {
        if (who != null) {
            return who;
        }
        Object instance = BRActivityClient.get().getInstance();
        Object singleton = BRActivityClient.get(instance).INTERFACE_SINGLETON();
        return BRSingleton.get(singleton).get();
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        Object instance = BRActivityClient.get().getInstance();
        Object singleton = BRActivityClient.get(instance).INTERFACE_SINGLETON();
        BRSingleton.get(singleton)._set_mInstance(proxyInvocation);
        // ActivityClient's singleton is an ActivityClientControllerSingleton, which caches
        // the controller in mKnownInstance rather than Singleton.mInstance. Setting only
        // the latter leaves the real controller in use and every hook here is dead code --
        // which is why getCallingActivity() was never intercepted.
        try {
            BRActivityClientActivityClientControllerSingleton.get(singleton)
                    ._set_mKnownInstance(proxyInvocation);
        } catch (Throwable t) {
            Slog.d(TAG, "no mKnownInstance on this platform: " + t);
        }
    }

    /**
     * Force the ActivityClient controller to be our proxy, right now.
     *
     * injectHook()'s normal path sets Singleton.mInstance, but ActivityClient caches its
     * controller in ActivityClientControllerSingleton.mKnownInstance -- so the hooks here
     * can end up never running. Activities that inspect their caller (microG's
     * GamesSignInActivity and AuthSignInActivity both do) then see the host and refuse to
     * start, which breaks Play Games sign-in. Call this before an activity's onCreate.
     */
    public static void ensureInstalled() {
        try {
            Object controller = BRActivityClient.get().getActivityClientController();
            if (controller == null || controller instanceof java.lang.reflect.Proxy) {
                return;
            }
            IActivityClientProxy proxy = new IActivityClientProxy(controller);
            proxy.onlyProxy(true);
            proxy.injectHook();
            Object instance = BRActivityClient.get().getInstance();
            Object singleton = BRActivityClient.get(instance).INTERFACE_SINGLETON();
            BRActivityClientActivityClientControllerSingleton.get(singleton)
                    ._set_mKnownInstance(proxy.getProxyInvocation());
            Slog.d(TAG, "activity client controller proxied");
        } catch (Throwable t) {
            Slog.d(TAG, "could not proxy the activity client controller: " + t);
        }
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @Override
    public Object getProxyInvocation() {
        return super.getProxyInvocation();
    }

    @Override
    public void onlyProxy(boolean o) {
        super.onlyProxy(o);
    }

    @ProxyMethod("finishActivity")
    public static class FinishActivity extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            IBinder token = (IBinder) args[0];
            BActivityManager.get().onFinishActivity(token);
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("activityResumed")
    public static class ActivityResumed extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            IBinder token = (IBinder) args[0];
            BActivityManager.get().onActivityResumed(token);
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("activityDestroyed")
    public static class ActivityDestroyed extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            IBinder token = (IBinder) args[0];
            BActivityManager.get().onActivityDestroyed(token);
            return method.invoke(who, args);
        }
    }

    
    @ProxyMethod("setTaskDescription")
    public static class SetTaskDescription extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ActivityManager.TaskDescription td = (ActivityManager.TaskDescription) args[1];
            args[1] = TaskDescriptionCompat.fix(td);
            return method.invoke(who, args);
        }
    }

}

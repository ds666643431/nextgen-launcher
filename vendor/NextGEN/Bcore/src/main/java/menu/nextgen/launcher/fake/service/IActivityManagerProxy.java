package menu.nextgen.launcher.fake.service;

import android.Manifest;
import android.app.ActivityManager;
import android.app.IServiceConnection;
import android.content.ComponentName;
import android.content.Context;
import android.content.IIntentReceiver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.os.IBinder;
import android.os.IInterface;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Objects;

import ref.android.app.BRActivityManagerNative;
import ref.android.app.BRActivityManagerOreo;
import ref.android.app.BRLoadedApkReceiverDispatcher;
import ref.android.app.BRLoadedApkReceiverDispatcherInnerReceiver;
import ref.android.app.BRLoadedApkServiceDispatcher;
import ref.android.app.BRLoadedApkServiceDispatcherInnerConnection;
import ref.android.content.BRContentProviderNative;
import ref.android.content.pm.BRUserInfo;
import ref.android.util.BRSingleton;
import menu.nextgen.launcher.NextGenCore;
import menu.nextgen.launcher.app.BActivityThread;
import menu.nextgen.launcher.core.GamesShim;
import menu.nextgen.launcher.core.GmsCore;
import menu.nextgen.launcher.core.env.AppSystemEnv;
import menu.nextgen.launcher.entity.AppConfig;
import menu.nextgen.launcher.entity.am.RunningAppProcessInfo;
import menu.nextgen.launcher.entity.am.RunningServiceInfo;
import menu.nextgen.launcher.fake.delegate.ContentProviderDelegate;
import menu.nextgen.launcher.fake.delegate.InnerReceiverDelegate;
import menu.nextgen.launcher.fake.delegate.ServiceConnectionDelegate;
import menu.nextgen.launcher.fake.frameworks.BActivityManager;
import menu.nextgen.launcher.fake.frameworks.BPackageManager;
import menu.nextgen.launcher.fake.hook.ClassInvocationStub;
import menu.nextgen.launcher.fake.hook.MethodHook;
import menu.nextgen.launcher.fake.hook.ProxyMethod;
import menu.nextgen.launcher.fake.hook.ScanClass;
import menu.nextgen.launcher.fake.service.base.PkgMethodProxy;
import menu.nextgen.launcher.fake.service.context.providers.ContentProviderStub;
import menu.nextgen.launcher.proxy.ProxyManifest;
import menu.nextgen.launcher.proxy.record.ProxyBroadcastRecord;
import menu.nextgen.launcher.proxy.record.ProxyPendingRecord;
import menu.nextgen.launcher.utils.MethodParameterUtils;
import menu.nextgen.launcher.utils.Reflector;
import menu.nextgen.launcher.utils.compat.ActivityManagerCompat;
import menu.nextgen.launcher.utils.compat.BuildCompat;
import menu.nextgen.launcher.utils.compat.ParceledListSliceCompat;
import menu.nextgen.launcher.utils.compat.TaskDescriptionCompat;
import menu.nextgen.launcher.utils.Slog;

import static android.content.Context.RECEIVER_EXPORTED;
import static android.content.Context.RECEIVER_NOT_EXPORTED;
import static android.content.pm.PackageManager.GET_META_DATA;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;


@ScanClass(ActivityManagerCommonProxy.class)
public class IActivityManagerProxy extends ClassInvocationStub {
    public static final String TAG = "ActivityManagerStub";

    @Override
    protected Object getWho() {
        Object iActivityManager = null;
        if (BuildCompat.isOreo()) {
            iActivityManager = BRActivityManagerOreo.get().IActivityManagerSingleton();
        } else if (BuildCompat.isL()) {
            iActivityManager = BRActivityManagerNative.get().gDefault();
        }
        return BRSingleton.get(iActivityManager).get();
    }

    @Override
    protected void inject(Object base, Object proxy) {
        Object iActivityManager = null;
        if (BuildCompat.isOreo()) {
            iActivityManager = BRActivityManagerOreo.get().IActivityManagerSingleton();
        } else if (BuildCompat.isL()) {
            iActivityManager = BRActivityManagerNative.get().gDefault();
        }
        BRSingleton.get(iActivityManager)._set_mInstance(proxy);
    }

    @Override
    public boolean isBadEnv() {
        return getProxyInvocation() != getWho();
    }

    @Override
    protected void onBindMethod() {
        super.onBindMethod();
        addMethodHook(new PkgMethodProxy("getAppStartMode"));
        addMethodHook(new PkgMethodProxy("setAppLockedVerifying"));
        addMethodHook(new PkgMethodProxy("reportJunkFromApp"));
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        try {
            return super.invoke(proxy, method, args);
        } catch (SecurityException e) {
            
            String methodName = method.getName();
            Slog.w(TAG, "ActivityManager invoke: SecurityException in " + methodName + ", returning safe default", e);
            
            
            if (methodName.startsWith("set") || methodName.startsWith("update")) {
                return null; 
            } else if (methodName.startsWith("get") || methodName.startsWith("query")) {
                return null; 
            } else if (methodName.startsWith("start") || methodName.startsWith("bind")) {
                return false; 
            } else if (methodName.startsWith("stop") || methodName.startsWith("unbind")) {
                return true; 
            } else {
                return null; 
            }
        } catch (Exception e) {
            Slog.e(TAG, "ActivityManager invoke: Unexpected error in " + method.getName(), e);
            return super.invoke(proxy, method, args);
        }
    }

    @ProxyMethod("getContentProvider")
    public static class GetContentProvider extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            int authIndex = getAuthIndex();
            Object auth = args[authIndex];
            Object content = null;

            if (auth instanceof String) {
                if (ProxyManifest.isProxy((String) auth)) {
                    return method.invoke(who, args);
                }

                if (BuildCompat.isQ()) {
                    args[1] = NextGenCore.getHostPkg();
                }

                boolean googleAuth = ((String) auth).contains("com.google.android.gms")
                        || ((String) auth).contains("com.android.vending")
                        || ((String) auth).contains("com.google.android.gsf")
                        || auth.equals("com.google.android.gms.chimera");

                // Forwarding Google authorities to the host is right for a host-cloned
                // GMS, whose providers really do live over there. But microG publishes
                // its OWN providers inside the container under the same namespace --
                // com.google.android.gms.microg.{settings,profile,...}. The host's GMS
                // never declares those, so escaping there resolves to nothing and microG
                // cannot read its own settings (null cursor -> MainSettingsActivity dies
                // inflating its fragment). Keep the .microg. namespace in the container.
                if (!((String) auth).contains(".microg.")
                        && (auth.equals("settings")
                        || auth.equals("media")
                        || auth.equals("telephony")
                        || googleAuth
                        || auth.equals("com.huawei.android.launcher.settings")
                        || auth.equals("com.hihonor.android.launcher.settings"))) {
                    content = method.invoke(who, args);
                    ContentProviderDelegate.update(content, (String) auth);
                    return content;
                } else {
                    

                    ProviderInfo providerInfo = NextGenCore.getBPackageManager()
                            .resolveContentProvider(
                                    (String) auth, GET_META_DATA, BActivityThread.getUserId());
                    if (providerInfo == null) {
                        
                        return null;
                    }

                    
                    IBinder providerBinder = null;
                    boolean sameProcess = false;
                    if (BActivityThread.getAppPid() != -1) {
                        AppConfig appConfig = NextGenCore.getBActivityManager()
                                .initProcess(
                                        providerInfo.packageName,
                                        providerInfo.processName,
                                        BActivityThread.getUserId());
                        sameProcess = appConfig.bpid == BActivityThread.getAppPid();
                        if (!sameProcess) {
                            // The owning process may still be starting -- microG publishes
                            // its settings provider from :persistent while :ui is already
                            // asking for it. Returning null here surfaces as a null cursor
                            // and aborts Play Games sign-in, so give it a moment.
                            for (int attempt = 0; attempt < 5; attempt++) {
                                providerBinder = NextGenCore.getBActivityManager()
                                        .acquireContentProviderClient(providerInfo);
                                if (providerBinder != null) {
                                    break;
                                }
                                try {
                                    Thread.sleep(100);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    break;
                                }
                            }
                        }
                        args[authIndex] = ProxyManifest.getProxyAuthorities(appConfig.bpid);
                        args[getUserIndex()] = NextGenCore.getHostUserId();
                    }

                    // The provider lives in the process that is asking for it. There is no
                    // client binder to acquire -- returning null here is what made microG's
                    // own :persistent process fail to find its SettingsProvider. Let the
                    // caller publish it itself: hand back the holder with the real info and
                    // a null provider, which is ActivityThread's cue to install it locally.
                    if (sameProcess) {
                        content = method.invoke(who, args);
                        Reflector.with(content).field("info").set(providerInfo);
                        Reflector.with(content).field("provider").set(null);
                        return content;
                    }

                    if (providerBinder == null)
                        return null;

                    content = method.invoke(who, args);
                    Reflector.with(content).field("info").set(providerInfo);
                    Reflector.with(content)
                            .field("provider")
                            .set(
                                    new ContentProviderStub()
                                            .wrapper(
                                                    BRContentProviderNative.get().asInterface(providerBinder),
                                                    providerInfo.packageName));
                }

                return content;
            }
            return method.invoke(who, args);
        }

        protected int getAuthIndex() {
            
            if (BuildCompat.isQ()) {
                return 2;
            } else {
                return 1;
            }
        }

        protected int getUserIndex() {
            return getAuthIndex() + 1;
        }
    }

    @ProxyMethod("startService")
    public static class StartService extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Intent intent = (Intent) args[1];
            String resolvedType = (String) args[2];
            ResolveInfo resolveInfo = NextGenCore.getBPackageManager().resolveService(intent, 0, resolvedType, BActivityThread.getUserId());
            if (resolveInfo == null) {
                return method.invoke(who, args);
            }

            int requireForegroundIndex = getRequireForeground();
            boolean requireForeground = false;
            if (requireForegroundIndex != -1) {
                requireForeground = (boolean) args[requireForegroundIndex];
            }
            return NextGenCore.getBActivityManager().startService(intent, resolvedType, requireForeground, BActivityThread.getUserId());
        }

        public int getRequireForeground() {
            if (BuildCompat.isOreo()) {
                return 3;
            }
            return -1;
        }
    }

    @ProxyMethod("stopService")
    public static class StopService extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                Intent intent = (Intent) args[1];
                String resolvedType = (String) args[2];
                
                                        
                        if (intent != null && intent.getComponent() != null) {
                            String servicePackage = intent.getComponent().getPackageName();
                            String currentPackage = BActivityThread.getAppPackageName();

                            
                            if (!servicePackage.equals(currentPackage)) {
                                Slog.w(TAG, "StopService: Attempting to stop service from different package: " +
                                        servicePackage + " (current: " + currentPackage + "), returning false");
                                return false;
                            }
                        }
                
                return NextGenCore.getBActivityManager().stopService(intent, resolvedType, BActivityThread.getUserId());
            } catch (SecurityException e) {
                Slog.w(TAG, "StopService: SecurityException caught, returning false", e);
                return false;
            } catch (Exception e) {
                Slog.e(TAG, "StopService: Error stopping service", e);
                return false;
            }
        }
    }

    @ProxyMethod("stopServiceToken")
    public static class StopServiceToken extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                ComponentName componentName = (ComponentName) args[0];
                IBinder token = (IBinder) args[1];
                
                                        
                        if (componentName != null) {
                            String servicePackage = componentName.getPackageName();
                            String currentPackage = BActivityThread.getAppPackageName();

                            
                            if (!servicePackage.equals(currentPackage)) {
                                Slog.w(TAG, "StopServiceToken: Attempting to stop service from different package: " +
                                        servicePackage + " (current: " + currentPackage + "), returning true");
                                return true;
                            }
                        }
                
                NextGenCore.getBActivityManager().stopServiceToken(componentName, token, BActivityThread.getUserId());
                return true;
            } catch (SecurityException e) {
                Slog.w(TAG, "StopServiceToken: SecurityException caught, returning true", e);
                return true;
            } catch (Exception e) {
                Slog.e(TAG, "StopServiceToken: Error stopping service token", e);
                return true;
            }
        }
    }

    @ProxyMethod("setActivityLocusContext")
    public static class SetActivityLocusContext extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                                        
                        if (args != null && args.length >= 2) {
                            String targetPackage = (String) args[1];
                            String currentPackage = BActivityThread.getAppPackageName();

                            
                            if (targetPackage != null && !targetPackage.equals(currentPackage)) {
                                Slog.w(TAG, "SetActivityLocusContext: Attempting to set locus context for different package: " +
                                        targetPackage + " (current: " + currentPackage + "), returning success");
                                return null; 
                            }
                        }
                
                
                return method.invoke(who, args);
            } catch (SecurityException e) {
                Slog.w(TAG, "SetActivityLocusContext: SecurityException caught, returning success", e);
                return null; 
            } catch (Exception e) {
                Slog.e(TAG, "SetActivityLocusContext: Error setting locus context", e);
                return null; 
            }
        }
    }

    public static Object BindServiceCommon(Object who, Method method, Object[] args,int callingPackageIndex) throws Throwable {
        try {
            Intent intent = (Intent) args[2];
            String resolvedType = (String) args[3];
            IServiceConnection connection = (IServiceConnection) args[4];

            
            if (intent == null) {
                Slog.w(TAG, "BindServiceCommon: Intent is null, proceeding with original call");
                return method.invoke(who, args);
            }

            

            int userId = intent.getIntExtra("_B_|_UserId", -1);
            userId = userId == -1 ? BActivityThread.getUserId() : userId;
            ResolveInfo resolveInfo = NextGenCore.getBPackageManager().resolveService(intent, 0, resolvedType, userId);

            // play-services binds GMS through com.google.android.gms/.chimera.GmsApiService
            // with the real action hidden in a "chimera-action:" data URI. That component
            // does not resolve against what the container's GMS declares, and the bind then
            // escapes to the REAL device GMS -- which is why the games connect service was
            // never found. Rebuild the intent from the action and re-resolve in-container.
            if (resolveInfo == null && GamesShim.isGuestGmsBind(intent)) {
                String action = GamesShim.gmsApiAction(intent);
                if (action != null) {
                    Intent rebuilt = new Intent(action);
                    rebuilt.setPackage(GmsCore.GMS_PKG);
                    if (intent.getExtras() != null) {
                        rebuilt.putExtras(intent.getExtras());
                    }
                    ResolveInfo viaAction = NextGenCore.getBPackageManager()
                            .resolveService(rebuilt, 0, resolvedType, userId);
                    if (viaAction != null) {
                        Slog.d(TAG, "re-resolved GMS bind via chimera action: " + action);
                        intent = rebuilt;
                        args[2] = rebuilt;
                        resolveInfo = viaAction;
                    }
                }
            }

            if (resolveInfo != null || AppSystemEnv.isOpenPackage(intent.getComponent())) {
                Intent proxyIntent = NextGenCore.getBActivityManager().bindService(intent,
                        connection == null ? null : connection.asBinder(),
                        resolvedType,
                        userId);
                if (connection != null) {
                    if (intent.getComponent() == null && resolveInfo != null) {
                        intent.setComponent(new ComponentName(resolveInfo.serviceInfo.packageName, resolveInfo.serviceInfo.name));
                    }
                    IServiceConnection proxy = ServiceConnectionDelegate.createProxy(connection, intent);
                    args[4] = proxy;

                    WeakReference<?> weakReference = BRLoadedApkServiceDispatcherInnerConnection.get(connection).mDispatcher();
                    if (weakReference != null) {
                        BRLoadedApkServiceDispatcher.get(weakReference.get())._set_mConnection(proxy);
                    }
                }

                
                if (proxyIntent != null && proxyIntent.getComponent() != null && 
                    proxyIntent.getComponent().getPackageName().equals(NextGenCore.getHostPkg())){
                    int flagsIndex = getFlagsIndex(args);
                    if (flagsIndex >= 0) {
                        int flags = MethodParameterUtils.toInt(args[flagsIndex]);
                        flags &= ~Context.BIND_EXTERNAL_SERVICE;
                        args[flagsIndex] = flags;
                    }
                }
                args[callingPackageIndex] = NextGenCore.getHostPkg();

                if (proxyIntent != null) {
                    args[2] = proxyIntent;
                    return method.invoke(who, args);
                }
            }
            return method.invoke(who, args);
        } catch (Exception e) {
            Slog.e(TAG, "BindServiceCommon: Unexpected error", e);
            return method.invoke(who, args);
        }
    }

    private static int getFlagsIndex(Object[] args) {
        
        for (int i = 5; i < args.length; i++) {
            Object arg = args[i];
            if (arg instanceof Integer || arg instanceof Long) {
                return i;
            }
        }
        return -1;
    }
    @ProxyMethod("bindService")
    public static class BindService extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return BindServiceCommon(who,method,args,6);
        }

        @Override
        protected boolean isEnable() {
            return NextGenCore.get().isCoreProcess() || NextGenCore.get().isServerProcess();
        }
    }

    
    @ProxyMethod("bindServiceInstance")
    public static class bindServiceInstance extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return BindServiceCommon(who,method,args,7);
        }

        @Override
        protected boolean isEnable() {
            return NextGenCore.get().isCoreProcess() || NextGenCore.get().isServerProcess();
        }
    }

    
    @ProxyMethod("bindIsolatedService")
    public static class BindIsolatedService extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            
            args[6] = null;
            return BindServiceCommon(who,method,args,7);
        }

        @Override
        protected boolean isEnable() {
            return NextGenCore.get().isCoreProcess() || NextGenCore.get().isServerProcess();
        }
    }

    @ProxyMethod("unbindService")
    public static class UnbindService extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            IServiceConnection iServiceConnection = (IServiceConnection) args[0];
            if (iServiceConnection == null) {
                return method.invoke(who, args);
            }
            NextGenCore.getBActivityManager().unbindService(iServiceConnection.asBinder(), BActivityThread.getUserId());
            ServiceConnectionDelegate delegate = ServiceConnectionDelegate.getDelegate(iServiceConnection.asBinder());
            if (delegate != null) {
                args[0] = delegate;
            }
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("getRunningAppProcesses")
    public static class GetRunningAppProcesses extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            RunningAppProcessInfo runningAppProcesses = BActivityManager.get().getRunningAppProcesses(BActivityThread.getAppPackageName(), BActivityThread.getUserId());
            if (runningAppProcesses == null) {
                return new ArrayList<>();
            }
            return runningAppProcesses.mAppProcessInfoList;
        }
    }

    @ProxyMethod("getServices")
    public static class GetServices extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            RunningServiceInfo runningServices = BActivityManager.get().getRunningServices(BActivityThread.getAppPackageName(), BActivityThread.getUserId());
            if (runningServices == null) {
                return new ArrayList<>();
            }
            return runningServices.mRunningServiceInfoList;
        }
    }

    @ProxyMethod("getIntentSender")
    public static class GetIntentSender extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            int type = (int) args[0];
            Intent[] intents = (Intent[]) args[getIntentsIndex(args)];
            MethodParameterUtils.replaceFirstAppPkg(args);

            for (int i = 0; i < intents.length; i++) {
                Intent intent = intents[i];
                switch (type) {
                    case ActivityManagerCompat.INTENT_SENDER_ACTIVITY:
                        Intent shadow = new Intent();
                        shadow.setComponent(new ComponentName(NextGenCore.getHostPkg(), ProxyManifest.getProxyPendingActivity(BActivityThread.getAppPid())));
                        ProxyPendingRecord.saveStub(shadow, intent, BActivityThread.getUserId());
                        intents[i] = shadow;
                        break;
                }
            }
            IInterface invoke = (IInterface) method.invoke(who, args);
            if (invoke != null) {
                String[] packagesForUid = BPackageManager.get().getPackagesForUid(BActivityThread.getCallingBUid());
                if (packagesForUid.length < 1) {
                    packagesForUid = new String[]{NextGenCore.getHostPkg()};
                }
                NextGenCore.getBActivityManager().getIntentSender(invoke.asBinder(), packagesForUid[0], BActivityThread.getCallingBUid());
            }
            return invoke;
        }

        private int getIntentsIndex(Object[] args) {
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof Intent[]) {
                    return i;
                }
            }
            if (BuildCompat.isR()) {
                return 6;
            } else {
                return 5;
            }
        }
    }

    @ProxyMethod("getPackageForIntentSender")
    public static class getPackageForIntentSender extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            IInterface invoke = (IInterface) args[0];
            return NextGenCore.getBActivityManager().getPackageForIntentSender(invoke.asBinder());
        }
    }

    @ProxyMethod("getUidForIntentSender")
    public static class getUidForIntentSender extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            IInterface invoke = (IInterface) args[0];
            return NextGenCore.getBActivityManager().getUidForIntentSender(invoke.asBinder());
        }
    }

    @ProxyMethod("getIntentSenderWithSourceToken")
    public static class GetIntentSenderWithSourceToken extends GetIntentSender {
    }

    @ProxyMethod("getIntentSenderWithFeature")
    public static class GetIntentSenderWithFeature extends GetIntentSender {
    }

    @ProxyMethod("broadcastIntentWithFeature")
    public static class BroadcastIntentWithFeature extends BroadcastIntent {
    }

    @ProxyMethod("broadcastIntent")
    public static class BroadcastIntent extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            int intentIndex = getIntentIndex(args);
            Intent intent = (Intent) args[intentIndex];
            String resolvedType = (String) args[intentIndex + 1];
            Intent proxyIntent = NextGenCore.getBActivityManager().sendBroadcast(intent, resolvedType, BActivityThread.getUserId());
            if (proxyIntent != null) {
                proxyIntent.setExtrasClassLoader(BActivityThread.getApplication().getClassLoader());
                ProxyBroadcastRecord.saveStub(proxyIntent, intent, BActivityThread.getUserId());
                args[intentIndex] = proxyIntent;
            }
            
            for (int i = 0; i < args.length; i++) {
                Object o = args[i];
                if (o instanceof String[]) {
                    args[i] = null;
                }
            }
            return method.invoke(who, args);
        }

        int getIntentIndex(Object[] args) {
            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                if (arg instanceof Intent) {
                    return i;
                }
            }
            return 1;
        }
    }

    @ProxyMethod("unregisterReceiver")
    public static class unregisterReceiver extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("finishReceiver")
    public static class finishReceiver extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("publishService")
    public static class PublishService extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("peekService")
    public static class PeekService extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceLastAppPkg(args);
            Intent intent = (Intent) args[0];
            String resolvedType = (String) args[1];
            IBinder peek = NextGenCore.getBActivityManager().peekService(intent, resolvedType, BActivityThread.getUserId());
            return peek;
        }
    }

    
    @ProxyMethod("sendIntentSender")
    public static class SendIntentSender extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return 0;
        }
    }

    
    @ProxyMethod("registerReceiverWithFeature")
    public static class RegisterReceiverWithFeature extends MethodHook{
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceFirstAppPkg(args);
            int receiverIndex = getReceiverIndex();
            if (args[receiverIndex] != null) {
                IIntentReceiver intentReceiver = (IIntentReceiver) args[receiverIndex];
                IIntentReceiver proxy = InnerReceiverDelegate.createProxy(intentReceiver);

                WeakReference<?> weakReference = BRLoadedApkReceiverDispatcherInnerReceiver.get(intentReceiver).mDispatcher();
                if (weakReference != null) {
                    BRLoadedApkReceiverDispatcher.get(weakReference.get())._set_mIIntentReceiver(proxy);
                }

                args[receiverIndex] = proxy;
            }
            
            if (args[getPermissionIndex()] != null) {
                args[getPermissionIndex()] = null;
            }

            if (BuildCompat.isU()) {
                int flagsIndex = args.length - 1;
                int flags = (int)args[flagsIndex];
                if((flags & RECEIVER_NOT_EXPORTED) == 0 && (flags & RECEIVER_EXPORTED) == 0){
                    flags |= RECEIVER_NOT_EXPORTED;
                }
                args[flagsIndex] = flags;
            }

            return method.invoke(who, args);
        }

        public int getReceiverIndex() {
            if (BuildCompat.isS()) {
                return 4;
            }
            return 3;
        }

        public int getPermissionIndex() {
            if (BuildCompat.isS()) {
                return 6;
            }
            return 5;
        }
    }

    
    @ProxyMethod("registerReceiver")
    public static class RegisterReceiver extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceFirstAppPkg(args);
            int receiverIndex = 2;
            if (args[receiverIndex] != null) {
                IIntentReceiver intentReceiver = (IIntentReceiver) args[receiverIndex];
                IIntentReceiver proxy = InnerReceiverDelegate.createProxy(intentReceiver);

                WeakReference<?> weakReference = BRLoadedApkReceiverDispatcherInnerReceiver.get(intentReceiver).mDispatcher();
                if (weakReference != null) {
                    BRLoadedApkReceiverDispatcher.get(weakReference.get())._set_mIIntentReceiver(proxy);
                }

                args[receiverIndex] = proxy;
            }
            int permissionIndex = 4;
            
            if (args[permissionIndex] != null) {
                args[permissionIndex] = null;
            }
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("grantUriPermission")
    public static class GrantUriPermission extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceLastUid(args);
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("setServiceForeground")
    public static class setServiceForeground extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            
            
            for (int i = args.length - 1; i >= 0; i--) {
                if (args[i] instanceof Integer) {
                    args[i] = 0; 
                    break;
                }
            }
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("getHistoricalProcessExitReasons")
    public static class getHistoricalProcessExitReasons extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return ParceledListSliceCompat.create(new ArrayList<>());
        }
    }

    @ProxyMethod("getCurrentUser")
    public static class getCurrentUser extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Object nextGen = BRUserInfo.get()._new(BActivityThread.getUserId(), "NextGen", BRUserInfo.get().FLAG_PRIMARY());
            return nextGen;
        }
    }

    @ProxyMethod("checkPermission")
    public static class checkPermission extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceLastUid(args);
            String permission = (String) args[0];
            if (permission.equals(Manifest.permission.ACCOUNT_MANAGER)
                    || permission.equals(Manifest.permission.SEND_SMS)) {
                return PackageManager.PERMISSION_GRANTED;
            }
            
            
            if (isAudioPermission(permission)) {
                Slog.d(TAG, "ActivityManager checkPermission: Granting audio permission: " + permission);
                return PackageManager.PERMISSION_GRANTED;
            }

            
            if (isStorageOrMediaPermission(permission)) {
                Slog.d(TAG, "ActivityManager checkPermission: Granting storage/media permission: " + permission);
                return PackageManager.PERMISSION_GRANTED;
            }
            
            return method.invoke(who, args);
        }
    }

    
    private static boolean isAudioPermission(String permission) {
        if (permission == null) return false;
        return permission.equals(Manifest.permission.RECORD_AUDIO)
                || permission.equals(Manifest.permission.CAPTURE_AUDIO_OUTPUT)
                || permission.equals(Manifest.permission.MODIFY_AUDIO_SETTINGS)
                || permission.equals("android.permission.FOREGROUND_SERVICE_MICROPHONE")
                || permission.equals("android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION")
                || permission.equals("android.permission.FOREGROUND_SERVICE_CAMERA")
                || permission.equals("android.permission.FOREGROUND_SERVICE_LOCATION")
                || permission.equals("android.permission.FOREGROUND_SERVICE_HEALTH")
                || permission.equals("android.permission.FOREGROUND_SERVICE_DATA_SYNC")
                || permission.equals("android.permission.FOREGROUND_SERVICE_SPECIAL_USE")
                || permission.equals("android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED")
                || permission.equals("android.permission.FOREGROUND_SERVICE_PHONE_CALL")
                || permission.equals("android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE");
    }

    @ProxyMethod("checkUriPermission")
    public static class checkUriPermission extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return PERMISSION_GRANTED;
        }
    }

    
    private static boolean isStorageOrMediaPermission(String permission) {
        if (permission == null) return false;
        if (permission.equals(Manifest.permission.READ_EXTERNAL_STORAGE)
                || permission.equals(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            return true;
        }
        if (permission.equals(Manifest.permission.READ_MEDIA_AUDIO)
                || permission.equals(Manifest.permission.READ_MEDIA_VIDEO)
                || permission.equals(Manifest.permission.READ_MEDIA_IMAGES)
                || permission.equals("android.permission.READ_MEDIA_VISUAL")
                || permission.equals("android.permission.READ_MEDIA_AURAL")
                || permission.equals(Manifest.permission.ACCESS_MEDIA_LOCATION)) {
            return true;
        }
        if (permission.equals("android.permission.READ_MEDIA_AUDIO_USER_SELECTED")
                || permission.equals("android.permission.READ_MEDIA_VIDEO_USER_SELECTED")
                || permission.equals("android.permission.READ_MEDIA_IMAGES_USER_SELECTED")
                || permission.equals("android.permission.READ_MEDIA_VISUAL_USER_SELECTED")
                || permission.equals("android.permission.READ_MEDIA_AURAL_USER_SELECTED")) {
                return true;
        }
        return false;
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

    @ProxyMethod("setRequestedOrientation")
    public static class setRequestedOrientation extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                return method.invoke(who, args);
            } catch (Throwable e) {
                e.printStackTrace();
            }
            return 0;
        }
    }

    @ProxyMethod("registerUidObserver")
    public static class registerUidObserver extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return 0;
        }
    }

    @ProxyMethod("unregisterUidObserver")
    public static class unregisterUidObserver extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return 0;
        }
    }

    @ProxyMethod("updateConfiguration")
    public static class updateConfiguration extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return 0;
        }
    }
}

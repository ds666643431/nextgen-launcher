package menu.nextgen.launcher.fake.hook;

import android.util.Log;

import java.util.HashMap;
import java.util.Map;

import menu.nextgen.launcher.NextGenCore;
import menu.nextgen.launcher.fake.delegate.AppInstrumentation;

import menu.nextgen.launcher.fake.service.HCallbackProxy;
import menu.nextgen.launcher.fake.service.IAccessibilityManagerProxy;
import menu.nextgen.launcher.fake.service.IAccountManagerProxy;
import menu.nextgen.launcher.fake.service.IActivityClientProxy;
import menu.nextgen.launcher.fake.service.IActivityManagerProxy;
import menu.nextgen.launcher.fake.service.IActivityTaskManagerProxy;
import menu.nextgen.launcher.fake.service.IAlarmManagerProxy;
import menu.nextgen.launcher.fake.service.IAppOpsManagerProxy;
import menu.nextgen.launcher.fake.service.IAppWidgetManagerProxy;
import menu.nextgen.launcher.fake.service.IAttributionSourceProxy;
import menu.nextgen.launcher.fake.service.IAutofillManagerProxy;
import menu.nextgen.launcher.fake.service.ISensitiveContentProtectionManagerProxy;
import menu.nextgen.launcher.fake.service.ISettingsSystemProxy;
import menu.nextgen.launcher.fake.service.IConnectivityManagerProxy;
import menu.nextgen.launcher.fake.service.ISystemSensorManagerProxy;
import menu.nextgen.launcher.fake.service.IContentProviderProxy;
import menu.nextgen.launcher.fake.service.IXiaomiAttributionSourceProxy;
import menu.nextgen.launcher.fake.service.IXiaomiSettingsProxy;
import menu.nextgen.launcher.fake.service.IXiaomiMiuiServicesProxy;
import menu.nextgen.launcher.fake.service.IDnsResolverProxy;
import menu.nextgen.launcher.fake.service.IContextHubServiceProxy;
import menu.nextgen.launcher.fake.service.IDeviceIdentifiersPolicyProxy;
import menu.nextgen.launcher.fake.service.IDevicePolicyManagerProxy;
import menu.nextgen.launcher.fake.service.IDisplayManagerProxy;
import menu.nextgen.launcher.fake.service.IFingerprintManagerProxy;
import menu.nextgen.launcher.fake.service.IGraphicsStatsProxy;
import menu.nextgen.launcher.fake.service.IJobServiceProxy;
import menu.nextgen.launcher.fake.service.ILauncherAppsProxy;
import menu.nextgen.launcher.fake.service.ILocationManagerProxy;
import menu.nextgen.launcher.fake.service.IMediaRouterServiceProxy;
import menu.nextgen.launcher.fake.service.IMediaSessionManagerProxy;
import menu.nextgen.launcher.fake.service.IAudioServiceProxy;
import menu.nextgen.launcher.fake.service.ISensorPrivacyManagerProxy;
import menu.nextgen.launcher.fake.service.ContentResolverProxy;
import menu.nextgen.launcher.fake.service.IMiuiSecurityManagerProxy;
import menu.nextgen.launcher.fake.service.SystemLibraryProxy;
import menu.nextgen.launcher.fake.service.ReLinkerProxy;
import menu.nextgen.launcher.fake.service.MediaRecorderProxy;
import menu.nextgen.launcher.fake.service.NetworkPermissionCompat;
import menu.nextgen.launcher.fake.service.AudioRecordProxy;
import menu.nextgen.launcher.fake.service.MediaRecorderClassProxy;
import menu.nextgen.launcher.fake.service.SQLiteDatabaseProxy;
import menu.nextgen.launcher.fake.service.ClassLoaderProxy;
import menu.nextgen.launcher.fake.service.FileSystemProxy;
import menu.nextgen.launcher.fake.service.GmsProxy;
import menu.nextgen.launcher.fake.service.LevelDbProxy;
import menu.nextgen.launcher.fake.service.DeviceIdProxy;
import menu.nextgen.launcher.fake.service.GoogleAccountManagerProxy;
import menu.nextgen.launcher.fake.service.AuthenticationProxy;
import menu.nextgen.launcher.fake.service.AndroidIdProxy;
import menu.nextgen.launcher.fake.service.AudioPermissionProxy;
import menu.nextgen.launcher.fake.service.NetworkPermissionCompat;

import menu.nextgen.launcher.fake.service.INetworkManagementServiceProxy;
import menu.nextgen.launcher.fake.service.INotificationManagerProxy;
import menu.nextgen.launcher.fake.service.IPackageManagerProxy;
import menu.nextgen.launcher.fake.service.IPermissionManagerProxy;
import menu.nextgen.launcher.fake.service.IPersistentDataBlockServiceProxy;
import menu.nextgen.launcher.fake.service.IPhoneSubInfoProxy;
import menu.nextgen.launcher.fake.service.IPowerManagerProxy;
import menu.nextgen.launcher.fake.service.ApkAssetsProxy;
import menu.nextgen.launcher.fake.service.ResourcesManagerProxy;
import menu.nextgen.launcher.fake.service.IShortcutManagerProxy;
import menu.nextgen.launcher.fake.service.IStorageManagerProxy;
import menu.nextgen.launcher.fake.service.IStorageStatsManagerProxy;
import menu.nextgen.launcher.fake.service.ISystemUpdateProxy;
import menu.nextgen.launcher.fake.service.ITelephonyManagerProxy;
import menu.nextgen.launcher.fake.service.ITelephonyRegistryProxy;
import menu.nextgen.launcher.fake.service.IUserManagerProxy;
import menu.nextgen.launcher.fake.service.IVibratorServiceProxy;
import menu.nextgen.launcher.fake.service.IVpnManagerProxy;
import menu.nextgen.launcher.fake.service.IWifiManagerProxy;
import menu.nextgen.launcher.fake.service.IWifiScannerProxy;
import menu.nextgen.launcher.fake.service.IWindowManagerProxy;
import menu.nextgen.launcher.fake.service.context.ContentServiceStub;
import menu.nextgen.launcher.fake.service.context.RestrictionsManagerStub;
import menu.nextgen.launcher.fake.service.libcore.OsStub;
import menu.nextgen.launcher.utils.Slog;
import menu.nextgen.launcher.utils.compat.BuildCompat;
import menu.nextgen.launcher.fake.service.ISettingsProviderProxy;
import menu.nextgen.launcher.fake.service.FeatureFlagUtilsProxy;
import menu.nextgen.launcher.fake.service.WorkManagerProxy;



public class HookManager {
    public static final String TAG = "HookManager";

    private static final HookManager sHookManager = new HookManager();

    private final Map<Class<?>, IInjectHook> mInjectors = new HashMap<>();

    public static HookManager get() {
        return sHookManager;
    }

    public void init() {
        if (NextGenCore.get().isCoreProcess() || NextGenCore.get().isServerProcess()) {
            addInjector(new IDisplayManagerProxy());
            addInjector(new OsStub());
            addInjector(new IActivityManagerProxy());
            addInjector(new IPackageManagerProxy());
            addInjector(new ITelephonyManagerProxy());
            addInjector(new HCallbackProxy());
            addInjector(new IAppOpsManagerProxy());
            addInjector(new INotificationManagerProxy());
            addInjector(new IAlarmManagerProxy());
            addInjector(new IAppWidgetManagerProxy());
            addInjector(new ContentServiceStub());
            addInjector(new IWindowManagerProxy());
            addInjector(new IUserManagerProxy());
            addInjector(new RestrictionsManagerStub());
            addInjector(new IMediaSessionManagerProxy());
            addInjector(new IAudioServiceProxy());
            addInjector(new ISensorPrivacyManagerProxy());
            addInjector(new ContentResolverProxy());
            addInjector(new SystemLibraryProxy());
            addInjector(new ReLinkerProxy());
            addInjector(new WorkManagerProxy());
            addInjector(new MediaRecorderProxy());
            addInjector(new AudioRecordProxy());
            addInjector(new IMiuiSecurityManagerProxy());
            addInjector(new ISettingsProviderProxy());
            addInjector(new FeatureFlagUtilsProxy());
            addInjector(new MediaRecorderClassProxy());
            addInjector(new SQLiteDatabaseProxy());
            addInjector(new ClassLoaderProxy());
            addInjector(new FileSystemProxy());
            addInjector(new GmsProxy());
            addInjector(new LevelDbProxy());
            addInjector(new DeviceIdProxy());
            addInjector(new GoogleAccountManagerProxy());
            addInjector(new AuthenticationProxy());
            addInjector(new AndroidIdProxy());
            addInjector(new AudioPermissionProxy());
            addInjector(new ILocationManagerProxy());
            addInjector(new IStorageManagerProxy());
            addInjector(new ILauncherAppsProxy());
            addInjector(new IJobServiceProxy());
            addInjector(new IAccessibilityManagerProxy());
            addInjector(new ITelephonyRegistryProxy());
            addInjector(new IDevicePolicyManagerProxy());
            addInjector(new IAccountManagerProxy());
            addInjector(new NetworkPermissionCompat());
            addInjector(new IConnectivityManagerProxy());
            addInjector(new IDnsResolverProxy());
                    addInjector(new IAttributionSourceProxy());
        addInjector(new IContentProviderProxy());
        addInjector(new ISettingsSystemProxy());
        addInjector(new ISystemSensorManagerProxy());
        
        
        addInjector(new IXiaomiAttributionSourceProxy());
        addInjector(new IXiaomiSettingsProxy());
        addInjector(new IXiaomiMiuiServicesProxy());
            addInjector(new IPhoneSubInfoProxy());
            addInjector(new IMediaRouterServiceProxy());
            addInjector(new IPowerManagerProxy());
            addInjector(new IContextHubServiceProxy());
            
            addInjector(new IVibratorServiceProxy());
            addInjector(new IPersistentDataBlockServiceProxy());
            addInjector(AppInstrumentation.get());
            
            addInjector(new IWifiManagerProxy());
            addInjector(new IWifiScannerProxy());
            addInjector(new ApkAssetsProxy());
            addInjector(new ResourcesManagerProxy());
            
            if (BuildCompat.isS()) {
                addInjector(new IActivityClientProxy(null));
                addInjector(new IVpnManagerProxy());
            }
            
            if (BuildCompat.isS()) {
                addInjector(new ISensitiveContentProtectionManagerProxy());
            }
            
            if (BuildCompat.isR()) {
                addInjector(new IPermissionManagerProxy());
            }
            
            if (BuildCompat.isQ()) {
                addInjector(new IActivityTaskManagerProxy());
            }
            
            if (BuildCompat.isPie()) {
                addInjector(new ISystemUpdateProxy());
            }
            
            if (BuildCompat.isOreo()) {
                addInjector(new IAutofillManagerProxy());
                addInjector(new IDeviceIdentifiersPolicyProxy());
                addInjector(new IStorageStatsManagerProxy());
            }
            
            if (BuildCompat.isN_MR1()) {
                addInjector(new IShortcutManagerProxy());
            }
            
            if (BuildCompat.isN()) {
                addInjector(new INetworkManagementServiceProxy());
            }
            
            if (BuildCompat.isM()) {
                addInjector(new IFingerprintManagerProxy());
                addInjector(new IGraphicsStatsProxy());
            }
            
            if (BuildCompat.isL()) {
                addInjector(new IJobServiceProxy());
            }
        }
        injectAll();
    }

    public void checkEnv(Class<?> clazz) {
        IInjectHook iInjectHook = mInjectors.get(clazz);
        if (iInjectHook != null && iInjectHook.isBadEnv()) {
            Log.d(TAG, "checkEnv: " + clazz.getSimpleName() + " is bad env");
            iInjectHook.injectHook();
        }
    }

    public void checkAll() {
        for (Class<?> aClass : mInjectors.keySet()) {
            IInjectHook iInjectHook = mInjectors.get(aClass);
            if (iInjectHook != null && iInjectHook.isBadEnv()) {
                Log.d(TAG, "checkEnv: " + aClass.getSimpleName() + " is bad env");
                iInjectHook.injectHook();
            }
        }
    }

    void addInjector(IInjectHook injectHook) {
        mInjectors.put(injectHook.getClass(), injectHook);
    }

    void injectAll() {
        for (IInjectHook value : mInjectors.values()) {
            try {
                Slog.d(TAG, "hook: " + value);
                value.injectHook();
            } catch (Exception e) {
                Slog.d(TAG, "hook error: " + value);
                
                handleHookError(value, e);
            }
        }
    }

    
    private void handleHookError(IInjectHook hook, Exception e) {
        String hookName = hook.getClass().getSimpleName();
        
        
        Slog.e(TAG, "Hook failed: " + hookName + " - " + e.getMessage(), e);
        
        
        if (hookName.contains("ActivityManager") || 
            hookName.contains("PackageManager") ||
            hookName.contains("WebView") ||
            hookName.contains("ContentProvider")) {
            
            Slog.w(TAG, "Critical hook failed: " + hookName + ", attempting recovery");
            
            try {
                
                if (hook.isBadEnv()) {
                    Slog.d(TAG, "Attempting to recover hook: " + hookName);
                    hook.injectHook();
                }
            } catch (Exception recoveryException) {
                Slog.e(TAG, "Hook recovery failed: " + hookName, recoveryException);
            }
        }
    }

    
    public boolean areCriticalHooksInstalled() {
        String[] criticalHooks = {
            "IActivityManagerProxy",
            "IPackageManagerProxy",
            "IContentProviderProxy"
        };
        
        for (String hookName : criticalHooks) {
            boolean found = false;
            for (Class<?> hookClass : mInjectors.keySet()) {
                if (hookClass.getSimpleName().equals(hookName)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                Slog.w(TAG, "Critical hook missing: " + hookName);
                return false;
            }
        }
        
        Slog.d(TAG, "All critical hooks are installed");
        return true;
    }

    
    public void reinitializeHooks() {
        Slog.d(TAG, "Reinitializing all hooks");
        
        
        mInjectors.clear();
        
        
        init();
        
        Slog.d(TAG, "Hook reinitialization completed");
    }
}

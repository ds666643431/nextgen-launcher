package menu.nextgen.launcher.core.system;

import android.os.IBinder;

import java.util.HashMap;
import java.util.Map;

import menu.nextgen.launcher.NextGenCore;
import menu.nextgen.launcher.core.system.accounts.BAccountManagerService;
import menu.nextgen.launcher.core.system.am.BActivityManagerService;
import menu.nextgen.launcher.core.system.am.BJobManagerService;
import menu.nextgen.launcher.core.system.location.BLocationManagerService;
import menu.nextgen.launcher.core.system.notification.BNotificationManagerService;
import menu.nextgen.launcher.core.system.os.BStorageManagerService;
import menu.nextgen.launcher.core.system.pm.BPackageManagerService;

import menu.nextgen.launcher.core.system.user.BUserManagerService;


public class ServiceManager {
    private static ServiceManager sServiceManager = null;
    public static final String ACTIVITY_MANAGER = "activity_manager";
    public static final String JOB_MANAGER = "job_manager";
    public static final String PACKAGE_MANAGER = "package_manager";
    public static final String STORAGE_MANAGER = "storage_manager";
    public static final String USER_MANAGER = "user_manager";

    public static final String ACCOUNT_MANAGER = "account_manager";
    public static final String LOCATION_MANAGER = "location_manager";
    public static final String NOTIFICATION_MANAGER = "notification_manager";

    private final Map<String, IBinder> mCaches = new HashMap<>();

    public static ServiceManager get() {
        if (sServiceManager == null) {
            synchronized (ServiceManager.class) {
                if (sServiceManager == null) {
                    sServiceManager = new ServiceManager();
                }
            }
        }
        return sServiceManager;
    }

    public static IBinder getService(String name) {
        return get().getServiceInternal(name);
    }

    private ServiceManager() {
        mCaches.put(ACTIVITY_MANAGER, BActivityManagerService.get());
        mCaches.put(JOB_MANAGER, BJobManagerService.get());
        mCaches.put(PACKAGE_MANAGER, BPackageManagerService.get());
        mCaches.put(STORAGE_MANAGER, BStorageManagerService.get());
        mCaches.put(USER_MANAGER, BUserManagerService.get());

        mCaches.put(ACCOUNT_MANAGER, BAccountManagerService.get());
        mCaches.put(LOCATION_MANAGER, BLocationManagerService.get());
        mCaches.put(NOTIFICATION_MANAGER, BNotificationManagerService.get());
    }

    public IBinder getServiceInternal(String name) {
        return mCaches.get(name);
    }

    public static void initNgManager() {
        NextGenCore.get().getService(ACTIVITY_MANAGER);
        NextGenCore.get().getService(JOB_MANAGER);
        NextGenCore.get().getService(PACKAGE_MANAGER);
        NextGenCore.get().getService(STORAGE_MANAGER);
        NextGenCore.get().getService(USER_MANAGER);

        NextGenCore.get().getService(ACCOUNT_MANAGER);
        NextGenCore.get().getService(LOCATION_MANAGER);
        NextGenCore.get().getService(NOTIFICATION_MANAGER);
    }
}

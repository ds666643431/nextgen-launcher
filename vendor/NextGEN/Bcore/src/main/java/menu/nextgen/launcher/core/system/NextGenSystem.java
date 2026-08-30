package menu.nextgen.launcher.core.system;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import menu.nextgen.launcher.NextGenCore;
import menu.nextgen.launcher.core.env.AppSystemEnv;
import menu.nextgen.launcher.core.env.BEnvironment;
import menu.nextgen.launcher.core.system.accounts.BAccountManagerService;
import menu.nextgen.launcher.core.system.am.BActivityManagerService;
import menu.nextgen.launcher.core.system.am.BJobManagerService;
import menu.nextgen.launcher.core.system.location.BLocationManagerService;
import menu.nextgen.launcher.core.system.notification.BNotificationManagerService;
import menu.nextgen.launcher.core.system.os.BStorageManagerService;
import menu.nextgen.launcher.core.system.pm.BPackageInstallerService;
import menu.nextgen.launcher.core.system.pm.BPackageManagerService;

import menu.nextgen.launcher.core.system.user.BUserHandle;
import menu.nextgen.launcher.core.system.user.BUserManagerService;
import menu.nextgen.launcher.entity.pm.InstallOption;
import menu.nextgen.launcher.utils.FileUtils;

import menu.nextgen.launcher.core.system.JarManager;


public class NextGenSystem {
    private static NextGenSystem sNextGenSystem;
    private final List<ISystemService> mServices = new ArrayList<>();
    private final static AtomicBoolean isStartup = new AtomicBoolean(false);

    public static NextGenSystem getSystem() {
        if (sNextGenSystem == null) {
            synchronized (NextGenSystem.class) {
                if (sNextGenSystem == null) {
                    sNextGenSystem = new NextGenSystem();
                }
            }
        }
        return sNextGenSystem;
    }

    public void startup() {
        if (isStartup.getAndSet(true))
            return;
        BEnvironment.load();

        mServices.add(BPackageManagerService.get());
        mServices.add(BUserManagerService.get());
        mServices.add(BActivityManagerService.get());
        mServices.add(BJobManagerService.get());
        mServices.add(BStorageManagerService.get());
        mServices.add(BPackageInstallerService.get());

        mServices.add(BProcessManagerService.get());
        mServices.add(BAccountManagerService.get());
        mServices.add(BLocationManagerService.get());
        mServices.add(BNotificationManagerService.get());

        for (ISystemService service : mServices) {
            service.systemReady();
        }

        List<String> preInstallPackages = AppSystemEnv.getPreInstallPackages();
        for (String preInstallPackage : preInstallPackages) {
            try {
                if (!BPackageManagerService.get().isInstalled(preInstallPackage, BUserHandle.USER_ALL)) {
                    PackageInfo packageInfo = NextGenCore.getPackageManager().getPackageInfo(preInstallPackage, 0);
                    BPackageManagerService.get().installPackageAsUser(packageInfo.applicationInfo.sourceDir, InstallOption.installBySystem(), BUserHandle.USER_ALL);
                }
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
        
        JarManager.getInstance().initializeAsync();
        
        
     
    }
}

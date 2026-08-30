package menu.nextgen.launcher.core;

import android.content.pm.PackageManager;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import menu.nextgen.launcher.NextGenCore;
import menu.nextgen.launcher.core.env.BEnvironment;
import menu.nextgen.launcher.entity.pm.InstallResult;


public class GmsCore {
    private static final String TAG = "GmsCore";

    private static final HashSet<String> GOOGLE_APP = new HashSet<>();
    private static final HashSet<String> GOOGLE_SERVICE = new HashSet<>();
    public static final String GMS_PKG = "com.google.android.gms";
    public static final String GSF_PKG = "com.google.android.gsf";
    public static final String VENDING_PKG = "com.android.vending";

    static {
        GOOGLE_APP.add(VENDING_PKG);
        GOOGLE_APP.add("com.google.android.play.games");
        GOOGLE_APP.add("com.google.android.wearable.app");
        GOOGLE_APP.add("com.google.android.wearable.app.cn");

        
        GOOGLE_SERVICE.add(GMS_PKG);
        GOOGLE_SERVICE.add(GSF_PKG);
        GOOGLE_SERVICE.add("com.google.android.gsf.login");
        GOOGLE_SERVICE.add("com.google.android.backuptransport");
        GOOGLE_SERVICE.add("com.google.android.backup");
        GOOGLE_SERVICE.add("com.google.android.configupdater");
        GOOGLE_SERVICE.add("com.google.android.syncadapters.contacts");
        GOOGLE_SERVICE.add("com.google.android.feedback");
        GOOGLE_SERVICE.add("com.google.android.onetimeinitializer");
        GOOGLE_SERVICE.add("com.google.android.partnersetup");
        GOOGLE_SERVICE.add("com.google.android.setupwizard");
        GOOGLE_SERVICE.add("com.google.android.syncadapters.calendar");
    }

    public static boolean isGoogleService(String packageName) {
        return GOOGLE_SERVICE.contains(packageName);
    }

    public static boolean isGoogleAppOrService(String str) {
        return GOOGLE_APP.contains(str) || GOOGLE_SERVICE.contains(str);
    }

    private static InstallResult installPackages(Set<String> list, int userId) {
        NextGenCore nextGenCore = NextGenCore.get();
        for (String packageName : list) {
            if (nextGenCore.isInstalled(packageName, userId)) {
                continue;
            }
            try {
                NextGenCore.getContext().getPackageManager().getApplicationInfo(packageName, 0);
            } catch (PackageManager.NameNotFoundException e) {
                
                continue;
            }
            InstallResult installResult = nextGenCore.installPackageAsUser(packageName, userId);
            if (installResult == null) {
                // A null result meant the install service was unreachable. Reporting it as
                // a failure beats the NullPointerException that used to escape here and
                // left GMS Manager showing "enabled" with nothing installed.
                return new InstallResult().installError("install failed for " + packageName);
            }
            if (!installResult.success) {
                return installResult;
            }
        }
        return new InstallResult();
    }

    private static void uninstallPackages(Set<String> list, int userId) {
        NextGenCore nextGenCore = NextGenCore.get();
        for (String packageName : list) {
            if (isLocallyInstalled(packageName)) {
                // Installed from a bundled APK (microG GmsCore / Companion), not cloned
                // from the host. GMS Manager only ever installs host clones, so it must
                // not remove these -- doing so silently wipes microG, and the microg_v3
                // pref then stops AppManager from putting it back.
                continue;
            }
            nextGenCore.uninstallPackageAsUser(packageName, userId);
        }
    }

    // Host clones are installed with FLAG_SYSTEM and keep the host's APK path, so no
    // base.apk is copied into the container. A locally installed APK always has one.
    // True here means "microG is what occupies this package name", not a host clone.
    public static boolean isLocallyInstalled(String packageName) {
        try {
            File baseApk = BEnvironment.getBaseApkDir(packageName);
            return baseApk != null && baseApk.exists();
        } catch (Throwable e) {
            return false;
        }
    }

    public static InstallResult installGApps(int userId) {
        Set<String> googleApps = new HashSet<>();

        googleApps.addAll(GOOGLE_SERVICE);
        googleApps.addAll(GOOGLE_APP);

        InstallResult installResult = installPackages(googleApps, userId);
        if (!installResult.success) {
            uninstallGApps(userId);
            return installResult;
        }
        return installResult;
    }

    public static void uninstallGApps(int userId) {
        uninstallPackages(GOOGLE_SERVICE, userId);
        uninstallPackages(GOOGLE_APP, userId);
    }

    public static void remove(String packageName) {
        GOOGLE_SERVICE.remove(packageName);
        GOOGLE_APP.remove(packageName);
    }


    public static boolean isSupportGms() {
        try {
            NextGenCore.getPackageManager().getPackageInfo(GMS_PKG, 0);
            return true;
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        return false;
    }

    public static boolean isInstalledGoogleService(int userId) {
        return NextGenCore.get().isInstalled(GMS_PKG, userId);
    }
}
// IBPackageInstallerService.aidl
package menu.nextgen.launcher.core.system.pm;

import menu.nextgen.launcher.core.system.pm.BPackageSettings;
import menu.nextgen.launcher.entity.pm.InstallOption;

// Declare any non-default types here with import statements

interface IBPackageInstallerService {
    int installPackageAsUser(in BPackageSettings ps, int userId);
    int uninstallPackageAsUser(in BPackageSettings ps, boolean removeApp, int userId);
    int clearPackage(in BPackageSettings ps, int userId);
    int updatePackage(in BPackageSettings ps);
}

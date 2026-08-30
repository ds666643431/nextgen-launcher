package menu.nextgen.launcher.core.system.pm.installer;

import menu.nextgen.launcher.core.env.BEnvironment;
import menu.nextgen.launcher.core.system.pm.BPackageSettings;
import menu.nextgen.launcher.entity.pm.InstallOption;
import menu.nextgen.launcher.utils.FileUtils;


public class RemoveUserExecutor implements Executor {

    @Override
    public int exec(BPackageSettings ps, InstallOption option, int userId) {
        String packageName = ps.pkg.packageName;
        
        FileUtils.deleteDir(BEnvironment.getDataDir(packageName, userId));
        FileUtils.deleteDir(BEnvironment.getDeDataDir(packageName, userId));
        FileUtils.deleteDir(BEnvironment.getExternalDataDir(packageName, userId));
        return 0;
    }
}

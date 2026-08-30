package menu.nextgen.launcher.core.system.pm.installer;

import menu.nextgen.launcher.core.env.BEnvironment;
import menu.nextgen.launcher.core.system.pm.BPackageSettings;
import menu.nextgen.launcher.entity.pm.InstallOption;
import menu.nextgen.launcher.utils.FileUtils;


public class RemoveAppExecutor implements Executor {
    @Override
    public int exec(BPackageSettings ps, InstallOption option, int userId) {
        FileUtils.deleteDir(BEnvironment.getAppDir(ps.pkg.packageName));
        return 0;
    }
}

package menu.nextgen.launcher.proxy;

import java.util.Locale;

import menu.nextgen.launcher.NextGenCore;


public class ProxyManifest {
    public static final int FREE_COUNT = 50;

    public static boolean isProxy(String msg) {
        return getBindProvider().equals(msg) || msg.contains("proxy_content_provider_");
    }

    public static String getBindProvider() {
        return NextGenCore.getHostPkg() + ".appcore.SystemCallProvider";
    }

    public static String getProxyAuthorities(int index) {
        return String.format(Locale.CHINA, "%s.proxy_content_provider_%d", NextGenCore.getHostPkg(), index);
    }

    public static String getProxyPendingActivity(int index) {
        return String.format(Locale.CHINA, "menu.nextgen.launcher.proxy.ProxyPendingActivity$P%d", index);
    }

    public static String getProxyActivity(int index) {
        return String.format(Locale.CHINA, "menu.nextgen.launcher.proxy.ProxyActivity$P%d", index);
    }

    public static String TransparentProxyActivity(int index) {
        return String.format(Locale.CHINA, "menu.nextgen.launcher.proxy.TransparentProxyActivity$P%d", index);
    }

    public static String getProxyService(int index) {
        return String.format(Locale.CHINA, "menu.nextgen.launcher.proxy.ProxyService$P%d", index);
    }

    public static String getProxyJobService(int index) {
        return String.format(Locale.CHINA, "menu.nextgen.launcher.proxy.ProxyJobService$P%d", index);
    }

    public static String getProxyFileProvider() {
        return NextGenCore.getHostPkg() + ".appcore.FileProvider";
    }

    public static String getProxyReceiver() {
        return NextGenCore.getHostPkg() + ".stub_receiver";
    }

    public static String getProcessName(int bPid) {
        return NextGenCore.getHostPkg() + ":p" + bPid;
    }
}

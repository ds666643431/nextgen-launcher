package menu.nextgen.launcher.fake.service;

import java.lang.reflect.Method;

import ref.android.os.BRServiceManager;
import ref.android.view.BRIGraphicsStatsStub;
import menu.nextgen.launcher.fake.hook.BinderInvocationStub;
import menu.nextgen.launcher.fake.hook.MethodHook;
import menu.nextgen.launcher.fake.hook.ProxyMethod;
import menu.nextgen.launcher.utils.MethodParameterUtils;


public class IGraphicsStatsProxy extends BinderInvocationStub {

    public IGraphicsStatsProxy() {
        super(BRServiceManager.get().getService("graphicsstats"));
    }

    @Override
    protected Object getWho() {
        return BRIGraphicsStatsStub.get().asInterface(BRServiceManager.get().getService("graphicsstats"));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService("graphicsstats");
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ProxyMethod("requestBufferForProcess")
    public static class RequestBufferForProcess extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceFirstAppPkg(args);
            return method.invoke(who, args);
        }
    }
}

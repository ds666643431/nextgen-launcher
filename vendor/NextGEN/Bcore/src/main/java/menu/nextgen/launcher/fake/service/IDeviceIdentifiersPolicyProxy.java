package menu.nextgen.launcher.fake.service;


import java.lang.reflect.Method;

import ref.android.os.BRIDeviceIdentifiersPolicyServiceStub;
import ref.android.os.BRServiceManager;
import menu.nextgen.launcher.NextGenCore;
import menu.nextgen.launcher.fake.hook.BinderInvocationStub;
import menu.nextgen.launcher.fake.hook.MethodHook;
import menu.nextgen.launcher.fake.hook.ProxyMethod;
import menu.nextgen.launcher.utils.Md5Utils;


public class IDeviceIdentifiersPolicyProxy extends BinderInvocationStub {

    public IDeviceIdentifiersPolicyProxy() {
        super(BRServiceManager.get().getService("device_identifiers"));
    }

    @Override
    protected Object getWho() {
        return BRIDeviceIdentifiersPolicyServiceStub.get().asInterface(BRServiceManager.get().getService("device_identifiers"));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService("device_identifiers");
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ProxyMethod("getSerialForPackage")
    public static class x extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {


            return Md5Utils.md5(NextGenCore.getHostPkg());
        }
    }
}

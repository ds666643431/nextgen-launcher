package menu.nextgen.launcher.fake.service.base;

import java.lang.reflect.Method;

import menu.nextgen.launcher.NextGenCore;
import menu.nextgen.launcher.app.BActivityThread;
import menu.nextgen.launcher.fake.hook.MethodHook;


public class UidMethodProxy extends MethodHook {
    private final int index;
    private final String name;

    public UidMethodProxy(String name, int index) {
        this.index = index;
        this.name = name;
    }

    @Override
    protected String getMethodName() {
        return name;
    }

    @Override
    protected Object hook(Object who, Method method, Object[] args) throws Throwable {
        int uid = (int) args[index];
        if (uid == BActivityThread.getBUid()) {
            args[index] = NextGenCore.getHostUid();
        }
        return method.invoke(who, args);
    }
}

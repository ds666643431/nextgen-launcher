package ref.android.app.admin;

import android.os.IBinder;
import android.os.IInterface;

import menu.nextgen.reflect.annotation.BClassName;
import menu.nextgen.reflect.annotation.BStaticMethod;

@BClassName("android.app.admin.IDevicePolicyManager")
public interface IDevicePolicyManager {
    @BClassName("android.app.admin.IDevicePolicyManager$Stub")
    interface Stub {
        @BStaticMethod
        IInterface asInterface(IBinder IBinder0);
    }
}

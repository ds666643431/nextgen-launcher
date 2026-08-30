package ref.android.os;

import android.os.IBinder;
import android.os.IInterface;

import menu.nextgen.reflect.annotation.BClassName;
import menu.nextgen.reflect.annotation.BStaticMethod;


@BClassName("android.os.IVibratorManagerService")
public interface IVibratorManagerService {

    @BClassName("android.os.IVibratorManagerService$Stub")
    interface Stub {
        @BStaticMethod
        IInterface asInterface(IBinder IBinder0);
    }
}

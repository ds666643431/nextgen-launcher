package ref.com.android.internal.os;

import android.os.IBinder;
import android.os.IInterface;

import menu.nextgen.reflect.annotation.BClassName;
import menu.nextgen.reflect.annotation.BStaticMethod;

@BClassName("com.android.internal.os.IDropBoxManagerService")
public interface IDropBoxManagerService {
    @BClassName("com.android.internal.os.IDropBoxManagerService$Stub")
    interface Stub {
        @BStaticMethod
        IInterface asInterface(IBinder IBinder0);
    }
}

package ref.com.android.internal.telecom;

import android.os.IBinder;
import android.os.IInterface;

import menu.nextgen.reflect.annotation.BClassName;
import menu.nextgen.reflect.annotation.BStaticMethod;

@BClassName("com.android.internal.telecom.ITelecomService")
public interface ITelecomService {
    @BClassName("com.android.internal.telecom.ITelecomService$Stub")
    interface Stub {
        @BStaticMethod
        IInterface asInterface(IBinder IBinder0);
    }
}

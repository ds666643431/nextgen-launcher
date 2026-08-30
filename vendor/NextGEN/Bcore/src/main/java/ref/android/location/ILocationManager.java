package ref.android.location;

import android.os.IBinder;
import android.os.IInterface;

import menu.nextgen.reflect.annotation.BClassName;
import menu.nextgen.reflect.annotation.BStaticMethod;

@BClassName("android.location.ILocationManager")
public interface ILocationManager {
    @BClassName("android.location.ILocationManager$Stub")
    interface Stub {
        @BStaticMethod
        IInterface asInterface(IBinder IBinder0);
    }
}

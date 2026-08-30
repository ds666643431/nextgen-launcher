package ref.android.app.servertransaction;

import android.os.IBinder;

import java.util.List;

import menu.nextgen.reflect.annotation.BClassName;
import menu.nextgen.reflect.annotation.BField;

@BClassName("android.app.servertransaction.ClientTransaction")
public interface ClientTransaction {
    @BField
    List<Object> mActivityCallbacks();

    @BField
    IBinder mActivityToken();

    @BField
    Object mLifecycleStateRequest();
}

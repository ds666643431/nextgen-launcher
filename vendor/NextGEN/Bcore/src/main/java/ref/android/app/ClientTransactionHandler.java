package ref.android.app;

import android.os.IBinder;

import menu.nextgen.reflect.annotation.BClassName;
import menu.nextgen.reflect.annotation.BMethod;

@BClassName("android.app.ClientTransactionHandler")
public interface ClientTransactionHandler {
    @BMethod
    Object getActivityClient(IBinder IBinder0);
}

package ref.android.accounts;

import android.os.IBinder;
import android.os.IInterface;

import menu.nextgen.reflect.annotation.BClassName;
import menu.nextgen.reflect.annotation.BStaticMethod;

@BClassName("android.accounts.IAccountManager")
public interface IAccountManager {
    @BClassName("android.accounts.IAccountManager$Stub")
    interface Stub {
        @BStaticMethod
        IInterface asInterface(IBinder IBinder0);
    }
}

package ref.android.app;

import android.os.IBinder;
import android.os.IInterface;

import menu.nextgen.reflect.annotation.BClassName;
import menu.nextgen.reflect.annotation.BStaticMethod;

@BClassName("android.app.ApplicationThreadNative")
public interface ApplicationThreadNative {
    @BStaticMethod
    IInterface asInterface(IBinder IBinder0);
}

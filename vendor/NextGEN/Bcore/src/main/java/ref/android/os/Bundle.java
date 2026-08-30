package ref.android.os;

import android.os.IBinder;

import menu.nextgen.reflect.annotation.BClassName;
import menu.nextgen.reflect.annotation.BMethod;

@BClassName("android.os.Bundle")
public interface Bundle {
    @BMethod
    IBinder getIBinder(String String0);

    @BMethod
    void putIBinder(String String0, IBinder IBinder1);
}

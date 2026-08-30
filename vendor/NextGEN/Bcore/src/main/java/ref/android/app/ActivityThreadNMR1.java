package ref.android.app;

import android.os.IBinder;

import java.util.List;

import menu.nextgen.reflect.annotation.BClassName;
import menu.nextgen.reflect.annotation.BMethod;

@BClassName("android.app.ActivityThread")
public interface ActivityThreadNMR1 {
    @BMethod
    void performNewIntents(IBinder IBinder0, List List1, boolean boolean2);
}

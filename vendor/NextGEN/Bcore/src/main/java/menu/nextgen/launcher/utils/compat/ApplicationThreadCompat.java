package menu.nextgen.launcher.utils.compat;

import android.os.IBinder;
import android.os.IInterface;

import ref.android.app.BRApplicationThreadNative;
import ref.android.app.BRIApplicationThreadOreoStub;

public class ApplicationThreadCompat {

    public static IInterface asInterface(IBinder binder) {
        if (BuildCompat.isOreo()) {
            return BRIApplicationThreadOreoStub.get().asInterface(binder);
        }
        return BRApplicationThreadNative.get().asInterface(binder);
    }
}

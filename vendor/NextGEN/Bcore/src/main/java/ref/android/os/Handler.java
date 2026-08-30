package ref.android.os;

import android.os.Handler.Callback;

import menu.nextgen.reflect.annotation.BClassName;
import menu.nextgen.reflect.annotation.BField;

@BClassName("android.os.Handler")
public interface Handler {
    @BField
    Callback mCallback();
}

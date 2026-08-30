package ref.android.app;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.IBinder;

import menu.nextgen.reflect.annotation.BClassName;
import menu.nextgen.reflect.annotation.BField;
import menu.nextgen.reflect.annotation.BMethod;

@BClassName("android.app.Activity")
public interface Activity {
    @BField
    ActivityInfo mActivityInfo();

    @BField
    String mEmbeddedID();

    @BField
    boolean mFinished();

    @BField
    android.app.Activity mParent();

    @BField
    int mResultCode();

    @BField
    Intent mResultData();

    @BField
    IBinder mToken();

    @BMethod
    void onActivityResult(int int0, int int1, Intent Intent2);
}

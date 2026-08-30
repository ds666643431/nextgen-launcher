package ref.android.net.wifi;

import android.os.IInterface;

import menu.nextgen.reflect.annotation.BClassName;
import menu.nextgen.reflect.annotation.BField;
import menu.nextgen.reflect.annotation.BStaticField;

@BClassName("android.net.wifi.WifiManager")
public interface WifiManager {
    @BStaticField
    IInterface sService();

    @BField
    IInterface mService();
}

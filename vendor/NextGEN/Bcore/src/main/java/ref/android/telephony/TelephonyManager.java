package ref.android.telephony;

import android.os.IInterface;

import menu.nextgen.reflect.annotation.BClassName;
import menu.nextgen.reflect.annotation.BStaticField;
import menu.nextgen.reflect.annotation.BStaticMethod;


@BClassName("android.telephony.TelephonyManager")
public interface TelephonyManager {

    @BStaticMethod
    Object getSubscriberInfoService();

    @BStaticField
    boolean sServiceHandleCacheEnabled();

    @BStaticField
    IInterface sIPhoneSubInfo();
}

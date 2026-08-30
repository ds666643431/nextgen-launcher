package ref.android.os;


import menu.nextgen.reflect.annotation.BClassName;
import menu.nextgen.reflect.annotation.BStaticField;
import menu.nextgen.reflect.annotation.BStaticMethod;

@BClassName("android.os.StrictMode")
public interface StrictMode {
    @BStaticField
    int DETECT_VM_FILE_URI_EXPOSURE();

    @BStaticField
    int PENALTY_DEATH_ON_FILE_URI_EXPOSURE();

    @BStaticField
    int sVmPolicyMask();

    @BStaticMethod
    void disableDeathOnFileUriExposure();
}

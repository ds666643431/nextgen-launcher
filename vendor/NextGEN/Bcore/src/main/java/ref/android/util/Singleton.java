package ref.android.util;


import menu.nextgen.reflect.annotation.BClassName;
import menu.nextgen.reflect.annotation.BField;
import menu.nextgen.reflect.annotation.BMethod;

@BClassName("android.util.Singleton")
public interface Singleton {
    @BField
    Object mInstance();

    @BMethod
    Object get();
}

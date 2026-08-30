package ref.android.content.res;

import android.content.res.Configuration;
import android.util.DisplayMetrics;

import menu.nextgen.reflect.annotation.BClassName;
import menu.nextgen.reflect.annotation.BConstructor;
import menu.nextgen.reflect.annotation.BMethod;

@BClassName("android.content.res.AssetManager")
public interface AssetManager {
    @BConstructor
    android.content.res.AssetManager _new();

    @BMethod
    Integer addAssetPath(String String0);

    @BMethod
    Configuration getConfiguration();

    @BMethod
    DisplayMetrics getDisplayMetrics();
}

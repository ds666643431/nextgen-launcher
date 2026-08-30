package ref.android.app;


import menu.nextgen.reflect.annotation.BClassName;
import menu.nextgen.reflect.annotation.BField;
import menu.nextgen.reflect.annotation.BStaticField;

@BClassName("android.app.WallpaperManager")
public interface WallpaperManager {
    @BStaticField
    Object sGlobals();

    @BClassName("android.app.WallpaperManager$Globals")
    interface Globals {
        @BField
        Object mService();
    }
}

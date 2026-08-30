package ref.android.view;

import android.graphics.Bitmap;

import menu.nextgen.reflect.annotation.BClassName;
import menu.nextgen.reflect.annotation.BStaticMethod;

@BClassName("android.view.SurfaceControl")
public interface SurfaceControl {
    @BStaticMethod
    Bitmap screnshot(int int0, int int1);
}

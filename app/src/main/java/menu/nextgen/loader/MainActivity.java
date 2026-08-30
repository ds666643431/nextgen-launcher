package menu.nextgen.loader;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.androidgamesdk.GameActivity;

import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

/**
 * Minimal GameActivity glue. This is the ONLY authored Java in the loader UI —
 * everything drawn on screen is Rust + egui inside libloader.so. The engine
 * (M2) will add its own Java, but the UI stays native.
 */
public class MainActivity extends GameActivity {

    static {
        // libloader.so exports android_main (via winit's android-game-activity glue).
        System.loadLibrary("loader");
    }

    private void hideSystemUI() {
        getWindow().getAttributes().layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        View decorView = getWindow().getDecorView();
        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(getWindow(), decorView);
        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        hideSystemUI();
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
    }
}

package menu.nextgen.launcher.core;

import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Pattern;

import menu.nextgen.launcher.NextGenCore;
import menu.nextgen.launcher.app.BActivityThread;
import menu.nextgen.launcher.core.env.BEnvironment;

/**
 * Bridges a guest app's Facebook login through the host's WebView.
 *
 * A guest's Facebook SDK opens its authorize URL in a Custom Tab or browser. Inside the
 * container there is no browser, and the Facebook app is hidden, so that would dead-end.
 * Instead the host opens the URL in its own WebView -- which already carries the Facebook
 * session cookies from "Connect Facebook" -- and writes the resulting redirect URI to a
 * file in the guest's data dir. This class polls for that file and feeds the URI back into
 * the guest's Facebook SDK, which performs its own token exchange.
 *
 * We are only a courier for the redirect string: no token is parsed, stored, or uploaded,
 * and no Facebook app id is ever needed.
 */
public class FacebookOAuth {

    private static final String TAG = "FacebookOAuth";

    private static final String ONI_DIR = "__oni";
    private static final String REDIRECT_FILE = "fb_redirect";

    private static final String HOST_ACTIVITY =
            "menu.nextgen.launchera.view.login.WebLoginActivity";

    /** The Custom Tab shim the Facebook SDK launches; our cue to start watching. */
    private static final String FB_CUSTOM_TAB = "com.facebook.CustomTabMainActivity";

    private static final String ACTION_REFRESH = "CustomTabMainActivity.action_refresh";
    private static final String EXTRA_URL = "CustomTabMainActivity.extra_url";

    /** Facebook returns to fbconnect:// or fb<appid>://. Match the shape, not an id. */
    private static final Pattern FB_SCHEME = Pattern.compile("fb\\d+");

    private static final int POLL_ATTEMPTS = 300;
    private static final long POLL_INTERVAL_MS = 200L;

    private static volatile boolean sWatching;

    private FacebookOAuth() {
    }

    public static boolean isFbRedirectUrl(String url) {
        if (url == null) {
            return false;
        }
        int idx = url.indexOf("://");
        if (idx <= 0) {
            return false;
        }
        String scheme = url.substring(0, idx).toLowerCase();
        return "fbconnect".equals(scheme) || FB_SCHEME.matcher(scheme).matches();
    }

    /** True when this activity is the Facebook SDK's Custom Tab shim. */
    public static boolean isFbCustomTab(String activityName) {
        return FB_CUSTOM_TAB.equals(activityName);
    }

    /**
     * Intercept a guest's attempt to open an http(s) URL in a browser and route it to the
     * host WebView instead.
     *
     * @return true when the intent was consumed and the caller should not start it.
     */
    public static boolean redirectOAuthBrowser(Intent intent) {
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction())) {
            return false;
        }
        Uri data = intent.getData();
        if (data == null) {
            return false;
        }
        String scheme = data.getScheme();
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            return false;
        }
        try {
            Intent host = new Intent();
            host.setClassName(NextGenCore.getHostPkg(), HOST_ACTIVITY);
            host.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            host.putExtra("oni_url", data.toString());
            host.putExtra("oni_pkg", BActivityThread.getAppPackageName());
            host.putExtra("oni_user", BActivityThread.getUserId());
            NextGenCore.getContext().startActivity(host);
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "could not open the host web login: " + t);
            return false;
        }
    }

    /**
     * Start watching for the captured redirect. Safe to call repeatedly -- only one watcher
     * runs at a time.
     *
     * @param customTabActivity the guest's live CustomTabMainActivity, to deliver into.
     */
    public static void armRedirectWatcher(final Object customTabActivity) {
        if (sWatching) {
            return;
        }
        sWatching = true;

        final String pkg = BActivityThread.getAppPackageName();
        final int userId = BActivityThread.getUserId();

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File file = new File(
                            new File(BEnvironment.getDataFilesDir(pkg, userId), ONI_DIR),
                            REDIRECT_FILE);
                    for (int i = 0; i < POLL_ATTEMPTS; i++) {
                        if (file.exists()) {
                            String url = read(file);
                            // Delete first: a stale file would re-fire on the next login.
                            file.delete();
                            if (isFbRedirectUrl(url)) {
                                deliver(customTabActivity, url);
                            }
                            return;
                        }
                        Thread.sleep(POLL_INTERVAL_MS);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Throwable t) {
                    Log.e(TAG, "redirect watcher failed: " + t);
                } finally {
                    sWatching = false;
                }
            }
        }, "oni-fb-redirect");
        thread.setDaemon(true);
        thread.start();
    }

    private static String read(File file) {
        try {
            return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).trim();
        } catch (Throwable t) {
            Log.e(TAG, "could not read the redirect file: " + t);
            return null;
        }
    }

    /**
     * Hand the redirect to the Facebook SDK exactly as a real Custom Tab return would.
     * Reflection throughout: the SDK is the guest's code, not ours to link against.
     */
    private static void deliver(final Object customTabActivity, final String url) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    // The SDK closes itself when this flag is set; clear it so our
                    // onNewIntent is not raced by the activity finishing.
                    try {
                        java.lang.reflect.Field field = customTabActivity.getClass()
                                .getDeclaredField("shouldCloseCustomTab");
                        field.setAccessible(true);
                        field.setBoolean(customTabActivity, false);
                    } catch (Throwable ignored) {
                    }

                    Intent intent = new Intent(ACTION_REFRESH);
                    intent.putExtra(EXTRA_URL, url);

                    java.lang.reflect.Method onNewIntent = findMethod(
                            customTabActivity.getClass(), "onNewIntent", Intent.class);
                    if (onNewIntent != null) {
                        onNewIntent.setAccessible(true);
                        onNewIntent.invoke(customTabActivity, intent);
                        Log.i(TAG, "delivered facebook redirect to the guest sdk");
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "could not deliver the redirect: " + t);
                }
            }
        });
    }

    private static java.lang.reflect.Method findMethod(Class<?> clazz, String name,
                                                       Class<?>... args) {
        Class<?> c = clazz;
        while (c != null) {
            try {
                return c.getDeclaredMethod(name, args);
            } catch (NoSuchMethodException e) {
                c = c.getSuperclass();
            }
        }
        return null;
    }
}

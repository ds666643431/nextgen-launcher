package menu.nextgen.launcher.core;

import android.accounts.Account;
import android.content.Context;
import android.util.Log;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import menu.nextgen.launcher.app.BActivityThread;

/**
 * Creates the account's Play Games player profile.
 *
 * Sign-in gets as far as a valid games_lite token and then dies on
 * GET /games/v1/players/me returning 404 -- the account has no gamer profile. A real
 * device creates one during the first Play Games sign-in; microG ships the same call
 * (ExtensionsKt.registerForGames) but never invokes it from the sign-in path, so the
 * account stays profile-less forever and every game reports "sign-in required".
 *
 * Registration is asynchronous and happens too late to rescue the attempt that triggered
 * it, so the first launch still fails; the profile exists from the next one on.
 *
 * All reflective: this is microG's code, loadable only inside the container's GMS process.
 */
public class GamesProfile {

    private static final String TAG = "GamesProfile";

    private static final String CLS_EXTENSIONS = "org.microg.gms.games.ExtensionsKt";
    private static final String CLS_CONTINUATION = "kotlin.coroutines.Continuation";
    private static final String CLS_EMPTY_CONTEXT = "kotlin.coroutines.EmptyCoroutineContext";
    private static final String CLS_REQUEST_QUEUE = "com.android.volley.RequestQueue";

    private static final String ACCOUNT_TYPE = "com.google";

    /** Registration is per-account and permanent; once per process is plenty. */
    private static volatile boolean sAttempted;

    private GamesProfile() {
    }

    /**
     * Register the container's Google account for Play Games, once.
     *
     * Safe to call from anywhere in the GMS process; does nothing if there is no account
     * or microG's helper is not where we expect it.
     */
    public static void ensureRegistered() {
        if (sAttempted) {
            return;
        }
        sAttempted = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                register();
            }
        }, "oni-games-register").start();
    }

    private static void register() {
        try {
            Context context = BActivityThread.getApplication();
            if (context == null) {
                return;
            }
            Account account = firstGoogleAccount(context);
            if (account == null) {
                Log.d(TAG, "no google account in the container; nothing to register");
                return;
            }

            ClassLoader loader = context.getClassLoader();
            Class<?> extensions = loader.loadClass(CLS_EXTENSIONS);
            Class<?> continuationClass = loader.loadClass(CLS_CONTINUATION);
            Class<?> requestQueueClass = loader.loadClass(CLS_REQUEST_QUEUE);

            Object continuation = newContinuation(loader, continuationClass);

            // registerForGames$default(context, account, queue, continuation, mask, marker)
            // mask bit 4 == "queue was not supplied", so microG builds its own.
            Method register = extensions.getDeclaredMethod("registerForGames$default",
                    Context.class, Account.class, requestQueueClass, continuationClass,
                    int.class, Object.class);
            register.setAccessible(true);
            Log.i(TAG, "registering " + account.name + " for play games");
            register.invoke(null, context, account, null, continuation, 4, null);
        } catch (Throwable t) {
            Log.w(TAG, "could not register for play games: " + t);
        }
    }

    private static Account firstGoogleAccount(Context context) {
        try {
            android.accounts.AccountManager manager =
                    android.accounts.AccountManager.get(context);
            Account[] accounts = manager.getAccountsByType(ACCOUNT_TYPE);
            return accounts != null && accounts.length > 0 ? accounts[0] : null;
        } catch (Throwable t) {
            Log.w(TAG, "account lookup failed: " + t);
            return null;
        }
    }

    /**
     * Minimal kotlin.coroutines.Continuation. The call is fire-and-forget -- we only need
     * somewhere for the coroutine to land, and a context for it to run in.
     */
    private static Object newContinuation(ClassLoader loader, Class<?> continuationClass)
            throws Exception {
        final Object emptyContext =
                loader.loadClass(CLS_EMPTY_CONTEXT).getField("INSTANCE").get(null);
        return Proxy.newProxyInstance(loader, new Class[]{continuationClass},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        String name = method.getName();
                        if ("getContext".equals(name)) {
                            return emptyContext;
                        }
                        if ("resumeWith".equals(name)) {
                            Log.i(TAG, "play games registration finished: "
                                    + (args != null && args.length > 0 ? args[0] : "?"));
                        }
                        return null;
                    }
                });
    }
}

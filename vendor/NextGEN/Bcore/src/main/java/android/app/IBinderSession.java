package android.app;

/**
 * Compile-time stub for Android 17 (API 37)'s {@code android.app.IBinderSession},
 * which is absent from compileSdk 35's android.jar. The real interface is on the
 * boot classpath at runtime and shadows this shell (ART never loads this copy).
 *
 * It exists only so {@link menu.nextgen.launcher.fake.delegate.ServiceConnectionDelegate}
 * can declare the API-37 {@code connected(ComponentName, IBinder, IBinderSession, boolean)}
 * overload the framework now calls; without an implementation of that method the
 * guest crashes with AbstractMethodError on the first service bind.
 */
public interface IBinderSession {
}

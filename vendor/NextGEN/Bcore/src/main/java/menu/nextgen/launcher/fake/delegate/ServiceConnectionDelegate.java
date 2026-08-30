package menu.nextgen.launcher.fake.delegate;

import android.app.IServiceConnection;
import android.content.ComponentName;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import menu.nextgen.launcher.core.GamesShim;

/**
 * Proxies the guest app's IServiceConnection to the real system.
 *
 * The framework's IServiceConnection.connected(...) signature changed across
 * Android versions:
 *   < 8   : connected(ComponentName, IBinder)
 *   8..16 : connected(ComponentName, IBinder, boolean dead)
 *   17+   : connected(ComponentName, IBinder, IBinderSession, boolean dead)   // API 37
 * We implement every overload and forward to the guest connection by reflection,
 * so a single build works on all of them (fixes the API-37 AbstractMethodError).
 */
public class ServiceConnectionDelegate extends IServiceConnection.Stub {
    private static final Map<IBinder, ServiceConnectionDelegate> sServiceConnectDelegate = new HashMap<>();
    private final IServiceConnection mConn;
    private final ComponentName mComponentName;

    /** Set when this bind targets the Play Games service, so the binder gets shimmed. */
    private boolean mGamesBind;

    private ServiceConnectionDelegate(IServiceConnection mConn, ComponentName targetComponent) {
        this.mConn = mConn;
        this.mComponentName = targetComponent;
    }

    public static ServiceConnectionDelegate getDelegate(IBinder iBinder) {
        return sServiceConnectDelegate.get(iBinder);
    }

    public static IServiceConnection createProxy(IServiceConnection base, Intent intent) {
        final IBinder iBinder = base.asBinder();
        ServiceConnectionDelegate delegate = sServiceConnectDelegate.get(iBinder);
        if (delegate == null) {
            try {
                iBinder.linkToDeath(new IBinder.DeathRecipient() {
                    @Override
                    public void binderDied() {
                        sServiceConnectDelegate.remove(iBinder);
                        iBinder.unlinkToDeath(this, 0);
                    }
                }, 0);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            delegate = new ServiceConnectionDelegate(base, intent.getComponent());
            sServiceConnectDelegate.put(iBinder, delegate);
        }
        delegate.mGamesBind = GamesShim.isGamesBind(intent);
        return delegate;
    }

    // Android < 8
    @Override
    public void connected(ComponentName name, IBinder service) throws RemoteException {
        forward(service, null, false);
    }

    // Android 8..16
    public void connected(ComponentName name, IBinder service, boolean dead) throws RemoteException {
        forward(service, null, dead);
    }

    // Android 17 / API 37 (android.app.IBinderSession is a compile-time stub here).
    // Intentionally NOT @Override: the method is absent in compileSdk < 37, but at
    // runtime on A17 this satisfies IServiceConnection.Stub's new abstract method.
    public void connected(ComponentName name, IBinder service, android.app.IBinderSession session, boolean dead) throws RemoteException {
        forward(service, session, dead);
    }

    private void forward(IBinder service, Object session, boolean dead) throws RemoteException {
        if (mGamesBind) {
            service = GamesShim.wrap(service);
        }
        Method m2 = null, m3 = null, m4 = null;
        for (Method mm : mConn.getClass().getMethods()) {
            if (!"connected".equals(mm.getName())) continue;
            switch (mm.getParameterTypes().length) {
                case 4: m4 = mm; break;
                case 3: m3 = mm; break;
                case 2: m2 = mm; break;
                default: break;
            }
        }
        try {
            if (m4 != null) { m4.setAccessible(true); m4.invoke(mConn, mComponentName, service, session, dead); return; }
            if (m3 != null) { m3.setAccessible(true); m3.invoke(mConn, mComponentName, service, dead); return; }
            if (m2 != null) { m2.setAccessible(true); m2.invoke(mConn, mComponentName, service); return; }
        } catch (Throwable t) {
            // InvocationTargetException wraps any RemoteException from the guest conn.
            t.printStackTrace();
        }
        // Last resort: original behavior.
        mConn.connected(mComponentName, service);
    }
}

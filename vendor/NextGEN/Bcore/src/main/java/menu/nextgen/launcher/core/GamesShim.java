package menu.nextgen.launcher.core;

import android.content.Intent;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;

import java.util.ArrayList;

/**
 * Translates between the modern Play Games client and microG's Games service.
 *
 * play-services-games v2 asks for server-side access with the SCOPED transaction and
 * expects the scoped reply. microG only implements the older unscoped pair. Left alone the
 * two never agree, sign-in resolves to nothing and the game reports DEVELOPER_ERROR even
 * though the OAuth token was granted.
 *
 * The chain wraps four binders in sequence, each one only rewriting the transaction it
 * cares about and passing everything else straight through:
 *
 *   Broker       getService            -> substitutes our InitCallback
 *   InitCallback onPostInitComplete    -> substitutes our Games wrapper
 *   Games        SERVER_ACCESS_SCOPED  -> downgrades to SERVER_ACCESS for microG
 *   AuthCallback ON_AUTH_CODE          -> upgrades to ON_AUTH_CODE_SCOPED for the client
 */
public class GamesShim {

    private static final String TAG = "GamesShim";

    public static final String BIND_ACTION = "com.google.android.gms.games.service.START";

    private static final String BROKER = "com.google.android.gms.common.internal.IGmsServiceBroker";
    private static final String GAMES = "com.google.android.gms.games.internal.IGamesService";
    private static final String GAMES_CB = "com.google.android.gms.games.internal.IGamesCallbacks";
    private static final String INIT_CB = "com.google.android.gms.common.internal.IGmsCallbacks";

    private static final int GET_SERVICE = 46;
    private static final int POST_INIT = 1;
    private static final int POST_INIT_CONN_INFO = 3;

    private static final int ON_AUTH_CODE = 25003;
    private static final int ON_AUTH_CODE_SCOPED = 25007;
    private static final int SERVER_ACCESS = 27003;
    private static final int SERVER_ACCESS_SCOPED = 27012;

    private GamesShim() {
    }

    /** True when this bind is for the Games service, in either intent form. */
    public static boolean isGamesBind(Intent intent) {
        if (intent == null) {
            return false;
        }
        if (BIND_ACTION.equals(intent.getAction())) {
            return true;
        }
        Uri data = intent.getData();
        return data != null
                && "chimera-action".equals(data.getScheme())
                && BIND_ACTION.equals(data.getSchemeSpecificPart());
    }

    /** True when a guest is binding something inside GMS/GSF. */
    public static boolean isGuestGmsBind(Intent intent) {
        if (intent == null) {
            return false;
        }
        String pkg = intent.getPackage();
        if (pkg == null && intent.getComponent() != null) {
            pkg = intent.getComponent().getPackageName();
        }
        return "com.google.android.gms".equals(pkg) || "com.google.android.gsf".equals(pkg);
    }

    /**
     * The real service action, whether it is on the intent directly or hidden in the
     * "chimera-action:" data URI that play-services uses.
     */
    public static String gmsApiAction(Intent intent) {
        if (intent == null) {
            return null;
        }
        if (intent.getAction() != null) {
            return intent.getAction();
        }
        Uri data = intent.getData();
        if (data != null && "chimera-action".equals(data.getScheme())) {
            return data.getSchemeSpecificPart();
        }
        return null;
    }

    public static IBinder wrap(IBinder binder) {
        return binder == null ? binder : new Broker(binder);
    }

    private static String descriptorOf(IBinder binder) {
        try {
            return binder.getInterfaceDescriptor();
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Pass-through binder wrapper. Subclasses override {@link #intercept} and return null
     * to mean "not mine, forward unchanged".
     */
    private abstract static class Fwd extends Binder {
        final IBinder target;

        Fwd(IBinder target, String descriptor) {
            this.target = target;
            attachInterface(null, descriptor);
        }

        abstract Boolean intercept(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException;

        @Override
        public boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            if (code == INTERFACE_TRANSACTION) {
                reply.writeString(getInterfaceDescriptor());
                return true;
            }
            Boolean handled;
            try {
                handled = intercept(code, data, reply, flags);
            } catch (Throwable t) {
                Log.w(TAG, "intercept failed, forwarding as-is: " + t);
                handled = null;
            }
            if (handled != null) {
                return handled;
            }
            // intercept() may have consumed part of the parcel before bailing out.
            data.setDataPosition(0);
            return target.transact(code, data, reply, flags);
        }

        @Override
        public boolean isBinderAlive() {
            return target.isBinderAlive();
        }

        @Override
        public boolean pingBinder() {
            return target.pingBinder();
        }

        @Override
        public void linkToDeath(DeathRecipient recipient, int flags) {
            try {
                target.linkToDeath(recipient, flags);
            } catch (RemoteException e) {
                recipient.binderDied();
            }
        }

        @Override
        public boolean unlinkToDeath(DeathRecipient recipient, int flags) {
            return target.unlinkToDeath(recipient, flags);
        }
    }

    /** Swaps the client's IGmsCallbacks for ours so we can see the service it gets back. */
    private static final class Broker extends Fwd {
        Broker(IBinder target) {
            super(target, BROKER);
        }

        @Override
        Boolean intercept(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code != GET_SERVICE) {
                return null;
            }
            data.enforceInterface(BROKER);
            IBinder callback = data.readStrongBinder();
            if (callback == null) {
                return null;
            }
            int rest = data.dataPosition();
            Parcel out = Parcel.obtain();
            try {
                out.writeInterfaceToken(BROKER);
                out.writeStrongBinder(new InitCallback(callback));
                out.appendFrom(data, rest, data.dataSize() - rest);
                return target.transact(GET_SERVICE, out, reply, flags);
            } finally {
                out.recycle();
            }
        }
    }

    /** Wraps the IGamesService handed back on connection completion. */
    private static final class InitCallback extends Fwd {
        InitCallback(IBinder target) {
            super(target, INIT_CB);
        }

        @Override
        Boolean intercept(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code != POST_INIT && code != POST_INIT_CONN_INFO) {
                return null;
            }
            data.enforceInterface(INIT_CB);
            int statusCode = data.readInt();
            IBinder service = data.readStrongBinder();
            if (service == null || !GAMES.equals(descriptorOf(service))) {
                return null;
            }
            int rest = data.dataPosition();
            Parcel out = Parcel.obtain();
            try {
                out.writeInterfaceToken(INIT_CB);
                out.writeInt(statusCode);
                out.writeStrongBinder(new Games(service));
                out.appendFrom(data, rest, data.dataSize() - rest);
                return target.transact(code, out, reply, flags);
            } finally {
                out.recycle();
            }
        }
    }

    /** Downgrades the client's scoped server-access request to the form microG implements. */
    private static final class Games extends Fwd {
        Games(IBinder target) {
            super(target, GAMES);
        }

        @Override
        Boolean intercept(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code != SERVER_ACCESS_SCOPED) {
                return null;
            }
            data.enforceInterface(GAMES);
            IBinder callback = data.readStrongBinder();
            String clientId = data.readString();
            int forceRefresh = data.readInt();
            ArrayList<String> scopes = data.createStringArrayList();
            if (callback == null) {
                return null;
            }
            Parcel out = Parcel.obtain();
            try {
                out.writeInterfaceToken(GAMES);
                // Remember the scopes so the reply can be rebuilt in scoped form.
                out.writeStrongBinder(new AuthCallback(callback, scopes));
                out.writeString(clientId);
                out.writeInt(forceRefresh);
                return target.transact(SERVER_ACCESS, out, reply, flags);
            } finally {
                out.recycle();
            }
        }
    }

    /** Upgrades microG's unscoped auth-code reply back to what the client expects. */
    private static final class AuthCallback extends Fwd {
        private final ArrayList<String> scopes;

        AuthCallback(IBinder target, ArrayList<String> scopes) {
            super(target, GAMES_CB);
            this.scopes = scopes;
        }

        @Override
        Boolean intercept(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code != ON_AUTH_CODE) {
                return null;
            }
            data.enforceInterface(GAMES_CB);
            int rest = data.dataPosition();
            Parcel out = Parcel.obtain();
            try {
                out.writeInterfaceToken(GAMES_CB);
                out.appendFrom(data, rest, data.dataSize() - rest);
                out.writeStringList(scopes);
                return target.transact(ON_AUTH_CODE_SCOPED, out, reply, flags);
            } finally {
                out.recycle();
            }
        }
    }
}

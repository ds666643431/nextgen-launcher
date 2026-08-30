package menu.nextgen.launchera.app

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.util.Log
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import menu.nextgen.launcher.NextGenCore
import menu.nextgen.launcher.utils.AbiUtils
import menu.nextgen.launchera.util.FacebookCookies
import menu.nextgen.launchera.util.VirtualGoogleAccount
import menu.nextgen.launchera.view.login.GoogleLoginActivity
import menu.nextgen.launchera.view.login.WebLoginActivity
import menu.nextgen.launchera.view.main.ShortcutActivity
import java.io.File
import java.io.FileOutputStream

/**
 * The whole engine surface the launcher needs, in one place, with primitive/String-only
 * signatures so the native side never has to marshal an ApplicationInfo.
 *
 * This lives in the HOST APP MODULE, not Bcore, so the host class loader can find it. Resolve
 * it from JNI_OnLoad and cache a GlobalRef -- FindClass from android_main hits the system
 * loader (there is no Java frame on the stack there) and will not see this class.
 *
 * THREADING: everything below except the flag accessors is a blocking binder round-trip into
 * :core. getServiceInternal sleeps 1000ms when that process is down and startActivity retries
 * with 200/400/600ms sleeps. Never call these from the render thread.
 */
object VBridge {

    private const val TAG = "VBridge"

    const val EXTRA_PKG = "pkg"
    const val EXTRA_USER_ID = "userId"

    /** Field separator for the packed rows returned by the list calls. */
    const val SEP = "\u001F"

    private val core: NextGenCore get() = NextGenCore.get()

    // ---------------------------------------------------------------- clone / launch

    /**
     * Clone the guest if it is not in the container yet, then launch it. This is what the
     * shortcut path does, and what the launcher's "play" button should call.
     *
     * @return true if a launch intent was resolved and dispatched -- NOT that the guest is up.
     */
    @JvmStatic
    fun cloneAndLaunch(packageName: String, userId: Int): Boolean {
        if (!isInstalled(packageName, userId)) {
            Log.w(TAG, "cloning $packageName into container ...")
            val error = install(packageName, userId)
            if (error != null) {
                Log.e(TAG, "clone of $packageName failed: $error")
                return false
            }
        }
        return launch(packageName, userId)
    }

    /** @return null on success, otherwise the engine's message. */
    @JvmStatic
    fun install(packageName: String, userId: Int): String? = try {
        val r = core.installPackageAsUser(packageName, userId)
        if (r.success) null else (r.msg ?: "install failed")
    } catch (t: Throwable) {
        Log.e(TAG, "install($packageName) threw", t); t.message ?: "install threw"
    }

    /** Install from an APK on disk -- the path the bundled microG APKs take. */
    @JvmStatic
    fun installApk(apkPath: String, userId: Int): String? = try {
        val r = core.installPackageAsUser(File(apkPath), userId)
        if (r.success) null else (r.msg ?: "install failed")
    } catch (t: Throwable) {
        Log.e(TAG, "installApk($apkPath) threw", t); t.message ?: "install threw"
    }

    /**
     * Install from a SAF content:// URI. The stream is opened server-side in :core, but the
     * grant is held by the app uid, so it resolves across the process boundary.
     */
    @JvmStatic
    fun installUri(contentUri: String, userId: Int): String? = try {
        val r = core.installPackageAsUser(Uri.parse(contentUri), userId)
        if (r.success) null else (r.msg ?: "install failed")
    } catch (t: Throwable) {
        Log.e(TAG, "installUri($contentUri) threw", t); t.message ?: "install threw"
    }

    /**
     * Routes through Bcore's LauncherActivity (isEnableLauncherActivity defaults true), which
     * starts from the Application context with FLAG_ACTIVITY_NEW_TASK -- so no Activity of ours
     * is required here. It does still need something of the host uid to be in the foreground,
     * or Android 10+ background-activity-start rules drop it silently.
     */
    @JvmStatic
    fun launch(packageName: String, userId: Int): Boolean = try {
        core.launchApk(packageName, userId)
    } catch (t: Throwable) {
        Log.e(TAG, "launch($packageName) threw", t); false
    }

    @JvmStatic
    fun uninstall(packageName: String, userId: Int) = safely("uninstall") {
        core.uninstallPackageAsUser(packageName, userId)
    }

    @JvmStatic
    fun clear(packageName: String, userId: Int) = safely("clear") {
        core.clearPackage(packageName, userId)
    }

    @JvmStatic
    fun stop(packageName: String, userId: Int) = safely("stop") {
        core.stopPackage(packageName, userId)
    }

    /**
     * Not fully authoritative: when shouldUseFallbackMode() trips after service failures the
     * engine falls back to the DEVICE PackageManager, and BPackageManager.isInstalledFallback
     * hardcodes startsWith("menu.nextgen.launcher") -> true. A guest that is installed on the
     * device but not cloned can read as "installed" on that path.
     */
    @JvmStatic
    fun isInstalled(packageName: String, userId: Int): Boolean = try {
        core.isInstalled(packageName, userId)
    } catch (t: Throwable) {
        Log.e(TAG, "isInstalled($packageName) threw", t); false
    }

    // ---------------------------------------------------------------- listing

    /**
     * Cloned apps for a user, as "pkg{SEP}label{SEP}versionName" rows.
     *
     * Label and icon MUST be resolved from the engine-returned ApplicationInfo, whose sourceDir
     * points at the sandbox base.apk. The packageName-String overloads of the host
     * PackageManager throw for packages that exist only inside the container.
     */
    @JvmStatic
    fun listCloned(userId: Int): Array<String> = try {
        val pm = NextGenCore.getContext().packageManager
        core.getInstalledApplications(0, userId).map { ai ->
            val label = runCatching { pm.getApplicationLabel(ai).toString() }
                .getOrDefault(ai.packageName)
            val version = runCatching {
                core.getInstalledPackages(0, userId)
                    .firstOrNull { it.packageName == ai.packageName }?.versionName
            }.getOrNull() ?: ""
            ai.packageName + SEP + label + SEP + version
        }.toTypedArray()
    } catch (t: Throwable) {
        Log.e(TAG, "listCloned($userId) threw", t); emptyArray()
    }

    /**
     * Device apps that can actually be cloned, as "pkg{SEP}label{SEP}sourceDir" rows.
     * Mirrors the filter the old picker used: no system apps, no incompatible ABI, and never
     * the host itself.
     */
    @JvmStatic
    fun listInstallable(): Array<String> = try {
        val pm = NextGenCore.getContext().packageManager
        pm.getInstalledApplications(0)
            .asSequence()
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
            .filter { !core.isNextGenApp(it.packageName) }
            .filter { runCatching { AbiUtils.isSupport(File(it.sourceDir)) }.getOrDefault(false) }
            .map { ai ->
                val label = runCatching { pm.getApplicationLabel(ai).toString() }
                    .getOrDefault(ai.packageName)
                ai.packageName + SEP + label + SEP + ai.sourceDir
            }
            .toList().toTypedArray()
    } catch (t: Throwable) {
        Log.e(TAG, "listInstallable threw", t); emptyArray()
    }

    /**
     * Write a guest's icon to [outPath] as a PNG, so the native UI can load it as a texture
     * without any Drawable crossing JNI. Pass userId < 0 to read a device app's icon instead
     * of a cloned one.
     */
    @JvmStatic
    fun writeIcon(packageName: String, userId: Int, outPath: String): Boolean = try {
        val pm = NextGenCore.getContext().packageManager
        val info = if (userId < 0) {
            pm.getApplicationInfo(packageName, 0)
        } else {
            core.getInstalledApplications(0, userId).first { it.packageName == packageName }
        }
        val drawable = info.loadIcon(pm)
        val w = drawable.intrinsicWidth.coerceAtLeast(1)
        val h = drawable.intrinsicHeight.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, w, h)
        drawable.draw(Canvas(bitmap))
        File(outPath).parentFile?.mkdirs()
        FileOutputStream(outPath).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        true
    } catch (t: Throwable) {
        Log.e(TAG, "writeIcon($packageName) threw", t); false
    }

    // ---------------------------------------------------------------- users

    /** Existing virtual user ids. install() auto-creates a missing one, so createUser is optional. */
    @JvmStatic
    fun users(): IntArray = try {
        core.users.map { it.id }.toIntArray()
    } catch (t: Throwable) {
        Log.e(TAG, "users threw", t); intArrayOf(0)
    }

    @JvmStatic
    fun createUser(userId: Int) = safely("createUser") { core.createUser(userId) }

    @JvmStatic
    fun deleteUser(userId: Int) = safely("deleteUser") { core.deleteUser(userId) }

    // ---------------------------------------------------------------- accounts

    /** Email of the Google account written into the container, or null. */
    @JvmStatic
    fun googleAccount(): String? = try {
        VirtualGoogleAccount.signedInAs()
    } catch (t: Throwable) {
        Log.e(TAG, "googleAccount threw", t); null
    }

    @JvmStatic
    fun googleSignOut() = safely("googleSignOut") { VirtualGoogleAccount.signOut() }

    /** Opens the EmbeddedSetup WebView flow; the account lands in the container's AccountManager. */
    @JvmStatic
    fun startGoogleSignIn(context: Context) = safely("startGoogleSignIn") {
        context.startActivity(
            Intent(context, GoogleLoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    @JvmStatic
    fun facebookSignedIn(): Boolean = try {
        FacebookCookies.isSignedIn()
    } catch (t: Throwable) {
        Log.e(TAG, "facebookSignedIn threw", t); false
    }

    @JvmStatic
    fun facebookSignOut() = safely("facebookSignOut") { FacebookCookies.signOut() }

    @JvmStatic
    fun startFacebookSignIn(context: Context) = safely("startFacebookSignIn") {
        context.startActivity(
            Intent(context, WebLoginActivity::class.java)
                .putExtra(WebLoginActivity.EXTRA_URL, WebLoginActivity.FB_LOGIN_URL)
                .putExtra(WebLoginActivity.EXTRA_FB_CONNECT, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    // ---------------------------------------------------------------- flags & misc

    @JvmStatic fun hideRoot(): Boolean = AppManager.mNextGenLoader.hideRoot()
    @JvmStatic fun setHideRoot(v: Boolean) = AppManager.mNextGenLoader.invalidHideRoot(v)

    @JvmStatic fun daemonEnabled(): Boolean = AppManager.mNextGenLoader.daemonEnable()
    @JvmStatic fun setDaemonEnabled(v: Boolean) = AppManager.mNextGenLoader.invalidDaemonEnable(v)

    @JvmStatic fun useVpnNetwork(): Boolean = AppManager.mNextGenLoader.useVpnNetwork()
    @JvmStatic fun setUseVpnNetwork(v: Boolean) = AppManager.mNextGenLoader.invalidUseVpnNetwork(v)

    @JvmStatic fun disableFlagSecure(): Boolean = AppManager.mNextGenLoader.disableFlagSecure()
    @JvmStatic fun setDisableFlagSecure(v: Boolean) = AppManager.mNextGenLoader.invalidDisableFlagSecure(v)

    @JvmStatic fun fullMicrog(): Boolean = AppManager.isFullMicrog(App.getContext())
    @JvmStatic fun setFullMicrog(v: Boolean) = AppManager.setFullMicrog(App.getContext(), v)

    /** Cheap. Guest file operations need this even though launchApk proceeds without it. */
    @JvmStatic
    fun hasAllFilesAccess(): Boolean = try {
        core.hasAllFilesAccess()
    } catch (t: Throwable) {
        Log.e(TAG, "hasAllFilesAccess threw", t); false
    }

    /**
     * Pin a home-screen shortcut, without the dialog the old UI wrapped it in.
     *
     * The intent MUST carry ACTION_MAIN -- requestPinShortcut rejects it at request time
     * otherwise, not at build time. The id is packageName + userId; changing the id or the
     * extras orphans shortcuts users have already pinned.
     */
    @JvmStatic
    fun createShortcut(context: Context, packageName: String, userId: Int, label: String): Boolean =
        try {
            if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
                false
            } else {
                val intent = Intent(context, ShortcutActivity::class.java)
                    .setAction(Intent.ACTION_MAIN)
                    .putExtra(EXTRA_PKG, packageName)
                    .putExtra(EXTRA_USER_ID, userId)
                val icon = File(context.cacheDir, "shortcut_$packageName$userId.png")
                val builder = ShortcutInfoCompat.Builder(context, packageName + userId)
                    .setIntent(intent)
                    .setShortLabel(label)
                    .setLongLabel(label)
                if (writeIcon(packageName, userId, icon.absolutePath)) {
                    builder.setIcon(IconCompat.createWithBitmap(
                        android.graphics.BitmapFactory.decodeFile(icon.absolutePath)
                    ))
                }
                ShortcutManagerCompat.requestPinShortcut(context, builder.build(), null)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "createShortcut($packageName) threw", t); false
        }

    private inline fun safely(what: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            Log.e(TAG, "$what threw", t)
        }
    }
}

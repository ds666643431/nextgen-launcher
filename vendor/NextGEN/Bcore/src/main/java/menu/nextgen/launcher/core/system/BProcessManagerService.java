package menu.nextgen.launcher.core.system;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import menu.nextgen.launcher.NextGenCore;
import menu.nextgen.launcher.core.IBActivityThread;
import menu.nextgen.launcher.core.env.BEnvironment;
import menu.nextgen.launcher.core.system.notification.BNotificationManagerService;
import menu.nextgen.launcher.core.system.pm.BPackageManagerService;
import menu.nextgen.launcher.core.system.user.BUserHandle;
import menu.nextgen.launcher.entity.AppConfig;
import menu.nextgen.launcher.proxy.ProxyManifest;
import menu.nextgen.launcher.utils.FileUtils;
import menu.nextgen.launcher.utils.compat.ApplicationThreadCompat;
import menu.nextgen.launcher.utils.compat.BundleCompat;
import menu.nextgen.launcher.utils.provider.ProviderCall;

// Replicated from the Onimod build (proven on MuMu): the wait for a guest process
// to attach (initLock.block) runs OUTSIDE mProcessLock, and getUsingBPidL() excludes
// bpids already held by tracked records (mPidsSelfLocked) so a relaunch after a
// watchdog kill never reuses a stale stub slot. Oni-only session/wipe hooks stripped.
public class BProcessManagerService implements ISystemService {
    public static final String TAG = "BProcessManager";
    public static BProcessManagerService sBProcessManagerService = new BProcessManagerService();
    private final Map<Integer, Map<String, ProcessRecord>> mProcessMap = new HashMap();
    private final List<ProcessRecord> mPidsSelfLocked = new ArrayList();
    private final Object mProcessLock = new Object();

    private boolean attachClientL(final ProcessRecord processRecord, final IBinder iBinder) {
        IBActivityThread asInterface = IBActivityThread.Stub.asInterface(iBinder);
        if (asInterface == null) {
            processRecord.kill();
            return false;
        }
        try {
            iBinder.linkToDeath(new IBinder.DeathRecipient() { // from class: menu.nextgen.launcher.core.system.BProcessManagerService.1
                @Override // android.os.IBinder.DeathRecipient
                public void binderDied() {
                    String str = processRecord.processName;
                    iBinder.unlinkToDeath(this, 0);
                    BProcessManagerService.this.onProcessDie(processRecord);
                }
            }, 0);
        } catch (RemoteException unused) {
        }
        processRecord.bActivityThread = asInterface;
        try {
            processRecord.appThread = ApplicationThreadCompat.asInterface(asInterface.getActivityThread());
            return true;
        } catch (RemoteException unused2) {
            return true;
        }
    }

    private static void createProc(ProcessRecord processRecord) {
        try {
            FileUtils.writeToFile(processRecord.processName.getBytes(), new File(BEnvironment.getProcDir(processRecord.bpid), "cmdline"));
        } catch (IOException unused) {
        }
    }

    public static BProcessManagerService get() {
        return sBProcessManagerService;
    }

    public static int getPid(Context context, String str) {
        try {
            for (ActivityManager.RunningAppProcessInfo runningAppProcessInfo : ((ActivityManager) context.getSystemService("activity")).getRunningAppProcesses()) {
                if (runningAppProcessInfo.processName.equals(str)) {
                    return runningAppProcessInfo.pid;
                }
            }
            return -1;
        } catch (Throwable unused) {
            return -1;
        }
    }

    private static String getProcessName(Context context, int i4) {
        String str;
        Iterator<ActivityManager.RunningAppProcessInfo> it = ((ActivityManager) context.getSystemService("activity")).getRunningAppProcesses().iterator();
        while (true) {
            if (!it.hasNext()) {
                str = null;
                break;
            }
            ActivityManager.RunningAppProcessInfo next = it.next();
            if (next.pid == i4) {
                str = next.processName;
                break;
            }
        }
        if (str != null) {
            return str;
        }
        throw new RuntimeException("processName = null");
    }

    private int getUsingBPidL() {
        List<ActivityManager.RunningAppProcessInfo> runningAppProcesses = ((ActivityManager) NextGenCore.getContext().getSystemService("activity")).getRunningAppProcesses();
        HashSet hashSet = new HashSet();
        Iterator<ActivityManager.RunningAppProcessInfo> it = runningAppProcesses.iterator();
        while (it.hasNext()) {
            hashSet.add(Integer.valueOf(parseBPid(it.next().processName)));
        }
        HashSet hashSet2 = new HashSet();
        synchronized (this.mPidsSelfLocked) {
            try {
                Iterator<ProcessRecord> it2 = this.mPidsSelfLocked.iterator();
                while (it2.hasNext()) {
                    hashSet2.add(Integer.valueOf(it2.next().bpid));
                }
            } catch (Throwable th) {
                throw th;
            }
        }
        for (int i4 = 0; i4 < 50; i4++) {
            if (!hashSet.contains(Integer.valueOf(i4)) && !hashSet2.contains(Integer.valueOf(i4))) {
                return i4;
            }
        }
        return -1;
    }

    private boolean initAppProcessL(ProcessRecord processRecord) {
        String str = processRecord.processName;
        AppConfig clientConfig = processRecord.getClientConfig();
        Bundle bundle = new Bundle();
        bundle.putParcelable(AppConfig.KEY, clientConfig);
        IBinder binder = BundleCompat.getBinder(ProviderCall.callSafely(processRecord.getProviderAuthority(), "_Ng_|_init_process_", null, bundle), "_Ng_|_client_");
        if (binder == null || !binder.isBinderAlive() || !attachClientL(processRecord, binder)) {
            return false;
        }
        createProc(processRecord);
        processRecord.pid = getPid(NextGenCore.getContext(), ProxyManifest.getProcessName(processRecord.bpid));
        processRecord.initLock.open();
        return true;
    }

    private int parseBPid(String str) {
        if (str == null) {
            return -1;
        }
        String str2 = NextGenCore.getHostPkg() + ":p";
        if (str.startsWith(str2)) {
            try {
                return Integer.parseInt(str.substring(str2.length()));
            } catch (NumberFormatException unused) {
            }
        }
        return -1;
    }

    private static void removeProc(ProcessRecord processRecord) {
        FileUtils.deleteDir(BEnvironment.getProcDir(processRecord.bpid));
    }

    private ProcessRecord startProcessLocked(String str, String str2, int i4, int i5, int i6, boolean z4) {
        Map<String, ProcessRecord> map;
        ProcessRecord processRecord;
        boolean z5 = false;
        ApplicationInfo applicationInfo = BPackageManagerService.get().getApplicationInfo(str, 0, i4);
        if (applicationInfo == null) {
            return null;
        }
        int uid = BUserHandle.getUid(i4, BPackageManagerService.get().getAppId(str));
        synchronized (this.mProcessLock) {
            try {
                map = this.mProcessMap.get(Integer.valueOf(uid));
                if (map == null) {
                    map = new HashMap<>();
                }
                processRecord = i5 == -1 ? map.get(str2) : null;
                if (processRecord == null) {
                    int usingBPidL = i5 == -1 ? getUsingBPidL() : i5;
                    if (usingBPidL == -1) {
                        throw new RuntimeException("No processes available");
                    }
                    processRecord = new ProcessRecord(applicationInfo, str2);
                    processRecord.uid = Process.myUid();
                    processRecord.bpid = usingBPidL;
                    processRecord.buid = BPackageManagerService.get().getAppId(str);
                    processRecord.callingBUid = getBUidByPidOrPackageName(i6, str);
                    processRecord.userId = i4;
                    map.put(str2, processRecord);
                    this.mPidsSelfLocked.add(processRecord);
                    synchronized (this.mProcessMap) {
                        this.mProcessMap.put(Integer.valueOf(uid), map);
                    }
                    z5 = true;
                }
            } catch (Throwable th) {
                throw th;
            }
        }
        if (!z5) {
            processRecord.initLock.block();
            if (processRecord.bActivityThread == null) {
                if (z4) {
                    return startProcessLocked(str, str2, i4, -1, i6, false);
                }
                return null;
            }
        } else if (!initAppProcessL(processRecord)) {
            synchronized (this.mProcessLock) {
                map.remove(str2);
                this.mPidsSelfLocked.remove(processRecord);
            }
            processRecord.initLock.open();
            return null;
        }
        return processRecord;
    }

    public ProcessRecord findProcessByPid(int i4) {
        synchronized (this.mPidsSelfLocked) {
            try {
                for (ProcessRecord processRecord : this.mPidsSelfLocked) {
                    if (processRecord.pid == i4) {
                        return processRecord;
                    }
                }
                return null;
            } catch (Throwable th) {
                throw th;
            }
        }
    }

    public ProcessRecord findProcessRecord(String str, String str2, int i4) {
        synchronized (this.mProcessMap) {
            try {
                Map<String, ProcessRecord> map = this.mProcessMap.get(Integer.valueOf(BUserHandle.getUid(i4, BPackageManagerService.get().getAppId(str))));
                if (map == null) {
                    return null;
                }
                return map.get(str2);
            } catch (Throwable th) {
                throw th;
            }
        }
    }

    public int getBUidByPidOrPackageName(int i4, String str) {
        ProcessRecord findProcessByPid = findProcessByPid(i4);
        return findProcessByPid == null ? BPackageManagerService.get().getAppId(str) : BUserHandle.getAppId(findProcessByPid.buid);
    }

    public List<ProcessRecord> getPackageProcessAsUser(String str, int i4) {
        synchronized (this.mProcessMap) {
            try {
                Map<String, ProcessRecord> map = this.mProcessMap.get(Integer.valueOf(BUserHandle.getUid(i4, BPackageManagerService.get().getAppId(str))));
                if (map == null) {
                    return new ArrayList();
                }
                return new ArrayList(map.values());
            } catch (Throwable th) {
                throw th;
            }
        }
    }

    public int getUserIdByCallingPid(int i4) {
        ProcessRecord findProcessByPid = findProcessByPid(i4);
        if (findProcessByPid == null) {
            return 0;
        }
        return findProcessByPid.userId;
    }

    public void killAllByPackageName(String str) {
        synchronized (this.mProcessLock) {
            synchronized (this.mPidsSelfLocked) {
                try {
                    ArrayList arrayList = new ArrayList(this.mPidsSelfLocked);
                    int appId = BPackageManagerService.get().getAppId(str);
                    for (ProcessRecord processRecord : this.mPidsSelfLocked) {
                        if (appId == BUserHandle.getAppId(processRecord.buid)) {
                            this.mProcessMap.remove(Integer.valueOf(processRecord.buid));
                            arrayList.remove(processRecord);
                            processRecord.kill();
                        }
                    }
                    this.mPidsSelfLocked.clear();
                    this.mPidsSelfLocked.addAll(arrayList);
                } catch (Throwable th) {
                    throw th;
                }
            }
        }
    }

    public void killPackageAsUser(String str, int i4) {
        synchronized (this.mProcessLock) {
            try {
                int uid = BUserHandle.getUid(i4, BPackageManagerService.get().getAppId(str));
                Map<String, ProcessRecord> map = this.mProcessMap.get(Integer.valueOf(uid));
                if (map == null) {
                    return;
                }
                for (ProcessRecord processRecord : map.values()) {
                    processRecord.kill();
                    this.mPidsSelfLocked.remove(processRecord);
                }
                this.mProcessMap.remove(Integer.valueOf(uid));
            } catch (Throwable th) {
                throw th;
            }
        }
    }

    public void onProcessDie(ProcessRecord processRecord) {
        synchronized (this.mProcessLock) {
            try {
                processRecord.kill();
                Map<String, ProcessRecord> map = this.mProcessMap.get(Integer.valueOf(processRecord.buid));
                if (map != null) {
                    map.remove(processRecord.processName);
                    if (map.isEmpty()) {
                        this.mProcessMap.remove(Integer.valueOf(processRecord.buid));
                    }
                }
                this.mPidsSelfLocked.remove(processRecord);
                removeProc(processRecord);
                BNotificationManagerService.get().deletePackageNotification(processRecord.getPackageName(), processRecord.userId);
            } catch (Throwable th) {
                throw th;
            }
        }
    }

    public void restartAppProcess(String str, String str2, int i4) {
        synchronized (this.mProcessLock) {
            try {
                Binder.getCallingUid();
                int callingPid = Binder.getCallingPid();
                if (findProcessByPid(callingPid) == null) {
                    startProcessLocked(str, str2, i4, parseBPid(getProcessName(NextGenCore.getContext(), callingPid)), callingPid);
                }
            } catch (Throwable th) {
                throw th;
            }
        }
    }

    public ProcessRecord startProcessLocked(String str, String str2, int i4, int i5, int i6) {
        return startProcessLocked(str, str2, i4, i5, i6, true);
    }

    @Override // menu.nextgen.launcher.core.system.ISystemService
    public void systemReady() {
        FileUtils.deleteDir(BEnvironment.getProcDir());
    }
}

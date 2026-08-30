package menu.nextgen.launcher.utils;

import android.util.Log;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;


public class TransactionThrottler {
    private static final String TAG = "TransactionThrottler";
    
    private static final int MAX_FAILURES_PER_WINDOW = 50; 
    private static final long WINDOW_DURATION_MS = 15000; 
    private static final long THROTTLE_DURATION_MS = 1000; 
    
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicLong windowStartTime = new AtomicLong(0);
    private final AtomicLong lastThrottleTime = new AtomicLong(0);
    
    
    public boolean shouldThrottle() {
        // nextgenlauncher: disabled. Throttling degraded launches to the real-system PM and
        // caused ProxyActivity relaunch loops on native-bridge emulators (MuMu).
        return false;
    }
    
    
    public void recordFailure() {
        // nextgenlauncher: no-op (see shouldThrottle). Failures no longer accumulate into a
        // fallback that loops the launcher on emulators.
    }
    
    
    public void reset() {
        failureCount.set(0);
        windowStartTime.set(0);
        lastThrottleTime.set(0);
        Log.d(TAG, "Reset transaction throttler");
    }
    
    
    public int getFailureCount() {
        return 0; // nextgenlauncher: never report failures -> never enter real-system-PM fallback
    }
}



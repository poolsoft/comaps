package app.organicmaps.carlauncher.performance;

import android.app.ActivityManager;
import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

/**
 * Launcher-only startup profile. It does not start, stop or modify the CoMaps
 * core; it only keeps optional launcher UI work away from the first map frame.
 */
public final class LauncherStartupProfile
{
  private static final String TAG = "LauncherStartup";
  private static final long THREE_GB = 3L * 1024L * 1024L * 1024L;

  private final boolean mLowRam;
  private final long mStartedAt = SystemClock.elapsedRealtime();
  private boolean mUiReadyLogged;

  public LauncherStartupProfile(Context context)
  {
    mLowRam = detectLowRam(context.getApplicationContext());
    Log.i(TAG, "profile=" + (mLowRam ? "LOW_RAM" : "STANDARD"));
  }

  public boolean isLowRam()
  {
    return mLowRam;
  }

  public void markUiReady()
  {
    if (mUiReadyLogged)
      return;
    mUiReadyLogged = true;
    Log.i(TAG, "launcher_ui_ready_ms=" + (SystemClock.elapsedRealtime() - mStartedAt));
  }

  private static boolean detectLowRam(Context context)
  {
    ActivityManager manager =
        (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
    if (manager == null)
      return false;

    ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
    manager.getMemoryInfo(info);
    boolean limitedTotalMemory = info.totalMem > 0 && info.totalMem <= THREE_GB;
    boolean lowAvailableMemory =
        info.totalMem > 0 && info.availMem * 4L < info.totalMem;
    return manager.isLowRamDevice() || info.lowMemory
        || limitedTotalMemory || lowAvailableMemory;
  }
}

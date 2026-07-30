package app.organicmaps.carlauncher.headunit;

import android.content.Context;
import android.os.SystemClock;
import android.view.KeyEvent;

import androidx.annotation.NonNull;

import app.organicmaps.carlauncher.music.MusicManager;
import android.util.Log;

/**
 * Single entry point for physical media commands received through Android or a
 * head-unit adapter. A short cross-source filter prevents one physical press
 * delivered as both KeyEvent and MediaSession input from running twice.
 */
public final class HardwareMediaKeyRouter {

    public enum Source {
        ACTIVITY,
        MEDIA_SESSION,
        HEAD_UNIT_ADAPTER
    }

    private static final long CROSS_SOURCE_DUPLICATE_WINDOW_MS = 300L;
    private static final String TAG = "HardwareMediaKeyRouter";
    private static HardwareMediaKeyRouter instance;

    private final Context appContext;
    private int lastKeyCode = KeyEvent.KEYCODE_UNKNOWN;
    private Source lastSource;
    private long lastEventTime;

    private HardwareMediaKeyRouter(@NonNull Context context) {
        appContext = context.getApplicationContext();
    }

    public static synchronized HardwareMediaKeyRouter getInstance(@NonNull Context context) {
        if (instance == null) {
            instance = new HardwareMediaKeyRouter(context);
        }
        return instance;
    }

    public synchronized boolean route(@NonNull Source source, int keyCode) {
        if (!isSupportedMediaKey(keyCode)) {
            Log.d(TAG,
                    "ignored source=" + source + " key=" + KeyEvent.keyCodeToString(keyCode));
            return false;
        }

        long now = SystemClock.elapsedRealtime();
        if (keyCode == lastKeyCode && lastSource != null && lastSource != source
                && now - lastEventTime >= 0
                && now - lastEventTime <= CROSS_SOURCE_DUPLICATE_WINDOW_MS) {
            Log.d(TAG,
                    "duplicate source=" + source
                            + " previousSource=" + lastSource
                            + " key=" + KeyEvent.keyCodeToString(keyCode));
            return true;
        }

        lastKeyCode = keyCode;
        lastSource = source;
        lastEventTime = now;
        Log.d(TAG,
                "route source=" + source + " key=" + KeyEvent.keyCodeToString(keyCode));
        return MusicManager.getInstance(appContext).handleHardwareMediaKey(keyCode);
    }

    private boolean isSupportedMediaKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_MEDIA_PLAY
                || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE
                || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                || keyCode == KeyEvent.KEYCODE_MEDIA_NEXT
                || keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS
                || keyCode == KeyEvent.KEYCODE_MEDIA_STOP;
    }
}

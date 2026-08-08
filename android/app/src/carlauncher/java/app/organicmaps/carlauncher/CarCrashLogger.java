package app.organicmaps.carlauncher;

import android.app.ActivityManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Writes startup breadcrumbs and fatal Java crashes where they can be retrieved without ADB. */
public final class CarCrashLogger implements Thread.UncaughtExceptionHandler {
    private static final String TAG = "CarCrashLogger";
    private static final Object LOCK = new Object();
    private static volatile CarCrashLogger instance;

    private final Thread.UncaughtExceptionHandler defaultHandler;
    private final Context context;
    private Uri mediaStoreUri;
    private File legacyFile;
    private static final String STATE_PREFS = "car_crash_logger_state";
    private static final String KEY_SESSION_ACTIVE = "session_active";
    private static final String KEY_LAST_STAGE = "last_stage";

    private CarCrashLogger(Context context) {
        this.context = context.getApplicationContext();
        this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        createSessionLog();
        android.content.SharedPreferences state = this.context.getSharedPreferences(
                STATE_PREFS, Context.MODE_PRIVATE);
        if (state.getBoolean(KEY_SESSION_ACTIVE, false)) {
            writeLine("PREVIOUS session terminated without a Java exception; last_stage="
                    + state.getString(KEY_LAST_STAGE, "unknown"));
        }
        state.edit().putBoolean(KEY_SESSION_ACTIVE, true)
                .putString(KEY_LAST_STAGE, "process_start").commit();
    }

    public static void init(Context context) {
        if (instance != null) return;
        synchronized (LOCK) {
            if (instance != null) return;
            instance = new CarCrashLogger(context);
            Thread.setDefaultUncaughtExceptionHandler(instance);
            instance.writeLine("START process; app=" + context.getPackageName()
                    + "; device=" + Build.MANUFACTURER + " " + Build.MODEL
                    + "; android=" + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        }
    }

    public static void recordStartupStage(String stage) {
        CarCrashLogger logger = instance;
        if (logger != null) {
            logger.context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
                    .edit().putString(KEY_LAST_STAGE, stage).commit();
            logger.writeLine("STAGE " + stage);
        }
    }

    public static void recordMemory(String stage) {
        CarCrashLogger logger = instance;
        if (logger == null) return;
        Runtime runtime = Runtime.getRuntime();
        ActivityManager manager = (ActivityManager) logger.context.getSystemService(
                Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        if (manager != null) manager.getMemoryInfo(info);
        long javaUsed = runtime.totalMemory() - runtime.freeMemory();
        logger.writeLine("MEMORY " + stage + "; java_used_mb=" + toMb(javaUsed)
                + "; java_max_mb=" + toMb(runtime.maxMemory())
                + "; system_available_mb=" + toMb(info.availMem)
                + "; system_total_mb=" + toMb(info.totalMem)
                + "; low_memory=" + info.lowMemory);
    }

    public static void markStartupComplete() {
        CarCrashLogger logger = instance;
        if (logger == null) return;
        logger.context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_SESSION_ACTIVE, false)
                .putString(KEY_LAST_STAGE, "launcher_ui_ready").commit();
        logger.writeLine("STAGE launcher_ui_ready");
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        writeCrash(thread, throwable);
        if (defaultHandler != null) {
            defaultHandler.uncaughtException(thread, throwable);
        }
    }

    private void createSessionLog() {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String fileName = "carlauncher_startup_" + timestamp + ".log";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                values.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
                values.put(MediaStore.Downloads.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/CoMapsAuto");
                mediaStoreUri = context.getContentResolver().insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (mediaStoreUri != null) return;
            } catch (Exception e) {
                Log.w(TAG, "Public Download log could not be created", e);
            }
        } else {
            try {
                File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File directory = new File(downloads, "CoMapsAuto");
                if ((directory.exists() || directory.mkdirs()) && directory.canWrite()) {
                    legacyFile = new File(directory, fileName);
                    return;
                }
            } catch (Exception e) {
                Log.w(TAG, "Legacy Download log could not be created", e);
            }
        }

        File fallback = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (fallback != null && (fallback.exists() || fallback.mkdirs())) {
            legacyFile = new File(fallback, fileName);
        }
    }

    private void writeCrash(Thread thread, Throwable throwable) {
        synchronized (LOCK) {
            try (PrintWriter writer = new PrintWriter(openOutputStream(true))) {
                if (writer == null) return;
                writer.println(timestamp() + " FATAL thread=" + thread.getName());
                throwable.printStackTrace(writer);
                writer.println("===== END CRASH =====");
            } catch (Exception e) {
                Log.e(TAG, "Crash log could not be saved", e);
            }
        }
    }

    private void writeLine(String message) {
        synchronized (LOCK) {
            try (OutputStream output = openOutputStream(true)) {
                if (output == null) return;
                output.write((timestamp() + " " + message + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                output.flush();
            } catch (Exception e) {
                Log.e(TAG, "Startup stage could not be saved", e);
            }
        }
    }

    private OutputStream openOutputStream(boolean append) throws Exception {
        if (mediaStoreUri != null) {
            ContentResolver resolver = context.getContentResolver();
            return resolver.openOutputStream(mediaStoreUri, append ? "wa" : "w");
        }
        return legacyFile != null ? new FileOutputStream(legacyFile, append) : null;
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
    }

    private static long toMb(long bytes) {
        return bytes > 0 ? bytes / (1024L * 1024L) : 0L;
    }
}

package app.organicmaps.carlauncher;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CarCrashLogger implements Thread.UncaughtExceptionHandler {
    private static final String TAG = "CarCrashLogger";
    private final Thread.UncaughtExceptionHandler defaultHandler;
    private final Context context;

    public CarCrashLogger(Context context) {
        this.context = context.getApplicationContext();
        this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
    }

    public static void init(Context context) {
        Thread.setDefaultUncaughtExceptionHandler(new CarCrashLogger(context));
        Log.i(TAG, "CarCrashLogger initialized");
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        try {
            saveCrashLog(throwable);
        } catch (Exception e) {
            Log.e(TAG, "Failed to save crash log", e);
        }

        // Pass to the default handler to let the app crash normally
        if (defaultHandler != null) {
            defaultHandler.uncaughtException(thread, throwable);
        }
    }

    private void saveCrashLog(Throwable throwable) {
        File logDir = context.getExternalFilesDir(null);
        if (logDir == null) return;

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File logFile = new File(logDir, "carlauncher_crash_" + timestamp + ".log");

        try (FileOutputStream fos = new FileOutputStream(logFile, true);
             PrintWriter pw = new PrintWriter(fos)) {

            pw.println("--- Car Launcher Crash Log ---");
            pw.println("Time: " + new Date().toString());
            pw.println("Exception: " + throwable.toString());
            pw.println("Stack Trace:");

            StringWriter sw = new StringWriter();
            PrintWriter stackTracePw = new PrintWriter(sw);
            throwable.printStackTrace(stackTracePw);
            pw.print(sw.toString());

            Log.e(TAG, "Crash log saved to: " + logFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Error writing crash log", e);
        }
    }
}

package app.organicmaps.carlauncher.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Launcher'a ozel hata yakalayici. 
 * OsmAnd'in genel hata raporundan bagimsiz calisir.
 */
public class CrashHandler implements Thread.UncaughtExceptionHandler {

    private final Context context;
    private final Thread.UncaughtExceptionHandler defaultHandler;

    public CrashHandler(Context context) {
        this.context = context.getApplicationContext();
        this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
    }

    public static void init(Context context) {
        Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(context));
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        String crashLog = getCrashInfo(throwable);
        saveToFile(crashLog);
        
        // Hata olustugunda uygulamayi kapatmadan once logu bastir
        Log.e("CarLauncherCrash", crashLog);
        
        // Varsayilan handler'a geri don (OsmAnd raporu yine de cikabilir ama biz logu aldik)
        if (defaultHandler != null) {
            defaultHandler.uncaughtException(thread, throwable);
        }
    }

    private String getCrashInfo(Throwable t) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);

        StringBuilder sb = new StringBuilder();
        sb.append("===== CAR LAUNCHER CRASH REPORT =====\n");
        sb.append("Time: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date())).append("\n");
        sb.append("Device: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n");
        sb.append("Android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("-------------------------------------\n");
        sb.append(sw.toString());
        sb.append("\n=====================================");
        return sb.toString();
    }

    private void saveToFile(String log) {
        try {
            File dir = context.getExternalFilesDir(null);
            if (dir != null) {
                File file = new File(dir, "launcher_crash.log");
                FileOutputStream fos = new FileOutputStream(file, true); // Append mode
                fos.write((log + "\n\n").getBytes());
                fos.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

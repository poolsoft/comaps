package app.organicmaps.carlauncher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

/**
 * Secilen uygulamalari sirayla baslatir.
 */
public class AutoLaunchManager {

    private final Context context;
    private final CarLauncherSettings settings;
    private final Handler handler = new Handler(Looper.getMainLooper());
    
    // Launch delay between apps (ms)
    private static final long LAUNCH_DELAY = 3500;

    public AutoLaunchManager(Context context) {
        this.context = context;
        this.settings = new CarLauncherSettings(context);
    }

    public void execute() {
        boolean hasAnyAppToLaunch = false;
        for (int i = 1; i <= 3; i++) {
            if (settings.isAutoLaunchEnabled(i)) {
                String pkg = settings.getAutoLaunchPackage(i);
                if (pkg != null && !pkg.isEmpty()) {
                    hasAnyAppToLaunch = true;
                    break;
                }
            }
        }

        if (!hasAnyAppToLaunch) {
            return; // Baslatilacak uygulama yoksa sureci hic baslatma (Turkce karakter yok)
        }

        // Sirali baslatma zinciri
        runSlot(1, () -> 
            runSlot(2, () -> 
                runSlot(3, () -> 
                    finishLaunch()
                )
            )
        );
    }

    private void runSlot(int slot, Runnable next) {
        if (settings.isAutoLaunchEnabled(slot)) {
            String pkg = settings.getAutoLaunchPackage(slot);
            if (pkg != null && !pkg.isEmpty()) {
                launchApp(pkg);
                // Delay before next
                handler.postDelayed(next, LAUNCH_DELAY);
            } else {
                // Skip
                next.run();
            }
        } else {
            // Skip
            next.run();
        }
    }

    private void launchApp(String pkg) {
        try {
            PackageManager pm = context.getPackageManager();
            Intent intent = pm.getLaunchIntentForPackage(pkg);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                // Toast.makeText(context, "Başlatılıyor: " + pkg, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e("AutoLaunch", "Failed to launch " + pkg, e);
        }
    }

    private void finishLaunch() {
        // En son Launcher'i one getir
        try {
            Intent intent = new Intent(context, app.organicmaps.activities.MapActivity.class); // Adjust class if needed
            // intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT); // Reorder?
            // Or Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT
            
            // MapActivity'yi tekrar on plana cekmek icin:
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            context.startActivity(intent);
            
            Toast.makeText(context, "Otomatik Başlatma Tamamlandı", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

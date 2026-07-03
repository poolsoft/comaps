package app.organicmaps.carlauncher;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public class TaskMonitorService extends Service {
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Servisin sadece onTaskRemoved eventi icin hayatta kalmasini istiyoruz
        return START_NOT_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        Log.w("CoMapsAuto", "onTaskRemoved: Task cleared from recents. Killing process to prevent MIUI Ghost Task!");
        
        // Android/MIUI uygulamanin Task'ini sildigi halde Process'ini bellekte canli tutarsa, 
        // uygulama Ghost/Zombie state'e duser. Bunu engellemek icin Process'i kendimiz olduruyoruz.
        stopSelf();
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(0);
    }
}

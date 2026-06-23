package app.organicmaps.carlauncher;

import android.content.ComponentName;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import app.organicmaps.carlauncher.music.MusicManager;

import java.util.List;

/**
 * NotificationListenerService - Harici müzik uygulamalarının (Spotify, YouTube Music, vb.)
 * bildirimlerini dinleyerek medya session bilgilerine erişim sağlar.
 *
 * Android'in MediaSessionManager.getActiveSessions() API'si bu servisin ComponentName'i
 * üzerinden çalışır. Servis aktif olarak bildirim dinlemezse session listesi boş döner.
 *
 * Permission: Kullanıcının Ayarlar > Bildirim Erişimi'nden izin vermesi gerekir.
 */
public class MediaNotificationListener extends NotificationListenerService {

    private static final String TAG = "MediaNotifListener";

    @Override
    public void onListenerConnected() {
        Log.d(TAG, "NotificationListener connected. Ready to receive media sessions.");
        // Bağlantı kurulunca mevcut session'ları hemen yükle
        refreshActiveSessions();
    }

    @Override
    public void onListenerDisconnected() {
        Log.w(TAG, "NotificationListener disconnected!");
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (isMediaNotification(sbn)) {
            Log.v(TAG, "Media notification posted from: " + sbn.getPackageName());
            // Bildirim geldikten sonra session'ın aktifleşmesi için kısa gecikme
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                refreshActiveSessions();
            }, 500);
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (isMediaNotification(sbn)) {
            Log.v(TAG, "Media notification removed from: " + sbn.getPackageName());
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                refreshActiveSessions();
            }, 300);
        }
    }

    /**
     * Bildirimin medya kategorisinde olup olmadığını kontrol eder.
     */
    private boolean isMediaNotification(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null) return false;
        String category = sbn.getNotification().category;
        return android.app.Notification.CATEGORY_TRANSPORT.equals(category)
                || android.app.Notification.CATEGORY_SERVICE.equals(category);
    }

    /**
     * Aktif medya session'larını yeniler ve MusicManager'a bildirir.
     * Reflection kullanmaz, direkt public metod çağırır.
     */
    private void refreshActiveSessions() {
        try {
            MediaSessionManager manager = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
            if (manager == null) return;

            ComponentName componentName = new ComponentName(this,
                    "app.organicmaps.carlauncher.MediaNotificationListener");
            List<MediaController> controllers = manager.getActiveSessions(componentName);

            if (controllers != null) {
                Log.d(TAG, "Active sessions: " + controllers.size());
                // MusicManager singleton'ına direkt bildir
                MusicManager musicManager = MusicManager.getInstance(getApplicationContext());
                musicManager.onSessionsRefreshed(controllers);
            }
        } catch (SecurityException e) {
            Log.w(TAG, "Notification access not granted yet");
        } catch (Exception e) {
            Log.w(TAG, "Failed to refresh sessions: " + e.getMessage());
        }
    }
}
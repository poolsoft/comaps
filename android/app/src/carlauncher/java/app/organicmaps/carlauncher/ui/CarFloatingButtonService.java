package app.organicmaps.carlauncher.ui;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import app.organicmaps.R;

public class CarFloatingButtonService extends Service {
    private static final String CHANNEL_ID = "CarFloatingButtonChannel";
    private static final int NOTIFICATION_ID = 4568;

    private android.location.LocationManager locationManager;
    private android.location.LocationListener locationListener;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP_SERVICE".equals(intent.getAction())) {
            stopLocationUpdates();
            CarFloatingButtonManager.getInstance(this).hideButton();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.floating_button_running))
                .setSmallIcon(R.drawable.ic_logo)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        // Butonu goster ve GPS guncellemelerini aktif tut
        CarFloatingButtonManager.getInstance(this).showButton();
        startLocationUpdates();

        return START_STICKY;
    }

    private void startLocationUpdates() {
        if (locationManager == null) {
            locationManager = (android.location.LocationManager) getSystemService(Context.LOCATION_SERVICE);
            locationListener = new android.location.LocationListener() {
                @Override
                public void onLocationChanged(android.location.Location location) {
                    if (location != null && location.hasSpeed()) {
                        float speedKmh = location.getSpeed() * 3.6f;
                        CarFloatingButtonManager.getInstance(CarFloatingButtonService.this).setNativeGpsSpeed(speedKmh);
                    }
                }
                @Override public void onStatusChanged(String provider, int status, android.os.Bundle extras) {}
                @Override public void onProviderEnabled(String provider) {}
                @Override public void onProviderDisabled(String provider) {}
            };
        }
        try {
            locationManager.requestLocationUpdates(android.location.LocationManager.GPS_PROVIDER, 1000, 0, locationListener);
        } catch (SecurityException e) {
            // ignore
        }
    }

    private void stopLocationUpdates() {
        if (locationManager != null && locationListener != null) {
            try {
                locationManager.removeUpdates(locationListener);
            } catch (SecurityException e) {
                // ignore
            }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopLocationUpdates();
        CarFloatingButtonManager.getInstance(this).hideButton();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Floating Button Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            serviceChannel.setDescription("Keeps the floating button and GPS active in background.");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }
}

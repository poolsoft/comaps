package app.organicmaps.carlauncher.hardware;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.audiofx.AudioEffect;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import java.util.List;

/**
 * Evrensel Arac Donanim Yoneticisi (CarHardwareManager).
 * Farkli multimedya teyp markalarini (XYAuto, HCN, Standart) algilar
 * ve donanimsal eylemleri soyutlayarak yurutur.
 */
public class CarHardwareManager {

    private static final String TAG = "CarHardwareManager";
    private static CarHardwareManager instance;
    private final Context context;

    public enum Platform {
        XY_AUTO,
        HCN,
        STANDARD
    }

    private Platform currentPlatform = Platform.STANDARD;

    public interface BluetoothMetadataListener {
        void onBluetoothTrackChanged(String title, String artist, String albumArtPath, boolean isPlaying);
    }

    private CarHardwareManager(Context context) {
        this.context = context.getApplicationContext();
        detectPlatform();
    }

    public static synchronized CarHardwareManager getInstance(Context context) {
        if (instance == null) {
            instance = new CarHardwareManager(context);
        }
        return instance;
    }

    /**
     * Cihazin hangi multimedya platformuna ait oldugunu paket adlarindan algilar.
     */
    private void detectPlatform() {
        PackageManager pm = context.getPackageManager();
        if (isPackageInstalled("sys.xy.tumu.app") || isPackageInstalled("com.acloud.stub.localmusic")) {
            currentPlatform = Platform.XY_AUTO;
            Log.d(TAG, "Algilanan Donanim Platformu: XYAuto");
        } else if (isPackageInstalled("com.hcn.AutoMediaPlayer") || isPackageInstalled("com.hcn.AutoSettings")) {
            currentPlatform = Platform.HCN;
            Log.d(TAG, "Algilanan Donanim Platformu: HCN");
        } else {
            currentPlatform = Platform.STANDARD;
            Log.d(TAG, "Algilanan Donanim Platformu: Standart Android");
        }
    }

    public Platform getCurrentPlatform() {
        return currentPlatform;
    }

    /**
     * Donanimsal Ekolayziri (DSP / Ses Efektleri) acar.
     */
    public void openEqualizer(Context activityContext) {
        if (currentPlatform == Platform.XY_AUTO) {
            if (launchApp(activityContext, "sys.xy.tumu.app")) {
                return;
            }
        } else if (currentPlatform == Platform.HCN) {
            // HCN DSP / EQ paketlerini dene (Turkce karakter yok)
            String[] hcnEqPackages = {"com.hcn.soundeffect", "com.hcn.eq", "com.hcn.settings"};
            for (String pkg : hcnEqPackages) {
                if (launchApp(activityContext, pkg)) {
                    return;
                }
            }
        }

        // Evrensel Fallback: Standart Android EQ panelini acmayi dene
        try {
            Intent intent = new Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL);
            intent.putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC);
            intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activityContext.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(activityContext, "Ekolayzır bulunamadı", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Standart EQ acilamadi", e);
        }
    }

    /**
     * Ekrani karartir / kapatir (Night Screen Off).
     */
    public void turnOffScreen() {
        if (currentPlatform == Platform.XY_AUTO) {
            try {
                Intent intent = new Intent("xy.android.setScreenState");
                intent.putExtra("screenstate", 2);
                context.sendBroadcast(intent);
                Log.d(TAG, "XYAuto ekran kapatma yayini gonderildi.");
            } catch (Exception e) {
                Log.e(TAG, "XYAuto ekran kapatilamadi", e);
            }
        } else if (currentPlatform == Platform.HCN) {
            try {
                Intent intent = new Intent("com.hcn.intent.action.SCREEN_OFF");
                context.sendBroadcast(intent);
                Log.d(TAG, "HCN ekran kapatma yayini gonderildi.");
            } catch (Exception e) {
                try {
                    Intent intent2 = new Intent("com.hcn.screen.off");
                    context.sendBroadcast(intent2);
                } catch (Exception ex) {
                    Log.e(TAG, "HCN ekran kapatilamadi", ex);
                }
            }
        } else {
            // Standart Android icin ekran kapatma uyarisi
            Toast.makeText(context, "Sistem ekran kapatma desteği bulunmuyor", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Tek tikla arka plan bellek temizligi yapar (RAM Optimizer).
     */
    public void cleanRam() {
        if (currentPlatform == Platform.XY_AUTO) {
            try {
                context.sendBroadcast(new Intent("xy.onekeyclean"));
                Toast.makeText(context, "Hafıza temizlendi", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "XYAuto bir tikla temizleme yayini gonderildi.");
            } catch (Exception e) {
                Log.e(TAG, "XYAuto bellek temizlenemedi", e);
            }
        } else if (currentPlatform == Platform.HCN) {
            try {
                context.sendBroadcast(new Intent("com.hcn.intent.action.ONEKEYCLEAN"));
                Toast.makeText(context, "Hafıza temizlendi", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                evrenselRamTemizle();
            }
        } else {
            evrenselRamTemizle();
        }
    }

    /**
     * Evrensel arka plan temizligi (Fallback).
     */
    private void evrenselRamTemizle() {
        try {
            ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (activityManager != null) {
                List<ActivityManager.RunningAppProcessInfo> processes = activityManager.getRunningAppProcesses();
                if (processes != null) {
                    for (ActivityManager.RunningAppProcessInfo info : processes) {
                        if (info.importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE) {
                            for (String pkg : info.pkgList) {
                                if (!pkg.equals(context.getPackageName())) {
                                    activityManager.killBackgroundProcesses(pkg);
                                }
                            }
                        }
                    }
                }
                Toast.makeText(context, "Hafıza optimize edildi", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Evrensel RAM temizligi basarisiz", e);
        }
    }

    /**
     * Donanima gore Bluetooth AVRCP (Telefon muzik bilgisi) yayin alicisini kaydeder.
     */
    public BroadcastReceiver registerBluetoothReceiver(BluetoothMetadataListener listener) {
        IntentFilter filter = new IntentFilter();
        
        if (currentPlatform == Platform.XY_AUTO) {
            filter.addAction("com.acloud.intent.play_status");
        } else {
            // Standart Android ve HCN AVRCP yayinlari (Turkce karakter yok)
            filter.addAction("com.android.music.metachanged");
            filter.addAction("com.android.music.playstatechanged");
            filter.addAction("android.bluetooth.a2dp-sink.profile.action.PLAYING_STATE_CHANGED");
        }

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (intent == null || intent.getAction() == null) return;
                
                String action = intent.getAction();
                String title = "";
                String artist = "";
                String artPath = "";
                boolean isPlaying = false;

                if ("com.acloud.intent.play_status".equals(action)) {
                    // XYAuto Bluetooth Müzik Yorumlama (Turkce karakter yok)
                    byte status = intent.getByteExtra("play_status", (byte) 16);
                    isPlaying = (status == 1);
                    title = intent.getStringExtra("songname");
                    artist = intent.getStringExtra("singer");
                    artPath = intent.getStringExtra("artistPicPath");
                } else if ("com.android.music.metachanged".equals(action)) {
                    title = intent.getStringExtra("track");
                    artist = intent.getStringExtra("artist");
                    isPlaying = intent.getBooleanExtra("playing", false);
                } else if ("com.android.music.playstatechanged".equals(action)) {
                    isPlaying = intent.getBooleanExtra("playing", false);
                } else if ("android.bluetooth.a2dp-sink.profile.action.PLAYING_STATE_CHANGED".equals(action)) {
                    int state = intent.getIntExtra("android.bluetooth.profile.extra.STATE", 0);
                    isPlaying = (state == 10 || state == 2); // Playing or Connected
                }

                if (listener != null) {
                    listener.onBluetoothTrackChanged(
                        TextUtils.isEmpty(title) ? "" : title,
                        TextUtils.isEmpty(artist) ? "" : artist,
                        artPath,
                        isPlaying
                    );
                }
            }
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            context.registerReceiver(receiver, filter);
        }

        return receiver;
    }

    private boolean isPackageInstalled(String packageName) {
        try {
            context.getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private boolean launchApp(Context activityContext, String packageName) {
        try {
            Intent intent = activityContext.getPackageManager().getLaunchIntentForPackage(packageName);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activityContext.startActivity(intent);
                return true;
            }
        } catch (Exception e) {
            Log.w(TAG, "Uygulama baslatilamadi: " + packageName);
        }
        return false;
    }

    public void playBluetooth() {
        if (currentPlatform == Platform.XY_AUTO) {
            context.sendBroadcast(new Intent("xy.android.forceplay"));
        } else {
            // Standart veya HCN bluetooth muzik oynat yayini (Turkce karakter yok)
            try {
                context.sendBroadcast(new Intent("com.hcn.intent.action.BT_PLAY"));
            } catch (Exception e) {
                // ignore
            }
        }
    }

    public void pauseBluetooth() {
        if (currentPlatform == Platform.XY_AUTO) {
            context.sendBroadcast(new Intent("xy.android.forcepause"));
        } else {
            try {
                context.sendBroadcast(new Intent("com.hcn.intent.action.BT_PAUSE"));
            } catch (Exception e) {
                // ignore
            }
        }
    }

    public void nextBluetooth() {
        if (currentPlatform == Platform.XY_AUTO) {
            context.sendBroadcast(new Intent("xy.android.nextmedia"));
        } else {
            try {
                context.sendBroadcast(new Intent("com.hcn.intent.action.BT_NEXT"));
            } catch (Exception e) {
                // ignore
            }
        }
    }

    public void prevBluetooth() {
        if (currentPlatform == Platform.XY_AUTO) {
            context.sendBroadcast(new Intent("xy.android.previousmedia"));
        } else {
            try {
                context.sendBroadcast(new Intent("com.hcn.intent.action.BT_PREV"));
            } catch (Exception e) {
                // ignore
            }
        }
    }
}

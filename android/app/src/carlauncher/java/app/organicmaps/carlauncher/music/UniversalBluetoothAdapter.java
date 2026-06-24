package app.organicmaps.carlauncher.music;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import net.osmand.plus.carlauncher.hardware.CarHardwareManager;

/**
 * Her multimedya platformu (XYAuto, HCN, Standart) icin 
 * evrensel Bluetooth AVRCP muzigini central MusicManager'a baglar.
 */
public class UniversalBluetoothAdapter implements BaseMediaAdapter {

    private static final String PACKAGE_NAME = "universal.bluetooth.player";
    private final Context context;
    private final MusicManager manager;
    private final CarHardwareManager hardwareManager;

    private String trackTitle = "";
    private String trackArtist = "";
    private String albumArtPath = "";
    private boolean isPlaying = false;
    
    private BroadcastReceiver btReceiver;

    public UniversalBluetoothAdapter(Context context, MusicManager manager) {
        this.context = context.getApplicationContext();
        this.manager = manager;
        this.hardwareManager = CarHardwareManager.getInstance(this.context);
        
        setupBluetoothListener();
    }

    private void setupBluetoothListener() {
        btReceiver = hardwareManager.registerBluetoothReceiver((title, artist, artPath, playing) -> {
            boolean changed = false;
            
            if (!TextUtils.equals(trackTitle, title)) {
                trackTitle = title;
                changed = true;
            }
            if (!TextUtils.equals(trackArtist, artist)) {
                trackArtist = artist;
                changed = true;
            }
            if (!TextUtils.equals(albumArtPath, artPath)) {
                albumArtPath = artPath;
                changed = true;
            }
            if (isPlaying != playing) {
                isPlaying = playing;
                changed = true;
                if (isPlaying) {
                    manager.onExternalPlayerStarted(PACKAGE_NAME);
                }
            }
            
            if (changed) {
                manager.notifyTrackChanged();
                manager.notifyStateChanged();
            }
        });
    }

    @Override
    public void play() {
        hardwareManager.playBluetooth();
    }

    @Override
    public void pause() {
        hardwareManager.pauseBluetooth();
    }

    @Override
    public void next() {
        hardwareManager.nextBluetooth();
    }

    @Override
    public void prev() {
        hardwareManager.prevBluetooth();
    }

    @Override
    public void seekTo(int position) {
        // Bluetooth AVRCP seek destegi teyp donanimina baglidir
    }

    @Override
    public boolean isPlaying() {
        return isPlaying;
    }

    @Override
    public boolean isActive() {
        // Bluetooth her zaman donanimda mevcuttur (Turkce karakter yok)
        return true;
    }

    @Override
    public String getTitle() {
        return TextUtils.isEmpty(trackTitle) ? "Bluetooth Müzik" : trackTitle;
    }

    @Override
    public String getArtist() {
        return trackArtist;
    }

    @Override
    public Bitmap getAlbumArt() {
        if (!TextUtils.isEmpty(albumArtPath)) {
            try {
                return BitmapFactory.decodeFile(albumArtPath);
            } catch (Exception e) {
                // ignore
            }
        }
        return null;
    }

    @Override
    public int getDuration() {
        return 0; // Genelde Bluetooth metadata sure dondurmez
    }

    @Override
    public int getPosition() {
        return 0;
    }

    @Override
    public String getPackageName() {
        return PACKAGE_NAME;
    }
}

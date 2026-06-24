package app.organicmaps.carlauncher.music;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;

/**
 * Hcn yerel muzik oynatici adaptor sinifi.
 * BaseMediaAdapter arayuzunu uygular.
 */
public class HcnMusicAdapter implements BaseMediaAdapter {

    private static final String PACKAGE_NAME = "com.hcn.AutoMediaPlayer";
    private final Context context;
    private final MusicManager manager;

    public HcnMusicAdapter(Context context, MusicManager manager) {
        this.context = context.getApplicationContext();
        this.manager = manager;
    }

    private void sendCommand(String action) {
        try {
            Intent intent = new Intent(action);
            intent.setPackage(PACKAGE_NAME);
            context.sendBroadcast(intent);
        } catch (Exception e) {
            // ignore
        }
    }

    @Override
    public void play() {
        sendCommand("com.auto.apimediaplayer.notification.PLAY");
        sendCommand("com.auto.apimediaplayer.notification.PLAYPAUSE");
    }

    @Override
    public void pause() {
        sendCommand("com.auto.apimediaplayer.notification.PAUSE");
        sendCommand("com.auto.apimediaplayer.notification.PLAYPAUSE");
    }

    @Override
    public void next() {
        sendCommand("com.auto.apimediaplayer.notification.NEXT");
    }

    @Override
    public void prev() {
        sendCommand("com.auto.apimediaplayer.notification.PREV");
    }

    @Override
    public void seekTo(int position) {
        // Seek islemi MediaSession uzerinden yonetilir
    }

    @Override
    public boolean isPlaying() {
        // Calma durumu MediaSession tarafindan takip edilir
        return false;
    }

    @Override
    public boolean isActive() {
        try {
            context.getPackageManager().getPackageInfo(PACKAGE_NAME, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getTitle() {
        return null; // MediaSession uzerinden dinamik alinir
    }

    @Override
    public String getArtist() {
        return null; // MediaSession uzerinden dinamik alinir
    }

    @Override
    public Bitmap getAlbumArt() {
        return null; // MediaSession uzerinden dinamik alinir
    }

    @Override
    public int getDuration() {
        return 0; // MediaSession uzerinden dinamik alinir
    }

    @Override
    public int getPosition() {
        return 0; // MediaSession uzerinden dinamik alinir
    }

    @Override
    public String getPackageName() {
        return PACKAGE_NAME;
    }
}

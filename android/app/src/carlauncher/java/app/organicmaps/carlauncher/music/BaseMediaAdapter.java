package app.organicmaps.carlauncher.music;

import android.graphics.Bitmap;

/**
 * Evrensel Medya Adaptoru Arayuzu.
 * Tum teyp oynaticilari (Yerel muzik, radyo, BT) ve standard Android
 * MediaSession oynaticilari bu arayuzu uygular.
 */
public interface BaseMediaAdapter {
    void play();
    void pause();
    void next();
    void prev();
    void seekTo(int position);

    boolean isPlaying();
    boolean isActive();
    String getTitle();
    String getArtist();
    Bitmap getAlbumArt();
    int getDuration();
    int getPosition();
    String getPackageName(); // Oynaticiyi tanimlayan benzersiz paket adi/ID
}

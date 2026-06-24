package app.organicmaps.carlauncher.music;

import android.content.Context;
import android.graphics.Bitmap;

/**
 * Dahili oynatici adaptor sinifi.
 * BaseMediaAdapter arayuzunu uygular.
 */
public class InternalPlayerAdapter implements BaseMediaAdapter {

    private final InternalMusicPlayer player;
    private final String packageName;

    public InternalPlayerAdapter(Context context, InternalMusicPlayer player) {
        this.player = player;
        this.packageName = "usage.internal.player";
    }

    @Override
    public void play() {
        player.play();
    }

    @Override
    public void pause() {
        player.pause();
    }

    @Override
    public void next() {
        player.playNext();
    }

    @Override
    public void prev() {
        player.playPrevious();
    }

    @Override
    public void seekTo(int position) {
        player.seekTo(position);
    }

    @Override
    public boolean isPlaying() {
        return player.isPlaying();
    }

    @Override
    public boolean isActive() {
        return true; // Dahili oynatici her zaman aktiftir
    }

    @Override
    public String getTitle() {
        MusicRepository.AudioTrack track = player.getCurrentTrack();
        return track != null ? track.getTitle() : null;
    }

    @Override
    public String getArtist() {
        MusicRepository.AudioTrack track = player.getCurrentTrack();
        return track != null ? track.getArtist() : null;
    }

    @Override
    public Bitmap getAlbumArt() {
        return null;
    }

    @Override
    public int getDuration() {
        return player.getDuration();
    }

    @Override
    public int getPosition() {
        return player.getCurrentPosition();
    }

    @Override
    public String getPackageName() {
        return packageName;
    }
}

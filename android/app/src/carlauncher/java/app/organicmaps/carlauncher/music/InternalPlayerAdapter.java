package app.organicmaps.carlauncher.music;

import android.content.Context;
import android.graphics.Bitmap;

/**
 * Dahili oynatici adaptor sinifi.
 * BaseMediaAdapter arayuzunu uygular.
 */
public class InternalPlayerAdapter implements BaseMediaAdapter {

    private final Context context;
    private final InternalMusicPlayer player;
    private final String packageName;

    public InternalPlayerAdapter(Context context, InternalMusicPlayer player) {
        this.context = context;
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
        MusicRepository.AudioTrack track = player.getCurrentTrack();
        if (track == null || track.getAlbumArtUri() == null) return null;

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                return android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(context.getContentResolver(), track.getAlbumArtUri()));
            } else {
                return android.provider.MediaStore.Images.Media.getBitmap(context.getContentResolver(), track.getAlbumArtUri());
            }
        } catch (Exception e) {
            return null;
        }
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

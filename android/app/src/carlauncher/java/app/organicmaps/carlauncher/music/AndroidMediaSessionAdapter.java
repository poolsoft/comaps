package app.organicmaps.carlauncher.music;

import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.util.Log;

/**
 * Standard Android MediaSession calarlarini (Spotify, YT Music vb.)
 * saran adaptor sinifi.
 */
public class AndroidMediaSessionAdapter implements BaseMediaAdapter {

    private static final String TAG = "MediaSessionAdapter";
    private final MusicManager manager;

    public AndroidMediaSessionAdapter(MusicManager manager) {
        this.manager = manager;
    }

    private MediaController getController() {
        return manager.getActiveExternalController();
    }

    @Override
    public void play() {
        MediaController controller = getController();
        if (controller != null) {
            try {
                controller.getTransportControls().play();
            } catch (Exception e) {
                Log.e(TAG, "play hatasi", e);
            }
        }
    }

    @Override
    public void pause() {
        MediaController controller = getController();
        if (controller != null) {
            try {
                controller.getTransportControls().pause();
            } catch (Exception e) {
                Log.e(TAG, "pause hatasi", e);
            }
        }
    }

    @Override
    public void next() {
        MediaController controller = getController();
        if (controller != null) {
            try {
                controller.getTransportControls().skipToNext();
            } catch (Exception e) {
                Log.e(TAG, "next hatasi", e);
            }
        }
    }

    @Override
    public void prev() {
        MediaController controller = getController();
        if (controller != null) {
            try {
                controller.getTransportControls().skipToPrevious();
            } catch (Exception e) {
                Log.e(TAG, "prev hatasi", e);
            }
        }
    }

    @Override
    public void seekTo(int position) {
        MediaController controller = getController();
        if (controller != null) {
            try {
                controller.getTransportControls().seekTo(position);
            } catch (Exception e) {
                Log.e(TAG, "seekTo hatasi", e);
            }
        }
    }

    @Override
    public boolean isPlaying() {
        MediaController controller = getController();
        if (controller != null) {
            PlaybackState state = controller.getPlaybackState();
            return state != null && state.getState() == PlaybackState.STATE_PLAYING;
        }
        return false;
    }

    @Override
    public boolean isActive() {
        // Eger aktif bir kontrolcumuz varsa ve XYAuto degilse aktiftir
        MediaController controller = getController();
        if (controller != null) {
            String pkg = controller.getPackageName();
            return pkg != null && !"com.acloud.stub.localmusic".equals(pkg) && !"com.acloud.stub.localradio".equals(pkg);
        }
        return false;
    }

    @Override
    public String getTitle() {
        MediaController controller = getController();
        if (controller != null) {
            MediaMetadata metadata = controller.getMetadata();
            if (metadata != null) {
                return metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
            }
        }
        return null;
    }

    @Override
    public String getArtist() {
        MediaController controller = getController();
        if (controller != null) {
            MediaMetadata metadata = controller.getMetadata();
            if (metadata != null) {
                return metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
            }
        }
        return null;
    }

    @Override
    public Bitmap getAlbumArt() {
        MediaController controller = getController();
        if (controller != null) {
            MediaMetadata metadata = controller.getMetadata();
            if (metadata != null) {
                return metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            }
        }
        return null;
    }

    @Override
    public int getDuration() {
        MediaController controller = getController();
        if (controller != null) {
            MediaMetadata metadata = controller.getMetadata();
            if (metadata != null) {
                return (int) metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
            }
        }
        return 0;
    }

    @Override
    public int getPosition() {
        MediaController controller = getController();
        if (controller != null) {
            PlaybackState state = controller.getPlaybackState();
            if (state != null) {
                return (int) state.getPosition();
            }
        }
        return 0;
    }

    @Override
    public String getPackageName() {
        MediaController controller = getController();
        return controller != null ? controller.getPackageName() : null;
    }
}

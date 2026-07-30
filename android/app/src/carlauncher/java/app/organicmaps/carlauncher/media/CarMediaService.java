package app.organicmaps.carlauncher.media;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.media.browse.MediaBrowser;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Bundle;
import android.service.media.MediaBrowserService;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

import app.organicmaps.R;
import app.organicmaps.carlauncher.headunit.HardwareMediaKeyRouter;
import app.organicmaps.carlauncher.music.MusicManager;
import app.organicmaps.carlauncher.music.MusicRepository;
import android.util.Log;

/**
 * Standard MediaBrowserService that bridges Android Auto / Steering wheel media controls
 * with OsmAnd CarLauncher internal music playback (MusicManager / InternalMusicPlayer).
 */
public class CarMediaService extends MediaBrowserService implements MusicManager.MusicUIListener {

    public static final String CHANNEL_ID = "car_launcher_music_channel";
    public static final int NOTIFICATION_ID = 888;
    public static final String ACTION_PLAY_PAUSE =
            "app.organicmaps.carlauncher.media.action.PLAY_PAUSE";
    public static final String ACTION_NEXT =
            "app.organicmaps.carlauncher.media.action.NEXT";
    public static final String ACTION_PREVIOUS =
            "app.organicmaps.carlauncher.media.action.PREVIOUS";
    public static final String ACTION_CLOSE =
            "app.organicmaps.carlauncher.media.action.CLOSE";
    public static final String ACTION_DIAGNOSTIC_STATE_CHANGED =
            "app.organicmaps.carlauncher.action.DIAGNOSTIC_STATE_CHANGED";

    private MediaSession mediaSession;
    private MusicManager musicManager;
    private boolean notificationDismissed;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        musicManager = MusicManager.getInstance(getApplicationContext());

        mediaSession = new MediaSession(this, "CarMediaService");
        mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS |
                MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setCallback(new MediaSessionCallback());
        mediaSession.setPlaybackState(buildPlaybackState(PlaybackState.STATE_PAUSED));
        setSessionToken(mediaSession.getSessionToken());
        musicManager.addListener(this);
        updateSessionActive();
        updateSessionMetadata(null);
        Log.i("CarMediaService", "created active=" + mediaSession.isActive());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_PLAY_PAUSE.equals(action)) {
            musicManager.handleInternalMediaKey(
                    android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        } else if (ACTION_NEXT.equals(action)) {
            musicManager.handleInternalMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_NEXT);
        } else if (ACTION_PREVIOUS.equals(action)) {
            musicManager.handleInternalMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS);
        } else if (ACTION_CLOSE.equals(action)) {
            notificationDismissed = true;
            if (musicManager.getInternalPlayer().isPlaying()) {
                musicManager.getInternalPlayer().pause();
            }
            stopForeground(true);
        } else if (ACTION_DIAGNOSTIC_STATE_CHANGED.equals(action)) {
            Log.d("CarMediaService", "diagnostic_state_changed");
        }
        updateSessionActive();
        updateSessionMetadata(null);
        updateNotification();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
        }
        if (musicManager != null) {
            musicManager.removeListener(this);
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public BrowserRoot onGetRoot(@NonNull String clientPackageName, int clientUid, @Nullable Bundle rootHints) {
        return new BrowserRoot("CAR_ROOT", null);
    }

    @Override
    public void onLoadChildren(@NonNull String parentId, @NonNull Result<List<MediaBrowser.MediaItem>> result) {
        result.sendResult(Collections.emptyList());
    }

    private PlaybackState buildPlaybackState(int state) {
        return new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
                        | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_SKIP_TO_NEXT
                        | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_STOP)
                .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                        state == PlaybackState.STATE_PLAYING ? 1.0f : 0.0f)
                .build();
    }

    private void updateSessionActive() {
        if (mediaSession != null) {
            mediaSession.setActive(musicManager != null
                    && musicManager.shouldOwnHardwareMediaSession());
        }
    }

    @Override
    public void onTrackChanged(String title, String artist, Bitmap albumArt,
            String packageName) {
        updateSessionMetadata("usage.internal.player".equals(packageName) ? albumArt : null);
        updateNotification();
    }

    @Override
    public void onPlaybackStateChanged(boolean isPlaying) {
        if (mediaSession != null) {
            boolean internalPlaying = musicManager.getInternalPlayer().isPlaying();
            mediaSession.setPlaybackState(buildPlaybackState(internalPlaying
                    ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED));
        }
        updateSessionActive();
        updateSessionMetadata(null);
        updateNotification();
    }

    @Override
    public void onSourceChanged(boolean isInternal) {
        updateSessionActive();
        updateSessionMetadata(null);
        updateNotification();
    }

    private void updateSessionMetadata(@Nullable Bitmap albumArt) {
        if (mediaSession == null || musicManager == null) {
            return;
        }
        MusicRepository.AudioTrack track = musicManager.getInternalPlayer().getCurrentTrack();
        MediaMetadata.Builder builder = new MediaMetadata.Builder();
        if (track != null) {
            builder.putString(MediaMetadata.METADATA_KEY_TITLE, track.getTitle());
            builder.putString(MediaMetadata.METADATA_KEY_ARTIST, track.getArtist());
            builder.putString(MediaMetadata.METADATA_KEY_ALBUM, track.getAlbum());
            builder.putLong(MediaMetadata.METADATA_KEY_DURATION, track.getDuration());
            if (albumArt != null) {
                builder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, albumArt);
            }
        }
        mediaSession.setMetadata(builder.build());
    }

    private void updateNotification() {
        if (musicManager == null || mediaSession == null
                || !musicManager.shouldOwnHardwareMediaSession()) {
            stopForeground(true);
            return;
        }

        MusicRepository.AudioTrack track = musicManager.getInternalPlayer().getCurrentTrack();
        if (track == null) {
            stopForeground(true);
            return;
        }

        boolean isPlaying = musicManager.getInternalPlayer().isPlaying();
        if (isPlaying) {
            notificationDismissed = false;
        } else if (notificationDismissed) {
            stopForeground(true);
            return;
        }
        PendingIntent previousIntent = createServicePendingIntent(
                ACTION_PREVIOUS, 1);
        PendingIntent playPauseIntent = createServicePendingIntent(
                ACTION_PLAY_PAUSE, 2);
        PendingIntent nextIntent = createServicePendingIntent(ACTION_NEXT, 3);
        PendingIntent closeIntent = createServicePendingIntent(ACTION_CLOSE, 4);

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        builder.setContentTitle(track.getTitle())
                .setContentText(track.getArtist())
                .setSmallIcon(R.drawable.ic_music_play)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .setOnlyAlertOnce(true)
                .setOngoing(isPlaying)
                .setDeleteIntent(closeIntent)
                .setStyle(new Notification.MediaStyle()
                        .setShowActionsInCompactView(0, 1, 2)
                        .setMediaSession(mediaSession.getSessionToken()))
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(this, android.R.drawable.ic_media_previous),
                        getString(R.string.car_media_action_previous), previousIntent).build())
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(this, isPlaying
                                ? android.R.drawable.ic_media_pause
                                : android.R.drawable.ic_media_play),
                        getString(isPlaying ? R.string.car_media_action_pause
                                : R.string.car_media_action_play), playPauseIntent).build())
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(this, android.R.drawable.ic_media_next),
                        getString(R.string.car_media_action_next), nextIntent).build())
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                        getString(R.string.car_media_action_close), closeIntent).build());

        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (launchIntent != null) {
            builder.setContentIntent(PendingIntent.getActivity(this, 5, launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        }
        startForeground(NOTIFICATION_ID, builder.build());
    }

    private PendingIntent createServicePendingIntent(String action, int requestCode) {
        Intent intent = new Intent(this, CarMediaService.class).setAction(action);
        return PendingIntent.getService(this, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, getString(R.string.car_media_channel_name),
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(getString(R.string.car_media_channel_description));
            channel.setSound(null, null);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private class MediaSessionCallback extends MediaSession.Callback {
        @Override
        public void onPlay() {
            routeHardwareKey(android.view.KeyEvent.KEYCODE_MEDIA_PLAY);
        }

        @Override
        public void onPause() {
            routeHardwareKey(android.view.KeyEvent.KEYCODE_MEDIA_PAUSE);
        }

        @Override
        public void onSkipToNext() {
            routeHardwareKey(android.view.KeyEvent.KEYCODE_MEDIA_NEXT);
        }

        @Override
        public void onSkipToPrevious() {
            routeHardwareKey(android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS);
        }

        @Override
        public void onStop() {
            routeHardwareKey(android.view.KeyEvent.KEYCODE_MEDIA_STOP);
        }

        @Override
        public boolean onMediaButtonEvent(@NonNull Intent mediaButtonIntent) {
            android.view.KeyEvent keyEvent = (android.view.KeyEvent)
                    mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
            if (keyEvent == null) {
                return false;
            }
            if (keyEvent.getAction() == android.view.KeyEvent.ACTION_UP) {
                return true;
            }
            return routeHardwareKey(keyEvent.getKeyCode());
        }

        private boolean routeHardwareKey(int keyCode) {
            return HardwareMediaKeyRouter.getInstance(CarMediaService.this).route(
                    HardwareMediaKeyRouter.Source.MEDIA_SESSION, keyCode);
        }
    }
}

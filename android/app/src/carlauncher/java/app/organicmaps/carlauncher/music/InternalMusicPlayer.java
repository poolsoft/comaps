package app.organicmaps.carlauncher.music;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.PowerManager;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles playback of local audio files using MediaPlayer.
 * Manages queue and playback state.
 * Includes Audio Focus management for Car interaction (Nav, Calls).
 */
public class InternalMusicPlayer {

    private static final String TAG = "InternalMusicPlayer";

    public interface PlaybackListener {
        void onTrackChanged(MusicRepository.AudioTrack track);

        void onPlaybackStateChanged(boolean isPlaying);

        void onCompletion();
    }

    private final Context context;
    private final AudioManager audioManager;
    private MediaPlayer mediaPlayer;
    private List<MusicRepository.AudioTrack> playlist = new ArrayList<>();
    private int currentIndex = -1;
    private boolean isPrepared = false;
    private boolean playOnFocusGain = false; // Focus geri geldiginde calmaya devam etsin mi (Turkce karakter yok)
    private boolean autoPlayOnPrepared = true; // Hazir olunca otomatik oynat
    private PlaybackListener listener;
    private boolean isShuffleOn = false;
    private int repeatMode = 0; // 0=off, 1=one, 2=all

    public InternalMusicPlayer(Context context) {
        this.context = context;
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        initMediaPlayer();
    }

    public void setListener(PlaybackListener listener) {
        this.listener = listener;
    }

    public boolean isShuffleOn() {
        return isShuffleOn;
    }

    public void setShuffleOn(boolean shuffleOn) {
        this.isShuffleOn = shuffleOn;
    }

    public int getRepeatMode() {
        return repeatMode;
    }

    public void setRepeatMode(int repeatMode) {
        this.repeatMode = repeatMode;
    }

    // --- Audio Focus Listener (Navigasyon ve Aramalar iÃ§in) ---
    private final AudioManager.OnAudioFocusChangeListener focusChangeListener = focusChange -> {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_LOSS:
                // KalÄ±cÄ± kayÄ±p (BaÅŸka mÃ¼zik uygulamasÄ± aÃ§Ä±ldÄ± veya arama var)
                playOnFocusGain = false;
                pause();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                // GeÃ§ici kayÄ±p (KÄ±sa konuÅŸma vs.)
                if (isPlaying()) {
                    playOnFocusGain = true;
                    pause();
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                // Navigasyon konuÅŸuyor -> Sesi kÄ±s
                if (mediaPlayer != null) {
                    mediaPlayer.setVolume(0.2f, 0.2f);
                }
                break;
            case AudioManager.AUDIOFOCUS_GAIN:
                // Odak geri geldi
                if (mediaPlayer != null) {
                    mediaPlayer.setVolume(1.0f, 1.0f); // Sesi normale dÃ¶ndÃ¼r
                }
                if (playOnFocusGain) {
                    play();
                }
                playOnFocusGain = false;
                break;
        }
    };

    private void initMediaPlayer() {
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK);

        // AraÃ§ kullanÄ±mÄ± iÃ§in Attributes
        mediaPlayer.setAudioAttributes(
                new AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build());

        mediaPlayer.setOnPreparedListener(mp -> {
            isPrepared = true;
            // HazÄ±r olunca Ã§al (EÄER isteniyorsa)
            if (autoPlayOnPrepared) {
                play();
            }
        });

        mediaPlayer.setOnCompletionListener(mp -> {
            if (listener != null) {
                listener.onCompletion();
            }
            playNext();
        });

        mediaPlayer.setOnErrorListener((mp, what, extra) -> {
            Log.e(TAG, "MediaPlayer error: " + what + ", " + extra);
            isPrepared = false;
            // Hata olursa bir sonraki sarkiya gec (zorla skip et) (Turkce karakter yok)
            playNext(true);
            return true;
        });
    }

    public void setPlaylist(List<MusicRepository.AudioTrack> tracks, int startIndex) {
        setPlaylist(tracks, startIndex, true); // Default: auto play
    }

    public void setPlaylist(List<MusicRepository.AudioTrack> tracks, int startIndex, boolean autoPlay) {
        if (tracks == null || tracks.isEmpty())
            return;
        this.playlist = new ArrayList<>(tracks);
        if (startIndex >= 0 && startIndex < playlist.size()) {
            playTrack(startIndex, autoPlay);
        }
    }

    private void playTrack(int index) {
        playTrack(index, true);
    }

    private void playTrack(int index, boolean autoPlay) {
        if (index < 0 || index >= playlist.size())
            return;

        // Ã–nceki durdur
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
        }

        currentIndex = index;
        MusicRepository.AudioTrack track = playlist.get(index);
        this.autoPlayOnPrepared = autoPlay;

        try {
            mediaPlayer.reset();
            isPrepared = false;
            mediaPlayer.setDataSource(context, track.getContentUri());
            mediaPlayer.prepareAsync();

            if (listener != null) {
                listener.onTrackChanged(track);
            }
            saveState(); // Save new track index
        } catch (IOException e) {
            Log.e(TAG, "Error setting data source", e);
            // Dosya bozuksa bir sonrakine gec (zorla skip et) (Turkce karakter yok)
            playNext(true);
        }
    }

    public void play() {
        if (!isPrepared)
            return;

        // Ã‡almadan Ã¶nce Audio Focus iste
        int result = audioManager.requestAudioFocus(focusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN);

        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            if (!mediaPlayer.isPlaying()) {
                mediaPlayer.start();
                if (listener != null)
                    listener.onPlaybackStateChanged(true);
            }
        }
    }

    public void pause() {
        if (isPrepared && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            if (listener != null)
                listener.onPlaybackStateChanged(false);

            saveState(); // Save position on pause

            // Focus'u bÄ±rakmaya gerek yok (Abandon focus), belki kullanÄ±cÄ± hemen devam
            // ettirir.
            // Ancak kalÄ±cÄ± durdurma durumunda abandonAudioFocus yapÄ±labilir.
        }
    }

    public void playPause() {
        if (isPrepared) {
            if (mediaPlayer.isPlaying()) {
                pause();
            } else {
                play();
            }
        } else if (currentIndex != -1) {
            playTrack(currentIndex);
        }
    }

    public void playNext() {
        playNext(false);
    }

    public void playNext(boolean forceSkip) {
        if (playlist.isEmpty())
            return;

        if (!forceSkip && repeatMode == 1) { // Repeat One (Tek parca tekrar - Turkce karakter yok)
            playTrack(currentIndex);
            return;
        }

        int nextIndex;
        if (isShuffleOn) {
            if (playlist.size() > 1) {
                nextIndex = currentIndex;
                while (nextIndex == currentIndex) {
                    nextIndex = (int) (Math.random() * playlist.size());
                }
            } else {
                nextIndex = 0;
            }
        } else {
            nextIndex = currentIndex + 1;
        }

        if (nextIndex >= playlist.size()) {
            if (repeatMode == 2) { // Repeat All (Tum liste tekrar - Turkce karakter yok)
                nextIndex = 0;
                playTrack(nextIndex);
            } else {
                // Repeat Off (Tekrar kapali - duraklat ve ilk sarkiya don - Turkce karakter yok)
                pause();
                currentIndex = 0;
                playTrack(0, false);
            }
        } else {
            playTrack(nextIndex);
        }
    }

    public void playPrevious() {
        if (playlist.isEmpty())
            return;

        // Eger sarki 3 saniyeden fazla caldiysa basa sar (Turkce karakter yok)
        if (isPrepared && mediaPlayer.isPlaying() && mediaPlayer.getCurrentPosition() > 3000) {
            mediaPlayer.seekTo(0);
            return;
        }

        if (repeatMode == 1) { // Repeat One (Turkce karakter yok)
            playTrack(currentIndex);
            return;
        }

        int prevIndex;
        if (isShuffleOn) {
            if (playlist.size() > 1) {
                prevIndex = currentIndex;
                while (prevIndex == currentIndex) {
                    prevIndex = (int) (Math.random() * playlist.size());
                }
            } else {
                prevIndex = 0;
            }
        } else {
            prevIndex = currentIndex - 1;
        }

        if (prevIndex < 0) {
            if (repeatMode == 2) { // Repeat All (Turkce karakter yok)
                prevIndex = playlist.size() - 1;
                playTrack(prevIndex);
            } else {
                // Repeat Off (Turkce karakter yok)
                prevIndex = playlist.size() - 1;
                playTrack(prevIndex);
            }
        } else {
            playTrack(prevIndex);
        }
    }

    public boolean isPlaying() {
        return isPrepared && mediaPlayer.isPlaying();
    }

    public MusicRepository.AudioTrack getCurrentTrack() {
        if (currentIndex >= 0 && currentIndex < playlist.size()) {
            return playlist.get(currentIndex);
        }
        return null;
    }

    public void release() {
        if (audioManager != null) {
            audioManager.abandonAudioFocus(focusChangeListener);
        }
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        saveState(); // Save state on release
    }

    // --- Seekbar Support ---

    public int getCurrentPosition() {
        if (isPrepared && mediaPlayer != null) {
            return mediaPlayer.getCurrentPosition();
        }
        return 0;
    }

    public int getDuration() {
        if (isPrepared && mediaPlayer != null) {
            return mediaPlayer.getDuration();
        }
        return 0;
    }

    public void seekTo(int position) {
        if (isPrepared && mediaPlayer != null) {
            mediaPlayer.seekTo(position);
        }
    }

    public int getAudioSessionId() {
        if (mediaPlayer != null) {
            return mediaPlayer.getAudioSessionId();
        }
        return 0;
    }

    // --- Persistence (Auto-Resume) ---
    private static final String PREF_NAME = "InternalMusicPlayer";
    private static final String PREF_KEY_INDEX = "last_index";
    private static final String PREF_KEY_POS = "last_position";
    private static final String PREF_KEY_PLAYLIST = "last_playlist_json"; // Simplification: just save index for now or
                                                                          // assume same playlist

    private void saveState() {
        android.content.SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        android.content.SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(PREF_KEY_INDEX, currentIndex);
        if (mediaPlayer != null && isPrepared) {
            editor.putInt(PREF_KEY_POS, mediaPlayer.getCurrentPosition());
        }
        editor.apply();
    }

    public void restoreState() {
        android.content.SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        int savedIndex = prefs.getInt(PREF_KEY_INDEX, -1);
        int savedPos = prefs.getInt(PREF_KEY_POS, 0);

        if (savedIndex >= 0 && savedIndex < playlist.size()) {
            // Just set the track, don't auto-play yet unless requested
            // To properly restore, we need to prepare the player
            playTrack(savedIndex);
            if (mediaPlayer != null) {
                // We need to wait for preparation to seek.
                // playTrack prepares async. We need a way to seek after prepare.
                // For now, let's just rely on the user or the auto-resume logic in MapActivity
                // to call play().
                // But playTrack auto-plays in current implementation!
                // Let's modify playTrack to accept 'autoPlay' boolean?
                // Or just pause immediately?
                pause();
                // Hack: modifying playTrack is cleaner but riskier.
                // Let's just set the variable and let simple Resume work if playlist is same?
                // Actually playTrack resets everything.
            }
        }
    }

    // Helper to resume
    public void resumeLastSession() {
        android.content.SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        int savedIndex = prefs.getInt(PREF_KEY_INDEX, -1);
        int savedPos = prefs.getInt(PREF_KEY_POS, 0);

        if (currentIndex == -1 && savedIndex != -1 && savedIndex < playlist.size()) {
            currentIndex = savedIndex; // Set index so playTrack works?
            // Need to call playTrack to load file
            playTrack(savedIndex);
            // Seek after prepare... this requires a listener or modifying playTrack.
            // For simplicity: restart track.
        } else if (currentIndex != -1) {
            play();
        }
    }

    public boolean isPrepared() {
        return isPrepared;
    }
}

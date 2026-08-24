package app.organicmaps.carlauncher.music;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.PowerManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;

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
    private List<MusicRepository.AudioTrack> playingQueue = new ArrayList<>();
    private int currentIndex = -1;
    private boolean isPrepared = false;
    private boolean playOnFocusGain = false; // Focus geri geldiginde calmaya devam etsin mi (Turkce karakter yok)
    private boolean autoPlayOnPrepared = true; // Hazir olunca otomatik oynat
    private boolean pendingPlayRequest = false;
    private PlaybackListener listener;
    private boolean isShuffleOn = false;
    private int repeatMode = 0; // 0=off, 1=one, 2=all
    private int pendingSeekPosition = 0; // Hazir oldugunda atlanacak saniye (Turkce karakter yok)
    private boolean wasPlayingBefore = false; // Son kapanista caliyor muydu? (Turkce karakter yok)

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
        if (this.isShuffleOn != shuffleOn) {
            this.isShuffleOn = shuffleOn;
            rebuildQueue();
            saveState();
        }
    }

    public int getRepeatMode() {
        return repeatMode;
    }

    public void setRepeatMode(int repeatMode) {
        if (this.repeatMode != repeatMode) {
            this.repeatMode = repeatMode;
            saveState();
        }
    }

    public List<MusicRepository.AudioTrack> getPlayingQueue() {
        return playingQueue.isEmpty() ? playlist : playingQueue;
    }

    public void playNext(MusicRepository.AudioTrack track) {
        playNextInQueue(track);
    }

    public void removeFromQueue(MusicRepository.AudioTrack track) {
        if (track == null) return;
        List<MusicRepository.AudioTrack> queue = getPlayingQueue();
        int removedIndex = queue.indexOf(track);
        if (removedIndex != -1) {
            queue.remove(removedIndex);
            if (currentIndex > removedIndex) {
                currentIndex--;
            } else if (currentIndex == removedIndex) {
                if (!queue.isEmpty()) {
                    if (currentIndex >= queue.size()) currentIndex = 0;
                    playTrack(currentIndex, true, 0);
                } else {
                    pause();
                }
            }
            saveState();
        }
    }

    private void rebuildQueue() {
        if (playlist.isEmpty()) {
            playingQueue.clear();
            return;
        }
        if (isShuffleOn) {
            MusicRepository.AudioTrack currentTrack = getCurrentTrack();
            List<MusicRepository.AudioTrack> rest = new ArrayList<>(playlist);
            if (currentTrack != null) {
                rest.remove(currentTrack);
            }
            java.util.Collections.shuffle(rest);
            playingQueue.clear();
            if (currentTrack != null) {
                playingQueue.add(currentTrack);
            }
            playingQueue.addAll(rest);
            currentIndex = 0;
        } else {
            MusicRepository.AudioTrack currentTrack = getCurrentTrack();
            playingQueue = new ArrayList<>(playlist);
            if (currentTrack != null) {
                int idx = playingQueue.indexOf(currentTrack);
                if (idx != -1) currentIndex = idx;
            }
        }
    }

    // --- Audio Focus Listener (Navigasyon ve Aramalar için) ---
    private final AudioManager.OnAudioFocusChangeListener focusChangeListener = focusChange -> {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_LOSS:
                // Kalıcı kayıp (Başka müzik uygulaması açıldı veya arama var)
                playOnFocusGain = false;
                pause();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                // Geçici kayıp (Kısa konuşma vs.)
                if (isPlaying()) {
                    playOnFocusGain = true;
                    pause();
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                // Navigasyon konuşuyor -> Sesi kıs
                if (mediaPlayer != null) {
                    mediaPlayer.setVolume(0.2f, 0.2f);
                }
                break;
            case AudioManager.AUDIOFOCUS_GAIN:
                // Odak geri geldi
                if (mediaPlayer != null) {
                    mediaPlayer.setVolume(1.0f, 1.0f); // Sesi normale döndür
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

        // Araç kullanımı için Attributes
        mediaPlayer.setAudioAttributes(
                new AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build());

        mediaPlayer.setOnPreparedListener(mp -> {
            isPrepared = true;
            if (pendingSeekPosition > 0) {
                mediaPlayer.seekTo(pendingSeekPosition);
                pendingSeekPosition = 0;
            }
            // Hazir olunca cal (EGER isteniyorsa) (Turkce karakter yok)
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

    /**
     * Supplies the scanned library before {@link #restoreState()} without
     * preparing a different track or overwriting the saved session.
     */
    public synchronized void setLibraryForRestore(List<MusicRepository.AudioTrack> tracks) {
        if (tracks == null || tracks.isEmpty()) {
            return;
        }
        playlist = new ArrayList<>(tracks);
        playingQueue = new ArrayList<>(tracks);
        currentIndex = -1;
    }

    public void setPlaylist(List<MusicRepository.AudioTrack> tracks, int startIndex, boolean autoPlay) {
        if (tracks == null || tracks.isEmpty() || startIndex < 0 || startIndex >= tracks.size())
            return;
        MusicRepository.AudioTrack targetTrack = tracks.get(startIndex);
        playTrackFromCollection(tracks, targetTrack, autoPlay);
    }

    public synchronized boolean playTrackFromCollection(List<MusicRepository.AudioTrack> collection,
            MusicRepository.AudioTrack selectedTrack, boolean autoPlay) {
        PlaybackQueueBuilder.Selection selection = PlaybackQueueBuilder.select(collection, selectedTrack);
        if (selection == null) {
            Log.w(TAG, "Selected track is not present in the source list; queue was not changed");
            return false;
        }

        this.playlist = new ArrayList<>(selection.getQueue());
        int selectedIndex = selection.getSelectedIndex();

        if (isShuffleOn) {
            List<MusicRepository.AudioTrack> rest = new ArrayList<>(selection.getQueue());
            rest.remove(selectedIndex);
            java.util.Collections.shuffle(rest);
            playingQueue.clear();
            playingQueue.add(selection.getSelectedTrack()); // Always position 0 in shuffle
            playingQueue.addAll(rest);
            currentIndex = 0;
        } else {
            playingQueue = new ArrayList<>(selection.getQueue()); // Full source snapshot as Queue
            currentIndex = selectedIndex; // Active track index
        }

        playTrack(currentIndex, autoPlay, 0);
        return true;
    }

    public synchronized void playTrackInQueue(int queueIndex) {
        List<MusicRepository.AudioTrack> queue = getPlayingQueue();
        if (queueIndex >= 0 && queueIndex < queue.size()) {
            playTrack(queueIndex, true, 0);
        }
    }

    /**
     * Sarkiyi mevcut oynatma listesinde bir sonraki siraya ekler (Play Next).
     */
    public boolean playNextInQueue(MusicRepository.AudioTrack track) {
        if (track == null) return false;
        List<MusicRepository.AudioTrack> queue = getPlayingQueue();
        if (queue.isEmpty()) {
            List<MusicRepository.AudioTrack> single = new ArrayList<>();
            single.add(track);
            setPlaylist(single, 0, true);
            return true;
        }
        int insertIndex = currentIndex + 1;
        if (insertIndex > queue.size()) {
            insertIndex = queue.size();
        }
        queue.add(insertIndex, track);
        saveState();
        return true;
    }

    /**
     * Sarkiyi mevcut oynatma listesinin en sonuna ekler (Add to Queue).
     */
    public boolean addToQueue(MusicRepository.AudioTrack track) {
        if (track == null) return false;
        List<MusicRepository.AudioTrack> queue = getPlayingQueue();
        if (queue.isEmpty()) {
            List<MusicRepository.AudioTrack> single = new ArrayList<>();
            single.add(track);
            setPlaylist(single, 0, true);
            return true;
        }
        queue.add(track);
        saveState();
        return true;
    }

    /** Adds a collection atomically while preserving its visible list order. */
    public synchronized boolean addTracksToQueue(List<MusicRepository.AudioTrack> tracks,
                                                  boolean afterCurrentTrack) {
        if (tracks == null || tracks.isEmpty()) return false;
        List<MusicRepository.AudioTrack> additions = new ArrayList<>();
        for (MusicRepository.AudioTrack track : tracks) {
            if (track != null) additions.add(track);
        }
        if (additions.isEmpty()) return false;

        List<MusicRepository.AudioTrack> queue = getPlayingQueue();
        if (queue.isEmpty()) {
            setPlaylist(additions, 0, true);
            return true;
        }
        if (afterCurrentTrack) {
            int insertIndex = Math.max(0, Math.min(currentIndex + 1, queue.size()));
            queue.addAll(insertIndex, additions);
        } else {
            queue.addAll(additions);
        }
        playlist = new ArrayList<>(queue);
        playingQueue = new ArrayList<>(queue);
        saveState();
        return true;
    }

    private void playTrack(int index) {
        playTrack(index, true, 0);
    }

    private void playTrack(int index, boolean autoPlay) {
        playTrack(index, autoPlay, 0);
    }

    private void playTrack(int index, boolean autoPlay, int seekPosition) {
        List<MusicRepository.AudioTrack> queue = getPlayingQueue();
        if (index < 0 || index >= queue.size())
            return;

        int requestedIndex = index;
        int attempts = 0;
        while (attempts < queue.size() && !queue.get(index).isAvailable()) {
            index = (index + 1) % queue.size();
            attempts++;
        }
        if (attempts >= queue.size()) {
            currentIndex = requestedIndex;
            isPrepared = false;
            if (listener != null) {
                listener.onPlaybackStateChanged(false);
            }
            saveState();
            return;
        }
        if (index != requestedIndex) {
            seekPosition = 0;
        }

        // Onceki durdur (Turkce karakter yok)
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
        }

        currentIndex = index;
        MusicRepository.AudioTrack track = queue.get(index);
        this.autoPlayOnPrepared = autoPlay || pendingPlayRequest;
        this.pendingSeekPosition = seekPosition;

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
        if (!isPrepared) {
            pendingPlayRequest = true;
            return;
        }

        // Çalmadan önce Audio Focus iste
        int result = audioManager.requestAudioFocus(focusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN);

        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            pendingPlayRequest = false;
            if (!mediaPlayer.isPlaying()) {
                mediaPlayer.start();
                if (listener != null)
                    listener.onPlaybackStateChanged(true);
            }
        }
    }

    public void pause() {
        pendingPlayRequest = false;
        if (isPrepared && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            if (listener != null)
                listener.onPlaybackStateChanged(false);

            saveState(); // Save position on pause

            // Focus'u bırakmaya gerek yok (Abandon focus), belki kullanıcı hemen devam
            // ettirir.
            // Ancak kalıcı durdurma durumunda abandonAudioFocus yapılabilir.
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
        List<MusicRepository.AudioTrack> queue = getPlayingQueue();
        if (queue.isEmpty())
            return;

        if (!forceSkip && repeatMode == 1) { // Repeat One (Tek parca tekrar - Turkce karakter yok)
            playTrack(currentIndex);
            return;
        }

        int nextIndex = currentIndex + 1;

        if (nextIndex >= queue.size()) {
            if (repeatMode == 2) { // Repeat All (Tum liste tekrar - Turkce karakter yok)
                if (isShuffleOn) {
                    rebuildQueue();
                }
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
        List<MusicRepository.AudioTrack> queue = getPlayingQueue();
        if (queue.isEmpty())
            return;

        if (repeatMode == 1) { // Repeat One (Turkce karakter yok)
            playTrack(currentIndex);
            return;
        }

        int prevIndex = currentIndex - 1;

        if (prevIndex < 0) {
            prevIndex = queue.size() - 1;
        }
        playTrack(prevIndex);
    }

    public void toggleRepeat() {
        this.repeatMode = (this.repeatMode + 1) % 3; // 0: Off, 1: Repeat One, 2: Repeat All
        saveState();
    }

    public boolean isPlaying() {
        return isPrepared && mediaPlayer.isPlaying();
    }

    public MusicRepository.AudioTrack getCurrentTrack() {
        List<MusicRepository.AudioTrack> queue = getPlayingQueue();
        if (currentIndex >= 0 && currentIndex < queue.size()) {
            return queue.get(currentIndex);
        }
        return null;
    }

    public void release() {
        saveState(); // Save state BEFORE releasing mediaPlayer
        if (audioManager != null) {
            audioManager.abandonAudioFocus(focusChangeListener);
        }
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
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

    public boolean wasPlayingBefore() {
        return wasPlayingBefore;
    }

    // --- Persistence (Auto-Resume) ---
    private static final String PREF_NAME = "InternalMusicPlayer";
    private static final String PREF_KEY_INDEX = "last_index";
    private static final String PREF_KEY_POS = "last_position";
    private static final String PREF_KEY_WAS_PLAYING = "last_was_playing";
    private static final String PREF_KEY_SHUFFLE = "is_shuffle";
    private static final String PREF_KEY_REPEAT = "repeat_mode";
    private static final String PREF_KEY_MEDIA_ID = "last_media_id";
    private static final String PREF_KEY_QUEUE = "last_queue";

    private void saveState() {
        android.content.SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        android.content.SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(PREF_KEY_INDEX, currentIndex);
        editor.putBoolean(PREF_KEY_SHUFFLE, isShuffleOn);
        editor.putInt(PREF_KEY_REPEAT, repeatMode);
        MusicRepository.AudioTrack currentTrack = getCurrentTrack();
        if (currentTrack != null) {
            editor.putString(PREF_KEY_MEDIA_ID, currentTrack.getMediaId());
        }
        JSONArray savedQueue = new JSONArray();
        for (MusicRepository.AudioTrack track : getPlayingQueue()) {
            savedQueue.put(track.getMediaId());
        }
        editor.putString(PREF_KEY_QUEUE, savedQueue.toString());

        // Eger parca hazirsa (isPrepared), o anki saniyesini ve calip calmadigini kaydet (Turkce karakter yok)
        if (mediaPlayer != null && isPrepared) {
            editor.putInt(PREF_KEY_POS, mediaPlayer.getCurrentPosition());
            editor.putBoolean(PREF_KEY_WAS_PLAYING, mediaPlayer.isPlaying());
        }
        editor.apply();
    }

    public void restoreState() {
        android.content.SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        int savedIndex = prefs.getInt(PREF_KEY_INDEX, -1);
        String savedMediaId = prefs.getString(PREF_KEY_MEDIA_ID, null);
        int savedPos = prefs.getInt(PREF_KEY_POS, 0);
        this.isShuffleOn = prefs.getBoolean(PREF_KEY_SHUFFLE, false);
        this.repeatMode = prefs.getInt(PREF_KEY_REPEAT, 0);
        this.wasPlayingBefore = prefs.getBoolean(PREF_KEY_WAS_PLAYING, false);

        if (!playlist.isEmpty()) {
            List<MusicRepository.AudioTrack> restoredQueue =
                    restoreQueue(prefs.getString(PREF_KEY_QUEUE, "[]"));
            if (!restoredQueue.isEmpty()) {
                playlist = new ArrayList<>(restoredQueue);
                playingQueue = new ArrayList<>(restoredQueue);
            } else {
                rebuildQueue();
            }
            int restoredIndex = findQueueIndex(savedMediaId);
            if (restoredIndex < 0) {
                restoredIndex = savedIndex;
            }
            if (restoredIndex >= 0 && restoredIndex < playingQueue.size()) {
                // Son sarkiyi o anki saniyesinden geri yukle, hemen baslatma mantigi MusicManager'da belirlenecek (Turkce karakter yok)
                playTrack(restoredIndex, false, savedPos);
            } else if (pendingPlayRequest && !playingQueue.isEmpty()) {
                playTrack(0, true, 0);
            }
        }
    }

    private List<MusicRepository.AudioTrack> restoreQueue(String savedQueueJson) {
        List<MusicRepository.AudioTrack> restored = new ArrayList<>();
        try {
            JSONArray references = new JSONArray(savedQueueJson);
            for (int i = 0; i < references.length(); i++) {
                String mediaId = references.optString(i, null);
                if (mediaId == null) continue;
                for (MusicRepository.AudioTrack track : playlist) {
                    if (MusicTrackIdentity.matchesReference(mediaId, track)) {
                        restored.add(track);
                        break;
                    }
                }
            }
        } catch (JSONException e) {
            Log.w(TAG, "Unable to restore saved playback queue", e);
        }
        return restored;
    }

    private int findQueueIndex(String mediaId) {
        if (mediaId == null) return -1;
        List<MusicRepository.AudioTrack> queue = getPlayingQueue();
        for (int i = 0; i < queue.size(); i++) {
            if (MusicTrackIdentity.matchesReference(mediaId, queue.get(i))) {
                return i;
            }
        }
        return -1;
    }

    // Helper to resume
    public void resumeLastSession() {
        if (currentIndex != -1) {
            play();
        }
    }

    public boolean isPrepared() {
        return isPrepared;
    }
}

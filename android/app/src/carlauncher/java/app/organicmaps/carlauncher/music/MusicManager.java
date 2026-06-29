package app.organicmaps.carlauncher.music;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Merkezi Muzik Yoneticisi.
 * Evrensel Adaptor tasarim kalibi ile calisir.
 */
public class MusicManager implements InternalMusicPlayer.PlaybackListener {

    private static final String TAG = "MusicManager";
    private static MusicManager instance;

    private final Context context;
    private final MusicRepository repository;
    private final InternalMusicPlayer internalPlayer;
    private MediaSessionManager mediaSessionManager;
    private MediaController activeExternalController;

    private boolean isInternalPlaying = false;
    private String preferredPackage;

    private final List<BaseMediaAdapter> adapters = new ArrayList<>();
    private final List<MusicUIListener> listeners = new CopyOnWriteArrayList<>();

    private enum MusicSource {
        INTERNAL,
        EXTERNAL
    }
    
    private MusicSource lastActiveSource = MusicSource.INTERNAL;

    public interface MusicUIListener {
        void onTrackChanged(String title, String artist, android.graphics.Bitmap albumArt, String packageName);
        void onPlaybackStateChanged(boolean isPlaying);
        void onSourceChanged(boolean isInternal);
    }

    private MusicManager(Context context) {
        this.context = context.getApplicationContext();
        this.repository = new MusicRepository(this.context);
        this.internalPlayer = new InternalMusicPlayer(this.context);
        this.internalPlayer.setListener(this);

        setupMediaSessionManager();

        // Adaptorleri sirayla ekle
        adapters.add(new InternalPlayerAdapter(this.context, internalPlayer));
        adapters.add(new AndroidMediaSessionAdapter(this));
        adapters.add(new XyAutoMusicAdapter(this.context, this));
        adapters.add(new XyAutoRadioAdapter(this.context, this));
        adapters.add(new HcnMusicAdapter(this.context, this));
        adapters.add(new HcnRadioAdapter(this.context, this));
        adapters.add(new UniversalBluetoothAdapter(this.context, this));

        // Baslangicta muzikleri tara
        repository.scanMusic((tracks, folders, artists) -> {
            Log.d(TAG, "Scan complete: " + tracks.size() + " tracks");
            if (!tracks.isEmpty()) {
                internalPlayer.setPlaylist(tracks, 0, false);
                
                // Varsayilan olarak autoplay eklenebilir veya kaldirilabilir.
                // internalPlayer.play();
            }
        });
    }

    public static synchronized MusicManager getInstance(Context context) {
        if (instance == null) {
            instance = new MusicManager(context);
        }
        return instance;
    }

    public MusicRepository getRepository() {
        return repository;
    }

    public InternalMusicPlayer getInternalPlayer() {
        return internalPlayer;
    }

    public MediaController getActiveExternalController() {
        return activeExternalController;
    }

    public List<BaseMediaAdapter> getAdapters() {
        return adapters;
    }

    public BaseMediaAdapter getActiveAdapter() {
        // 1. preferredPackage degeriyle eslesen ve aktif olan adaptoru bul
        if (preferredPackage != null) {
            for (BaseMediaAdapter adapter : adapters) {
                if (preferredPackage.equals(adapter.getPackageName()) && adapter.isActive()) {
                    return adapter;
                }
            }
            // AndroidMediaSessionAdapter durumunda
            if (activeExternalController != null && activeExternalController.getPackageName().equals(preferredPackage)) {
                for (BaseMediaAdapter adapter : adapters) {
                    if (adapter instanceof AndroidMediaSessionAdapter && adapter.isActive()) {
                        return adapter;
                    }
                }
            }
        }

        // 2. Calan bir adaptor varsa onu bul
        for (BaseMediaAdapter adapter : adapters) {
            if (adapter.isPlaying() && adapter.isActive()) {
                return adapter;
            }
        }

        // 3. Tercih edilen veya calan yoksa, en son aktif olan kaynaga gore sec
        if (lastActiveSource == MusicSource.INTERNAL) {
            return adapters.get(0); // InternalPlayerAdapter
        }

        if (activeExternalController != null) {
            for (BaseMediaAdapter adapter : adapters) {
                if (adapter instanceof AndroidMediaSessionAdapter && adapter.isActive()) {
                    return adapter;
                }
            }
        }

        // 4. Diger aktif adaptorler
        for (BaseMediaAdapter adapter : adapters) {
            if (adapter.isActive() && !(adapter instanceof InternalPlayerAdapter) && !(adapter instanceof AndroidMediaSessionAdapter)) {
                return adapter;
            }
        }

        return adapters.get(0);
    }

    public void setPreferredPackage(String packageName) {
        this.preferredPackage = packageName;

        // XYAuto yerel muzik servis baglantisini yonet
        for (BaseMediaAdapter adapter : adapters) {
            if (adapter instanceof XyAutoMusicAdapter) {
                if ("com.acloud.stub.localmusic".equals(packageName)) {
                    ((XyAutoMusicAdapter) adapter).bindService();
                } else {
                    ((XyAutoMusicAdapter) adapter).unbindService();
                }
            }
        }

        if (packageName != null && !packageName.equals("usage.internal.player")) {
            if (internalPlayer != null && internalPlayer.isPlaying()) {
                internalPlayer.pause();
            }
        }
        
        notifyTrackChanged();
        notifyStateChanged();

        if (mediaSessionManager != null) {
            try {
                ComponentName listenerComp = new ComponentName(context,
                        "app.organicmaps.carlauncher.MediaNotificationListener");
                updateActiveController(mediaSessionManager.getActiveSessions(listenerComp));
            } catch (Exception e) {
                Log.w(TAG, "Failed to update controller for preferred package: " + e.getMessage());
            }
        }
    }

    public void requestSmartFocus(String activePackageName) {
        if (activePackageName == null) return;

        // 1. Dahili oynatici odagi kaybettiyse duraklat
        if (!"usage.internal.player".equals(activePackageName)) {
            if (internalPlayer != null && internalPlayer.isPlaying()) {
                internalPlayer.pause();
            }
        }

        // 2. Diger tum adaptorleri duraklat
        for (BaseMediaAdapter adapter : adapters) {
            if (adapter != null && !activePackageName.equals(adapter.getPackageName())) {
                if (adapter.isPlaying()) {
                    adapter.pause();
                }
            }
        }
    }

    public void onExternalPlayerStarted(String packageName) {
        lastActiveSource = MusicSource.EXTERNAL;
        
        // Akilli odaklanma tetikle
        requestSmartFocus(packageName);

        if (packageName != null && !packageName.equals(preferredPackage)) {
            setPreferredPackage(packageName);
        } else {
            notifyStateChanged();
        }
    }

    public int getXyDuration() {
        BaseMediaAdapter activeAdapter = getActiveAdapter();
        return activeAdapter != null ? activeAdapter.getDuration() : 0;
    }

    public int getXyPosition() {
        BaseMediaAdapter activeAdapter = getActiveAdapter();
        return activeAdapter != null ? activeAdapter.getPosition() : 0;
    }

    public void seekXy(int position) {
        BaseMediaAdapter activeAdapter = getActiveAdapter();
        if (activeAdapter != null) {
            activeAdapter.seekTo(position);
        }
    }

    public String getPreferredPackage() {
        return preferredPackage;
    }

    private void setupMediaSessionManager() {
        mediaSessionManager = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
        if (mediaSessionManager == null)
            return;

        ComponentName listenerComponent = new ComponentName(context,
                "app.organicmaps.carlauncher.MediaNotificationListener");

        try {
            mediaSessionManager.addOnActiveSessionsChangedListener(
                    this::updateActiveController,
                    listenerComponent);

            updateActiveController(mediaSessionManager.getActiveSessions(listenerComponent));

        } catch (SecurityException e) {
            Log.e(TAG, "Media Control izni yok! Kullanici ayarlardan izin vermeli.", e);
        } catch (Exception e) {
            Log.e(TAG, "MediaSession setup hatasi", e);
        }
    }

    public boolean checkNotificationAccess() {
        try {
            return androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context)
                    .contains(context.getPackageName());
        } catch (NoClassDefFoundError e) {
             String enabledListeners = android.provider.Settings.Secure.getString(context.getContentResolver(), 
                "enabled_notification_listeners");
             return enabledListeners != null && enabledListeners.contains(context.getPackageName());
        }
    }

    private void updateActiveController(List<MediaController> controllers) {
        MediaController candidate = null;
        if (controllers != null) {
            for (MediaController controller : controllers) {
                PlaybackState state = controller.getPlaybackState();
                if (state != null && state.getState() == PlaybackState.STATE_PLAYING) {
                    candidate = controller;
                    break;
                }
            }
            if (candidate == null && preferredPackage != null) {
                for (MediaController controller : controllers) {
                    if (controller.getPackageName().equals(preferredPackage)) {
                        candidate = controller;
                        break;
                    }
                }
            }
            if (candidate == null && !controllers.isEmpty()) {
                candidate = controllers.get(0);
            }
        }

        if (activeExternalController != candidate) {
            if (activeExternalController != null) {
                activeExternalController.unregisterCallback(externalCallback);
            }

            activeExternalController = candidate;

            if (activeExternalController != null) {
                activeExternalController.registerCallback(externalCallback);
                externalCallback.onMetadataChanged(activeExternalController.getMetadata());
                externalCallback.onPlaybackStateChanged(activeExternalController.getPlaybackState());
            }
        }
    }

    private final MediaController.Callback externalCallback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(@Nullable PlaybackState state) {
            boolean isExternalPlaying = state != null && state.getState() == PlaybackState.STATE_PLAYING;
            if (isExternalPlaying) {
                lastActiveSource = MusicSource.EXTERNAL;
                
                if (activeExternalController != null) {
                    String pkg = activeExternalController.getPackageName();
                    
                    // Akilli odaklanma tetikle
                    requestSmartFocus(pkg);
                    
                    if (pkg != null && !pkg.equals(preferredPackage)) {
                        preferredPackage = pkg;
                        for (BaseMediaAdapter adapter : adapters) {
                            if (adapter instanceof XyAutoMusicAdapter) {
                                if ("com.acloud.stub.localmusic".equals(pkg)) {
                                    ((XyAutoMusicAdapter) adapter).bindService();
                                } else {
                                    ((XyAutoMusicAdapter) adapter).unbindService();
                                }
                            }
                        }
                    }
                } else {
                    if (internalPlayer.isPlaying()) {
                        internalPlayer.pause();
                    }
                }
            }
            notifyStateChanged();
            updateVisualizerState();
        }

        @Override
        public void onMetadataChanged(@Nullable MediaMetadata metadata) {
            notifyTrackChanged();
        }

        @Override
        public void onSessionDestroyed() {
            activeExternalController = null;
            notifyTrackChanged();
        }
    };

    private void sendMediaKey(int keycode) {
        try {
            android.media.AudioManager am = (android.media.AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                long eventtime = android.os.SystemClock.uptimeMillis();
                android.view.KeyEvent downEvent = new android.view.KeyEvent(eventtime, eventtime, android.view.KeyEvent.ACTION_DOWN, keycode, 0);
                android.view.KeyEvent upEvent = new android.view.KeyEvent(eventtime, eventtime, android.view.KeyEvent.ACTION_UP, keycode, 0);
                am.dispatchMediaKeyEvent(downEvent);
                am.dispatchMediaKeyEvent(upEvent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to send media key: " + keycode, e);
        }
    }
    public void play() {
        if (!checkNotificationAccess()) {
            android.content.Intent intent = new android.content.Intent("app.organicmaps.carlauncher.REQUEST_NOTIFICATION_PERMISSION");
            androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
        }

        if (mediaSessionManager != null) {
            try {
                android.content.ComponentName listenerComp = new android.content.ComponentName(context, "app.organicmaps.carlauncher.MediaNotificationListener");
                java.util.List<android.media.session.MediaController> controllers = mediaSessionManager.getActiveSessions(listenerComp);
                updateActiveController(controllers);
            } catch (Exception e) {}
        }

        BaseMediaAdapter localAdapter = null;
        if (preferredPackage != null && !"usage.internal.player".equals(preferredPackage)) {
            for (BaseMediaAdapter adapter : adapters) {
                if (preferredPackage.equals(adapter.getPackageName()) 
                    && !(adapter instanceof AndroidMediaSessionAdapter)
                    && !(adapter instanceof InternalPlayerAdapter)) {
                    localAdapter = adapter;
                    break;
                }
            }
        }

        if (localAdapter != null) {
            localAdapter.play();
            notifyStateChanged();
            return;
        }

        if (preferredPackage != null && !"usage.internal.player".equals(preferredPackage)) {
            boolean hasPreferredController = false;
            if (activeExternalController != null && preferredPackage.equals(activeExternalController.getPackageName())) {
                hasPreferredController = true;
            }

            if (hasPreferredController) {
                BaseMediaAdapter activeAdapter = getActiveAdapter();
                if (activeAdapter != null && !(activeAdapter instanceof InternalPlayerAdapter)) {
                    activeAdapter.play();
                    notifyStateChanged();
                    return;
                }
            }
            
            sendMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PLAY);
            return;
        }

        if (internalPlayer != null) {
            internalPlayer.play();
            notifyStateChanged();
        }
    }


    public void togglePlayPause() {
        if (!checkNotificationAccess()) {
            android.content.Intent intent = new android.content.Intent("app.organicmaps.carlauncher.REQUEST_NOTIFICATION_PERMISSION");
            androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
        }

        if (mediaSessionManager != null) {
            try {
                ComponentName listenerComp = new ComponentName(context, "app.organicmaps.carlauncher.MediaNotificationListener");
                List<MediaController> controllers = mediaSessionManager.getActiveSessions(listenerComp);
                updateActiveController(controllers);
            } catch (Exception e) {
                 // Ignore
            }
        }

        // Yerel teyp adaptoru kontrolu
        BaseMediaAdapter localAdapter = null;
        if (preferredPackage != null && !"usage.internal.player".equals(preferredPackage)) {
            for (BaseMediaAdapter adapter : adapters) {
                if (preferredPackage.equals(adapter.getPackageName()) 
                    && !(adapter instanceof AndroidMediaSessionAdapter)
                    && !(adapter instanceof InternalPlayerAdapter)) {
                    localAdapter = adapter;
                    break;
                }
            }
        }

        // Eger yerel teyp adaptoru ise dogrudan play/pause cagir
        if (localAdapter != null) {
            if (localAdapter.isPlaying()) {
                localAdapter.pause();
            } else {
                localAdapter.play();
            }
            notifyStateChanged();
            return;
        }

        // Harici bir uygulama secilmisse ve aktif session'i olmasa bile dahili oynaticiyi oynatma
        if (preferredPackage != null && !"usage.internal.player".equals(preferredPackage)) {
            boolean hasPreferredController = false;
            if (activeExternalController != null && preferredPackage.equals(activeExternalController.getPackageName())) {
                hasPreferredController = true;
            }

            if (hasPreferredController) {
                BaseMediaAdapter activeAdapter = getActiveAdapter();
                if (activeAdapter != null && !(activeAdapter instanceof InternalPlayerAdapter)) {
                    if (activeAdapter.isPlaying()) {
                        activeAdapter.pause();
                    } else {
                        activeAdapter.play();
                    }
                    notifyStateChanged();
                    return;
                }
            }
            
            // Session yoksa veya eslesmiyorsa, genel medya tusu gondererek hariciyi tetikle
            sendMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
            lastActiveSource = MusicSource.EXTERNAL;
            notifyStateChanged();
            return;
        }

        BaseMediaAdapter activeAdapter = getActiveAdapter();
        if (activeAdapter != null) {
            if (activeAdapter.isPlaying()) {
                activeAdapter.pause();
            } else {
                activeAdapter.play();
                if (!(activeAdapter instanceof InternalPlayerAdapter)) {
                    lastActiveSource = MusicSource.EXTERNAL;
                } else {
                    lastActiveSource = MusicSource.INTERNAL;
                }
            }
        } else {
            sendMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
            lastActiveSource = MusicSource.EXTERNAL;
        }
        notifyStateChanged();
    }

    public void skipToNext() {
        if (preferredPackage != null && !"usage.internal.player".equals(preferredPackage)) {
            // Yerel teyp adaptoru kontrolu
            BaseMediaAdapter localAdapter = null;
            for (BaseMediaAdapter adapter : adapters) {
                if (preferredPackage.equals(adapter.getPackageName()) 
                    && !(adapter instanceof AndroidMediaSessionAdapter)
                    && !(adapter instanceof InternalPlayerAdapter)) {
                    localAdapter = adapter;
                    break;
                }
            }
            if (localAdapter != null) {
                localAdapter.next();
                return;
            }

            boolean hasPreferredController = false;
            if (activeExternalController != null && preferredPackage.equals(activeExternalController.getPackageName())) {
                hasPreferredController = true;
            }
            if (hasPreferredController) {
                BaseMediaAdapter activeAdapter = getActiveAdapter();
                if (activeAdapter != null && !(activeAdapter instanceof InternalPlayerAdapter)) {
                    activeAdapter.next();
                    return;
                }
            }
            sendMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_NEXT);
            return;
        }

        BaseMediaAdapter activeAdapter = getActiveAdapter();
        if (activeAdapter != null) {
            activeAdapter.next();
        } else {
            sendMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_NEXT);
        }
    }

    public void skipToPrevious() {
        if (preferredPackage != null && !"usage.internal.player".equals(preferredPackage)) {
            // Yerel teyp adaptoru kontrolu
            BaseMediaAdapter localAdapter = null;
            for (BaseMediaAdapter adapter : adapters) {
                if (preferredPackage.equals(adapter.getPackageName()) 
                    && !(adapter instanceof AndroidMediaSessionAdapter)
                    && !(adapter instanceof InternalPlayerAdapter)) {
                    localAdapter = adapter;
                    break;
                }
            }
            if (localAdapter != null) {
                localAdapter.prev();
                return;
            }

            boolean hasPreferredController = false;
            if (activeExternalController != null && preferredPackage.equals(activeExternalController.getPackageName())) {
                hasPreferredController = true;
            }
            if (hasPreferredController) {
                BaseMediaAdapter activeAdapter = getActiveAdapter();
                if (activeAdapter != null && !(activeAdapter instanceof InternalPlayerAdapter)) {
                    activeAdapter.prev();
                    return;
                }
            }
            sendMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS);
            return;
        }

        BaseMediaAdapter activeAdapter = getActiveAdapter();
        if (activeAdapter != null) {
            activeAdapter.prev();
        } else {
            sendMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS);
        }
    }

    private String sanitizeEncoding(String text) {
        if (text == null) return null;
        try {
            return text
                .replace("Ã„\u00b0", "\u0130") // Buyuk I
                .replace("Ã„\u00b1", "\u0131") // Kucuk i
                .replace("Ã…\u009f", "\u015f") // Kucuk s
                .replace("Ã…\u009e", "\u015e") // Buyuk S
                .replace("Ã„\u009f", "\u011f") // Kucuk g
                .replace("Ã„\u009e", "\u011e") // Buyuk G
                .replace("Ãƒ\u00bc", "\u00fc") // Kucuk u
                .replace("Ãƒ\u009c", "\u00dc") // Buyuk U
                .replace("Ãƒ\u00b6", "\u00f6") // Kucuk o
                .replace("Ãƒ\u0096", "\u00d6") // Buyuk O
                .replace("Ãƒ\u00a7", "\u00e7") // Kucuk c
                .replace("Ãƒ\u0087", "\u00c7"); // Buyuk C
        } catch (Exception e) {
            return text;
        }
    }

    public boolean isShuffleOn() {
        return internalPlayer.isShuffleOn();
    }

    public void setShuffleOn(boolean shuffleOn) {
        internalPlayer.setShuffleOn(shuffleOn);
        notifyStateChanged();
    }

    public int getRepeatMode() {
        return internalPlayer.getRepeatMode();
    }

    public void setRepeatMode(int repeatMode) {
        internalPlayer.setRepeatMode(repeatMode);
        notifyStateChanged();
    }

    public boolean useExternal() {
        BaseMediaAdapter activeAdapter = getActiveAdapter();
        return activeAdapter != null && !(activeAdapter instanceof InternalPlayerAdapter);
    }

    @Override
    public void onTrackChanged(MusicRepository.AudioTrack track) {
        if (!useExternal()) {
            notifyTrackChanged();
            updateNotificationService();
        }
    }

    @Override
    public void onPlaybackStateChanged(boolean isPlaying) {
        isInternalPlaying = isPlaying;
        if (isPlaying) {
             lastActiveSource = MusicSource.INTERNAL;
             preferredPackage = "usage.internal.player";
             
             // Akilli odaklanma tetikle
             requestSmartFocus("usage.internal.player");
             
             for (BaseMediaAdapter adapter : adapters) {
                 if (adapter instanceof XyAutoMusicAdapter) {
                     ((XyAutoMusicAdapter) adapter).unbindService();
                 }
             }
        }
        
        if (!useExternal()) {
            notifyStateChanged();
            updateNotificationService();
        }
        updateVisualizerState();
    }

    @Override
    public void onCompletion() {
        updateNotificationService();
    }
    
    private void updateNotificationService() {
        Intent intent = new Intent(context, MusicPlaybackService.class);
        if (internalPlayer.isPlaying() || (internalPlayer.getCurrentTrack() != null && isInternalPlaying)) {
             intent.setAction(MusicPlaybackService.ACTION_UPDATE);
             if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                 context.startForegroundService(intent);
             } else {
                 context.startService(intent);
             }
        } else {
             intent.setAction(MusicPlaybackService.ACTION_UPDATE);
             // Do not start FGS if not playing on Android 14+ to prevent SecurityException
             if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                 // We can start it as a normal service to stop it, or just send a close action
                 intent.setAction(MusicPlaybackService.ACTION_CLOSE);
                 context.startService(intent); // Normal service start is allowed to process a stop or close
             } else {
                 context.startService(intent);
             }
        }
    }

    public void addListener(MusicUIListener listener) {
        listeners.add(listener);
        new Handler(Looper.getMainLooper()).post(() -> {
            notifyTrackChangedForListener(listener);
            notifyStateChangedForListener(listener);
        });
    }

    public void removeListener(MusicUIListener listener) {
        listeners.remove(listener);
    }

    public void notifyTrackChanged() {
        for (MusicUIListener l : listeners) {
            notifyTrackChangedForListener(l);
        }
    }

    public void notifyStateChanged() {
        for (MusicUIListener l : listeners) {
            notifyStateChangedForListener(l);
        }
    }

    private void notifyTrackChangedForListener(MusicUIListener l) {
        BaseMediaAdapter adapter = getActiveAdapter();
        if (adapter != null) {
            String title = adapter.getTitle();
            String artist = adapter.getArtist();
            android.graphics.Bitmap art = adapter.getAlbumArt();
            String pkg = adapter.getPackageName();

            l.onTrackChanged(
                    sanitizeEncoding(title != null ? title : "Bilinmeyen"),
                    sanitizeEncoding(artist != null ? artist : ""),
                    art,
                    pkg);
            l.onSourceChanged(adapter instanceof InternalPlayerAdapter);
        } else {
            l.onTrackChanged("Muzik Secin", "", null, context.getPackageName());
            l.onSourceChanged(true);
        }
    }

    private void notifyStateChangedForListener(MusicUIListener l) {
        BaseMediaAdapter adapter = getActiveAdapter();
        if (adapter != null) {
            l.onPlaybackStateChanged(adapter.isPlaying());
        } else {
            l.onPlaybackStateChanged(false);
        }
    }
    
    private void checkVisualizerState() {
        if (isPlaying()) {
            if (!visualizerListeners.isEmpty()) startVisualizer();
        } else {
            stopVisualizer();
        }
    }

    private android.media.audiofx.Visualizer mVisualizer;
    public interface MusicVisualizerListener {
        void onFftDataCapture(byte[] fft);
    }
    private final List<MusicVisualizerListener> visualizerListeners = new CopyOnWriteArrayList<>();

    public void addVisualizerListener(MusicVisualizerListener listener) {
        visualizerListeners.add(listener);
        if (isPlaying()) {
            startVisualizer();
        }
    }

    public void removeVisualizerListener(MusicVisualizerListener listener) {
        visualizerListeners.remove(listener);
        if (visualizerListeners.isEmpty()) {
            stopVisualizer();
        }
    }

    private void startVisualizer() {
        if (mVisualizer != null) return;
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) 
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
             Log.w(TAG, "RECORD_AUDIO permission missing for Visualizer.");
             return;
        }

        try {
            int sessionId = 0;
            if (internalPlayer.isPlaying()) {
                sessionId = internalPlayer.getAudioSessionId();
            }
            
            mVisualizer = new android.media.audiofx.Visualizer(sessionId);
            mVisualizer.setCaptureSize(android.media.audiofx.Visualizer.getCaptureSizeRange()[1]);
            mVisualizer.setDataCaptureListener(new android.media.audiofx.Visualizer.OnDataCaptureListener() {
                @Override
                public void onWaveFormDataCapture(android.media.audiofx.Visualizer visualizer, byte[] waveform, int samplingRate) {
                }

                @Override
                public void onFftDataCapture(android.media.audiofx.Visualizer visualizer, byte[] fft, int samplingRate) {
                    for (MusicVisualizerListener l : visualizerListeners) {
                         l.onFftDataCapture(fft);
                    }
                }
            }, android.media.audiofx.Visualizer.getMaxCaptureRate() / 2, false, true);
            
            mVisualizer.setEnabled(true);
            Log.d(TAG, "Visualizer Started. Session: " + sessionId);

        } catch (Exception e) {
            Log.e(TAG, "Failed to start Visualizer", e);
            stopVisualizer();
        }
    }

    private void stopVisualizer() {
        if (mVisualizer != null) {
            mVisualizer.setEnabled(false);
            mVisualizer.release();
            mVisualizer = null;
            Log.d(TAG, "Visualizer Stopped");
        }
    }

    private boolean isPlaying() {
        BaseMediaAdapter activeAdapter = getActiveAdapter();
        return activeAdapter != null && activeAdapter.isPlaying();
    }

    private void updateVisualizerState() {
        if (isPlaying() && !visualizerListeners.isEmpty()) {
            startVisualizer();
        } else {
            stopVisualizer();
        }
    }

    public static class MediaSessionInfo {
        public String packageName;
        public String appName;
        public boolean isPlaying;
        public boolean isPaused;
        public String currentTrack;
        public boolean isActive;
    }
    
    public List<MediaSessionInfo> getActiveMediaSessions() {
        List<MediaSessionInfo> sessions = new ArrayList<>();
        
        if (mediaSessionManager != null) {
            try {
                ComponentName listener = new ComponentName(context, 
                    "app.organicmaps.carlauncher.MediaNotificationListener");
                List<MediaController> controllers = mediaSessionManager.getActiveSessions(listener);
                
                for (MediaController controller : controllers) {
                    String packageName = controller.getPackageName();
                    String appName = getAppName(packageName);
                    PlaybackState state = controller.getPlaybackState();
                    MediaMetadata metadata = controller.getMetadata();
                    
                    MediaSessionInfo info = new MediaSessionInfo();
                    info.packageName = packageName;
                    info.appName = appName;
                    info.isPlaying = (state != null && state.getState() == PlaybackState.STATE_PLAYING);
                    info.isPaused = (state != null && state.getState() == PlaybackState.STATE_PAUSED);
                    info.currentTrack = metadata != null ? 
                        metadata.getString(MediaMetadata.METADATA_KEY_TITLE) : null;
                    info.isActive = (controller == activeExternalController);
                    
                    sessions.add(info);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to get active sessions", e);
            }
        }
        
        return sessions;
    }
    
    public void forceSetActiveController(String packageName) {
        if ("usage.internal.player".equals(packageName)) {
            if (activeExternalController != null) {
                activeExternalController.unregisterCallback(externalCallback);
                activeExternalController = null;
            }
            lastActiveSource = MusicSource.INTERNAL;
            setPreferredPackage(packageName);
            notifyTrackChanged();
            notifyStateChanged();
            return;
        }
        
        if (mediaSessionManager != null) {
            try {
                ComponentName listener = new ComponentName(context, 
                    "app.organicmaps.carlauncher.MediaNotificationListener");
                List<MediaController> controllers = mediaSessionManager.getActiveSessions(listener);
                
                for (MediaController controller : controllers) {
                    if (controller.getPackageName().equals(packageName)) {
                        if (activeExternalController != null) {
                            activeExternalController.unregisterCallback(externalCallback);
                        }
                        
                        activeExternalController = controller;
                        activeExternalController.registerCallback(externalCallback);
                        lastActiveSource = MusicSource.EXTERNAL;
                        setPreferredPackage(packageName);
                        
                        externalCallback.onMetadataChanged(controller.getMetadata());
                        externalCallback.onPlaybackStateChanged(controller.getPlaybackState());
                        break;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to force set controller", e);
            }
        }
    }
    
    public void onSessionsRefreshed(java.util.List<android.media.session.MediaController> controllers) {
        if (controllers != null) {
            updateActiveController(controllers);
        }
    }

    private String getAppName(String packageName) {
        try {
            PackageManager pm = context.getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
            return pm.getApplicationLabel(info).toString();
        } catch (Exception e) {
            return packageName;
        }
    }
}


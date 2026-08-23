package app.organicmaps.carlauncher.music;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    private final PlaylistManager playlistManager;
    private final InternalMusicPlayer internalPlayer;
    private MediaSessionManager mediaSessionManager;
    private MediaController activeExternalController;

    private boolean isInternalPlaying = false;
    private String lastCountedMediaId;
    // Runtime focus used by Smart Focus and the music UI. This is deliberately
    // separate from the user-configured default stored in CarLauncherSettings.
    private String preferredPackage;
    private String pendingTargetPackage;
    private int pendingTargetKeyCode = KeyEvent.KEYCODE_UNKNOWN;
    private long pendingTargetCommandTime;
    private static final long PENDING_DEFAULT_COMMAND_TIMEOUT_MS = 15000L;

    private final List<BaseMediaAdapter> adapters = new ArrayList<>();
    private final List<MusicUIListener> listeners = new CopyOnWriteArrayList<>();

    private enum MusicSource {
        INTERNAL,
        EXTERNAL
    }

    private MusicSource lastActiveSource = MusicSource.INTERNAL;

    // TTS Ducking: navigasyon sesi baslarken muzigi kisip, bitince normale dondurme
    private int savedMusicVolume = -1;          // Kismadan onceki ses seviyesi
    private boolean isDucking = false;          // Su an ducking aktif mi
    private static final float DUCK_RATIO = 0.2f; // TTS sirasinda muzik sesinin %20'ye dusurulmesi
    private final Handler duckHandler = new Handler(Looper.getMainLooper());
    private Runnable duckRestoreRunnable = null; // Gecikme ile geri yukleme icin

    public interface MusicUIListener {
        void onTrackChanged(String title, String artist, android.graphics.Bitmap albumArt, String packageName);
        void onPlaybackStateChanged(boolean isPlaying);
        void onSourceChanged(boolean isInternal);
    }

    private MusicManager(Context context) {
        this.context = context.getApplicationContext();
        this.repository = new MusicRepository(this.context);
        this.playlistManager = new PlaylistManager(this.context);
        this.internalPlayer = new InternalMusicPlayer(this.context);
        this.internalPlayer.setListener(this);
        this.preferredPackage = getConfiguredDefaultPackage();

        setupMediaSessionManager();

        // Adaptorleri sirayla ekle
        adapters.add(new InternalPlayerAdapter(this.context, internalPlayer));
        adapters.add(new AndroidMediaSessionAdapter(this));
        adapters.add(new XyAutoMusicAdapter(this.context, this));
        adapters.add(new XyAutoRadioAdapter(this.context, this));
        adapters.add(new HcnMusicAdapter(this.context, this));
        adapters.add(new HcnRadioAdapter(this.context, this));
        adapters.add(new UniversalBluetoothAdapter(this.context, this));

        // Cache-first: restore immediately, refresh after the launcher first frame.
        java.util.concurrent.atomic.AtomicBoolean restoredFromCache =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        repository.addIndexReadyListener(() -> {
            List<MusicRepository.AudioTrack> cachedTracks = repository.getIndexedTracks();
            if (repository.getCachedTracks().isEmpty()) return;
            internalPlayer.setLibraryForRestore(cachedTracks);
            internalPlayer.restoreState();
            restoredFromCache.set(true);
            app.organicmaps.carlauncher.CarLauncherSettings cachedSettings =
                    new app.organicmaps.carlauncher.CarLauncherSettings(this.context);
            if (cachedSettings.isAutoPlayMusicEnabled() || internalPlayer.wasPlayingBefore()) {
                internalPlayer.resumeLastSession();
            }
        });
        app.organicmaps.carlauncher.CarLauncherSettings startupSettings =
                new app.organicmaps.carlauncher.CarLauncherSettings(this.context);
        MusicRepository.ScanState initialScanState = repository.getScanState();
        long scanAge = initialScanState != null && initialScanState.lastSuccessfulScanTime > 0L
                ? System.currentTimeMillis() - initialScanState.lastSuccessfulScanTime : Long.MAX_VALUE;
        long refreshInterval = 15L * 60L * 1000L;
        long refreshDelay = scanAge < refreshInterval
                ? Math.max(2000L, refreshInterval - scanAge) : 2000L;
        if (startupSettings.isAutoScanMusicEnabled()) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> repository.scanMusic((tracks, folders, artists) -> {
            Log.d(TAG, "Scan complete: " + tracks.size() + " tracks");
            if (!tracks.isEmpty() && !restoredFromCache.get()) {
                internalPlayer.setLibraryForRestore(tracks);
                internalPlayer.restoreState();

                app.organicmaps.carlauncher.CarLauncherSettings settings =
                     new app.organicmaps.carlauncher.CarLauncherSettings(this.context);
                if (settings.isAutoPlayMusicEnabled() || internalPlayer.wasPlayingBefore()) {
                     internalPlayer.resumeLastSession();
                }
            }
        }, MusicRepository.ScanReason.STARTUP_REFRESH), refreshDelay);
        }

        // Core kodlara dokunmadan, sistemdeki ses calim durumlarini (TTS/Navigasyon) dinleme
        setupAudioPlaybackCallback();
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

    public String getConfiguredDefaultPackage() {
        String configured = new app.organicmaps.carlauncher.CarLauncherSettings(context)
                .getMusicApp();
        return configured == null || "internal".equals(configured)
                ? "usage.internal.player" : configured;
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

        lastSmartFocusTime = System.currentTimeMillis();

        // 1. Harici kaynak aktiflesince dahili oynaticiyi kesin durdur (Turkce karakter yok)
        // isPlaying() kontrolu olmaksizin - race condition'i onler
        if (!"usage.internal.player".equals(activePackageName)) {
            if (internalPlayer != null) {
                internalPlayer.pause();
            }
        }

        // 2. A physical player can be visible through both MediaSession and a
        // head-unit adapter. Pause every package only once; duplicate toggle
        // commands can otherwise start the old source again.
        Set<String> pausedPackages = new HashSet<>();

        if (activeExternalController != null) {
            PlaybackState state = activeExternalController.getPlaybackState();
            String controllerPackage = activeExternalController.getPackageName();
            if (state != null && state.getState() == PlaybackState.STATE_PLAYING
                    && controllerPackage != null
                    && !controllerPackage.equals(activePackageName)) {
                BaseMediaAdapter dedicatedAdapter = findDedicatedAdapter(controllerPackage);
                if (dedicatedAdapter != null) {
                    dedicatedAdapter.pause();
                } else {
                    activeExternalController.getTransportControls().pause();
                }
                pausedPackages.add(controllerPackage);
            }
        }

        for (BaseMediaAdapter adapter : adapters) {
            if (adapter == null || adapter instanceof InternalPlayerAdapter
                    || adapter instanceof AndroidMediaSessionAdapter) continue;
            String adapterPkg = adapter.getPackageName();
            if (adapterPkg != null && !adapterPkg.equals(activePackageName)
                    && !pausedPackages.contains(adapterPkg) && adapter.isPlaying()) {
                adapter.pause();
                pausedPackages.add(adapterPkg);
            }
        }
    }

    @Nullable
    private BaseMediaAdapter findDedicatedAdapter(String packageName) {
        for (BaseMediaAdapter adapter : adapters) {
            if (adapter instanceof InternalPlayerAdapter
                    || adapter instanceof AndroidMediaSessionAdapter) continue;
            if (packageName.equals(adapter.getPackageName())) {
                return adapter;
            }
        }
        return null;
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
                if (isOwnController(controller)) continue;
                PlaybackState state = controller.getPlaybackState();
                if (state != null && state.getState() == PlaybackState.STATE_PLAYING) {
                    candidate = controller;
                    break;
                }
            }
            if (candidate == null && preferredPackage != null) {
                for (MediaController controller : controllers) {
                    if (isOwnController(controller)) continue;
                    if (controller.getPackageName().equals(preferredPackage)) {
                        candidate = controller;
                        break;
                    }
                }
            }
            if (candidate == null) {
                for (MediaController controller : controllers) {
                    if (!isOwnController(controller)) {
                        candidate = controller;
                        break;
                    }
                }
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
                executePendingTargetCommand(activeExternalController);
            }
        }
    }

    private boolean isOwnController(@Nullable MediaController controller) {
        return controller != null && context.getPackageName().equals(controller.getPackageName());
    }

    private long lastSmartFocusTime = 0;

    private final MediaController.Callback externalCallback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(@Nullable PlaybackState state) {
            boolean isExternalPlaying = state != null && state.getState() == PlaybackState.STATE_PLAYING;

            // Bluetooth gibi inatçı oynatıcıların pause isteğini yok sayıp gönderdiği sahte PLAYING bildirimlerini engelle (Turkce karakter yok)
            if (isExternalPlaying && "usage.internal.player".equals(preferredPackage)) {
                if (System.currentTimeMillis() - lastSmartFocusTime < 2000) {
                    // Yakin zamanda smart focus ile durdurma gonderilmisse, bu gecikmeli durumu yoksay.
                    return;
                }
            }

            if (isExternalPlaying) {
                lastActiveSource = MusicSource.EXTERNAL;

                if (activeExternalController != null) {
                    String pkg = activeExternalController.getPackageName();

                    // Akilli odaklanma tetikle
                    requestSmartFocus(pkg);

                    // preferredPackage guncelle - setPreferredPackage ile (Turkce karakter yok)
                    // Direkt atama yerine metodu kullan: dahili player durdurmasi + XY bind + bildirimler
                    if (pkg != null && !pkg.equals(preferredPackage)) {
                        setPreferredPackage(pkg);
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
            notifyStateChanged();
        }
    };

    public boolean hasActiveExternalPlayback() {
        if (activeExternalController != null && !isOwnController(activeExternalController)) {
            PlaybackState state = activeExternalController.getPlaybackState();
            if (state != null && state.getState() == PlaybackState.STATE_PLAYING) {
                return true;
            }
        }
        for (BaseMediaAdapter adapter : adapters) {
            if (adapter instanceof InternalPlayerAdapter
                    || adapter instanceof AndroidMediaSessionAdapter) {
                continue;
            }
            if (adapter.isActive() && adapter.isPlaying()) {
                return true;
            }
        }
        return false;
    }

    public boolean shouldOwnHardwareMediaSession() {
        return !hasActiveExternalPlayback()
                && pendingTargetKeyCode == KeyEvent.KEYCODE_UNKNOWN;
    }

    /**
     * Routes a hardware command without redispatching it through AudioManager.
     * Redispatching from our own MediaSession would send the command back to the
     * same session and can create a loop.
     */
    public boolean handleHardwareMediaKey(int keyCode) {
        refreshExternalControllers();

        MediaController playingController = getPlayingExternalController();
        if (playingController != null) {
            recordHardwareDecision(keyCode, "active_external:" + playingController.getPackageName());
            executeControllerCommand(playingController, keyCode);
            return true;
        }

        BaseMediaAdapter playingAdapter = getPlayingDedicatedAdapter();
        if (playingAdapter != null) {
            recordHardwareDecision(keyCode, "active_adapter:" + playingAdapter.getPackageName());
            executeAdapterCommand(playingAdapter, keyCode);
            return true;
        }

        if (internalPlayer.isPlaying()) {
            recordHardwareDecision(keyCode, "active_internal");
            executeInternalCommand(keyCode);
            return true;
        }

        String defaultPackage = getConfiguredDefaultPackage();
        if ("usage.internal.player".equals(defaultPackage)) {
            preferredPackage = defaultPackage;
            lastActiveSource = MusicSource.INTERNAL;
            recordHardwareDecision(keyCode, "default_internal");
            executeInternalCommand(keyCode);
            return true;
        }

        BaseMediaAdapter defaultAdapter = findDedicatedAdapter(defaultPackage);
        if (defaultAdapter != null && defaultAdapter.isActive()) {
            preferredPackage = defaultPackage;
            lastActiveSource = MusicSource.EXTERNAL;
            recordHardwareDecision(keyCode, "default_adapter:" + defaultPackage);
            executeAdapterCommand(defaultAdapter, keyCode);
            return true;
        }

        MediaController defaultController = findExternalController(defaultPackage);
        if (defaultController != null) {
            preferredPackage = defaultPackage;
            lastActiveSource = MusicSource.EXTERNAL;
            recordHardwareDecision(keyCode, "default_session:" + defaultPackage);
            executeControllerCommand(defaultController, keyCode);
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE
                || keyCode == KeyEvent.KEYCODE_MEDIA_STOP) {
            recordHardwareDecision(keyCode, "idle_ignore");
            return false;
        }

        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(defaultPackage);
        if (launchIntent != null) {
            queuePendingTargetCommand(defaultPackage, keyCode);
            preferredPackage = defaultPackage;
            lastActiveSource = MusicSource.EXTERNAL;
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(launchIntent);
                recordHardwareDecision(keyCode, "launch_default:" + defaultPackage);
                return true;
            } catch (Exception e) {
                clearPendingTargetCommand();
                Log.e(TAG, "Default media app could not be launched: " + defaultPackage, e);
            }
        }

        // A removed or unavailable default app must not leave steering controls dead.
        preferredPackage = "usage.internal.player";
        lastActiveSource = MusicSource.INTERNAL;
        recordHardwareDecision(keyCode, "default_unavailable_internal_fallback");
        executeInternalCommand(keyCode);
        return true;
    }

    /**
     * Handles controls originating from the internal player's own notification.
     * These controls must keep targeting the track displayed by that notification,
     * independently from the configured idle default.
     */
    public boolean handleInternalMediaKey(int keyCode) {
        preferredPackage = "usage.internal.player";
        lastActiveSource = MusicSource.INTERNAL;
        requestSmartFocus(preferredPackage);
        recordHardwareDecision(keyCode, "internal_notification");
        executeInternalCommand(keyCode);
        return true;
    }

    private void refreshExternalControllers() {
        if (mediaSessionManager == null) {
            return;
        }
        try {
            ComponentName listener = new ComponentName(context,
                    "app.organicmaps.carlauncher.MediaNotificationListener");
            updateActiveController(mediaSessionManager.getActiveSessions(listener));
        } catch (Exception e) {
            Log.w(TAG, "External sessions could not be refreshed", e);
        }
    }

    @Nullable
    private MediaController getPlayingExternalController() {
        if (activeExternalController == null || isOwnController(activeExternalController)) {
            return null;
        }
        PlaybackState state = activeExternalController.getPlaybackState();
        return state != null && state.getState() == PlaybackState.STATE_PLAYING
                ? activeExternalController : null;
    }

    @Nullable
    private BaseMediaAdapter getPlayingDedicatedAdapter() {
        for (BaseMediaAdapter adapter : adapters) {
            if (adapter instanceof InternalPlayerAdapter
                    || adapter instanceof AndroidMediaSessionAdapter) {
                continue;
            }
            if (adapter.isActive() && adapter.isPlaying()) {
                return adapter;
            }
        }
        return null;
    }

    @Nullable
    private MediaController findExternalController(String packageName) {
        if (mediaSessionManager == null || packageName == null) {
            return null;
        }
        try {
            ComponentName listener = new ComponentName(context,
                    "app.organicmaps.carlauncher.MediaNotificationListener");
            for (MediaController controller : mediaSessionManager.getActiveSessions(listener)) {
                if (!isOwnController(controller)
                        && packageName.equals(controller.getPackageName())) {
                    return controller;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Default media session could not be found", e);
        }
        return null;
    }

    private void executePendingTargetCommand(@NonNull MediaController controller) {
        if (pendingTargetKeyCode == KeyEvent.KEYCODE_UNKNOWN
                || pendingTargetPackage == null) {
            return;
        }
        long age = System.currentTimeMillis() - pendingTargetCommandTime;
        if (age < 0 || age > PENDING_DEFAULT_COMMAND_TIMEOUT_MS) {
            clearPendingTargetCommand();
            return;
        }
        if (!pendingTargetPackage.equals(controller.getPackageName())) {
            return;
        }
        String targetPackage = pendingTargetPackage;
        int keyCode = pendingTargetKeyCode;
        clearPendingTargetCommand();
        recordHardwareDecision(keyCode, "pending_session_ready:" + targetPackage);
        executeControllerCommand(controller, keyCode);
    }

    private void clearPendingTargetCommand() {
        pendingTargetPackage = null;
        pendingTargetKeyCode = KeyEvent.KEYCODE_UNKNOWN;
        pendingTargetCommandTime = 0;
    }

    private void queuePendingTargetCommand(@NonNull String targetPackage, int keyCode) {
        // A cold PLAY_PAUSE request means "wake and play". If the launched app
        // auto-starts playback, replaying a toggle would immediately pause it.
        pendingTargetPackage = targetPackage;
        pendingTargetKeyCode = keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                ? KeyEvent.KEYCODE_MEDIA_PLAY : keyCode;
        pendingTargetCommandTime = System.currentTimeMillis();
        long commandTime = pendingTargetCommandTime;
        // Yield our fallback MediaSession while the selected app is starting.
        notifyStateChanged();
        duckHandler.postDelayed(() -> {
            if (pendingTargetKeyCode != KeyEvent.KEYCODE_UNKNOWN
                    && pendingTargetCommandTime == commandTime) {
                recordHardwareDecision(pendingTargetKeyCode,
                        "pending_session_timeout:" + pendingTargetPackage);
                clearPendingTargetCommand();
                notifyStateChanged();
            }
        }, PENDING_DEFAULT_COMMAND_TIMEOUT_MS);
    }

    private void executeControllerCommand(@NonNull MediaController controller, int keyCode) {
        MediaController.TransportControls controls = controller.getTransportControls();
        PlaybackState state = controller.getPlaybackState();
        boolean playing = state != null && state.getState() == PlaybackState.STATE_PLAYING;
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY) {
            controls.play();
        } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE
                || keyCode == KeyEvent.KEYCODE_MEDIA_STOP) {
            controls.pause();
        } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            if (playing) controls.pause(); else controls.play();
        } else if (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT) {
            controls.skipToNext();
            if (!playing) controls.play();
        } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
            controls.skipToPrevious();
            if (!playing) controls.play();
        }
    }

    private void executeAdapterCommand(@NonNull BaseMediaAdapter adapter, int keyCode) {
        boolean playing = adapter.isPlaying();
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY) {
            adapter.play();
        } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE
                || keyCode == KeyEvent.KEYCODE_MEDIA_STOP) {
            adapter.pause();
        } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            if (playing) adapter.pause(); else adapter.play();
        } else if (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT) {
            adapter.next();
        } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
            adapter.prev();
        }
        notifyStateChanged();
    }

    private void executeInternalCommand(int keyCode) {
        boolean playing = internalPlayer.isPlaying();
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY) {
            internalPlayer.play();
        } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE
                || keyCode == KeyEvent.KEYCODE_MEDIA_STOP) {
            internalPlayer.pause();
        } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            if (playing) internalPlayer.pause(); else internalPlayer.play();
        } else if (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT) {
            internalPlayer.playNext();
        } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
            internalPlayer.playPrevious();
        }
        notifyStateChanged();
    }

    private void recordHardwareDecision(int keyCode, String destination) {
        Log.d(TAG, "SMART_FOCUS key=" + KeyEvent.keyCodeToString(keyCode)
                + " destination=" + destination
                + " configuredDefault=" + getConfiguredDefaultPackage()
                + " focused=" + preferredPackage);
    }

    public void play() {
        handleHardwareMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY);
    }

    /**
     * Plays the source explicitly selected in the music UI. This intentionally
     * does not use the configured idle default or the previously playing app.
     */
    public void playFocusedSource() {
        String targetPackage = preferredPackage;
        if (targetPackage == null) {
            play();
            return;
        }
        if ("usage.internal.player".equals(targetPackage)) {
            requestSmartFocus(targetPackage);
            lastActiveSource = MusicSource.INTERNAL;
            executeInternalCommand(KeyEvent.KEYCODE_MEDIA_PLAY);
            return;
        }

        requestSmartFocus(targetPackage);
        lastActiveSource = MusicSource.EXTERNAL;

        BaseMediaAdapter adapter = findDedicatedAdapter(targetPackage);
        if (adapter != null) {
            recordHardwareDecision(KeyEvent.KEYCODE_MEDIA_PLAY,
                    "focused_adapter:" + targetPackage);
            executeAdapterCommand(adapter, KeyEvent.KEYCODE_MEDIA_PLAY);
            return;
        }

        MediaController controller = findExternalController(targetPackage);
        if (controller != null) {
            recordHardwareDecision(KeyEvent.KEYCODE_MEDIA_PLAY,
                    "focused_session:" + targetPackage);
            executeControllerCommand(controller, KeyEvent.KEYCODE_MEDIA_PLAY);
            return;
        }

        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(targetPackage);
        if (launchIntent != null) {
            queuePendingTargetCommand(targetPackage, KeyEvent.KEYCODE_MEDIA_PLAY);
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(launchIntent);
                recordHardwareDecision(KeyEvent.KEYCODE_MEDIA_PLAY,
                        "launch_focused:" + targetPackage);
                return;
            } catch (Exception e) {
                clearPendingTargetCommand();
                Log.e(TAG, "Focused media app could not be launched: " + targetPackage, e);
            }
        }

        recordHardwareDecision(KeyEvent.KEYCODE_MEDIA_PLAY,
                "focused_unavailable_internal_fallback");
        setPreferredPackage("usage.internal.player");
        lastActiveSource = MusicSource.INTERNAL;
        executeInternalCommand(KeyEvent.KEYCODE_MEDIA_PLAY);
    }


    public void togglePlayPause() {
        handleHardwareMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
    }

    public void skipToNext() {
        handleHardwareMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT);
    }

    public void skipToPrevious() {
        handleHardwareMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
    }

    public boolean isShuffleOn() {
        return internalPlayer != null && internalPlayer.isShuffleOn();
    }

    public void setShuffleOn(boolean shuffleOn) {
        if (internalPlayer != null) {
            internalPlayer.setShuffleOn(shuffleOn);
            notifyStateChanged();
        }
    }

    public void toggleShuffle() {
        setShuffleOn(!isShuffleOn());
    }

    public int getRepeatMode() {
        return internalPlayer != null ? internalPlayer.getRepeatMode() : 0;
    }

    public void setRepeatMode(int repeatMode) {
        if (internalPlayer != null) {
            internalPlayer.setRepeatMode(repeatMode);
            notifyStateChanged();
        }
    }

    public void toggleRepeat() {
        if (internalPlayer != null) {
            internalPlayer.toggleRepeat();
            notifyStateChanged();
        }
    }

    private String sanitizeEncoding(String text) {
        if (text == null) return null;
        try {
            return text
                .replace("Ä\u00b0", "\u0130") // Buyuk I
                .replace("Ä\u00b1", "\u0131") // Kucuk i
                .replace("Å\u009f", "\u015f") // Kucuk s
                .replace("Å\u009e", "\u015e") // Buyuk S
                .replace("Ä\u009f", "\u011f") // Kucuk g
                .replace("Ä\u009e", "\u011e") // Buyuk G
                .replace("Ã\u00bc", "\u00fc") // Kucuk u
                .replace("Ã\u009c", "\u00dc") // Buyuk U
                .replace("Ã\u00b6", "\u00f6") // Kucuk o
                .replace("Ã\u0096", "\u00d6") // Buyuk O
                .replace("Ã\u00a7", "\u00e7") // Kucuk c
                .replace("Ã\u0087", "\u00c7"); // Buyuk C
        } catch (Exception e) {
            return text;
        }
    }

    public boolean useExternal() {
        BaseMediaAdapter activeAdapter = getActiveAdapter();
        return activeAdapter != null && !(activeAdapter instanceof InternalPlayerAdapter);
    }

    @Override
    public void onTrackChanged(MusicRepository.AudioTrack track) {
        lastCountedMediaId = null;
        if (!useExternal()) {
            notifyTrackChanged();
        }
    }

    @Override
    public void onPlaybackStateChanged(boolean isPlaying) {
        isInternalPlaying = isPlaying;
        if (isPlaying) {
             MusicRepository.AudioTrack currentTrack = internalPlayer.getCurrentTrack();
             if (currentTrack != null && !currentTrack.getMediaId().equals(lastCountedMediaId)) {
                 playlistManager.addToRecentlyPlayed(currentTrack);
                 lastCountedMediaId = currentTrack.getMediaId();
             }
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
        }
        updateVisualizerState();
    }

    @Override
    public void onCompletion() {
        notifyTrackChanged();
        notifyStateChanged();
    }

    public void addListener(MusicUIListener listener) {
        if (listeners.contains(listener)) {
            return;
        }
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
        // Onceki instance'i kapat: kaynak dahiliden hariciye (veya tersi) gecmis olabilir (Turkce karakter yok)
        stopVisualizer();

        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "RECORD_AUDIO permission missing for Visualizer.");
            return;
        }

        try {
            // Dahili caliyorsa session ID'sini kullan, degilse 0 (sistem output mix - harici icin)
            int sessionId = internalPlayer.isPlaying() ? internalPlayer.getAudioSessionId() : 0;

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
            Log.d(TAG, "Visualizer Started. Session: " + sessionId + " (" + (sessionId == 0 ? "external/mix" : "internal") + ")");

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
                    if (isOwnController(controller)) continue;
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
                    if (isOwnController(controller)) continue;
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

    // ---------------------------------------------------------------------------
    // TTS Ducking API
    // XYAuto gibi harici adaptorler Android AudioFocus'u dinlemedigi icin
    // STREAM_MUSIC kanal sesi manuel olarak dusurulup geri yuklenir.
    // ---------------------------------------------------------------------------

    /**
     * TTS (navigasyon sesi) basladiginda cagrilis.
     * Muzigi DUCK_RATIO oraniyla gecici olarak kistiginda etkisi hissedilir.
     */
    public void ttsStarted() {
        // Gecikme ile restore planlanmissa iptal et (onceki tts bitmeden yenisi basladi)
        if (duckRestoreRunnable != null) {
            duckHandler.removeCallbacks(duckRestoreRunnable);
            duckRestoreRunnable = null;
        }

        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;

        int maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int currentVol = am.getStreamVolume(AudioManager.STREAM_MUSIC);

        if (!isDucking) {
            savedMusicVolume = currentVol; // Sadece ilk ducking'de kaydet
        }
        isDucking = true;

        int duckVol = Math.max(1, (int) (maxVol * DUCK_RATIO));
        if (currentVol > duckVol) {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, duckVol, 0);
            Log.d(TAG, "TTS ducking: ses " + currentVol + " -> " + duckVol);
        }
    }

    /**
     * TTS (navigasyon sesi) bittiginde cagrilis.
     * Muzigi kaydedilen seviyeye geri yukler.
     * Kisa bir gecikme ile yukleme yapilir: TTS motoru bazi sistemlerde
     * utteranceCompleted'dan hemen sonra kisa sureli ses birakmaya devam edebilir.
     */
    public void ttsStopped() {
        if (!isDucking) return;

        if (duckRestoreRunnable != null) {
            duckHandler.removeCallbacks(duckRestoreRunnable);
        }
        duckRestoreRunnable = () -> {
            isDucking = false;
            if (savedMusicVolume < 0) return;
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return;
            int maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int restoreVol = Math.min(savedMusicVolume, maxVol);
            am.setStreamVolume(AudioManager.STREAM_MUSIC, restoreVol, 0);
            Log.d(TAG, "TTS ducking bitti: ses " + restoreVol + "'e geri yuklendi");
            savedMusicVolume = -1;
        };
        // 600ms gecikme: TTS motoru son utterance'i bitirir, sonra sesi ac
        duckHandler.postDelayed(duckRestoreRunnable, 600);
    }

    private void setupAudioPlaybackCallback() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                am.registerAudioPlaybackCallback(new AudioManager.AudioPlaybackCallback() {
                    @Override
                    public void onPlaybackConfigChanged(java.util.List<android.media.AudioPlaybackConfiguration> configs) {
                        if (configs == null) return;
                        boolean isSpeechOrNavActive = false;
                        for (android.media.AudioPlaybackConfiguration config : configs) {
                            if (config.getAudioAttributes() != null) {
                                int usage = config.getAudioAttributes().getUsage();
                                int contentType = config.getAudioAttributes().getContentType();
                                if (usage == android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
                                        || contentType == android.media.AudioAttributes.CONTENT_TYPE_SPEECH) {
                                    isSpeechOrNavActive = true;
                                    break;
                                }
                            }
                        }

                        if (isSpeechOrNavActive) {
                            ttsStarted();
                        } else {
                            ttsStopped();
                        }
                    }
                }, new Handler(Looper.getMainLooper()));
            }
        }
    }

    // ---------------------------------------------------------------------------

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

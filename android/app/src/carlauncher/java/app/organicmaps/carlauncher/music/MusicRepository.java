package app.organicmaps.carlauncher.music;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.media.MediaScannerConnection;
import android.util.Log;
import android.util.AtomicFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Repository for scanning and managing local music files.
 * Handles checking external apps vs local files.
 */
public class MusicRepository {

    private static final String TAG = "MusicRepository";
    private final Context context;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Object scanLock = new Object();
    private boolean scanInProgress;
    private final List<OnScanCompletedListener> pendingScanListeners = new ArrayList<>();
    private List<AudioTrack> cachedTracks = new ArrayList<>();
    private List<AudioFolder> cachedFolders = new ArrayList<>();
    private List<AudioArtist> cachedArtists = new ArrayList<>();
    private final List<ScanStateListener> scanStateListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private volatile ScanState scanState;
    private final android.content.BroadcastReceiver storageReceiver;
    private static final String SCAN_PREFS = "car_music_scan_state";

    // Singleton support if needed, or instantiated by MusicManager
    public MusicRepository(Context context) {
        this.context = context.getApplicationContext();
        loadCachedIndex();
        android.content.SharedPreferences prefs = this.context.getSharedPreferences(
                SCAN_PREFS, Context.MODE_PRIVATE);
        scanState = new ScanState(false, ScanReason.CACHE_LOAD,
                prefs.getLong("last_scan_time", 0L), cachedTracks.size(),
                countStorage(cachedTracks, StorageType.INTERNAL),
                countStorage(cachedTracks, StorageType.USB), null,
                hasMusicReadPermission());
        storageReceiver = new android.content.BroadcastReceiver() {
            @Override public void onReceive(Context receiverContext, android.content.Intent intent) {
                String action = intent != null ? intent.getAction() : null;
                if (android.content.Intent.ACTION_MEDIA_MOUNTED.equals(action)) {
                    scanMusic(null, ScanReason.USB_MOUNTED);
                } else if (android.content.Intent.ACTION_MEDIA_EJECT.equals(action)
                        || android.content.Intent.ACTION_MEDIA_REMOVED.equals(action)
                        || android.content.Intent.ACTION_MEDIA_UNMOUNTED.equals(action)) {
                    refreshCachedAvailability(ScanReason.USB_REMOVED);
                }
            }
        };
        android.content.IntentFilter storageFilter = new android.content.IntentFilter();
        storageFilter.addAction(android.content.Intent.ACTION_MEDIA_MOUNTED);
        storageFilter.addAction(android.content.Intent.ACTION_MEDIA_EJECT);
        storageFilter.addAction(android.content.Intent.ACTION_MEDIA_REMOVED);
        storageFilter.addAction(android.content.Intent.ACTION_MEDIA_UNMOUNTED);
        storageFilter.addDataScheme("file");
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            this.context.registerReceiver(storageReceiver, storageFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            this.context.registerReceiver(storageReceiver, storageFilter);
        }
    }

    public enum ScanReason { CACHE_LOAD, STARTUP_REFRESH, USER_REQUEST, USB_MOUNTED, USB_REMOVED }

    public interface ScanStateListener { void onScanStateChanged(ScanState state); }

    public static final class ScanState {
        public final boolean scanning;
        public final ScanReason reason;
        public final long lastSuccessfulScanTime;
        public final int totalTracks;
        public final int internalTracks;
        public final int usbTracks;
        public final String error;
        public final boolean permissionGranted;

        ScanState(boolean scanning, ScanReason reason, long lastSuccessfulScanTime,
                int totalTracks, int internalTracks, int usbTracks, String error,
                boolean permissionGranted) {
            this.scanning = scanning;
            this.reason = reason;
            this.lastSuccessfulScanTime = lastSuccessfulScanTime;
            this.totalTracks = totalTracks;
            this.internalTracks = internalTracks;
            this.usbTracks = usbTracks;
            this.error = error;
            this.permissionGranted = permissionGranted;
        }
    }

    public ScanState getScanState() { return scanState; }
    public void addScanStateListener(ScanStateListener listener) {
        if (listener != null) {
            scanStateListeners.add(listener);
            mainHandler.post(() -> listener.onScanStateChanged(scanState));
        }
    }
    public void removeScanStateListener(ScanStateListener listener) {
        scanStateListeners.remove(listener);
    }

    private void publishScanState(ScanState state) {
        scanState = state;
        for (ScanStateListener listener : scanStateListeners) {
            mainHandler.post(() -> listener.onScanStateChanged(state));
        }
    }

    public interface OnCopyCompletedListener {
        void onCopyCompleted(boolean success, String messageOrPath);
    }

    /**
     * Copy a USB track to device internal storage (/Music/OsmAndLocal/) asynchronously.
     */
    public void copyTrackToInternalStorage(AudioTrack track, OnCopyCompletedListener listener) {
        ioExecutor.execute(() -> {
            if (track == null || track.getPath() == null) {
                notifyCopyCompleted(listener, false, "Geçersiz şarkı yolu.");
                return;
            }
            File srcFile = new File(track.getPath());
            if (!srcFile.exists()) {
                notifyCopyCompleted(listener, false, "Kaynak dosya bulunamadı veya USB takılı değil.");
                return;
            }

            File destDir = new File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC), "OsmAndLocal");
            if (!destDir.exists()) {
                destDir.mkdirs();
            }

            File destFile = new File(destDir, srcFile.getName());
            try (java.io.InputStream in = new java.io.FileInputStream(srcFile);
                 java.io.OutputStream out = new java.io.FileOutputStream(destFile)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                notifyCopyCompleted(listener, true, destFile.getAbsolutePath());
            } catch (Exception e) {
                Log.e(TAG, "Copy track failed", e);
                notifyCopyCompleted(listener, false, e.getMessage());
            }
        });
    }

    public interface OnScanCompletedListener {
        void onScanCompleted(List<AudioTrack> tracks, List<AudioFolder> folders, List<AudioArtist> artists);
    }

    /**
     * Scan device for music files asynchronously.
     */
    public void scanMusic(final OnScanCompletedListener listener) {
        scanMusic(listener, ScanReason.USER_REQUEST);
    }

    public void scanMusic(final OnScanCompletedListener listener, ScanReason reason) {
        synchronized (scanLock) {
            if (listener != null) {
                pendingScanListeners.add(listener);
            }
            if (scanInProgress) {
                return;
            }
            scanInProgress = true;
        }
        ScanState previous = scanState;
        publishScanState(new ScanState(true, reason,
                previous != null ? previous.lastSuccessfulScanTime : 0L,
                previous != null ? previous.totalTracks : cachedTracks.size(),
                previous != null ? previous.internalTracks : 0,
                previous != null ? previous.usbTracks : 0, null,
                hasMusicReadPermission()));
        ioExecutor.execute(() -> {
            boolean canRefreshIndex = hasMusicReadPermission();
            List<AudioTrack> tracks = canRefreshIndex
                    ? scanDeviceForAudio() : getIndexedTracks();
            if (canRefreshIndex) tracks = mergeWithUnavailableCachedTracks(tracks);
            final List<AudioTrack> indexedTracks = tracks;
            final List<AudioTrack> availableTracks = getPhysicalTracks(indexedTracks);
            List<AudioFolder> folders = organizeIntoFolders(availableTracks);
            List<AudioArtist> artists = organizeIntoArtists(availableTracks);

            synchronized (this) {
                cachedTracks = indexedTracks;
                cachedFolders = organizeIntoFolders(indexedTracks);
                cachedArtists = artists;
            }
            if (canRefreshIndex) saveCachedIndex(indexedTracks);

            long completedAt = canRefreshIndex ? System.currentTimeMillis()
                    : (scanState != null ? scanState.lastSuccessfulScanTime : 0L);
            if (canRefreshIndex) {
                context.getSharedPreferences(SCAN_PREFS, Context.MODE_PRIVATE).edit()
                        .putLong("last_scan_time", completedAt).apply();
            }

            List<OnScanCompletedListener> callbacks;
            synchronized (scanLock) {
                callbacks = new ArrayList<>(pendingScanListeners);
                pendingScanListeners.clear();
                scanInProgress = false;
            }
            publishScanState(new ScanState(false, reason, completedAt,
                    availableTracks.size(), countAvailableStorage(indexedTracks, StorageType.INTERNAL),
                    countAvailableStorage(indexedTracks, StorageType.USB), null, canRefreshIndex));
            for (OnScanCompletedListener callback : callbacks) {
                mainHandler.post(() -> callback.onScanCompleted(
                        new ArrayList<>(availableTracks), new ArrayList<>(folders),
                        new ArrayList<>(artists)));
            }
        });
    }

    public boolean isScanInProgress() {
        synchronized (scanLock) {
            return scanInProgress;
        }
    }

    private File getIndexFile() {
        return new File(context.getFilesDir(), "car_music_index_v1.json");
    }

    private void loadCachedIndex() {
        File indexFile = getIndexFile();
        if (!indexFile.isFile()) return;
        try (FileInputStream input = new FileInputStream(indexFile)) {
            byte[] data = new byte[(int) indexFile.length()];
            int offset = 0;
            while (offset < data.length) {
                int read = input.read(data, offset, data.length - offset);
                if (read < 0) break;
                offset += read;
            }
            JSONArray array = new JSONArray(new String(data, 0, offset,
                    java.nio.charset.StandardCharsets.UTF_8));
            List<AudioTrack> tracks = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                String path = item.optString("path", null);
                String content = item.optString("content", null);
                String art = item.optString("art", null);
                StorageType storage;
                try {
                    storage = StorageType.valueOf(item.optString("storage", StorageType.INTERNAL.name()));
                } catch (Exception ignored) {
                    storage = StorageType.INTERNAL;
                }
                tracks.add(new AudioTrack(item.optLong("id"), item.optString("title"),
                        item.optString("artist"), item.optString("album"), item.optLong("duration"),
                        path, content == null ? null : Uri.parse(content),
                        art == null ? null : Uri.parse(art), storage, true, item.optLong("dateAdded")));
            }
            List<AudioTrack> physical = refreshAvailability(tracks);
            synchronized (this) {
                cachedTracks = physical;
                cachedFolders = organizeIntoFolders(physical);
                cachedArtists = organizeIntoArtists(physical);
            }
            Log.i(TAG, "Loaded cached music index: " + physical.size() + " tracks");
        } catch (Exception e) {
            Log.w(TAG, "Cached music index could not be loaded", e);
        }
    }

    private void saveCachedIndex(List<AudioTrack> tracks) {
        File target = getIndexFile();
        AtomicFile atomicFile = new AtomicFile(target);
        FileOutputStream output = null;
        try {
            JSONArray array = new JSONArray();
            for (AudioTrack track : tracks) {
                JSONObject item = new JSONObject();
                item.put("id", track.getId());
                item.put("title", track.getTitle());
                item.put("artist", track.getArtist());
                item.put("album", track.getAlbum());
                item.put("duration", track.getDuration());
                item.put("path", track.getPath());
                item.put("content", track.getContentUri() != null ? track.getContentUri().toString() : null);
                item.put("art", track.getAlbumArtUri() != null ? track.getAlbumArtUri().toString() : null);
                item.put("storage", track.getStorageType().name());
                item.put("dateAdded", track.getDateAdded());
                item.put("available", track.isAvailable());
                array.put(item);
            }
            output = atomicFile.startWrite();
            output.write(array.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            atomicFile.finishWrite(output);
        } catch (Exception e) {
            Log.w(TAG, "Music index could not be saved", e);
            if (output != null) atomicFile.failWrite(output);
        }
    }

    public boolean hasMusicReadPermission() {
        String permission = android.os.Build.VERSION.SDK_INT
                >= android.os.Build.VERSION_CODES.TIRAMISU
                ? android.Manifest.permission.READ_MEDIA_AUDIO
                : android.Manifest.permission.READ_EXTERNAL_STORAGE;
        return androidx.core.content.ContextCompat.checkSelfPermission(context, permission)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private void notifyCopyCompleted(OnCopyCompletedListener listener, boolean success, String messageOrPath) {
        if (listener != null) {
            mainHandler.post(() -> listener.onCopyCompleted(success, messageOrPath));
        }
    }

    public synchronized List<AudioTrack> getCachedTracks() {
        return getPhysicalTracks(new ArrayList<>(cachedTracks));
    }

    public synchronized List<AudioTrack> getIndexedTracks() {
        return new ArrayList<>(cachedTracks);
    }

    public List<AudioTrack> getPhysicalTracks(List<AudioTrack> tracks) {
        List<AudioTrack> physical = new ArrayList<>();
        if (tracks == null) return physical;
        for (AudioTrack track : tracks) {
            if (isTrackCurrentlyAvailable(track)) physical.add(track);
        }
        return physical;
    }

    private boolean isTrackCurrentlyAvailable(AudioTrack track) {
        if (track == null) return false;
        if (track.getPath() != null && !track.getPath().isEmpty()) {
            File file = new File(track.getPath());
            return file.exists() && file.length() > 0;
        }
        return track.getContentUri() != null
                && "content".equalsIgnoreCase(track.getContentUri().getScheme());
    }

    private AudioTrack withAvailability(AudioTrack track, boolean available) {
        return new AudioTrack(track.getId(), track.getTitle(), track.getArtist(),
                track.getAlbum(), track.getDuration(), track.getPath(), track.getContentUri(),
                track.getAlbumArtUri(), track.getStorageType(), available, track.getDateAdded());
    }

    private List<AudioTrack> refreshAvailability(List<AudioTrack> tracks) {
        List<AudioTrack> result = new ArrayList<>();
        if (tracks == null) return result;
        for (AudioTrack track : tracks) {
            result.add(withAvailability(track, isTrackCurrentlyAvailable(track)));
        }
        return result;
    }

    private List<AudioTrack> mergeWithUnavailableCachedTracks(List<AudioTrack> scanned) {
        List<AudioTrack> merged = new ArrayList<>(scanned);
        List<AudioTrack> previous;
        synchronized (this) { previous = new ArrayList<>(cachedTracks); }
        for (AudioTrack oldTrack : previous) {
            boolean found = false;
            for (AudioTrack current : scanned) {
                if (MusicTrackIdentity.matches(oldTrack, current)
                        || MusicTrackIdentity.matchesReference(oldTrack.getMediaId(), current)) {
                    found = true;
                    break;
                }
            }
            if (!found) merged.add(withAvailability(oldTrack, false));
        }
        return merged;
    }

    private void refreshCachedAvailability(ScanReason reason) {
        ioExecutor.execute(() -> {
            List<AudioTrack> refreshed;
            synchronized (this) {
                refreshed = refreshAvailability(cachedTracks);
                cachedTracks = refreshed;
                cachedFolders = organizeIntoFolders(refreshed);
                cachedArtists = organizeIntoArtists(getPhysicalTracks(refreshed));
            }
            saveCachedIndex(refreshed);
            ScanState previous = scanState;
            publishScanState(new ScanState(false, reason,
                    previous != null ? previous.lastSuccessfulScanTime : 0L,
                    countAvailable(refreshed),
                    countAvailableStorage(refreshed, StorageType.INTERNAL),
                    countAvailableStorage(refreshed, StorageType.USB), null,
                    hasMusicReadPermission()));
        });
    }

    private static int countStorage(List<AudioTrack> tracks, StorageType type) {
        int count = 0;
        if (tracks != null) for (AudioTrack track : tracks) {
            if (track.getStorageType() == type) count++;
        }
        return count;
    }
    private int countAvailable(List<AudioTrack> tracks) { return getPhysicalTracks(tracks).size(); }
    private int countAvailableStorage(List<AudioTrack> tracks, StorageType type) {
        int count = 0;
        if (tracks != null) for (AudioTrack track : tracks) {
            if (track.getStorageType() == type && isTrackCurrentlyAvailable(track)) count++;
        }
        return count;
    }

    public List<AudioFolder> getFoldersByStorage(StorageType storageType) {
        List<AudioFolder> result = new ArrayList<>();
        for (AudioFolder folder : getCachedFolders()) {
            if (folder.getStorageType() == storageType) {
                List<AudioTrack> validTracks = getPhysicalTracks(folder.getTracks());
                if (!validTracks.isEmpty()) {
                    result.add(new AudioFolder(folder.getName(), folder.getPath(), validTracks, folder.getStorageType()));
                }
            }
        }
        return result;
    }

    public synchronized List<AudioFolder> getCachedFolders() {
        List<AudioFolder> result = new ArrayList<>();
        for (AudioFolder folder : new ArrayList<>(cachedFolders)) {
            List<AudioTrack> validTracks = getPhysicalTracks(folder.getTracks());
            if (!validTracks.isEmpty()) {
                result.add(new AudioFolder(folder.getName(), folder.getPath(), validTracks, folder.getStorageType()));
            }
        }
        return result;
    }

    public synchronized List<AudioArtist> getCachedArtists() {
        return new ArrayList<>(cachedArtists);
    }


    private List<AudioTrack> scanDeviceForAudio() {
        List<AudioTrack> tracks = new ArrayList<>();

        Uri collection;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
        } else {
            collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        }

        String[] projection = new String[] {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATA, // Path
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DATE_ADDED
        };

        // Filter for music only (USB muzikleri elememek icin secim filtresi kaldirildi)
        String selection = null;

        try (Cursor cursor = context.getContentResolver().query(
                collection,
                projection,
                selection,
                null,
                MediaStore.Audio.Media.TITLE + " ASC")) {
            if (cursor != null) {
                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                int titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                int artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                int albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
                int durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
                int dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
                int albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID);
                int dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED);

                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idColumn);
                    String title = cursor.getString(titleColumn);
                    String artist = cursor.getString(artistColumn);
                    String album = cursor.getString(albumColumn);
                    long duration = cursor.getLong(durationColumn);
                    String path = cursor.getString(dataColumn);
                    long albumId = cursor.getLong(albumIdColumn);
                    long dateAdded = cursor.getLong(dateAddedColumn);

                    // Strict Filter: Duration > 15s (Avoids SFX/Notification sounds)
                    if (duration < 15000) {
                        continue;
                    }

                    // Strict Filter: Extension and Path
                    if (path != null) {
                        String lowerPath = path.toLowerCase(java.util.Locale.ROOT);

                        // Allowed extensions (Genisletilmis format destegi)
                        if (!lowerPath.endsWith(".mp3") &&
                                !lowerPath.endsWith(".mp4") &&
                                !lowerPath.endsWith(".flac") &&
                                !lowerPath.endsWith(".m4a") &&
                                !lowerPath.endsWith(".wav") &&
                                !lowerPath.endsWith(".wma") &&
                                !lowerPath.endsWith(".aac") &&
                                !lowerPath.endsWith(".ogg")) {
                            continue;
                        }

                        // Strict Exclusion Filter (WhatsApp, Telegram, Voice Notes, System Sounds, App Data)
                        if (isUnwantedPath(path)) {
                            continue;
                        }
                    }

                    Uri contentUri = ContentUris.withAppendedId(collection, id);
                    Uri albumArtUri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId);

                    // Storage Type Detection (USB vs Internal)
                    StorageType storageType = StorageType.INTERNAL;
                    boolean isAvailable = true;

                    if (path != null) {
                        File file = new File(path);
                        isAvailable = file.exists();
                        String lowerPath = path.toLowerCase(java.util.Locale.ROOT);
                        if (!lowerPath.startsWith("/storage/emulated/") && !lowerPath.startsWith("/data/")) {
                            storageType = StorageType.USB;
                        }
                    }

                    AudioTrack track = new AudioTrack(id, title, artist, album, duration, path,
                            contentUri, albumArtUri, storageType, isAvailable, dateAdded);
                    tracks.add(track);
                }
            }


        } catch (Exception e) {
            Log.e(TAG, "Error scanning audio", e);
        }

        // MediaStore taramasindan sonra dogrudan harici USB disk yollarini da tara (Turkce karakter yok)
        try {
            List<AudioTrack> directTracks = scanDeviceForAudioDirectly();
            for (AudioTrack dt : directTracks) {
                boolean exists = false;
                for (AudioTrack t : tracks) {
                    if (dt.getPath() != null && dt.getPath().equals(t.getPath())) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    tracks.add(dt);
                }
            }
            requestSystemMediaIndex(directTracks);
        } catch (Exception e) {
            Log.e(TAG, "Direct storage scan error: " + e.getMessage());
        }

        return tracks;
    }


    private List<AudioTrack> scanDeviceForAudioDirectly() {
        List<AudioTrack> tracks = new ArrayList<>();
        Set<String> visitedDirectories = new HashSet<>();
        File storageDir = new File("/storage");
        if (storageDir.exists() && storageDir.isDirectory()) {
            File[] volumes = storageDir.listFiles();
            if (volumes != null) {
                for (File vol : volumes) {
                    if (vol.isDirectory()) {
                        String name = vol.getName();
                        // Dahili hafizayi ve gizli klasorleri atla (Turkce karakter yok)
                        if (!name.equals("emulated") && !name.equals("self") && !name.startsWith(".")) {
                            Log.d(TAG, "Taranan harici USB birimi: " + vol.getAbsolutePath());
                            scanDirectoryDirectly(vol, tracks, visitedDirectories);
                        }
                    }
                }
            }
        }

        // Alps teyp yedek USB mount yollarini ve dahili hafiza Müzik/Download klasorlerini de tarayalim
        String[] fallbackPaths = {
            "/storage/emulated/0/Music", "/sdcard/Music",
            "/storage/emulated/0/Download", "/sdcard/Download",
            "/storage/udisk", "/storage/udisk2", "/storage/usb_storage",
            "/mnt/media_rw", "/mnt/usb", "/mnt/usb_storage",
            "/storage/usb0", "/storage/usb1", "/storage/usb2", "/storage/usb3", "/storage/usbotg"
        };
        for (String path : fallbackPaths) {
            File fallbackDir = new File(path);
            if (fallbackDir.exists() && fallbackDir.isDirectory()) {
                scanDirectoryDirectly(fallbackDir, tracks, visitedDirectories);
            }
        }

        return tracks;
    }

    /**
     * Checks if a file path belongs to unwanted directories like WhatsApp, Telegram, Voice Notes,
     * System Ringtones/Notifications, App Data or folders containing .nomedia.
     */
    private boolean isUnwantedPath(String path) {
        if (path == null) return true;

        String lower = path.toLowerCase(java.util.Locale.ROOT);

        // Social Media & Messaging App Voice Notes / Audio Filters
        if (lower.contains("/whatsapp/") ||
                lower.contains("whatsapp audio") ||
                lower.contains("whatsapp voice") ||
                lower.contains("whatsapp documents") ||
                lower.contains("/telegram/") ||
                lower.contains("/viber/") ||
                lower.contains("/line/") ||
                lower.contains("/wechat/") ||
                lower.contains("voice_notes") ||
                lower.contains("voice notes") ||
                lower.contains("/voice_") ||
                lower.contains("call_rec") ||
                lower.contains("call_recording") ||
                lower.contains("/recordings/") ||
                lower.contains("/audiorecordings/")) {
            return true;
        }

        // System Sounds & App Data Filters
        if (lower.contains("/android/data/") ||
                lower.contains("/android/media/") ||
                lower.contains("/notifications/") ||
                lower.contains("/notification/") ||
                lower.contains("/ringtones/") ||
                lower.contains("/ringtone/") ||
                lower.contains("/alarms/") ||
                lower.contains("/alarm/") ||
                lower.contains("/ui/") ||
                lower.contains("/podcasts/")) {
            return true;
        }

        // Check if any parent folder contains a .nomedia file
        File file = new File(path);
        if (hasNoMediaInHierarchy(file.getParentFile())) {
            return true;
        }

        return false;
    }

    /**
     * Recursively checks if a directory or any of its parent directories contain a .nomedia file.
     */
    private boolean hasNoMediaInHierarchy(File dir) {
        File current = dir;
        while (current != null && current.exists()) {
            File noMedia = new File(current, ".nomedia");
            if (noMedia.exists()) {
                return true;
            }
            // Stop at storage root level to avoid unneeded disk traversals
            String path = current.getAbsolutePath();
            if (path.equals("/storage") || path.equals("/storage/emulated") || path.equals("/mnt") || path.equals("/")) {
                break;
            }
            current = current.getParentFile();
        }
        return false;
    }

    private void scanDirectoryDirectly(File dir, List<AudioTrack> tracks,
            Set<String> visitedDirectories) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return;

        try {
            if (!visitedDirectories.add(dir.getCanonicalPath())) return;
        } catch (Exception e) {
            if (!visitedDirectories.add(dir.getAbsolutePath())) return;
        }

        // Skip folders containing .nomedia
        if (hasNoMediaInHierarchy(dir)) {
            return;
        }

        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                String name = file.getName().toLowerCase(java.util.Locale.ROOT);
                if (name.equals("android") || name.equals("lost.dir") || name.startsWith(".")) {
                    continue;
                }
                scanDirectoryDirectly(file, tracks, visitedDirectories);
            } else {
                String path = file.getAbsolutePath();
                if (isUnwantedPath(path)) {
                    continue;
                }

                String lowerPath = path.toLowerCase(java.util.Locale.ROOT);

                if (lowerPath.endsWith(".mp3") ||
                        lowerPath.endsWith(".flac") ||
                        lowerPath.endsWith(".wav") ||
                        lowerPath.endsWith(".m4a") ||
                        lowerPath.endsWith(".wma") ||
                        lowerPath.endsWith(".aac") ||
                        lowerPath.endsWith(".ogg")) {

                    // Durationsuz taramada bildirim seslerini elemek icin boyut filtresi (>500KB) (Turkce karakter yok)
                    if (file.length() < 500 * 1024) {
                        continue;
                    }


                    long id = path.hashCode();
                    String title = file.getName();
                    int dotIndex = title.lastIndexOf('.');
                    if (dotIndex > 0) {
                        title = title.substring(0, dotIndex);
                    }

                    String artist = "Bilinmeyen";
                    String album = "USB Muzik";
                    long duration = 180000; // Varsayilan 3 dk

                    Uri contentUri = Uri.fromFile(file);
                    Uri albumArtUri = Uri.EMPTY;

                    boolean exists = false;
                    for (AudioTrack t : tracks) {
                        if (path.equals(t.getPath())) {
                            exists = true;
                            break;
                        }
                    }

                    if (!exists) {
                        StorageType storageType = (!lowerPath.startsWith("/storage/emulated/") && !lowerPath.startsWith("/data/")) ? StorageType.USB : StorageType.INTERNAL;
                        AudioTrack track = new AudioTrack(id, title, artist, album, duration, path,
                                contentUri, albumArtUri, storageType, true, file.lastModified() / 1000L);
                        tracks.add(track);
                    }
                }
            }
        }
    }

    private void requestSystemMediaIndex(List<AudioTrack> directTracks) {
        if (directTracks == null || directTracks.isEmpty()) return;
        List<String> paths = new ArrayList<>();
        for (AudioTrack track : directTracks) {
            if (track.getPath() != null) paths.add(track.getPath());
        }
        for (int start = 0; start < paths.size(); start += 200) {
            int end = Math.min(start + 200, paths.size());
            MediaScannerConnection.scanFile(context,
                    paths.subList(start, end).toArray(new String[0]), null, null);
        }
    }

    private List<AudioFolder> organizeIntoFolders(List<AudioTrack> tracks) {
        Map<String, List<AudioTrack>> folderMap = new HashMap<>();

        for (AudioTrack track : tracks) {
            if (track.getPath() == null) continue;
            File file = new File(track.getPath());
            File parent = file.getParentFile();
            if (parent != null) {
                String folderPath = parent.getAbsolutePath();

                if (!folderMap.containsKey(folderPath)) {
                    folderMap.put(folderPath, new ArrayList<>());
                }
                folderMap.get(folderPath).add(track);
            }
        }

        List<AudioFolder> folders = new ArrayList<>();
        for (Map.Entry<String, List<AudioTrack>> entry : folderMap.entrySet()) {
            File f = new File(entry.getKey());
            StorageType folderStorage = StorageType.INTERNAL;
            String lowerPath = entry.getKey().toLowerCase(java.util.Locale.ROOT);
            if (!lowerPath.startsWith("/storage/emulated/") && !lowerPath.startsWith("/data/")) {
                folderStorage = StorageType.USB;
            }
            folders.add(new AudioFolder(f.getName(), entry.getKey(), entry.getValue(), folderStorage));
        }

        // Sort folders by name
        Collections.sort(folders, new Comparator<AudioFolder>() {
            @Override
            public int compare(AudioFolder o1, AudioFolder o2) {
                return o1.getName().compareToIgnoreCase(o2.getName());
            }
        });

        return folders;
    }

    private List<AudioArtist> organizeIntoArtists(List<AudioTrack> tracks) {
        Map<String, List<AudioTrack>> artistMap = new HashMap<>();

        for (AudioTrack track : tracks) {
            String artistName = (track.getArtist() != null && !track.getArtist().trim().isEmpty())
                                ? track.getArtist().trim() : "Bilinmeyen Sanatci";

            if (!artistMap.containsKey(artistName)) {
                artistMap.put(artistName, new ArrayList<>());
            }
            artistMap.get(artistName).add(track);
        }

        List<AudioArtist> artists = new ArrayList<>();
        for (Map.Entry<String, List<AudioTrack>> entry : artistMap.entrySet()) {
            artists.add(new AudioArtist(entry.getKey(), entry.getValue()));
        }

        // Sort artists by name
        Collections.sort(artists, new Comparator<AudioArtist>() {
            @Override
            public int compare(AudioArtist o1, AudioArtist o2) {
                return o1.getName().compareToIgnoreCase(o2.getName());
            }
        });

        return artists;
    }

    /**
     * Port farketmeksizin (usb0, usb1, udisk2 vb.) bir kayitli dosya yolunu
     * o an taranan disklerdeki gercek fiziksel yolla eslestirir (Port-Agnostic Re-linking).
     */
    public AudioTrack findTrackPortAgnostic(String savedPath) {
        return findTrackByReference(savedPath);
    }

    /**
     * Resolves both current stable media IDs and legacy absolute-path records.
     */
    public synchronized AudioTrack findTrackByReference(String reference) {
        if (reference == null) return null;

        for (AudioTrack track : cachedTracks) {
            if (isTrackCurrentlyAvailable(track)
                    && MusicTrackIdentity.matchesReference(reference, track)) {
                return track;
            }
        }
        for (AudioTrack track : cachedTracks) {
            if (MusicTrackIdentity.matchesReference(reference, track)) return track;
        }

        // A legacy removable-storage path may be mounted under another port.
        String targetRelative = extractRelativePath(reference);
        if (targetRelative != null && !targetRelative.isEmpty()) {
            for (AudioTrack track : cachedTracks) {
                if (track.getRelativePath() != null && track.getRelativePath().equalsIgnoreCase(targetRelative)) {
                    return track;
                }
            }
        }

        return null;
    }

    public static String extractVolumeId(String path) {
        if (path == null) return "INTERNAL";
        String lower = path.toLowerCase(java.util.Locale.ROOT);
        if (lower.startsWith("/storage/emulated/") || lower.startsWith("/data/")) {
            return "INTERNAL";
        }
        String[] parts = path.split("/");
        if (parts.length >= 3) {
            return parts[2]; // Örn: 1A2B-3C4D veya usb0
        }
        return "USB_GENERIC";
    }

    public static String extractRelativePath(String path) {
        if (path == null) return "";
        String lower = path.toLowerCase(java.util.Locale.ROOT);
        if (lower.startsWith("/storage/emulated/0/")) {
            return path.substring("/storage/emulated/0/".length());
        }
        String[] parts = path.split("/");
        if (parts.length >= 4) {
            StringBuilder sb = new StringBuilder();
            for (int i = 3; i < parts.length; i++) {
                if (i > 3) sb.append("/");
                sb.append(parts[i]);
            }
            return sb.toString();
        }
        return path;
    }

    // --- Data Models ---

    public enum StorageType {
        INTERNAL,
        USB
    }

    public static class AudioTrack {
        private final long id;
        private final String title;
        private final String artist;
        private final String album;
        private final long duration;
        private final String path;
        private final Uri contentUri;
        private final Uri albumArtUri;
        private final StorageType storageType;
        private final boolean isAvailable;
        private final String volumeId;
        private final String relativePath;
        private final String mediaId;
        private final long dateAdded;

        public AudioTrack(long id, String title, String artist, String album, long duration, String path,
                Uri contentUri, Uri albumArtUri) {
            this(id, title, artist, album, duration, path, contentUri, albumArtUri, StorageType.INTERNAL, true);
        }

        public AudioTrack(long id, String title, String artist, String album, long duration, String path,
                Uri contentUri, Uri albumArtUri, StorageType storageType, boolean isAvailable) {
            this(id, title, artist, album, duration, path, contentUri, albumArtUri,
                    storageType, isAvailable, 0L);
        }

        public AudioTrack(long id, String title, String artist, String album, long duration, String path,
                Uri contentUri, Uri albumArtUri, StorageType storageType, boolean isAvailable,
                long dateAdded) {
            this.id = id;
            this.title = title;
            this.artist = artist;
            this.album = album;
            this.duration = duration;
            this.path = path;
            this.contentUri = contentUri;
            this.albumArtUri = albumArtUri;
            this.storageType = storageType;
            this.isAvailable = isAvailable;
            this.volumeId = extractVolumeId(path);
            this.relativePath = extractRelativePath(path);
            this.mediaId = MusicTrackIdentity.create(id, contentUri, volumeId, relativePath, path);
            this.dateAdded = dateAdded;
        }

        public long getId() {
            return id;
        }

        public String getMediaId() {
            return mediaId;
        }

        public String getTitle() {
            return title;
        }

        public String getArtist() {
            return artist;
        }

        public String getAlbum() {
            return album;
        }

        public long getDuration() {
            return duration;
        }

        public long getDateAdded() {
            return dateAdded;
        }

        public Uri getContentUri() {
            return contentUri;
        }

        public String getPath() {
            return path;
        }

        public Uri getAlbumArtUri() {
            return albumArtUri;
        }

        public StorageType getStorageType() {
            return storageType;
        }

        public boolean isAvailable() {
            return isAvailable;
        }

        public boolean isUsb() {
            return storageType == StorageType.USB;
        }

        public String getVolumeId() {
            return volumeId;
        }

        public String getRelativePath() {
            return relativePath;
        }

        public boolean isSameMedia(AudioTrack other) {
            return MusicTrackIdentity.matches(this, other);
        }
    }


    public static class AudioFolder {
        private final String name;
        private final String path;
        private final List<AudioTrack> tracks;
        private final StorageType storageType;

        public AudioFolder(String name, String path, List<AudioTrack> tracks) {
            this(name, path, tracks, StorageType.INTERNAL);
        }

        public AudioFolder(String name, String path, List<AudioTrack> tracks, StorageType storageType) {
            this.name = name;
            this.path = path;
            this.tracks = tracks;
            this.storageType = storageType;
        }

        public String getName() {
            return name;
        }

        public String getPath() {
            return path;
        }

        public List<AudioTrack> getTracks() {
            return tracks;
        }

        public StorageType getStorageType() {
            return storageType;
        }

        public boolean isUsb() {
            return storageType == StorageType.USB;
        }
    }

    public static class AudioArtist {
        private final String name;
        private final List<AudioTrack> tracks;

        public AudioArtist(String name, List<AudioTrack> tracks) {
            this.name = name;
            this.tracks = tracks;
        }

        public String getName() {
            return name;
        }

        public List<AudioTrack> getTracks() {
            return tracks;
        }
    }
}

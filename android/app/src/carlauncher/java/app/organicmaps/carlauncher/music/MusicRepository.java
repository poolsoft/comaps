package app.organicmaps.carlauncher.music;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    // Singleton support if needed, or instantiated by MusicManager
    public MusicRepository(Context context) {
        this.context = context;
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
        synchronized (scanLock) {
            if (listener != null) {
                pendingScanListeners.add(listener);
            }
            if (scanInProgress) {
                return;
            }
            scanInProgress = true;
        }
        ioExecutor.execute(() -> {
            List<AudioTrack> tracks = hasMusicReadPermission()
                    ? scanDeviceForAudio() : new ArrayList<>();
            List<AudioFolder> folders = organizeIntoFolders(tracks);
            List<AudioArtist> artists = organizeIntoArtists(tracks);

            synchronized (this) {
                cachedTracks = tracks;
                cachedFolders = folders;
                cachedArtists = artists;
            }

            List<OnScanCompletedListener> callbacks;
            synchronized (scanLock) {
                callbacks = new ArrayList<>(pendingScanListeners);
                pendingScanListeners.clear();
                scanInProgress = false;
            }
            for (OnScanCompletedListener callback : callbacks) {
                mainHandler.post(() -> callback.onScanCompleted(
                        new ArrayList<>(tracks), new ArrayList<>(folders),
                        new ArrayList<>(artists)));
            }
        });
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

    public List<AudioTrack> getPhysicalTracks(List<AudioTrack> tracks) {
        List<AudioTrack> physical = new ArrayList<>();
        if (tracks == null) return physical;
        for (AudioTrack track : tracks) {
            if (track.getContentUri() != null
                    && "content".equalsIgnoreCase(track.getContentUri().getScheme())) {
                physical.add(track);
            } else if (track.getPath() != null) {
                File f = new File(track.getPath());
                if (f.exists() && f.length() > 0) {
                    physical.add(track);
                }
            }
        }
        return physical;
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
        } catch (Exception e) {
            Log.e(TAG, "Direct storage scan error: " + e.getMessage());
        }

        return tracks;
    }


    private List<AudioTrack> scanDeviceForAudioDirectly() {
        List<AudioTrack> tracks = new ArrayList<>();
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
                            scanDirectoryDirectly(vol, tracks);
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
                scanDirectoryDirectly(fallbackDir, tracks);
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

    private void scanDirectoryDirectly(File dir, List<AudioTrack> tracks) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return;

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
                scanDirectoryDirectly(file, tracks);
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
            if (MusicTrackIdentity.matchesReference(reference, track)) {
                return track;
            }
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

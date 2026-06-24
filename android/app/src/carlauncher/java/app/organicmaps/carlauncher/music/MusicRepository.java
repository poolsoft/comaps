package app.organicmaps.carlauncher.music;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Repository for scanning and managing local music files.
 * Handles checking external apps vs local files.
 */
public class MusicRepository {

    private static final String TAG = "MusicRepository";
    private final Context context;
    private List<AudioTrack> cachedTracks = new ArrayList<>();
    private List<AudioFolder> cachedFolders = new ArrayList<>();
    private List<AudioArtist> cachedArtists = new ArrayList<>();

    // Singleton support if needed, or instantiated by MusicManager
    public MusicRepository(Context context) {
        this.context = context;
    }

    public interface OnScanCompletedListener {
        void onScanCompleted(List<AudioTrack> tracks, List<AudioFolder> folders, List<AudioArtist> artists);
    }

    /**
     * Scan device for music files asynchronously.
     */
    public void scanMusic(final OnScanCompletedListener listener) {
        new Thread(() -> {
            List<AudioTrack> tracks = scanDeviceForAudio();
            List<AudioFolder> folders = organizeIntoFolders(tracks);
            List<AudioArtist> artists = organizeIntoArtists(tracks);

            synchronized (this) {
                cachedTracks = tracks;
                cachedFolders = folders;
                cachedArtists = artists;
            }

            if (listener != null) {
                // Return on main thread if possible, but caller usually handles threading
                listener.onScanCompleted(tracks, folders, artists);
            }
        }).start();
    }

    public List<AudioTrack> getCachedTracks() {
        return cachedTracks;
    }

    public List<AudioFolder> getCachedFolders() {
        return cachedFolders;
    }

    public List<AudioArtist> getCachedArtists() {
        return cachedArtists;
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
                MediaStore.Audio.Media.ALBUM_ID
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

                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idColumn);
                    String title = cursor.getString(titleColumn);
                    String artist = cursor.getString(artistColumn);
                    String album = cursor.getString(albumColumn);
                    long duration = cursor.getLong(durationColumn);
                    String path = cursor.getString(dataColumn);
                    long albumId = cursor.getLong(albumIdColumn);

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

                        // Exclude unwanted folders (App Data, Social Media Audio, System Sounds)
                        if (lowerPath.contains("/android/data/") ||
                                lowerPath.contains("/whatsapp/") ||
                                lowerPath.contains("/telegram/") ||
                                lowerPath.contains("/notifications/") ||
                                lowerPath.contains("/ringtones/") ||
                                lowerPath.contains("/alarms/") ||
                                lowerPath.contains("/samsung/music/")) { // Example generic exclusion if needed
                            continue;
                        }
                    }

                    Uri contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);

                    // Album art uri
                    Uri albumArtUri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"),
                            albumId);

                    AudioTrack track = new AudioTrack(id, title, artist, album, duration, path, contentUri,
                            albumArtUri);
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

        // Alps teyp yedek USB mount yollarini ve dahili hafiza MÃ¼zik/Download klasorlerini de tarayalim
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

    private void scanDirectoryDirectly(File dir, List<AudioTrack> tracks) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return;

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
                        AudioTrack track = new AudioTrack(id, title, artist, album, duration, path, contentUri, albumArtUri);
                        tracks.add(track);
                    }
                }
            }
        }
    }

    private List<AudioFolder> organizeIntoFolders(List<AudioTrack> tracks) {
        Map<String, List<AudioTrack>> folderMap = new HashMap<>();

        for (AudioTrack track : tracks) {
            if (track.getPath() == null)
                continue;

            File file = new File(track.getPath());
            File parent = file.getParentFile();
            if (parent != null) {
                String folderName = parent.getName();
                String folderPath = parent.getAbsolutePath();

                // Use path as key to handle duplicate names in different locations
                if (!folderMap.containsKey(folderPath)) {
                    folderMap.put(folderPath, new ArrayList<>());
                }
                folderMap.get(folderPath).add(track);
            }
        }

        List<AudioFolder> folders = new ArrayList<>();
        for (Map.Entry<String, List<AudioTrack>> entry : folderMap.entrySet()) {
            File f = new File(entry.getKey());
            folders.add(new AudioFolder(f.getName(), entry.getKey(), entry.getValue()));
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

    // --- Data Models ---

    public static class AudioTrack {
        private final long id;
        private final String title;
        private final String artist;
        private final String album;
        private final long duration;
        private final String path;
        private final Uri contentUri;
        private final Uri albumArtUri;

        public AudioTrack(long id, String title, String artist, String album, long duration, String path,
                Uri contentUri, Uri albumArtUri) {
            this.id = id;
            this.title = title;
            this.artist = artist;
            this.album = album;
            this.duration = duration;
            this.path = path;
            this.contentUri = contentUri;
            this.albumArtUri = albumArtUri;
        }

        public String getTitle() {
            return title;
        }

        public String getArtist() {
            return artist;
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
    }

    public static class AudioFolder {
        private final String name;
        private final String path;
        private final List<AudioTrack> tracks;

        public AudioFolder(String name, String path, List<AudioTrack> tracks) {
            this.name = name;
            this.path = path;
            this.tracks = tracks;
        }

        public String getName() {
            return name;
        }

        public List<AudioTrack> getTracks() {
            return tracks;
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

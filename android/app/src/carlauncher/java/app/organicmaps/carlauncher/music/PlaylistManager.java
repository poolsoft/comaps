package app.organicmaps.carlauncher.music;

import android.content.Context;
import android.content.SharedPreferences;

import net.osmand.PlatformUtil;

import org.apache.commons.logging.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Playlist ve son calinanlar yonetimi.
 * SharedPreferences ile JSON formatinda saklar.
 */
public class PlaylistManager {

    private static final Log LOG = PlatformUtil.getLog(PlaylistManager.class);
    private static final String PREFS_NAME = "music_playlists";
    private static final String KEY_PLAYLISTS = "playlists";
    private static final String KEY_RECENTLY_PLAYED = "recently_played";
    private static final String KEY_FAVORITES = "favorites";
    private static final String KEY_PLAY_COUNTS = "play_counts";
    private static final int MAX_RECENT = 50;
    private static final int DATA_VERSION = 2;
    private static final String KEY_DATA_VERSION = "data_version";

    private final SharedPreferences prefs;

    public PlaylistManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // --- Playlist CRUD ---

    public synchronized List<Playlist> getAllPlaylists() {
        List<Playlist> result = new ArrayList<>();
        try {
            String json = prefs.getString(KEY_PLAYLISTS, "[]");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                Playlist p = new Playlist();
                p.id = obj.getString("id");
                p.name = obj.getString("name");
                p.tracks = jsonArrayToList(obj.getJSONArray("tracks"));
                result.add(p);
            }
        } catch (JSONException e) {
            LOG.error("Unable to read saved playlists", e);
        }
        return result;
    }

    public synchronized void savePlaylist(Playlist playlist) {
        List<Playlist> all = getAllPlaylists();

        // Update or add
        boolean found = false;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id.equals(playlist.id)) {
                all.set(i, playlist);
                found = true;
                break;
            }
        }
        if (!found) {
            all.add(playlist);
        }

        savePlaylists(all);
    }

    public synchronized void deletePlaylist(String playlistId) {
        List<Playlist> all = getAllPlaylists();
        all.removeIf(p -> p.id.equals(playlistId));
        savePlaylists(all);
    }

    private void savePlaylists(List<Playlist> playlists) {
        try {
            JSONArray arr = new JSONArray();
            for (Playlist p : playlists) {
                JSONObject obj = new JSONObject();
                obj.put("id", p.id);
                obj.put("name", p.name);
                obj.put("tracks", new JSONArray(p.tracks));
                arr.put(obj);
            }
            prefs.edit()
                    .putInt(KEY_DATA_VERSION, DATA_VERSION)
                    .putString(KEY_PLAYLISTS, arr.toString())
                    .apply();
        } catch (JSONException e) {
            LOG.error("Unable to save playlists", e);
        }
    }

    // --- Recently Played ---

    public synchronized List<String> getRecentlyPlayed() {
        try {
            String json = prefs.getString(KEY_RECENTLY_PLAYED, "[]");
            return jsonArrayToList(new JSONArray(json));
        } catch (JSONException e) {
            return new ArrayList<>();
        }
    }

    public synchronized void addToRecentlyPlayed(String trackReference) {
        List<String> recent = getRecentlyPlayed();

        // Remove if exists (to move to top)
        recent.remove(trackReference);

        // Add to beginning
        recent.add(0, trackReference);

        // Limit size
        while (recent.size() > MAX_RECENT) {
            recent.remove(recent.size() - 1);
        }

        saveStringList(KEY_RECENTLY_PLAYED, recent);
    }

    public synchronized void addToRecentlyPlayed(MusicRepository.AudioTrack track) {
        if (track != null) {
            List<String> recent = getRecentlyPlayed();
            recent.removeIf(reference -> MusicTrackIdentity.matchesReference(reference, track));
            recent.add(0, track.getMediaId());
            while (recent.size() > MAX_RECENT) {
                recent.remove(recent.size() - 1);
            }
            saveStringList(KEY_RECENTLY_PLAYED, recent);
            incrementPlayCount(track);
        }
    }

    public synchronized int getPlayCount(MusicRepository.AudioTrack track) {
        if (track == null) return 0;
        JSONObject counts = getPlayCounts();
        int count = counts.optInt(track.getMediaId(), -1);
        if (count >= 0) {
            return count;
        }
        // Read an old absolute-path key until it is migrated by the next play.
        return track.getPath() == null ? 0 : counts.optInt(track.getPath(), 0);
    }

    private synchronized void incrementPlayCount(MusicRepository.AudioTrack track) {
        JSONObject counts = getPlayCounts();
        int current = getPlayCount(track);
        try {
            counts.put(track.getMediaId(), current + 1);
            if (track.getPath() != null) {
                counts.remove(track.getPath());
            }
            prefs.edit()
                    .putInt(KEY_DATA_VERSION, DATA_VERSION)
                    .putString(KEY_PLAY_COUNTS, counts.toString())
                    .apply();
        } catch (JSONException ignored) {
            // JSONObject accepts string keys and integer values; this is defensive.
        }
    }

    private JSONObject getPlayCounts() {
        try {
            return new JSONObject(prefs.getString(KEY_PLAY_COUNTS, "{}"));
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    // --- Shuffle ---

    public List<String> shuffleTracks(List<String> tracks) {
        List<String> shuffled = new ArrayList<>(tracks);
        Collections.shuffle(shuffled);
        return shuffled;
    }

    // --- Favorites ---

    public synchronized List<String> getFavorites() {
        try {
            String json = prefs.getString(KEY_FAVORITES, "[]");
            return jsonArrayToList(new JSONArray(json));
        } catch (JSONException e) {
            return new ArrayList<>();
        }
    }

    public synchronized void addToFavorites(String trackReference) {
        List<String> favs = getFavorites();
        if (!favs.contains(trackReference)) {
            favs.add(trackReference);
            saveStringList(KEY_FAVORITES, favs);
        }
    }

    public void addToFavorites(MusicRepository.AudioTrack track) {
        if (track != null) {
            addToFavorites(track.getMediaId());
        }
    }

    public synchronized void removeFromFavorites(String trackReference) {
        List<String> favs = getFavorites();
        if (favs.remove(trackReference)) {
            saveStringList(KEY_FAVORITES, favs);
        }
    }

    public synchronized void removeFromFavorites(MusicRepository.AudioTrack track) {
        if (track == null) return;
        List<String> favorites = getFavorites();
        boolean changed = favorites.removeIf(reference ->
                MusicTrackIdentity.matchesReference(reference, track));
        if (changed) {
            saveStringList(KEY_FAVORITES, favorites);
        }
    }

    public synchronized boolean isFavorite(String trackReference) {
        return getFavorites().contains(trackReference);
    }

    public synchronized boolean isFavorite(MusicRepository.AudioTrack track) {
        if (track == null) return false;
        for (String reference : getFavorites()) {
            if (MusicTrackIdentity.matchesReference(reference, track)) {
                return true;
            }
        }
        return false;
    }

    // --- Helpers ---

    private List<String> jsonArrayToList(JSONArray arr) throws JSONException {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            list.add(arr.getString(i));
        }
        return list;
    }

    private void saveStringList(String key, List<String> values) {
        prefs.edit()
                .putInt(KEY_DATA_VERSION, DATA_VERSION)
                .putString(key, new JSONArray(values).toString())
                .apply();
    }

    // --- Data Classes ---

    public static class Playlist {
        public String id;
        public String name;
        public List<String> tracks = new ArrayList<>();

        public Playlist() {
            this.id = UUID.randomUUID().toString();
        }

        public Playlist(String name) {
            this();
            this.name = name;
        }
    }
}

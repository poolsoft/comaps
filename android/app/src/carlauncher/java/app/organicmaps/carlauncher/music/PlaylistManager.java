package app.organicmaps.carlauncher.music;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Playlist ve son calinanlar yonetimi.
 * SharedPreferences ile JSON formatinda saklar.
 */
public class PlaylistManager {

    private static final String PREFS_NAME = "music_playlists";
    private static final String KEY_PLAYLISTS = "playlists";
    private static final String KEY_RECENTLY_PLAYED = "recently_played";
    private static final String KEY_FAVORITES = "favorites";
    private static final int MAX_RECENT = 50;

    private final SharedPreferences prefs;

    public PlaylistManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // --- Playlist CRUD ---

    public List<Playlist> getAllPlaylists() {
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
            e.printStackTrace();
        }
        return result;
    }

    public void savePlaylist(Playlist playlist) {
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

    public void deletePlaylist(String playlistId) {
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
            prefs.edit().putString(KEY_PLAYLISTS, arr.toString()).apply();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    // --- Recently Played ---

    public List<String> getRecentlyPlayed() {
        try {
            String json = prefs.getString(KEY_RECENTLY_PLAYED, "[]");
            return jsonArrayToList(new JSONArray(json));
        } catch (JSONException e) {
            return new ArrayList<>();
        }
    }

    public void addToRecentlyPlayed(String trackPath) {
        List<String> recent = getRecentlyPlayed();

        // Remove if exists (to move to top)
        recent.remove(trackPath);

        // Add to beginning
        recent.add(0, trackPath);

        // Limit size
        while (recent.size() > MAX_RECENT) {
            recent.remove(recent.size() - 1);
        }

        prefs.edit().putString(KEY_RECENTLY_PLAYED, new JSONArray(recent).toString()).apply();
    }

    // --- Shuffle ---

    public List<String> shuffleTracks(List<String> tracks) {
        List<String> shuffled = new ArrayList<>(tracks);
        Collections.shuffle(shuffled);
        return shuffled;
    }

    // --- Favorites ---

    public List<String> getFavorites() {
        try {
            String json = prefs.getString(KEY_FAVORITES, "[]");
            return jsonArrayToList(new JSONArray(json));
        } catch (JSONException e) {
            return new ArrayList<>();
        }
    }

    public void addToFavorites(String trackPath) {
        List<String> favs = getFavorites();
        if (!favs.contains(trackPath)) {
            favs.add(trackPath);
            prefs.edit().putString(KEY_FAVORITES, new JSONArray(favs).toString()).apply();
        }
    }

    public void removeFromFavorites(String trackPath) {
        List<String> favs = getFavorites();
        if (favs.remove(trackPath)) {
            prefs.edit().putString(KEY_FAVORITES, new JSONArray(favs).toString()).apply();
        }
    }

    public boolean isFavorite(String trackPath) {
        return getFavorites().contains(trackPath);
    }

    // --- Helpers ---

    private List<String> jsonArrayToList(JSONArray arr) throws JSONException {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            list.add(arr.getString(i));
        }
        return list;
    }

    // --- Data Classes ---

    public static class Playlist {
        public String id;
        public String name;
        public List<String> tracks = new ArrayList<>();

        public Playlist() {
            this.id = String.valueOf(System.currentTimeMillis());
        }

        public Playlist(String name) {
            this();
            this.name = name;
        }
    }
}

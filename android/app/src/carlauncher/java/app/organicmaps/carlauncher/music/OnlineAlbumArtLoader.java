package app.organicmaps.carlauncher.music;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Optional, cached online cover lookup for tracks which have no embedded artwork. */
public final class OnlineAlbumArtLoader {
    public interface Callback { void onLoaded(@Nullable Bitmap bitmap); }
    private static final String PREFS = "car_launcher_prefs";
    private static final String POLICY_KEY = "car_launcher_online_album_art";
    private static final String USER_AGENT = "CoMaps-Auto/1.0 (https://github.com/poolsoft/comaps)";
    private static volatile OnlineAlbumArtLoader instance;
    private final Context context;
    private final File cacheDir;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Set<String> pending = Collections.synchronizedSet(new HashSet<>());
    private long lastMusicBrainzRequest;

    private OnlineAlbumArtLoader(Context context) {
        this.context = context.getApplicationContext();
        cacheDir = new File(this.context.getCacheDir(), "online_album_art");
        if (!cacheDir.exists()) cacheDir.mkdirs();
    }

    public static OnlineAlbumArtLoader getInstance(Context context) {
        if (instance == null) synchronized (OnlineAlbumArtLoader.class) {
            if (instance == null) instance = new OnlineAlbumArtLoader(context);
        }
        return instance;
    }

    public void load(String title, String artist, Callback callback) {
        String policy = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(POLICY_KEY, "off");
        if ("off".equals(policy) || isUnknown(title) || isUnknown(artist) || !networkAllowed(policy)) return;
        String key = sha256(title.trim().toLowerCase() + "\n" + artist.trim().toLowerCase());
        File imageFile = new File(cacheDir, key + ".jpg");
        Bitmap cached = BitmapFactory.decodeFile(imageFile.getAbsolutePath());
        if (cached != null) { callback.onLoaded(cached); return; }
        File missFile = new File(cacheDir, key + ".miss");
        if (missFile.exists() && System.currentTimeMillis() - missFile.lastModified() < 7L * 24 * 60 * 60 * 1000) return;
        if (!pending.add(key)) return;
        executor.execute(() -> {
            Bitmap result = null;
            try {
                String groupId = findReleaseGroup(title, artist);
                if (groupId != null) result = downloadCover(groupId);
                if (result != null) {
                    try (FileOutputStream out = new FileOutputStream(imageFile)) {
                        result.compress(Bitmap.CompressFormat.JPEG, 88, out);
                    }
                } else missFile.createNewFile();
            } catch (Exception ignored) {
                // Network failures are not negative-cached; the next playback may retry.
            } finally { pending.remove(key); }
            Bitmap delivered = result;
            if (delivered != null) mainHandler.post(() -> callback.onLoaded(delivered));
        });
    }

    private boolean networkAllowed(String policy) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        return cm != null && cm.getActiveNetwork() != null && ("any".equals(policy) || !cm.isActiveNetworkMetered());
    }

    @Nullable
    private String findReleaseGroup(String title, String artist) throws Exception {
        long wait = 1100L - (System.currentTimeMillis() - lastMusicBrainzRequest);
        if (wait > 0) Thread.sleep(wait);
        lastMusicBrainzRequest = System.currentTimeMillis();
        String query = "recording:\"" + cleanQuery(title) + "\" AND artist:\"" + cleanQuery(artist) + "\"";
        String url = "https://musicbrainz.org/ws/2/recording/?fmt=json&limit=3&query="
                + URLEncoder.encode(query, StandardCharsets.UTF_8.name());
        JSONArray recordings = new JSONObject(readText(url)).optJSONArray("recordings");
        if (recordings == null) return null;
        for (int i = 0; i < recordings.length(); i++) {
            JSONObject recording = recordings.optJSONObject(i);
            if (recording == null || recording.optInt("score", 0) < 75) continue;
            JSONArray releases = recording.optJSONArray("releases");
            if (releases == null) continue;
            for (int j = 0; j < releases.length(); j++) {
                JSONObject release = releases.optJSONObject(j);
                JSONObject group = release != null ? release.optJSONObject("release-group") : null;
                if (group != null && !group.optString("id").isEmpty()) return group.optString("id");
            }
        }
        return null;
    }

    @Nullable
    private Bitmap downloadCover(String groupId) throws Exception {
        JSONArray images = new JSONObject(readText("https://coverartarchive.org/release-group/" + groupId + "/"))
                .optJSONArray("images");
        if (images == null) return null;
        for (int i = 0; i < images.length(); i++) {
            JSONObject image = images.optJSONObject(i);
            if (image == null || !image.optBoolean("front", false)) continue;
            JSONObject thumbnails = image.optJSONObject("thumbnails");
            String imageUrl = thumbnails != null ? thumbnails.optString("500") : null;
            if (imageUrl == null || imageUrl.isEmpty()) imageUrl = image.optString("image");
            if (!imageUrl.isEmpty()) {
                if (imageUrl.startsWith("http://coverartarchive.org/")) {
                    imageUrl = "https://" + imageUrl.substring("http://".length());
                }
                byte[] bytes = readBytes(imageUrl);
                return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            }
        }
        return null;
    }

    private String readText(String url) throws Exception { return new String(readBytes(url), StandardCharsets.UTF_8); }

    private byte[] readBytes(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(7000);
        connection.setReadTimeout(10000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        try (InputStream in = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1 && out.size() < 3 * 1024 * 1024) out.write(buffer, 0, read);
            return out.toByteArray();
        } finally { connection.disconnect(); }
    }

    private static boolean isUnknown(String value) {
        if (value == null) return true;
        String clean = value.trim().toLowerCase();
        return clean.isEmpty() || "unknown".equals(clean) || "<unknown>".equals(clean) || "bilinmeyen".equals(clean);
    }
    private static String cleanQuery(String value) { return value.replace("\\", " ").replace("\"", " ").trim(); }
    private static String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte b : bytes) result.append(String.format("%02x", b));
            return result.toString();
        } catch (Exception e) { return Integer.toHexString(value.hashCode()); }
    }
}

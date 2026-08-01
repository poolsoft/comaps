package app.organicmaps.carlauncher.antenna;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Persistent, map-engine-independent state for the antenna alignment feature. */
public final class AntennaManager {
    public static final String PICK_SOURCE = "SOURCE";
    public static final String PICK_TARGET = "TARGET";

    private static final String PREFS_NAME = "antenna_prefs";
    private static final String KEY_SOURCE = "point_source";
    private static final String KEY_TARGET = "point_target";
    private static final String KEY_SOURCE_LEGACY = "point_a";
    private static final String KEY_TARGET_LEGACY = "point_b";
    private static volatile AntennaManager instance;

    @NonNull private final SharedPreferences preferences;
    @NonNull private final List<AntennaListener> listeners = new CopyOnWriteArrayList<>();
    @Nullable private AntennaPoint source;
    @Nullable private AntennaPoint target;
    @Nullable private String pickingMode;
    private boolean layerVisible;

    private AntennaManager(@NonNull Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        source = readPoint(KEY_SOURCE, KEY_SOURCE_LEGACY);
        target = readPoint(KEY_TARGET, KEY_TARGET_LEGACY);
    }

    @NonNull
    public static AntennaManager getInstance(@NonNull Context context) {
        AntennaManager local = instance;
        if (local == null) {
            synchronized (AntennaManager.class) {
                local = instance;
                if (local == null) instance = local = new AntennaManager(context);
            }
        }
        return local;
    }

    public void addListener(@NonNull AntennaListener listener) { listeners.add(listener); }
    public void removeListener(@NonNull AntennaListener listener) { listeners.remove(listener); }

    public void setSource(double latitude, double longitude, double altitude, @NonNull String name) {
        source = new AntennaPoint(latitude, longitude, altitude, name);
        saveAndNotify();
    }

    public void setTarget(double latitude, double longitude, double altitude, @NonNull String name) {
        target = new AntennaPoint(latitude, longitude, altitude, name);
        saveAndNotify();
    }

    @Nullable public AntennaPoint getSource() { return source; }
    @Nullable public AntennaPoint getTarget() { return target; }

    public void clearSource() { source = null; saveAndNotify(); }
    public void clearTarget() { target = null; saveAndNotify(); }
    public void clearPoints() { source = null; target = null; saveAndNotify(); }

    public void swapPoints() {
        AntennaPoint oldSource = source;
        source = target;
        target = oldSource;
        saveAndNotify();
    }

    public boolean isLayerVisible() { return layerVisible; }
    public void setLayerVisible(boolean visible) { layerVisible = visible; notifyListeners(); }
    @Nullable public String getPickingMode() { return pickingMode; }
    public void setPickingMode(@Nullable String mode) { pickingMode = mode; notifyListeners(); }

    public double getDistanceMeters() {
        if (source == null || target == null) return 0.0;
        float[] result = new float[1];
        Location.distanceBetween(source.latitude, source.longitude, target.latitude, target.longitude, result);
        return result[0];
    }

    public double getAzimuthSourceToTarget() {
        if (source == null || target == null) return 0.0;
        Location from = new Location("antenna-source");
        from.setLatitude(source.latitude);
        from.setLongitude(source.longitude);
        Location to = new Location("antenna-target");
        to.setLatitude(target.latitude);
        to.setLongitude(target.longitude);
        return (from.bearingTo(to) + 360.0) % 360.0;
    }

    public double getElevationSourceToTarget() {
        if (source == null || target == null) return 0.0;
        return Math.toDegrees(Math.atan2(target.altitude - source.altitude, getDistanceMeters()));
    }

    private void saveAndNotify() {
        SharedPreferences.Editor editor = preferences.edit();
        writePoint(editor, KEY_SOURCE, source);
        writePoint(editor, KEY_TARGET, target);
        editor.apply();
        notifyListeners();
    }

    private static void writePoint(@NonNull SharedPreferences.Editor editor, @NonNull String key,
                                   @Nullable AntennaPoint point) {
        if (point == null) editor.remove(key);
        else editor.putString(key, point.toJson().toString());
    }

    @Nullable
    private AntennaPoint readPoint(@NonNull String key, @NonNull String legacyKey) {
        String json = preferences.getString(key, null);
        if (json == null) json = preferences.getString(legacyKey, null);
        return json == null ? null : AntennaPoint.fromJson(json);
    }

    private void notifyListeners() {
        for (AntennaListener listener : listeners) listener.onAntennaPointsChanged();
    }

    public interface AntennaListener { void onAntennaPointsChanged(); }

    public static final class AntennaPoint {
        public final double latitude;
        public final double longitude;
        public final double altitude;
        @NonNull public final String name;

        AntennaPoint(double latitude, double longitude, double altitude, @NonNull String name) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.altitude = altitude;
            this.name = name;
        }

        @NonNull JSONObject toJson() {
            JSONObject object = new JSONObject();
            try {
                object.put("lat", latitude);
                object.put("lon", longitude);
                object.put("alt", altitude);
                object.put("name", name);
            } catch (JSONException ignored) {}
            return object;
        }

        @Nullable static AntennaPoint fromJson(@NonNull String json) {
            try {
                JSONObject object = new JSONObject(json);
                return new AntennaPoint(object.getDouble("lat"), object.getDouble("lon"),
                        object.optDouble("alt", 0.0), object.optString("name", ""));
            } catch (JSONException error) {
                return null;
            }
        }
    }
}

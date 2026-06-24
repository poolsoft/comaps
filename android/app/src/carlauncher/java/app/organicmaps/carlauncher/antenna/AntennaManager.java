package app.organicmaps.carlauncher.antenna;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.Nullable;

import net.osmand.data.LatLon;
import net.osmand.util.MapUtils;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Anten noktalarini (Kaynak ve Hedef) yoneten Singleton.
 * pointA/pointB yerine pointSource/pointTarget kullanilir.
 * Geriye uyumluluk icin getPointA/getPointB wrapper'lari muhafaza edilmistir.
 */
public class AntennaManager {

    private static AntennaManager instance;
    private static final String PREFS_NAME = "antenna_prefs";
    private static final String KEY_SOURCE = "point_source"; // onceki: point_a
    private static final String KEY_TARGET = "point_target"; // onceki: point_b
    // Eski anahtar adlari â€” goc icin
    private static final String KEY_POINT_A_LEGACY = "point_a";
    private static final String KEY_POINT_B_LEGACY = "point_b";

    // Picking mode sabitleri
    public static final String PICK_SOURCE = "SOURCE";
    public static final String PICK_TARGET = "TARGET";

    private boolean layerVisible = false;
    private String pickingMode = null; // PICK_SOURCE, PICK_TARGET veya null

    private final Context context;
    private final SharedPreferences prefs;

    private AntennaPoint pointSource;
    private AntennaPoint pointTarget;
    private AntennaListener listener;

    public interface AntennaListener {
        void onAntennaPointsChanged();
    }

    private AntennaManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadPoints();
    }

    public static synchronized AntennaManager getInstance(Context context) {
        if (instance == null) {
            instance = new AntennaManager(context);
        }
        return instance;
    }

    public void setListener(AntennaListener listener) {
        this.listener = listener;
    }

    // --- Kaynak Nokta ---

    public void setSource(LatLon latLon, String name, double altitude) {
        this.pointSource = new AntennaPoint(latLon.getLatitude(), latLon.getLongitude(), altitude, name);
        savePoints();
        notifyListener();
    }

    @Nullable
    public AntennaPoint getSource() {
        return pointSource;
    }

    // --- Hedef Nokta ---

    public void setTarget(LatLon latLon, String name, double altitude) {
        this.pointTarget = new AntennaPoint(latLon.getLatitude(), latLon.getLongitude(), altitude, name);
        savePoints();
        notifyListener();
    }

    @Nullable
    public AntennaPoint getTarget() {
        return pointTarget;
    }

    // --- Geriye uyumluluk wrapper'lari ---

    /** @deprecated getSource() kullanin */
    @Deprecated
    public void setPointA(LatLon latLon, String name, double altitude) {
        setSource(latLon, name, altitude);
    }

    /** @deprecated getTarget() kullanin */
    @Deprecated
    public void setPointB(LatLon latLon, String name, double altitude) {
        setTarget(latLon, name, altitude);
    }

    /** @deprecated getSource() kullanin */
    @Deprecated
    @Nullable
    public AntennaPoint getPointA() {
        return pointSource;
    }

    /** @deprecated getTarget() kullanin */
    @Deprecated
    @Nullable
    public AntennaPoint getPointB() {
        return pointTarget;
    }

    // --- Temizle ---

    public void clearPoints() {
        this.pointSource = null;
        this.pointTarget = null;
        savePoints();
        notifyListener();
    }

    /** Sadece kaynak noktayi temizler. */
    public void clearSource() {
        this.pointSource = null;
        savePoints();
        notifyListener();
    }

    /** Sadece hedef noktayi temizler. */
    public void clearTarget() {
        this.pointTarget = null;
        savePoints();
        notifyListener();
    }

    /** Kaynak ve hedef noktalarÄ± yer degistirir. */
    public void swapPoints() {
        AntennaPoint temp = this.pointSource;
        this.pointSource = this.pointTarget;
        this.pointTarget = temp;
        savePoints();
        notifyListener();
    }

    // --- Layer / Picking ---

    public boolean isLayerVisible() {
        return layerVisible;
    }

    public void setLayerVisible(boolean visible) {
        this.layerVisible = visible;
        notifyListener();
    }

    public String getPickingMode() {
        return pickingMode;
    }

    public void setPickingMode(String mode) {
        this.pickingMode = mode;
        notifyListener();
    }

    // --- Hesaplamalar ---

    public double getDistanceMeters() {
        if (pointSource == null || pointTarget == null) return 0;
        return MapUtils.getDistance(pointSource.lat, pointSource.lon, pointTarget.lat, pointTarget.lon);
    }

    public double getAzimuthSourceToTarget() {
        if (pointSource == null || pointTarget == null) return 0;
        android.location.Location locA = new android.location.Location("");
        locA.setLatitude(pointSource.lat);
        locA.setLongitude(pointSource.lon);
        android.location.Location locB = new android.location.Location("");
        locB.setLatitude(pointTarget.lat);
        locB.setLongitude(pointTarget.lon);
        return locA.bearingTo(locB);
    }

    /** @deprecated getAzimuthSourceToTarget() kullanin */
    @Deprecated
    public double getAzimuthAtoB() {
        return getAzimuthSourceToTarget();
    }

    public double getElevationSourceToTarget() {
        if (pointSource == null || pointTarget == null) return 0;
        double dist = getDistanceMeters();
        double altDiff = pointTarget.altitude - pointSource.altitude;
        return Math.toDegrees(Math.atan2(altDiff, dist));
    }

    /** @deprecated getElevationSourceToTarget() kullanin */
    @Deprecated
    public double getElevationAtoB() {
        return getElevationSourceToTarget();
    }

    // --- Kalicilik ---

    private void savePoints() {
        SharedPreferences.Editor editor = prefs.edit();
        if (pointSource != null)
            editor.putString(KEY_SOURCE, pointSource.toJson().toString());
        else
            editor.remove(KEY_SOURCE);

        if (pointTarget != null)
            editor.putString(KEY_TARGET, pointTarget.toJson().toString());
        else
            editor.remove(KEY_TARGET);

        editor.apply();
    }

    private void loadPoints() {
        // Yeni anahtardan yukle
        String jsonSource = prefs.getString(KEY_SOURCE, null);
        // Eski kayittan goc
        if (jsonSource == null) jsonSource = prefs.getString(KEY_POINT_A_LEGACY, null);
        if (jsonSource != null) pointSource = AntennaPoint.fromJson(jsonSource);

        String jsonTarget = prefs.getString(KEY_TARGET, null);
        if (jsonTarget == null) jsonTarget = prefs.getString(KEY_POINT_B_LEGACY, null);
        if (jsonTarget != null) pointTarget = AntennaPoint.fromJson(jsonTarget);
    }

    private void notifyListener() {
        if (listener != null) {
            listener.onAntennaPointsChanged();
        }
    }

    // --- AntennaPoint sinifi ---

    public static class AntennaPoint {
        public double lat;
        public double lon;
        public double altitude;
        public String name;

        public AntennaPoint(double lat, double lon, double altitude, String name) {
            this.lat = lat;
            this.lon = lon;
            this.altitude = altitude;
            this.name = name;
        }

        public LatLon toLatLon() {
            return new LatLon(lat, lon);
        }

        public JSONObject toJson() {
            try {
                JSONObject obj = new JSONObject();
                obj.put("lat", lat);
                obj.put("lon", lon);
                obj.put("alt", altitude);
                obj.put("name", name);
                return obj;
            } catch (JSONException e) {
                return new JSONObject();
            }
        }

        public static AntennaPoint fromJson(String jsonStr) {
            try {
                JSONObject obj = new JSONObject(jsonStr);
                return new AntennaPoint(
                        obj.optDouble("lat"),
                        obj.optDouble("lon"),
                        obj.optDouble("alt", 0),
                        obj.optString("name", "Bilinmiyor"));
            } catch (JSONException e) {
                return null;
            }
        }
    }
}

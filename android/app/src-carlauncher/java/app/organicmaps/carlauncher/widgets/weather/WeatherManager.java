package app.organicmaps.carlauncher.widgets.weather;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import android.location.Location;
import app.organicmaps.sdk.location.LocationHelper;
import app.organicmaps.MwmApplication;
import app.organicmaps.carlauncher.CarLauncherSettings;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Hava Durumu Yoneticisi.
 * Open-Meteo API ile iletisim kurar ve verileri onbellege alir.
 * Singleton yapisindadir.
 */
public class WeatherManager {

    private static final String TAG = "WeatherManager";
    private static WeatherManager instance;
    private final Context context;
    private final SharedPreferences prefs;
    private final java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isFetching = false;

    // Cache Keys
    private static final String PREF_NAME = "weather_cache";
    private static final String KEY_LAST_UPDATE = "last_update_time";
    private static final String KEY_CACHED_JSON = "cached_json";
    private static final String KEY_LAST_LAT = "last_lat";
    private static final String KEY_LAST_LON = "last_lon";

    // Constants
    private static final long STALE_THRESHOLD_MS = 3 * 60 * 60 * 1000; // 3 Saat (Eski veri) - FIXED typo
    private static final long REFRESH_INTERVAL_MS = 30 * 60 * 1000; // 30 Dakika
    private static final float LOCATION_CHANGE_THRESHOLD = 20000; // 20 km

    // Data Listeners
    private final List<WeatherListener> listeners = new ArrayList<>();

    public interface WeatherListener {
        void onWeatherUpdated(WeatherData data);
        void onWeatherError(String error);
    }

    private static final String KEY_UPDATE_INTERVAL = "update_interval_minutes";

    private WeatherManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public int getUpdateIntervalMinutes() {
        return prefs.getInt(KEY_UPDATE_INTERVAL, 30);
    }

    public void setUpdateIntervalMinutes(int minutes) {
        prefs.edit().putInt(KEY_UPDATE_INTERVAL, minutes).apply();
    }

    public void forceRefresh() {
 
        // Need access to last location if possible, or just ignore if no loc.
        // Assuming we have cached lat/lon
        double lat = getDouble(prefs, KEY_LAST_LAT, 0);
        double lon = getDouble(prefs, KEY_LAST_LON, 0);
        if (lat != 0 && lon != 0) {
            fetchWeather(lat, lon);
        }
    }

    public static synchronized WeatherManager getInstance(Context context) {
        if (instance == null) {
            instance = new WeatherManager(context);
        }
        return instance;
    }

    public void addListener(WeatherListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
            // Hemen var olan veriyi gonder
            WeatherData cached = getCachedWeather();
            if (cached != null) {
                listener.onWeatherUpdated(cached);
            }
        }
    }

    public void removeListener(WeatherListener listener) {
        listeners.remove(listener);
    }

    /**
     * Konum guncellendiginde cagrilir.
     * Veri eski ise veya konum cok degistiyse yenileme yapar.
     */
    public void updateLocation(Location location) {
        if (location == null) return;

        long lastUpdate = prefs.getLong(KEY_LAST_UPDATE, 0);
        double lastLat = getDouble(prefs, KEY_LAST_LAT, 0);
        double lastLon = getDouble(prefs, KEY_LAST_LON, 0);

        float[] results = new float[1];
        android.location.Location.distanceBetween(lastLat, lastLon, location.getLatitude(), location.getLongitude(), results);
        float distance = results[0];

        boolean timeExpired = (System.currentTimeMillis() - lastUpdate) > REFRESH_INTERVAL_MS;
        boolean locationChanged = distance > LOCATION_CHANGE_THRESHOLD;

        CarLauncherSettings settings = new CarLauncherSettings(context);
        if (settings.isWeatherEnabled() && (timeExpired || locationChanged)) {
            fetchWeather(location.getLatitude(), location.getLongitude());
        }
    }

    /**
     * API'den hava durumu verisi ceker.
     * URL Guncellendi: Saatlik veriler, Ruzgar, Yagis, Gorunurluk, Hissedilen.
     */
    public void fetchWeather(double lat, double lon) {
        if (isFetching) {
            Log.d(TAG, "Hava durumu verisi zaten cekiliyor.");
            return;
        }
        if (!isNetworkAvailable()) {
            Log.d(TAG, "Internet yok, veri cekilemedi.");
            return;
        }
        isFetching = true;

        String url = String.format(Locale.US, 
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
            "&current=temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m,wind_direction_10m" +
            "&hourly=temperature_2m,weather_code,precipitation_probability,visibility" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
            "&timezone=auto", 
            lat, lon);

        executor.execute(() -> {
            String json = doFetch(url);
            mainHandler.post(() -> {
                isFetching = false;
                if (json != null) {
                    saveCache(json, lat, lon);
                    WeatherData data = parseWeather(json, System.currentTimeMillis());
                    notifyListeners(data);
                } else {
                    for (WeatherListener l : listeners) {
                        l.onWeatherError("Hava durumu verisi alinamadi");
                    }
                }
            });
        });
    }

    public WeatherData getCachedWeather() {
        String jsonStr = prefs.getString(KEY_CACHED_JSON, null);
        long lastUpdate = prefs.getLong(KEY_LAST_UPDATE, 0);

        if (jsonStr == null) return null;

        return parseWeather(jsonStr, lastUpdate);
    }

    private boolean isNetworkAvailable() {
        if (context == null) return false;
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = cm.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }

    // --- Helper Methods ---

    private void saveCache(String json, double lat, double lon) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_CACHED_JSON, json);
        editor.putLong(KEY_LAST_UPDATE, System.currentTimeMillis());
        putDouble(editor, KEY_LAST_LAT, lat);
        putDouble(editor, KEY_LAST_LON, lon);
        editor.apply();
    }

    private WeatherData parseWeather(String jsonStr, long timestamp) {
        try {
            JSONObject json = new JSONObject(jsonStr);
            WeatherData data = new WeatherData();
            
            // --- Current Units ---
            JSONObject current = json.getJSONObject("current");
            data.temp = current.getDouble("temperature_2m");
            data.weatherCode = current.getInt("weather_code");
            data.humidity = current.optInt("relative_humidity_2m", 0);
            data.apparentTemp = current.optDouble("apparent_temperature", data.temp);
            data.windSpeed = current.optDouble("wind_speed_10m", 0);
            data.windDirection = current.optInt("wind_direction_10m", 0);
            
            // --- Daily ---
            JSONObject daily = json.getJSONObject("daily");
            if (daily != null) {
                org.json.JSONArray maxTemps = daily.getJSONArray("temperature_2m_max");
                org.json.JSONArray minTemps = daily.getJSONArray("temperature_2m_min");
                org.json.JSONArray codes = daily.getJSONArray("weather_code");
                org.json.JSONArray rainProbs = daily.optJSONArray("precipitation_probability_max");
                org.json.JSONArray times = daily.getJSONArray("time");

                // Daily List
                data.dailyForecasts = new ArrayList<>();
                for (int i = 0; i < maxTemps.length(); i++) {
                    DailyForecast df = new DailyForecast();
                    df.date = times.getString(i);
                    df.maxTemp = maxTemps.getDouble(i);
                    df.minTemp = minTemps.getDouble(i);
                    df.weatherCode = codes.getInt(i);
                    if (rainProbs != null) df.rainProb = rainProbs.getInt(i);
                    data.dailyForecasts.add(df);
                }
                
                // Current Day Max/Min from Index 0
                if (!data.dailyForecasts.isEmpty()) {
                    data.maxTemp = data.dailyForecasts.get(0).maxTemp;
                    data.minTemp = data.dailyForecasts.get(0).minTemp;
                    data.rainProbToday = data.dailyForecasts.get(0).rainProb;
                }
            }
            
            // --- Hourly ---
            JSONObject hourly = json.getJSONObject("hourly");
            if (hourly != null) {
                org.json.JSONArray temps = hourly.getJSONArray("temperature_2m");
                org.json.JSONArray codes = hourly.getJSONArray("weather_code");
                org.json.JSONArray rains = hourly.optJSONArray("precipitation_probability");
                org.json.JSONArray visibilities = hourly.optJSONArray("visibility");
                org.json.JSONArray times = hourly.getJSONArray("time");

                // Get current hour index roughly (simple logic: Open-Meteo returns past hours too)
                // We'll just store all and let UI filter or take next 24
                
                data.hourlyForecasts = new ArrayList<>();
                // Limit to 24 hours from "now" logic requires parsing time string, 
                // for simplicity we take first 24 points or try to find current hour index.
                // Open-Meteo "current" block gives current time, usually aligns. 
                // We will just load first 24 entries starting from current local time index approximately
                // Ideally we should match string timestamps. For now, simple list.
                
                int count = Math.min(times.length(), 24); // Just take first 24 for safety if logic complex
                // Better: Take ALL and filter in UI based on timestamp vs current time.
                // Storing all for now.
                for (int i = 0; i < times.length(); i++) {
                     HourlyForecast hf = new HourlyForecast();
                     hf.time = times.getString(i);
                     hf.temp = temps.getDouble(i);
                     hf.weatherCode = codes.getInt(i);
                     if (rains != null) hf.rainProb = rains.getInt(i);
                     if (visibilities != null) hf.visibility = visibilities.getDouble(i);
                     data.hourlyForecasts.add(hf);
                }
            }
            
            data.timestamp = timestamp;
            data.isStale = (System.currentTimeMillis() - timestamp) > STALE_THRESHOLD_MS;
            
            return data;
        } catch (Exception e) {
            Log.e(TAG, "JSON Parse Error", e);
            return null;
        }
    }

    private void notifyListeners(WeatherData data) {
        mainHandler.post(() -> {
            for (WeatherListener l : listeners) {
                l.onWeatherUpdated(data);
            }
        });
    }

    // SharedPreferences doesn't support double directly
    private void putDouble(SharedPreferences.Editor editor, String key, double value) {
        editor.putLong(key, Double.doubleToRawLongBits(value));
    }

    private double getDouble(SharedPreferences prefs, String key, double defaultValue) {
        return Double.longBitsToDouble(prefs.getLong(key, Double.doubleToRawLongBits(defaultValue)));
    }

    // --- doFetch ---

    private String doFetch(String urlStr) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                return sb.toString();
            }
        } catch (Exception e) {
            Log.e(TAG, "Fetch Error", e);
        }
        return null;
    }

    // --- Data Model ---

    public static class WeatherData {
        public double temp;
        public double maxTemp;
        public double minTemp;
        public double apparentTemp; // Hissedilen
        public double windSpeed; // km/h
        public int windDirection;
        public int humidity; // %
        public int rainProbToday; // %
        
        public int weatherCode;
        public long timestamp;
        public boolean isStale;
        
        public List<DailyForecast> dailyForecasts;
        public List<HourlyForecast> hourlyForecasts;

        public String getIconName(int code) {
            // WMO Code Mapping
            if (code == 0) return "ic_weather_clear";
            if (code >= 1 && code <= 3) return "ic_weather_cloudy";
            if (code >= 45 && code <= 48) return "ic_weather_cloudy";
            if (code >= 51 && code <= 67) return "ic_weather_rain";
            if (code >= 71 && code <= 77) return "ic_weather_rain";
            if (code >= 80 && code <= 82) return "ic_weather_rain";
            if (code >= 95) return "ic_weather_rain";
            return "ic_weather_cloudy";
        }
        
        // Convenience for current
        public String getIconName() {
            return getIconName(weatherCode);
        }
    }
    
    public static class DailyForecast {
        public String date; // YYYY-MM-DD
        public double maxTemp;
        public double minTemp;
        public int weatherCode;
        public int rainProb;
    }
    
    public static class HourlyForecast {
        public String time; // ISO8601
        public double temp;
        public int weatherCode;
        public int rainProb;
        public double visibility; // meters
    }
}

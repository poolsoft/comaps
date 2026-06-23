package app.organicmaps.carlauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Map;

/**
 * Ayarlari ve Widget duzenini yedekler/geri yukler.
 */
public class LauncherBackupManager {

    private static final String PREF_WIDGETS = "car_launcher_widgets";

    public static void exportBackup(Context context, Uri uri) {
        try {
            JSONObject root = new JSONObject();
            root.put("timestamp", System.currentTimeMillis());

            // 1. Main Settings
            SharedPreferences mainPrefs = PreferenceManager.getDefaultSharedPreferences(context);
            JSONObject mainSettingsJson = new JSONObject();
            for (Map.Entry<String, ?> entry : mainPrefs.getAll().entrySet()) {
                mainSettingsJson.put(entry.getKey(), entry.getValue());
            }
            root.put("main_settings", mainSettingsJson);

            // 2. Widget Settings
            SharedPreferences widgetPrefs = context.getSharedPreferences(PREF_WIDGETS, Context.MODE_PRIVATE);
            JSONObject widgetSettingsJson = new JSONObject();
            for (Map.Entry<String, ?> entry : widgetPrefs.getAll().entrySet()) {
                widgetSettingsJson.put(entry.getKey(), entry.getValue());
            }
            root.put("widget_settings", widgetSettingsJson);

            // Write to File
            try (OutputStream os = context.getContentResolver().openOutputStream(uri)) {
                if (os != null) {
                    os.write(root.toString(2).getBytes());
                }
            }

            Toast.makeText(context, "Yedekleme Başarılı", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(context, "Yedekleme Hatası: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    public static void restoreBackup(Context context, Uri uri) {
        try {
            // Read JSON
            StringBuilder sb = new StringBuilder();
            try (InputStream is = context.getContentResolver().openInputStream(uri);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }

            JSONObject root = new JSONObject(sb.toString());

            // 1. Restore Main Settings
            if (root.has("main_settings")) {
                SharedPreferences mainPrefs = PreferenceManager.getDefaultSharedPreferences(context);
                SharedPreferences.Editor editor = mainPrefs.edit();
                // Clear old or just overwrite? Overwrite is safer, but clear ensures clean state.
                // Let's clear relevant keys maybe? No, clear all might wipe other osmand settings.
                // Be careful. Only Car Launcher keys usually start with "car_launcher" or "autolaunch".
                // But user wants full restore.
                // Safest: Iterate JSON and put.
                
                JSONObject mainJson = root.getJSONObject("main_settings");
                putAll(editor, mainJson);
                editor.apply();
            }

            // 2. Restore Widget Settings
            if (root.has("widget_settings")) {
                SharedPreferences widgetPrefs = context.getSharedPreferences(PREF_WIDGETS, Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = widgetPrefs.edit();
                editor.clear(); // Widgets should be fully replaced
                
                JSONObject widgetJson = root.getJSONObject("widget_settings");
                putAll(editor, widgetJson);
                editor.apply();
            }
            
            // Force Widget Manager Reset to reload config
            app.organicmaps.carlauncher.widgets.WidgetManager.getInstance(context).forceResetForNewSession();

            Toast.makeText(context, "Geri Yükleme Başarılı. Lütfen uygulamayı yeniden başlatın.", Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(context, "Geri Yükleme Hatası: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    // Helper to put generic values from JSON to Editor
    private static void putAll(SharedPreferences.Editor editor, JSONObject json) throws Exception {
        java.util.Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = json.get(key);
            
            if (value instanceof Boolean) {
                editor.putBoolean(key, (Boolean) value);
            } else if (value instanceof Float) {
                // JSON double, Pref float
                 editor.putFloat(key, ((Double) value).floatValue());
            } else if (value instanceof Integer) {
                editor.putInt(key, (Integer) value);
            } else if (value instanceof Long) {
                editor.putLong(key, (Long) value);
            } else if (value instanceof String) {
                editor.putString(key, (String) value);
            }
        }
    }
}

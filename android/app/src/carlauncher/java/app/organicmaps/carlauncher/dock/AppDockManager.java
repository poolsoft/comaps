package app.organicmaps.carlauncher.dock;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * App Dock yoneticisi.
 * Uygulama kisayollarini yonetir, kaydet/yukle.
 */
public class AppDockManager {

    private static final String PREFS_NAME = "car_launcher_app_dock";
    private static final String KEY_SHORTCUTS = "shortcuts";
    private static final int MAX_SHORTCUTS = 8;

    private final Context context;
    private final SharedPreferences prefs;
    private final List<AppShortcut> shortcuts;

    public AppDockManager(@NonNull Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.shortcuts = new ArrayList<>();
    }

    /**
     * Kisayollari yukle.
     */
    public void loadShortcuts() {
        shortcuts.clear();

        String shortcutsJson = prefs.getString(KEY_SHORTCUTS, null);

        if (shortcutsJson != null) {
            // Kaydedilmis kisayollari yukle
            loadFromJson(shortcutsJson);
        } else {
            // Varsayilan kisayollari yukle
            loadDefaultShortcuts();
        }
    }

    /**
     * JSON'dan yukle.
     */
    private void loadFromJson(String json) {
        try {
            JSONArray array = new JSONArray(json);
            PackageManager pm = context.getPackageManager();

            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                String packageName = obj.getString("package");
                int order = obj.getInt("order");
                LaunchMode launchMode = LaunchMode.FULL_SCREEN;

                if (obj.has("launchMode")) {
                    try {
                        launchMode = LaunchMode.valueOf(obj.getString("launchMode"));
                    } catch (IllegalArgumentException e) {
                        launchMode = LaunchMode.FULL_SCREEN;
                    }
                }

                try {
                    String appName;
                    Drawable icon;

                    if (InternalApp.isInternalApp(packageName)) {
                        InternalApp app = InternalApp.fromPackageName(packageName);
                        if (app != null) {
                            appName = app.getDefaultName();
                            icon = app.getIcon(context);
                        } else {
                            appName = "Bilinmeyen";
                            icon = context.getResources().getDrawable(android.R.drawable.sym_def_app_icon, null);
                        }
                    } else {
                        // Handle Normal Apps
                        ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
                        appName = appInfo.loadLabel(pm).toString();
                        icon = appInfo.loadIcon(pm);
                    }

                    shortcuts.add(new AppShortcut(packageName, appName, icon, order, launchMode));
                } catch (PackageManager.NameNotFoundException e) {
                    // Uygulama yuklu degil
                }
            }

            sortShortcuts();
        } catch (JSONException e) {
            e.printStackTrace();
            loadDefaultShortcuts();
        }
    }

    /**
     * Varsayilan kisayollari yukle.
     */
    private void loadDefaultShortcuts() {
        PackageManager pm = context.getPackageManager();

        String[] defaultApps = {
                "internal://neon_dashboard",
                "internal://dashboard",
                "com.spotify.music",
                "com.google.android.dialer",
                "com.android.messaging",
                "com.android.settings",
                "app.organicmaps"
        };

        int order = 0;
        for (String packageName : defaultApps) {
            try {
                String appName;
                Drawable icon;
                if (InternalApp.isInternalApp(packageName)) {
                    InternalApp app = InternalApp.fromPackageName(packageName);
                    if (app != null) {
                        appName = app.getDefaultName();
                        icon = app.getIcon(context);
                    } else {
                        continue;
                    }
                } else {
                    ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
                    appName = appInfo.loadLabel(pm).toString();
                    icon = appInfo.loadIcon(pm);
                }

                shortcuts.add(new AppShortcut(packageName, appName, icon, order++, LaunchMode.FULL_SCREEN));
            } catch (PackageManager.NameNotFoundException e) {
                // Uygulama yuklu degil
            }
        }
    }

    /**
     * Kisayol sirasini degistir.
     */
    public void moveShortcut(int fromPosition, int toPosition) {
        if (fromPosition < 0 || fromPosition >= shortcuts.size() ||
                toPosition < 0 || toPosition >= shortcuts.size()) {
            return;
        }

        AppShortcut s = shortcuts.remove(fromPosition);
        shortcuts.add(toPosition, s);

        // Update order indices
        for (int i = 0; i < shortcuts.size(); i++) {
            shortcuts.get(i).setOrder(i);
        }

        saveShortcuts();
    }

    /**
     * Kisayollari kaydet.
     */
    public void saveShortcuts() {
        try {
            JSONArray array = new JSONArray();

            for (AppShortcut shortcut : shortcuts) {
                JSONObject obj = new JSONObject();
                obj.put("package", shortcut.getPackageName());
                obj.put("order", shortcut.getOrder());
                obj.put("launchMode", shortcut.getLaunchMode().name());
                array.put(obj);
            }

            prefs.edit()
                    .putString(KEY_SHORTCUTS, array.toString())
                    .apply();

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * Kisayol ekle.
     */
    public boolean addShortcut(@NonNull AppShortcut shortcut) {
        if (shortcuts.size() >= MAX_SHORTCUTS) {
            return false;
        }

        // Ayni uygulama zaten var mi?
        for (AppShortcut existing : shortcuts) {
            if (existing.getPackageName().equals(shortcut.getPackageName())) {
                return false;
            }
        }

        shortcuts.add(shortcut);
        sortShortcuts();
        saveShortcuts();
        return true;
    }

    /**
     * Kisayol cikar.
     */
    public void removeShortcut(@NonNull AppShortcut shortcut) {
        shortcuts.remove(shortcut);

        // Order'lari yeniden duzenle
        for (int i = 0; i < shortcuts.size(); i++) {
            shortcuts.get(i).setOrder(i);
        }

        saveShortcuts();
    }

    /**
     * Kisayollari sirala.
     */
    private void sortShortcuts() {
        Collections.sort(shortcuts, new Comparator<AppShortcut>() {
            @Override
            public int compare(AppShortcut s1, AppShortcut s2) {
                return Integer.compare(s1.getOrder(), s2.getOrder());
            }
        });
    }

    /**
     * Tum kisayollari al.
     */
    public List<AppShortcut> getShortcuts() {
        return new ArrayList<>(shortcuts);
    }

    /**
     * Maksimum kisayol sayisi.
     */
    public int getMaxShortcuts() {
        return MAX_SHORTCUTS;
    }

    /**
     * Daha kisayol eklenebilir mi?
     */
    public boolean canAddMore() {
        return shortcuts.size() < MAX_SHORTCUTS;
    }

    /**
     * Tum kisayollari temizle (sifirla).
     */
    public void clearAllShortcuts() {
        shortcuts.clear();
        prefs.edit().remove(KEY_SHORTCUTS).apply();
    }
}

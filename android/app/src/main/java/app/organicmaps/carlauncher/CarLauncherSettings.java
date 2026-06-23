package app.organicmaps.carlauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Car Launcher ayarlarini yoneten sinif.
 * SharedPreferences wrapper.
 */
public class CarLauncherSettings {
    private static final String PREF_NAME = "car_launcher_prefs";

    // Widget Keys
    private static final String KEY_WIDGET_ORDER = "widget_order";
    private static final String KEY_WIDGET_VISIBILITY_PREFIX = "widget_visible_";
    public static final String KEY_WIDGET_DISPLAY_MODE = "widget_display_mode"; // 0: List, 1: Paged

    // Grid Slot Keys (NEW) - v2 to force reset defaults and support separate modes
    public static final String KEY_WIDGET_SLOTS_PORTRAIT = "widget_slot_count_portrait_v2";
    public static final String KEY_WIDGET_SLOTS_LANDSCAPE = "widget_slot_count_landscape_v2";
    
    // Panel Resize Keys
    public static final String KEY_WIDGET_PANEL_WIDTH_PERCENT = "widget_panel_width_percent";
    public static final String KEY_WIDGET_PANEL_HEIGHT_PORTRAIT = "widget_panel_height_portrait";
    
    // Layout Mode (Classic/Metro)
    public static final String KEY_WIDGET_LAYOUT_MODE = "widget_layout_mode_v2";

    // Appearance Keys
    public static final String KEY_STATUS_BAR = "car_launcher_status_bar";
    public static final String KEY_DARK_THEME = "car_launcher_dark_theme";
    public static final String KEY_PORTRAIT_MAP_ONLY = "car_launcher_portrait_map_only";
    public static final String KEY_FLOATING_BUTTON = "car_launcher_floating_button";
    public static final String KEY_NIGHT_DIM_MODE = "car_launcher_night_dim_mode";
    public static final String KEY_PARALLAX_INTENSITY = "car_launcher_parallax_intensity";
    public static final String KEY_BACKGROUND_STYLE = "car_launcher_background_style";

    // Music Keys
    public static final String KEY_MUSIC_APP = "car_launcher_music_app";
    public static final String KEY_AMBIANCE_VISUALIZER = "car_launcher_ambiance_visualizer";

    // Dock Keys
    public static final String KEY_MAX_SHORTCUTS = "car_launcher_max_shortcuts";
    public static final String KEY_DOCK_POSITION = "car_launcher_dock_position"; // "bottom", "left", "right"
    public static final String KEY_DOCK_STYLE = "car_launcher_dock_style"; // "glass", "solid", "transparent"
    public static final String KEY_DOCK_SIZE = "car_launcher_dock_size"; // 0-100 arasi boyut yuzdesi

    // Widget Panel Keys
    public static final String KEY_WIDGET_PANEL_POSITION = "widget_panel_position"; // "right", "bottom", "left"
    public static final String KEY_WIDGET_CARD_STYLE = "widget_card_style"; // "modern", "classic", "minimal"
    public static final String KEY_EXPANSION_BEHAVIOR = "car_launcher_expansion_behavior";

    private final SharedPreferences prefs;

    public CarLauncherSettings(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // --- Widget Settings ---

    public List<String> getWidgetOrder(List<String> defaultOrder) {
        String savedOrder = prefs.getString(KEY_WIDGET_ORDER, null);
        if (savedOrder == null) {
            return defaultOrder;
        }
        return new ArrayList<>(Arrays.asList(TextUtils.split(savedOrder, ",")));
    }

    public void setWidgetOrder(List<String> order) {
        prefs.edit().putString(KEY_WIDGET_ORDER, TextUtils.join(",", order)).apply();
    }

    public boolean isWidgetVisible(String widgetKey, boolean defaultValue) {
        return prefs.getBoolean(KEY_WIDGET_VISIBILITY_PREFIX + widgetKey, defaultValue);
    }

    public void setWidgetVisible(String widgetKey, boolean visible) {
        prefs.edit().putBoolean(KEY_WIDGET_VISIBILITY_PREFIX + widgetKey, visible).apply();
    }

    public int getWidgetDisplayMode() {
        try {
            String val = prefs.getString(KEY_WIDGET_DISPLAY_MODE, "0");
            return Integer.parseInt(val);
        } catch (Exception e) {
            return 0; // Default to List
        }
    }

    public void setWidgetDisplayMode(int mode) {
        prefs.edit().putString(KEY_WIDGET_DISPLAY_MODE, String.valueOf(mode)).apply();
    }

    // --- Grid Slot Settings ---
    public int getPortraitSlotCount() {
        return prefs.getInt(KEY_WIDGET_SLOTS_PORTRAIT, 3); // Default 3 (Standard Density)
    }

    public void setPortraitSlotCount(int count) {
        prefs.edit().putInt(KEY_WIDGET_SLOTS_PORTRAIT, count).apply();
    }
    
    public int getLandscapeSlotCount() {
        return prefs.getInt(KEY_WIDGET_SLOTS_LANDSCAPE, 3); // Default 3
    }
    
    public void setLandscapeSlotCount(int count) {
        prefs.edit().putInt(KEY_WIDGET_SLOTS_LANDSCAPE, count).apply();
    }
    
    public float getWidgetPanelWidthPercent() {
        return prefs.getFloat(KEY_WIDGET_PANEL_WIDTH_PERCENT, 0.4f); // Default 40%
    }
    
    public void setWidgetPanelWidthPercent(float percent) {
        prefs.edit().putFloat(KEY_WIDGET_PANEL_WIDTH_PERCENT, percent).apply();
    }

    // --- Portrait Panel Height (0.1 - 0.7) ---
    public float getWidgetPanelHeightPortrait() {
        return prefs.getFloat(KEY_WIDGET_PANEL_HEIGHT_PORTRAIT, 0.30f);
    }

    public void setWidgetPanelHeightPortrait(float percent) {
        prefs.edit().putFloat(KEY_WIDGET_PANEL_HEIGHT_PORTRAIT, Math.max(0.1f, Math.min(0.7f, percent))).apply();
    }

    public static final String KEY_WIDGET_HANDLE_VERTICAL_BIAS = "widget_handle_vertical_bias";

    public float getWidgetHandleVerticalBias() {
        return prefs.getFloat(KEY_WIDGET_HANDLE_VERTICAL_BIAS, 0.5f);
    }
    
    public void setWidgetHandleVerticalBias(float bias) {
        prefs.edit().putFloat(KEY_WIDGET_HANDLE_VERTICAL_BIAS, bias).apply();
    }
    
    public boolean isMetroMode() {
        return "metro".equals(prefs.getString(KEY_WIDGET_LAYOUT_MODE, "classic"));
    }
    
    public void setMetroMode(boolean isMetro) {
        prefs.edit().putString(KEY_WIDGET_LAYOUT_MODE, isMetro ? "metro" : "classic").apply();
    }

    // --- Appearance Settings ---

    public boolean isStatusBarVisible() {
        return prefs.getBoolean(KEY_STATUS_BAR, true);
    }

    public void setStatusBarVisible(boolean visible) {
        prefs.edit().putBoolean(KEY_STATUS_BAR, visible).apply();
    }

    public boolean isDarkTheme() {
        return prefs.getBoolean(KEY_DARK_THEME, true);
    }

    public void setDarkTheme(boolean dark) {
        prefs.edit().putBoolean(KEY_DARK_THEME, dark).apply();
    }

    public boolean isPortraitMapOnly() {
        return prefs.getBoolean(KEY_PORTRAIT_MAP_ONLY, false);
    }

    public void setPortraitMapOnly(boolean only) {
        prefs.edit().putBoolean(KEY_PORTRAIT_MAP_ONLY, only).apply();
    }

    public boolean isFloatingButtonEnabled() {
        return prefs.getBoolean(KEY_FLOATING_BUTTON, false);
    }

    public void setFloatingButtonEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_FLOATING_BUTTON, enabled).apply();
    }

    public static final String KEY_FLOATING_BUTTON_FORCE_GPS = "car_launcher_floating_button_force_gps";

    public boolean isFloatingButtonForceGpsEnabled() {
        return prefs.getBoolean(KEY_FLOATING_BUTTON_FORCE_GPS, false);
    }

    public void setFloatingButtonForceGpsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_FLOATING_BUTTON_FORCE_GPS, enabled).apply();
    }

    public static final String KEY_FLOATING_BUTTON_SIZE = "car_launcher_floating_button_size";

    public int getFloatingButtonSize() {
        return prefs.getInt(KEY_FLOATING_BUTTON_SIZE, 86);
    }

    public void setFloatingButtonSize(int size) {
        prefs.edit().putInt(KEY_FLOATING_BUTTON_SIZE, Math.max(50, Math.min(140, size))).apply();
    }

    // --- Music Settings ---

    public String getMusicApp() {
        return prefs.getString(KEY_MUSIC_APP, "internal");
    }

    public void setMusicApp(String packageName) {
        prefs.edit().putString(KEY_MUSIC_APP, packageName).apply();
    }
    
    private static final String KEY_AUTO_PLAY_MUSIC = "car_launcher_auto_play_music";
    
    public boolean isAutoPlayMusicEnabled() {
        return prefs.getBoolean(KEY_AUTO_PLAY_MUSIC, false);
    }
    
    public void setAutoPlayMusicEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_PLAY_MUSIC, enabled).apply();
    }

    public boolean isAmbianceVisualizerEnabled() {
        return prefs.getBoolean(KEY_AMBIANCE_VISUALIZER, true);
    }

    public void setAmbianceVisualizerEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AMBIANCE_VISUALIZER, enabled).apply();
    }
    
    // --- Weather Settings ---
    private static final String KEY_WEATHER_ENABLED = "car_launcher_weather_enabled";
    
    public boolean isWeatherEnabled() {
        return prefs.getBoolean(KEY_WEATHER_ENABLED, true);
    }
    
    public void setWeatherEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_WEATHER_ENABLED, enabled).apply();
    }

    // --- Auto Launch Settings ---
    public static final String KEY_AUTOLAUNCH_ENABLE_PREFIX = "autolaunch_enable_";
    public static final String KEY_AUTOLAUNCH_PKG_PREFIX = "autolaunch_pkg_";
    public static final String KEY_AUTOLAUNCH_NAME_PREFIX = "autolaunch_name_";

    public boolean isAutoLaunchEnabled(int slot) {
        return prefs.getBoolean(KEY_AUTOLAUNCH_ENABLE_PREFIX + slot, false);
    }
    
    public void setAutoLaunchEnabled(int slot, boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTOLAUNCH_ENABLE_PREFIX + slot, enabled).apply();
    }

    public String getAutoLaunchPackage(int slot) {
        return prefs.getString(KEY_AUTOLAUNCH_PKG_PREFIX + slot, null);
    }
    
    public String getAutoLaunchAppName(int slot) {
        return prefs.getString(KEY_AUTOLAUNCH_NAME_PREFIX + slot, "Seçilmedi");
    }

    public void setAutoLaunchApp(int slot, String pkg, String name) {
        prefs.edit()
             .putString(KEY_AUTOLAUNCH_PKG_PREFIX + slot, pkg)
             .putString(KEY_AUTOLAUNCH_NAME_PREFIX + slot, name)
             .apply();
    }

    // --- Dock Settings ---

    public int getMaxShortcuts() {
        return prefs.getInt(KEY_MAX_SHORTCUTS, 6);
    }

    public void setMaxShortcuts(int max) {
        prefs.edit().putInt(KEY_MAX_SHORTCUTS, max).apply();
    }

    public void resetDock() {
        // Reset is handled by AppDockManager.clearAllShortcuts()
    }

    public String getDockPosition() {
        return prefs.getString(KEY_DOCK_POSITION, "bottom");
    }

    public void setDockPosition(String pos) {
        prefs.edit().putString(KEY_DOCK_POSITION, pos).apply();
    }

    public String getDockStyle() {
        return prefs.getString(KEY_DOCK_STYLE, "glass");
    }

    public void setDockStyle(String style) {
        prefs.edit().putString(KEY_DOCK_STYLE, style).apply();
    }

    public String getWidgetPanelPosition() {
        return prefs.getString(KEY_WIDGET_PANEL_POSITION, "right");
    }

    public void setWidgetPanelPosition(String pos) {
        prefs.edit().putString(KEY_WIDGET_PANEL_POSITION, pos).apply();
    }

    public String getExpansionBehavior() {
        return prefs.getString(KEY_EXPANSION_BEHAVIOR, "swap");
    }

    public void setExpansionBehavior(String behavior) {
        prefs.edit().putString(KEY_EXPANSION_BEHAVIOR, behavior).apply();
    }

    public String getWidgetCardStyle() {
        return prefs.getString(KEY_WIDGET_CARD_STYLE, "modern");
    }

    public void setWidgetCardStyle(String style) {
        prefs.edit().putString(KEY_WIDGET_CARD_STYLE, style).apply();
    }

    // --- Dock Size (intensity 0-100, 50=varsayilan) ---
    public int getDockSize() {
        return prefs.getInt(KEY_DOCK_SIZE, 50); // %50 varsayilan (normal)
    }

    public void setDockSize(int sizePercent) {
        prefs.edit().putInt(KEY_DOCK_SIZE, Math.max(0, Math.min(100, sizePercent))).apply();
    }

    // --- General ---

    public boolean isLauncherEnabled() {
        return prefs.getBoolean("car_launcher_enabled", true);
    }

    public void setLauncherEnabled(boolean enabled) {
        prefs.edit().putBoolean("car_launcher_enabled", enabled).apply();
    }

    // --- Utility ---

    // --- Generic Widget Config ---
    private static final String KEY_WIDGET_CONFIG_PREFIX = "widget_config_";

    public String getWidgetConfig(String widgetId) {
        return prefs.getString(KEY_WIDGET_CONFIG_PREFIX + widgetId, null);
    }

    public void setWidgetConfig(String widgetId, String config) {
        prefs.edit().putString(KEY_WIDGET_CONFIG_PREFIX + widgetId, config).apply();
    }

    public String getNightDimMode() {
        return prefs.getString(KEY_NIGHT_DIM_MODE, "osmand");
    }

    public void setNightDimMode(String mode) {
        prefs.edit().putString(KEY_NIGHT_DIM_MODE, mode).apply();
    }

    // --- Premium Appearance Settings ---
    public int getParallaxIntensity() {
        return prefs.getInt(KEY_PARALLAX_INTENSITY, 20);
    }

    public void setParallaxIntensity(int intensity) {
        prefs.edit().putInt(KEY_PARALLAX_INTENSITY, intensity).apply();
    }

    public String getBackgroundStyle() {
        return prefs.getString(KEY_BACKGROUND_STYLE, "modern");
    }

    public void setBackgroundStyle(String style) {
        prefs.edit().putString(KEY_BACKGROUND_STYLE, style).apply();
    }

    public static final String KEY_PIP_MODE = "car_launcher_pip_mode";

    public boolean isPipModeEnabled() {
        return prefs.getBoolean(KEY_PIP_MODE, true); // Varsayilan olarak aktif (true)
    }

    public void setPipModeEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_PIP_MODE, enabled).apply();
    }

    public SharedPreferences getPrefs() {
        return prefs;
    }
}

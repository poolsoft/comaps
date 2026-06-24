package app.organicmaps.carlauncher.dock;

import android.content.Context;
import android.graphics.drawable.Drawable;

import app.organicmaps.R;

/**
 * Tum dahili uygulamalarin merkezi listesi ve ozellikleri.
 */
public enum InternalApp {
    SETTINGS("internal://settings", "Ayarlar", android.R.drawable.ic_menu_preferences),
    MUSIC("internal://music", "Muzik", android.R.drawable.ic_media_play),
    ANTENNA("internal://antenna", "Anten", R.drawable.ic_action_compass),
    DASHBOARD("internal://dashboard", "Dashboard", android.R.drawable.ic_menu_compass),
    NEON_DASHBOARD("internal://neon_dashboard", "Dijital Gosterge", android.R.drawable.ic_menu_view);

    private final String packageName;
    private final String defaultName;
    private final int defaultIconRes;

    InternalApp(String packageName, String defaultName, int defaultIconRes) {
        this.packageName = packageName;
        this.defaultName = defaultName;
        this.defaultIconRes = defaultIconRes;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getDefaultName() {
        return defaultName;
    }

    public Drawable getIcon(Context context) {
        return context.getResources().getDrawable(defaultIconRes, null);
    }

    public static boolean isInternalApp(String packageName) {
        return packageName != null && packageName.startsWith("internal://");
    }

    public static InternalApp fromPackageName(String packageName) {
        if (packageName == null) return null;
        for (InternalApp app : values()) {
            if (app.packageName.equals(packageName)) {
                return app;
            }
        }
        return null;
    }
}

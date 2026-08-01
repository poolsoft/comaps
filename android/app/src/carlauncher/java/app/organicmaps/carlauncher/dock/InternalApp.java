package app.organicmaps.carlauncher.dock;

import android.content.Context;
import android.graphics.drawable.Drawable;

import app.organicmaps.R;

/**
 * Tum dahili uygulamalarin merkezi listesi ve ozellikleri.
 */
public enum InternalApp {
    SETTINGS("internal://settings", "Ayarlar", R.drawable.ic_internal_settings),
    MUSIC("internal://music", "Muzik", R.drawable.ic_internal_music),
    ANTENNA("internal://antenna", "Anten", R.drawable.ic_internal_antenna),
    DASHBOARD("internal://dashboard", "Dashboard", R.drawable.ic_internal_dashboard),
    NEON_DASHBOARD("internal://neon_dashboard", "Dijital Gosterge", R.drawable.ic_internal_neon_dashboard);

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

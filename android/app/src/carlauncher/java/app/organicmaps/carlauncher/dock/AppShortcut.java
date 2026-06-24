package app.organicmaps.carlauncher.dock;

import android.graphics.drawable.Drawable;

/**
 * Uygulama kisayolu model.
 * Dock'ta gosterilecek uygulama bilgisi.
 */
public class AppShortcut {

    private String packageName;
    private String appName;
    private Drawable icon;
    private int order;
    private LaunchMode launchMode;

    public AppShortcut(String packageName, String appName, Drawable icon, int order) {
        this(packageName, appName, icon, order, LaunchMode.FULL_SCREEN);
    }

    public AppShortcut(String packageName, String appName, Drawable icon, int order, LaunchMode launchMode) {
        this.packageName = packageName;
        this.appName = appName;
        this.icon = icon;
        this.order = order;
        this.launchMode = launchMode;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public Drawable getIcon() {
        return icon;
    }

    public void setIcon(Drawable icon) {
        this.icon = icon;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public LaunchMode getLaunchMode() {
        return launchMode;
    }

    public void setLaunchMode(LaunchMode launchMode) {
        this.launchMode = launchMode;
    }
}

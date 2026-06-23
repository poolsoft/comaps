package app.organicmaps.carlauncher.dock;

/**
 * Uygulama acilis modu.
 */
public enum LaunchMode {
    FULL_SCREEN("Tam Ekran"), // Normal acilis
    OVERLAY("Overlay"), // Floating window
    SPLIT_SCREEN("Split-Screen"), // Yan yana
    WIDGET_ONLY("Sadece Widget"); // Acma, sadece widget panelde goster

    private final String displayName;

    LaunchMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

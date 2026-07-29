package app.organicmaps.carlauncher.ui;

/**
 * CarLauncher widget paneli icin hazir duzen profilleri.
 * Kodlarda ve yorumlarda kesinlikle Turkce karakter kullanilmamistir.
 */
public enum LayoutPreset {
    NAVIGATION("Navigasyon"),
    MEDIA("Medya"),
    MINIMALIST("Minimalist"),
    USER("Kullanici");

    private final String title;

    LayoutPreset(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }
}

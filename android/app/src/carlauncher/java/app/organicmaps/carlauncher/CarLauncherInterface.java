package app.organicmaps.carlauncher;

import app.organicmaps.carlauncher.ui.PanelContentManager;


/**
 * Interface to expose CarLauncher specific methods from MapActivity.
 */
public interface CarLauncherInterface {
    void openAppDrawer();

    void closeAppDrawer();

    void openCarLauncherSettings();

    void openMusicPlayer();
    
    void openWeatherDashboard();

    void openAntennaAlignmentInPanel();
    
    void openAntennaAlignmentFullscreen();

    /**
     * Sağ panel içeriğini değiştirir (Android Auto UI).
     * @param content PanelContent enum değeri
     */
    void setPanelContent(PanelContentManager.PanelContent content);

    Object getMapView();

    PanelContentManager getPanelContentManager();

    void onLayoutModeToggle();

    void onDesktopModeToggle();

    boolean isDesktopMode();

    int getLayoutMode();

    boolean isWidgetPanelOpen();

    void applyNightDimMode();

    void applyStatusBarVisibility();

    void checkAndRefreshDockFragmentIfNeeded();
}

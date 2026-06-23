package app.organicmaps.carlauncher;

import app.organicmaps.carlauncher.ui.PanelContentManager;


/**
 * Interface to expose CarLauncher specific methods from MwmActivity.
 */
public interface CarLauncherInterface {
    void openAppDrawer();

    void closeAppDrawer();

    void openMusicPlayer();
    
    void openWeatherDashboard();

    void openAntennaAlignmentInPanel();
    
    void openAntennaAlignmentFullscreen();

    /**
     * Sağ panel içeriğini değiştirir (Android Auto UI).
     * @param content PanelContent enum değeri
     */
    void setPanelContent(PanelContentManager.PanelContent content);


}

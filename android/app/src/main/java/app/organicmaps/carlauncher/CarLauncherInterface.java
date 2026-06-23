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

    

    /**
     * SaÃ„Å¸ panel iÃƒÂ§eriÃ„Å¸ini deÃ„Å¸iÃ…Å¸tirir (Android Auto UI).
     * @param content PanelContent enum deÃ„Å¸eri
     */
    void setPanelContent(PanelContentManager.PanelContent content);


}

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
     * SaÄŸ panel iÃ§eriÄŸini deÄŸiÅŸtirir (Android Auto UI).
     * @param content PanelContent enum deÄŸeri
     */
    void setPanelContent(PanelContentManager.PanelContent content);


}

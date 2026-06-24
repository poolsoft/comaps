package app.organicmaps.carlauncher;

import app.organicmaps.carlauncher.ui.PanelContentManager;
import app.organicmaps.views.OsmandMapTileView;

/**
 * Interface to expose CarLauncher specific methods from MapActivity.
 */
public interface CarLauncherInterface {
    void openAppDrawer();

    void closeAppDrawer();

    void openMusicPlayer();
    
    void openWeatherDashboard();

    void openAntennaAlignmentInPanel();
    
    void openAntennaAlignmentFullscreen();

    /**
     * SaÄŸ panel iÃ§eriÄŸini deÄŸiÅŸtirir (Android Auto UI).
     * @param content PanelContent enum deÄŸeri
     */
    void setPanelContent(PanelContentManager.PanelContent content);

    OsmandMapTileView getMapView();
}

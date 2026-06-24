package app.organicmaps.carlauncher;

import android.os.Bundle;
import androidx.annotation.Nullable;
import app.organicmaps.MwmActivity;
import app.organicmaps.R;
import app.organicmaps.carlauncher.ui.PanelContentManager;

public class CarLauncherActivity extends MwmActivity implements CarLauncherInterface {
    
    private PanelContentManager panelContentManager;

    @Override
    protected void onSafeCreate(@Nullable Bundle savedInstanceState) {
        // MwmActivity onSafeCreate setContentView(R.layout.activity_map) cagirir.
        super.onSafeCreate(savedInstanceState);
        
        // MwmActivity layout'u uzerinde CarLauncher icin gerekli panel container'ini
        // olusturabilir veya varolan bir container id'sini kullanabiliriz.
        // Ornegin, MwmActivity'nin layout'unda R.id.fragment_container_view vs kullanabiliriz.
        // Sadece ornekleme amaciyla PanelContentManager'i baslatiyoruz:
        // panelContentManager = new PanelContentManager(getSupportFragmentManager(), R.id.map_fragment_container);
        // NOT: Gercek containerId projeye gore ayarlanmalidir.
    }

    @Override
    public void openAppDrawer() {
        if (panelContentManager != null) {
            panelContentManager.setContent(PanelContentManager.PanelContent.APP_DRAWER);
        }
    }

    @Override
    public void closeAppDrawer() {
        if (panelContentManager != null) {
            panelContentManager.setContent(PanelContentManager.PanelContent.WIDGETS);
        }
    }

    @Override
    public void openMusicPlayer() {
        if (panelContentManager != null) {
            panelContentManager.setContent(PanelContentManager.PanelContent.MUSIC);
        }
    }

    @Override
    public void openWeatherDashboard() {
        if (panelContentManager != null) {
            panelContentManager.setContent(PanelContentManager.PanelContent.WEATHER);
        }
    }

    @Override
    public void setPanelContent(PanelContentManager.PanelContent content) {
        if (panelContentManager != null) {
            panelContentManager.setContent(content);
        }
    }

    @Override
    public void onLayoutModeToggle() {
        // Layout mode toggle implementasyonu
    }

    @Override
    public void onDesktopModeToggle() {
        if (panelContentManager != null) {
            panelContentManager.setContent(PanelContentManager.PanelContent.DESKTOP);
        }
    }

    @Override
    public void openCarLauncherSettings() {
        if (panelContentManager != null) {
            panelContentManager.setContent(PanelContentManager.PanelContent.SETTINGS);
        }
    }

    @Override
    public void checkAndRefreshDockFragmentIfNeeded() {
        // Dock fragment guncellemesi
    }
}

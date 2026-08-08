package app.organicmaps.carlauncher.ui;

import android.content.Context;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import app.organicmaps.R;
import app.organicmaps.carlauncher.CarLauncherSettings;

/**
 * SaÃ„S¸ panel iÃƒÂ§eriÃ„S¸ini yÃƒÂ¶neten sÃ„Â±nÃ„Â±f.
 * Android Auto UI mantÃ„Â±Ã„S¸Ã„Â±: panel mÃƒÂ¼zik/bildirim/app drawer gÃƒÂ¶sterir.
 */
public class PanelContentManager {

    public enum PanelContent {
        WIDGETS,      // Varsayilan: widget listesi
        MUSIC,        // Muzik player
        NOTIFICATION, // Bildirim + muzik (ust uste)
        APP_DRAWER,   // App drawer listesi
        WEATHER,      // Hava durumu
        SETTINGS,     // Ayarlar fragmenti
        DESKTOP,      // Masaustu Modu (WidgetPanelFragment)
        ANTENNA,      // Anten Hizalama
        DASHBOARD     // Arac Telemetri & Spor Kadran
    }

    private PanelContent currentContent = null;
    private final FragmentManager fragmentManager;
    private final int containerId;

    public PanelContentManager(FragmentManager fragmentManager, int containerId) {
        this.fragmentManager = fragmentManager;
        this.containerId = containerId;
    }

    public interface OnFullScreenStateChangeListener {
        void onFullScreenStateChanged(boolean isFullScreen);
    }

    private OnFullScreenStateChangeListener fullScreenListener;

    public void setOnFullScreenStateChangeListener(OnFullScreenStateChangeListener listener) {
        this.fullScreenListener = listener;
    }

    /**
     * Panel icerigini degistirir.
     * Her degisimde eski fragment remove edilir, yenisi eklenir.
     * APP_DRAWER/MUSIC icin fullscreen durumu otomatik senkronize edilir.
     */
    public void setContent(PanelContent content) {
        // Her zaman en guncel fullscreen durumunu set et (Turkce karakter yok)
        boolean needsFullScreen = (content != PanelContent.WIDGETS
                && content != PanelContent.MUSIC && content != PanelContent.ANTENNA);
        if (fullScreenListener != null) {
            fullScreenListener.onFullScreenStateChanged(needsFullScreen);
        }

        if (currentContent == content) return;
        currentContent = content;

        Fragment fragment = null;
        String tag = content.name();

        switch (content) {
            case WIDGETS:
                // Varsayilan: widget listesi yerine premium birlesik panel
                fragment = new UnifiedPanelFragment();
                break;
            case MUSIC:
                fragment = new MusicPlayerFragment();
                break;
            case APP_DRAWER:
                fragment = new AppDrawerFragment();
                break;
            case SETTINGS:
                fragment = new CarLauncherSettingsFragment();
                break;
            case DESKTOP:
                fragment = new WidgetPanelFragment();
                break;
            case WEATHER:
                fragment = new WeatherDashboardFragment();
                break;
            case ANTENNA:
                // fragment = new app.organicmaps.carlauncher.antenna.AntennaWidgetFragment();
                break;
            case DASHBOARD:
                fragment = new DashboardFragment();
                break;
        }

        if (fragment != null) {
            fragmentManager.beginTransaction()
                    .replace(containerId, fragment, tag)
                    .commitAllowingStateLoss();
        }
    }

    public PanelContent getCurrentContent() {
        return currentContent;
    }
}


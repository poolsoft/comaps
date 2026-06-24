package app.organicmaps.carlauncher.ui;

import android.content.Context;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import app.organicmaps.R;
import app.organicmaps.carlauncher.CarLauncherSettings;

/**
 * SaÄŸ panel iÃ§eriÄŸini yÃ¶neten sÄ±nÄ±f.
 * Android Auto UI mantÄ±ÄŸÄ±: panel mÃ¼zik/bildirim/app drawer gÃ¶sterir.
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
        if (fullScreenListener != null) {
            boolean needsFullScreen = (content == PanelContent.APP_DRAWER || content == PanelContent.MUSIC || content == PanelContent.DESKTOP);
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
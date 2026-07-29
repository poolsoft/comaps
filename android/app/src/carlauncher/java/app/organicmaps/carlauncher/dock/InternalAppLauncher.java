package app.organicmaps.carlauncher.dock;

import android.content.Context;
import android.content.Intent;

import app.organicmaps.carlauncher.CarLauncherInterface;
import app.organicmaps.carlauncher.ui.NeonDashboardActivity;
import app.organicmaps.carlauncher.ui.PanelContentManager;

/**
 * Dahili (Internal) uygulamalarin merkezden yonetilip baslatildigi yardimci sinif.
 */
public class InternalAppLauncher {

    public static void launch(Context context, String uri) {
        InternalApp app = InternalApp.fromPackageName(uri);
        if (app == null) return;

        CarLauncherInterface launcher =
                context instanceof CarLauncherInterface
                        ? (CarLauncherInterface) context : null;

        switch (app) {
            case SETTINGS:
                if (launcher != null) launcher.openCarLauncherSettings();
                break;
            case MUSIC:
                if (launcher != null) {
                    launcher.openMusicPlayer();
                }
                break;
            case ANTENNA:
                if (launcher != null) {
                    launcher.openAntennaAlignmentInPanel();
                }
                break;
            case DASHBOARD:
                if (launcher != null) {
                    launcher.setPanelContent(PanelContentManager.PanelContent.DASHBOARD);
                }
                break;
            case NEON_DASHBOARD:
                Intent neonIntent = new Intent(context, NeonDashboardActivity.class);
                if (launcher == null) {
                    neonIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                }
                context.startActivity(neonIntent);
                break;
        }
    }
}

package app.organicmaps.carlauncher.dock;

import android.content.Context;
import android.content.Intent;

import app.organicmaps.activities.MapActivity;
import app.organicmaps.carlauncher.ui.NeonDashboardActivity;
import app.organicmaps.carlauncher.ui.PanelContentManager;

/**
 * Dahili (Internal) uygulamalarin merkezden yonetilip baslatildigi yardimci sinif.
 */
public class InternalAppLauncher {

    public static void launch(Context context, String uri) {
        InternalApp app = InternalApp.fromPackageName(uri);
        if (app == null) return;

        MapActivity mapActivity = null;
        if (context instanceof MapActivity) {
            mapActivity = (MapActivity) context;
        }

        switch (app) {
            case SETTINGS:
                if (mapActivity != null) mapActivity.openCarLauncherSettings();
                break;
            case MUSIC:
                if (mapActivity != null) mapActivity.openMusicPlayer();
                break;
            case ANTENNA:
                if (mapActivity != null) mapActivity.openAntennaAlignmentInPanel();
                break;
            case DASHBOARD:
                if (mapActivity != null) {
                    mapActivity.getPanelContentManager().setContent(PanelContentManager.PanelContent.DASHBOARD);
                }
                break;
            case NEON_DASHBOARD:
                Intent neonIntent = new Intent(context, NeonDashboardActivity.class);
                if (mapActivity == null) {
                    neonIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                }
                context.startActivity(neonIntent);
                break;
        }
    }
}

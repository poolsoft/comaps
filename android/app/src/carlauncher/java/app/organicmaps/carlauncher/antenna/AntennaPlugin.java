package app.organicmaps.carlauncher.antenna;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import app.organicmaps.MwmApplication;
import app.organicmaps.activities.MapActivity;
import app.organicmaps.plugins.OsmandPlugin;
import app.organicmaps.views.OsmandMapTileView;

/**
 * Plugin to handle Antenna Widget map interactions.
 * Manages the AntennaMapLayer and its visibility.
 */
public class AntennaPlugin extends OsmandPlugin {
    public static final String ID = "antenna_plugin";
    private AntennaMapLayer mapLayer;

    public AntennaPlugin(MwmApplication app) {
        super(app);
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getName() {
        return "Antenna Plugin";
    }

    @Override
    public CharSequence getDescription(boolean linksEnabled) {
        return "Antenna alignment tool helper";
    }

    @Override
    public void registerLayers(@NonNull Context context, @Nullable MapActivity mapActivity) {
        if (mapActivity != null) {
            createLayer(mapActivity);
        }
    }

    @Override
    public void updateLayers(@NonNull Context context, @Nullable MapActivity mapActivity) {
        if (mapActivity != null) {
            createLayer(mapActivity);
        }
    }

    private void createLayer(MapActivity mapActivity) {
        if (mapLayer == null) {
            OsmandMapTileView mapView = mapActivity.getMapView();
            if (mapView != null) {
                mapLayer = new AntennaMapLayer(mapView);
                mapView.addLayer(mapLayer, 5.5f);
            }
        }
    }

    @Override
    public int getLogoResourceId() {
        return app.organicmaps.R.drawable.ic_extension_dark; // Default or custom
    }


}

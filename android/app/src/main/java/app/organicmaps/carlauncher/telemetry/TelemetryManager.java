package app.organicmaps.carlauncher.telemetry;

import android.os.Handler;
import android.os.Looper;

import android.location.Location;
import app.organicmaps.sdk.location.LocationHelper;
import app.organicmaps.MwmApplication;
import app.organicmaps.sdk.location.LocationHelper;

import app.organicmaps.sdk.routing.RoutingController;




import java.util.ArrayList;
import java.util.List;

public class TelemetryManager implements app.organicmaps.location.LocationListener {

    private static TelemetryManager instance;
    private final MwmApplication app;

    private final List<TelemetryListener> listeners = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // --- State Modelleri ---
    public static class LocationState {
        public Location rawLocation;
        public float speedKmh = 0f;
        public double altitudeMeters = 0.0;
        public float bearing = 0f;
    }

    public static class NavigationState {
        public boolean isActive = false;
        public int turnIconRes = 0;
        public String distanceStr = "";
        public String instructionStr = "";
        public String etaStr = "";
    }

    public static class ObdState {
        public boolean isActive = false;
        public String rpm = "--";
        public String temp = "--";
        public String volt = "--";
        public String load = "--";
    }

    private final LocationState locationState = new LocationState();
    private final NavigationState navigationState = new NavigationState();
    private final ObdState obdState = new ObdState();

    // OBD Computers
    private Object compRpm;
    private Object compTemp;
    private Object compVolt;
    private Object compLoad;

    private long lastLocationTime = 0;
    private final Runnable staleGpsRunnable = new Runnable() {
        @Override
        public void run() {
            long now = System.currentTimeMillis();
            if (now - lastLocationTime >= 3000 && locationState.speedKmh > 0) {
                locationState.speedKmh = 0f;
                notifyListeners();
            }
            mainHandler.postDelayed(this, 1000);
        }
    };

    private TelemetryManager(MwmApplication app) {
        this.app = app;
        initObdComputers();
        mainHandler.postDelayed(staleGpsRunnable, 1000);
    }

    public LocationState getLocationState() { return locationState; }
    public NavigationState getNavigationState() { return navigationState; }
    public ObdState getObdState() { return obdState; }

    public static TelemetryManager getInstance(MwmApplication app) {
        if (instance == null) {
            instance = new TelemetryManager(app);
            if (app.getLocationProvider() != null) {
                app.getLocationProvider().addLocationListener(instance);
            }
        }
        return instance;
    }

    private void initObdComputers() {
        VehicleMetricsPlugin plugin = PluginsHelper.getPlugin(VehicleMetricsPlugin.class);
        if (plugin != null && plugin.isActive()) {
            compRpm = null;
            compTemp = null;
            compVolt = null;
            compLoad = null;
        }
    }

    public void addListener(TelemetryListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
            listener.onTelemetryUpdated(locationState, navigationState, obdState);
        }
    }

    public void removeListener(TelemetryListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void updateLocation(Location location) {
        if (location == null) return;
        
        lastLocationTime = System.currentTimeMillis();
        locationState.rawLocation = location;

        if (location.hasSpeed()) {
            float speedKmh = location.getSpeed() * 3.6f;
            // YAZILIM FILTRESI: 3 km/h alti 0 gosterilir (Dalgalanmayi onler)
            if (speedKmh <= 3.0f) {
                locationState.speedKmh = 0f;
            } else {
                locationState.speedKmh = speedKmh;
            }
        }
        if (location.hasAltitude()) locationState.altitudeMeters = location.getAltitude();
        if (location.hasBearing()) locationState.bearing = location.getBearing();

        // OsmAnd'in yerel GPS tetiklemesine bagli olarak diger verileri de guncelle
        pollNavigation();
        pollObd();

        notifyListeners();
    }

    private void pollNavigation() {
        RoutingController RoutingController = app.getRoutingHelper();
        if (RoutingController != null && RoutingController.isFollowingMode() && RoutingController.isRouteCalculated()) {
            navigationState.isActive = true;
            try {
                app.organicmaps.sdk.routing.JunctionInfo nextDirection = RoutingController.getNextRouteDirectionInfo(new app.organicmaps.sdk.routing.JunctionInfo(), true);
                if (nextDirection != null && nextDirection.distanceTo > 0) {
                    navigationState.distanceStr = OsmAndFormatter.getFormattedDistance(nextDirection.distanceTo, app);
                    
                    if (nextDirection.directionInfo != null) {
                        Object Object = nextDirection.directionInfo.getObject();
                        navigationState.turnIconRes = getTurnIcon(Object);
                        navigationState.instructionStr = getTurnInstruction(Object, nextDirection.directionInfo.getStreetName());
                    }
                } else {
                    navigationState.distanceStr = "--";
                    navigationState.turnIconRes = android.R.drawable.arrow_up_float;
                    navigationState.instructionStr = "Duz git";
                }

                int remainingDistance = RoutingController.getLeftDistance();
                int remainingTime = RoutingController.getLeftTime();
                if (remainingDistance > 0 && remainingTime > 0) {
                    navigationState.etaStr = OsmAndFormatter.getFormattedDuration(remainingTime, app) + " (" + OsmAndFormatter.getFormattedDistance(remainingDistance, app) + ")";
                } else {
                    navigationState.etaStr = "";
                }
            } catch (Exception e) {
                navigationState.isActive = false;
            }
        } else {
            navigationState.isActive = false;
        }
    }

    private void pollObd() {
        VehicleMetricsPlugin plugin = PluginsHelper.getPlugin(VehicleMetricsPlugin.class);
        if (plugin != null && plugin.isActive() && plugin.isConnected()) {
            obdState.isActive = true;
            if (compRpm != null) obdState.rpm = plugin.getWidgetValue(compRpm);
            if (compTemp != null) obdState.temp = plugin.getWidgetValue(compTemp) + "ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â°C";
            if (compVolt != null) obdState.volt = plugin.getWidgetValue(compVolt) + "V";
            if (compLoad != null) obdState.load = plugin.getWidgetValue(compLoad) + "%";
        } else {
            obdState.isActive = false;
            obdState.rpm = "--";
            obdState.temp = "--";
            obdState.volt = "--";
            obdState.load = "--";
        }
    }

    private void notifyListeners() {
        mainHandler.post(() -> {
            for (TelemetryListener listener : listeners) {
                listener.onTelemetryUpdated(locationState, navigationState, obdState);
            }
        });
    }

    private int getTurnIcon(Object Object) {
        if (Object == null) return 0;
        if (Object.isRoundAbout()) return android.R.drawable.ic_menu_rotate;
        switch (Object.getValue()) {
            case Object.C: return android.R.drawable.arrow_up_float;
            case Object.TL:
            case Object.TSLL: return android.R.drawable.ic_menu_revert;
            case Object.TR:
            case Object.TSLR: return android.R.drawable.ic_menu_always_landscape_portrait;
            case Object.TU: return android.R.drawable.ic_menu_rotate;
            case Object.KL: return android.R.drawable.ic_menu_revert;
            case Object.KR: return android.R.drawable.ic_menu_always_landscape_portrait;
            default: return android.R.drawable.arrow_up_float;
        }
    }

    private String getTurnInstruction(Object Object, String streetName) {
        if (Object == null) return "Devam et";
        String inst = "Devam et";
        if (Object.isRoundAbout()) {
            inst = "Doneleden " + Object.getExitOut() + ". cikis";
        } else {
            switch (Object.getValue()) {
                case Object.C: inst = "Duz git"; break;
                case Object.TL: inst = "Sola don"; break;
                case Object.TSLL: inst = "Keskin sola don"; break;
                case Object.TR: inst = "Saga don"; break;
                case Object.TSLR: inst = "Keskin saga don"; break;
                case Object.TU: inst = "U donus yap"; break;
                case Object.KL: inst = "Sola devam et"; break;
                case Object.KR: inst = "Saga devam et"; break;
            }
        }
        if (streetName != null && !streetName.isEmpty()) {
            inst += "\n" + streetName;
        }
        return inst;
    }

    public interface TelemetryListener {
        void onTelemetryUpdated(LocationState loc, NavigationState nav, ObdState obd);
    }
}

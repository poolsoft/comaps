package app.organicmaps.carlauncher.telemetry;

import android.os.Handler;
import android.os.Looper;

import net.osmand.Location;
import app.organicmaps.OsmandApplication;
import app.organicmaps.OsmAndLocationProvider;
import app.organicmaps.routing.NextDirectionInfo;
import app.organicmaps.routing.RoutingHelper;
import app.organicmaps.utils.OsmAndFormatter;
import net.osmand.router.TurnType;
import app.organicmaps.plugins.PluginsHelper;
import app.organicmaps.plugins.odb.VehicleMetricsPlugin;
import net.osmand.shared.obd.OBDDataComputer;

import java.util.ArrayList;
import java.util.List;

public class TelemetryManager implements OsmAndLocationProvider.OsmAndLocationListener {

    private static TelemetryManager instance;
    private final OsmandApplication app;

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
    private OBDDataComputer.OBDComputerWidget compRpm;
    private OBDDataComputer.OBDComputerWidget compTemp;
    private OBDDataComputer.OBDComputerWidget compVolt;
    private OBDDataComputer.OBDComputerWidget compLoad;

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

    private TelemetryManager(OsmandApplication app) {
        this.app = app;
        initObdComputers();
        mainHandler.postDelayed(staleGpsRunnable, 1000);
    }

    public LocationState getLocationState() { return locationState; }
    public NavigationState getNavigationState() { return navigationState; }
    public ObdState getObdState() { return obdState; }

    public static TelemetryManager getInstance(OsmandApplication app) {
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
            compRpm = OBDDataComputer.INSTANCE.registerWidget(OBDDataComputer.OBDTypeWidget.RPM, 0);
            compTemp = OBDDataComputer.INSTANCE.registerWidget(OBDDataComputer.OBDTypeWidget.TEMPERATURE_COOLANT, 0);
            compVolt = OBDDataComputer.INSTANCE.registerWidget(OBDDataComputer.OBDTypeWidget.BATTERY_VOLTAGE, 0);
            compLoad = OBDDataComputer.INSTANCE.registerWidget(OBDDataComputer.OBDTypeWidget.CALCULATED_ENGINE_LOAD, 0);
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
        RoutingHelper routingHelper = app.getRoutingHelper();
        if (routingHelper != null && routingHelper.isFollowingMode() && routingHelper.isRouteCalculated()) {
            navigationState.isActive = true;
            try {
                NextDirectionInfo nextDirection = routingHelper.getNextRouteDirectionInfo(new NextDirectionInfo(), true);
                if (nextDirection != null && nextDirection.distanceTo > 0) {
                    navigationState.distanceStr = OsmAndFormatter.getFormattedDistance(nextDirection.distanceTo, app);
                    
                    if (nextDirection.directionInfo != null) {
                        TurnType turnType = nextDirection.directionInfo.getTurnType();
                        navigationState.turnIconRes = getTurnIcon(turnType);
                        navigationState.instructionStr = getTurnInstruction(turnType, nextDirection.directionInfo.getStreetName());
                    }
                } else {
                    navigationState.distanceStr = "--";
                    navigationState.turnIconRes = android.R.drawable.arrow_up_float;
                    navigationState.instructionStr = "Duz git";
                }

                int remainingDistance = routingHelper.getLeftDistance();
                int remainingTime = routingHelper.getLeftTime();
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
            if (compTemp != null) obdState.temp = plugin.getWidgetValue(compTemp) + "°C";
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

    private int getTurnIcon(TurnType turnType) {
        if (turnType == null) return 0;
        if (turnType.isRoundAbout()) return android.R.drawable.ic_menu_rotate;
        switch (turnType.getValue()) {
            case TurnType.C: return android.R.drawable.arrow_up_float;
            case TurnType.TL:
            case TurnType.TSLL: return android.R.drawable.ic_menu_revert;
            case TurnType.TR:
            case TurnType.TSLR: return android.R.drawable.ic_menu_always_landscape_portrait;
            case TurnType.TU: return android.R.drawable.ic_menu_rotate;
            case TurnType.KL: return android.R.drawable.ic_menu_revert;
            case TurnType.KR: return android.R.drawable.ic_menu_always_landscape_portrait;
            default: return android.R.drawable.arrow_up_float;
        }
    }

    private String getTurnInstruction(TurnType turnType, String streetName) {
        if (turnType == null) return "Devam et";
        String inst = "Devam et";
        if (turnType.isRoundAbout()) {
            inst = "Doneleden " + turnType.getExitOut() + ". cikis";
        } else {
            switch (turnType.getValue()) {
                case TurnType.C: inst = "Duz git"; break;
                case TurnType.TL: inst = "Sola don"; break;
                case TurnType.TSLL: inst = "Keskin sola don"; break;
                case TurnType.TR: inst = "Saga don"; break;
                case TurnType.TSLR: inst = "Keskin saga don"; break;
                case TurnType.TU: inst = "U donus yap"; break;
                case TurnType.KL: inst = "Sola devam et"; break;
                case TurnType.KR: inst = "Saga devam et"; break;
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

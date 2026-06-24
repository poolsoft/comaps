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

import app.organicmaps.sdk.location.LocationListener;
import app.organicmaps.sdk.routing.RoutingController;
import app.organicmaps.sdk.routing.RoutingInfo;
import app.organicmaps.sdk.routing.CarDirection;

public class TelemetryManager implements LocationListener {

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
        if (location.hasAltitude()) locationState.altitudeMeters = location.getAltitude();
        if (location.hasBearing()) locationState.bearing = location.getBearing();

        // OsmAnd'in yerel GPS tetiklemesine bagli olarak diger verileri de guncelle
        pollNavigation();
        pollObd();

        notifyListeners();
    }

    private void pollNavigation() {
        RoutingController routingController = RoutingController.get();
        RoutingInfo info = routingController.getCachedRoutingInfo();
        
        if (info != null && RoutingInfo.RoutingSessionState.isNavigable(info.routingSessionState)) {
            navigationState.isActive = true;
            try {
                if (info.distToTurn != null) {
                    navigationState.distanceStr = info.distToTurn.toString(app);
                } else {
                    navigationState.distanceStr = "--";
                }
                
                if (info.carDirection != null) {
                    navigationState.turnIconRes = info.carDirection.getTurnRes();
                    navigationState.instructionStr = getTurnInstruction(info.carDirection, info.nextStreet);
                } else {
                    navigationState.turnIconRes = android.R.drawable.arrow_up_float;
                    navigationState.instructionStr = "Duz git";
                }

                int remainingTime = info.totalTimeInSeconds;
                if (info.distToTarget != null && remainingTime > 0) {
                    int mins = remainingTime / 60;
                    int hrs = mins / 60;
                    mins = mins % 60;
                    String timeStr = hrs > 0 ? (hrs + " sa " + mins + " dk") : (mins + " dk");
                    navigationState.etaStr = timeStr + " (" + info.distToTarget.toString(app) + ")";
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
        // OBD is not supported in Organic Maps, removed logic.
        obdState.isActive = false;
        obdState.rpm = "--";
        obdState.temp = "--";
        obdState.volt = "--";
        obdState.load = "--";
    }

    private void notifyListeners() {
        mainHandler.post(() -> {
            for (TelemetryListener listener : listeners) {
                listener.onTelemetryUpdated(locationState, navigationState, obdState);
            }
        });
    }

    private String getTurnInstruction(CarDirection direction, String streetName) {
        if (direction == null) return "Devam et";
        String inst = "Devam et";
        if (CarDirection.isRoundAbout(direction)) {
            inst = "Doneleden cikis";
        } else {
            switch (direction) {
                case GO_STRAIGHT:
                case NO_TURN: inst = "Duz git"; break;
                case TURN_LEFT: inst = "Sola don"; break;
                case TURN_SHARP_LEFT: inst = "Keskin sola don"; break;
                case TURN_SLIGHT_LEFT: inst = "Hafif sola don"; break;
                case TURN_RIGHT: inst = "Saga don"; break;
                case TURN_SHARP_RIGHT: inst = "Keskin saga don"; break;
                case TURN_SLIGHT_RIGHT: inst = "Hafif saga don"; break;
                case U_TURN_LEFT:
                case U_TURN_RIGHT: inst = "U donus yap"; break;
                case REACHED_YOUR_DESTINATION: inst = "Hedefe ulasildi"; break;
                case EXIT_HIGHWAY_TO_LEFT: inst = "Otoyoldan sola cikis"; break;
                case EXIT_HIGHWAY_TO_RIGHT: inst = "Otoyoldan saga cikis"; break;
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

package app.organicmaps.carlauncher.telemetry;

import android.location.Location;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import app.organicmaps.sdk.location.LocationHelper;
import app.organicmaps.sdk.location.LocationListener;
import app.organicmaps.sdk.routing.RoutingController;
import app.organicmaps.sdk.routing.RoutingInfo;

import java.util.ArrayList;
import java.util.List;

public class TelemetryManager implements LocationListener {

    private static TelemetryManager instance;

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

    // OBD OrganicMaps tarafindan desteklenmiyor, placeholder.
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

    private TelemetryManager() {
        app.organicmaps.MwmApplication.from(app.organicmaps.MwmApplication.get()).getLocationHelper().addListener(this);
        mainHandler.postDelayed(staleGpsRunnable, 1000);
    }

    public LocationState getLocationState() { return locationState; }
    public NavigationState getNavigationState() { return navigationState; }
    public ObdState getObdState() { return obdState; }

    public static TelemetryManager getInstance() {
        if (instance == null) {
            instance = new TelemetryManager();
        }
        return instance;
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
    public void onLocationUpdated(@NonNull Location location) {
        lastLocationTime = System.currentTimeMillis();
        locationState.rawLocation = location;

        if (location.hasSpeed()) {
            float speedKmh = location.getSpeed() * 3.6f;
            // Hiz dalgalanmasini (GPS drift) onlemek icin 3km/h alti 0 sayilir
            if (speedKmh <= 3.0f) {
                locationState.speedKmh = 0f;
            } else {
                locationState.speedKmh = speedKmh;
            }
        }
        if (location.hasAltitude()) locationState.altitudeMeters = location.getAltitude();
        if (location.hasBearing()) locationState.bearing = location.getBearing();

        pollNavigation();

        notifyListeners();
    }

    private void pollNavigation() {
        RoutingController routingController = RoutingController.get();
        if (routingController != null && routingController.isNavigating()) {
            navigationState.isActive = true;
            RoutingInfo info = routingController.getCachedRoutingInfo();
            if (info != null) {
                // OrganicMaps RoutingInfo mapping
                navigationState.distanceStr = info.distToTurn.toString();
                
                // TODO: RoutingInfo turn res mapping
                navigationState.turnIconRes = 0; 
                navigationState.instructionStr = "Devam Et";
                
                navigationState.etaStr = info.distToTarget.toString();
            }
        } else {
            navigationState.isActive = false;
        }
    }

    private void notifyListeners() {
        mainHandler.post(() -> {
            for (TelemetryListener listener : listeners) {
                listener.onTelemetryUpdated(locationState, navigationState, obdState);
            }
        });
    }

    public interface TelemetryListener {
        void onTelemetryUpdated(LocationState loc, NavigationState nav, ObdState obd);
    }
}

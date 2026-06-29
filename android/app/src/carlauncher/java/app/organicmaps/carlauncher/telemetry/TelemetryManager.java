package app.organicmaps.carlauncher.telemetry;

import android.content.Context;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import app.organicmaps.R;
import app.organicmaps.sdk.location.LocationHelper;
import app.organicmaps.sdk.location.LocationListener;
import app.organicmaps.sdk.routing.CarDirection;
import app.organicmaps.sdk.routing.RoutingController;
import app.organicmaps.sdk.routing.RoutingInfo;
import app.organicmaps.util.Utils;

import java.util.ArrayList;
import java.util.List;

import app.organicmaps.carlauncher.obd.OBDConnectionManager;
import app.organicmaps.carlauncher.obd.OBDCommand;
import app.organicmaps.carlauncher.obd.OBDDataField;

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
        public String streetName = "";
    }

    public static class NavigationState {
        public boolean isActive = false;
        public int turnIconRes = 0;
        public String distanceStr = "";
        public String instructionStr = "";
        public String etaStr = "";
        // Hiz limiti bilgisi (navigasyon aktifken doldurulur, negatif = bilgi yok)
        public double speedLimitMps = -1.0;
        public boolean isSpeedLimitExceeded = false;
    }

    // OBD OrganicMaps tarafindan desteklenmiyor, placeholder.
    public static class ObdState {
        public boolean isActive = false;
        public String rpm = "--";
        public String temp = "--";
        public String volt = "--";
        public String load = "--";
    }

    private final android.content.Context mContext;
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

    private TelemetryManager(android.content.Context context) {
        this.mContext = context.getApplicationContext();
        app.organicmaps.MwmApplication.from(context).getLocationHelper().addListener(this);
        mainHandler.postDelayed(staleGpsRunnable, 1000);

        OBDConnectionManager.getInstance(mContext).addListener(new OBDConnectionManager.OBDConnectionListener() {
            @Override
            public void onConnectionStatusChanged(boolean connected) {
                obdState.isActive = connected;
                if (!connected) {
                    obdState.rpm = "--";
                    obdState.temp = "--";
                    obdState.volt = "--";
                    obdState.load = "--";
                }
                notifyListeners();
            }

            @Override
            public void onDataReceived(java.util.Map<OBDCommand, OBDDataField<Object>> data) {
                OBDDataField<Object> rpmField = data.get(OBDCommand.OBD_RPM_COMMAND);
                if (rpmField != null && rpmField != OBDDataField.NO_DATA) {
                    obdState.rpm = String.valueOf(((Number) rpmField.getValue()).intValue());
                }
                OBDDataField<Object> tempField = data.get(OBDCommand.OBD_ENGINE_COOLANT_TEMP_COMMAND);
                if (tempField != null && tempField != OBDDataField.NO_DATA) {
                    obdState.temp = String.valueOf(((Number) tempField.getValue()).intValue());
                }
                OBDDataField<Object> voltField = data.get(OBDCommand.OBD_BATTERY_VOLTAGE_COMMAND);
                if (voltField != null && voltField != OBDDataField.NO_DATA) {
                    obdState.volt = String.format(java.util.Locale.US, "%.1f", ((Number) voltField.getValue()).floatValue());
                }
                OBDDataField<Object> loadField = data.get(OBDCommand.OBD_CALCULATED_ENGINE_LOAD_COMMAND);
                if (loadField != null && loadField != OBDDataField.NO_DATA) {
                    obdState.load = String.format(java.util.Locale.US, "%.1f", ((Number) loadField.getValue()).floatValue());
                }
                notifyListeners();
            }
        });
    }

    public LocationState getLocationState() { return locationState; }
    public NavigationState getNavigationState() { return navigationState; }
    public ObdState getObdState() { return obdState; }

    public static synchronized TelemetryManager getInstance(android.content.Context context) {
        if (instance == null) {
            instance = new TelemetryManager(context);
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

        // O anki cadde/sokak ismini native JNI ile al
        try {
            if (app.organicmaps.sdk.OrganicMaps.isInitialized()) {
                String address = app.organicmaps.sdk.Framework.nativeGetAddress(location.getLatitude(), location.getLongitude());
                locationState.streetName = address != null ? address : "";
            }
        } catch (Exception e) {
            locationState.streetName = "";
        }

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
                navigationState.distanceStr = info.distToTurn != null ? Utils.formatDistance(mContext, info.distToTurn).toString() : "";
                
                // RoutingInfo turn res mapping
                if (info.carDirection != null) {
                    navigationState.turnIconRes = info.carDirection.getTurnRes();
                    navigationState.instructionStr = getTurnInstruction(info.carDirection, info.exitNum, info.nextStreet);
                } else {
                    navigationState.turnIconRes = 0;
                    navigationState.instructionStr = "";
                }
                
                // ETA formatlama
                if (info.totalTimeInSeconds > 0) {
                    navigationState.etaStr = Utils.formatArrivalTime(info.totalTimeInSeconds);
                } else {
                    navigationState.etaStr = info.distToTarget != null ? Utils.formatDistance(mContext, info.distToTarget).toString() : "";
                }

                // Hiz limiti: RoutingInfo'dan dogrudan aliniyor (negatif = bilgi yok)
                navigationState.speedLimitMps = info.speedLimitMps;
                // Hiz asimi kontrolu: mevcut hiz vs limit
                if (info.speedLimitMps > 0 && locationState.rawLocation != null && locationState.rawLocation.hasSpeed()) {
                    navigationState.isSpeedLimitExceeded = locationState.rawLocation.getSpeed() > info.speedLimitMps;
                } else {
                    navigationState.isSpeedLimitExceeded = false;
                }
            }
        } else {
            navigationState.isActive = false;
            navigationState.speedLimitMps = -1.0;
            navigationState.isSpeedLimitExceeded = false;
        }
    }

    private String getTurnInstruction(CarDirection direction, int exitNum, String nextStreet) {
        if (direction == null) return "";
        String instruction;
        
        switch (direction) {
            case NO_TURN:
            case GO_STRAIGHT:
                instruction = mContext.getString(R.string.car_nav_straight);
                break;
            case TURN_RIGHT:
                instruction = mContext.getString(R.string.car_nav_turn_right);
                break;
            case TURN_SHARP_RIGHT:
                instruction = mContext.getString(R.string.car_nav_turn_sharp_right);
                break;
            case TURN_SLIGHT_RIGHT:
                instruction = mContext.getString(R.string.car_nav_turn_slight_right);
                break;
            case TURN_LEFT:
                instruction = mContext.getString(R.string.car_nav_turn_left);
                break;
            case TURN_SHARP_LEFT:
                instruction = mContext.getString(R.string.car_nav_turn_sharp_left);
                break;
            case TURN_SLIGHT_LEFT:
                instruction = mContext.getString(R.string.car_nav_turn_slight_left);
                break;
            case U_TURN_LEFT:
            case U_TURN_RIGHT:
                instruction = mContext.getString(R.string.car_nav_u_turn);
                break;
            case ENTER_ROUND_ABOUT:
            case LEAVE_ROUND_ABOUT:
            case STAY_ON_ROUND_ABOUT:
                if (exitNum > 0) {
                    instruction = mContext.getString(R.string.car_nav_roundabout, exitNum);
                } else {
                    instruction = mContext.getString(R.string.car_nav_roundabout_no_exit);
                }
                break;
            case REACHED_YOUR_DESTINATION:
                instruction = mContext.getString(R.string.car_nav_reached_destination);
                break;
            case EXIT_HIGHWAY_TO_LEFT:
                instruction = mContext.getString(R.string.car_nav_exit_highway_left);
                break;
            case EXIT_HIGHWAY_TO_RIGHT:
                instruction = mContext.getString(R.string.car_nav_exit_highway_right);
                break;
            default:
                instruction = mContext.getString(R.string.car_nav_continue);
                break;
        }
        
        if (nextStreet != null && !nextStreet.isEmpty()) {
            instruction += "\n" + nextStreet;
        }
        return instruction;
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

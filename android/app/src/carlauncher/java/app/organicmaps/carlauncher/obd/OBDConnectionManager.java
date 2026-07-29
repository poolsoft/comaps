package app.organicmaps.carlauncher.obd;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.util.Pair;
import app.organicmaps.sdk.util.log.Logger;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import okio.Okio;
import okio.Sink;
import okio.Source;

public class OBDConnectionManager {
    private static final String TAG = "OBDConnectionManager";
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

    private static OBDConnectionManager instance;

    private final Context context;
    private final List<OBDConnectionListener> listeners = new CopyOnWriteArrayList<>();
    
    private OBDDispatcher dispatcher;
    private ScheduledExecutorService simulatorExecutor;
    private boolean isSimulatorActive = false;
    private boolean isConnected = false;

    public interface OBDConnectionListener {
        void onConnectionStatusChanged(boolean connected);
        void onDataReceived(Map<OBDCommand, OBDDataField<Object>> data);
    }

    private OBDConnectionManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized OBDConnectionManager getInstance(Context context) {
        if (instance == null) {
            instance = new OBDConnectionManager(context);
        }
        return instance;
    }

    public void addListener(OBDConnectionListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
            listener.onConnectionStatusChanged(isConnected);
        }
    }

    public void removeListener(OBDConnectionListener listener) {
        listeners.remove(listener);
    }

    public boolean isConnected() {
        return isConnected;
    }

    public boolean isSimulatorActive() {
        return isSimulatorActive;
    }

    public Set<BluetoothDevice> getPairedDevices() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null && adapter.isEnabled()) {
            try {
                return adapter.getBondedDevices();
            } catch (SecurityException e) {
                Logger.e(TAG, "Missing bluetooth permissions", e);
            }
        }
        return Collections.emptySet();
    }

    public synchronized void connectDevice(final String deviceAddress) {
        disconnect();

        logInfo("Connecting to OBD device: " + deviceAddress);
        
        dispatcher = new OBDDispatcher(true);
        dispatcher.addCommand(OBDCommand.OBD_RPM_COMMAND);
        dispatcher.addCommand(OBDCommand.OBD_ENGINE_COOLANT_TEMP_COMMAND);
        dispatcher.addCommand(OBDCommand.OBD_BATTERY_VOLTAGE_COMMAND);
        dispatcher.addCommand(OBDCommand.OBD_CALCULATED_ENGINE_LOAD_COMMAND);

        dispatcher.setReadStatusListener(new OBDDispatcher.OBDReadStatusListener() {
            @Override
            public void onIOError() {
                Logger.e(TAG, "OBD Connection IO Error");
                notifyStatusChanged(false);
            }
        });

        dispatcher.setUpdateListener(new OBDDispatcher.OBDUpdateListener() {
            @Override
            public void onDataUpdated(Map<OBDCommand, OBDDataField<Object>> cache) {
                notifyDataReceived(cache);
            }
        });

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            Logger.e(TAG, "Bluetooth not supported");
            notifyStatusChanged(false);
            return;
        }

        final BluetoothDevice device;
        try {
            device = adapter.getRemoteDevice(deviceAddress);
        } catch (IllegalArgumentException e) {
            Logger.e(TAG, "Invalid MAC address: " + deviceAddress, e);
            notifyStatusChanged(false);
            return;
        }

        OBDConnector connector = new OBDConnector() {
            private BluetoothSocket socket;

            @Override
            public Pair<Source, Sink> connect() throws Exception {
                try {
                    socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
                    socket.connect();
                    if (socket.isConnected()) {
                        Source src = Okio.source(socket.getInputStream());
                        Sink snk = Okio.sink(socket.getOutputStream());
                        return new Pair<>(src, snk);
                    }
                } catch (Exception e) {
                    Logger.e(TAG, "Socket connect failed", e);
                    if (socket != null) {
                        try {
                            socket.close();
                        } catch (IOException ignored) {}
                    }
                    throw e;
                }
                return null;
            }

            @Override
            public void onConnectionSuccess() {
                logInfo("OBD Connection Success");
                notifyStatusChanged(true);
            }

            @Override
            public void onConnectionFailed() {
                Logger.e(TAG, "OBD Connection Failed");
                notifyStatusChanged(false);
            }

            @Override
            public void disconnect() {
                logInfo("OBD Connector disconnect called");
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (IOException ignored) {}
                    socket = null;
                }
            }
        };

        dispatcher.connect(connector);
    }

    public synchronized void connectSimulator() {
        disconnect();
        isSimulatorActive = true;
        logInfo("Connecting OBD simulator...");

        simulatorExecutor = Executors.newSingleThreadScheduledExecutor();
        simulatorExecutor.scheduleAtFixedRate(new Runnable() {
            private int rpmBase = 800;
            private boolean rpmIncreasing = true;
            private int temp = 85;
            private float volt = 13.6f;
            private float load = 25.0f;

            @Override
            public void run() {
                if (!isSimulatorActive) return;

                if (rpmIncreasing) {
                    rpmBase += 150;
                    if (rpmBase >= 3500) rpmIncreasing = false;
                } else {
                    rpmBase -= 120;
                    if (rpmBase <= 800) rpmIncreasing = true;
                }

                load = 10.0f + (rpmBase / 3500f) * 60.0f;
                volt = 13.5f + (float) Math.random() * 0.4f;

                double rand = Math.random();
                if (rand > 0.9 && temp < 98) temp++;
                if (rand < 0.1 && temp > 82) temp--;

                java.util.Map<OBDCommand, OBDDataField<Object>> simData = new java.util.HashMap<>();
                simData.put(OBDCommand.OBD_RPM_COMMAND, new OBDDataField<Object>(rpmBase));
                simData.put(OBDCommand.OBD_ENGINE_COOLANT_TEMP_COMMAND, new OBDDataField<Object>(temp));
                simData.put(OBDCommand.OBD_BATTERY_VOLTAGE_COMMAND, new OBDDataField<Object>(volt));
                simData.put(OBDCommand.OBD_CALCULATED_ENGINE_LOAD_COMMAND, new OBDDataField<Object>(load));

                notifyDataReceived(simData);
            }
        }, 0, 500, TimeUnit.MILLISECONDS);

        notifyStatusChanged(true);
    }

    public synchronized void disconnect() {
        logInfo("Disconnecting OBD...");
        isSimulatorActive = false;
        
        if (simulatorExecutor != null) {
            simulatorExecutor.shutdownNow();
            simulatorExecutor = null;
        }

        if (dispatcher != null) {
            dispatcher.stopReading();
            dispatcher = null;
        }

        notifyStatusChanged(false);
    }

    private void notifyStatusChanged(boolean connected) {
        this.isConnected = connected;
        for (OBDConnectionListener listener : listeners) {
            listener.onConnectionStatusChanged(connected);
        }
    }

    private void notifyDataReceived(Map<OBDCommand, OBDDataField<Object>> data) {
        for (OBDConnectionListener listener : listeners) {
            listener.onDataReceived(data);
        }
    }

    private void logInfo(String msg) {
        Logger.i(TAG, msg);
    }
}

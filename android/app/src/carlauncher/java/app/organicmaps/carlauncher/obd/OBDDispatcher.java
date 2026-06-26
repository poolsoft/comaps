package app.organicmaps.carlauncher.obd;

import android.util.Pair;
import app.organicmaps.sdk.util.log.Logger;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import okio.Buffer;
import okio.Sink;
import okio.Source;

public class OBDDispatcher {
    private static final String TAG = "OBDDispatcher";

    private final boolean debug;
    private final List<OBDCommand> commandQueue = new CopyOnWriteArrayList<>();
    private final Map<OBDCommand, OBDDataField<Object>> sensorDataCache = new ConcurrentHashMap<>();
    
    private Source inputStream = null;
    private Sink outputStream = null;
    private OBDReadStatusListener readStatusListener = null;
    private OBDUpdateListener updateListener = null;
    private ExecutorService executorService = null;

    private final int initOBDTimeout = 30000;
    private final long initOBDRetryOffset = 2000L;

    public interface OBDReadStatusListener {
        void onIOError();
    }

    public interface OBDUpdateListener {
        void onDataUpdated(Map<OBDCommand, OBDDataField<Object>> cache);
    }

    public OBDDispatcher(boolean debug) {
        this.debug = debug;
    }

    public boolean isDebug() {
        return debug;
    }

    public synchronized void connect(final OBDConnector connector) {
        logInfo("Connecting OBD...");
        if (executorService != null && !executorService.isShutdown()) {
            stopReading();
        }
        executorService = Executors.newSingleThreadExecutor();
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    Pair<Source, Sink> connectionResult = connector.connect();
                    if (connectionResult == null) {
                        connector.onConnectionFailed();
                    } else {
                        connector.onConnectionSuccess();
                        inputStream = connectionResult.first;
                        outputStream = connectionResult.second;
                        startReadObdLooper();
                    }
                } catch (Exception e) {
                    Logger.e(TAG, "Unexpected error in connect: " + e.getMessage(), e);
                    if (readStatusListener != null) {
                        readStatusListener.onIOError();
                    }
                    connector.onConnectionFailed();
                } finally {
                    connector.disconnect();
                    cleanupResources();
                }
            }
        });
    }

    private void startReadObdLooper() {
        logInfo("Start reading obd with " + inputStream + " and " + outputStream);
        Obd2Connection connection = new Obd2Connection(createTransport(), this);
        long startInitTime = System.currentTimeMillis();
        
        while (!connection.initialize() && !Thread.currentThread().isInterrupted() && isConnected(connection)) {
            try {
                Thread.sleep(initOBDRetryOffset);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            if (System.currentTimeMillis() - startInitTime > initOBDTimeout) {
                break;
            }
        }

        if (!connection.isInitialized()) {
            connection.finish();
            return;
        }

        try {
            int cycleIndex = 0;
            while (isConnected(connection) && !Thread.currentThread().isInterrupted()) {
                if (commandQueue.isEmpty()) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    continue;
                }

                logInfo("Start new commandsRound " + cycleIndex);
                cycleIndex++;

                // iterating copy to avoid CME
                List<OBDCommand> currentQueue = new ArrayList<>(commandQueue);
                for (OBDCommand command : currentQueue) {
                    if (Thread.currentThread().isInterrupted() || !isConnected(connection)) {
                        break;
                    }
                    handleCommand(command, connection);
                }

                if (updateListener != null) {
                    updateListener.onDataUpdated(new HashMap<>(sensorDataCache));
                }
            }
        } finally {
            connection.finish();
        }
    }

    private UnderlyingTransport createTransport() {
        return new UnderlyingTransport() {
            @Override
            public void write(byte[] bytes) throws IOException {
                Sink out = outputStream;
                if (out != null) {
                    Buffer buffer = new Buffer();
                    buffer.write(bytes);
                    out.write(buffer, buffer.size());
                    out.flush();
                }
            }

            @Override
            public String read() throws IOException {
                Buffer readBuffer = new Buffer();
                long loopDelay = 100L;
                long ticks = 0L;
                long timeout = 15000L;
                long timeoutTicks = timeout / loopDelay;

                while (!Thread.currentThread().isInterrupted() && isConnectedWithoutConnectionState()) {
                    Source in = inputStream;
                    if (in == null) {
                        return UnderlyingTransport.CONTEXTINACTIVE;
                    }
                    long bytesRead = in.read(readBuffer, 1024);
                    if (bytesRead > 0) {
                        return readBuffer.readUtf8();
                    }
                    if (bytesRead == -1) {
                        return UnderlyingTransport.UNABLETOREAD;
                    }
                    if (ticks > timeoutTicks) {
                        return UnderlyingTransport.TIMEOUT;
                    }
                    try {
                        Thread.sleep(loopDelay);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return UnderlyingTransport.CONTEXTINACTIVE;
                    }
                    ticks++;
                }
                return UnderlyingTransport.CONTEXTINACTIVE;
            }
        };
    }

    private boolean isConnected(Obd2Connection connection) {
        return inputStream != null && outputStream != null && !connection.isFinished();
    }

    private boolean isConnectedWithoutConnectionState() {
        return inputStream != null && outputStream != null;
    }

    private OBDResponse handleCommand(OBDCommand command, Obd2Connection connection) {
        if (command.isStale() && sensorDataCache.containsKey(command)) {
            return OBDResponse.OK;
        }

        String fullCommand = command.isTextCommand() ? command.getTextCommand() : 
            String.format(java.util.Locale.US, "%02X%02X", command.getCommandGroup(), command.getCommand());
        
        OBDResponse commandResult = connection.run(fullCommand, command);
        if (commandResult != null) {
            if (commandResult.isValid() && commandResult.getResult().length >= command.getResponseLength()) {
                Object parsed = command.parseResponse(commandResult.getResult());
                if (parsed != null) {
                    sensorDataCache.put(command, new OBDDataField<>(parsed));
                }
            } else if (commandResult.equals(OBDResponse.NO_DATA)) {
                sensorDataCache.put(command, OBDDataField.NO_DATA);
            } else if (commandResult.equals(OBDResponse.CONNECTION_FAILURE)) {
                if (readStatusListener != null) {
                    readStatusListener.onIOError();
                }
            } else {
                logInfo("Incorrect response length or unknown error for command " + command);
            }
        }
        return commandResult;
    }

    public void addCommand(OBDCommand commandToRead) {
        if (!commandQueue.contains(commandToRead)) {
            commandQueue.add(commandToRead);
        }
    }

    public void clearCommands() {
        commandQueue.clear();
    }

    public void removeCommand(OBDCommand commandToStopReading) {
        commandQueue.remove(commandToStopReading);
    }

    public void setReadStatusListener(OBDReadStatusListener listener) {
        this.readStatusListener = listener;
    }

    public void setUpdateListener(OBDUpdateListener listener) {
        this.updateListener = listener;
    }

    private void cleanupResources() {
        inputStream = null;
        outputStream = null;
        sensorDataCache.clear();
        readStatusListener = null;
        updateListener = null;
    }

    public synchronized void stopReading() {
        logInfo("stop reading");
        if (executorService != null) {
            executorService.shutdownNow();
            executorService = null;
        }
        cleanupResources();
        logInfo("after stop reading");
    }

    public Map<OBDCommand, OBDDataField<Object>> getRawData() {
        return new HashMap<>(sensorDataCache);
    }

    private void logInfo(String msg) {
        if (debug) {
            Logger.d(TAG, msg);
        } else {
            Logger.i(TAG, msg);
        }
    }
}

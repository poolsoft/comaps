package app.organicmaps.carlauncher.obd;

import app.organicmaps.sdk.util.log.Logger;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class Obd2Connection {
    private static final String TAG = "Obd2Connection";

    private final UnderlyingTransport connection;
    private final OBDDispatcher obdDispatcher;

    private static final String[] sideDataToRemoveFromAnswer = {
        "SEARCHING",
        "ERROR",
        "BUS INIT",
        "BUSINIT",
        "BUS ERROR",
        "BUSERROR"
    };

    private static final String[] initCommands = {
        "ATZ", "AT E0", "AT L0", "AT S0", "AT H0", "AT SP 0"
    };

    private boolean initialized = false;
    private boolean finished = false;

    public Obd2Connection(UnderlyingTransport connection, OBDDispatcher obdDispatcher) {
        this.connection = connection;
        this.obdDispatcher = obdDispatcher;
    }

    public synchronized boolean initialize() {
        if (!initialized) {
            initialized = runInitCommands();
        }
        return initialized;
    }

    public boolean isFinished() {
        return finished;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void finish() {
        finished = true;
    }

    private boolean runInitCommands() {
        logInfo("runInitCommands");
        for (String command : initCommands) {
            try {
                String responseString = runImpl(command);
                responseString = normalizeResponseString(command, responseString, null);
                OBDResponse systemResponse = getSystemResponse(responseString);
                if (systemResponse == OBDResponse.STOPPED || systemResponse == OBDResponse.ERROR) {
                    Logger.e(TAG, "error while init obd " + systemResponse);
                    return false;
                }
            } catch (IOException e) {
                Logger.e(TAG, "Exception in runInitCommands: " + e.getMessage(), e);
                return false;
            }
        }
        return true;
    }

    private String runImpl(String command) throws IOException {
        StringBuilder response = new StringBuilder();
        logInfo("start write " + command);
        connection.write((command + "\r").getBytes(StandardCharsets.UTF_8));
        logInfo("end write " + command);
        logInfo("start read");
        while (!finished) {
            String responseRead = connection.read();
            if (UnderlyingTransport.TIMEOUT.equals(responseRead) ||
                UnderlyingTransport.CONTEXTINACTIVE.equals(responseRead) ||
                UnderlyingTransport.UNABLETOREAD.equals(responseRead)) {
                return responseRead;
            }
            logInfo("runImpl(" + command + ") returned " + responseRead);
            if (responseRead.contains("0:")) {
                responseRead = responseRead.substring(responseRead.indexOf("0:"));
            }
            responseRead = responseRead.replace("\r", "")
                .replace("\n", "")
                .replace(" ", "")
                .replace("\t", "")
                .replace(".", "");
            int endFlagPosition = responseRead.indexOf(">");
            if (endFlagPosition != -1) {
                responseRead = responseRead.substring(0, endFlagPosition);
            }
            response.append(responseRead);
            if (endFlagPosition != -1) {
                break;
            }
        }
        logInfo("end read");
        return response.toString();
    }

    public OBDResponse run(String fullCommand, OBDCommand command) {
        if (finished) {
            return OBDResponse.ERROR;
        }
        int commandCode = command.getCommand();
        OBDCommand.CommandType commandType = command.getCommandType();

        try {
            String responseString = runImpl(fullCommand);
            String originalResponseValue = responseString;
            responseString = normalizeResponseString(fullCommand, responseString, commandType);
            OBDResponse systemResponse = getSystemResponse(responseString);
            if (systemResponse != null) {
                return systemResponse;
            }

            if (command.isHexAnswer()) {
                int[] hexValues = toHexValues(responseString);
                if (hexValues.length < 3 ||
                    hexValues[0] != commandType.getCode() ||
                    hexValues[1] != commandCode) {
                    logInfo("Incorrect answer data (size " + hexValues.length + ") for " + fullCommand);
                } else {
                    hexValues = Arrays.copyOfRange(hexValues, 2, hexValues.length);
                }
                return new OBDResponse(hexValues);
            } else {
                byte[] bytes = responseString.getBytes(StandardCharsets.UTF_8);
                int[] ints = new int[bytes.length];
                for (int i = 0; i < bytes.length; i++) {
                    ints[i] = bytes[i] & 0xFF;
                }
                return new OBDResponse(ints);
            }
        } catch (Exception e) {
            Logger.e(TAG, "Conversion error: command: '" + fullCommand + "'", e);
            return OBDResponse.ERROR;
        }
    }

    private OBDResponse getSystemResponse(String responseString) {
        if ("STOPPED".equals(responseString)) return OBDResponse.STOPPED;
        if ("OK".equals(responseString)) return OBDResponse.OK;
        if ("?".equals(responseString)) return OBDResponse.QUESTION_MARK;
        if ("NODATA".equals(responseString)) return OBDResponse.NO_DATA;
        if ("UNABLETOCONNECT".equals(responseString)) {
            finished = true;
            Logger.e(TAG, "connection failure");
            return OBDResponse.CONNECTION_FAILURE;
        }
        if (UnderlyingTransport.CONTEXTINACTIVE.equals(responseString)) {
            finished = true;
            Logger.e(TAG, "context inactive");
            return OBDResponse.ERROR;
        }
        if (UnderlyingTransport.UNABLETOREAD.equals(responseString)) {
            finished = true;
            Logger.e(TAG, "unable to read from stream");
            return OBDResponse.ERROR;
        }
        if (UnderlyingTransport.TIMEOUT.equals(responseString)) {
            Logger.e(TAG, "reading timeout");
            return OBDResponse.ERROR;
        }
        if ("CANERROR".equals(responseString)) {
            Logger.e(TAG, "CAN bus error");
            return OBDResponse.ERROR;
        }
        return null;
    }

    private String normalizeResponseString(String fullCommand, String response, OBDCommand.CommandType commandType) {
        String normalizedResponse = response;
        String unspacedCommand = fullCommand.replace(" ", "");
        normalizedResponse = unpackLongFrame(normalizedResponse);
        if (normalizedResponse.startsWith(unspacedCommand)) {
            normalizedResponse = normalizedResponse.substring(unspacedCommand.length());
        }
        if (commandType != null) {
            String responseCodeText = commandType.getResponseCodeText();
            if (!normalizedResponse.startsWith(responseCodeText)) {
                int responseStart = normalizedResponse.indexOf(responseCodeText);
                if (responseStart != -1) {
                    normalizedResponse = normalizedResponse.substring(responseStart);
                }
            }
        }
        normalizedResponse = removeSideData(normalizedResponse);
        return normalizedResponse;
    }

    private String removeSideData(String response) {
        String result = response;
        for (String pattern : sideDataToRemoveFromAnswer) {
            result = result.replace(pattern, "");
        }
        return result;
    }

    private String unpackLongFrame(String response) {
        if (!response.contains(":")) return response;
        String result = response.substring(response.indexOf(':') + 1);
        result = result.replaceAll("[0-9]:", "");
        return result;
    }

    private void logInfo(String msg) {
        if (obdDispatcher.isDebug()) {
            Logger.d(TAG, msg);
        } else {
            Logger.i(TAG, msg);
        }
    }

    public static boolean isInitCommand(String command) {
        for (String cmd : initCommands) {
            if (cmd.equals(command)) {
                return true;
            }
        }
        return false;
    }

    public static int[] toHexValues(String buffer) {
        int[] values = new int[buffer.length() / 2];
        for (int i = 0; i < values.length; i++) {
            values[i] = 16 * toDigitValue(buffer.charAt(2 * i)) + toDigitValue(buffer.charAt(2 * i + 1));
        }
        return values;
    }

    private static int toDigitValue(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c == 'a' || c == 'A') return 10;
        if (c == 'b' || c == 'B') return 11;
        if (c == 'c' || c == 'C') return 12;
        if (c == 'd' || c == 'D') return 13;
        if (c == 'e' || c == 'E') return 14;
        if (c == 'f' || c == 'F') return 15;
        throw new IllegalArgumentException(c + " is not a valid hex digit");
    }
}

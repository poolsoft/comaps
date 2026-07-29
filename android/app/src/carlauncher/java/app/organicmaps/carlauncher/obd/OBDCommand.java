package app.organicmaps.carlauncher.obd;

public enum OBDCommand {
    OBD_CALCULATED_ENGINE_LOAD_COMMAND(0x01, 0x04, 1, response -> response[0] * (100.0f / 255.0f), "vm_eload"),
    OBD_THROTTLE_POSITION_COMMAND(0x01, 0x11, 1, response -> response[0] * (100.0f / 255.0f), "vm_tpos"),
    OBD_ENGINE_OIL_TEMPERATURE_COMMAND(0x01, 0x5C, 1, response -> response[0] - 40, "vm_eotemp"),
    OBD_FUEL_PRESSURE_COMMAND(0x01, 0x0A, 1, response -> response[0] * 3, "vm_fpress"),
    OBD_BATTERY_VOLTAGE_COMMAND(0x01, 0x42, 2, response -> (float)((response[0] * 256) + response[1]) / 1000f, "vm_bvol"),
    OBD_ALT_BATTERY_VOLTAGE_COMMAND(0, 0, 2, response -> {
        byte[] bytes = new byte[response.length];
        for (int i = 0; i < response.length; i++) bytes[i] = (byte) response[i];
        String str = new String(bytes);
        if (str.endsWith("\r")) str = str.substring(0, str.length() - 1);
        try {
            return Float.parseFloat(str) / 10f;
        } catch (NumberFormatException e) {
            return 0f;
        }
    }, "vm_bvol", CommandType.LIVE, false, "AT RV", false),
    OBD_AMBIENT_AIR_TEMPERATURE_COMMAND(0x01, 0x46, 1, response -> response[0] - 40, "vm_atemp"),
    OBD_RPM_COMMAND(0x01, 0x0C, 2, response -> (response[0] * 256 + response[1]) / 4, "vm_espeed"),
    OBD_ENGINE_RUNTIME_COMMAND(0x01, 0x1F, 2, response -> (256 * response[0]) + response[1], "vm_runtime"),
    OBD_SPEED_COMMAND(0x01, 0x0D, 1, response -> response.length > 0 ? response[0] : 0, "vm_vspeed"),
    OBD_AIR_INTAKE_TEMP_COMMAND(0x01, 0x0F, 1, response -> response[0] - 40, "vm_itemp"),
    OBD_ENGINE_COOLANT_TEMP_COMMAND(0x01, 0x05, 1, response -> response[0] - 40, "vm_ctemp"),
    OBD_FUEL_CONSUMPTION_RATE_COMMAND(0x01, 0x5E, 2, response -> ((response[0] * 256) + response[1]) / 20.0, "vm_fcons"),
    OBD_FUEL_TYPE_COMMAND(0x01, 0x51, 1, response -> response[0], null, CommandType.LIVE, true, null, true),
    OBD_VIN_COMMAND(0x09, 0x02, 1, response -> {
        StringBuilder vin = new StringBuilder();
        for (int i = 1; i < response.length; i++) {
            vin.append((char) response[i]);
        }
        return vin.toString();
    }, null, CommandType.IDENTIFICATION, true, null, true),
    OBD_FUEL_LEVEL_COMMAND(0x01, 0x2F, 1, response -> response[0] * (100.0f / 255.0f), "vm_fuel");

    public enum CommandType {
        LIVE(0x41), FREEZE(0x42), IDENTIFICATION(0x49);
        private final int code;
        private final String responseCodeText;
        CommandType(int code) {
            this.code = code;
            this.responseCodeText = String.format("%02X", code);
        }
        public int getCode() { return code; }
        public String getResponseCodeText() { return responseCodeText; }
    }

    public interface OBDParser {
        Object parse(int[] response);
    }

    private final int commandGroup;
    private final int command;
    private final int responseLength;
    private final OBDParser parser;
    private final String gpxTag;
    private final CommandType commandType;
    private final boolean isStale;
    private final String textCommand;
    private final boolean isHexAnswer;

    OBDCommand(int commandGroup, int command, int responseLength, OBDParser parser, String gpxTag) {
        this(commandGroup, command, responseLength, parser, gpxTag, CommandType.LIVE, false, null, true);
    }

    OBDCommand(int commandGroup, int command, int responseLength, OBDParser parser, String gpxTag,
               CommandType commandType, boolean isStale, String textCommand, boolean isHexAnswer) {
        this.commandGroup = commandGroup;
        this.command = command;
        this.responseLength = responseLength;
        this.parser = parser;
        this.gpxTag = gpxTag;
        this.commandType = commandType;
        this.isStale = isStale;
        this.textCommand = textCommand;
        this.isHexAnswer = isHexAnswer;
    }

    public int getCommandGroup() { return commandGroup; }
    public int getCommand() { return command; }
    public int getResponseLength() { return responseLength; }
    public String getGpxTag() { return gpxTag; }
    public CommandType getCommandType() { return commandType; }
    public boolean isStale() { return isStale; }
    public String getTextCommand() { return textCommand; }
    public boolean isHexAnswer() { return isHexAnswer; }

    public Object parseResponse(int[] response) {
        return parser.parse(response);
    }

    public boolean isTextCommand() {
        return command == 0 && commandGroup == 0 && textCommand != null;
    }

    public static OBDCommand getByCode(int commandGroup, int commandId) {
        for (OBDCommand cmd : values()) {
            if (cmd.commandGroup == commandGroup && cmd.command == commandId) {
                return cmd;
            }
        }
        return null;
    }

    public static OBDCommand getCommand(String name) {
        for (OBDCommand cmd : values()) {
            if (cmd.name().equalsIgnoreCase(name)) {
                return cmd;
            }
        }
        return null;
    }
}

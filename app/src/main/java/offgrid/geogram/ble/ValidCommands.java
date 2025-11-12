package offgrid.geogram.ble;

public class ValidCommands {

    public static final String[] commands = new String[]{
            "+",
            "/"
    };

    // syntax: /repeat AV83
    public static final String PARCEL_REPEAT = "/repeat";

    // syntax: /read 1234567890 CALLSIGN (timestamp and author of the message being read)
    public static final String READ_RECEIPT = "/read";
    // syntax: /R 949441328 CALLSIGN (compact format - last 9 digits of timestamp + callsign)
    public static final String READ_RECEIPT_COMPACT = "/R";


    public static boolean isValidCommand(String command) {
        if(command == null){
            return false;
        }
        for (String cmd : commands) {
            if (command.startsWith(cmd)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Is this a valid one-line keyword made to our device?
     * @param command
     * @return true when it is an usable command
     */
    public static boolean isValidRequest(String command){
        if(command.startsWith(PARCEL_REPEAT + " ")){
            return true;
        }
        if(command.startsWith(READ_RECEIPT + " ")){
            return true;
        }
        if(command.startsWith(READ_RECEIPT_COMPACT + " ")){
            return true;
        }
        return false;
    }

}

package offgrid.geogram.ble;

public class ValidCommands {

    public static final String[] commands = new String[]{
            "+",
            "/"
    };

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
}

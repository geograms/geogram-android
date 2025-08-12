package offgrid.geogram.core;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

public class Log {

    private static final int sizeOfLog = 5000;

    // List of messages received
    public static final CopyOnWriteArrayList<String> logMessages = new CopyOnWriteArrayList<>();

    // Callback interface
    public interface LogListener {
        void onLogUpdated(String message);
    }

    private static LogListener logListener;

    // Method to set the listener
    public static void setLogListener(LogListener listener) {
        logListener = listener;
    }

    public static void clear() {
        logMessages.clear();
        if (logListener != null) {
            logListener.onLogUpdated(null); // Notify of cleared logs
        }
    }

    public static void log(int priority, String tag, String message) {
        // Get the current timestamp
        String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                .format(Calendar.getInstance().getTime());

        // Format the message with the timestamp
        String formattedMessage = timestamp + " [" + tag + "] " + message;

        if(tag.length() == 1){
            formattedMessage = timestamp + " " + tag + " " + message;
        }

        // Write to the system log
        if(Central.debugForLocalTests == false) {
            try {
                android.util.Log.println(priority, tag, formattedMessage);
            } catch (Exception e) {
                // don't print messages
                e.printStackTrace();
            }
        }
        // Add the message
        logMessages.add(formattedMessage);

        // Trim log size
        if (logMessages.size() > sizeOfLog) {
            logMessages.remove(0);
        }

        // Notify the listener
        if (logListener != null) {
            logListener.onLogUpdated(formattedMessage);
        }
    }

    public static void d(String tag, String message) {
        log(android.util.Log.DEBUG, tag, message);
    }

    public static void e(String tag, String message) {
        log(android.util.Log.ERROR, tag, message);
    }

    public static void i(String tag, String message) {
        log(android.util.Log.INFO, tag, message);
    }
}

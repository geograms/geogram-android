package offgrid.geogram.core;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

public class Log {

    private static final int sizeOfLog = 5000;

    // File logging configuration
    private static final String LOG_FILENAME = "app_debug.log";
    private static final long MAX_LOG_SIZE = 5 * 1024 * 1024; // 5 MB
    private static final long TRIM_TO_SIZE = 1 * 1024 * 1024; // 1 MB
    private static File logFile = null;
    private static SimpleDateFormat fileLogDateFormat = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US);

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

    /**
     * Initialize file logging. Should be called once from Application or MainActivity.
     */
    public static void initFileLogging(android.content.Context context) {
        if (logFile == null && context != null) {
            logFile = new File(context.getFilesDir(), LOG_FILENAME);
            writeToFile("=== File logging initialized ===");
        }
    }

    /**
     * Write a message to the log file.
     */
    private static void writeToFile(String message) {
        if (logFile == null) {
            // Try to initialize using MainActivity if available
            offgrid.geogram.MainActivity mainActivity = offgrid.geogram.MainActivity.getInstance();
            if (mainActivity != null) {
                logFile = new File(mainActivity.getFilesDir(), LOG_FILENAME);
            } else {
                // Can't write to file yet - context not available
                return;
            }
        }

        try {
            // Check file size and trim if needed
            if (logFile.exists() && logFile.length() > MAX_LOG_SIZE) {
                trimLogFile();
            }

            // Append to log file
            FileWriter writer = new FileWriter(logFile, true);
            writer.write(message + "\n");
            writer.close();

        } catch (IOException e) {
            // Silently fail - don't spam logs with log errors
            android.util.Log.e("Log", "Failed to write to log file: " + e.getMessage());
        }
    }

    /**
     * Trim log file to TRIM_TO_SIZE by keeping only the most recent logs.
     */
    private static void trimLogFile() {
        try {
            if (!logFile.exists() || logFile.length() <= MAX_LOG_SIZE) {
                return;
            }

            // Read last TRIM_TO_SIZE bytes
            RandomAccessFile raf = new RandomAccessFile(logFile, "r");
            long fileLength = raf.length();
            long seekPosition = fileLength - TRIM_TO_SIZE;

            // Seek to position
            raf.seek(seekPosition);

            // Read from this position to end
            byte[] buffer = new byte[(int) TRIM_TO_SIZE];
            int bytesRead = raf.read(buffer);
            raf.close();

            // Find first newline to avoid partial log line
            int firstNewline = 0;
            for (int i = 0; i < bytesRead; i++) {
                if (buffer[i] == '\n') {
                    firstNewline = i + 1;
                    break;
                }
            }

            // Write trimmed content back
            FileWriter writer = new FileWriter(logFile, false); // overwrite mode
            writer.write("=== Log trimmed from " + (fileLength / 1024) + " KB to " + (TRIM_TO_SIZE / 1024) + " KB ===\n");
            writer.write(new String(buffer, firstNewline, bytesRead - firstNewline));
            writer.close();

        } catch (IOException e) {
            android.util.Log.e("Log", "Failed to trim log file: " + e.getMessage());
        }
    }

    /**
     * Get the log file path.
     */
    public static String getLogFilePath() {
        return logFile != null ? logFile.getAbsolutePath() : "Not initialized";
    }

    public static void log(int priority, String tag, String message) {
        // Get the current timestamp for display
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

        // Write to file with milliseconds timestamp
        String fileTimestamp = fileLogDateFormat.format(Calendar.getInstance().getTime());
        String priorityStr;
        switch (priority) {
            case android.util.Log.DEBUG: priorityStr = "D"; break;
            case android.util.Log.INFO: priorityStr = "I"; break;
            case android.util.Log.WARN: priorityStr = "W"; break;
            case android.util.Log.ERROR: priorityStr = "E"; break;
            default: priorityStr = "?"; break;
        }
        String fileLogMessage = fileTimestamp + " " + priorityStr + " [" + tag + "] " + message;
        writeToFile(fileLogMessage);

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

    public static void d(String tag, String message, Throwable throwable) {
        log(android.util.Log.DEBUG, tag, message + "\n" + android.util.Log.getStackTraceString(throwable));
    }

    public static void e(String tag, String message) {
        log(android.util.Log.ERROR, tag, message);
    }

    public static void e(String tag, String message, Throwable throwable) {
        log(android.util.Log.ERROR, tag, message + "\n" + android.util.Log.getStackTraceString(throwable));
    }

    public static void i(String tag, String message) {
        log(android.util.Log.INFO, tag, message);
    }

    public static void i(String tag, String message, Throwable throwable) {
        log(android.util.Log.INFO, tag, message + "\n" + android.util.Log.getStackTraceString(throwable));
    }

    public static void w(String tag, String message) {
        log(android.util.Log.WARN, tag, message);
    }

    public static void w(String tag, String message, Throwable throwable) {
        log(android.util.Log.WARN, tag, message + "\n" + android.util.Log.getStackTraceString(throwable));
    }
}

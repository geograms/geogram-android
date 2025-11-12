package offgrid.geogram.util;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Simple file-based logger for debugging.
 * Writes logs to app's files directory for easy retrieval via ADB.
 */
public class FileLogger {
    private static final String TAG = "FileLogger";
    private static final String LOG_FILENAME = "app_debug.log";
    private static final long MAX_LOG_SIZE = 5 * 1024 * 1024; // 5 MB

    private static File logFile = null;
    private static SimpleDateFormat dateFormat = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US);

    /**
     * Initialize the file logger with app context.
     */
    public static void init(Context context) {
        if (logFile == null && context != null) {
            logFile = new File(context.getFilesDir(), LOG_FILENAME);

            // Check file size and rotate if needed
            if (logFile.exists() && logFile.length() > MAX_LOG_SIZE) {
                File oldLog = new File(context.getFilesDir(), LOG_FILENAME + ".old");
                if (oldLog.exists()) {
                    oldLog.delete();
                }
                logFile.renameTo(oldLog);
                logFile = new File(context.getFilesDir(), LOG_FILENAME);
            }

            log("FileLogger", "=== FileLogger initialized ===");
        }
    }

    /**
     * Log a message to file.
     * Format: MM-DD HH:MM:SS.mmm | TAG | message
     */
    public static void log(String tag, String message) {
        if (logFile == null) {
            Log.w(TAG, "FileLogger not initialized, skipping log");
            return;
        }

        try {
            String timestamp = dateFormat.format(new Date());
            String logLine = timestamp + " | " + tag + " | " + message + "\n";

            FileWriter writer = new FileWriter(logFile, true); // append mode
            writer.write(logLine);
            writer.close();

            // Also log to logcat
            Log.d(tag, message);

        } catch (IOException e) {
            Log.e(TAG, "Failed to write log: " + e.getMessage());
        }
    }

    /**
     * Log with automatic tag extraction from calling class.
     */
    public static void log(String message) {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        String tag = "Unknown";

        // stackTrace[0] = getStackTrace()
        // stackTrace[1] = this method
        // stackTrace[2] = caller
        if (stackTrace.length > 2) {
            String className = stackTrace[2].getClassName();
            tag = className.substring(className.lastIndexOf('.') + 1);
        }

        log(tag, message);
    }

    /**
     * Clear the log file.
     */
    public static void clear() {
        if (logFile != null && logFile.exists()) {
            logFile.delete();
            log("FileLogger", "=== Log cleared ===");
        }
    }

    /**
     * Get the log file path.
     */
    public static String getLogPath() {
        return logFile != null ? logFile.getAbsolutePath() : "Not initialized";
    }
}

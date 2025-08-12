package offgrid.geogram.old.bluetooth_old.watchdog;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import offgrid.geogram.old.bluetooth_old.broadcast.BroadcastSender;
import offgrid.geogram.old.bluetooth_old.other.comms.BlueCommands;
import offgrid.geogram.core.Central;
import offgrid.geogram.core.Log;

/**
 * Singleton class for BluePing.
 */
public class BluePing {

    // Static instance of the singleton
    private static BluePing instance;

    // Application context
    private Context context;

    // Handler for managing delayed tasks
    private Handler handler;
    private final int INITIAL_DELAY_MS = 60000; // 60 seconds
    private final int PING_INTERVAL_MS = 1 * 60 * 1000; // 1 minutes for the loop

    // Indicates whether the ping service is running
    private boolean isRunning = false; // initial state

    // Private constructor to prevent instantiation from outside
    private BluePing(Context context) {
        // Save application context to prevent memory leaks
        this.context = context.getApplicationContext();

        // Initialize the handler and schedule the initial delay
        this.handler = new Handler(Looper.getMainLooper());
    }

    /**
     * Schedules the ping service to start after an initial delay.
     */
    public synchronized void start() {
        if (isRunning) {
            return; // Already running
        }
        isRunning = true;
        handler.postDelayed(() -> handler.post(pingRunnable), INITIAL_DELAY_MS);
    }

    /**
     * Stops the periodic ping service.
     */
    public synchronized void stop() {
        if (!isRunning) {
            return; // Not running
        }
        isRunning = false;
        handler.removeCallbacks(pingRunnable);
    }

    // Runnable to handle periodic pings
    private final Runnable pingRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isRunning) {
                return;
            }

            try {
                Log.i("BluePing", "Ping sent to all devices within reach");
                String message = BlueCommands.oneLineCommandPing + Central.getInstance().getSettings().getIdDevice();
                BroadcastSender.broadcastMessageToAllEddystoneDevicesShort(message, context);
            } catch (Exception e) {
                Log.e("BluePing", "Error sending ping: " + e.getMessage());
            }
            // Schedule the next ping
            handler.postDelayed(this, PING_INTERVAL_MS);
        }
    };

    /**
     * Provides the global instance of the BluePing class.
     *
     * @param context The application context required for initialization.
     * @return The singleton instance of BluePing.
     */
    public static synchronized BluePing getInstance(Context context) {
        if (instance == null) {
            instance = new BluePing(context);
        }
        return instance;
    }

    /**
     * Checks whether the ping service is currently running.
     *
     * @return {@code true} if the ping service is running, {@code false} otherwise.
     */
    public boolean isRunning() {
        return isRunning;
    }
}

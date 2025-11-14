package offgrid.geogram.i2p;

import android.content.Context;
import android.content.SharedPreferences;
import offgrid.geogram.core.Log;

/**
 * Main service for managing I2P connectivity lifecycle.
 *
 * Responsibilities:
 * - Start/stop I2P router connection
 * - Manage SAM bridge connection
 * - Generate and persist I2P destination
 * - Monitor I2P tunnel status
 * - Handle battery-based auto-disconnect
 */
public class I2PService {
    private static final String TAG = "I2PService";
    private static final String PREFS_NAME = "i2p_prefs";
    private static final String PREF_ENABLED = "i2p_enabled";
    private static final String PREF_BATTERY_THRESHOLD = "battery_disconnect_threshold";
    private static final int DEFAULT_BATTERY_THRESHOLD = 10; // 10%

    private static I2PService instance;
    private Context context;
    private I2PDestination destination;
    private SAMBridge samBridge;
    private boolean isRunning;
    private boolean isReady;

    private I2PService(Context context) {
        this.context = context.getApplicationContext();
        this.isRunning = false;
        this.isReady = false;
    }

    public static synchronized I2PService getInstance(Context context) {
        if (instance == null) {
            instance = new I2PService(context);
        }
        return instance;
    }

    /**
     * Initialize I2P service and generate/load destination
     */
    public void initialize() {
        Log.i(TAG, "Initializing I2P service");

        // Load or generate I2P destination
        destination = new I2PDestination(context);

        if (destination.isValid()) {
            Log.i(TAG, "I2P destination ready: " + destination.getBase32Address());
        } else {
            Log.e(TAG, "Failed to initialize I2P destination");
        }

        // Check if I2P was enabled by user
        if (isEnabled()) {
            startI2P();
        }
    }

    /**
     * Start I2P service
     */
    public void startI2P() {
        if (isRunning) {
            Log.w(TAG, "I2P already running");
            return;
        }

        Log.i(TAG, "Starting I2P service");

        try {
            // Initialize SAM bridge connection
            samBridge = new SAMBridge(context);

            // TODO: Connect to SAM bridge (port 7656)
            // TODO: Create I2P session
            // TODO: Wait for tunnels to be ready

            isRunning = true;
            isReady = false; // Will be set to true once tunnels are ready

            Log.i(TAG, "I2P service started (tunnels initializing...)");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start I2P: " + e.getMessage());
            isRunning = false;
        }
    }

    /**
     * Stop I2P service
     */
    public void stopI2P() {
        if (!isRunning) {
            Log.w(TAG, "I2P not running");
            return;
        }

        Log.i(TAG, "Stopping I2P service");

        try {
            // TODO: Close SAM bridge connection
            // TODO: Destroy I2P session

            if (samBridge != null) {
                samBridge.disconnect();
                samBridge = null;
            }

            isRunning = false;
            isReady = false;

            Log.i(TAG, "I2P service stopped");
        } catch (Exception e) {
            Log.e(TAG, "Error stopping I2P: " + e.getMessage());
        }
    }

    /**
     * Get I2P destination (Base32 address)
     */
    public String getI2PDestination() {
        if (destination == null) {
            destination = new I2PDestination(context);
        }
        return destination != null ? destination.getBase32Address() : null;
    }

    /**
     * Generate and save a new I2P destination
     */
    public String generateAndSaveDestination() {
        Log.i(TAG, "Generating new I2P destination");
        destination = new I2PDestination(context);
        return destination.getBase32Address();
    }

    /**
     * Check if I2P is ready (tunnels established)
     */
    public boolean isI2PReady() {
        return isReady;
    }

    /**
     * Check if I2P service is running
     */
    public boolean isRunning() {
        return isRunning;
    }

    /**
     * Check if I2P is enabled by user preference
     */
    public boolean isEnabled() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(PREF_ENABLED, true); // Enabled by default
    }

    /**
     * Set I2P enabled/disabled
     */
    public void setEnabled(boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(PREF_ENABLED, enabled).apply();

        Log.i(TAG, "I2P " + (enabled ? "enabled" : "disabled") + " by user");

        if (enabled) {
            startI2P();
        } else {
            stopI2P();
        }
    }

    /**
     * Get battery disconnect threshold (percentage)
     */
    public int getBatteryThreshold() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(PREF_BATTERY_THRESHOLD, DEFAULT_BATTERY_THRESHOLD);
    }

    /**
     * Set battery disconnect threshold
     */
    public void setBatteryThreshold(int threshold) {
        if (threshold < 5 || threshold > 20) {
            Log.w(TAG, "Invalid battery threshold: " + threshold + " (must be 5-20)");
            return;
        }

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(PREF_BATTERY_THRESHOLD, threshold).apply();

        Log.i(TAG, "Battery threshold set to " + threshold + "%");
    }

    /**
     * Handle battery level change (called by BatteryMonitorService)
     */
    public void onBatteryLevelChanged(int batteryPercent) {
        int threshold = getBatteryThreshold();
        int reconnectThreshold = threshold + 5; // 5% hysteresis

        if (batteryPercent < threshold && isRunning()) {
            Log.w(TAG, "Battery below " + threshold + "%, stopping I2P");
            stopI2P();
            // Note: We don't disable I2P, just stop it temporarily
        } else if (batteryPercent > reconnectThreshold && !isRunning() && isEnabled()) {
            Log.i(TAG, "Battery above " + reconnectThreshold + "%, restarting I2P");
            startI2P();
        }
    }

    /**
     * Get SAM bridge instance (for advanced operations)
     */
    public SAMBridge getSAMBridge() {
        return samBridge;
    }

    /**
     * Set I2P ready status (called by SAM bridge when tunnels are ready)
     */
    void setReady(boolean ready) {
        this.isReady = ready;
        if (ready) {
            Log.i(TAG, "I2P tunnels ready");
        }
    }
}

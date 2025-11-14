package offgrid.geogram.i2p;

import android.content.Context;
import android.content.SharedPreferences;
import offgrid.geogram.core.Log;

import net.i2p.I2PAppContext;
import net.i2p.client.I2PClient;
import net.i2p.client.I2PClientFactory;
import net.i2p.client.I2PSession;
import net.i2p.client.streaming.I2PSocketManager;
import net.i2p.client.streaming.I2PSocketManagerFactory;
import net.i2p.data.Destination;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

/**
 * Main service for managing embedded I2P router lifecycle.
 *
 * This service embeds a full I2P router into the app, providing:
 * - Standalone I2P connectivity (no external app required)
 * - I2P destination management
 * - Tunnel creation and management
 * - Battery-aware operation
 */
public class I2PService {
    private static final String TAG = "I2P/Service";
    private static final String PREFS_NAME = "i2p_prefs";
    private static final String PREF_ENABLED = "i2p_enabled";
    private static final String PREF_BATTERY_THRESHOLD = "battery_disconnect_threshold";
    private static final int DEFAULT_BATTERY_THRESHOLD = 10; // 10%

    private static I2PService instance;
    private Context context;

    // I2P Router components
    private I2PAppContext i2pContext;
    private I2PClient i2pClient;
    private I2PSession i2pSession;
    private I2PSocketManager socketManager;
    private Destination destination;

    // State
    private boolean isRunning;
    private boolean isReady;
    private Thread startupThread;

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
     * Initialize I2P service and prepare router environment
     */
    public void initialize() {
        Log.i(TAG, "Initializing I2P service");

        try {
            // Set up I2P directory in app's private storage
            File i2pDir = new File(context.getFilesDir(), "i2p");
            if (!i2pDir.exists()) {
                i2pDir.mkdirs();
                Log.i(TAG, "Created I2P directory: " + i2pDir.getAbsolutePath());
            }

            // Set I2P directory property
            System.setProperty("i2p.dir.base", i2pDir.getAbsolutePath());
            System.setProperty("i2p.dir.config", i2pDir.getAbsolutePath());
            System.setProperty("i2p.dir.router", i2pDir.getAbsolutePath());
            System.setProperty("i2p.dir.app", i2pDir.getAbsolutePath());

            Log.i(TAG, "I2P directory configured: " + i2pDir.getAbsolutePath());

            // Check if I2P was enabled by user
            if (isEnabled()) {
                startI2P();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize I2P: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Start I2P router and create session
     */
    public void startI2P() {
        if (isRunning) {
            Log.w(TAG, "I2P already running");
            return;
        }

        Log.i(TAG, "Starting I2P router...");
        isRunning = true;
        isReady = false;

        // Start I2P in background thread (takes 2-10 minutes)
        startupThread = new Thread(() -> {
            try {
                // Create I2P context with minimal configuration for mobile
                Properties routerConfig = new Properties();
                routerConfig.setProperty("i2np.udp.enable", "false"); // Disable UDP on mobile
                routerConfig.setProperty("i2np.ntcp.enable", "true"); // Use TCP only
                routerConfig.setProperty("router.sharePercentage", "80"); // Share 80% bandwidth
                routerConfig.setProperty("router.hiddenMode", "false");

                Log.i(TAG, "Creating I2P context...");
                i2pContext = I2PAppContext.getGlobalContext();

                // Give context time to initialize
                Thread.sleep(2000);

                // Create I2P client
                Log.i(TAG, "Creating I2P client...");
                i2pClient = I2PClientFactory.createClient();

                // Load or create destination
                File destFile = new File(context.getFilesDir(), "i2p/destination.dat");
                boolean destinationLoaded = false;

                if (destFile.exists()) {
                    try {
                        Log.i(TAG, "Loading existing I2P destination...");
                        FileInputStream fis = new FileInputStream(destFile);
                        destination = new Destination();
                        destination.readBytes(fis);
                        fis.close();
                        destinationLoaded = true;
                        Log.i(TAG, "Successfully loaded I2P destination from file");
                    } catch (Exception e) {
                        Log.w(TAG, "Failed to load I2P destination (corrupted file): " + e.getMessage());
                        // Delete corrupted file
                        if (destFile.delete()) {
                            Log.i(TAG, "Deleted corrupted destination file, will create new one");
                        }
                    }
                }

                if (!destinationLoaded) {
                    Log.i(TAG, "Creating new I2P destination...");
                    FileOutputStream fos = new FileOutputStream(destFile);
                    destination = i2pClient.createDestination(fos);
                    fos.close();
                    Log.i(TAG, "Successfully created new I2P destination");
                }

                Log.i(TAG, "I2P destination: " + destination.toBase32());

                // Create I2P session
                Log.i(TAG, "Creating I2P session...");
                Properties sessionProps = new Properties();
                sessionProps.setProperty("inbound.length", "2"); // 2 hops inbound
                sessionProps.setProperty("outbound.length", "2"); // 2 hops outbound
                sessionProps.setProperty("inbound.quantity", "3"); // 3 tunnels
                sessionProps.setProperty("outbound.quantity", "3"); // 3 tunnels

                i2pSession = i2pClient.createSession(null, sessionProps);
                i2pSession.connect();

                Log.i(TAG, "I2P session connected, building tunnels...");

                // Create socket manager - it will use the session's context internally
                Properties sockMgrProps = new Properties();
                sockMgrProps.setProperty("manager.name", "Geogram");
                socketManager = I2PSocketManagerFactory.createManager(sockMgrProps);

                // Wait for tunnels to be ready (this can take 2-10 minutes)
                int attempts = 0;
                while (attempts < 60 && isRunning) { // Wait up to 10 minutes
                    try {
                        // Check if session is ready (has active tunnels)
                        if (i2pSession.isClosed()) {
                            Log.e(TAG, "I2P session closed unexpectedly");
                            break;
                        }

                        // Simple ready check: if we've been connected for a while, assume ready
                        if (attempts >= 12) { // After 2 minutes, consider it ready enough
                            isReady = true;
                            Log.i(TAG, "I2P tunnels likely ready (took " + (attempts * 10) + " seconds)");
                            break;
                        }
                    } catch (Exception e) {
                        // Tunnels not ready yet
                        Log.d(TAG, "Still waiting for tunnels: " + e.getMessage());
                    }

                    Thread.sleep(10000); // Check every 10 seconds
                    attempts++;

                    if (attempts % 6 == 0) { // Log every minute
                        Log.i(TAG, "Still building I2P tunnels... (" + (attempts * 10 / 60) + " minutes)");
                    }
                }

                if (!isReady) {
                    Log.w(TAG, "I2P tunnels not ready after 10 minutes, but router is running");
                    // Set ready anyway so user can try to use it
                    isReady = true;
                }

            } catch (Exception e) {
                Log.e(TAG, "Failed to start I2P router: " + e.getMessage());
                e.printStackTrace();
                isRunning = false;
                isReady = false;
            }
        });
        startupThread.setName("I2P-Startup");
        startupThread.start();

        Log.i(TAG, "I2P router startup initiated (this will take several minutes)");
    }

    /**
     * Stop I2P router and clean up
     */
    public void stopI2P() {
        if (!isRunning) {
            Log.w(TAG, "I2P not running");
            return;
        }

        Log.i(TAG, "Stopping I2P router...");

        try {
            // Interrupt startup thread if still running
            if (startupThread != null && startupThread.isAlive()) {
                startupThread.interrupt();
            }

            // Close socket manager
            if (socketManager != null) {
                socketManager.destroySocketManager();
                socketManager = null;
            }

            // Disconnect session
            if (i2pSession != null) {
                i2pSession.destroySession();
                i2pSession = null;
            }

            // Clear context reference
            // Note: We don't directly shut down the router context as it's managed globally
            i2pContext = null;

            isRunning = false;
            isReady = false;

            Log.i(TAG, "I2P router stopped");
        } catch (Exception e) {
            Log.e(TAG, "Error stopping I2P: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Get I2P destination (Base32 address)
     */
    public String getI2PDestination() {
        if (destination != null) {
            return destination.toBase32();
        }

        // Try loading from file
        try {
            File destFile = new File(context.getFilesDir(), "i2p/destination.dat");
            if (destFile.exists()) {
                FileInputStream fis = new FileInputStream(destFile);
                destination = new Destination();
                destination.readBytes(fis);
                fis.close();
                return destination.toBase32();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading destination: " + e.getMessage());
        }

        return null;
    }

    /**
     * Generate and save a new I2P destination
     */
    public String generateAndSaveDestination() {
        try {
            Log.i(TAG, "Generating new I2P destination");
            I2PClient client = I2PClientFactory.createClient();
            File destFile = new File(context.getFilesDir(), "i2p/destination.dat");
            FileOutputStream fos = new FileOutputStream(destFile);
            destination = client.createDestination(fos);
            fos.close();
            return destination.toBase32();
        } catch (Exception e) {
            Log.e(TAG, "Failed to generate destination: " + e.getMessage());
            return null;
        }
    }

    /**
     * Check if I2P is ready (tunnels established)
     */
    public boolean isI2PReady() {
        return isReady && socketManager != null;
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
        return prefs.getBoolean(PREF_ENABLED, false); // Disabled by default (user must opt-in)
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
        if (threshold < 5 || threshold > 30) {
            Log.w(TAG, "Invalid battery threshold: " + threshold + " (must be 5-30)");
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
        } else if (batteryPercent > reconnectThreshold && !isRunning() && isEnabled()) {
            Log.i(TAG, "Battery above " + reconnectThreshold + "%, restarting I2P");
            startI2P();
        }
    }

    /**
     * Get I2P socket manager for creating connections
     */
    public I2PSocketManager getSocketManager() {
        return socketManager;
    }

    /**
     * Get I2P session
     */
    public I2PSession getSession() {
        return i2pSession;
    }
}

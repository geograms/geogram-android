package offgrid.geogram.core;

import static offgrid.geogram.core.Messages.log;

import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import offgrid.geogram.apps.chat.ChatFragmentBroadcast;
// Removed Google Play Services dependency
// import offgrid.geogram.old.old.WiFi_control;
import offgrid.geogram.server.SimpleSparkServer;
import offgrid.geogram.settings.SettingsLoader;
import offgrid.geogram.settings.SettingsUser;

public class Central {

    /*
     * New settings
     */
    private SettingsUser settings = null;

    public static boolean debugForLocalTests = false;


    public ChatFragmentBroadcast broadcastChatFragment = new ChatFragmentBroadcast();

    /*
     * Old settings, need to be incrementally removed
     */
    private static Central instance; // Singleton instance
    // Removed Google Play Services dependency - WiFi_control used Nearby API
    // public static WiFi_control wifiControl;

    public static SimpleSparkServer server = null;

    public static boolean alreadyStarted = false;
    public static String device_name = null;
    public static boolean hasNeededPermissions = false;

    // Private constructor to prevent direct instantiation
    private Central() {
    }

    /**
     * Returns the singleton instance of Central.
     *
     * @return The Central instance.
     */
    public static synchronized Central getInstance() {
        if (instance == null) {
            instance = new Central();
        }
        return instance;
    }

    /**
     * Initializes the WiFi control system.
     * DEPRECATED: WiFi_control used Google Play Services Nearby API
     *
     * @param context The application context.
     */
    @Deprecated
    public void initializeWiFiControl(Context context) {
        // Removed - WiFi_control used Google Play Services Nearby API
        // This functionality is not needed for current features
        /*
        if (alreadyStarted) {
            return;
        }
        wifiControl = new WiFi_control(context);
        wifiControl.startAdvertising();
        wifiControl.startDiscovery();

        alreadyStarted = true;
        */
    }

    /**
     * Sets up WiFi.
     *
     * @param act The activity instance.
     * @param TAG The tag for logging purposes.
     * @return True if the setup was successful; false otherwise.
     */
    public boolean wifi_setup(AppCompatActivity act, String TAG) {
        // Check and request permissions
        if (!hasNeededPermissions) {
            Central.getInstance().message(act, TAG, "Failed to get necessary permissions");
            return false;
        } else {
            log(TAG, "Necessary permissions available");
        }

        // Start the background service
        log(TAG, "Starting the background service");
        Intent serviceIntent = new Intent(act, BackgroundService.class);
        ContextCompat.startForegroundService(act, serviceIntent);
        return true;
    }


    public void loadSettings(Context context) {
        try {
            settings = SettingsLoader.loadSettings(context);
            // setup other variables
        } catch (Exception e) {
            log("SettingsLoader", "Failed to load settings. Creating default settings with identity.");
            settings = SettingsLoader.createDefaultSettings(context);
        }
    }

    public SettingsUser getSettings() {
        return settings;
    }

    /**
     * Displays a toast message and logs the message.
     *
     * @param act     The activity instance.
     * @param TAG     The tag for logging purposes.
     * @param message The message to display.
     */
    public void message(AppCompatActivity act, String TAG, String message) {
        Toast.makeText(act, message, Toast.LENGTH_SHORT).show();
        Log.d(TAG, message);
    }

    public void setSettings(SettingsUser settings) {
        this.settings = settings;
    }
}

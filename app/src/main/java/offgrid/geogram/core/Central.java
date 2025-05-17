package offgrid.geogram.core;

import static offgrid.geogram.core.Messages.log;

import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import offgrid.geogram.core.old.WiFi_control;
import offgrid.geogram.server.SimpleSparkServer;
import offgrid.geogram.settings.SettingsLoader;
import offgrid.geogram.settings.SettingsUser;

public class Central {

    /*
     * New settings
     */
    private SettingsUser settings = null;

    public static boolean debugForLocalTests = false;



    /*
     * Old settings, need to be incrementally removed
     */
    private static Central instance; // Singleton instance
    public static WiFi_control wifiControl;

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
     *
     * @param context The application context.
     */
    public void initializeWiFiControl(Context context) {
        if (alreadyStarted) {
            return;
        }
        wifiControl = new WiFi_control(context);
        wifiControl.startAdvertising();
        wifiControl.startDiscovery();

        alreadyStarted = true;
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
        } catch (Exception e) {
            settings = new SettingsUser(); // Default settings if loading fails
            log("SettingsLoader", "Failed to load settings. Using defaults.");
            SettingsLoader.saveSettings(context, settings);
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

package offgrid.geogram.i2p;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.IBinder;
import offgrid.geogram.core.Log;

/**
 * Background service that monitors battery level and manages I2P lifecycle.
 *
 * Responsibilities:
 * - Monitor battery level changes
 * - Disconnect I2P when battery < 10% (configurable)
 * - Reconnect I2P when battery > 15% (with 5% hysteresis)
 * - Show notifications for battery-related I2P state changes
 */
public class BatteryMonitorService extends Service {
    private static final String TAG = "BatteryMonitorService";
    private static final int RECONNECT_HYSTERESIS = 5; // 5% above disconnect threshold

    private BroadcastReceiver batteryReceiver;
    private I2PService i2pService;
    private boolean wasStoppedDueToBattery = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Battery monitor service created");

        i2pService = I2PService.getInstance(this);

        // Register battery level receiver
        batteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                handleBatteryChange(intent);
            }
        };

        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(batteryReceiver, filter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Battery monitor service started");

        // Check current battery level immediately
        checkBatteryLevel();

        return START_STICKY; // Restart if killed
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "Battery monitor service destroyed");

        if (batteryReceiver != null) {
            try {
                unregisterReceiver(batteryReceiver);
            } catch (IllegalArgumentException e) {
                // Already unregistered
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // Not a bound service
    }

    /**
     * Handle battery level change broadcast
     */
    private void handleBatteryChange(Intent intent) {
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        if (level >= 0 && scale > 0) {
            int batteryPercent = (int) ((level / (float) scale) * 100);
            checkBatteryAndManageI2P(batteryPercent);
        }
    }

    /**
     * Check current battery level without waiting for broadcast
     */
    private void checkBatteryLevel() {
        BatteryManager batteryManager = (BatteryManager) getSystemService(BATTERY_SERVICE);
        if (batteryManager != null) {
            int batteryPercent = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            if (batteryPercent > 0) {
                checkBatteryAndManageI2P(batteryPercent);
            }
        }
    }

    /**
     * Check battery level and manage I2P accordingly
     *
     * @param batteryPercent Current battery percentage (0-100)
     */
    private void checkBatteryAndManageI2P(int batteryPercent) {
        int disconnectThreshold = i2pService.getBatteryThreshold();
        int reconnectThreshold = disconnectThreshold + RECONNECT_HYSTERESIS;

        Log.d(TAG, "Battery at " + batteryPercent + "% (disconnect: " + disconnectThreshold +
                   "%, reconnect: " + reconnectThreshold + "%)");

        // Check if we should disconnect I2P
        if (batteryPercent < disconnectThreshold && i2pService.isRunning()) {
            Log.w(TAG, "Battery below " + disconnectThreshold + "%, stopping I2P");
            i2pService.stopI2P();
            wasStoppedDueToBattery = true;

            // TODO: Show notification to user
            // showNotification("I2P disabled to conserve battery");
        }

        // Check if we should reconnect I2P
        else if (batteryPercent > reconnectThreshold && !i2pService.isRunning()) {
            // Only reconnect if:
            // 1. User has I2P enabled in preferences
            // 2. I2P was stopped due to battery (not manually disabled)
            if (i2pService.isEnabled() && wasStoppedDueToBattery) {
                Log.i(TAG, "Battery above " + reconnectThreshold + "%, restarting I2P");
                i2pService.startI2P();
                wasStoppedDueToBattery = false;

                // TODO: Show notification to user
                // showNotification("I2P reconnected");
            }
        }

        // Delegate battery level to I2P service
        i2pService.onBatteryLevelChanged(batteryPercent);
    }

    /**
     * Show notification to user about I2P state change
     * (To be implemented with NotificationManager)
     */
    private void showNotification(String message) {
        Log.i(TAG, "Notification: " + message);
        // TODO: Implement notification using NotificationManager
        // This would show a system notification informing the user
        // about I2P being disabled/enabled due to battery level
    }
}

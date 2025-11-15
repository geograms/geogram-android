package offgrid.geogram.p2p;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.IBinder;
import offgrid.geogram.core.Log;

/**
 * Background service that monitors battery level and manages P2P lifecycle.
 *
 * Responsibilities:
 * - Monitor battery level changes
 * - Disconnect P2P when battery < 10% (configurable)
 * - Reconnect P2P when battery > 15% (with 5% hysteresis)
 * - Show notifications for battery-related P2P state changes
 */
public class BatteryMonitorService extends Service {
    private static final String TAG = "P2P/Battery";
    private static final int RECONNECT_HYSTERESIS = 5; // 5% above disconnect threshold

    private BroadcastReceiver batteryReceiver;
    private P2PService p2pService;
    private boolean wasStoppedDueToBattery = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Battery monitor service created");

        p2pService = P2PService.getInstance(this);

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
     * Check battery level and manage P2P accordingly
     *
     * @param batteryPercent Current battery percentage (0-100)
     */
    private void checkBatteryAndManageI2P(int batteryPercent) {
        int disconnectThreshold = p2pService.getBatteryThreshold();
        int reconnectThreshold = disconnectThreshold + RECONNECT_HYSTERESIS;

        Log.d(TAG, "Battery at " + batteryPercent + "% (disconnect: " + disconnectThreshold +
                   "%, reconnect: " + reconnectThreshold + "%)");

        // Check if we should disconnect P2P
        if (batteryPercent < disconnectThreshold && p2pService.isRunning()) {
            Log.w(TAG, "Battery below " + disconnectThreshold + "%, stopping P2P");
            p2pService.stopP2P();
            wasStoppedDueToBattery = true;

            // TODO: Show notification to user
            // showNotification("P2P disabled to conserve battery");
        }

        // Check if we should reconnect P2P
        else if (batteryPercent > reconnectThreshold && !p2pService.isRunning()) {
            // Only reconnect if:
            // 1. User has P2P enabled in preferences
            // 2. P2P was stopped due to battery (not manually disabled)
            if (p2pService.isEnabled() && wasStoppedDueToBattery) {
                Log.i(TAG, "Battery above " + reconnectThreshold + "%, restarting P2P");
                p2pService.startP2P();
                wasStoppedDueToBattery = false;

                // TODO: Show notification to user
                // showNotification("P2P reconnected");
            }
        }

        // Delegate battery level to P2P service
        p2pService.onBatteryLevelChanged(batteryPercent);
    }

    /**
     * Show notification to user about P2P state change
     * (To be implemented with NotificationManager)
     */
    private void showNotification(String message) {
        Log.i(TAG, "Notification: " + message);
        // TODO: Implement notification using NotificationManager
        // This would show a system notification informing the user
        // about P2P being disabled/enabled due to battery level
    }
}

package offgrid.geogram.network;

import android.content.Context;
import offgrid.geogram.core.Log;
import offgrid.geogram.devices.Device;
import offgrid.geogram.wifi.WiFiDiscoveryService;

/**
 * Manages connection routing between WiFi and BLE.
 *
 * Connection priority:
 * 1. WiFi (fastest, lowest latency)
 * 2. BLE (local only, discovery only)
 */
public class ConnectionManager {
    private static final String TAG = "ConnectionManager";
    private static ConnectionManager instance;

    private Context context;
    private WiFiDiscoveryService wifiService;

    /**
     * Connection method enum
     */
    public enum ConnectionMethod {
        WIFI,
        BLE,
        NONE
    }

    private ConnectionManager(Context context) {
        this.context = context.getApplicationContext();
        this.wifiService = WiFiDiscoveryService.getInstance(context);
    }

    public static synchronized ConnectionManager getInstance(Context context) {
        if (instance == null) {
            instance = new ConnectionManager(context);
        }
        return instance;
    }

    /**
     * Select best connection method for device
     *
     * Priority:
     * 1. WiFi - if device has WiFi IP and is reachable
     * 2. BLE - if device is in BLE range (discovery only)
     * 3. NONE - no connection available
     *
     * @param device The device to connect to
     * @return Best available connection method
     */
    public ConnectionMethod selectConnectionMethod(Device device) {
        if (device == null) {
            return ConnectionMethod.NONE;
        }

        // Priority 1: WiFi (if available)
        if (hasWiFiConnection(device)) {
            Log.d(TAG, "Selected WiFi for device " + device.ID);
            return ConnectionMethod.WIFI;
        }

        // Priority 2: BLE (local discovery only, not for collections)
        if (hasBLEConnection(device)) {
            Log.d(TAG, "Selected BLE for device " + device.ID);
            return ConnectionMethod.BLE;
        }

        Log.w(TAG, "No connection method available for device " + device.ID);
        return ConnectionMethod.NONE;
    }

    /**
     * Check if device has WiFi connection
     */
    private boolean hasWiFiConnection(Device device) {
        String wifiIp = wifiService.getDeviceIp(device.ID);
        return wifiIp != null && !wifiIp.isEmpty();
    }

    /**
     * Check if device has BLE connection
     */
    private boolean hasBLEConnection(Device device) {
        // Check if device was recently seen via BLE
        // Consider device in BLE range if seen in last 5 minutes
        long fiveMinutesAgo = System.currentTimeMillis() - (5 * 60 * 1000);
        long lastSeen = device.latestTimestamp();

        return lastSeen > fiveMinutesAgo;
    }

    /**
     * Get WiFi IP for device (if available)
     *
     * @param device The device
     * @return WiFi IP address, or null if not available
     */
    public String getWiFiIP(Device device) {
        if (device == null) {
            return null;
        }
        return wifiService.getDeviceIp(device.ID);
    }

    /**
     * Check if device is reachable via any method
     *
     * @param device The device to check
     * @return true if device is reachable
     */
    public boolean isDeviceReachable(Device device) {
        ConnectionMethod method = selectConnectionMethod(device);
        return method != ConnectionMethod.NONE;
    }

    /**
     * Get description of current connection method
     *
     * @param method Connection method
     * @return Human-readable description
     */
    public static String getConnectionDescription(ConnectionMethod method) {
        switch (method) {
            case WIFI:
                return "WiFi";
            case BLE:
                return "Bluetooth";
            case NONE:
                return "Offline";
            default:
                return "Unknown";
        }
    }

    /**
     * Get connection speed estimate
     *
     * @param method Connection method
     * @return Speed description (Fast/Medium/Slow)
     */
    public static String getConnectionSpeed(ConnectionMethod method) {
        switch (method) {
            case WIFI:
                return "Fast";
            case BLE:
                return "Very Slow";
            case NONE:
                return "N/A";
            default:
                return "Unknown";
        }
    }

    /**
     * Get expected latency for connection method
     *
     * @param method Connection method
     * @return Estimated latency in milliseconds
     */
    public static long getExpectedLatency(ConnectionMethod method) {
        switch (method) {
            case WIFI:
                return 100; // ~100ms
            case BLE:
                return 1000; // ~1 second
            case NONE:
                return -1; // Not available
            default:
                return -1;
        }
    }
}

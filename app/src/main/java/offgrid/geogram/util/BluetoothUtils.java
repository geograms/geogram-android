package offgrid.geogram.util;

import android.bluetooth.BluetoothGatt;

import java.lang.reflect.Method;

import offgrid.geogram.core.Log;

public class BluetoothUtils {

    public static final String TAG = "BluetoothUtils";

    /**
     * Converts RSSI to a human-readable distance.
     */
    public static String calculateDistance(int rssi) {
        double txPower = -59; // Default Tx Power for BLE beacons
        if (rssi == 0) {
            return "unknown";
        }

        double ratio = rssi * 1.0 / txPower;
        double distance;
        if (ratio < 1.0) {
            distance = Math.pow(ratio, 10);
        } else {
            distance = (0.89976) * Math.pow(ratio, 7.7095) + 0.111;
        }

        if (distance < 0.5) {
            return "next to you";
        } else {
            return String.format("%.2f meters", distance);
        }
    }

    public static boolean refreshDeviceCache(BluetoothGatt gatt) {
        try {
            Method refresh = gatt.getClass().getMethod("refresh");
            if (refresh != null) {
                return (Boolean) refresh.invoke(gatt);
            }
        } catch (Exception e) {
            Log.e(TAG, "Could not refresh device cache: " + e.getMessage());
        }
        return false;
    }

}

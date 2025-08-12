package offgrid.geogram.ble;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

import offgrid.geogram.core.Log;
import offgrid.geogram.events.EventControl;
import offgrid.geogram.events.EventType;

public class BluetoothListener {

    private static final String TAG = "📡";
    private static BluetoothListener instance;

    private final Context context;
    private BluetoothLeScanner scanner;
    private boolean isListening = false;
    private boolean isPaused = false;

    private static final long DUPLICATE_INTERVAL_MS = 3000; // Ignore duplicates within 3 seconds
    private static final long MESSAGE_EXPIRY_MS = 60000; // Discard messages older than 60s

    private final Map<String, Long> recentMessages = new HashMap<>();

    private BluetoothListener(Context context) {
        this.context = context.getApplicationContext();
        BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (manager != null && manager.getAdapter() != null) {
            scanner = manager.getAdapter().getBluetoothLeScanner();
        }
    }

    public static synchronized BluetoothListener getInstance(Context context) {
        if (instance == null) {
            instance = new BluetoothListener(context);
        }
        return instance;
    }

    public void startListening() {
        if (scanner == null || isListening || !hasScanPermission()) return;

        isPaused = false;

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        try {
            scanner.startScan(null, settings, scanCallback);
            isListening = true;
            //Log.i(TAG, "Started BLE scan (all devices).");
        } catch (SecurityException e) {
            Log.e(TAG, "Permission denied: cannot start BLE scan. " + e.getMessage());
        }
    }

    public void stopListening() {
        if (scanner != null && isListening) {
            try {
                scanner.stopScan(scanCallback);
            } catch (SecurityException e) {
                Log.e(TAG, "Permission denied: cannot stop BLE scan. " + e.getMessage());
            }
            isListening = false;
            isPaused = false;
            Log.i(TAG, "Stopped BLE scan.");
        }
    }

    public void pauseListening() {
        if (scanner != null && isListening && !isPaused) {
            try {
                scanner.stopScan(scanCallback);
            } catch (SecurityException e) {
                Log.e(TAG, "Permission denied: cannot pause BLE scan. " + e.getMessage());
            }
            isListening = false;
            isPaused = true;
            //Log.i(TAG, "Paused BLE scan.");
        }
    }

    public void resumeListening() {
        if (scanner != null && isPaused && hasScanPermission()) {
            startListening();
            //Log.i(TAG, "Resumed BLE scan.");
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    private boolean hasScanPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN)
                    == PackageManager.PERMISSION_GRANTED;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED
                    || context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if (result == null || result.getScanRecord() == null) return;

            String deviceAddress = result.getDevice() != null ? result.getDevice().getAddress() : "Unknown";
            int rssi = result.getRssi();
            byte[] rawData = result.getScanRecord().getBytes();

            String textPayload = null;
            if (result.getScanRecord().getServiceData() != null) {
                for (byte[] data : result.getScanRecord().getServiceData().values()) {
                    String decoded = tryDecodeText(data);
                    if (decoded != null && decoded.startsWith(">")) {
                        textPayload = decoded;
                        break;
                    }
                }
            }

            if (textPayload != null) {
                long now = System.currentTimeMillis();

                // Clean up old entries
                Iterator<Map.Entry<String, Long>> iterator = recentMessages.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<String, Long> entry = iterator.next();
                    if (now - entry.getValue() > MESSAGE_EXPIRY_MS) {
                        iterator.remove();
                    }
                }

                // Check if message is duplicate
                Long lastSeen = recentMessages.get(textPayload);
                if (lastSeen != null && now - lastSeen < DUPLICATE_INTERVAL_MS) {
                    return; // skip duplicate
                }

                // Update timestamp and log
                recentMessages.put(textPayload, now);

                // call the event
                EventControl.startEvent(EventType.BLUETOOTH_MESSAGE_RECEIVED, textPayload);

                Log.i(TAG, String.format(Locale.US,
                        "%s: %s",
                        deviceAddress,
                        textPayload
                ));
            }
        }
    };

    private String tryDecodeText(byte[] data) {
        try {
            return new String(data, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}

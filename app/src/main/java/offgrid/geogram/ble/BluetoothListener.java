package offgrid.geogram.ble;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import offgrid.geogram.core.Log;
import offgrid.geogram.events.EventControl;
import offgrid.geogram.events.EventType;

/**
 * GATT-enabled Bluetooth listener with dual capabilities:
 * - Scans for BLE advertisements (for discovery)
 * - Automatically connects to discovered devices via GATT
 * - Receives messages via both advertisements (backward compatibility) and GATT (reliable)
 * - Sends ACK for received GATT messages
 */
public class BluetoothListener {

    private static final String TAG = "📡";
    private static BluetoothListener instance;

    private final Context context;
    private BluetoothLeScanner scanner;
    private boolean isListening = false;
    private boolean isPaused = false;

    private static final long DUPLICATE_INTERVAL_MS = 3000; // Ignore duplicates within 3 seconds
    private static final long MESSAGE_EXPIRY_MS = 60000; // Discard messages older than 60s
    private static final long DEVICE_EXPIRY_MS = 30000; // Consider device disconnected after 30 seconds

    private final Map<String, Long> recentMessages = new ConcurrentHashMap<>();

    // Track discovered devices for GATT connection
    private final Map<String, DiscoveredDevice> discoveredDevices = new ConcurrentHashMap<>();

    private final Handler handler = new Handler();

    // Cleanup task for expired messages and devices
    private final Runnable cleanupTask = new Runnable() {
        @Override
        public void run() {
            cleanupExpiredEntries();
            handler.postDelayed(this, 10000); // Run every 10 seconds
        }
    };

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
                .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                .build();

        try {
            scanner.startScan(null, settings, scanCallback);
            isListening = true;

            // Start cleanup task
            handler.post(cleanupTask);

            Log.i("BluetoothListener", "[Bluetooth] BLE scan started (GATT-enabled)");
        } catch (SecurityException e) {
            Log.e("BluetoothListener", "[Bluetooth] Permission denied: cannot start BLE scan. " + e.getMessage());
        }
    }

    public void stopListening() {
        if (scanner != null && isListening) {
            try {
                scanner.stopScan(scanCallback);
            } catch (SecurityException e) {
                Log.e("BluetoothListener", "[Bluetooth] Permission denied: cannot stop BLE scan. " + e.getMessage());
            }
            isListening = false;
            isPaused = false;

            // Stop cleanup task
            handler.removeCallbacks(cleanupTask);

            Log.i("BluetoothListener", "[Bluetooth] Stopped BLE scan.");
        }
    }

    public void pauseListening() {
        if (scanner != null && isListening && !isPaused) {
            try {
                scanner.stopScan(scanCallback);
            } catch (SecurityException e) {
                Log.e("BluetoothListener", "[Bluetooth] Permission denied: cannot pause BLE scan. " + e.getMessage());
            }
            isListening = false;
            isPaused = true;
        }
    }

    public void resumeListening() {
        if (scanner != null && isPaused && hasScanPermission()) {
            startListening();
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

            BluetoothDevice device = result.getDevice();
            String deviceAddress = device != null ? device.getAddress() : "Unknown";
            int rssi = result.getRssi();

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
                        "[Bluetooth] %s: %s",
                        deviceAddress,
                        textPayload
                ));

                // Track discovered device and attempt GATT connection
                if (device != null) {
                    handleDiscoveredDevice(device, textPayload);
                }
            }
        }
    };

    private void handleDiscoveredDevice(BluetoothDevice device, String beacon) {
        String address = device.getAddress();

        // Only connect to Geogram devices
        if (!isGeogramDevice(beacon)) {
            // Not a Geogram device, skip GATT connection
            return;
        }

        // Extract and map callsign from beacon to MAC address
        // This enables HTTP-over-GATT and relay sync to find devices by callsign
        BluetoothSender sender = BluetoothSender.getInstance(context);
        sender.extractAndMapCallsign(beacon, address);

        // Update discovered device
        DiscoveredDevice discovered = discoveredDevices.get(address);
        if (discovered == null) {
            discovered = new DiscoveredDevice(address, System.currentTimeMillis());
            discoveredDevices.put(address, discovered);
            Log.i("BluetoothListener", "[Bluetooth] Geogram device discovered: " + address + " (" + beacon.substring(0, Math.min(20, beacon.length())) + "...)");

            // Attempt GATT connection to Geogram device
            attemptGattConnection(device);
        } else {
            // Update last seen time
            discovered.lastSeen = System.currentTimeMillis();

            // Check if GATT connection exists - if not, retry connection
            // This handles cases where connection was lost (app restart, BT toggle, etc.)
            if (!sender.hasActiveConnection(address)) {
                // Only retry if we haven't attempted recently (avoid connection storms)
                long timeSinceLastAttempt = System.currentTimeMillis() - discovered.lastConnectionAttempt;
                if (timeSinceLastAttempt > 30000) { // Retry every 30 seconds
                    Log.i("BluetoothListener", "[Bluetooth] No GATT connection to known device " + address + ", retrying");
                    discovered.lastConnectionAttempt = System.currentTimeMillis();
                    attemptGattConnection(device);
                }
            }
        }
    }

    /**
     * Check if the beacon is from a Geogram device.
     * Geogram devices advertise:
     * 1. Location/ping beacons: >+[CALLSIGN]#[MODEL] (e.g., >+X1ADK0@RY1B-IUZT#APP-0.4.0)
     * 2. Message parcels: >[A-Z]{2}[0-9]:[...] (e.g., >AV0:X1ADK0:ANY:JADA)
     * 3. System commands: >/[...] or >INV: or >REQ: etc.
     */
    private boolean isGeogramDevice(String beacon) {
        if (beacon == null || beacon.length() < 3) {
            return false;
        }

        // Remove leading '>' if present
        String content = beacon.startsWith(">") ? beacon.substring(1) : beacon;

        // Check for location/ping beacon: +[CALLSIGN]#[MODEL]
        if (content.startsWith("+")) {
            // Check for APP- or LT1- or other known Geogram device models
            return content.contains("#APP-") ||
                   content.contains("#LT1-") ||
                   content.contains("#MESH-") ||
                   content.contains("#RELAY-");
        }

        // Check for message parcel format: [A-Z]{2}[0-9]:[SENDER]:[DEST]:[...]
        if (content.length() >= 4) {
            char c0 = content.charAt(0);
            char c1 = content.charAt(1);
            char c2 = content.charAt(2);
            char c3 = content.charAt(3);

            // Check: [A-Z][A-Z][0-9]:
            if (c0 >= 'A' && c0 <= 'Z' &&
                c1 >= 'A' && c1 <= 'Z' &&
                c2 >= '0' && c2 <= '9' &&
                c3 == ':') {
                return true;
            }
        }

        // Check for system commands: /, INV:, REQ:, MSG:, etc.
        if (content.startsWith("/") ||
            content.startsWith("INV:") ||
            content.startsWith("REQ:") ||
            content.startsWith("MSG:")) {
            return true;
        }

        // Not a recognized Geogram beacon
        return false;
    }

    private void attemptGattConnection(BluetoothDevice device) {
        // Delegate GATT connection to BluetoothSender (it manages connections)
        BluetoothSender sender = BluetoothSender.getInstance(context);
        handler.postDelayed(() -> {
            sender.connectToDevice(device);
        }, 500); // 500ms delay - quick enough to connect before MAC rotation, but allows server initialization
    }

    /**
     * Handle parcel received via GATT (called by BluetoothSender)
     * This is called from a Binder thread, so we must post to main thread for UI events
     */
    public void handleGattParcel(String parcel, String deviceAddress) {
        long now = System.currentTimeMillis();

        // Check if duplicate
        Long lastSeen = recentMessages.get(parcel);
        if (lastSeen != null && now - lastSeen < DUPLICATE_INTERVAL_MS) {
            Log.d("BluetoothListener", "[Bluetooth] Duplicate GATT parcel ignored: " + parcel.substring(0, Math.min(15, parcel.length())));
            return;
        }

        // Process parcel (fire event)
        Log.i(TAG, String.format(Locale.US,
                "[Bluetooth] %s (GATT): %s",
                deviceAddress,
                parcel
        ));

        // Post event to main thread (GATT callbacks run on Binder thread)
        final String finalParcel = parcel;
        handler.post(() -> {
            EventControl.startEvent(EventType.BLUETOOTH_MESSAGE_RECEIVED, finalParcel);
        });

        // Mark as seen
        recentMessages.put(parcel, now);
    }

    private void cleanupExpiredEntries() {
        long now = System.currentTimeMillis();

        // Clean up old messages
        Iterator<Map.Entry<String, Long>> messageIter = recentMessages.entrySet().iterator();
        while (messageIter.hasNext()) {
            Map.Entry<String, Long> entry = messageIter.next();
            if (now - entry.getValue() > MESSAGE_EXPIRY_MS) {
                messageIter.remove();
            }
        }

        // Clean up inactive devices
        Iterator<Map.Entry<String, DiscoveredDevice>> deviceIter = discoveredDevices.entrySet().iterator();
        while (deviceIter.hasNext()) {
            Map.Entry<String, DiscoveredDevice> entry = deviceIter.next();
            if (now - entry.getValue().lastSeen > DEVICE_EXPIRY_MS) {
                Log.d("BluetoothListener", "[Bluetooth] Device expired: " + entry.getKey());
                deviceIter.remove();
            }
        }
    }

    private String tryDecodeText(byte[] data) {
        try {
            return new String(data, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    // Helper class to track discovered devices
    private static class DiscoveredDevice {
        final String address;
        long lastSeen;
        long lastConnectionAttempt;

        DiscoveredDevice(String address, long lastSeen) {
            this.address = address;
            this.lastSeen = lastSeen;
            this.lastConnectionAttempt = 0; // Will attempt connection immediately on first discovery
        }
    }
}

package offgrid.geogram.ble;

import static offgrid.geogram.ble.BluetoothCentral.GATT_ACK_TIMEOUT_MS;
import static offgrid.geogram.ble.BluetoothCentral.GATT_CHARACTERISTIC_CONTROL_UUID;
import static offgrid.geogram.ble.BluetoothCentral.GATT_CHARACTERISTIC_RX_UUID;
import static offgrid.geogram.ble.BluetoothCentral.GATT_CHARACTERISTIC_TX_UUID;
import static offgrid.geogram.ble.BluetoothCentral.GATT_MTU_SIZE;
import static offgrid.geogram.ble.BluetoothCentral.GATT_SERVICE_UUID;
import static offgrid.geogram.ble.BluetoothCentral.MAX_GATT_CONNECTIONS;
import static offgrid.geogram.ble.BluetoothCentral.advertiseDurationMillis;
import static offgrid.geogram.ble.BluetoothCentral.selfIntervalSeconds;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.ParcelUuid;

import androidx.annotation.RequiresApi;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import offgrid.geogram.core.Central;
import offgrid.geogram.core.Log;
import offgrid.geogram.events.EventControl;
import offgrid.geogram.events.EventType;

/**
 * GATT-based Bluetooth sender with dual-role capability:
 * - Acts as GATT Server (peripheral) to accept connections
 * - Acts as GATT Central to connect to discovered peers
 * - Sends messages via GATT characteristics (bidirectional, reliable)
 * - Maintains backward compatibility with advertising for presence announcement
 */
@SuppressLint("ObsoleteSdkInt")
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class BluetoothSender {

    private static final String TAG = "BluetoothSender";
    private static final UUID SERVICE_UUID = UUID.fromString("0000FEAA-0000-1000-8000-00805F9B34FB");

    // Message priority levels (lower number = higher priority)
    private static final int PRIORITY_HIGH = 1;    // User chat messages
    private static final int PRIORITY_NORMAL = 2;  // /repeat, relay messages
    private static final int PRIORITY_LOW = 3;     // Pings (+), read receipts (/R, /read)

    private static BluetoothSender instance;
    private final Context context;
    private final Handler handler = new Handler();

    // Bluetooth components
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeAdvertiser advertiser;
    private BluetoothGattServer gattServer;

    // State management
    private boolean isRunning = false;
    private boolean isPaused = false;

    // Message queue with priority (lower priority number = sent first)
    private final Queue<QueuedMessage> messageQueue = new PriorityQueue<>();
    private boolean isSending = false;

    // GATT connection management
    private final Map<String, BluetoothGatt> activeConnections = new ConcurrentHashMap<>();
    private final Map<String, BluetoothGatt> connectingDevices = new ConcurrentHashMap<>();  // Devices in process of connecting
    private final Map<String, PendingAck> pendingAcks = new ConcurrentHashMap<>();

    // Callsign-to-MAC address mapping for relay sync lookup
    // Key: callsign (e.g., "X1ADK0"), Value: MAC address (e.g., "6C:12:48:4A:72:C7")
    private final Map<String, String> callsignToMacMap = new ConcurrentHashMap<>();

    // Self-advertising for presence announcement
    private String selfMessage = null;
    private AdvertiseCallback advertiseCallback;

    private final Runnable selfAdvertiseTask = new Runnable() {
        @Override
        public void run() {
            if (isRunning && !isPaused && selfMessage != null) {
                // Only advertise for presence - actual messages go via GATT
                advertiseSelfBeacon();
                handler.postDelayed(this, selfIntervalSeconds * 1000L);
            }
        }
    };

    private BluetoothSender(Context context) {
        this.context = context.getApplicationContext();
        bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            bluetoothAdapter = bluetoothManager.getAdapter();
            if (bluetoothAdapter != null) {
                advertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
            }
        }
    }

    public static synchronized BluetoothSender getInstance(Context context) {
        if (instance == null) {
            instance = new BluetoothSender(context);
        }
        return instance;
    }

    public void start() {
        if (isRunning) return;
        isRunning = true;
        isPaused = false;

        // Start GATT server
        startGattServer();

        // Start self-advertise beacon
        if (selfMessage != null) {
            handler.post(selfAdvertiseTask);
        }

        Log.i(TAG, "Bluetooth sender started with GATT server");
        tryToSendNext();
    }

    public void stop() {
        isRunning = false;
        isPaused = false;

        // Close all GATT connections (both active and connecting)
        for (BluetoothGatt gatt : activeConnections.values()) {
            try {
                gatt.disconnect();
                gatt.close();
            } catch (Exception e) {
                Log.e(TAG, "Error closing active GATT connection: " + e.getMessage());
            }
        }
        for (BluetoothGatt gatt : connectingDevices.values()) {
            try {
                gatt.disconnect();
                gatt.close();
            } catch (Exception e) {
                Log.e(TAG, "Error closing connecting GATT connection: " + e.getMessage());
            }
        }
        activeConnections.clear();
        connectingDevices.clear();
        pendingAcks.clear();

        // Stop GATT server
        if (gattServer != null) {
            try {
                gattServer.close();
            } catch (Exception e) {
                Log.e(TAG, "Error closing GATT server: " + e.getMessage());
            }
            gattServer = null;
        }

        // Stop advertising
        stopAdvertising();
        messageQueue.clear();
        handler.removeCallbacks(selfAdvertiseTask);

        Log.i(TAG, "BluetoothSender stopped");
    }

    public void pause() {
        if (!isPaused) {
            isPaused = true;
            stopAdvertising();
            handler.removeCallbacks(selfAdvertiseTask);
            Log.i(TAG, "BluetoothSender paused");
        }
    }

    public void resume() {
        if (isPaused) {
            isPaused = false;
            Log.i(TAG, "BluetoothSender resumed");
            tryToSendNext();
            if (selfMessage != null) {
                handler.post(selfAdvertiseTask);
            }
        }
    }

    public void sendMessage(String message) {
        if (message == null || message.isEmpty()) return;

        // Use callsign for consistency
        String callsign = Central.getInstance().getSettings().getCallsign();
        if (callsign == null || callsign.isEmpty()) {
            callsign = Central.getInstance().getSettings().getIdDevice();
        }

        boolean singleMessage = ValidCommands.isValidCommand(message);
        BluetoothMessage msg = new BluetoothMessage(callsign, "ANY", message, singleMessage);
        sendMessage(msg);
    }

    public void sendMessage(BluetoothMessage msg) {
        // Fire event for integration
        EventControl.startEvent(EventType.BLE_BROADCAST_SENT, msg);
        Log.i(TAG, "Queued message: " + msg.getOutput());

        int addedCount = 0;
        int duplicateCount = 0;

        for (String parcel : msg.getMessageBox().values()) {
            if (!parcel.startsWith(">")) {
                parcel = ">" + parcel;
            }

            // Detect message priority based on content
            int priority = PRIORITY_HIGH;  // Default: user chat messages
            String content = parcel.substring(1); // Remove ">" prefix

            if (content.startsWith("+")) {
                // Ping/location message -> low priority
                priority = PRIORITY_LOW;
            } else if (content.startsWith("/R ") || content.startsWith("/read ")) {
                // Read receipt -> low priority
                priority = PRIORITY_LOW;
            } else if (content.startsWith("/repeat")) {
                // Parcel retransmission request -> normal priority
                priority = PRIORITY_NORMAL;
            } else if (content.startsWith("INV:") || content.startsWith("REQ:") || content.startsWith("MSG:")) {
                // Relay protocol messages -> normal priority
                priority = PRIORITY_NORMAL;
            }
            // Everything else (user chat messages) keeps PRIORITY_HIGH

            // Create queued message with priority
            QueuedMessage queuedMsg = new QueuedMessage(parcel, msg.getIdFromSender(), priority);

            // Prevent duplicates
            boolean isDuplicate = false;
            for (QueuedMessage existing : messageQueue) {
                if (existing.parcel.equals(parcel)) {
                    isDuplicate = true;
                    duplicateCount++;
                    break;
                }
            }

            if (!isDuplicate) {
                messageQueue.offer(queuedMsg);
                addedCount++;
                Log.d(TAG, "→ Queued parcel #" + addedCount + ": " + parcel.substring(0, Math.min(30, parcel.length())) + "... Queue size: " + messageQueue.size());
            }
        }

        if (duplicateCount > 0) {
            Log.i(TAG, "Queue dedup: added " + addedCount + " parcels, skipped " + duplicateCount + " duplicates. Queue size: " + messageQueue.size());
        }

        if (isRunning && !isPaused) {
            tryToSendNext();
        }
    }

    private void tryToSendNext() {
        if (!isRunning || isPaused || isSending || messageQueue.isEmpty()) {
            return;
        }

        final QueuedMessage queuedMsg = messageQueue.peek();
        if (queuedMsg == null) return;

        Log.d(TAG, "← Sending parcel (queue size: " + messageQueue.size() + ", isSending: " + isSending + "): " + queuedMsg.parcel.substring(0, Math.min(30, queuedMsg.parcel.length())));

        if (!hasAdvertisePermission()) {
            Log.i(TAG, "Missing BLUETOOTH permissions. Cannot send.");
            return;
        }

        // Try to send via GATT to connected peers first
        if (!activeConnections.isEmpty()) {
            sendViaGatt(queuedMsg);
        } else {
            // No GATT connections - fallback to advertising for discovery
            Log.d(TAG, "No GATT connections, advertising parcel for discovery");
            sendViaAdvertising(queuedMsg.parcel);
        }
    }

    private void sendViaGatt(QueuedMessage queuedMsg) {
        isSending = true;
        boolean sent = false;

        // Send to all connected peers
        for (Map.Entry<String, BluetoothGatt> entry : activeConnections.entrySet()) {
            String deviceAddress = entry.getKey();
            BluetoothGatt gatt = entry.getValue();

            try {
                BluetoothGattService service = gatt.getService(UUID.fromString(GATT_SERVICE_UUID));
                if (service == null) {
                    Log.w(TAG, "GATT service not found on device " + deviceAddress);
                    continue;
                }

                BluetoothGattCharacteristic rxChar = service.getCharacteristic(UUID.fromString(GATT_CHARACTERISTIC_RX_UUID));
                if (rxChar == null) {
                    Log.w(TAG, "RX characteristic not found on device " + deviceAddress);
                    continue;
                }

                // Write parcel to RX characteristic
                rxChar.setValue(queuedMsg.parcel.getBytes(StandardCharsets.UTF_8));
                Log.d(TAG, "⚡ Attempting GATT write to " + deviceAddress + " (" + queuedMsg.parcel.length() + " bytes): " + queuedMsg.parcel.substring(0, Math.min(20, queuedMsg.parcel.length())));
                boolean writeResult = gatt.writeCharacteristic(rxChar);

                if (writeResult) {
                    sent = true;

                    // Set up ACK timeout
                    String ackKey = deviceAddress + ":" + queuedMsg.parcel.substring(0, Math.min(5, queuedMsg.parcel.length()));
                    PendingAck pendingAck = new PendingAck(queuedMsg, System.currentTimeMillis());
                    pendingAcks.put(ackKey, pendingAck);
                    Log.d(TAG, "⏱ Waiting for ACK: " + ackKey + " (pending ACKs: " + pendingAcks.size() + ")");

                    // Schedule ACK timeout
                    handler.postDelayed(() -> {
                        if (pendingAcks.containsKey(ackKey)) {
                            Log.w(TAG, "⏰ ACK TIMEOUT for " + ackKey + " after " + GATT_ACK_TIMEOUT_MS + "ms - will retry (queue size: " + messageQueue.size() + ")");
                            pendingAcks.remove(ackKey);
                            isSending = false;
                            tryToSendNext(); // Retry
                        }
                    }, GATT_ACK_TIMEOUT_MS);

                    Log.i(TAG, "✓ GATT write queued to " + deviceAddress + ": " + queuedMsg.parcel.substring(0, Math.min(20, queuedMsg.parcel.length())) + "...");
                } else {
                    Log.w(TAG, "✗ GATT write FAILED to " + deviceAddress);
                }

            } catch (Exception e) {
                Log.e(TAG, "Error sending via GATT to " + deviceAddress + ": " + e.getMessage());
            }
        }

        if (sent) {
            // Wait for ACK before removing from queue
            // ACK will be handled in onCharacteristicWrite callback
        } else {
            // No successful GATT writes, fallback to advertising
            isSending = false;
            sendViaAdvertising(queuedMsg.parcel);
        }
    }

    private void sendViaAdvertising(String parcel) {
        // Fallback to advertising (backward compatibility)
        AdvertiseData data = buildAdvertiseData(parcel);
        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)  // Allow GATT connections
                .build();

        isSending = true;

        AdvertiseCallback callback = new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                Log.i(TAG, "Started advertising parcel: " + parcel.substring(0, Math.min(20, parcel.length())));

                handler.postDelayed(() -> {
                    stopAdvertising();
                    messageQueue.poll(); // Remove from queue
                    isSending = false;
                    tryToSendNext(); // Send next parcel
                }, advertiseDurationMillis);
            }

            @Override
            public void onStartFailure(int errorCode) {
                Log.w(TAG, "Failed to advertise. Error code: " + errorCode);
                stopAdvertising();
                messageQueue.poll(); // Remove from queue anyway
                isSending = false;
                tryToSendNext();
            }
        };

        advertiseCallback = callback;
        try {
            advertiser.startAdvertising(settings, data, callback);
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException while advertising: " + e.getMessage());
            isSending = false;
        }
    }

    private void advertiseSelfBeacon() {
        if (selfMessage == null) return;

        AdvertiseData data = buildAdvertiseData(selfMessage);
        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .setConnectable(true)  // Allow GATT connections
                .build();

        AdvertiseCallback callback = new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                Log.d(TAG, "Self-beacon advertising");
            }

            @Override
            public void onStartFailure(int errorCode) {
                Log.w(TAG, "Self-beacon advertising failed: " + errorCode);
            }
        };

        try {
            advertiser.startAdvertising(settings, data, callback);
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException while self-advertising: " + e.getMessage());
        }
    }

    public void setSelfMessage(String message) {
        this.selfMessage = message;
        if (isRunning && !isPaused) {
            handler.removeCallbacks(selfAdvertiseTask);
            handler.post(selfAdvertiseTask);
            Log.i(TAG, "Self message set to: " + message);
        }
    }

    public void setSelfIntervalSeconds(int seconds) {
        if (seconds <= 0) return;
        selfIntervalSeconds = seconds;
        Log.i(TAG, "Self-advertise interval set to: " + seconds + " seconds");

        if (isRunning && !isPaused && selfMessage != null) {
            handler.removeCallbacks(selfAdvertiseTask);
            handler.post(selfAdvertiseTask);
        }
    }

    private void stopAdvertising() {
        if (advertiser != null && advertiseCallback != null) {
            if (!hasAdvertisePermission()) {
                Log.w(TAG, "Missing BLUETOOTH_ADVERTISE permission. Cannot stop advertiser.");
                return;
            }
            try {
                advertiser.stopAdvertising(advertiseCallback);
            } catch (SecurityException e) {
                Log.w(TAG, "SecurityException while stopping advertiser: " + e.getMessage());
            }
        }
        advertiseCallback = null;
        isSending = false;
    }

    private AdvertiseData buildAdvertiseData(String message) {
        if (message.length() >= 24) {
            message = message.substring(0, 23);
        }

        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);

        return new AdvertiseData.Builder()
                .addServiceUuid(new ParcelUuid(SERVICE_UUID))
                .addServiceData(new ParcelUuid(SERVICE_UUID), bytes)
                .setIncludeDeviceName(false)
                .build();
    }

    private boolean hasAdvertisePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                   context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    // ==================== GATT Server Implementation ====================

    private void startGattServer() {
        if (gattServer != null) {
            Log.i(TAG, "GATT server already running");
            return;
        }

        try {
            gattServer = bluetoothManager.openGattServer(context, gattServerCallback);

            if (gattServer == null) {
                Log.e(TAG, "Failed to create GATT server");
                return;
            }

            // Create service
            BluetoothGattService service = new BluetoothGattService(
                    UUID.fromString(GATT_SERVICE_UUID),
                    BluetoothGattService.SERVICE_TYPE_PRIMARY
            );

            // TX Characteristic (we write, they read/notify)
            BluetoothGattCharacteristic txChar = new BluetoothGattCharacteristic(
                    UUID.fromString(GATT_CHARACTERISTIC_TX_UUID),
                    BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                    BluetoothGattCharacteristic.PERMISSION_WRITE
            );

            // RX Characteristic (they write, we read/notify)
            BluetoothGattCharacteristic rxChar = new BluetoothGattCharacteristic(
                    UUID.fromString(GATT_CHARACTERISTIC_RX_UUID),
                    BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                    BluetoothGattCharacteristic.PERMISSION_WRITE
            );

            // Control Characteristic (ACK/NACK)
            BluetoothGattCharacteristic controlChar = new BluetoothGattCharacteristic(
                    UUID.fromString(GATT_CHARACTERISTIC_CONTROL_UUID),
                    BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                    BluetoothGattCharacteristic.PERMISSION_WRITE | BluetoothGattCharacteristic.PERMISSION_READ
            );

            // Add CCCD descriptor to CONTROL characteristic for notification subscription
            android.bluetooth.BluetoothGattDescriptor cccdDescriptor = new android.bluetooth.BluetoothGattDescriptor(
                    UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"), // CCCD UUID
                    android.bluetooth.BluetoothGattDescriptor.PERMISSION_READ | android.bluetooth.BluetoothGattDescriptor.PERMISSION_WRITE
            );
            controlChar.addDescriptor(cccdDescriptor);

            service.addCharacteristic(txChar);
            service.addCharacteristic(rxChar);
            service.addCharacteristic(controlChar);

            boolean added = gattServer.addService(service);
            if (added) {
                Log.i(TAG, "GATT server started with service " + GATT_SERVICE_UUID);
            } else {
                Log.e(TAG, "Failed to add GATT service");
            }

        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException starting GATT server: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Error starting GATT server: " + e.getMessage());
        }
    }

    private final BluetoothGattServerCallback gattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            super.onConnectionStateChange(device, status, newState);

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "GATT client connected: " + device.getAddress());
                // Client will discover services and subscribe to characteristics

                // Establish bidirectional connection: connect back to the client as a GATT client
                // This allows both devices to send messages to each other via GATT
                if (!activeConnections.containsKey(device.getAddress()) &&
                    !connectingDevices.containsKey(device.getAddress())) {
                    handler.postDelayed(() -> {
                        connectToDevice(device);
                    }, 500); // Small delay to avoid connection storm
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "GATT client disconnected: " + device.getAddress());
                activeConnections.remove(device.getAddress());
            }
        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId,
                                                   BluetoothGattCharacteristic characteristic,
                                                   boolean preparedWrite, boolean responseNeeded,
                                                   int offset, byte[] value) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);

            String uuid = characteristic.getUuid().toString();

            if (uuid.equalsIgnoreCase(GATT_CHARACTERISTIC_RX_UUID)) {
                // Received message parcel from peer
                String parcel = new String(value, StandardCharsets.UTF_8);
                Log.i(TAG, "📥 Received parcel via GATT from " + device.getAddress() + " (" + parcel.length() + " bytes): " + parcel.substring(0, Math.min(20, parcel.length())));

                // Extract callsign from parcel and update mapping for relay sync lookup
                // Parcel format: >+CALLSIGN@GEOCODE#MODEL or >CALLSIGN@GEOCODE#MODEL
                extractAndMapCallsign(parcel, device.getAddress());

                // Process parcel (fire event for BluetoothListener to handle)
                BluetoothListener.getInstance(context).handleGattParcel(parcel, device.getAddress());

                // Send GATT protocol response
                if (responseNeeded) {
                    try {
                        gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
                        Log.d(TAG, "✓ Sent GATT protocol response");
                    } catch (SecurityException e) {
                        Log.e(TAG, "SecurityException sending GATT response: " + e.getMessage());
                    }
                }

                // Send application-level ACK via CONTROL characteristic
                Log.d(TAG, "📤 Sending ACK for parcel: " + parcel.substring(0, Math.min(10, parcel.length())));
                sendAckToDevice(device.getAddress(), parcel);

            } else if (uuid.equalsIgnoreCase(GATT_CHARACTERISTIC_CONTROL_UUID)) {
                // Received ACK/NACK
                String control = new String(value, StandardCharsets.UTF_8);
                handleControlMessage(device.getAddress(), control);

                if (responseNeeded) {
                    try {
                        gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
                    } catch (SecurityException e) {
                        Log.e(TAG, "SecurityException sending GATT response: " + e.getMessage());
                    }
                }
            }
        }

        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId,
                                               android.bluetooth.BluetoothGattDescriptor descriptor,
                                               boolean preparedWrite, boolean responseNeeded,
                                               int offset, byte[] value) {
            super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value);

            // Client is subscribing/unsubscribing from notifications
            String uuid = descriptor.getUuid().toString();
            if (uuid.equalsIgnoreCase("00002902-0000-1000-8000-00805f9b34fb")) { // CCCD
                boolean enabled = java.util.Arrays.equals(value, android.bluetooth.BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                Log.i(TAG, "Client " + device.getAddress() + (enabled ? " subscribed to" : " unsubscribed from") + " notifications");
            }

            if (responseNeeded) {
                try {
                    gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
                } catch (SecurityException e) {
                    Log.e(TAG, "SecurityException sending GATT response for descriptor: " + e.getMessage());
                }
            }
        }
    };

    private void handleControlMessage(String deviceAddress, String control) {
        if (control.startsWith("ACK:")) {
            String ackKey = deviceAddress + ":" + control.substring(4);
            if (pendingAcks.containsKey(ackKey)) {
                Log.i(TAG, "✓ Received ACK from " + deviceAddress + " for " + ackKey + " (pending: " + pendingAcks.size() + ", queue: " + messageQueue.size() + ")");
                pendingAcks.remove(ackKey);

                // Extract parcel prefix from ackKey (e.g., ">ZA0:" from "7E:76:02:29:6B:B9:>ZA0:")
                String parcelPrefix = control.substring(4); // e.g., ">ZA0:"

                // Check if any other devices are still waiting for ACKs for this same parcel
                boolean otherDevicesWaiting = false;
                for (String key : pendingAcks.keySet()) {
                    if (key.endsWith(parcelPrefix)) {
                        otherDevicesWaiting = true;
                        Log.d(TAG, "⏳ Still waiting for ACK from other device: " + key);
                        break;
                    }
                }

                // Only remove from queue when ALL devices have ACKed this parcel
                if (!otherDevicesWaiting) {
                    QueuedMessage completed = messageQueue.poll();
                    Log.d(TAG, "← Completed parcel, removed from queue. New queue size: " + messageQueue.size());
                    isSending = false;
                    handler.post(() -> tryToSendNext());
                } else {
                    Log.d(TAG, "← Parcel ACKed by " + deviceAddress + ", but waiting for other devices before removing from queue");
                }
            } else {
                Log.d(TAG, "⚠ Received unexpected ACK (already processed?): " + ackKey);
            }
        } else if (control.startsWith("NACK:")) {
            String nackKey = deviceAddress + ":" + control.substring(5);
            Log.w(TAG, "✗ Received NACK from " + deviceAddress + " for " + nackKey + ", will retry");
            // ACK timeout will handle retry
        }
    }

    private void sendAckToDevice(String deviceAddress, String parcel) {
        // When we receive data via GATT server, we need to send ACK back via the same server connection
        // using notifications instead of trying to write as a client

        if (gattServer == null) {
            Log.w(TAG, "GATT server not available, cannot send ACK");
            return;
        }

        try {
            // Get the GATT service from our server
            BluetoothGattService service = gattServer.getService(UUID.fromString(GATT_SERVICE_UUID));
            if (service == null) {
                Log.w(TAG, "GATT service not found on server, cannot send ACK");
                return;
            }

            // Get the CONTROL characteristic from our server
            BluetoothGattCharacteristic controlChar = service.getCharacteristic(UUID.fromString(GATT_CHARACTERISTIC_CONTROL_UUID));
            if (controlChar == null) {
                Log.w(TAG, "CONTROL characteristic not found on server, cannot send ACK");
                return;
            }

            // Create ACK message with parcel prefix
            String parcelPrefix = parcel.substring(0, Math.min(5, parcel.length()));
            String ackMessage = "ACK:" + parcelPrefix;

            // Set the characteristic value
            controlChar.setValue(ackMessage.getBytes(StandardCharsets.UTF_8));

            // Get the BluetoothDevice object from the address
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);

            // Send notification to the client
            boolean notifyResult = gattServer.notifyCharacteristicChanged(device, controlChar, false);

            if (notifyResult) {
                Log.i(TAG, "✓ Sent ACK notification to " + deviceAddress + " for parcel: " + parcelPrefix);
            } else {
                Log.w(TAG, "✗ FAILED to send ACK notification to " + deviceAddress + " (client may not be subscribed or notification failed)");
            }

        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException sending ACK to " + deviceAddress + ": " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Error sending ACK to " + deviceAddress + ": " + e.getMessage());
        }
    }

    // Helper method to connect to a discovered device (called by BluetoothListener)
    public void connectToDevice(BluetoothDevice device) {
        String address = device.getAddress();

        if (activeConnections.containsKey(address) || connectingDevices.containsKey(address)) {
            Log.d(TAG, "Already connected or connecting to " + address);
            return;
        }

        if (activeConnections.size() + connectingDevices.size() >= MAX_GATT_CONNECTIONS) {
            Log.w(TAG, "Max GATT connections reached, cannot connect to " + address);
            return;
        }

        try {
            Log.i(TAG, "Connecting to device: " + address);
            BluetoothGatt gatt = device.connectGatt(context, false, gattClientCallback);
            if (gatt != null) {
                connectingDevices.put(address, gatt);  // Add to connecting, not active yet
            }
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException connecting to device: " + e.getMessage());
        }
    }

    private final BluetoothGattCallback gattClientCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Connected to GATT server: " + gatt.getDevice().getAddress());
                try {
                    // Request MTU for larger parcels (service discovery will happen in onMtuChanged)
                    boolean mtuRequested = gatt.requestMtu(GATT_MTU_SIZE);
                    if (!mtuRequested) {
                        // MTU request failed, discover services directly
                        Log.w(TAG, "MTU request failed, discovering services directly");
                        gatt.discoverServices();
                    }
                } catch (SecurityException e) {
                    Log.e(TAG, "SecurityException in onConnectionStateChange: " + e.getMessage());
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                String address = gatt.getDevice().getAddress();
                Log.i(TAG, "Disconnected from GATT server: " + address);
                activeConnections.remove(address);
                connectingDevices.remove(address);  // Also remove from connecting list

                // Clean up callsign-to-MAC mapping for this MAC address
                // (Android randomizes MACs, so old mappings become stale)
                callsignToMacMap.entrySet().removeIf(entry -> entry.getValue().equals(address));

                gatt.close();
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);

            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "MTU changed to " + mtu + " for " + gatt.getDevice().getAddress());
            } else {
                Log.w(TAG, "MTU change failed with status " + status + " for " + gatt.getDevice().getAddress());
            }

            // Now discover services after MTU negotiation completes (or fails)
            try {
                Log.i(TAG, "Discovering services on " + gatt.getDevice().getAddress());
                gatt.discoverServices();
            } catch (SecurityException e) {
                Log.e(TAG, "SecurityException discovering services: " + e.getMessage());
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);

            String address = gatt.getDevice().getAddress();

            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Services discovered on " + address);

                BluetoothGattService service = gatt.getService(UUID.fromString(GATT_SERVICE_UUID));
                if (service != null) {
                    Log.i(TAG, "Found Geogram GATT service on " + address);

                    // Subscribe to CONTROL characteristic for ACK notifications
                    BluetoothGattCharacteristic controlChar = service.getCharacteristic(UUID.fromString(GATT_CHARACTERISTIC_CONTROL_UUID));
                    if (controlChar != null) {
                        try {
                            boolean notifyEnabled = gatt.setCharacteristicNotification(controlChar, true);
                            Log.i(TAG, "Enabled notifications on CONTROL characteristic: " + notifyEnabled);

                            // Also write to CCCD descriptor to enable notifications on the remote device
                            android.bluetooth.BluetoothGattDescriptor descriptor = controlChar.getDescriptor(
                                    UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")); // CCCD UUID
                            if (descriptor != null) {
                                descriptor.setValue(android.bluetooth.BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                boolean descriptorWritten = gatt.writeDescriptor(descriptor);
                                Log.i(TAG, "Wrote CCCD descriptor for notifications: " + descriptorWritten);
                            }
                        } catch (SecurityException e) {
                            Log.e(TAG, "SecurityException enabling notifications: " + e.getMessage());
                        }
                    }

                    // Move from connecting to active - connection is now ready!
                    connectingDevices.remove(address);
                    activeConnections.put(address, gatt);

                    // Ready to send messages
                    handler.post(() -> tryToSendNext());
                } else {
                    Log.w(TAG, "Geogram GATT service not found on " + address);
                    // No Geogram service, disconnect
                    connectingDevices.remove(address);
                    gatt.disconnect();
                }
            } else {
                Log.w(TAG, "Service discovery failed on " + address + " with status " + status);
                // Service discovery failed, disconnect
                connectingDevices.remove(address);
                gatt.disconnect();
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);

            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Characteristic write successful to " + gatt.getDevice().getAddress());
                // Write succeeded, wait for ACK via control characteristic
            } else {
                Log.w(TAG, "Characteristic write failed to " + gatt.getDevice().getAddress() + " with status " + status);
                // Retry will be handled by ACK timeout
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);

            String uuid = characteristic.getUuid().toString();
            if (uuid.equalsIgnoreCase(GATT_CHARACTERISTIC_CONTROL_UUID)) {
                // Received ACK notification from server
                String control = new String(characteristic.getValue(), StandardCharsets.UTF_8);
                String deviceAddress = gatt.getDevice().getAddress();
                Log.i(TAG, "Received notification from " + deviceAddress + ": " + control);
                handleControlMessage(deviceAddress, control);
            }
        }
    };

    // Connection tracking methods

    /**
     * Check if there is an active GATT connection to a specific device.
     * @param deviceAddress MAC address or callsign of the device
     * @return true if active GATT connection exists
     */
    public boolean hasActiveConnection(String deviceAddress) {
        if (deviceAddress == null) return false;

        // Check if it's a direct MAC address match
        if (activeConnections.containsKey(deviceAddress)) {
            return true;
        }

        // Check if it's a callsign - look up the MAC address
        String macAddress = callsignToMacMap.get(deviceAddress);
        if (macAddress != null && activeConnections.containsKey(macAddress)) {
            return true;
        }

        return false;
    }

    /**
     * Get all devices with active GATT connections.
     * @return Set of device addresses (MAC addresses or callsigns)
     */
    public java.util.Set<String> getActiveConnectionAddresses() {
        return new java.util.HashSet<>(activeConnections.keySet());
    }

    /**
     * Get the number of active GATT connections.
     * @return count of active connections
     */
    public int getActiveConnectionCount() {
        return activeConnections.size();
    }

    /**
     * Extract callsign from a GATT parcel and update the callsign-to-MAC mapping.
     * This enables relay sync to find GATT connections by callsign.
     *
     * Handles both beacon formats:
     * - With location: >+CALLSIGN@GEOCODE#MODEL (e.g., >+X1ADK0@RY1A-IUZU#APP-0.4.0)
     * - Without location: >+CALLSIGN#MODEL (e.g., >+X1ADK0#APP-0.4.0)
     *
     * @param parcel Message parcel from GATT
     * @param macAddress MAC address of the device that sent this parcel
     */
    private void extractAndMapCallsign(String parcel, String macAddress) {
        try {
            // Remove leading '>' if present
            String content = parcel.startsWith(">") ? parcel.substring(1) : parcel;

            // Remove leading '+' if present (location beacon indicator)
            content = content.startsWith("+") ? content.substring(1) : content;

            // Extract callsign - handle both formats:
            // With location: "X1ADK0@RY1A-IUZU#APP-0.4.0" - extract before '@'
            // Without location: "X1ADK0#APP-0.4.0" - extract before '#'
            int atIndex = content.indexOf('@');
            int hashIndex = content.indexOf('#');

            String callsign = null;
            if (atIndex > 0) {
                // Beacon has location - extract before '@'
                callsign = content.substring(0, atIndex);
            } else if (hashIndex > 0) {
                // Beacon without location - extract before '#'
                callsign = content.substring(0, hashIndex);
            }

            if (callsign != null && !callsign.isEmpty()) {
                // Update mapping
                callsignToMacMap.put(callsign, macAddress);
                Log.d(TAG, "Mapped callsign " + callsign + " to MAC " + macAddress);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to extract callsign from parcel: " + parcel + " - " + e.getMessage());
        }
    }

    // Helper classes
    private static class QueuedMessage implements Comparable<QueuedMessage> {
        final String parcel;
        final String senderId;
        final int priority;

        QueuedMessage(String parcel, String senderId, int priority) {
            this.parcel = parcel;
            this.senderId = senderId;
            this.priority = priority;
        }

        @Override
        public int compareTo(QueuedMessage other) {
            // Lower priority number = higher priority (sent first)
            return Integer.compare(this.priority, other.priority);
        }
    }

    private static class PendingAck {
        final QueuedMessage message;
        final long timestamp;

        PendingAck(QueuedMessage message, long timestamp) {
            this.message = message;
            this.timestamp = timestamp;
        }
    }
}

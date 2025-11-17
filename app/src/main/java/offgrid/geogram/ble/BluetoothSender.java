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

    // HTTP-over-GATT support
    // Key: requestId, Value: CompletableFuture<HttpResponse>
    private final Map<String, java.util.concurrent.CompletableFuture<offgrid.geogram.p2p.P2PHttpClient.HttpResponse>> pendingHttpRequests = new ConcurrentHashMap<>();
    private static final String HTTP_REQ_PREFIX = "HTTP_REQ:";
    private static final String HTTP_RESP_PREFIX = "HTTP_RESP:";
    private static final int HTTP_TIMEOUT_MS = 30000; // 30 seconds

    // Prepared write buffers for handling long writes (messages > MTU)
    // Key: deviceAddress, Value: accumulated byte buffer
    private final Map<String, java.io.ByteArrayOutputStream> preparedWriteBuffers = new ConcurrentHashMap<>();

    // Multi-parcel HTTP response buffers (for responses split across multiple BT messages)
    // Key: message ID (2-letter code from parcel header like "AB"), Value: BluetoothMessage for assembly
    private final Map<String, BluetoothMessage> pendingMultiParcelHttpResponses = new ConcurrentHashMap<>();

    // Flow control for GATT writes
    // Key: deviceAddress, Value: true if write is pending (waiting for onCharacteristicWrite callback)
    private final Map<String, Boolean> pendingWrites = new ConcurrentHashMap<>();
    // Key: deviceAddress, Value: timestamp of last successful write
    private final Map<String, Long> lastWriteTimestamp = new ConcurrentHashMap<>();
    // Key: deviceAddress, Value: consecutive failure count (for exponential backoff)
    private final Map<String, Integer> writeFailureCount = new ConcurrentHashMap<>();

    // Flow control constants
    private static final long MIN_WRITE_INTERVAL_MS = 100;  // Minimum 100ms between writes to same device
    private static final long BASE_BACKOFF_MS = 200;        // Base delay for exponential backoff
    private static final int MAX_BACKOFF_FAILURES = 5;      // Max failures before capping backoff
    private static final long MAX_BACKOFF_MS = 3200;        // Max backoff delay (200ms * 2^4 = 3.2s)

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

        Log.i(TAG, "[Bluetooth] Bluetooth sender started with GATT server");
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
                Log.e(TAG, "[Bluetooth] Error closing active GATT connection: " + e.getMessage());
            }
        }
        for (BluetoothGatt gatt : connectingDevices.values()) {
            try {
                gatt.disconnect();
                gatt.close();
            } catch (Exception e) {
                Log.e(TAG, "[Bluetooth] Error closing connecting GATT connection: " + e.getMessage());
            }
        }
        activeConnections.clear();
        connectingDevices.clear();
        pendingAcks.clear();

        // Clear flow control tracking
        pendingWrites.clear();
        lastWriteTimestamp.clear();
        writeFailureCount.clear();

        // Stop GATT server
        if (gattServer != null) {
            try {
                gattServer.close();
            } catch (Exception e) {
                Log.e(TAG, "[Bluetooth] Error closing GATT server: " + e.getMessage());
            }
            gattServer = null;
        }

        // Stop advertising
        stopAdvertising();
        messageQueue.clear();
        handler.removeCallbacks(selfAdvertiseTask);

        Log.i(TAG, "[Bluetooth] BluetoothSender stopped");
    }

    public void pause() {
        if (!isPaused) {
            isPaused = true;
            stopAdvertising();
            handler.removeCallbacks(selfAdvertiseTask);
            Log.i(TAG, "[Bluetooth] BluetoothSender paused");
        }
    }

    public void resume() {
        if (isPaused) {
            isPaused = false;
            Log.i(TAG, "[Bluetooth] BluetoothSender resumed");
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
        Log.i(TAG, "[Bluetooth] Queued message: " + msg.getOutput());

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
                Log.d(TAG, "[Bluetooth] → Queued parcel #" + addedCount + ": " + parcel.substring(0, Math.min(30, parcel.length())) + "... Queue size: " + messageQueue.size());
            }
        }

        if (duplicateCount > 0) {
            Log.i(TAG, "[Bluetooth] Queue dedup: added " + addedCount + " parcels, skipped " + duplicateCount + " duplicates. Queue size: " + messageQueue.size());
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

        Log.d(TAG, "[Bluetooth] ← Sending parcel (queue size: " + messageQueue.size() + ", isSending: " + isSending + "): " + queuedMsg.parcel.substring(0, Math.min(30, queuedMsg.parcel.length())));

        if (!hasAdvertisePermission()) {
            Log.i(TAG, "[Bluetooth] Missing BLUETOOTH permissions. Cannot send.");
            return;
        }

        // Try to send via GATT to connected peers first
        if (!activeConnections.isEmpty()) {
            sendViaGatt(queuedMsg);
        } else {
            // No GATT connections - fallback to advertising for discovery
            Log.d(TAG, "[Bluetooth] No GATT connections, advertising parcel for discovery");
            sendViaAdvertising(queuedMsg.parcel);
        }
    }

    private void sendViaGatt(QueuedMessage queuedMsg) {
        isSending = true;
        boolean sent = false;
        boolean anyDeviceReady = false;

        long now = System.currentTimeMillis();

        // Send to all connected peers (with flow control)
        for (Map.Entry<String, BluetoothGatt> entry : activeConnections.entrySet()) {
            String deviceAddress = entry.getKey();
            BluetoothGatt gatt = entry.getValue();

            try {
                // Flow control check 1: Skip if there's already a pending write for this device
                Boolean hasPendingWrite = pendingWrites.get(deviceAddress);
                if (Boolean.TRUE.equals(hasPendingWrite)) {
                    Log.d(TAG, "[Bluetooth] ⏸ Skipping write to " + deviceAddress + " - write already pending");
                    anyDeviceReady = true; // Device exists, just busy
                    continue;
                }

                // Flow control check 2: Rate limiting - ensure minimum interval between writes
                Long lastWrite = lastWriteTimestamp.get(deviceAddress);
                if (lastWrite != null) {
                    long timeSinceLastWrite = now - lastWrite;
                    if (timeSinceLastWrite < MIN_WRITE_INTERVAL_MS) {
                        long waitTime = MIN_WRITE_INTERVAL_MS - timeSinceLastWrite;
                        Log.d(TAG, "[Bluetooth] ⏸ Rate limiting - will retry in " + waitTime + "ms");
                        // Schedule retry after minimum interval
                        handler.postDelayed(() -> {
                            isSending = false;
                            tryToSendNext();
                        }, waitTime);
                        anyDeviceReady = true;
                        continue;
                    }
                }

                // Flow control check 3: Exponential backoff for devices with recent failures
                Integer failures = writeFailureCount.get(deviceAddress);
                if (failures != null && failures > 0) {
                    // Calculate backoff delay: BASE_BACKOFF_MS * 2^failures, capped at MAX_BACKOFF_MS
                    long backoffDelay = Math.min(BASE_BACKOFF_MS * (1L << Math.min(failures, MAX_BACKOFF_FAILURES)), MAX_BACKOFF_MS);

                    Long lastFailTime = lastWriteTimestamp.get(deviceAddress);
                    if (lastFailTime != null && (now - lastFailTime) < backoffDelay) {
                        long waitTime = backoffDelay - (now - lastFailTime);
                        Log.d(TAG, "[Bluetooth] ⏸ Backing off " + deviceAddress + " (failures: " + failures + ", wait: " + waitTime + "ms)");
                        // Schedule retry after backoff
                        handler.postDelayed(() -> {
                            isSending = false;
                            tryToSendNext();
                        }, waitTime);
                        anyDeviceReady = true;
                        continue;
                    }
                }

                BluetoothGattService service = gatt.getService(UUID.fromString(GATT_SERVICE_UUID));
                if (service == null) {
                    Log.w(TAG, "[Bluetooth] GATT service not found on device " + deviceAddress);
                    continue;
                }

                BluetoothGattCharacteristic rxChar = service.getCharacteristic(UUID.fromString(GATT_CHARACTERISTIC_RX_UUID));
                if (rxChar == null) {
                    Log.w(TAG, "[Bluetooth] RX characteristic not found on device " + deviceAddress);
                    continue;
                }

                // Write parcel to RX characteristic
                rxChar.setValue(queuedMsg.parcel.getBytes(StandardCharsets.UTF_8));
                Log.d(TAG, "[Bluetooth] ⚡ Attempting GATT write to " + deviceAddress + " (" + queuedMsg.parcel.length() + " bytes): " + queuedMsg.parcel.substring(0, Math.min(20, queuedMsg.parcel.length())));

                // Mark write as pending BEFORE attempting write
                pendingWrites.put(deviceAddress, true);
                boolean writeResult = gatt.writeCharacteristic(rxChar);

                if (writeResult) {
                    sent = true;
                    anyDeviceReady = true;

                    // Update timestamp for rate limiting
                    lastWriteTimestamp.put(deviceAddress, now);

                    // Set up ACK timeout
                    String ackKey = deviceAddress + ":" + queuedMsg.parcel.substring(0, Math.min(5, queuedMsg.parcel.length()));
                    PendingAck pendingAck = new PendingAck(queuedMsg, System.currentTimeMillis());
                    pendingAcks.put(ackKey, pendingAck);
                    Log.d(TAG, "[Bluetooth] ⏱ Waiting for ACK: " + ackKey + " (pending ACKs: " + pendingAcks.size() + ")");

                    // Schedule ACK timeout
                    handler.postDelayed(() -> {
                        if (pendingAcks.containsKey(ackKey)) {
                            Log.w(TAG, "[Bluetooth] ⏰ ACK TIMEOUT for " + ackKey + " after " + GATT_ACK_TIMEOUT_MS + "ms - will retry (queue size: " + messageQueue.size() + ")");
                            pendingAcks.remove(ackKey);
                            // Clear pending write flag so we can retry
                            pendingWrites.remove(deviceAddress);
                            isSending = false;
                            tryToSendNext(); // Retry
                        }
                    }, GATT_ACK_TIMEOUT_MS);

                    Log.i(TAG, "[Bluetooth] ✓ GATT write queued to " + deviceAddress + ": " + queuedMsg.parcel.substring(0, Math.min(20, queuedMsg.parcel.length())) + "...");
                } else {
                    // Write failed to queue - clear pending flag and increment failure counter
                    pendingWrites.remove(deviceAddress);
                    int failCount = writeFailureCount.getOrDefault(deviceAddress, 0) + 1;
                    writeFailureCount.put(deviceAddress, failCount);
                    lastWriteTimestamp.put(deviceAddress, now);  // Record failure time for backoff
                    Log.w(TAG, "[Bluetooth] ✗ GATT write FAILED to " + deviceAddress + " (failures: " + failCount + ")");
                }

            } catch (Exception e) {
                // Exception during write - clear pending flag
                pendingWrites.remove(deviceAddress);
                int failCount = writeFailureCount.getOrDefault(deviceAddress, 0) + 1;
                writeFailureCount.put(deviceAddress, failCount);
                Log.e(TAG, "[Bluetooth] Error sending via GATT to " + deviceAddress + ": " + e.getMessage());
            }
        }

        if (sent) {
            // Wait for ACK before removing from queue
            // ACK will be handled in onCharacteristicWrite callback
        } else if (anyDeviceReady) {
            // Devices exist but are rate-limited or have pending writes
            // Retry will be scheduled by the flow control checks above
            isSending = false;
        } else {
            // No successful GATT writes and no devices ready, fallback to advertising
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
                Log.i(TAG, "[Bluetooth] Started advertising parcel: " + parcel.substring(0, Math.min(20, parcel.length())));

                handler.postDelayed(() -> {
                    stopAdvertising();
                    messageQueue.poll(); // Remove from queue
                    isSending = false;
                    tryToSendNext(); // Send next parcel
                }, advertiseDurationMillis);
            }

            @Override
            public void onStartFailure(int errorCode) {
                Log.w(TAG, "[Bluetooth] Failed to advertise. Error code: " + errorCode);
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
            Log.e(TAG, "[Bluetooth] SecurityException while advertising: " + e.getMessage());
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
                Log.d(TAG, "[Bluetooth] Self-beacon advertising");
            }

            @Override
            public void onStartFailure(int errorCode) {
                Log.w(TAG, "[Bluetooth] Self-beacon advertising failed: " + errorCode);
            }
        };

        try {
            advertiser.startAdvertising(settings, data, callback);
        } catch (SecurityException e) {
            Log.e(TAG, "[Bluetooth] SecurityException while self-advertising: " + e.getMessage());
        }
    }

    public void setSelfMessage(String message) {
        this.selfMessage = message;
        if (isRunning && !isPaused) {
            handler.removeCallbacks(selfAdvertiseTask);
            handler.post(selfAdvertiseTask);
            Log.i(TAG, "[Bluetooth] Self message set to: " + message);
        }
    }

    public void setSelfIntervalSeconds(int seconds) {
        if (seconds <= 0) return;
        selfIntervalSeconds = seconds;
        Log.i(TAG, "[Bluetooth] Self-advertise interval set to: " + seconds + " seconds");

        if (isRunning && !isPaused && selfMessage != null) {
            handler.removeCallbacks(selfAdvertiseTask);
            handler.post(selfAdvertiseTask);
        }
    }

    /**
     * Trigger an immediate self-advertisement/ping
     * (useful when user manually refreshes the device list)
     */
    public void triggerImmediatePing() {
        if (isRunning && !isPaused && selfMessage != null) {
            Log.i(TAG, "[Bluetooth] Triggering immediate BLE self-advertise (user-requested)");
            handler.post(selfAdvertiseTask);
        } else {
            Log.w(TAG, "[Bluetooth] Cannot trigger immediate ping - sender not running or no self message set");
        }
    }

    private void stopAdvertising() {
        if (advertiser != null && advertiseCallback != null) {
            if (!hasAdvertisePermission()) {
                Log.w(TAG, "[Bluetooth] Missing BLUETOOTH_ADVERTISE permission. Cannot stop advertiser.");
                return;
            }
            try {
                advertiser.stopAdvertising(advertiseCallback);
            } catch (SecurityException e) {
                Log.w(TAG, "[Bluetooth] SecurityException while stopping advertiser: " + e.getMessage());
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
            Log.i(TAG, "[Bluetooth] GATT server already running");
            return;
        }

        try {
            gattServer = bluetoothManager.openGattServer(context, gattServerCallback);

            if (gattServer == null) {
                Log.e(TAG, "[Bluetooth] Failed to create GATT server");
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
                Log.i(TAG, "[Bluetooth] GATT server started with service " + GATT_SERVICE_UUID);
            } else {
                Log.e(TAG, "[Bluetooth] Failed to add GATT service");
            }

        } catch (SecurityException e) {
            Log.e(TAG, "[Bluetooth] SecurityException starting GATT server: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "[Bluetooth] Error starting GATT server: " + e.getMessage());
        }
    }

    private final BluetoothGattServerCallback gattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            super.onConnectionStateChange(device, status, newState);

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "[Bluetooth] GATT client connected: " + device.getAddress());
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
                Log.i(TAG, "[Bluetooth] GATT client disconnected: " + device.getAddress());
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
                // Check if this is a prepared write (long write for messages > MTU)
                if (preparedWrite) {
                    // Accumulate chunks in buffer
                    String deviceKey = device.getAddress();
                    java.io.ByteArrayOutputStream buffer = preparedWriteBuffers.get(deviceKey);
                    if (buffer == null) {
                        buffer = new java.io.ByteArrayOutputStream();
                        preparedWriteBuffers.put(deviceKey, buffer);
                    }
                    try {
                        buffer.write(value);
                        Log.d(TAG, "[Bluetooth] 📥 Prepared write chunk from " + device.getAddress() + " offset=" + offset + " size=" + value.length + " total=" + buffer.size());
                    } catch (java.io.IOException e) {
                        Log.e(TAG, "[Bluetooth] Error accumulating prepared write: " + e.getMessage());
                    }

                    // Send GATT protocol response for prepared write
                    if (responseNeeded) {
                        try {
                            gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
                        } catch (SecurityException e) {
                            Log.e(TAG, "[Bluetooth] SecurityException sending GATT response: " + e.getMessage());
                        }
                    }
                    return; // Wait for onExecuteWrite()
                }

                // Regular single write (not prepared) - process immediately
                String parcel = new String(value, StandardCharsets.UTF_8);
                Log.i(TAG, "[Bluetooth] 📥 Received parcel via GATT from " + device.getAddress() + " (" + parcel.length() + " bytes): " + parcel.substring(0, Math.min(20, parcel.length())));

                // Extract callsign from parcel and update mapping for relay sync lookup
                // Parcel format: >+CALLSIGN@GEOCODE#MODEL or >CALLSIGN@GEOCODE#MODEL
                extractAndMapCallsign(parcel, device.getAddress());

                // Remove '>' prefix if present
                String content = parcel.startsWith(">") ? parcel.substring(1) : parcel;

                // Check if this is an HTTP request or response
                if (content.startsWith(HTTP_REQ_PREFIX)) {
                    // Handle HTTP request over GATT (always single parcel)
                    Log.d(TAG, "[Bluetooth] 📥 Routing to HTTP request handler: " + content.substring(0, Math.min(50, content.length())));
                    handleIncomingHttpRequest(content, device.getAddress());
                } else if (content.startsWith(HTTP_RESP_PREFIX)) {
                    // Handle HTTP response over GATT (may be single or start of multi-parcel)
                    Log.d(TAG, "[Bluetooth] 📥 Routing to HTTP response handler: " + content.substring(0, Math.min(50, content.length())));
                    handleIncomingHttpResponse(content);
                } else if (content.contains(":") && content.length() >= 4) {
                    // Check if this is a multi-parcel message (format: XX00:... or XX01:... with zero-padded 2-digit parcel numbers)
                    String parcelId = content.substring(0, Math.min(4, content.length()));
                    if (parcelId.matches("[A-Z]{2}\\d{2}")) {
                        // This is a multi-parcel message - check if it's for an HTTP response
                        String messageId = parcelId.substring(0, 2);
                        handleMultiParcelMessage(content, messageId);
                    } else {
                        // Regular message parcel - process through BluetoothListener
                        Log.d(TAG, "[Bluetooth] 📥 Routing to regular message handler");
                        BluetoothListener.getInstance(context).handleGattParcel(parcel, device.getAddress());
                    }
                } else {
                    // Regular message parcel - process through BluetoothListener
                    Log.d(TAG, "[Bluetooth] 📥 Routing to regular message handler");
                    BluetoothListener.getInstance(context).handleGattParcel(parcel, device.getAddress());
                }

                // Send GATT protocol response
                if (responseNeeded) {
                    try {
                        gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
                        Log.d(TAG, "[Bluetooth] ✓ Sent GATT protocol response");
                    } catch (SecurityException e) {
                        Log.e(TAG, "[Bluetooth] SecurityException sending GATT response: " + e.getMessage());
                    }
                }

                // Send application-level ACK via CONTROL characteristic
                Log.d(TAG, "[Bluetooth] 📤 Sending ACK for parcel: " + parcel.substring(0, Math.min(10, parcel.length())));
                sendAckToDevice(device.getAddress(), parcel);

            } else if (uuid.equalsIgnoreCase(GATT_CHARACTERISTIC_CONTROL_UUID)) {
                // Received ACK/NACK
                String control = new String(value, StandardCharsets.UTF_8);
                handleControlMessage(device.getAddress(), control);

                if (responseNeeded) {
                    try {
                        gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
                    } catch (SecurityException e) {
                        Log.e(TAG, "[Bluetooth] SecurityException sending GATT response: " + e.getMessage());
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
                Log.i(TAG, "[Bluetooth] Client " + device.getAddress() + (enabled ? " subscribed to" : " unsubscribed from") + " notifications");
            }

            if (responseNeeded) {
                try {
                    gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
                } catch (SecurityException e) {
                    Log.e(TAG, "[Bluetooth] SecurityException sending GATT response for descriptor: " + e.getMessage());
                }
            }
        }

        @Override
        public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {
            super.onExecuteWrite(device, requestId, execute);

            String deviceKey = device.getAddress();
            Log.d(TAG, "[Bluetooth] 📝 onExecuteWrite from " + deviceKey + " execute=" + execute);

            if (execute) {
                // Commit the prepared write - process accumulated buffer
                java.io.ByteArrayOutputStream buffer = preparedWriteBuffers.remove(deviceKey);
                if (buffer != null) {
                    byte[] completeData = buffer.toByteArray();
                    String parcel = new String(completeData, StandardCharsets.UTF_8);
                    Log.i(TAG, "[Bluetooth] 📥 Completed prepared write from " + deviceKey + " (" + parcel.length() + " bytes total): " + parcel.substring(0, Math.min(20, parcel.length())));

                    // Extract callsign from parcel and update mapping for relay sync lookup
                    extractAndMapCallsign(parcel, deviceKey);

                    // Remove '>' prefix if present
                    String content = parcel.startsWith(">") ? parcel.substring(1) : parcel;

                    // Check if this is an HTTP request or response
                    if (content.startsWith(HTTP_REQ_PREFIX)) {
                        // Handle HTTP request over GATT (always single parcel)
                        Log.d(TAG, "[Bluetooth] 📥 Routing to HTTP request handler: " + content.substring(0, Math.min(50, content.length())));
                        handleIncomingHttpRequest(content, deviceKey);
                    } else if (content.startsWith(HTTP_RESP_PREFIX)) {
                        // Handle HTTP response over GATT (may be single or start of multi-parcel)
                        Log.d(TAG, "[Bluetooth] 📥 Routing to HTTP response handler: " + content.substring(0, Math.min(50, content.length())));
                        handleIncomingHttpResponse(content);
                    } else if (content.contains(":") && content.length() >= 4) {
                        // Check if this is a multi-parcel message (format: XX00:... or XX01:... with zero-padded 2-digit parcel numbers)
                        String parcelId = content.substring(0, Math.min(4, content.length()));
                        if (parcelId.matches("[A-Z]{2}\\d{2}")) {
                            // This is a multi-parcel message - check if it's for an HTTP response
                            String messageId = parcelId.substring(0, 2);
                            handleMultiParcelMessage(content, messageId);
                        } else {
                            // Regular message parcel - process through BluetoothListener
                            Log.d(TAG, "[Bluetooth] 📥 Routing to regular message handler");
                            BluetoothListener.getInstance(context).handleGattParcel(parcel, deviceKey);
                        }
                    } else {
                        // Regular message parcel - process through BluetoothListener
                        Log.d(TAG, "[Bluetooth] 📥 Routing to regular message handler");
                        BluetoothListener.getInstance(context).handleGattParcel(parcel, deviceKey);
                    }

                    // Send application-level ACK via CONTROL characteristic
                    Log.d(TAG, "[Bluetooth] 📤 Sending ACK for prepared write: " + parcel.substring(0, Math.min(10, parcel.length())));
                    sendAckToDevice(deviceKey, parcel);
                }
            } else {
                // Abort the prepared write - clear buffer
                preparedWriteBuffers.remove(deviceKey);
                Log.d(TAG, "[Bluetooth] ⚠ Prepared write aborted for " + deviceKey);
            }

            // Send GATT protocol response
            try {
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null);
                Log.d(TAG, "[Bluetooth] ✓ Sent GATT execute write response");
            } catch (SecurityException e) {
                Log.e(TAG, "[Bluetooth] SecurityException sending execute write response: " + e.getMessage());
            }
        }
    };

    private void handleControlMessage(String deviceAddress, String control) {
        if (control.startsWith("ACK:")) {
            String ackKey = deviceAddress + ":" + control.substring(4);
            if (pendingAcks.containsKey(ackKey)) {
                Log.i(TAG, "[Bluetooth] ✓ Received ACK from " + deviceAddress + " for " + ackKey + " (pending: " + pendingAcks.size() + ", queue: " + messageQueue.size() + ")");
                pendingAcks.remove(ackKey);

                // Extract parcel prefix from ackKey (e.g., ">ZA0:" from "7E:76:02:29:6B:B9:>ZA0:")
                String parcelPrefix = control.substring(4); // e.g., ">ZA0:"

                // Check if any other devices are still waiting for ACKs for this same parcel
                boolean otherDevicesWaiting = false;
                for (String key : pendingAcks.keySet()) {
                    if (key.endsWith(parcelPrefix)) {
                        otherDevicesWaiting = true;
                        Log.d(TAG, "[Bluetooth] ⏳ Still waiting for ACK from other device: " + key);
                        break;
                    }
                }

                // Only remove from queue when ALL devices have ACKed this parcel
                if (!otherDevicesWaiting) {
                    QueuedMessage completed = messageQueue.poll();
                    Log.d(TAG, "[Bluetooth] ← Completed parcel, removed from queue. New queue size: " + messageQueue.size());
                    isSending = false;
                    handler.post(() -> tryToSendNext());
                } else {
                    Log.d(TAG, "[Bluetooth] ← Parcel ACKed by " + deviceAddress + ", but waiting for other devices before removing from queue");
                }
            } else {
                Log.d(TAG, "[Bluetooth] ⚠ Received unexpected ACK (already processed?): " + ackKey);
            }
        } else if (control.startsWith("NACK:")) {
            String nackKey = deviceAddress + ":" + control.substring(5);
            Log.w(TAG, "[Bluetooth] ✗ Received NACK from " + deviceAddress + " for " + nackKey + ", will retry");
            // ACK timeout will handle retry
        }
    }

    private void sendAckToDevice(String deviceAddress, String parcel) {
        // When we receive data via GATT server, we need to send ACK back via the same server connection
        // using notifications instead of trying to write as a client

        if (gattServer == null) {
            Log.w(TAG, "[Bluetooth] GATT server not available, cannot send ACK");
            return;
        }

        try {
            // Get the GATT service from our server
            BluetoothGattService service = gattServer.getService(UUID.fromString(GATT_SERVICE_UUID));
            if (service == null) {
                Log.w(TAG, "[Bluetooth] GATT service not found on server, cannot send ACK");
                return;
            }

            // Get the CONTROL characteristic from our server
            BluetoothGattCharacteristic controlChar = service.getCharacteristic(UUID.fromString(GATT_CHARACTERISTIC_CONTROL_UUID));
            if (controlChar == null) {
                Log.w(TAG, "[Bluetooth] CONTROL characteristic not found on server, cannot send ACK");
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
                Log.i(TAG, "[Bluetooth] ✓ Sent ACK notification to " + deviceAddress + " for parcel: " + parcelPrefix);
            } else {
                Log.w(TAG, "[Bluetooth] ✗ FAILED to send ACK notification to " + deviceAddress + " (client may not be subscribed or notification failed)");
            }

        } catch (SecurityException e) {
            Log.e(TAG, "[Bluetooth] SecurityException sending ACK to " + deviceAddress + ": " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "[Bluetooth] Error sending ACK to " + deviceAddress + ": " + e.getMessage());
        }
    }

    // Helper method to connect to a discovered device (called by BluetoothListener)
    public void connectToDevice(BluetoothDevice device) {
        String address = device.getAddress();

        if (activeConnections.containsKey(address) || connectingDevices.containsKey(address)) {
            Log.d(TAG, "[Bluetooth] Already connected or connecting to " + address);
            return;
        }

        if (activeConnections.size() + connectingDevices.size() >= MAX_GATT_CONNECTIONS) {
            Log.w(TAG, "[Bluetooth] Max GATT connections reached, cannot connect to " + address);
            return;
        }

        try {
            Log.i(TAG, "[Bluetooth] Connecting to device: " + address);
            BluetoothGatt gatt = device.connectGatt(context, false, gattClientCallback);
            if (gatt != null) {
                connectingDevices.put(address, gatt);  // Add to connecting, not active yet
            }
        } catch (SecurityException e) {
            Log.e(TAG, "[Bluetooth] SecurityException connecting to device: " + e.getMessage());
        }
    }

    private final BluetoothGattCallback gattClientCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "[Bluetooth] Connected to GATT server: " + gatt.getDevice().getAddress());
                try {
                    // Request MTU for larger parcels (service discovery will happen in onMtuChanged)
                    boolean mtuRequested = gatt.requestMtu(GATT_MTU_SIZE);
                    if (!mtuRequested) {
                        // MTU request failed, discover services directly
                        Log.w(TAG, "[Bluetooth] MTU request failed, discovering services directly");
                        gatt.discoverServices();
                    }
                } catch (SecurityException e) {
                    Log.e(TAG, "[Bluetooth] SecurityException in onConnectionStateChange: " + e.getMessage());
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                String address = gatt.getDevice().getAddress();
                Log.i(TAG, "[Bluetooth] Disconnected from GATT server: " + address);
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
                Log.i(TAG, "[Bluetooth] MTU changed to " + mtu + " for " + gatt.getDevice().getAddress());
            } else {
                Log.w(TAG, "[Bluetooth] MTU change failed with status " + status + " for " + gatt.getDevice().getAddress());
            }

            // Now discover services after MTU negotiation completes (or fails)
            try {
                Log.i(TAG, "[Bluetooth] Discovering services on " + gatt.getDevice().getAddress());
                gatt.discoverServices();
            } catch (SecurityException e) {
                Log.e(TAG, "[Bluetooth] SecurityException discovering services: " + e.getMessage());
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);

            String address = gatt.getDevice().getAddress();

            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "[Bluetooth] Services discovered on " + address);

                BluetoothGattService service = gatt.getService(UUID.fromString(GATT_SERVICE_UUID));
                if (service != null) {
                    Log.i(TAG, "[Bluetooth] Found Geogram GATT service on " + address);

                    // Subscribe to CONTROL characteristic for ACK notifications
                    BluetoothGattCharacteristic controlChar = service.getCharacteristic(UUID.fromString(GATT_CHARACTERISTIC_CONTROL_UUID));
                    if (controlChar != null) {
                        try {
                            boolean notifyEnabled = gatt.setCharacteristicNotification(controlChar, true);
                            Log.i(TAG, "[Bluetooth] Enabled notifications on CONTROL characteristic: " + notifyEnabled);

                            // Also write to CCCD descriptor to enable notifications on the remote device
                            android.bluetooth.BluetoothGattDescriptor descriptor = controlChar.getDescriptor(
                                    UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")); // CCCD UUID
                            if (descriptor != null) {
                                descriptor.setValue(android.bluetooth.BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                boolean descriptorWritten = gatt.writeDescriptor(descriptor);
                                Log.i(TAG, "[Bluetooth] Wrote CCCD descriptor for notifications: " + descriptorWritten);
                            }
                        } catch (SecurityException e) {
                            Log.e(TAG, "[Bluetooth] SecurityException enabling notifications: " + e.getMessage());
                        }
                    }

                    // Move from connecting to active - connection is now ready!
                    connectingDevices.remove(address);
                    activeConnections.put(address, gatt);

                    // Ready to send messages
                    handler.post(() -> tryToSendNext());
                } else {
                    Log.w(TAG, "[Bluetooth] Geogram GATT service not found on " + address);
                    // Service might not be ready yet (race condition during startup)
                    // Keep connection open and let BluetoothListener retry later
                    // Close this connection attempt but allow future retries
                    connectingDevices.remove(address);
                    try {
                        gatt.disconnect();
                        gatt.close();
                    } catch (Exception e) {
                        Log.e(TAG, "[Bluetooth] Error closing GATT: " + e.getMessage());
                    }
                }
            } else {
                Log.w(TAG, "[Bluetooth] Service discovery failed on " + address + " with status " + status);
                // Service discovery failed, disconnect
                connectingDevices.remove(address);
                gatt.disconnect();
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);

            String deviceAddress = gatt.getDevice().getAddress();

            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "[Bluetooth] ✓ Characteristic write successful to " + deviceAddress);

                // Clear pending write flag - this device can now send again
                pendingWrites.remove(deviceAddress);

                // Reset failure counter on success
                writeFailureCount.remove(deviceAddress);

                // Trigger next send attempt (respecting rate limits)
                handler.postDelayed(() -> {
                    if (isRunning && !isSending && !messageQueue.isEmpty()) {
                        tryToSendNext();
                    }
                }, MIN_WRITE_INTERVAL_MS);

            } else {
                Log.w(TAG, "[Bluetooth] ✗ Characteristic write failed to " + deviceAddress + " with status " + status);

                // Clear pending write flag so we can retry
                pendingWrites.remove(deviceAddress);

                // Increment failure counter for exponential backoff
                int failCount = writeFailureCount.getOrDefault(deviceAddress, 0) + 1;
                writeFailureCount.put(deviceAddress, failCount);

                // Update timestamp for backoff calculation
                lastWriteTimestamp.put(deviceAddress, System.currentTimeMillis());

                // Calculate backoff delay
                long backoffDelay = Math.min(BASE_BACKOFF_MS * (1L << Math.min(failCount, MAX_BACKOFF_FAILURES)), MAX_BACKOFF_MS);
                Log.d(TAG, "[Bluetooth] Will retry after " + backoffDelay + "ms (failures: " + failCount + ")");

                // Schedule retry with exponential backoff
                handler.postDelayed(() -> {
                    if (isRunning && !isSending && !messageQueue.isEmpty()) {
                        tryToSendNext();
                    }
                }, backoffDelay);
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
                Log.i(TAG, "[Bluetooth] Received notification from " + deviceAddress + ": " + control);
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
     * Get MAC address from deviceId (which might be a callsign or MAC address)
     * @param deviceId Callsign or MAC address
     * @return MAC address, or null if not found
     */
    public String getMacAddress(String deviceId) {
        if (deviceId == null) return null;

        // Check if it's already a MAC address (format: XX:XX:XX:XX:XX:XX)
        if (deviceId.matches("([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}")) {
            return deviceId;
        }

        // Otherwise, look up callsign in map
        return callsignToMacMap.get(deviceId);
    }

    /**
     * Get callsign from MAC address (reverse lookup)
     * @param macAddress MAC address
     * @return Callsign, or null if not found
     */
    public String getCallsignFromMac(String macAddress) {
        if (macAddress == null) return null;

        // Search through the callsign->MAC map to find the callsign
        for (Map.Entry<String, String> entry : callsignToMacMap.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(macAddress)) {
                return entry.getKey();
            }
        }

        return null;
    }

    /**
     * Get BluetoothDevice from deviceId (which might be a callsign or MAC address)
     * @param deviceId Callsign or MAC address
     * @return BluetoothDevice, or null if not found or invalid
     */
    public BluetoothDevice getBluetoothDevice(String deviceId) {
        String macAddress = getMacAddress(deviceId);
        if (macAddress == null) {
            return null;
        }

        try {
            return bluetoothAdapter.getRemoteDevice(macAddress);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "[Bluetooth] Invalid MAC address: " + macAddress, e);
            return null;
        }
    }

    /**
     * Extract callsign from a beacon/parcel and update the callsign-to-MAC mapping.
     * This enables relay sync and HTTP-over-GATT to find devices by callsign.
     *
     * Handles both beacon formats:
     * - With location: >+CALLSIGN@GEOCODE#MODEL (e.g., >+X1ADK0@RY1A-IUZU#APP-0.4.0)
     * - Without location: >+CALLSIGN#MODEL (e.g., >+X1ADK0#APP-0.4.0)
     *
     * @param parcel Beacon or message parcel
     * @param macAddress MAC address of the device that sent this beacon/parcel
     */
    public void extractAndMapCallsign(String parcel, String macAddress) {
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
                Log.d(TAG, "[Bluetooth] Mapped callsign " + callsign + " to MAC " + macAddress);
            }
        } catch (Exception e) {
            Log.w(TAG, "[Bluetooth] Failed to extract callsign from parcel: " + parcel + " - " + e.getMessage());
        }
    }

    // HTTP-over-GATT Methods

    /**
     * Send a message to a specific device (callsign or MAC address)
     */
    private void sendMessageToDevice(String message, String deviceId) {
        // Get our callsign
        String callsign = Central.getInstance().getSettings().getCallsign();
        if (callsign == null || callsign.isEmpty()) {
            callsign = Central.getInstance().getSettings().getIdDevice();
        }

        // Create message directed to specific device
        // Use multi-parcel mode (singleMessage=false) to handle large responses (e.g., tree-data.js files)
        // BluetoothMessage will automatically split into parcels with headers if message exceeds MTU
        // Small messages will still fit in one parcel, large ones will be split with checksum validation
        boolean singleMessage = (message.length() < 400); // Use single message for small responses only
        BluetoothMessage msg = new BluetoothMessage(callsign, deviceId, message, singleMessage);
        sendMessage(msg);
    }

    /**
     * Send HTTP request over GATT to a device
     * @param deviceId Callsign or MAC address of target device
     * @param method HTTP method (GET, POST, etc.)
     * @param path Request path
     * @param timeoutMs Timeout in milliseconds
     * @return CompletableFuture that completes with HttpResponse
     */
    public java.util.concurrent.CompletableFuture<offgrid.geogram.p2p.P2PHttpClient.HttpResponse> sendHttpRequestOverGatt(
            String deviceId, String method, String path, int timeoutMs) {

        java.util.concurrent.CompletableFuture<offgrid.geogram.p2p.P2PHttpClient.HttpResponse> future =
            new java.util.concurrent.CompletableFuture<>();

        // Check if we have an active GATT connection to this device
        if (!hasActiveConnection(deviceId)) {
            Log.w(TAG, "[Bluetooth] HTTP-over-GATT: No active GATT connection to " + deviceId + " - request will likely fail");
            // Don't fail immediately - queued messages might still be delivered if connection is established soon
        } else {
            Log.d(TAG, "[Bluetooth] HTTP-over-GATT: Active GATT connection confirmed to " + deviceId);
        }

        // Generate unique request ID
        String requestId = java.util.UUID.randomUUID().toString().substring(0, 8);

        // Get our callsign to include in request (so receiver can send response)
        String callsign = Central.getInstance().getSettings().getCallsign();
        if (callsign == null || callsign.isEmpty()) {
            callsign = Central.getInstance().getSettings().getIdDevice();
        }

        // Format: HTTP_REQ:{requestId}:{method}:{path}:{senderCallsign}
        String httpRequest = HTTP_REQ_PREFIX + requestId + ":" + method + ":" + path + ":" + callsign;

        // Store pending request
        pendingHttpRequests.put(requestId, future);

        // Send request as GATT message to specific device
        sendMessageToDevice(httpRequest, deviceId);

        Log.d(TAG, "[Bluetooth] HTTP-over-GATT: Sent " + method + " " + path + " to " + deviceId + " (requestId: " + requestId + ")");

        // Set timeout
        handler.postDelayed(() -> {
            if (pendingHttpRequests.remove(requestId) != null) {
                Log.w(TAG, "[Bluetooth] HTTP-over-GATT: Request " + requestId + " timed out after " + timeoutMs + "ms (no response received)");
                future.completeExceptionally(new java.util.concurrent.TimeoutException(
                    "HTTP-over-GATT request timeout after " + timeoutMs + "ms"));
            }
        }, timeoutMs);

        return future;
    }

    /**
     * Handle incoming HTTP request from remote device
     */
    private void handleIncomingHttpRequest(String parcel, String deviceAddress) {
        try {
            // Format: HTTP_REQ:{requestId}:{method}:{path}:{senderCallsign}
            String content = parcel.substring(HTTP_REQ_PREFIX.length());
            String[] parts = content.split(":", 4);

            if (parts.length < 4) {
                Log.e(TAG, "[Bluetooth] Invalid HTTP request format (expected 4 parts): " + parcel);
                return;
            }

            String requestId = parts[0];
            String method = parts[1];
            String path = parts[2];
            String senderCallsign = parts[3]; // Sender's callsign is now included in the request!

            Log.d(TAG, "[Bluetooth] HTTP-over-GATT: Received " + method + " " + path + " from " + senderCallsign + " (" + deviceAddress + ") (requestId: " + requestId + ")");

            // Execute HTTP request against local server in background
            new Thread(() -> {
                try {
                    offgrid.geogram.settings.ConfigManager configManager =
                        offgrid.geogram.settings.ConfigManager.getInstance(context);
                    int port = configManager.getConfig().getHttpApiPort();
                    String url = "http://localhost:" + port + path;

                    Log.d(TAG, "[Bluetooth] HTTP-over-GATT: Executing local request: " + url);

                    java.net.URL urlObj = new java.net.URL(url);
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) urlObj.openConnection();
                    conn.setRequestMethod(method);
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(30000);

                    int statusCode = conn.getResponseCode();
                    String contentType = conn.getContentType();
                    String responseBody;
                    String encoding = "text";

                    Log.d(TAG, "[Bluetooth] HTTP-over-GATT: Content-Type for " + path + ": " + contentType);

                    // Check if response is binary (image, video, etc.)
                    // IMPORTANT: JavaScript, JSON, HTML, CSS, and other text files should NEVER be base64 encoded
                    // ALWAYS check file extension FIRST, regardless of content-type
                    boolean isBinary = false;

                    // Priority 1: Check file extension (most reliable for static files)
                    String lowerPath = path.toLowerCase();
                    if (lowerPath.endsWith(".js") || lowerPath.endsWith(".json") ||
                        lowerPath.endsWith(".html") || lowerPath.endsWith(".htm") ||
                        lowerPath.endsWith(".css") || lowerPath.endsWith(".txt") ||
                        lowerPath.endsWith(".xml") || lowerPath.endsWith(".svg") ||
                        lowerPath.endsWith(".csv")) {
                        // Text file extensions - always send as text
                        isBinary = false;
                        Log.d(TAG, "[Bluetooth] HTTP-over-GATT: Text file detected by extension: " + path);
                    }
                    else if (lowerPath.endsWith(".jpg") || lowerPath.endsWith(".jpeg") ||
                             lowerPath.endsWith(".png") || lowerPath.endsWith(".gif") ||
                             lowerPath.endsWith(".webp") || lowerPath.endsWith(".bmp") ||
                             lowerPath.endsWith(".ico") ||
                             lowerPath.endsWith(".mp4") || lowerPath.endsWith(".webm") ||
                             lowerPath.endsWith(".mp3") || lowerPath.endsWith(".wav") ||
                             lowerPath.endsWith(".pdf") || lowerPath.endsWith(".zip")) {
                        // Binary file extensions - always send as base64
                        isBinary = true;
                        Log.d(TAG, "[Bluetooth] HTTP-over-GATT: Binary file detected by extension: " + path);
                    }
                    // Priority 2: Check content-type (if extension didn't match)
                    else if (contentType != null) {
                        // Explicitly check for text types first (always send as text)
                        if (contentType.startsWith("text/") ||
                            contentType.startsWith("application/javascript") ||
                            contentType.startsWith("application/json") ||
                            contentType.startsWith("application/xml")) {
                            isBinary = false;
                            Log.d(TAG, "[Bluetooth] HTTP-over-GATT: Text file detected by content-type: " + contentType);
                        }
                        // Only encode as base64 for actual binary types
                        else if (contentType.startsWith("image/") ||
                                 contentType.startsWith("video/") ||
                                 contentType.startsWith("audio/") ||
                                 contentType.startsWith("application/octet-stream") ||
                                 contentType.startsWith("application/pdf")) {
                            isBinary = true;
                            Log.d(TAG, "[Bluetooth] HTTP-over-GATT: Binary file detected by content-type: " + contentType);
                        }
                        // Unknown content-type - default to text for safety
                        else {
                            isBinary = false;
                            Log.d(TAG, "[Bluetooth] HTTP-over-GATT: Unknown content-type, defaulting to text: " + contentType);
                        }
                    }
                    // Priority 3: No extension match and no content-type - default to text
                    else {
                        isBinary = false;
                        Log.d(TAG, "[Bluetooth] HTTP-over-GATT: No content-type, defaulting to text");
                    }

                    Log.d(TAG, "[Bluetooth] HTTP-over-GATT: FINAL isBinary=" + isBinary + " for path=" + path);

                    java.io.InputStream inputStream = statusCode < 400 ? conn.getInputStream() : conn.getErrorStream();
                    if (inputStream != null) {
                        // Read stream as bytes using ByteArrayOutputStream (compatible with all Android versions)
                        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
                        byte[] data = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = inputStream.read(data, 0, data.length)) != -1) {
                            buffer.write(data, 0, bytesRead);
                        }
                        buffer.flush();
                        byte[] responseBytes = buffer.toByteArray();
                        inputStream.close();

                        if (isBinary) {
                            // Base64 encode binary data to safely transmit as text
                            responseBody = android.util.Base64.encodeToString(responseBytes, android.util.Base64.NO_WRAP);
                            encoding = "base64";
                            Log.d(TAG, "[Bluetooth] HTTP-over-GATT: Encoded binary response (" + responseBytes.length + " bytes) as Base64");
                        } else {
                            // Keep text as-is
                            responseBody = new String(responseBytes, java.nio.charset.StandardCharsets.UTF_8);
                        }
                    } else {
                        responseBody = "";
                    }

                    conn.disconnect();

                    // Send HTTP response back to sender's CALLSIGN (not MAC address)
                    // Format: HTTP_RESP:{requestId}:{statusCode}:{encoding}:{body}
                    String httpResponse = HTTP_RESP_PREFIX + requestId + ":" + statusCode + ":" + encoding + ":" + responseBody;
                    sendMessageToDevice(httpResponse, senderCallsign);

                    Log.d(TAG, "[Bluetooth] HTTP-over-GATT: Sent response " + statusCode + " (" + encoding + ") to " + senderCallsign + " (requestId: " + requestId + ")");

                } catch (Exception e) {
                    Log.e(TAG, "[Bluetooth] HTTP-over-GATT: Error processing request", e);
                    // Send error response to sender's CALLSIGN (not MAC address)
                    // Format: HTTP_RESP:{requestId}:{statusCode}:{encoding}:{body}
                    String errorResponse = HTTP_RESP_PREFIX + requestId + ":500:text:Error: " + e.getMessage();
                    sendMessageToDevice(errorResponse, senderCallsign);
                }
            }).start();

        } catch (Exception e) {
            Log.e(TAG, "[Bluetooth] Error handling HTTP request: " + e.getMessage(), e);
        }
    }

    /**
     * Handle incoming HTTP response from remote device
     */
    private void handleIncomingHttpResponse(String parcel) {
        try {
            // Format: HTTP_RESP:{requestId}:{statusCode}:{encoding}:{body}
            String content = parcel.substring(HTTP_RESP_PREFIX.length());
            String[] parts = content.split(":", 4);

            if (parts.length < 4) {
                Log.e(TAG, "[Bluetooth] Invalid HTTP response format (expected 4 parts): " + parcel);
                return;
            }

            String requestId = parts[0];
            int statusCode = Integer.parseInt(parts[1]);
            String encoding = parts[2];
            String body = parts[3];

            // Keep Base64-encoded responses as-is (don't decode here)
            // P2PHttpClient will decode properly for binary data to avoid corruption
            if ("base64".equals(encoding)) {
                Log.d(TAG, "[Bluetooth] HTTP-over-GATT: Base64-encoded response, length=" + body.length() + " chars");
                // Leave body as Base64 string - DO NOT decode to avoid corruption of binary data
            }

            Log.d(TAG, "[Bluetooth] HTTP-over-GATT: Received response " + statusCode + " (" + encoding + ") (requestId: " + requestId + ")");
            Log.d(TAG, "[Bluetooth] HTTP-over-GATT: Body length=" + body.length() + " bytes");
            if (body.length() > 0) {
                Log.d(TAG, "[Bluetooth] HTTP-over-GATT: Body preview (first 200 chars): " + body.substring(0, Math.min(200, body.length())));
            }

            // Find pending request and complete it
            java.util.concurrent.CompletableFuture<offgrid.geogram.p2p.P2PHttpClient.HttpResponse> future =
                pendingHttpRequests.remove(requestId);

            if (future != null) {
                Log.d(TAG, "[Bluetooth] HTTP-over-GATT: Completing pending request " + requestId + " with status " + statusCode);
                offgrid.geogram.p2p.P2PHttpClient.HttpResponse response =
                    new offgrid.geogram.p2p.P2PHttpClient.HttpResponse(statusCode, body);
                future.complete(response);
                Log.d(TAG, "[Bluetooth] HTTP-over-GATT: ✓ Future completed successfully for request " + requestId);
            } else {
                Log.w(TAG, "[Bluetooth] HTTP-over-GATT: Received response for unknown/expired request: " + requestId + " (possibly timed out already)");
            }

        } catch (Exception e) {
            Log.e(TAG, "[Bluetooth] Error handling HTTP response: " + e.getMessage(), e);
        }
    }

    /**
     * Handle incoming multi-parcel message (for large HTTP responses split across multiple parcels)
     */
    private void handleMultiParcelMessage(String parcel, String messageId) {
        try {
            // Get or create BluetoothMessage for this message ID
            BluetoothMessage message = pendingMultiParcelHttpResponses.get(messageId);
            if (message == null) {
                message = new BluetoothMessage();
                pendingMultiParcelHttpResponses.put(messageId, message);
                Log.d(TAG, "[Bluetooth] HTTP-over-GATT: Started buffering multi-parcel message " + messageId);
            }

            // Add this parcel to the message
            message.addMessageParcel(parcel);
            Log.d(TAG, "[Bluetooth] HTTP-over-GATT: Added parcel to message " + messageId + " (" + message.getMessageParcelsTotal() + " parcels so far)");

            // Check if message is complete
            if (message.isMessageCompleted()) {
                Log.i(TAG, "[Bluetooth] HTTP-over-GATT: ✓ Multi-parcel message " + messageId + " completed!");
                pendingMultiParcelHttpResponses.remove(messageId);

                // Get complete message and check if it's an HTTP response
                String completeMessage = message.getMessage();
                if (completeMessage.startsWith(HTTP_RESP_PREFIX)) {
                    Log.d(TAG, "[Bluetooth] HTTP-over-GATT: Multi-parcel HTTP response assembled (" + completeMessage.length() + " bytes)");
                    handleIncomingHttpResponse(completeMessage);
                } else {
                    Log.w(TAG, "[Bluetooth] HTTP-over-GATT: Multi-parcel message was not an HTTP response, ignoring");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "[Bluetooth] Error handling multi-parcel message: " + e.getMessage(), e);
            // Clean up on error
            pendingMultiParcelHttpResponses.remove(messageId);
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

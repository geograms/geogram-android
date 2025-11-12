package offgrid.geogram.relay;

import android.content.Context;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import offgrid.geogram.ble.BluetoothMessage;
import offgrid.geogram.ble.BluetoothSender;
import offgrid.geogram.core.Central;
import offgrid.geogram.core.Log;

/**
 * Handles relay message synchronization over BLE.
 *
 * Implements the sync protocol:
 * 1. Inventory exchange - share list of message IDs
 * 2. Gap analysis - identify missing messages
 * 3. Message transfer - request and send missing messages
 *
 * Message format:
 * - INV:<msg-id1>,<msg-id2>,... - Inventory list
 * - REQ:<msg-id> - Request specific message
 * - MSG:<relay-message-markdown> - Relay message content
 */
public class RelayMessageSync {

    private static final String TAG = "RelayMessageSync";

    // Protocol commands
    private static final String CMD_INVENTORY = "INV:";
    private static final String CMD_REQUEST = "REQ:";
    private static final String CMD_MESSAGE = "MSG:";

    // Sync state
    private static final int MAX_INVENTORY_SIZE = 50; // Max message IDs per inventory
    private static final int MAX_REQUEST_BATCH = 5;   // Max concurrent requests
    private static final long SYNC_INTERVAL_MS = 30000; // 30 seconds

    private final Context context;
    private final RelayStorage storage;
    private final RelaySettings settings;
    private final BluetoothSender bluetoothSender;

    // Track sync sessions with remote devices
    private final Map<String, SyncSession> activeSessions = new HashMap<>();

    // Track recently sent/received to avoid duplicates
    private final Set<String> recentlyProcessed = new HashSet<>();
    private static final int MAX_RECENT_SIZE = 100;

    // Singleton instance
    private static RelayMessageSync instance;

    private RelayMessageSync(Context context) {
        this.context = context.getApplicationContext();
        this.storage = new RelayStorage(context);
        this.settings = new RelaySettings(context);
        this.bluetoothSender = BluetoothSender.getInstance(context);
    }

    public static synchronized RelayMessageSync getInstance(Context context) {
        if (instance == null) {
            instance = new RelayMessageSync(context);
        }
        return instance;
    }

    /**
     * Start sync session with a remote device.
     * Sends inventory of outbox messages.
     *
     * @param remoteDeviceId ID of remote device
     */
    public void startSync(String remoteDeviceId) {
        if (!settings.isRelayEnabled()) {
            Log.d(TAG, "Relay disabled, skipping sync");
            return;
        }

        // Verify GATT connection exists before attempting sync
        // Relay sync now uses GATT for high-bandwidth, reliable transfer
        if (!bluetoothSender.hasActiveConnection(remoteDeviceId)) {
            Log.w(TAG, "No active GATT connection to " + remoteDeviceId + ", skipping sync");
            Log.w(TAG, "Active connections: " + bluetoothSender.getActiveConnectionCount());
            return;
        }

        Log.i(TAG, "=== RELAY SYNC STARTED ===");
        Log.i(TAG, "Local device: " + getDeviceId());
        Log.i(TAG, "Remote device: " + remoteDeviceId);
        Log.i(TAG, "Auto-accept: " + settings.isAutoAcceptEnabled());
        Log.i(TAG, "Using GATT connection for high-bandwidth sync");

        // Get or create sync session
        SyncSession session = getOrCreateSession(remoteDeviceId);
        session.lastSyncAttempt = System.currentTimeMillis();

        // Send inventory
        sendInventory(remoteDeviceId);
    }

    /**
     * Send inventory of available messages to remote device.
     */
    private void sendInventory(String remoteDeviceId) {
        // Get messages from outbox (messages to be relayed)
        List<String> outboxIds = storage.listMessages("outbox");

        Log.i(TAG, "Sending inventory to " + remoteDeviceId);
        Log.i(TAG, "Outbox contains " + outboxIds.size() + " messages");

        // Filter out messages that can't be parsed - prevents sync from getting stuck
        List<String> validIds = new ArrayList<>();
        for (String messageId : outboxIds) {
            RelayMessage msg = storage.getMessage(messageId, "outbox");
            if (msg != null) {
                validIds.add(messageId);
            } else {
                Log.w(TAG, "⚠ Skipping unparsable message from inventory: " + messageId);
            }
        }

        if (validIds.size() < outboxIds.size()) {
            Log.w(TAG, "Filtered out " + (outboxIds.size() - validIds.size()) + " unparsable messages from inventory");
        }

        if (validIds.isEmpty()) {
            Log.d(TAG, "No valid messages in outbox - sending empty inventory");
            sendBluetoothMessage(remoteDeviceId, CMD_INVENTORY);
            return;
        }

        // Limit inventory size
        int count = Math.min(validIds.size(), MAX_INVENTORY_SIZE);
        List<String> inventoryIds = validIds.subList(0, count);

        // Build inventory message
        StringBuilder inv = new StringBuilder(CMD_INVENTORY);
        for (int i = 0; i < inventoryIds.size(); i++) {
            if (i > 0) inv.append(",");
            inv.append(inventoryIds.get(i));
        }

        Log.i(TAG, "Inventory content: " + inv.toString().substring(0, Math.min(100, inv.length())) + "...");

        // Send via BLE
        sendBluetoothMessage(remoteDeviceId, inv.toString());
        Log.i(TAG, "✓ Sent inventory with " + inventoryIds.size() + " valid messages");
    }

    /**
     * Handle incoming BLE message.
     * Parses relay protocol commands and processes accordingly.
     *
     * @param message BLE message received
     */
    public void handleIncomingMessage(BluetoothMessage message) {
        if (!settings.isRelayEnabled()) {
            Log.d(TAG, "Relay disabled - ignoring incoming message");
            return;
        }

        String content = message.getMessage();
        if (content == null) {
            return;
        }

        String sender = message.getIdFromSender();
        String msgType = (content.startsWith(CMD_INVENTORY) ? "INVENTORY" :
                         content.startsWith(CMD_REQUEST) ? "REQUEST" :
                         content.startsWith(CMD_MESSAGE) ? "MESSAGE" : "UNKNOWN");

        Log.i(TAG, "=== RELAY MESSAGE RECEIVED ===");
        Log.i(TAG, "From: " + sender);
        Log.i(TAG, "Type: " + msgType);
        Log.i(TAG, "Content length: " + content.length() + " bytes");

        // Parse relay protocol commands
        if (content.startsWith(CMD_INVENTORY)) {
            handleInventory(sender, content.substring(CMD_INVENTORY.length()));
        } else if (content.startsWith(CMD_REQUEST)) {
            handleRequest(sender, content.substring(CMD_REQUEST.length()));
        } else if (content.startsWith(CMD_MESSAGE)) {
            handleRelayMessage(sender, content.substring(CMD_MESSAGE.length()));
        } else {
            Log.w(TAG, "Unknown relay protocol message");
        }
    }

    /**
     * Handle inventory message from remote device.
     * Performs gap analysis and requests missing messages.
     */
    private void handleInventory(String remoteDeviceId, String inventoryData) {
        Log.i(TAG, "--- Processing Inventory ---");
        Log.i(TAG, "From: " + remoteDeviceId);

        // Parse inventory
        String[] remoteIds = inventoryData.trim().isEmpty() ? new String[0] : inventoryData.split(",");
        Set<String> remoteInventory = new HashSet<>();
        for (String id : remoteIds) {
            if (!id.trim().isEmpty()) {
                remoteInventory.add(id.trim());
            }
        }

        Log.i(TAG, "Remote has " + remoteInventory.size() + " messages");

        if (remoteInventory.isEmpty()) {
            Log.d(TAG, "Empty inventory received - remote has no messages");
            return;
        }

        // Get our inbox messages
        List<String> inboxIds = storage.listMessages("inbox");
        Set<String> localInventory = new HashSet<>(inboxIds);
        Log.i(TAG, "Local inbox has " + localInventory.size() + " messages");

        // Gap analysis - find messages we don't have
        List<String> missingMessages = new ArrayList<>();
        for (String remoteId : remoteInventory) {
            if (!localInventory.contains(remoteId) && !isRecentlyProcessed(remoteId)) {
                missingMessages.add(remoteId);
            }
        }

        Log.i(TAG, "Missing " + missingMessages.size() + " messages from remote");

        if (missingMessages.isEmpty()) {
            Log.d(TAG, "No missing messages to request");
            return;
        }

        // Request missing messages (batch limited)
        int requestCount = Math.min(missingMessages.size(), MAX_REQUEST_BATCH);
        Log.i(TAG, "Requesting " + requestCount + " messages");
        for (int i = 0; i < requestCount; i++) {
            requestMessage(remoteDeviceId, missingMessages.get(i));
        }

        // Update session
        SyncSession session = getOrCreateSession(remoteDeviceId);
        session.lastInventoryReceived = System.currentTimeMillis();
        session.pendingRequests.addAll(missingMessages.subList(0, requestCount));
    }

    /**
     * Request a specific message from remote device.
     */
    private void requestMessage(String remoteDeviceId, String messageId) {
        String request = CMD_REQUEST + messageId;
        sendBluetoothMessage(remoteDeviceId, request);
        Log.i(TAG, "→ Requested message: " + messageId);
    }

    /**
     * Handle message request from remote device.
     * Sends the requested message if available in outbox.
     */
    private void handleRequest(String remoteDeviceId, String messageId) {
        Log.i(TAG, "--- Processing Request ---");
        Log.i(TAG, "From: " + remoteDeviceId);
        Log.i(TAG, "Requested message ID: " + messageId);

        // Load message from outbox
        RelayMessage message = storage.getMessage(messageId, "outbox");
        if (message == null) {
            Log.w(TAG, "✗ Requested message not found in outbox: " + messageId);
            return;
        }

        Log.i(TAG, "Found message - from: " + message.getFromCallsign() + " to: " + message.getToCallsign());

        // Check if message should be sent based on settings
        if (!message.shouldAccept(settings)) {
            Log.w(TAG, "✗ Message rejected by relay settings");
            return;
        }

        // Send message
        Log.i(TAG, "→ Sending message to " + remoteDeviceId);
        sendRelayMessage(remoteDeviceId, message);
    }

    /**
     * Send relay message to remote device.
     */
    private void sendRelayMessage(String remoteDeviceId, RelayMessage message) {
        Log.i(TAG, "--- Sending Relay Message ---");
        Log.i(TAG, "Message ID: " + message.getId());
        Log.i(TAG, "To device: " + remoteDeviceId);

        // Serialize to markdown
        String markdown = message.toMarkdown();
        Log.i(TAG, "Markdown size: " + markdown.length() + " bytes");

        // Add relay command prefix
        String content = CMD_MESSAGE + markdown;

        // Send via BLE
        sendBluetoothMessage(remoteDeviceId, content);
        Log.i(TAG, "✓ Sent relay message to " + remoteDeviceId);

        // Mark as recently processed
        addToRecentlyProcessed(message.getId());

        // Update message metadata
        message.addRelayNode(getDeviceId());
        message.setReceivedVia("bluetooth");

        // Move to sent folder
        storage.moveMessage(message.getId(), "outbox", "sent");
        Log.i(TAG, "✓ Message moved to sent folder");
    }

    /**
     * Handle incoming relay message.
     * Saves to inbox if accepted by settings.
     */
    private void handleRelayMessage(String remoteDeviceId, String markdown) {
        Log.i(TAG, "--- Processing Relay Message ---");
        Log.i(TAG, "From: " + remoteDeviceId);
        Log.i(TAG, "Content size: " + markdown.length() + " bytes");

        // Parse markdown
        RelayMessage message = RelayMessage.parseMarkdown(markdown);
        if (message == null) {
            Log.e(TAG, "✗ Failed to parse relay message markdown");
            return;
        }

        Log.i(TAG, "Parsed message ID: " + message.getId());
        Log.i(TAG, "Message from: " + message.getFromCallsign() + " to: " + message.getToCallsign());

        // Check if already processed
        if (isRecentlyProcessed(message.getId())) {
            Log.d(TAG, "Message " + message.getId() + " already processed (skipping)");
            return;
        }

        // Check if we already have this message
        if (storage.getMessage(message.getId()) != null) {
            Log.d(TAG, "Message " + message.getId() + " already in storage (skipping)");
            addToRecentlyProcessed(message.getId());
            return;
        }

        // Check acceptance based on settings
        boolean shouldAccept = message.shouldAccept(settings);
        Log.i(TAG, "Should accept: " + shouldAccept + " (auto-accept: " + settings.isAutoAcceptEnabled() + ")");

        if (!shouldAccept) {
            Log.w(TAG, "✗ Message rejected by relay settings");
            return;
        }

        // Update metadata
        message.setReceivedAt(System.currentTimeMillis() / 1000);
        message.setReceivedVia("bluetooth");
        message.addRelayNode(getDeviceId());

        // Save to inbox
        boolean saved = storage.saveMessage(message, "inbox");
        if (saved) {
            Log.i(TAG, "✓ Message saved to inbox successfully");
            addToRecentlyProcessed(message.getId());

            // Remove from session pending requests
            SyncSession session = activeSessions.get(remoteDeviceId);
            if (session != null) {
                session.pendingRequests.remove(message.getId());
                session.messagesReceived++;
                Log.i(TAG, "Session stats - Received: " + session.messagesReceived + ", Sent: " + session.messagesSent);
            }
        } else {
            Log.e(TAG, "✗ Failed to save message to inbox");
        }
    }

    /**
     * Send a message via Bluetooth using existing infrastructure.
     */
    private void sendBluetoothMessage(String destination, String content) {
        try {
            String msgType = content.startsWith(CMD_INVENTORY) ? "INV" :
                           content.startsWith(CMD_REQUEST) ? "REQ" :
                           content.startsWith(CMD_MESSAGE) ? "MSG" : "???";
            Log.d(TAG, "Sending " + msgType + " via BLE (" + content.length() + " bytes) to " + destination);

            // Create BluetoothMessage
            // Use GATT connection for relay sync (higher bandwidth, reliable delivery)
            // Relay messages are sent directly to the target device via GATT when available
            BluetoothMessage bleMessage = new BluetoothMessage(
                    getDeviceId(),
                    destination,  // Use actual destination for GATT routing
                    content,
                    false  // Never use single-message mode for relay - always use GATT for reliability
            );

            // Send BluetoothMessage object (which contains all parcels)
            String[] parcels = bleMessage.getMessageParcels();
            Log.d(TAG, "BLE message split into " + parcels.length + " parcel(s)");
            bluetoothSender.sendMessage(bleMessage);
            Log.d(TAG, "✓ BLE message queued for transmission");
        } catch (Exception e) {
            // Handle gracefully - may fail in test environment
            Log.e(TAG, "✗ Failed to send BLE message: " + e.getMessage(), e);
        }
    }

    /**
     * Get device ID for this relay node.
     */
    private String getDeviceId() {
        try {
            if (Central.getInstance() != null && Central.getInstance().getSettings() != null) {
                String callsign = Central.getInstance().getSettings().getIdDevice();
                if (callsign != null && !callsign.isEmpty()) {
                    return callsign;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting device ID: " + e.getMessage());
        }
        return "UNKNOWN";
    }

    /**
     * Get or create sync session for remote device.
     */
    private SyncSession getOrCreateSession(String remoteDeviceId) {
        SyncSession session = activeSessions.get(remoteDeviceId);
        if (session == null) {
            session = new SyncSession(remoteDeviceId);
            activeSessions.put(remoteDeviceId, session);
        }
        return session;
    }

    /**
     * Check if message was recently processed.
     */
    private boolean isRecentlyProcessed(String messageId) {
        return recentlyProcessed.contains(messageId);
    }

    /**
     * Add message to recently processed list.
     */
    private void addToRecentlyProcessed(String messageId) {
        recentlyProcessed.add(messageId);

        // Limit size of recent list
        if (recentlyProcessed.size() > MAX_RECENT_SIZE) {
            // Remove oldest (simple approach - just clear half)
            List<String> toRemove = new ArrayList<>();
            int removeCount = 0;
            for (String id : recentlyProcessed) {
                if (removeCount++ >= MAX_RECENT_SIZE / 2) {
                    break;
                }
                toRemove.add(id);
            }
            recentlyProcessed.removeAll(toRemove);
        }
    }

    /**
     * Clean up old sync sessions.
     */
    public void cleanupSessions() {
        long now = System.currentTimeMillis();
        List<String> toRemove = new ArrayList<>();

        for (Map.Entry<String, SyncSession> entry : activeSessions.entrySet()) {
            SyncSession session = entry.getValue();
            if (now - session.lastSyncAttempt > SYNC_INTERVAL_MS * 3) {
                toRemove.add(entry.getKey());
            }
        }

        for (String deviceId : toRemove) {
            activeSessions.remove(deviceId);
            Log.d(TAG, "Removed stale sync session for " + deviceId);
        }
    }

    /**
     * Get sync statistics.
     */
    public SyncStats getStats() {
        SyncStats stats = new SyncStats();
        stats.activeSessions = activeSessions.size();
        stats.recentlyProcessed = recentlyProcessed.size();

        for (SyncSession session : activeSessions.values()) {
            stats.totalMessagesSent += session.messagesSent;
            stats.totalMessagesReceived += session.messagesReceived;
            stats.totalPendingRequests += session.pendingRequests.size();
        }

        return stats;
    }

    /**
     * Sync session data for a remote device.
     */
    private static class SyncSession {
        final String remoteDeviceId;
        long lastSyncAttempt;
        long lastInventoryReceived;
        int messagesSent;
        int messagesReceived;
        final Set<String> pendingRequests = new HashSet<>();

        SyncSession(String remoteDeviceId) {
            this.remoteDeviceId = remoteDeviceId;
            this.lastSyncAttempt = System.currentTimeMillis();
        }
    }

    /**
     * Sync statistics.
     */
    public static class SyncStats {
        public int activeSessions;
        public int recentlyProcessed;
        public int totalMessagesSent;
        public int totalMessagesReceived;
        public int totalPendingRequests;

        @Override
        public String toString() {
            return "SyncStats{" +
                    "activeSessions=" + activeSessions +
                    ", recentlyProcessed=" + recentlyProcessed +
                    ", messagesSent=" + totalMessagesSent +
                    ", messagesReceived=" + totalMessagesReceived +
                    ", pendingRequests=" + totalPendingRequests +
                    '}';
        }
    }

    // Testing methods

    /**
     * Get storage instance (for testing).
     */
    RelayStorage getStorage() {
        return storage;
    }

    /**
     * Get settings instance (for testing).
     */
    RelaySettings getSettings() {
        return settings;
    }

    /**
     * Clear recently processed list (for testing).
     */
    void clearRecentlyProcessed() {
        recentlyProcessed.clear();
    }

    /**
     * Get active sessions (for testing).
     */
    Map<String, SyncSession> getActiveSessions() {
        return activeSessions;
    }

    /**
     * Get pending request count for a session (for testing).
     */
    int getPendingRequestCount(String remoteDeviceId) {
        SyncSession session = activeSessions.get(remoteDeviceId);
        return session != null ? session.pendingRequests.size() : 0;
    }
}

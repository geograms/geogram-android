package offgrid.geogram.ble.events;

import offgrid.geogram.apps.chat.ChatMessage;
import offgrid.geogram.ble.BluetoothMessage;
import offgrid.geogram.core.Central;
import offgrid.geogram.database.DatabaseMessages;
import offgrid.geogram.events.EventAction;
import offgrid.geogram.relay.RelayMessageSync;
import android.util.Log;

public class EventBleBroadcastMessageReceived extends EventAction {
    private static final String TAG = "EventBleMessageReceived";

    public EventBleBroadcastMessageReceived(String id) {
        super(id);
    }

    @Override
    public void action(Object... data) {
        BluetoothMessage messageBluetooth = (BluetoothMessage) data[0];

        // Check if this is a relay protocol message or system command
        String content = messageBluetooth.getMessage();
        if (content != null && (content.startsWith("INV:") || content.startsWith("REQ:") || content.startsWith("MSG:"))) {
            // This is a relay protocol message - handle it through RelayMessageSync
            try {
                RelayMessageSync relaySync = RelayMessageSync.getInstance(Central.getInstance().broadcastChatFragment.getContext());
                relaySync.handleIncomingMessage(messageBluetooth);
                Log.d(TAG, "Handled relay protocol message");
                return; // Don't process as regular chat message
            } catch (Exception e) {
                Log.e(TAG, "Error handling relay message: " + e.getMessage());
            }
        }

        // Filter out all system commands - don't save to database or show in UI
        if (content != null && (
            content.startsWith("INV:") ||
            content.startsWith("REQ:") ||
            content.startsWith("MSG:") ||
            content.startsWith("/repeat") ||
            content.startsWith("/"))) {
            Log.d(TAG, "Filtered system command from geochat: " + content.substring(0, Math.min(20, content.length())));
            return;
        }

        // Skip own messages - they were already added by EventBleBroadcastMessageSent
        try {
            offgrid.geogram.settings.SettingsUser settings = Central.getInstance().getSettings();
            if (settings != null) {
                String localCallsign = settings.getCallsign();
                if (localCallsign != null && localCallsign.equals(messageBluetooth.getIdFromSender())) {
                    Log.d(TAG, "Skipping own broadcast message (already saved when sent)");
                    return;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not check message sender: " + e.getMessage());
        }

        // Regular chat message processing
        ChatMessage message = ChatMessage.convert(messageBluetooth);
        // Tag as local Bluetooth message
        message.setMessageType(offgrid.geogram.apps.chat.ChatMessageType.LOCAL);
        // add the message on the database
        DatabaseMessages.getInstance().add(message);
        // flush immediately to disk
        DatabaseMessages.getInstance().flushNow();

        // Trigger UI refresh from database to prevent duplicates
        Central.getInstance().broadcastChatFragment.refreshMessagesFromDatabase();
    }
}

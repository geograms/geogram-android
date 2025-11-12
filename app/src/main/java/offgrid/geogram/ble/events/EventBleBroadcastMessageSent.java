package offgrid.geogram.ble.events;

import offgrid.geogram.apps.chat.ChatMessage;
import offgrid.geogram.ble.BluetoothMessage;
import offgrid.geogram.core.Central;
import offgrid.geogram.core.Log;
import offgrid.geogram.database.DatabaseMessages;
import offgrid.geogram.events.EventAction;

public class EventBleBroadcastMessageSent extends EventAction {
    private static final String TAG = "EventBleMessageSent";

    public EventBleBroadcastMessageSent(String id) {
        super(id);
    }

    @Override
    public void action(Object... data) {
        BluetoothMessage message = (BluetoothMessage) data[0];

        // Filter out all system/relay commands - don't save to database or show in UI
        String content = message.getMessage();
        if (content != null && (
            content.startsWith("INV:") ||
            content.startsWith("REQ:") ||
            content.startsWith("MSG:") ||
            content.startsWith("/repeat") ||
            content.startsWith("/"))) {
            Log.d(TAG, "Filtered system command from geochat (sent): " + content.substring(0, Math.min(20, content.length())));
            return;
        }

        ChatMessage chatMessage = ChatMessage.convert(message);
        chatMessage.setWrittenByMe(true);

        // CRITICAL FIX: Set the authorId to the local device's callsign
        // When sending a message, message.getIdFromSender() may be null/empty
        // because the message hasn't been broadcast yet with full sender info
        try {
            if (Central.getInstance() != null && Central.getInstance().getSettings() != null) {
                String localCallsign = Central.getInstance().getSettings().getCallsign();
                if (localCallsign != null && !localCallsign.isEmpty()) {
                    chatMessage.setAuthorId(localCallsign);
                    Log.d(TAG, "Set authorId for sent message: " + localCallsign);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to set authorId for sent message: " + e.getMessage());
        }

        // Tag as local Bluetooth message
        chatMessage.setMessageType(offgrid.geogram.apps.chat.ChatMessageType.LOCAL);
        // add the message to the database
        DatabaseMessages.getInstance().add(chatMessage);
        // flush immediately to disk
        DatabaseMessages.getInstance().flushNow();

        // Trigger UI refresh to show the sent message
        if (Central.getInstance() != null && Central.getInstance().broadcastChatFragment != null) {
            Central.getInstance().broadcastChatFragment.refreshMessagesFromDatabase();
        }
    }
}

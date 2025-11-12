package offgrid.geogram.ble.events;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeSet;

import offgrid.geogram.apps.chat.ChatMessage;
import offgrid.geogram.ble.BluetoothMessage;
import offgrid.geogram.ble.BluetoothSender;
import offgrid.geogram.ble.ValidCommands;
import offgrid.geogram.ble.missing.MissingMessagesBLE;
import offgrid.geogram.core.Central;
import offgrid.geogram.core.Log;
import offgrid.geogram.database.DatabaseLocations;
import offgrid.geogram.database.DatabaseMessages;
import offgrid.geogram.devices.ConnectionType;
import offgrid.geogram.devices.DeviceManager;
import offgrid.geogram.devices.DeviceType;
import offgrid.geogram.devices.EventConnected;
import offgrid.geogram.events.EventAction;
import offgrid.geogram.relay.RelayMessageSync;
import offgrid.geogram.util.GeoCode4;

public class EventBleMessageReceived extends EventAction {

    HashMap<String, BluetoothMessage> messages = new HashMap<>();
    private static final String TAG = "EventBleMessageReceived";

    public EventBleMessageReceived(String id) {
        super(id);
    }

    @Override
    public void action(Object... data) {
        String message = (String) data[0];

        // remove the > from the beginning
        message = message.substring(1);

        Log.i(TAG, "-->> Received message: " + message);
        // Handle the received message here

        // is this a single message?
        if(ValidCommands.isValidCommand(message)){
            // this is a one-time message
            BluetoothMessage msg = new BluetoothMessage();
            msg.addMessageParcel(message);
            // process the message right away
            handleSingleMessage(msg);
            return;
        }


        // need to have the separator for multiple parcels
        if(message.contains(":") == false){
            return;
        }

        // get the id of the message
        String id = message.substring(0, 2);
        BluetoothMessage msg;
        // create or add a new message
        if(messages.containsKey(id)){
            msg = messages.get(id);
        }else {
            msg = new BluetoothMessage();
            messages.put(id, msg);
        }
        if(msg == null){
            msg = new BluetoothMessage();
            messages.put(id, msg);
        }


        // add the message
        msg.addMessageParcel(message);
        // check if the message is complete
        if(msg.isMessageCompleted() == false){
            // if more than 3 seconds pass without update, ask for missing parcels
            //TODO: add the code here to ask for a new parcel
            shouldWeAskForMissingPackages(msg);
            return;
        }

        // needs to have a destination
        String destination = msg.getIdDestination();
        if(destination == null){
            return;
        }

        // the message is complete, do the rest

        // Get local device ID for message filtering
        String localId = null;
        try {
            if (Central.getInstance() != null && Central.getInstance().getSettings() != null) {
                localId = Central.getInstance().getSettings().getIdDevice();
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not get local device ID: " + e.getMessage());
        }

        // Check if this is a relay command (INV:, REQ:, MSG:)
        String content = msg.getMessage();
        if (content != null && (content.startsWith("INV:") || content.startsWith("REQ:") || content.startsWith("MSG:"))) {
            // Process relay messages sent via broadcast OR targeted to this device via GATT
            if (msg.getIdDestination().equalsIgnoreCase("ANY") ||
                (localId != null && msg.getIdDestination().equalsIgnoreCase(localId))) {
                // This is a relay message for us, forward to RelayMessageSync
                try {
                    if (Central.getInstance() != null && Central.getInstance().broadcastChatFragment != null) {
                        RelayMessageSync relaySync = RelayMessageSync.getInstance(Central.getInstance().broadcastChatFragment.getContext());
                        relaySync.handleIncomingMessage(msg);
                        Log.i(TAG, "Forwarded relay command to RelayMessageSync: " + content.substring(0, Math.min(20, content.length())));
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to process relay message: " + e.getMessage());
                }
                // Don't process relay messages as regular chat messages
                Log.i(TAG, "-->> Message completed: " + msg.getOutput());
                return;
            }
        }

        // is this a broadcast message?
        if(msg.getIdDestination().equalsIgnoreCase("ANY")){
            // Regular broadcast message
            handleBroadcastMessage(msg);
        }

        Log.i(TAG, "-->> Message completed: " + msg.getOutput());
    }

    /**
     * Long messages will lose packages. This is the place to ask for missing packages.
     */
    private void shouldWeAskForMissingPackages(BluetoothMessage msg) {
        ArrayList<String> missingParcels = msg.getMissingParcels();
        if(missingParcels.isEmpty()){
            return;
        }

        // Send NACK request for each missing parcel
        for(String missingParcelId : missingParcels) {
            String nackMessage = "/repeat " + missingParcelId;
            BluetoothSender.getInstance(null).sendMessage(nackMessage);
            Log.i(TAG, "Requesting missing parcel: " + missingParcelId);
        }

        // messages are sent in sequence. If there is a missing sequence, ask for it
        Log.i(TAG, msg.getId() + " is missing packages: " + missingParcels.size());
    }


    private void handleSingleMessage(BluetoothMessage msg) {
        String text = msg.getMessage();

        android.util.Log.d("ReadReceipts", "handleSingleMessage: " + text.substring(0, Math.min(30, text.length())));

        // + is for location messages
        if(text.startsWith("+")){
            handleLocationMessage(msg);
            return;
        }

        // handle commands that start with /
        if(text.startsWith("/")){
            android.util.Log.d("ReadReceipts", "Command detected: " + text.substring(0, Math.min(30, text.length())));
            // handle commands
            handleCommand(msg);
            return;
        }
    }

    private void handleCommand(BluetoothMessage msg) {
        String text = msg.getMessage();
        android.util.Log.d("ReadReceipts", "handleCommand - isValidRequest: " + ValidCommands.isValidRequest(text) + " for: " + text.substring(0, Math.min(30, text.length())));

        if(ValidCommands.isValidRequest(text) == false){
            android.util.Log.w("ReadReceipts", "Command NOT valid request: " + text);
            return;
        }
        if(text.startsWith(ValidCommands.PARCEL_REPEAT)){
            handleParcelRepeat(text);
            return;
        }
        if(text.startsWith(ValidCommands.READ_RECEIPT)){
            android.util.Log.i("ReadReceipts", "Calling handleReadReceipt for: " + text);
            handleReadReceipt(text, msg.getIdFromSender());
            return;
        }
        if(text.startsWith(ValidCommands.READ_RECEIPT_COMPACT)){
            android.util.Log.i("ReadReceipts", "Calling handleReadReceipt for compact format: " + text);
            handleReadReceipt(text, msg.getIdFromSender());
            return;
        }
        Log.i(TAG, "Command received but not understood: " + text);
    }

    /**
     * Place a request to send again a parcel from a message being dispatched
     * @param text /REPEAT AF8
     */
    private void handleParcelRepeat(String text) {
        // account for the command size plus the space
        String data = text.substring(ValidCommands.PARCEL_REPEAT.length()+1);
        String messageId = data.substring(0,2);
        String parcelNumber = data.substring(2);
        MissingMessagesBLE.addToQueue(ConnectionType.BLE, messageId, parcelNumber);
    }

    /**
     * Handle a read receipt notification from another device
     * @param text /read 1234567890 AUTHOR_ID OR /R 949441328 AUTHOR_ID (compact format)
     * @param readerCallsign The callsign of the device that read the message
     */
    private void handleReadReceipt(String text, String readerCallsign) {
        try {
            // Parse: /read TIMESTAMP AUTHOR_ID or /R COMPACT_TIMESTAMP AUTHOR_ID
            boolean isCompact = text.startsWith("/R ");
            String commandPrefix = isCompact ? "/R" : ValidCommands.READ_RECEIPT;
            String data = text.substring(commandPrefix.length()).trim();
            String[] parts = data.split(" ", 2);
            if (parts.length != 2) {
                Log.w(TAG, "Invalid read receipt format: " + text);
                return;
            }

            String timestampPart = parts[0];
            String authorId = parts[1];

            // For compact format, we need to match against the last N digits
            boolean usePartialMatch = isCompact || timestampPart.length() < 13;

            // Find the message in database by timestamp and author
            TreeSet<ChatMessage> messages = DatabaseMessages.getInstance().getMessages();
            ChatMessage targetMessage = null;

            for (ChatMessage msg : messages) {
                if (!authorId.equals(msg.authorId)) {
                    continue; // Author must match exactly
                }

                if (usePartialMatch) {
                    // Compact format: match last N digits of timestamp
                    String msgTimestampStr = String.valueOf(msg.timestamp);
                    String msgCompactTimestamp = msgTimestampStr.length() > timestampPart.length() ?
                            msgTimestampStr.substring(msgTimestampStr.length() - timestampPart.length()) : msgTimestampStr;
                    if (msgCompactTimestamp.equals(timestampPart)) {
                        targetMessage = msg;
                        Log.d(TAG, "Matched compact read receipt: " + timestampPart + " -> full timestamp: " + msg.timestamp);
                        break;
                    }
                } else {
                    // Full format: exact timestamp match
                    long timestamp = Long.parseLong(timestampPart);
                    if (msg.timestamp == timestamp) {
                        targetMessage = msg;
                        break;
                    }
                }
            }

            if (targetMessage == null) {
                Log.d(TAG, "Message not found for read receipt: timestamp=" + timestampPart +
                        " (compact=" + isCompact + ") author=" + authorId);
                return;
            }

            // Add the read receipt
            targetMessage.addReadReceipt(readerCallsign, System.currentTimeMillis());
            android.util.Log.i("ReadReceipts", "RECEIVED READ receipt from " + readerCallsign +
                    " for message from " + authorId + " (timestamp=" + targetMessage.timestamp + ")");

            // Save to database
            DatabaseMessages.getInstance().flushNow();

            // Refresh UI if available
            if (Central.getInstance() != null && Central.getInstance().broadcastChatFragment != null) {
                Central.getInstance().broadcastChatFragment.refreshMessagesFromDatabase();
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to handle read receipt: " + e.getMessage(), e);
        }
    }

    private void handleBroadcastMessage(BluetoothMessage msg) {
        String content = msg.getMessage();

        // Filter out system/relay commands - don't save to database or show in UI
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
        String senderCallsign = msg.getIdFromSender();
        Log.d(TAG, "Checking broadcast message sender: '" + senderCallsign + "'");

        try {
            offgrid.geogram.settings.SettingsUser settings = Central.getInstance().getSettings();
            if (settings != null) {
                String localCallsign = settings.getCallsign();

                if (localCallsign != null && senderCallsign != null) {
                    // Use case-insensitive comparison and trim whitespace
                    if (localCallsign.trim().equalsIgnoreCase(senderCallsign.trim())) {
                        Log.d(TAG, "Skipping own broadcast message (local: '" + localCallsign + "' == sender: '" + senderCallsign + "')");
                        return;
                    } else {
                        Log.d(TAG, "Not own message (local: '" + localCallsign + "' != sender: '" + senderCallsign + "')");
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking message sender: " + e.getMessage(), e);
            // Continue processing - better to risk a duplicate than lose messages
        }

        // this is a generic message, convert to a standard format
        ChatMessage chatMessage = ChatMessage.convert(msg);

        // Tag as local Bluetooth message
        chatMessage.setMessageType(offgrid.geogram.apps.chat.ChatMessageType.LOCAL);

        // add to our database of broadcast messages
        DatabaseMessages.getInstance().add(chatMessage);

        // Trigger UI refresh from database to prevent duplicates
        Central.getInstance().broadcastChatFragment.refreshMessagesFromDatabase();
    }

    private void handleLocationMessage(BluetoothMessage msg) {
        Log.i(TAG, "Location message received");
        // example of messages: +053156@RY19-IUZS#Android Phone or +X1A2B3#T-Dongle ESP32
        String text = msg.getMessage();

        // Extract device model if present (format: ...#MODEL)
        String deviceModel = null;
        if(text.contains("#")){
            int hashIndex = text.indexOf("#");
            deviceModel = text.substring(hashIndex + 1);
            text = text.substring(0, hashIndex); // Remove model from text for further parsing
        }

        // Check if this is a simple ping message (no coordinates)
        if(text.contains("@") == false){
            // This is a simple ping: +CALLSIGN or +CALLSIGN#MODEL
            String callsign = text.substring(1); // Remove the '+' prefix

            if(callsign.isEmpty()){
                return;
            }

            Log.i(TAG, "Ping received from: " + callsign + (deviceModel != null ? " (" + deviceModel + ")" : ""));

            // Determine device type based on device model
            // Android phones (APP) are potential relays, so classify as INTERNET_IGATE
            DeviceType deviceType = DeviceType.HT_PORTABLE;  // Default
            if (deviceModel != null && deviceModel.startsWith("APP-")) {
                deviceType = DeviceType.INTERNET_IGATE;  // Android phones with relay capability
                Log.i(TAG, "Detected Android device with relay capability");
            }

            // Add device without geocode (null geocode indicates BLE ping only)
            EventConnected event = new EventConnected(ConnectionType.BLE, null);
            DeviceManager.getInstance().addNewLocationEvent(callsign, deviceType, event, deviceModel);
            return;
        }

        // Handle location messages with coordinates
        String geocodeExtracted = text.substring(text.indexOf("@") + 1);
        String authorId = text.substring(1, text.indexOf("@"));
        String[] coordinate = geocodeExtracted.split("-");
        if(coordinate.length != 2){
            return;
        }

        // Determine device type based on device model
        // Android phones (APP) are potential relays, so classify as INTERNET_IGATE
        DeviceType deviceType = DeviceType.HT_PORTABLE;  // Default
        if (deviceModel != null && deviceModel.startsWith("APP-")) {
            deviceType = DeviceType.INTERNET_IGATE;  // Android phones with relay capability
            Log.i(TAG, "Detected Android device with relay capability");
        }

        // notify other parts of the code that a new location was received
        EventConnected event = new EventConnected(ConnectionType.BLE, geocodeExtracted);
        DeviceManager.getInstance().addNewLocationEvent(authorId, deviceType, event, deviceModel);

    }

}

package offgrid.geogram.ble.events;

import java.util.ArrayList;
import java.util.HashMap;

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

        // is this a broadcast message?
        if(msg.getIdDestination().equalsIgnoreCase("ANY")){
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

        // + is for location messages
        if(text.startsWith("+")){
            handleLocationMessage(msg);
            return;
        }

        // handle commands that start with /
        if(text.startsWith("/")){
            // handle commands
            handleCommand(msg);
            return;
        }
    }

    private void handleCommand(BluetoothMessage msg) {
        String text = msg.getMessage();
        if(ValidCommands.isValidRequest(text) == false){
            return;
        }
        if(text.startsWith(ValidCommands.PARCEL_REPEAT)){
            handleParcelRepeat(text);
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

    private void handleBroadcastMessage(BluetoothMessage msg) {
        // this is a generic message, convert to a standard format
        ChatMessage chatMessage = ChatMessage.convert(msg);

        // Tag as local Bluetooth message
        chatMessage.setMessageType(offgrid.geogram.apps.chat.ChatMessageType.LOCAL);

        // add to our database of broadcast messages
        DatabaseMessages.getInstance().add(chatMessage);

        // place it on the chat (when possible)
        Central.getInstance().broadcastChatFragment.addMessage(chatMessage);
    }

    private void handleLocationMessage(BluetoothMessage msg) {
        Log.i(TAG, "Location message received");
        // example of messages: +053156@RY19-IUZS or +X1A2B3 (ping from T-Dongle)
        String text = msg.getMessage();

        // Check if this is a simple ping message (no coordinates)
        if(text.contains("@") == false){
            // This is a simple ping from T-Dongle: +CALLSIGN
            String callsign = text.substring(1); // Remove the '+' prefix

            if(callsign.isEmpty()){
                return;
            }

            Log.i(TAG, "Ping received from: " + callsign);

            // Add device without geocode (null geocode indicates BLE ping only)
            EventConnected event = new EventConnected(ConnectionType.BLE, null);
            DeviceManager.getInstance().addNewLocationEvent(callsign, DeviceType.HT_PORTABLE, event);
            return;
        }

        // Handle location messages with coordinates
        String geocodeExtracted = text.substring(text.indexOf("@") + 1);
        String authorId = text.substring(1, text.indexOf("@"));
        String[] coordinate = geocodeExtracted.split("-");
        if(coordinate.length != 2){
            return;
        }

        // notify other parts of the code that a new location was received
        EventConnected event = new EventConnected(ConnectionType.BLE, geocodeExtracted);
        DeviceManager.getInstance().addNewLocationEvent(authorId, DeviceType.HT_PORTABLE, event);

    }

}

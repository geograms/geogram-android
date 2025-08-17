package offgrid.geogram.ble.events;

import java.util.HashMap;

import offgrid.geogram.apps.chat.ChatMessage;
import offgrid.geogram.ble.BluetoothMessage;
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

        // need to have the separator
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
            Log.i(TAG, "Message not yet completed: " + msg.getId());
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

    private void handleBroadcastMessage(BluetoothMessage msg) {
        // start by getting the text of the message
        String text = msg.getMessage();

        // + is for location messages
        if(text.startsWith("+")){
            handleLocationMessage(msg);
            return;
        }

        // this is a generic message, convert to a standard format
        String idThisDevice = Central.getInstance().getSettings().getIdDevice();
        ChatMessage chatMessage = ChatMessage.convert(msg);

        // add to our database of broadcast messages
        DatabaseMessages.getInstance().add(chatMessage);

        // place it on the chat (when possible)
        Central.getInstance().broadcastChatFragment.addMessage(chatMessage);
    }

    private void handleLocationMessage(BluetoothMessage msg) {
        Log.i(TAG, "Location message received");
        // example of messages: +053156@RY19-IUZS
        String text = msg.getMessage();
        // needs to contain the coordinate portion
        if(text.contains("@") == false){
            return;
        }
        String geocodeExtracted = text.substring(text.indexOf("@") + 1);
        String[] coordinate = geocodeExtracted.split("-");
        if(coordinate.length != 2){
            return;
        }

        // notify other parts of the code that a new location was received
        EventConnected event = new EventConnected(ConnectionType.BLE, geocodeExtracted);
        DeviceManager.getInstance().addNewLocationEvent(msg.getAuthor(), DeviceType.HT_PORTABLE, event);

    }

}

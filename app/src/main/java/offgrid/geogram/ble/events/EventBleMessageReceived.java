package offgrid.geogram.ble.events;

import java.util.HashMap;

import offgrid.geogram.apps.chat.ChatMessage;
import offgrid.geogram.ble.BluetoothMessage;
import offgrid.geogram.core.Central;
import offgrid.geogram.core.Log;
import offgrid.geogram.events.EventAction;

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

        String destination = msg.getIdDestination();
        if(destination == null){
            return;
        }

        // the message is complete, do the rest
        if(msg.getIdDestination().equalsIgnoreCase("ANY")){
            // this is a broadcast message to be placed on the chat
            ChatMessage chatMessage = ChatMessage.convert(msg);
            Central.getInstance().broadcastChatFragment.addMessage(chatMessage);
            addMessageToDatabase(chatMessage);
        }

        Log.i(TAG, "-->> Message completed: " + msg.getOutput());

    }

    private void addMessageToDatabase(ChatMessage chatMessage) {
        if(chatMessage.getAuthorId() == null){
            return;
        }
        //Central.getInstance().databaseCallSign.update(chatMessage);
    }
}

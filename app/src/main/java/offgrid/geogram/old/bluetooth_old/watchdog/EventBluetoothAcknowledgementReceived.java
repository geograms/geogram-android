package offgrid.geogram.old.bluetooth_old.watchdog;

import android.content.Context;

import offgrid.geogram.old.bluetooth_old.BlueQueueSending;
import offgrid.geogram.old.bluetooth_old.other.comms.BluePackage;
import offgrid.geogram.old.bluetooth_old.other.comms.DataType;
import offgrid.geogram.core.Log;
import offgrid.geogram.devices.chat.ChatDatabaseWithDevice;
import offgrid.geogram.devices.chat.ChatMessage;
import offgrid.geogram.devices.chat.ChatMessages;
import offgrid.geogram.events.EventAction;
import offgrid.geogram.events.EventControl;
import offgrid.geogram.events.EventType;

public class EventBluetoothAcknowledgementReceived extends EventAction {

    private static final String TAG = "EventBluetoothAcknowledgementReceived";
    private final Context context;

    public EventBluetoothAcknowledgementReceived(String id, Context context) {
        super(id);
        this.context = context;
    }

    @Override
    public void action(Object... data) {
        // object data[0] is of type String. e.g. >ACK:DEVICEID:TG:
        String text = (String) data[0];
        Log.i(TAG, "Ack received: " + text);
        // break into useful parts
        String[] parts = text.split(":");
        if (parts.length != 3) {
            Log.e(TAG, "Invalid ack received: " + text);
            return;
        }
        String deviceId = parts[1];
        String messageId = parts[2];

        // get the packageSent with this ID
        BluePackage packageSent = BlueQueueSending.getInstance(context).packagesToSend.get(messageId);

        if (packageSent == null) {
            Log.e(TAG, "Package not found: " + messageId);
            return;
        }

        // only handle C commands (direct chat)
        if (packageSent.getCommand().equals(DataType.C) == false) {
            return;
        }

        // use the timestamp as id
        long timestamp = packageSent.getTimestamp();

        // iterate to find all related messages from that device Id
        ChatMessages deviceMessages =
                ChatDatabaseWithDevice.getInstance(context).getMessages(deviceId);

        ChatMessage chatMessage = deviceMessages.getMessage(timestamp, packageSent.getData());
        if (chatMessage == null) {
            Log.e(TAG, "Message not found: " + packageSent.getData());
            return;
        }
        // make the changes
        chatMessage.setDelivered(true);
        // save it to disk
        ChatDatabaseWithDevice.getInstance(context).saveToDisk(deviceId, deviceMessages);
        Log.i(TAG, "Message marked as delivered: " + packageSent.getData());
        // inform that the screen can be updated
        EventControl.startEvent(EventType.MESSAGE_DIRECT_UPDATE, chatMessage);
    }
}

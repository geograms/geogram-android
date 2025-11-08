package offgrid.geogram.ble.events;

import offgrid.geogram.apps.chat.ChatMessage;
import offgrid.geogram.ble.BluetoothMessage;
import offgrid.geogram.core.Central;
import offgrid.geogram.database.DatabaseMessages;
import offgrid.geogram.events.EventAction;

public class EventBleBroadcastMessageReceived extends EventAction {
    private static final String TAG = "EventBleMessageReceived";

    public EventBleBroadcastMessageReceived(String id) {
        super(id);
    }

    @Override
    public void action(Object... data) {
        BluetoothMessage messageBluetooth = (BluetoothMessage) data[0];
        ChatMessage message = ChatMessage.convert(messageBluetooth);
        // Tag as local Bluetooth message
        message.setMessageType(offgrid.geogram.apps.chat.ChatMessageType.LOCAL);
        // add the message on the database
        DatabaseMessages.getInstance().add(message);
        // flush immediately to disk
        DatabaseMessages.getInstance().flushNow();

        // add the message on the broadcast window
        Central.getInstance().broadcastChatFragment.addMessage(message);
    }
}

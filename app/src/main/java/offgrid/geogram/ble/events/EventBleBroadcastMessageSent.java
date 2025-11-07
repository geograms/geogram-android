package offgrid.geogram.ble.events;

import offgrid.geogram.apps.chat.ChatMessage;
import offgrid.geogram.ble.BluetoothMessage;
import offgrid.geogram.core.Central;
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
        ChatMessage chatMessage = ChatMessage.convert(message);
        chatMessage.setWrittenByMe(true);
        // add the message to the database
        DatabaseMessages.getInstance().add(chatMessage);
        // flush immediately to disk
        DatabaseMessages.getInstance().flushNow();
        // add the message on the broadcast window
        Central.getInstance().broadcastChatFragment.addMessage(chatMessage);
    }
}

package offgrid.geogram.ble.missing;

import offgrid.geogram.apps.chat.ChatMessage;
import offgrid.geogram.ble.BluetoothMessage;
import offgrid.geogram.core.Central;
import offgrid.geogram.events.EventAction;

/**
 * Make sure that this message gets archived when sending it over BLE
 */
public class EventArchiveMessageBLE extends EventAction {
    private static final String TAG = "EventArchiveMessageBLE";

    public EventArchiveMessageBLE(String id) {
        super(id);
    }

    @Override
    public void action(Object... data) {
        BluetoothMessage message = (BluetoothMessage) data[0];
        MissingMessagesBLE.addToArchive(message);
    }
}

package offgrid.geogram.ble.missing;

import offgrid.geogram.ble.BluetoothMessage;

public class ArchivedMessageBLE {
    final String MessageId;
    final long timeFirstReceived;
    final BluetoothMessage message;


    public ArchivedMessageBLE(BluetoothMessage message) {
        this.message = message;
        MessageId = message.getId();
        this.timeFirstReceived = System.currentTimeMillis();
    }
}

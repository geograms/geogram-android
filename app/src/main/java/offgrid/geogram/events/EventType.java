package offgrid.geogram.events;

public enum EventType {
    MESSAGE_DIRECT_RECEIVED,    // a new direct chat message arrived
    MESSAGE_DIRECT_SENT,        // a chat message was sent to someone
    MESSAGE_DIRECT_UPDATE,      // update the screen with messages
    BLE_BROADCAST_RECEIVED, // broadcast message was received
    BLE_BROADCAST_SENT,     // broadcast message was received

    BLUETOOTH_ACKNOWLEDGE_RECEIVED, // a message sent by bluetooth was acknowledged
    BLUETOOTH_MESSAGE_RECEIVED  // a package was received from another bluetooth device
}

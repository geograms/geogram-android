package offgrid.geogram.events;

public enum EventType {
    MESSAGE_DIRECT_RECEIVED,    // a new direct chat message arrived
    MESSAGE_DIRECT_SENT,        // a chat message was sent to someone
    MESSAGE_DIRECT_UPDATE,      // update the screen with messages
    MESSAGE_BROADCAST_RECEIVED, // broadcast message was received

    BLUETOOTH_ACKNOWLEDGE_RECEIVED, // a message sent by bluetooth was acknowledged
    BLUETOOTH_PACKAGE_RECEIVED  // a package was received from another bluetooth device
}

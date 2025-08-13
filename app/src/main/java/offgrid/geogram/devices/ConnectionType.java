package offgrid.geogram.devices;

public enum ConnectionType {
    DIRECT,     // direct device to device connection
    BRIDGE,     // received by another device, sent to that device on a different method
    INTERNET,   // received through the internet
    PIDGEON,    // message carried and delivered on behalf of another user
}

package offgrid.geogram.apps.chat;

public enum ChatMessageType {
    TEXT,     // text messages
    CHAT,     // chat messages
    DATA,     // generic data inside
    PING,     // announcing the device
    PUB,      // public key
    LOCAL,    // local Bluetooth message
    INTERNET, // internet API message
    WIFI      // local WiFi/LAN message
}

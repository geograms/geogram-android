package offgrid.geogram.core;

/**
 * Defines the protocol actions for interaction
 * of two or more devices.
 *
 * For example, activating the Wi-Fi after request
 * from another phone using the bluetooth beacon.
 */
public class Protocol {

    /**
     * Start the Wi-Fi direct, share the SSID
     * and password details on the bluetooth beacon
     */
    public static void WiFiStart(){

    }

    /**
     * When more than NN minutes pass without activity
     * then close the WiFi direct connection to save
     * battery
     */
    public static void WiFiCloseWhenNotUsed(){

    }

}

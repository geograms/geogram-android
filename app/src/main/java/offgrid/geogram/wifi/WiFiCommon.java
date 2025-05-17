package offgrid.geogram.wifi;

import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pManager;

import java.util.Collection;

public class WiFiCommon {

    public static WifiP2pDeviceList peers = null;

    public static WifiP2pManager wifiP2pManager;
    public static WifiP2pManager.Channel channel;

    public static String ssid = null;
    public static String passphrase = null;


}

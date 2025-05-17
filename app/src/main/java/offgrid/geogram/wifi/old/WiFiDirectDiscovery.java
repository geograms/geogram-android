package offgrid.geogram.wifi.old;

import static offgrid.geogram.MainActivity.activity;
import static offgrid.geogram.wifi.WiFiCommon.channel;
import static offgrid.geogram.wifi.WiFiCommon.wifiP2pManager;

import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pManager;

import offgrid.geogram.core.Log;
import offgrid.geogram.core.PermissionsHelper;

public class WiFiDirectDiscovery {

    private static final String TAG = "WiFiDirectPeerDiscovery";
    private final Context context;
    private boolean isReceiverRegistered = false;
    private boolean isDiscovering = false;

    public WiFiDirectDiscovery(Context context) {
        this.context = context;
        // Initialize the Wi-Fi P2P Manager
        if(wifiP2pManager == null) {
            wifiP2pManager = (WifiP2pManager) context.getSystemService(Context.WIFI_P2P_SERVICE);
        }
        if(channel == null) {
            channel = wifiP2pManager.initialize(context, context.getMainLooper(), null);
        }
    }


    public void startDiscovery(int counter) {
        // Avoid endless loops
        if (counter > 3) {
            return;
        }

        if (!PermissionsHelper.hasAllPermissions(activity)) {
            Log.e(TAG, "Missing necessary permissions. Repeating permission request");
            PermissionsHelper.requestPermissionsIfNecessary(activity);
            startDiscovery(counter + 1);
            return;
        }

        // Ensure Wi-Fi is enabled
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        if (!wifiManager.isWifiEnabled()) {
            Log.e(TAG, "Wi-Fi is disabled. Prompting user to enable it.");
            // Prompt user to enable Wi-Fi
            Intent intent = new Intent(WifiManager.ACTION_PICK_WIFI_NETWORK);
            context.startActivity(intent);
            return;
        }

        // Start peer discovery
        try {
            isDiscovering = true;
            wifiP2pManager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    Log.i(TAG, "Peer discovery started successfully");
                }

                @Override
                public void onFailure(int reason) {
                    isDiscovering = false;
                    switch (reason) {
                        case WifiP2pManager.P2P_UNSUPPORTED:
                            Log.e(TAG, "Peer discovery failed: P2P unsupported");
                            break;
                        case WifiP2pManager.BUSY:
                            Log.e(TAG, "Peer discovery failed: Framework busy");
                            break;
                        case WifiP2pManager.ERROR:
                        default:
                            Log.e(TAG, "Peer discovery failed: Internal error");
                            break;
                    }
                }
            });
        } catch (SecurityException e) {
            isDiscovering = false;
            Log.e(TAG, "Permission denied while starting peer discovery: " + e.getMessage());
        }
    }

    public void stopDiscovery() {
        if (isReceiverRegistered) {
            try {
                //context.unregisterReceiver(receiver);
                isReceiverRegistered = false;
                Log.i(TAG, "Peer discovery stopped.");
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Receiver not registered: " + e.getMessage());
            }
        } else {
           // Log.e(TAG, "Receiver is not registered, cannot unregister.");
        }
    }

}

package offgrid.geogram.wifi.old;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Handler;

import androidx.core.app.ActivityCompat;

import offgrid.geogram.core.Log;

public class WiFiDirectDiscovery2 {

    private static final String TAG = "WiFiDirectPeerDiscovery";

    private final WifiP2pManager wifiP2pManager;
    private final WifiP2pManager.Channel channel;
    private final Context context;
    private final BroadcastReceiver receiver;
    private boolean isReceiverRegistered = false;
    private boolean isDiscovering = false;

    public WiFiDirectDiscovery2(Context context) {
        this.context = context;

        // Initialize the Wi-Fi P2P Manager
        wifiP2pManager = (WifiP2pManager) context.getSystemService(Context.WIFI_P2P_SERVICE);
        channel = wifiP2pManager.initialize(context, context.getMainLooper(), null);

        // Define a BroadcastReceiver to handle Wi-Fi P2P state changes
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
                    Log.i(TAG, "Detected changes in available peers.");
                    listAvailablePeers();
                }
            }
        };
    }

    public void registerIntents() {
        if (!isReceiverRegistered) {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
            intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
            intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
            context.registerReceiver(receiver, intentFilter);
            isReceiverRegistered = true;
            Log.i(TAG, "Wi-Fi P2P intents registered.");
        }
    }

    public void unregisterIntents() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(receiver);
                isReceiverRegistered = false;
                Log.i(TAG, "Wi-Fi P2P intents unregistered.");
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Receiver was not registered: " + e.getMessage());
            }
        } else {
            Log.e(TAG, "Receiver is not registered; cannot unregister.");
        }
    }

    public void startPeerDiscovery() {
        if (!hasPermissions()) {
            Log.e(TAG, "Missing necessary permissions. Cannot start peer discovery.");
            return;
        }

        try {
            WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            if (!wifiManager.isWifiEnabled()) {
                Log.e(TAG, "Wi-Fi is disabled. Enable Wi-Fi to start peer discovery.");
                return;
            }

            wifiP2pManager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    isDiscovering = true;
                    Log.i(TAG, "Peer discovery started successfully.");
                }

                @Override
                public void onFailure(int reason) {
                    isDiscovering = false;
                    Log.e(TAG, "Failed to start peer discovery. Reason: " + reason);
                }
            });
        } catch (SecurityException e) {
            Log.e(TAG, "Permission denied while starting peer discovery: " + e.getMessage());
        }
    }

    public void stopPeerDiscovery() {
        if (isDiscovering) {
            try {
                wifiP2pManager.stopPeerDiscovery(channel, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        isDiscovering = false;
                        Log.i(TAG, "Peer discovery stopped successfully.");
                    }

                    @Override
                    public void onFailure(int reason) {
                        Log.e(TAG, "Failed to stop peer discovery. Reason: " + reason);
                    }
                });
            } catch (SecurityException e) {
                Log.e(TAG, "Permission denied while stopping peer discovery: " + e.getMessage());
            }
        } else {
            Log.i(TAG, "Peer discovery is not currently running.");
        }
    }

    public void createGroupWithRetry() {
        stopPeerDiscovery();

        if (!hasPermissions()) {
            Log.e(TAG, "Missing necessary permissions. Cannot create group.");
            return;
        }

        wifiP2pManager.removeGroup(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.i(TAG, "Existing group removed. Attempting to create a new group...");
                attemptCreateGroup();
            }

            @Override
            public void onFailure(int reason) {
                Log.e(TAG, "Failed to remove group. Reason: " + reason);
                attemptCreateGroup(); // Proceed to create group anyway
            }
        });
    }

    private void attemptCreateGroup() {
        if (!hasPermissions()) {
            Log.e(TAG, "Missing necessary permissions. Cannot create group.");
            return;
        }

        try {
            wifiP2pManager.createGroup(channel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    Log.i(TAG, "Wi-Fi Direct group created successfully.");
                }

                @Override
                public void onFailure(int reason) {
                    if (reason == WifiP2pManager.BUSY) {
                        Log.e(TAG, "Failed to create group. Reason: Framework busy. Retrying...");
                        new Handler().postDelayed(() -> attemptCreateGroup(), 2000); // Retry after 2 seconds
                    } else {
                        Log.e(TAG, "Failed to create group. Reason: " + reason);
                    }
                }
            });
        } catch (SecurityException e) {
            Log.e(TAG, "Permission denied while creating group: " + e.getMessage());
        }
    }

    public void logAvailablePeers() {
        if (!hasPermissions()) {
            Log.e(TAG, "Missing necessary permissions. Cannot list peers.");
            return;
        }

        try {
            wifiP2pManager.requestPeers(channel, peers -> {
                if (peers.getDeviceList().isEmpty()) {
                    Log.i(TAG, "No peers currently available.");
                } else {
                    Log.i(TAG, "Currently available peers:");
                    for (WifiP2pDevice device : peers.getDeviceList()) {
                        Log.i(TAG, "Peer: " + device.deviceName + " - " + device.deviceAddress);
                    }
                }
            });
        } catch (SecurityException e) {
            Log.e(TAG, "Permission denied while requesting peers: " + e.getMessage());
        }
    }

    private void listAvailablePeers() {
        if (!hasPermissions()) {
            Log.e(TAG, "Missing necessary permissions. Cannot list peers.");
            return;
        }

        try {
            wifiP2pManager.requestPeers(channel, peers -> {
                for (WifiP2pDevice device : peers.getDeviceList()) {
                    Log.i(TAG, "Discovered peer: " + device.deviceName + " - " + device.deviceAddress);
                }
            });
        } catch (SecurityException e) {
            Log.e(TAG, "Permission denied while requesting peers: " + e.getMessage());
        }
    }

    private boolean hasPermissions() {
        try {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Permission not granted: ACCESS_FINE_LOCATION");
                return false;
            }
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Permission not granted: ACCESS_WIFI_STATE");
                return false;
            }
            return true;
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException during permission check: " + e.getMessage());
            return false;
        }
    }
}

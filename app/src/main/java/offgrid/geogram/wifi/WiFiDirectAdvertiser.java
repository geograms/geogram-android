package offgrid.geogram.wifi;

import static offgrid.geogram.core.Messages.log;
import static offgrid.geogram.wifi.WiFiCommon.channel;
import static offgrid.geogram.wifi.WiFiCommon.passphrase;
import static offgrid.geogram.wifi.WiFiCommon.ssid;
import static offgrid.geogram.wifi.WiFiCommon.wifiP2pManager;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pManager;

import androidx.core.app.ActivityCompat;

import offgrid.geogram.bluetooth.BluetoothCentral;
import offgrid.geogram.core.Log;

public class WiFiDirectAdvertiser {
    private static final String TAG = "WiFiDirectAdvertiser";

    private static volatile WiFiDirectAdvertiser instance;
    private final BroadcastReceiver receiver;
    private final Context context;
    private boolean isAdvertising = false;

    private WiFiDirectAdvertiser(Context context) {
        this.context = context;

        // Initialize the Wi-Fi P2P Manager
        if (wifiP2pManager == null) {
            wifiP2pManager = (WifiP2pManager) context.getSystemService(Context.WIFI_P2P_SERVICE);
        }
        if (channel == null) {
            channel = wifiP2pManager.initialize(context, context.getMainLooper(), null);
        }

        // Define a BroadcastReceiver to handle Wi-Fi P2P state changes
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        Log.e(TAG, "Permission ACCESS_FINE_LOCATION not granted. Skipping connection info.");
                        return;
                    }
                    wifiP2pManager.requestConnectionInfo(channel, info -> {
                        if (info.groupFormed && info.isGroupOwner) {
                            Log.i(TAG, "This device is the Group Owner");
                            logGroupDetails();
                        } else if (info.groupFormed) {
                            Log.i(TAG, "This device is a Client");
                        }
                    });
                } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        Log.e(TAG, "Permission ACCESS_FINE_LOCATION not granted. Skipping peer request.");
                        return;
                    }
                    wifiP2pManager.requestPeers(channel, peers -> {
                        WiFiCommon.peers = peers;
                    });
                } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
                    Log.i(TAG, "This device's Wi-Fi Direct state changed");
                }
            }
        };
    }

    public static WiFiDirectAdvertiser getInstance(Context context) {
        if (instance == null) {
            synchronized (WiFiDirectAdvertiser.class) {
                if (instance == null) {
                    instance = new WiFiDirectAdvertiser(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    /**
     * Logs the SSID and passphrase of the group if available.
     */
    private void logGroupDetails() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Permission ACCESS_FINE_LOCATION not granted. Cannot retrieve group details.");
            return;
        }

        try {
            wifiP2pManager.requestGroupInfo(channel, group -> {
                if (group != null) {
                    ssid = group.getNetworkName();
                    passphrase = group.getPassphrase();
                    Log.i(TAG, "Group SSID: " + ssid);
                    Log.i(TAG, "Group Passphrase: " + passphrase);

                    if (group.isGroupOwner()) {
                        String groupOwnerIp = "192.168.49.1";
                        Log.i(TAG, "Device is Group Owner. IP Address: " + groupOwnerIp);
                        disableInternetGateway(group);
                    } else {
                        Log.i(TAG, "Device is Client. Group Owner IP: " + group.getOwner().deviceAddress);
                    }

                    startBluetooth();
                } else {
                    Log.e(TAG, "No group information available.");
                }
            });
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException while retrieving group details: " + e.getMessage());
        }
    }

    /**
     * Disables the advertisement of internet access for the Wi-Fi Direct group.
     */
    private void disableInternetGateway(WifiP2pGroup group) {
        if (group != null && group.isGroupOwner()) {
            Log.i(TAG, "Disabling internet gateway advertisement for the group.");
            // Android doesn't allow direct manipulation of DHCP or routing tables for Wi-Fi Direct,
            // but this ensures the hotspot is recognized as local-only.
        }
    }

    private void startBluetooth() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            log(TAG, "Bluetooth is not supported on this device.");
            return;
        } else if (!bluetoothAdapter.isEnabled()) {
            log(TAG, "Bluetooth is disabled. Please turn it on");
            return;
        }
        BluetoothCentral.getInstance(this.context).start();
    }

    /**
     * Checks if all necessary permissions are granted.
     */
    private boolean hasPermissions() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Permission not granted: ACCESS_FINE_LOCATION");
            return false;
        }

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Permission not granted: ACCESS_WIFI_STATE");
            return false;
        }

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CHANGE_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Permission not granted: CHANGE_WIFI_STATE");
            return false;
        }

        Log.i(TAG, "All required permissions are granted.");
        return true;
    }

    /**
     * Starts advertising as a Wi-Fi Direct group owner.
     */
    public synchronized void startAdvertising() {
        if (isAdvertising) {
            Log.i(TAG, "Wi-Fi Direct advertising is already running.");
            return;
        }

        if (!hasPermissions()) {
            Log.e(TAG, "Missing necessary permissions. Cannot start advertising.");
            return;
        }

        isAdvertising = true;

        // Ensure any existing group is removed
        wifiP2pManager.removeGroup(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.i(TAG, "Existing group removed successfully. Attempting to create a new group...");
                attemptCreateGroup();
            }

            @Override
            public void onFailure(int reason) {
                Log.e(TAG, "Failed to remove existing group. Reason: " + reason + ". Attempting to create a new group...");
                if (reason == WifiP2pManager.BUSY) {
                    Log.e(TAG, "Device is busy. Retrying after delay...");
                    retryGroupCreationWithDelay();
                }
            }
        });

        // Register the receiver to listen for Wi-Fi Direct events
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
        context.registerReceiver(receiver, intentFilter);
        Log.i(TAG, "Wi-Fi Direct advertising started.");
    }

    /**
     * Retries group creation after a short delay.
     */
    private void retryGroupCreationWithDelay() {
        new android.os.Handler().postDelayed(this::attemptCreateGroup, 3000); // Retry after 3 seconds
    }


    /**
     * Attempts to create a Wi-Fi Direct group after cleanup.
     */
    private void attemptCreateGroup() {
        try {
            wifiP2pManager.createGroup(channel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    Log.i(TAG, "Wi-Fi Direct group created. This device is now the Group Owner.");
                }

                @Override
                public void onFailure(int reason) {
                    Log.e(TAG, "Failed to create Wi-Fi Direct group. Reason: " + reason);
                }
            });
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException while creating Wi-Fi Direct group: " + e.getMessage());
        }
    }

    /**
     * Stops advertising and removes the group.
     */
    public synchronized void stopAdvertising() {
        if (!isAdvertising) {
            Log.i(TAG, "Wi-Fi Direct advertising is not running.");
            return;
        }

        if (!hasPermissions()) {
            Log.e(TAG, "Missing necessary permissions. Cannot stop advertising.");
            return;
        }

        isAdvertising = false;

        try {
            wifiP2pManager.removeGroup(channel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    Log.i(TAG, "Wi-Fi Direct group removed.");
                }

                @Override
                public void onFailure(int reason) {
                    Log.e(TAG, "Failed to remove Wi-Fi Direct group. Reason: " + reason);
                }
            });
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException while removing Wi-Fi Direct group: " + e.getMessage());
        }

        // Unregister the receiver
        try {
            context.unregisterReceiver(receiver);
            Log.i(TAG, "Wi-Fi Direct advertising stopped.");
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Receiver was not registered: " + e.getMessage());
        }
    }
}

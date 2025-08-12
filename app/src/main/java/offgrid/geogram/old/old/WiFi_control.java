package offgrid.geogram.old.old;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;

import java.util.ArrayList;

import android.bluetooth.BluetoothAdapter;
import android.provider.Settings;

import offgrid.geogram.core.Central;
import offgrid.geogram.MainActivity;

public class WiFi_control {
    private static final String TAG = "WiFi_control";
    private static final String SERVICE_ID = "nostr.tio";
    public static final int PERMISSION_REQUEST_CODE = 1;

    private WifiP2pManager wifiP2pManager;
    private WifiP2pManager.Channel channel;
    private BroadcastReceiver receiver;
    private IntentFilter intentFilter;
    private Context context;

    private boolean startedAdvertising = false;

    // list of found devices
    public ArrayList<String> itemList = new ArrayList<>();
    public ArrayAdapter<String> peersAdapter;

    public WiFi_control(Context context) {
        this.context = context;

        // Initialize Wi-Fi P2P
        wifiP2pManager = (WifiP2pManager) context.getSystemService(Context.WIFI_P2P_SERVICE);
        if (wifiP2pManager == null) {
            message("This device does not support Wi-Fi Direct");
            return;
        }
        channel = wifiP2pManager.initialize(context, context.getMainLooper(), null);

        // Set up intent filter
        intentFilter = new IntentFilter();
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        receiver = new WiFiDirectBroadcastReceiver(wifiP2pManager, channel, this);
    }

    private void message(String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        Log.d(TAG, message);
    }

    private void log(String message) {
        Log.d(TAG, message);
    }

    public void startAdvertising() {
        // avoid multiple starts (only one is accepted)
        if(startedAdvertising == true){
            return;
        }
        AdvertisingOptions advertisingOptions = new AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build();
        Nearby.getConnectionsClient(context)
                .startAdvertising(getLocalUserName(), SERVICE_ID, connectionLifecycleCallback, advertisingOptions)
                .addOnSuccessListener(unused -> log("Advertising started"))
                .addOnFailureListener(e -> message("Advertising failed"));
        // no need to repeat this method
        startedAdvertising = true;
    }

//    public void startDiscovery() {
//        Nearby.getConnectionsClient(context)
//                .startDiscovery(SERVICE_ID, endpointDiscoveryCallback, new DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build())
//                .addOnSuccessListener(unused -> message("Discovery started"))
//                .addOnFailureListener(e -> message("Discovery failed"));
//    }

    public void startDiscovery() {
        DiscoveryOptions discoveryOptions =
                new DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build();
        Nearby.getConnectionsClient(context)
                .startDiscovery(SERVICE_ID, endpointDiscoveryCallback, discoveryOptions)
                .addOnSuccessListener(
                        (Void unused) -> {
                            // We're discovering!
                            log("Discovery started");
                        })
                .addOnFailureListener(
                        (Exception e) -> {
                            // We're unable to start discovering.
                            log("Discovery failed");
                        });
    }



    private String getLocalUserName() {
        // no need to repeat when there is already a name
        if(Central.device_name != null){
            return Central.device_name;
        }
        String deviceName = null;

        // Try to get the Bluetooth name if permission is granted
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED) {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (bluetoothAdapter != null && bluetoothAdapter.getName() != null) {
                deviceName = bluetoothAdapter.getName();
                log("Bluetooth permission was granted");
            }
        } else {
            message("Bluetooth permission not granted");
        }

        if (deviceName == null) {
            // Fallback to Android Device Name
            deviceName = Settings.Secure.getString(context.getContentResolver(), "bluetooth_name");
            if (deviceName == null) {
                deviceName = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
            }
        }

        // save the name for future usage
        Central.device_name = deviceName;
        log("Device name: " + deviceName);
        return deviceName;
    }

//    private String getLocalUserName() {
//        String deviceName = null;
//
//        // Try to get the Bluetooth name if permission is granted
//        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED) {
//            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
//            if (bluetoothAdapter != null && bluetoothAdapter.getName() != null) {
//                deviceName = bluetoothAdapter.getName();
//            }
//        } else {
//            message("Bluetooth permission not granted");
//        }
//
//        if (deviceName == null) {
//            // Fallback to Android Device Name
//            deviceName = Settings.Secure.getString(context.getContentResolver(), "bluetooth_name");
//            if (deviceName == null) {
//                deviceName = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
//            }
//        }
//
//        message("Device name: " + deviceName);
//        return deviceName;
//    }

    private final ConnectionLifecycleCallback connectionLifecycleCallback = new ConnectionLifecycleCallback() {
        @Override
        public void onConnectionInitiated(String endpointId, ConnectionInfo connectionInfo) {
            // Automatically accept the connection on both sides.
            Nearby.getConnectionsClient(context).acceptConnection(endpointId, payloadCallback);
            String endpointName = connectionInfo.getEndpointName();
            message("Connected to: " + endpointName);
            addItem(endpointName);
            if(peersAdapter != null){
                peersAdapter.notifyDataSetChanged();
            }
        }

        @Override
        public void onConnectionResult(String endpointId, ConnectionResolution result) {
            switch (result.getStatus().getStatusCode()) {
                case ConnectionsStatusCodes.STATUS_OK:
                    //message("Connected to: " + endpointId);
                    break;
                case ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED:
                    message("Connection rejected: " + endpointId);
                    break;
                case ConnectionsStatusCodes.STATUS_ERROR:
                    message("Connection error: " + endpointId);
                    break;
                default:
                    message("Unknown status code: " + result.getStatus().getStatusCode());
            }
        }

        @Override
        public void onDisconnected(String endpointId) {
            message("Disconnected from endpoint: " + endpointId);
        }
    };

    /**
     * Adds a device on the list of devices that were found
     * @param endpointId
     */
    private void addItem(String endpointId) {
        // avoid duplicates
        for(String item: itemList){
            if(item.startsWith(endpointId)){
                log("Already added " + endpointId);
                return;
            }
        }
        // can be added
        itemList.add(endpointId);
    }

    private final PayloadCallback payloadCallback = new PayloadCallback() {
        @Override
        public void onPayloadReceived(String endpointId, Payload payload) {
            message("Payload received from endpoint: " + endpointId);
        }

        @Override
        public void onPayloadTransferUpdate(String endpointId, PayloadTransferUpdate update) {
            message("Payload transfer update from endpoint: " + endpointId);
        }
    };

    public static void checkAndRequestPermissions(MainActivity act) {
        String[] permissions;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // API 31+
            permissions = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.CHANGE_WIFI_STATE,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.NEARBY_WIFI_DEVICES,
                    Manifest.permission.FOREGROUND_SERVICE
            };
        } else {
            permissions = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.CHANGE_WIFI_STATE
            };
        }

        // Check which permissions are not granted
        boolean allPermissionsGranted = true;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(act, permission) != PackageManager.PERMISSION_GRANTED) {
                //message(permission + " permission not granted");
                allPermissionsGranted = false;
            }
        }

        // Request the necessary permissions if they are not granted
        if (!allPermissionsGranted) {
            ActivityCompat.requestPermissions(act, permissions, PERMISSION_REQUEST_CODE);
        }
    }

    /**
     * Checks if the user has granted the needed permissions to continue
     * @param act
     * @return true when all is ready
     */
    public static boolean hasNecessaryPermissions(AppCompatActivity act) {
        String[] permissions;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // API 31+
            permissions = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.CHANGE_WIFI_STATE,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.NEARBY_WIFI_DEVICES
            };
        } else {
            permissions = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.CHANGE_WIFI_STATE
            };
        }

        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(act, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }


    private final EndpointDiscoveryCallback endpointDiscoveryCallback = new EndpointDiscoveryCallback() {
        @Override
        public void onEndpointFound(String endpointId, DiscoveredEndpointInfo info) {
            // An endpoint was found. We request a connection to it.
            Nearby.getConnectionsClient(context)
                    .requestConnection(getLocalUserName(), endpointId, connectionLifecycleCallback)
                    .addOnSuccessListener(unused -> connectionRequested())
                    .addOnFailureListener(e -> message("Connection request failed"));
        }

        @Override
        public void onEndpointLost(String endpointId) {
            // A previously discovered endpoint has gone away.
            log("Endpoint lost: " + endpointId);
        }
    };

    private void connectionRequested() {
        // log("Connection requested")
    }

    public void registerReceiver() {
        context.registerReceiver(receiver, intentFilter);
    }

    public void unregisterReceiver() {
        context.unregisterReceiver(receiver);
    }

    public boolean isWiFiDirectFunctioning() {

        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null || !wifiManager.isWifiEnabled()) {
            message("Wi-Fi is not enabled");
            return false;
        }

        WifiP2pManager wifiP2pManager = (WifiP2pManager) context.getSystemService(Context.WIFI_P2P_SERVICE);
        if (wifiP2pManager == null) {
            message("Wi-Fi Direct is not supported on this device");
            return false;
        }

        WifiP2pManager.Channel channel = wifiP2pManager.initialize(context, context.getMainLooper(), null);
        if (channel == null) {
            message("Failed to create a channel for Wi-Fi Direct");
            return false;
        }

        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {
            message("Necessary permissions are not granted");
            return false;
        }

        try {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
            intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
            intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
            intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

            final boolean[] isAvailable = {false};
            wifiP2pManager.requestPeers(channel, peers -> {
                if (peers != null && !peers.getDeviceList().isEmpty()) {
                    isAvailable[0] = true;
                } else {
                    isAvailable[0] = false;
                }
            });

            return isAvailable[0];
        } catch (SecurityException e) {
            message("SecurityException: " + e.getMessage());
            return false;
        }
    }

    public static class WiFiDirectBroadcastReceiver extends BroadcastReceiver {
        private WifiP2pManager wifiP2pManager;
        private WifiP2pManager.Channel channel;
        private WiFi_control wifiControl;

        public WiFiDirectBroadcastReceiver(WifiP2pManager wifiP2pManager, WifiP2pManager.Channel channel, WiFi_control wifiControl) {
            this.wifiP2pManager = wifiP2pManager;
            this.channel = channel;
            this.wifiControl = wifiControl;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
                int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
                if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                    //log("Wi-Fi P2P is enabled");
                } else {
                    wifiControl.message("Wi-Fi P2P is not enabled");
                }
            } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
                if (wifiP2pManager != null) {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        wifiControl.message("Location permission is required for Wi-Fi Direct functionality");
                        return;
                    }

                    wifiP2pManager.requestPeers(channel, peerList -> {
                        if (peerList.getDeviceList().isEmpty()) {
                           // wifiControl.message("No devices found");
                        } else {
                            wifiControl.message("Peers found");
                        }
                    });
                }
            } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
                if (wifiP2pManager == null) {
                    return;
                }

               // wifiP2pManager.requestConnectionInfo(channel, wifiControl::onConnectionInfoAvailable);
            } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
                // Respond to this device's wifi state changing
            }
        }
    }
}

package offgrid.geogram.wifi;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSpecifier;
import android.net.wifi.WifiNetworkSuggestion;
import android.os.Build;

import androidx.annotation.NonNull;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import offgrid.geogram.core.Log;

/**
 * Singleton class for connecting to a Wi-Fi Direct network using known SSID and password,
 * retrieving the peer's IP address, making HTTP requests, managing previous connections, and disconnecting.
 */
public class WiFiDirectConnector {

    private static final String TAG = "WiFiDirectConnector";

    private static WiFiDirectConnector instance;

    private final Context context;
    private ConnectivityManager.NetworkCallback networkCallback;
    private final Map<String, ConnectivityManager.NetworkCallback>
            networkCallbacks = new ConcurrentHashMap<>();

    private WifiManager wifiManager;
    private String previousSsid = null;
    private int previousNetworkId = -1;

    private String peerIpAddress;

    private WiFiDirectConnector(Context context) {
        this.context = context.getApplicationContext();
        this.wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
    }

    /**
     * Returns the singleton instance of WiFiDirectConnector.
     *
     * @param context the application context
     * @return the singleton instance
     */
    public static synchronized WiFiDirectConnector getInstance(Context context) {
        if (instance == null) {
            instance = new WiFiDirectConnector(context);
        }
        return instance;
    }

    /**
     * Saves the current Wi-Fi connection (SSID and Network ID) and suggests it for reconnection.
     */
    public void saveCurrentConnection() {
        if (wifiManager != null) {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            if (wifiInfo != null && wifiInfo.getNetworkId() != -1) {
                previousSsid = wifiInfo.getSSID().replace("\"", "");
                previousNetworkId = wifiInfo.getNetworkId();
                Log.i(TAG, "Saved current connection: SSID=" + previousSsid + ", Network ID=" + previousNetworkId);
                suggestPreviousNetwork();
            } else {
                Log.e(TAG, "No current Wi-Fi connection to save.");
            }
        }
    }

    /**
     * Suggests the previously saved network for automatic reconnection.
     *
     * @return true if the suggestion was successfully added, false otherwise
     */
    private boolean suggestPreviousNetwork() {
        if (previousSsid == null || previousSsid.isEmpty()) {
            Log.e(TAG, "No previously saved network to suggest.");
            return false;
        }

        WifiNetworkSuggestion suggestion = new WifiNetworkSuggestion.Builder()
                .setSsid(previousSsid)
                .build();

        int status = wifiManager.addNetworkSuggestions(Collections.singletonList(suggestion));
        if (status == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
            Log.i(TAG, "Network suggestion added for SSID=" + previousSsid);
            return true;
        } else {
            Log.e(TAG, "Failed to add network suggestion. Status: " + status);
            return false;
        }
    }

    /**
     * Reconnects to the previously saved Wi-Fi connection.
     *
     * @return true if reconnection was successfully requested, false otherwise
     */
    public boolean reconnectToPreviousNetwork() {
        if (previousSsid == null || previousSsid.isEmpty()) {
            Log.e(TAG, "No previously saved network to reconnect to.");
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

            WifiNetworkSpecifier specifier = new WifiNetworkSpecifier.Builder()
                    .setSsid(previousSsid)
                    .build();

            NetworkRequest request = new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .setNetworkSpecifier(specifier)
                    .build();

            CountDownLatch latch = new CountDownLatch(1);
            final boolean[] success = {false};

            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(@NonNull Network network) {
                    Log.i(TAG, "Reconnected to the previous network: SSID=" + previousSsid);
                    connectivityManager.bindProcessToNetwork(network);
                    success[0] = true;
                    latch.countDown();
                }

                @Override
                public void onUnavailable() {
                    Log.e(TAG, "Failed to reconnect to the previous network: SSID=" + previousSsid);
                    success[0] = false;
                    latch.countDown();
                }
            };

            connectivityManager.requestNetwork(request, networkCallback);

            try {
                latch.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Log.e(TAG, "Connection interrupted: " + e.getMessage());
                return false;
            }

            return success[0];
        } else {
            Log.e(TAG, "Reconnect to previous network requires Android Q or above.");
            return false;
        }
    }

    /**
     * Connects to a Wi-Fi Direct network using known SSID and password.
     *
     * @param ssid     the SSID of the Wi-Fi Direct network
     * @param password the password of the Wi-Fi Direct network
     * @return true if the connection was successful, false otherwise
     */
    public boolean connectToNetwork(String ssid, String password) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            WifiNetworkSpecifier specifier = new WifiNetworkSpecifier.Builder()
                    .setSsid(ssid)
                    .setWpa2Passphrase(password)
                    .build();

            NetworkRequest request = new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .setNetworkSpecifier(specifier)
                    .build();

            ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

            CountDownLatch latch = new CountDownLatch(1);
            final boolean[] result = {false};

            // Check if a previous callback for this SSID exists and unregister it
            if (networkCallbacks.containsKey(ssid)) {
                connectivityManager.unregisterNetworkCallback(networkCallbacks.get(ssid));
                networkCallbacks.remove(ssid);
            }

            ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(@NonNull Network network) {
                    Log.i(TAG, "Connected to Wi-Fi Direct network: " + ssid);
                    connectivityManager.bindProcessToNetwork(network);
                    result[0] = true;
                    latch.countDown();
                }

                @Override
                public void onUnavailable() {
                    Log.e(TAG, "Failed to connect to Wi-Fi Direct network: " + ssid);
                    result[0] = false;
                    latch.countDown();
                }

                @Override
                public void onLost(@NonNull Network network) {
                    Log.i(TAG, "Wi-Fi Direct network lost: " + ssid);
                    // Optionally handle the network being lost
                }
            };

            networkCallbacks.put(ssid, networkCallback); // Track the callback for this SSID

            try {
                connectivityManager.requestNetwork(request, networkCallback);

                // Wait for connection result with a timeout
                if (!latch.await(30, TimeUnit.SECONDS)) {
                    Log.e(TAG, "Connection to Wi-Fi Direct network timed out: " + ssid);
                    result[0] = false;
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "Connection interrupted: " + e.getMessage());
                return false;
            } finally {
                // Ensure callback is removed after the connection attempt
                connectivityManager.unregisterNetworkCallback(networkCallback);
                networkCallbacks.remove(ssid);
            }

            return result[0];
        } else {
            Log.e(TAG, "Connecting to a network requires Android Q or above.");
            return false;
        }
    }

    /**
     * Retrieves the current IP address of the device when connected to a Wi-Fi network.
     *
     * @return the current IP address as a string, or null if unavailable
     */
    public String getCurrentIpAddress() {
        try {
            if (wifiManager != null) {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                if (wifiInfo != null) {
                    int ipAddress = wifiInfo.getIpAddress();
                    // Convert the packed integer to an IPv4 address
                    return InetAddress.getByAddress(new byte[]{
                            (byte) (ipAddress & 0xFF),
                            (byte) ((ipAddress >> 8) & 0xFF),
                            (byte) ((ipAddress >> 16) & 0xFF),
                            (byte) ((ipAddress >> 24) & 0xFF)
                    }).getHostAddress();
                }
            }
        } catch (Exception e) {
            Log.e("IPUtils", "Error retrieving IP address: " + e.getMessage());
        }
        return null;
    }

    /**
     * Retrieves the DHCP server IP address of the current Wi-Fi connection.
     *
     * @return the DHCP server IP address as a string, or null if unavailable
     */
    public String getDhcpServerIpAddress() {
        try {
            if (wifiManager != null) {
                DhcpInfo dhcpInfo = wifiManager.getDhcpInfo();
                if (dhcpInfo != null) {
                    return InetAddress.getByAddress(new byte[]{
                            (byte) (dhcpInfo.serverAddress & 0xFF),
                            (byte) ((dhcpInfo.serverAddress >> 8) & 0xFF),
                            (byte) ((dhcpInfo.serverAddress >> 16) & 0xFF),
                            (byte) ((dhcpInfo.serverAddress >> 24) & 0xFF)
                    }).getHostAddress();
                }
            }
        } catch (Exception e) {
            Log.e("IPUtils", "Error retrieving DHCP server IP address: " + e.getMessage());
        }
        return null;
    }

    /**
     * Fetches a web page from the peer device.
     *
     * @param ipAddress the IP address of the peer device
     * @param port      the port number on which the web server is running
     * @param path      the path of the resource to fetch
     */
    public void fetchWebPage(String ipAddress, int port, String path) {
        try {
            String urlString = "http://" + ipAddress + ":" + port + "/" + path;
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            Log.i(TAG, "Fetching web page: " + urlString);
            int responseCode = connection.getResponseCode();
            Log.i(TAG, "Response Code: " + responseCode);

            if (responseCode == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                Log.i(TAG, "Response: " + response.toString());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error fetching web page: " + e.getMessage());
        }
    }

    /**
     * Disconnects from the Wi-Fi Direct network and releases the resources.
     */
    public void disconnect() {
        try {
            ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (networkCallback != null) {
                    connectivityManager.unregisterNetworkCallback(networkCallback);
                    networkCallback = null;
                }
            }
            if (wifiManager != null) {
                wifiManager.disconnect();
            }
            Log.i(TAG, "Disconnected from the Wi-Fi Direct network.");
        } catch (Exception e) {
            Log.e(TAG, "Error disconnecting from the Wi-Fi Direct network: " + e.getMessage());
        }
    }
}

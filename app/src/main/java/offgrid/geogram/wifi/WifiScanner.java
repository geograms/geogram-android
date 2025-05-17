package offgrid.geogram.wifi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import android.content.pm.PackageManager;

import java.util.List;

import offgrid.geogram.util.WiFiUtils;
import offgrid.geogram.wifi.details.Coordinates;
import offgrid.geogram.wifi.details.WiFiNetwork;
import offgrid.geogram.wifi.details.WiFiType;

/**
 * Singleton class for passively receiving available Wi-Fi networks and storing the results.
 */
public class WifiScanner {

    private static final String TAG = "WifiScanner";

    private static WifiScanner instance;

    private final Context context;
    private final WifiManager wifiManager;
    private boolean isRunning = false;

    private final Handler handler = new Handler();

    private WifiScanner(Context context) {
        this.context = context.getApplicationContext();
        this.wifiManager = (WifiManager) this.context.getSystemService(Context.WIFI_SERVICE);

        // Register BroadcastReceiver for Wi-Fi scan results
        IntentFilter filter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        BroadcastReceiver wifiScanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(intent.getAction())) {
                    Log.i(TAG, "SCAN_RESULTS_AVAILABLE_ACTION received.");
                    updateWifiNetworks();
                }
            }
        };
        this.context.registerReceiver(wifiScanReceiver, filter);
    }

    /**
     * Returns the singleton instance of WifiScanner.
     *
     * @param context the application context
     * @return the singleton instance
     */
    public static synchronized WifiScanner getInstance(Context context) {
        if (instance == null) {
            instance = new WifiScanner(context);
        }
        return instance;
    }

    /**
     * Starts the passive Wi-Fi scanning by enabling the BroadcastReceiver and scheduling active scans.
     */
    public synchronized void startScanning() {
        if (!isRunning) {
            if (!wifiManager.isWifiEnabled()) {
                Log.e(TAG, "Wi-Fi is disabled. Enable Wi-Fi to start scanning.");
                return;
            }
            if (!hasPermissions()) {
                Log.e(TAG, "Missing necessary permissions. Cannot start Wi-Fi scanning.");
                return;
            }
            if (!isLocationEnabled()) {
                Log.e(TAG, "Location services are disabled. Cannot start Wi-Fi scanning.");
                return;
            }
            isRunning = true;
            Log.i(TAG, "Passive Wi-Fi scanning started.");

            // Schedule an initial active scan after 10 seconds
            handler.postDelayed(this::triggerActiveScan, 10000);

            // Schedule recurring active scans every 1 minute
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (isRunning) {
                        triggerActiveScan();
                        handler.postDelayed(this, 60000); // Schedule next scan in 1 minute
                    }
                }
            }, 60000);
        }
    }

    /**
     * Stops receiving Wi-Fi scan results and cancels active scan tasks.
     */
    public synchronized void stopScanning() {
        if (isRunning) {
            isRunning = false;
            handler.removeCallbacksAndMessages(null);
            Log.i(TAG, "Passive Wi-Fi scanning stopped.");
        }
    }

    /**
     * Triggers an active Wi-Fi scan.
     */
    private void triggerActiveScan() {
        if (!wifiManager.isWifiEnabled()) {
            Log.e(TAG, "Wi-Fi is disabled. Cannot trigger active scan.");
            return;
        }

        boolean scanStarted = wifiManager.startScan();
        if (scanStarted) {
            Log.i(TAG, "Active Wi-Fi scan triggered successfully.");
        } else {
            Log.e(TAG, "Failed to trigger active Wi-Fi scan.");
        }
    }

    /**
     * Updates the list of available Wi-Fi networks and logs the number of networks found.
     */
    private void updateWifiNetworks() {
        if (!hasPermissions()) {
            Log.e(TAG, "Missing permissions during Wi-Fi scan. Cannot retrieve results.");
            return;
        }

        try {
            List<ScanResult> results = wifiManager.getScanResults();
            Log.i(TAG, "Scan result count: " + (results != null ? results.size() : "null"));
            // get the current coordinates
            Coordinates coordinates = Coordinates.createFromContext(this.context);
            if (results != null) {
                for (ScanResult result : results) {
                    addReachableNetwork(result, coordinates);
                }
            }
            Log.i(TAG, "Wi-Fi Scan Complete: "
                    + WiFiDatabase.getInstance(context).getNumberOfNetworksReachable()
                    + " networks are listed");
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException while retrieving Wi-Fi scan results: " + e.getMessage());
        }
    }

    private void addReachableNetwork(ScanResult result, Coordinates coordinates) {
        String SSID = result.SSID;
        String BSSID = result.BSSID;
        String capabilities = result.capabilities;

        WiFiNetwork network = new WiFiNetwork();
        String hash = WiFiUtils.generateHashSSID(SSID);
        network.setSSIDhash(hash);
        network.setSSID(SSID);
        network.setBSSID(BSSID);
        network.setCapabilities(capabilities);
        network.setType(WiFiType.UNKNOWN);

        // mark the position for this device
        if(coordinates != null){
            network.setPositionRecent(coordinates);
        }

        WiFiDatabase.getInstance(context).addNetworkReachable(network);
    }

    private boolean hasPermissions() {
        boolean fineLocationGranted = ActivityCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean wifiStateGranted = ActivityCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_WIFI_STATE) == PackageManager.PERMISSION_GRANTED;
        boolean changeWifiGranted = ActivityCompat.checkSelfPermission(context, android.Manifest.permission.CHANGE_WIFI_STATE) == PackageManager.PERMISSION_GRANTED;

        Log.i(TAG, "Permissions check: ACCESS_FINE_LOCATION=" + fineLocationGranted +
                ", ACCESS_WIFI_STATE=" + wifiStateGranted + ", CHANGE_WIFI_STATE=" + changeWifiGranted);

        return fineLocationGranted && wifiStateGranted && changeWifiGranted;
    }

    private boolean isLocationEnabled() {
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        boolean locationEnabled = locationManager != null && (
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        );
        Log.i(TAG, "Location services enabled: " + locationEnabled);
        return locationEnabled;
    }
}

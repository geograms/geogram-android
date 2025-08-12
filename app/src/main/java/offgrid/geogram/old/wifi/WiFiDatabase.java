package offgrid.geogram.old.wifi;

import static offgrid.geogram.old.wifi.details.WiFiGuessing.detectNetworkType;

import android.content.Context;

import java.util.HashMap;

import offgrid.geogram.core.Log;
import offgrid.geogram.old.wifi.details.WiFiNetwork;

/**
 * Singleton class for managing Wi-Fi database operations.
 */
public class WiFiDatabase {

    // List of currently reachable Wi-Fi hosts
    // String is the two-byte hash of the host SSID
    private final HashMap<String, WiFiNetwork> networksReachable = new HashMap<>();

    // Static instance of the singleton
    private static WiFiDatabase instance;
    private static final String TAG = "WiFiDatabase";

    // Stored context
    private final Context context;

    /**
     * Private constructor to prevent direct instantiation.
     *
     * @param context the application context
     */
    private WiFiDatabase(Context context) {
        this.context = context.getApplicationContext(); // Use Application Context to prevent memory leaks
    }

    /**
     * Returns the singleton instance of WiFiDatabase.
     *
     * @param context the application context
     * @return the singleton instance
     */
    public static synchronized WiFiDatabase getInstance(Context context) {
        if (instance == null) {
            instance = new WiFiDatabase(context);
        }
        return instance;
    }

    /**
     * Adds a Wi-Fi host to the database.
     */
    public void addNetworkReachable(WiFiNetwork network) {
        if(network.isNotMinimallyComplete()){
            //Log.i(TAG, "Network not complete: " + network.getSSID());
            return;
        }

        for(WiFiNetwork networkExisting : networksReachable.values()){
            if(networkExisting.getSSID().equals(network.getSSID()) == false){
                continue;
            }
            // there is a match
            networkExisting.setTimeLastSeen(System.currentTimeMillis());
            if(network.getPassword() != null){
                networkExisting.setPassword(network.getPassword());
            }
            return;
        }

        // add our metadata
        network.setTimeFirstSeen(System.currentTimeMillis());
        network.setTimeLastSeen(System.currentTimeMillis());

        // detect type of network and try to improve the data
        detectNetworkType(network);


        // Place the network on the reachable list
        networksReachable.put(network.getSSIDhash(), network);
        Log.i(TAG, "Added: " + network.getSSID());
    }

    /**
     * Gets a Wi-Fi host from the database.
     *
     * @param ssidHash the hash of the host SSID
     * @return the Wi-Fi host or null if not found
     */
    public WiFiNetwork getReachableNetwork(String ssidHash) {
        return networksReachable.get(ssidHash);
    }

    /**
     * Clears the list of reachable networks.
     */
    public void clearReachableNetworks() {
        networksReachable.clear();
    }

    /**
     * Gets the stored application context.
     *
     * @return the application context
     */
    public Context getContext() {
        return context;
    }

    public String getNumberOfNetworksReachable() {
        return String.valueOf(networksReachable.size());
    }
}

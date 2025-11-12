package offgrid.geogram.wifi;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import offgrid.geogram.util.NetworkUtils;
import offgrid.geogram.devices.ConnectionType;
import offgrid.geogram.devices.DeviceManager;
import offgrid.geogram.devices.DeviceType;
import offgrid.geogram.devices.EventConnected;

/**
 * Discovers Geogram devices on the local WiFi network by scanning IP range.
 *
 * - Scans x.x.x.1-254 on the local subnet
 * - Tests HTTP API endpoint on port 45678
 * - Stores discovered devices with their IP addresses
 * - Re-scans every 2 minutes for new devices
 */
public class WiFiDiscoveryService {
    private static final String TAG = "WiFiDiscovery";
    private static final int API_PORT = 45678;
    private static final String API_STATUS_ENDPOINT = "/api/status";
    private static final int HTTP_TIMEOUT_MS = 2000; // 2 second timeout per IP
    private static final long SCAN_INTERVAL_SECONDS = 120L; // 2 minutes
    private static final int SCAN_THREAD_POOL_SIZE = 20; // Scan 20 IPs concurrently

    // Singleton instance
    private static WiFiDiscoveryService instance;
    private final Context context;

    // Discovered devices: callsign -> IP address
    private final Map<String, String> discoveredDevices = new HashMap<>();

    // Scheduled executor for periodic scans
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "WiFiDiscoveryScheduler");
            t.setDaemon(true);
            return t;
        }
    });

    private ScheduledFuture<?> scanTask;
    private boolean isRunning = false;

    private WiFiDiscoveryService(Context context) {
        this.context = context.getApplicationContext();
        // Load previously discovered devices from SharedPreferences
        loadDiscoveredDevices();
    }

    public static synchronized WiFiDiscoveryService getInstance(Context context) {
        if (instance == null) {
            instance = new WiFiDiscoveryService(context);
        }
        return instance;
    }

    /**
     * Start periodic WiFi network scanning
     */
    public synchronized void start() {
        if (isRunning) {
            Log.d(TAG, "WiFi discovery already running");
            return;
        }

        isRunning = true;

        // Quick ping previously discovered devices first (faster startup)
        scheduler.execute(this::quickPingPreviousDevices);

        // Then run full scan
        scheduler.execute(this::scanNetwork);

        // Schedule periodic scans every 2 minutes
        scanTask = scheduler.scheduleWithFixedDelay(
            this::scanNetwork,
            SCAN_INTERVAL_SECONDS,
            SCAN_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        );

        Log.i(TAG, "WiFi discovery started (scanning every " + SCAN_INTERVAL_SECONDS + "s)");
    }

    /**
     * Stop periodic scanning
     */
    public synchronized void stop() {
        if (!isRunning) {
            return;
        }

        if (scanTask != null) {
            scanTask.cancel(false);
            scanTask = null;
        }

        isRunning = false;
        Log.i(TAG, "WiFi discovery stopped");
    }

    /**
     * Check if a device with given IP is already discovered
     */
    public synchronized boolean isDiscovered(String ipAddress) {
        return discoveredDevices.containsValue(ipAddress);
    }

    /**
     * Get IP address for a discovered device
     */
    public synchronized String getDeviceIp(String callsign) {
        return discoveredDevices.get(callsign);
    }

    /**
     * Get all discovered devices
     */
    public synchronized Map<String, String> getDiscoveredDevices() {
        return new HashMap<>(discoveredDevices);
    }

    /**
     * Scan the local network for Geogram devices
     */
    private void scanNetwork() {
        try {
            Log.i(TAG, "=== Starting WiFi network scan ===");

            // Get local IP address
            String localIp = NetworkUtils.getIPAddress();
            if (localIp == null || localIp.isEmpty() ||
                localIp.equals("Not available") || localIp.startsWith("Error")) {
                Log.w(TAG, "Could not determine local IP address, skipping scan: " + localIp);
                return;
            }

            Log.i(TAG, "Local IP: " + localIp);

            // Extract subnet (e.g., "192.168.1" from "192.168.1.42")
            String subnet = getSubnet(localIp);
            if (subnet == null) {
                Log.w(TAG, "Invalid IP address format: " + localIp);
                return;
            }

            Log.i(TAG, "Scanning subnet: " + subnet + ".0/24");

            // Create thread pool for concurrent scanning
            ExecutorService scanPool = Executors.newFixedThreadPool(SCAN_THREAD_POOL_SIZE);
            AtomicInteger foundCount = new AtomicInteger(0);

            // Scan all IPs in range (1-254)
            for (int i = 1; i <= 254; i++) {
                final String targetIp = subnet + "." + i;

                // Skip our own IP
                if (targetIp.equals(localIp)) {
                    continue;
                }

                scanPool.execute(() -> {
                    if (checkGeogramDevice(targetIp)) {
                        foundCount.incrementAndGet();
                    }
                });
            }

            // Wait for all scans to complete (max 30 seconds)
            scanPool.shutdown();
            if (!scanPool.awaitTermination(30, TimeUnit.SECONDS)) {
                Log.w(TAG, "Scan timeout - some IPs may not have been checked");
                scanPool.shutdownNow();
            }

            Log.i(TAG, "=== WiFi scan complete === Found " + foundCount.get() + " devices");
            Log.i(TAG, "Total discovered devices: " + discoveredDevices.size());

        } catch (Exception e) {
            Log.e(TAG, "Error during network scan: " + e.getMessage(), e);
        }
    }

    /**
     * Extract subnet from IP address (e.g., "192.168.1" from "192.168.1.42")
     */
    private String getSubnet(String ipAddress) {
        String[] parts = ipAddress.split("\\.");
        if (parts.length != 4) {
            return null;
        }
        return parts[0] + "." + parts[1] + "." + parts[2];
    }

    /**
     * Check if an IP address is running Geogram HTTP API
     * @return true if Geogram device found
     */
    private boolean checkGeogramDevice(String ipAddress) {
        HttpURLConnection conn = null;
        try {
            // Try to connect to Geogram API status endpoint
            URL url = new URL("http://" + ipAddress + ":" + API_PORT + API_STATUS_ENDPOINT);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(HTTP_TIMEOUT_MS);
            conn.setReadTimeout(HTTP_TIMEOUT_MS);

            int responseCode = conn.getResponseCode();

            if (responseCode == 200) {
                // Read response to extract callsign
                java.io.InputStream is = conn.getInputStream();
                java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
                String response = s.hasNext() ? s.next() : "";
                is.close();

                // Parse JSON response to get device info
                // Response format: {"success":true,"server":"Geogram HTTP API",...}
                if (response.contains("Geogram HTTP API") || response.contains("\"success\":true")) {
                    String callsign = extractCallsign(response, ipAddress);
                    onGeogramDeviceFound(ipAddress, callsign);
                    return true;
                }
            }

        } catch (IOException e) {
            // Connection failed - not a Geogram device or device is offline
            // This is expected for most IPs, so don't log it
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }

        return false;
    }

    /**
     * Extract callsign from API response
     */
    private String extractCallsign(String jsonResponse, String ipAddress) {
        try {
            // Parse JSON to extract callsign
            // Response format: {"success":true,"server":"Geogram HTTP API","callsign":"X18SC8",...}
            if (jsonResponse.contains("\"callsign\"")) {
                int callsignStart = jsonResponse.indexOf("\"callsign\":\"") + 12;
                int callsignEnd = jsonResponse.indexOf("\"", callsignStart);
                if (callsignStart > 12 && callsignEnd > callsignStart) {
                    String callsign = jsonResponse.substring(callsignStart, callsignEnd);
                    if (!callsign.isEmpty()) {
                        return callsign;
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to extract callsign from response: " + e.getMessage());
        }

        // Fallback: use IP-based identifier if callsign not available
        return "WIFI-" + ipAddress.replace(".", "-");
    }

    /**
     * Called when a Geogram device is found on the network
     */
    private synchronized void onGeogramDeviceFound(String ipAddress, String callsign) {
        // Check if already discovered
        if (discoveredDevices.containsKey(callsign)) {
            String existingIp = discoveredDevices.get(callsign);
            if (!existingIp.equals(ipAddress)) {
                Log.i(TAG, "Device " + callsign + " changed IP: " + existingIp + " -> " + ipAddress);
                discoveredDevices.put(callsign, ipAddress);
            }
            return;
        }

        // New device discovered
        Log.i(TAG, "✓ Discovered Geogram device: " + callsign + " at " + ipAddress);
        discoveredDevices.put(callsign, ipAddress);

        // Save to persistent storage
        saveDiscoveredDevices();

        // Register device with DeviceManager on main thread to avoid UI update issues
        android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        mainHandler.post(() -> {
            EventConnected event = new EventConnected(ConnectionType.WIFI, null);
            DeviceManager.getInstance().addNewLocationEvent(
                callsign,
                DeviceType.INTERNET_IGATE, // WiFi devices are internet-capable
                event,
                "APP-WIFI" // Device model indicating WiFi connection
            );
        });

        // Store IP address in device metadata
        storeDeviceIpAddress(callsign, ipAddress);
    }

    /**
     * Store IP address in device database
     */
    private void storeDeviceIpAddress(String callsign, String ipAddress) {
        // TODO: Add IP address field to Device entity in database
        // For now, store in memory map
        Log.d(TAG, "Stored IP for " + callsign + ": " + ipAddress);
    }

    /**
     * Manually trigger a network scan
     */
    public void scanNow() {
        scheduler.execute(this::scanNetwork);
    }

    /**
     * Quick ping previously discovered devices on startup
     * This allows immediate communication without waiting for full scan
     */
    private void quickPingPreviousDevices() {
        if (discoveredDevices.isEmpty()) {
            Log.d(TAG, "No previous devices to ping");
            return;
        }

        Log.i(TAG, "Quick-pinging " + discoveredDevices.size() + " previously discovered devices...");

        // Create snapshot to avoid concurrent modification
        Map<String, String> devicesToCheck = new HashMap<>(discoveredDevices);

        int foundCount = 0;
        for (Map.Entry<String, String> entry : devicesToCheck.entrySet()) {
            String callsign = entry.getKey();
            String ipAddress = entry.getValue();

            // Quick check if device is still available
            if (checkGeogramDevice(ipAddress)) {
                foundCount++;
                Log.i(TAG, "✓ Previous device still available: " + callsign + " at " + ipAddress);
            } else {
                // Device not responding - remove from discovered list
                synchronized (this) {
                    discoveredDevices.remove(callsign);
                }
                Log.d(TAG, "✗ Previous device not responding: " + callsign + " at " + ipAddress);
            }
        }

        Log.i(TAG, "Quick-ping complete: " + foundCount + "/" + devicesToCheck.size() + " devices still available");

        // Save updated list
        saveDiscoveredDevices();
    }

    /**
     * Load discovered devices from persistent storage
     */
    private void loadDiscoveredDevices() {
        try {
            android.content.SharedPreferences prefs = context.getSharedPreferences("wifi_discovery", Context.MODE_PRIVATE);
            String devicesJson = prefs.getString("discovered_devices", "{}");

            // Parse JSON manually (simple format: {"callsign":"ip",...})
            if (!devicesJson.equals("{}")) {
                String[] entries = devicesJson.replace("{", "").replace("}", "").split(",");
                for (String entry : entries) {
                    if (entry.contains(":")) {
                        String[] parts = entry.split(":");
                        if (parts.length == 2) {
                            String callsign = parts[0].replace("\"", "").trim();
                            String ip = parts[1].replace("\"", "").trim();
                            discoveredDevices.put(callsign, ip);

                            // IMMEDIATELY register device with DeviceManager
                            // This makes WiFi devices available right away on app startup
                            android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                            mainHandler.post(() -> {
                                EventConnected event = new EventConnected(ConnectionType.WIFI, null);
                                DeviceManager.getInstance().addNewLocationEvent(
                                    callsign,
                                    DeviceType.INTERNET_IGATE,
                                    event,
                                    "APP-WIFI"
                                );
                            });
                        }
                    }
                }
                Log.i(TAG, "Loaded " + discoveredDevices.size() + " previously discovered devices from storage and registered with DeviceManager");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to load discovered devices: " + e.getMessage());
        }
    }

    /**
     * Save discovered devices to persistent storage
     */
    private void saveDiscoveredDevices() {
        try {
            android.content.SharedPreferences prefs = context.getSharedPreferences("wifi_discovery", Context.MODE_PRIVATE);
            android.content.SharedPreferences.Editor editor = prefs.edit();

            // Build simple JSON format
            StringBuilder json = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, String> entry : discoveredDevices.entrySet()) {
                if (!first) json.append(",");
                json.append("\"").append(entry.getKey()).append("\":\"").append(entry.getValue()).append("\"");
                first = false;
            }
            json.append("}");

            editor.putString("discovered_devices", json.toString());
            editor.apply();

            Log.d(TAG, "Saved " + discoveredDevices.size() + " discovered devices to storage");
        } catch (Exception e) {
            Log.e(TAG, "Failed to save discovered devices: " + e.getMessage());
        }
    }
}

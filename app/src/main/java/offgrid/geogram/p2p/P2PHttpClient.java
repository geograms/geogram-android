package offgrid.geogram.p2p;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import offgrid.geogram.core.Log;
import offgrid.geogram.devices.Device;
import offgrid.geogram.devices.DeviceManager;
import offgrid.geogram.settings.ConfigManager;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * HTTP client for making requests to remote devices over local WiFi
 */
public class P2PHttpClient {
    private static final String TAG = "Relay/HttpClient";

    private final Context context;

    public P2PHttpClient(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * Make an HTTP GET request
     *
     * @param deviceId The device ID to connect to
     * @param path The API path (e.g., "/api/collections")
     * @return Response body as String, or null on error
     */
    public HttpResponse get(String deviceId, String path) {
        return get(deviceId, null, path, 10000);
    }

    /**
     * Make an HTTP GET request with custom timeout
     *
     * @param deviceId The device ID to connect to
     * @param path The API path (e.g., "/api/collections")
     * @param timeoutMs Connection timeout in milliseconds
     * @return Response object with status code and body
     */
    public HttpResponse get(String deviceId, String path, int timeoutMs) {
        return get(deviceId, null, path, timeoutMs);
    }

    /**
     * Make an HTTP GET request with optional IP address override
     *
     * @param deviceId The device ID to connect to
     * @param remoteIp Optional IP address to use for WiFi connection (if null, will look up device)
     * @param path The API path (e.g., "/api/collections")
     * @param timeoutMs Connection timeout in milliseconds
     * @return Response object with status code and body
     */
    public HttpResponse get(String deviceId, String remoteIp, String path, int timeoutMs) {
        Log.i(TAG, "");
        Log.i(TAG, "═══════════════════════════════════════════════════════");
        Log.i(TAG, "HTTP GET REQUEST");
        Log.i(TAG, "═══════════════════════════════════════════════════════");
        Log.i(TAG, "Device ID: " + deviceId);
        Log.i(TAG, "Remote IP: " + remoteIp);
        Log.i(TAG, "Path:      " + path);
        Log.i(TAG, "═══════════════════════════════════════════════════════");
        Log.i(TAG, "");

        // Check WiFi status (for local network connections)
        boolean isWifiConnected = isWifiConnected();
        Log.i(TAG, "WiFi connected: " + isWifiConnected);

        // If remoteIp is provided and WiFi is connected, try direct connection first
        if (isWifiConnected && remoteIp != null && !remoteIp.isEmpty()) {
            boolean isLocal = isLocalNetworkAddress(remoteIp);
            Log.i(TAG, "Remote IP '" + remoteIp + "' is local network: " + isLocal);

            if (isLocal) {
                Log.i(TAG, "→ Attempting direct WiFi connection to: " + remoteIp);
                Device tempDevice = new Device(remoteIp, offgrid.geogram.devices.DeviceType.PRIMARY_STATION);
                HttpResponse response = getViaHttp(tempDevice, path, timeoutMs);
                if (response.isSuccess()) {
                    Log.i(TAG, "✓ Direct WiFi connection successful");
                    return response;
                } else {
                    Log.w(TAG, "✗ Direct WiFi connection failed, will try relay");
                }
            }
        }

        // Check if device ID is a valid IP address
        boolean hasValidIp = isValidIpAddress(deviceId);

        if (hasValidIp && isWifiConnected) {
            // Device has IP address and WiFi is connected - try direct connection
            boolean isLocalNetwork = isLocalNetworkAddress(deviceId);

            if (isLocalNetwork) {
                Log.i(TAG, "→ Attempting direct WiFi to local IP: " + deviceId);
                Device tempDevice = new Device(deviceId, offgrid.geogram.devices.DeviceType.PRIMARY_STATION);
                HttpResponse response = getViaHttp(tempDevice, path, timeoutMs);
                if (response.isSuccess()) {
                    Log.i(TAG, "✓ Direct WiFi connection successful");
                    return response;
                } else {
                    Log.w(TAG, "✗ Direct WiFi connection failed, will try relay");
                }
            }
        }

        // WiFi not available or direct connection failed - try relay
        if (!hasValidIp) {
            // Device ID is a callsign (e.g., "X15RJ0") - use relay
            Log.i(TAG, "→ Device ID is callsign '" + deviceId + "' - using relay");
            return tryRelayConnection(deviceId, path, timeoutMs);
        } else {
            // Device ID is an IP but WiFi failed/unavailable - check if callsign available
            Log.i(TAG, "→ Direct connection not available, checking for relay");

            // Try to find device's callsign
            DeviceManager deviceManager = DeviceManager.getInstance();
            for (Device d : deviceManager.getDevicesSpotted()) {
                if (d.ID.equals(deviceId) && d.callsign != null && !d.callsign.isEmpty()) {
                    Log.i(TAG, "→ Found callsign '" + d.callsign + "' for device, using relay");
                    return tryRelayConnection(d.callsign, path, timeoutMs);
                }
            }

            Log.e(TAG, "✗ No relay available for IP-based device: " + deviceId);
            return new HttpResponse(503, "{\"success\": false, \"error\": \"Device not reachable via WiFi or relay\"}");
        }
    }

    /**
     * Ping a device via relay to check if it's reachable
     * @param callsign Device callsign (e.g., X1SQYS)
     * @return true if device responds via relay, false otherwise
     */
    public boolean pingViaRelay(String callsign) {
        if (callsign == null || callsign.isEmpty()) {
            return false;
        }

        try {
            ConfigManager configManager = ConfigManager.getInstance(context);
            String relayUrl = configManager.getConfig().getDeviceRelayServerUrl();

            // Convert WebSocket URL to HTTP URL
            String httpUrl = relayUrl.replace("ws://", "http://").replace("wss://", "https://");
            if (httpUrl.contains(":45679")) {
                httpUrl = httpUrl.replace(":45679", "");
            }

            // Try to ping the device via relay proxy
            String apiUrl = httpUrl + "/device/" + callsign + "/api/ping";
            Log.d(TAG, "Relay ping: " + apiUrl);

            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);  // Short timeout for ping
            conn.setReadTimeout(3000);

            int responseCode = conn.getResponseCode();
            conn.disconnect();

            boolean reachable = (responseCode >= 200 && responseCode < 300);
            Log.d(TAG, "Relay ping " + callsign + ": " + (reachable ? "✓ SUCCESS" : "✗ FAILED (" + responseCode + ")"));
            return reachable;

        } catch (Exception e) {
            Log.d(TAG, "Relay ping " + callsign + ": ✗ FAILED (" + e.getMessage() + ")");
            return false;
        }
    }

    /**
     * Try to connect to device via relay server
     */
    private HttpResponse tryRelayConnection(String deviceId, String path, int timeoutMs) {
        Log.i(TAG, "→ Attempting relay connection to: " + deviceId);
        return getViaRelay(deviceId, path, timeoutMs);
    }

    /**
     * Make HTTP GET request via relay server
     */
    private HttpResponse getViaRelay(String callsign, String path, int timeoutMs) {
        try {
            // Get relay server URL from settings
            ConfigManager configManager = ConfigManager.getInstance(context);
            String relayUrl = configManager.getConfig().getDeviceRelayServerUrl();

            // Convert WebSocket URL to HTTP URL
            String httpUrl = relayUrl.replace("ws://", "http://").replace("wss://", "https://");
            // Remove WebSocket port
            if (httpUrl.contains(":45679")) {
                httpUrl = httpUrl.replace(":45679", "");
            }

            // Build relay proxy URL
            String apiUrl = httpUrl + "/device/" + callsign + path;
            Log.d(TAG, "Relay GET: " + apiUrl);

            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);

            int responseCode = conn.getResponseCode();
            Log.d(TAG, "Relay response code: " + responseCode);

            InputStream inputStream;
            if (responseCode >= 200 && responseCode < 300) {
                inputStream = conn.getInputStream();
            } else {
                inputStream = conn.getErrorStream();
            }

            if (inputStream == null) {
                return new HttpResponse(responseCode, "");
            }

            BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            StringBuilder response = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                response.append(line);
            }

            reader.close();
            conn.disconnect();

            Log.i(TAG, "✓ Relay request successful");
            return new HttpResponse(responseCode, response.toString());

        } catch (Exception e) {
            Log.e(TAG, "Relay request failed: " + e.getMessage());
            return new HttpResponse(500, "Relay request failed: " + e.getMessage());
        }
    }

    /**
     * Get an InputStream for downloading binary data from a remote device
     *
     * @param deviceId The device ID to connect to
     * @param remoteIp Optional IP address to use for WiFi connection
     * @param path The API path
     * @param timeoutMs Connection timeout in milliseconds
     * @return InputStreamResponse containing the stream and connection to close after use
     */
    public InputStreamResponse getInputStream(String deviceId, String remoteIp, String path, int timeoutMs) {
        Log.i(TAG, "");
        Log.i(TAG, "═══════════════════════════════════════════════════════");
        Log.i(TAG, "HTTP GET INPUTSTREAM REQUEST");
        Log.i(TAG, "═══════════════════════════════════════════════════════");
        Log.i(TAG, "Device ID: " + deviceId);
        Log.i(TAG, "Remote IP: " + remoteIp);
        Log.i(TAG, "Path:      " + path);
        Log.i(TAG, "═══════════════════════════════════════════════════════");
        Log.i(TAG, "");

        // Check if WiFi is connected
        boolean isWifiConnected = isWifiConnected();
        Log.i(TAG, "WiFi connected: " + isWifiConnected);

        if (!isWifiConnected) {
            Log.w(TAG, "✗ WiFi NOT connected");
            return new InputStreamResponse(null, null, 503, "WiFi not connected");
        }

        // If remoteIp is provided and it's a local network address, use it directly
        if (remoteIp != null && !remoteIp.isEmpty()) {
            boolean isLocal = isLocalNetworkAddress(remoteIp);
            Log.i(TAG, "Remote IP '" + remoteIp + "' is local network: " + isLocal);

            if (isLocal) {
                Log.i(TAG, "✓ WiFi connected + local IP - using direct HTTP to: " + remoteIp);
                return getInputStreamViaHttp(remoteIp, path, timeoutMs);
            }
        }

        Log.e(TAG, "✗ No valid remote IP provided");
        return new InputStreamResponse(null, null, 400, "No valid remote IP provided");
    }

    /**
     * Get InputStream via direct HTTP connection
     */
    private InputStreamResponse getInputStreamViaHttp(String remoteIp, String path, int timeoutMs) {
        try {
            String apiUrl = "http://" + remoteIp + ":45678" + path;
            Log.d(TAG, "HTTP GET InputStream: " + apiUrl);

            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);

            int responseCode = conn.getResponseCode();
            Log.d(TAG, "HTTP response code: " + responseCode);

            if (responseCode >= 200 && responseCode < 300) {
                InputStream inputStream = conn.getInputStream();
                return new InputStreamResponse(inputStream, conn, responseCode, null);
            } else {
                InputStream errorStream = conn.getErrorStream();
                String errorMsg = "";
                if (errorStream != null) {
                    BufferedReader reader = new BufferedReader(
                        new InputStreamReader(errorStream, StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    errorMsg = sb.toString();
                    reader.close();
                }
                conn.disconnect();
                return new InputStreamResponse(null, null, responseCode, errorMsg);
            }

        } catch (Exception e) {
            Log.e(TAG, "HTTP InputStream request failed: " + e.getMessage());
            return new InputStreamResponse(null, null, 500, "Request failed: " + e.getMessage());
        }
    }

    /**
     * Make HTTP GET request via direct HTTP connection
     * Note: This method assumes device.ID is a valid IP address
     */
    private HttpResponse getViaHttp(Device device, String path, int timeoutMs) {
        String remoteIp = device.ID;

        try {
            String apiUrl = "http://" + remoteIp + ":45678" + path;
            Log.d(TAG, "HTTP GET: " + apiUrl);

            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);

            int responseCode = conn.getResponseCode();
            Log.d(TAG, "HTTP response code: " + responseCode);

            InputStream inputStream;
            if (responseCode >= 200 && responseCode < 300) {
                inputStream = conn.getInputStream();
            } else {
                inputStream = conn.getErrorStream();
            }

            if (inputStream == null) {
                return new HttpResponse(responseCode, "");
            }

            BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            StringBuilder response = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                response.append(line);
            }

            reader.close();
            conn.disconnect();

            return new HttpResponse(responseCode, response.toString());

        } catch (Exception e) {
            Log.e(TAG, "HTTP request failed: " + e.getMessage());
            return new HttpResponse(500, "Request failed: " + e.getMessage());
        }
    }

    /**
     * Check if a string is a valid IP address
     */
    private boolean isValidIpAddress(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }

        // Simple IP address validation (IPv4)
        // Matches format: xxx.xxx.xxx.xxx where xxx is 0-255
        String[] parts = str.split("\\.");
        if (parts.length != 4) {
            return false;
        }

        try {
            for (String part : parts) {
                int num = Integer.parseInt(part);
                if (num < 0 || num > 255) {
                    return false;
                }
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Check if WiFi is currently connected
     */
    private boolean isWifiConnected() {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) {
                return false;
            }

            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            boolean isConnected = activeNetwork != null && activeNetwork.isConnected();
            boolean isWifi = activeNetwork != null && activeNetwork.getType() == ConnectivityManager.TYPE_WIFI;

            Log.d(TAG, "Network check: connected=" + isConnected + ", wifi=" + isWifi);
            return isConnected && isWifi;
        } catch (Exception e) {
            Log.e(TAG, "Error checking WiFi connectivity: " + e.getMessage());
            return false;
        }
    }

    /**
     * Check if an IP address is on a local/private network
     *
     * Private IP ranges:
     * - 10.0.0.0/8 (10.0.0.0 - 10.255.255.255)
     * - 172.16.0.0/12 (172.16.0.0 - 172.31.255.255)
     * - 192.168.0.0/16 (192.168.0.0 - 192.168.255.255)
     * - 127.0.0.0/8 (localhost)
     */
    private boolean isLocalNetworkAddress(String ipAddress) {
        if (!isValidIpAddress(ipAddress)) {
            return false;
        }

        String[] parts = ipAddress.split("\\.");
        int first = Integer.parseInt(parts[0]);
        int second = Integer.parseInt(parts[1]);

        // 10.0.0.0/8
        if (first == 10) {
            return true;
        }

        // 172.16.0.0/12 (172.16.0.0 - 172.31.255.255)
        if (first == 172 && second >= 16 && second <= 31) {
            return true;
        }

        // 192.168.0.0/16
        if (first == 192 && second == 168) {
            return true;
        }

        // 127.0.0.0/8 (localhost)
        if (first == 127) {
            return true;
        }

        return false;
    }

    /**
     * HTTP response container
     */
    public static class HttpResponse {
        public final int statusCode;
        public final String body;

        public HttpResponse(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }

        public boolean isSuccess() {
            return statusCode >= 200 && statusCode < 300;
        }
    }

    /**
     * InputStream response container for binary downloads
     * IMPORTANT: Caller must close both the stream and connection after use
     */
    public static class InputStreamResponse {
        public final InputStream stream;
        public final HttpURLConnection connection;
        public final int statusCode;
        public final String errorMessage;

        public InputStreamResponse(InputStream stream, HttpURLConnection connection, int statusCode, String errorMessage) {
            this.stream = stream;
            this.connection = connection;
            this.statusCode = statusCode;
            this.errorMessage = errorMessage;
        }

        public boolean isSuccess() {
            return statusCode >= 200 && statusCode < 300 && stream != null;
        }

        /**
         * Close the stream and disconnect the connection
         */
        public void close() {
            try {
                if (stream != null) {
                    stream.close();
                }
            } catch (Exception e) {
                // Ignore
            }
            try {
                if (connection != null) {
                    connection.disconnect();
                }
            } catch (Exception e) {
                // Ignore
            }
        }
    }
}

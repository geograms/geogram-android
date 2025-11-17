package offgrid.geogram.p2p;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import offgrid.geogram.ble.BluetoothSender;
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
            // Device ID is a callsign (e.g., "X15RJ0") - try relay first
            Log.i(TAG, "→ Device ID is callsign '" + deviceId + "' - trying relay");
            HttpResponse relayResponse = tryRelayConnection(deviceId, path, timeoutMs);
            if (relayResponse.isSuccess()) {
                return relayResponse;
            }

            // Relay failed - try GATT as last resort
            Log.w(TAG, "✗ Relay connection failed, trying BLE GATT");
            return tryGattConnection(deviceId, path, timeoutMs);
        } else {
            // Device ID is an IP but WiFi failed/unavailable - check if callsign available
            Log.i(TAG, "→ Direct connection not available, checking for relay");

            // Try to find device's callsign
            String callsign = null;
            DeviceManager deviceManager = DeviceManager.getInstance();
            for (Device d : deviceManager.getDevicesSpotted()) {
                if (d.ID.equals(deviceId) && d.callsign != null && !d.callsign.isEmpty()) {
                    callsign = d.callsign;
                    break;
                }
            }

            if (callsign != null) {
                // Try relay first
                Log.i(TAG, "→ Found callsign '" + callsign + "' for device, trying relay");
                HttpResponse relayResponse = tryRelayConnection(callsign, path, timeoutMs);
                if (relayResponse.isSuccess()) {
                    return relayResponse;
                }

                // Relay failed - try GATT
                Log.w(TAG, "✗ Relay connection failed, trying BLE GATT");
                return tryGattConnection(callsign, path, timeoutMs);
            }

            // No callsign - try GATT with device ID (might be MAC address)
            Log.i(TAG, "→ No callsign found, trying BLE GATT directly");
            return tryGattConnection(deviceId, path, timeoutMs);
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
     * Try to connect to device via BLE GATT
     */
    private HttpResponse tryGattConnection(String deviceId, String path, int timeoutMs) {
        try {
            Log.i(TAG, "→ Attempting BLE GATT connection to: " + deviceId);

            // Get BluetoothSender instance
            offgrid.geogram.ble.BluetoothSender sender =
                offgrid.geogram.ble.BluetoothSender.getInstance(context);

            // Check if we have an active GATT connection
            if (!sender.hasActiveConnection(deviceId)) {
                Log.i(TAG, "No active GATT connection to " + deviceId + ", attempting to connect...");

                // Get BluetoothDevice from deviceId (callsign or MAC)
                BluetoothDevice device = sender.getBluetoothDevice(deviceId);
                if (device == null) {
                    Log.e(TAG, "✗ Cannot find BluetoothDevice for: " + deviceId);
                    return new HttpResponse(503, "{\"success\": false, \"error\": \"Cannot find device\"}");
                }

                // Try to establish connection
                sender.connectToDevice(device);

                // Wait a bit for connection to establish
                Thread.sleep(2000);

                // Verify connection was established
                if (!sender.hasActiveConnection(deviceId)) {
                    Log.e(TAG, "✗ Failed to establish GATT connection to: " + deviceId);
                    return new HttpResponse(503, "{\"success\": false, \"error\": \"No active GATT connection to device\"}");
                }
            }

            Log.i(TAG, "Using active GATT connection for HTTP request");

            // Send HTTP request over GATT
            java.util.concurrent.CompletableFuture<HttpResponse> future =
                sender.sendHttpRequestOverGatt(deviceId, "GET", path, timeoutMs);

            // Wait for response
            HttpResponse response = future.get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);

            if (response.isSuccess()) {
                Log.i(TAG, "✓ BLE GATT request successful: status=" + response.statusCode + ", body length=" + response.body.length() + " bytes");
                if (response.body.length() > 0) {
                    Log.d(TAG, "Response body preview (first 200 chars): " + response.body.substring(0, Math.min(200, response.body.length())));
                } else {
                    Log.w(TAG, "⚠ Response body is EMPTY!");
                }
            } else {
                Log.w(TAG, "BLE GATT request returned error: " + response.statusCode);
            }

            return response;

        } catch (java.util.concurrent.TimeoutException e) {
            Log.e(TAG, "✗ BLE GATT request timeout after " + timeoutMs + "ms");
            return new HttpResponse(504, "{\"success\": false, \"error\": \"BLE GATT request timeout\"}");
        } catch (Exception e) {
            Log.e(TAG, "✗ BLE GATT request failed: " + e.getMessage(), e);
            return new HttpResponse(503, "{\"success\": false, \"error\": \"BLE GATT error: " + e.getMessage() + "\"}");
        }
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

        // If WiFi is connected and remoteIp is provided and local, try direct connection first
        if (isWifiConnected && remoteIp != null && !remoteIp.isEmpty()) {
            boolean isLocal = isLocalNetworkAddress(remoteIp);
            Log.i(TAG, "Remote IP '" + remoteIp + "' is local network: " + isLocal);

            if (isLocal) {
                Log.i(TAG, "✓ WiFi connected + local IP - trying direct HTTP to: " + remoteIp);
                InputStreamResponse response = getInputStreamViaHttp(remoteIp, path, timeoutMs);
                if (response.isSuccess()) {
                    return response;
                }
                Log.w(TAG, "✗ Direct HTTP failed, will try relay");
            }
        }

        // Try WiFi direct connection by IP if deviceId looks like an IP
        if (isWifiConnected && deviceId != null && deviceId.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
            Log.i(TAG, "Device ID is an IP address, trying direct connection: " + deviceId);
            InputStreamResponse response = getInputStreamViaHttp(deviceId, path, timeoutMs);
            if (response.isSuccess()) {
                return response;
            }
            Log.w(TAG, "✗ Direct HTTP to device IP failed, will try relay");
        }

        // Use relay if deviceId is a callsign (not an IP address)
        if (deviceId != null && !deviceId.isEmpty() && !deviceId.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
            Log.i(TAG, "Trying relay for callsign: " + deviceId);
            InputStreamResponse relayResponse = getInputStreamViaRelay(deviceId, path, timeoutMs);
            if (relayResponse.isSuccess()) {
                return relayResponse;
            }

            // Relay failed - try GATT
            Log.w(TAG, "✗ Relay failed, trying BLE GATT for inputstream");
            return getInputStreamViaGatt(deviceId, path, timeoutMs);
        }

        // Try GATT as last resort
        Log.i(TAG, "No other options available, trying BLE GATT");
        return getInputStreamViaGatt(deviceId, path, timeoutMs);
    }

    /**
     * Get InputStream with Range request support (for chunked downloads)
     *
     * @param deviceId Device to connect to
     * @param remoteIp Optional remote IP address
     * @param path The API path
     * @param startByte Start byte offset (inclusive)
     * @param endByte End byte offset (inclusive)
     * @param timeoutMs Connection timeout in milliseconds
     * @return InputStreamResponse containing the stream and connection to close after use
     */
    public InputStreamResponse getInputStreamWithRange(String deviceId, String remoteIp, String path,
                                                       long startByte, long endByte, int timeoutMs) {
        Log.i(TAG, "HTTP GET INPUTSTREAM WITH RANGE: bytes=" + startByte + "-" + endByte);

        // Check if WiFi is connected
        boolean isWifiConnected = isWifiConnected();

        // If WiFi is connected and remoteIp is provided and local, try direct connection first
        if (isWifiConnected && remoteIp != null && !remoteIp.isEmpty()) {
            boolean isLocal = isLocalNetworkAddress(remoteIp);
            if (isLocal) {
                Log.i(TAG, "✓ WiFi connected + local IP - trying direct HTTP with Range");
                InputStreamResponse response = getInputStreamViaHttpWithRange(remoteIp, path, startByte, endByte, timeoutMs);
                if (response.isSuccess()) {
                    return response;
                }
                Log.w(TAG, "✗ Direct HTTP with Range failed, will try relay");
            }
        }

        // Try WiFi direct connection by IP if deviceId looks like an IP
        if (isWifiConnected && deviceId != null && deviceId.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
            Log.i(TAG, "Device ID is an IP address, trying direct connection with Range");
            InputStreamResponse response = getInputStreamViaHttpWithRange(deviceId, path, startByte, endByte, timeoutMs);
            if (response.isSuccess()) {
                return response;
            }
            Log.w(TAG, "✗ Direct HTTP to device IP with Range failed, will try relay");
        }

        // Use relay if deviceId is a callsign
        if (deviceId != null && !deviceId.isEmpty() && !deviceId.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
            Log.i(TAG, "Trying relay with Range for callsign: " + deviceId);
            InputStreamResponse relayResponse = getInputStreamViaRelayWithRange(deviceId, path, startByte, endByte, timeoutMs);
            if (relayResponse.isSuccess()) {
                return relayResponse;
            }

            // Relay failed - try GATT
            Log.w(TAG, "✗ Relay with Range failed, trying BLE GATT");
            return getInputStreamViaGattWithRange(deviceId, path, startByte, endByte, timeoutMs);
        }

        // Try GATT as last resort
        Log.i(TAG, "No other options available, trying BLE GATT with Range");
        return getInputStreamViaGattWithRange(deviceId, path, startByte, endByte, timeoutMs);
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
     * Get InputStream via relay server
     */
    private InputStreamResponse getInputStreamViaRelay(String callsign, String path, int timeoutMs) {
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
            Log.d(TAG, "Relay GET InputStream: " + apiUrl);

            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);

            int responseCode = conn.getResponseCode();
            Log.d(TAG, "Relay response code: " + responseCode);

            if (responseCode >= 200 && responseCode < 300) {
                InputStream inputStream = conn.getInputStream();
                Log.i(TAG, "✓ Relay InputStream request successful");
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
            Log.e(TAG, "Relay InputStream request failed: " + e.getMessage());
            return new InputStreamResponse(null, null, 500, "Relay request failed: " + e.getMessage());
        }
    }

    /**
     * Get InputStream via BLE GATT connection
     */
    private InputStreamResponse getInputStreamViaGatt(String deviceId, String path, int timeoutMs) {
        try {
            Log.i(TAG, "→ Attempting BLE GATT InputStream request to: " + deviceId);

            // Get BluetoothSender instance
            BluetoothSender sender = BluetoothSender.getInstance(context);

            // Check if we have an active GATT connection
            if (!sender.hasActiveConnection(deviceId)) {
                Log.i(TAG, "No active GATT connection, attempting to connect...");

                // Get BluetoothDevice from deviceId (callsign or MAC)
                BluetoothDevice device = sender.getBluetoothDevice(deviceId);
                if (device == null) {
                    Log.e(TAG, "✗ Cannot find BluetoothDevice for: " + deviceId);
                    return new InputStreamResponse(null, null, 503, "Cannot find device");
                }

                // Try to establish connection
                sender.connectToDevice(device);

                // Wait for connection to establish
                Thread.sleep(2000);

                // Verify connection was established
                if (!sender.hasActiveConnection(deviceId)) {
                    Log.e(TAG, "✗ Failed to establish GATT connection");
                    return new InputStreamResponse(null, null, 503, "No active GATT connection");
                }
            }

            // Send HTTP request over GATT
            java.util.concurrent.CompletableFuture<HttpResponse> future =
                sender.sendHttpRequestOverGatt(deviceId, "GET", path, timeoutMs);

            // Wait for response
            HttpResponse response = future.get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);

            if (response.isSuccess()) {
                Log.i(TAG, "✓ BLE GATT InputStream request successful");

                // Convert response body to InputStream
                // For binary data that was Base64 encoded, we need to decode it
                byte[] data;
                if (response.body.startsWith("{") || response.body.startsWith("[")) {
                    // JSON response - use as-is
                    data = response.body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                } else {
                    // Assume it might be Base64-encoded binary data
                    try {
                        data = android.util.Base64.decode(response.body, android.util.Base64.NO_WRAP);
                        Log.d(TAG, "Decoded Base64 response (" + data.length + " bytes)");
                    } catch (IllegalArgumentException e) {
                        // Not Base64 - treat as plain text
                        data = response.body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    }
                }

                InputStream stream = new java.io.ByteArrayInputStream(data);
                return new InputStreamResponse(stream, null, response.statusCode, null);
            } else {
                Log.e(TAG, "✗ BLE GATT request failed with status: " + response.statusCode);
                return new InputStreamResponse(null, null, response.statusCode, response.body);
            }

        } catch (java.util.concurrent.TimeoutException e) {
            Log.e(TAG, "BLE GATT request timeout", e);
            return new InputStreamResponse(null, null, 504, "BLE GATT request timeout");
        } catch (Exception e) {
            Log.e(TAG, "BLE GATT InputStream request failed", e);
            return new InputStreamResponse(null, null, 503, "BLE GATT error: " + e.getMessage());
        }
    }

    /**
     * Get InputStream via direct HTTP with Range header
     */
    private InputStreamResponse getInputStreamViaHttpWithRange(String remoteIp, String path,
                                                               long startByte, long endByte, int timeoutMs) {
        try {
            String apiUrl = "http://" + remoteIp + ":45678" + path;
            Log.d(TAG, "HTTP GET InputStream with Range: " + apiUrl + " (bytes=" + startByte + "-" + endByte + ")");

            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setRequestProperty("Range", "bytes=" + startByte + "-" + endByte);

            int responseCode = conn.getResponseCode();
            Log.d(TAG, "HTTP response code: " + responseCode);

            // Accept both 200 (full content) and 206 (partial content)
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
            Log.e(TAG, "HTTP InputStream with Range failed: " + e.getMessage());
            return new InputStreamResponse(null, null, 500, "Request failed: " + e.getMessage());
        }
    }

    /**
     * Get InputStream via relay server with Range header
     */
    private InputStreamResponse getInputStreamViaRelayWithRange(String callsign, String path,
                                                                long startByte, long endByte, int timeoutMs) {
        try {
            // Get relay server URL from settings
            ConfigManager configManager = ConfigManager.getInstance(context);
            String relayUrl = configManager.getConfig().getDeviceRelayServerUrl();

            // Convert WebSocket URL to HTTP URL
            String httpUrl = relayUrl.replace("ws://", "http://").replace("wss://", "https://");
            if (httpUrl.contains(":45679")) {
                httpUrl = httpUrl.replace(":45679", "");
            }

            // Build relay proxy URL
            String apiUrl = httpUrl + "/device/" + callsign + path;
            Log.d(TAG, "Relay GET InputStream with Range: " + apiUrl + " (bytes=" + startByte + "-" + endByte + ")");

            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setRequestProperty("Range", "bytes=" + startByte + "-" + endByte);

            int responseCode = conn.getResponseCode();
            Log.d(TAG, "Relay response code: " + responseCode);

            if (responseCode >= 200 && responseCode < 300) {
                InputStream inputStream = conn.getInputStream();
                Log.i(TAG, "✓ Relay InputStream with Range request successful");
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
            Log.e(TAG, "Relay InputStream with Range failed: " + e.getMessage());
            return new InputStreamResponse(null, null, 500, "Relay request failed: " + e.getMessage());
        }
    }

    /**
     * Get InputStream via BLE GATT with Range header
     * Note: This requires the server to support Range requests
     */
    private InputStreamResponse getInputStreamViaGattWithRange(String deviceId, String path,
                                                               long startByte, long endByte, int timeoutMs) {
        try {
            Log.i(TAG, "→ Attempting BLE GATT InputStream with Range to: " + deviceId +
                      " (bytes=" + startByte + "-" + endByte + ")");

            // Get BluetoothSender instance
            BluetoothSender sender = BluetoothSender.getInstance(context);

            // Check if we have an active GATT connection
            if (!sender.hasActiveConnection(deviceId)) {
                Log.i(TAG, "No active GATT connection, attempting to connect...");

                BluetoothDevice device = sender.getBluetoothDevice(deviceId);
                if (device == null) {
                    Log.e(TAG, "✗ Cannot find BluetoothDevice for: " + deviceId);
                    return new InputStreamResponse(null, null, 503, "Cannot find device");
                }

                sender.connectToDevice(device);
                Thread.sleep(2000);

                if (!sender.hasActiveConnection(deviceId)) {
                    Log.e(TAG, "✗ Failed to establish GATT connection");
                    return new InputStreamResponse(null, null, 503, "No active GATT connection");
                }
            }

            // Send HTTP GET request with Range header over GATT
            // Add Range header to request path as query parameter (simple approach)
            String pathWithRange = path + (path.contains("?") ? "&" : "?") +
                                 "range=" + startByte + "-" + endByte;

            java.util.concurrent.CompletableFuture<HttpResponse> future =
                sender.sendHttpRequestOverGatt(deviceId, "GET", pathWithRange, timeoutMs);

            // Wait for response
            HttpResponse response = future.get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);

            if (response.isSuccess()) {
                Log.i(TAG, "✓ BLE GATT InputStream with Range successful");

                // Convert response body to InputStream
                byte[] data;
                if (response.body.startsWith("{") || response.body.startsWith("[")) {
                    data = response.body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                } else {
                    try {
                        data = android.util.Base64.decode(response.body, android.util.Base64.NO_WRAP);
                        Log.d(TAG, "Decoded Base64 chunk (" + data.length + " bytes)");
                    } catch (IllegalArgumentException e) {
                        data = response.body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    }
                }

                InputStream stream = new java.io.ByteArrayInputStream(data);
                return new InputStreamResponse(stream, null, response.statusCode, null);
            } else {
                Log.e(TAG, "✗ BLE GATT request with Range failed: " + response.statusCode);
                return new InputStreamResponse(null, null, response.statusCode, response.body);
            }

        } catch (java.util.concurrent.TimeoutException e) {
            Log.e(TAG, "BLE GATT request with Range timeout", e);
            return new InputStreamResponse(null, null, 504, "BLE GATT request timeout");
        } catch (Exception e) {
            Log.e(TAG, "BLE GATT InputStream with Range failed", e);
            return new InputStreamResponse(null, null, 503, "BLE GATT error: " + e.getMessage());
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

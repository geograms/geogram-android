package offgrid.geogram.wifi;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import offgrid.geogram.core.Central;

/**
 * Sends messages to devices on local WiFi network via HTTP API
 */
public class WiFiMessageSender {
    private static final String TAG = "WiFiMessageSender";
    private static final int API_PORT = 45678;
    private static final String API_BROADCAST_ENDPOINT = "/api/ble/send";
    private static final int HTTP_TIMEOUT_MS = 5000;
    private static final Gson gson = new Gson();

    private static WiFiMessageSender instance;
    private final Context context;
    private final ExecutorService sendPool;

    private WiFiMessageSender(Context context) {
        this.context = context.getApplicationContext();
        this.sendPool = Executors.newFixedThreadPool(5);
    }

    public static synchronized WiFiMessageSender getInstance(Context context) {
        if (instance == null) {
            instance = new WiFiMessageSender(context);
        }
        return instance;
    }

    /**
     * Send broadcast message to all discovered WiFi devices
     * @param message Message content
     * @return List of callsigns that successfully received the message
     */
    public List<String> sendBroadcastMessage(String message) {
        List<String> successfulRecipients = new ArrayList<>();

        // Get all discovered WiFi devices
        Map<String, String> devices = WiFiDiscoveryService.getInstance(context).getDiscoveredDevices();

        if (devices.isEmpty()) {
            Log.d(TAG, "No WiFi devices discovered, skipping WiFi send");
            return successfulRecipients;
        }

        Log.i(TAG, "Sending broadcast message to " + devices.size() + " WiFi devices");

        // Send to each device concurrently
        for (Map.Entry<String, String> entry : devices.entrySet()) {
            String callsign = entry.getKey();
            String ipAddress = entry.getValue();

            sendPool.execute(() -> {
                if (sendMessageToDevice(ipAddress, message)) {
                    synchronized (successfulRecipients) {
                        successfulRecipients.add(callsign);
                    }
                    Log.i(TAG, "✓ Sent via WiFi to " + callsign + " (" + ipAddress + ")");
                } else {
                    Log.w(TAG, "✗ Failed to send via WiFi to " + callsign + " (" + ipAddress + ")");
                }
            });
        }

        return successfulRecipients;
    }

    /**
     * Send message to a specific IP address via HTTP API
     * @param ipAddress Target device IP
     * @param message Message content
     * @return true if successful
     */
    private boolean sendMessageToDevice(String ipAddress, String message) {
        HttpURLConnection conn = null;
        try {
            // Build URL
            URL url = new URL("http://" + ipAddress + ":" + API_PORT + API_BROADCAST_ENDPOINT);

            // Create connection
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(HTTP_TIMEOUT_MS);
            conn.setReadTimeout(HTTP_TIMEOUT_MS);
            conn.setDoOutput(true);

            // Build JSON payload
            JsonObject payload = new JsonObject();
            payload.addProperty("message", message);
            String jsonPayload = gson.toJson(payload);

            // Send request
            OutputStream os = conn.getOutputStream();
            os.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
            os.flush();
            os.close();

            // Check response
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                return true;
            } else {
                Log.w(TAG, "HTTP " + responseCode + " from " + ipAddress);
                return false;
            }

        } catch (IOException e) {
            // Connection failed - device may be offline
            Log.d(TAG, "Failed to send to " + ipAddress + ": " + e.getMessage());
            return false;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * Check if any devices are reachable via WiFi
     */
    public boolean hasWiFiDevices() {
        return !WiFiDiscoveryService.getInstance(context).getDiscoveredDevices().isEmpty();
    }

    /**
     * Get count of discovered WiFi devices
     */
    public int getWiFiDeviceCount() {
        return WiFiDiscoveryService.getInstance(context).getDiscoveredDevices().size();
    }

    /**
     * Check if a specific callsign is reachable via WiFi
     */
    public boolean isDeviceOnWiFi(String callsign) {
        return WiFiDiscoveryService.getInstance(context).getDeviceIp(callsign) != null;
    }

    /**
     * Get WiFi device callsigns
     */
    public Set<String> getWiFiDeviceCallsigns() {
        return WiFiDiscoveryService.getInstance(context).getDiscoveredDevices().keySet();
    }
}

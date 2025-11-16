/*
 * Copyright (c) geogram
 * License: Apache-2.0
 */
package offgrid.geogram.p2p;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import offgrid.geogram.core.Log;
import offgrid.geogram.settings.ConfigManager;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Utility class to check which devices are connected to the relay server
 *
 * @author brito
 */
public class DeviceRelayChecker {

    private static final String TAG = "Relay/Checker";
    private static final long UPDATE_INTERVAL_MS = 30000; // 30 seconds

    private static DeviceRelayChecker instance;

    private Context context;
    private Set<String> connectedDevices;
    private OkHttpClient okHttpClient;
    private Handler handler;
    private Gson gson;
    private boolean isRunning = false;

    private DeviceRelayChecker(Context context) {
        this.context = context.getApplicationContext();
        this.connectedDevices = new HashSet<>();
        this.gson = new Gson();
        this.handler = new Handler(Looper.getMainLooper());

        this.okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    public static synchronized DeviceRelayChecker getInstance(Context context) {
        if (instance == null) {
            instance = new DeviceRelayChecker(context);
        }
        return instance;
    }

    /**
     * Start periodic relay status checks
     */
    public void start() {
        if (isRunning) {
            Log.d(TAG, "Relay checker already running");
            return;
        }

        Log.i(TAG, "═══════════════════════════════════════════════════════");
        Log.i(TAG, "RELAY CHECKER START");
        Log.i(TAG, "═══════════════════════════════════════════════════════");

        ConfigManager configManager = ConfigManager.getInstance(context);
        boolean relayEnabled = configManager.getConfig().isDeviceRelayEnabled();
        String relayUrl = configManager.getConfig().getDeviceRelayServerUrl();

        Log.i(TAG, "Relay enabled: " + relayEnabled);
        Log.i(TAG, "Relay URL: " + relayUrl);

        isRunning = true;
        updateRelayStatus();
        Log.i(TAG, "✓ Started relay status checker");
    }

    /**
     * Stop periodic checks
     */
    public void stop() {
        isRunning = false;
        handler.removeCallbacksAndMessages(null);
        Log.d(TAG, "Stopped relay status checker");
    }

    /**
     * Check if a device is connected to relay server
     *
     * @param callsign Device callsign (e.g., X1ABCD)
     * @return true if device is connected to relay
     */
    public boolean isDeviceOnRelay(String callsign) {
        if (callsign == null || callsign.isEmpty()) {
            return false;
        }
        return connectedDevices.contains(callsign.toUpperCase());
    }

    /**
     * Get set of all connected device callsigns
     */
    public Set<String> getConnectedDevices() {
        return new HashSet<>(connectedDevices);
    }

    /**
     * Update relay status from server
     */
    private void updateRelayStatus() {
        if (!isRunning) {
            return;
        }

        // Run in background thread
        new Thread(() -> {
            try {
                ConfigManager configManager = ConfigManager.getInstance(context);
                if (!configManager.getConfig().isDeviceRelayEnabled()) {
                    Log.w(TAG, "✗ Device relay is DISABLED, skipping status check");
                    scheduleNextUpdate();
                    return;
                }

                String relayUrl = configManager.getConfig().getDeviceRelayServerUrl();
                if (relayUrl == null || relayUrl.isEmpty()) {
                    Log.w(TAG, "No relay server URL configured");
                    scheduleNextUpdate();
                    return;
                }

                // Convert WebSocket URL to HTTP URL
                String httpUrl = relayUrl.replace("ws://", "http://").replace("wss://", "https://");
                // Remove port from WebSocket URL and use standard HTTP port
                if (httpUrl.contains(":45679")) {
                    httpUrl = httpUrl.replace(":45679", "");
                }
                httpUrl = httpUrl + "/relay/status";

                Log.d(TAG, "Checking relay status: " + httpUrl);

                Request request = new Request.Builder()
                        .url(httpUrl)
                        .get()
                        .build();

                try (Response response = okHttpClient.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String responseBody = response.body().string();
                        Log.d(TAG, "Relay status response: " + responseBody);

                        parseRelayStatus(responseBody);
                    } else {
                        Log.w(TAG, "Failed to get relay status: " + response.code());
                        clearConnectedDevices();
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error fetching relay status", e);
                    clearConnectedDevices();
                }

            } catch (Exception e) {
                Log.e(TAG, "Error updating relay status", e);
                clearConnectedDevices();
            } finally {
                scheduleNextUpdate();
            }
        }).start();
    }

    /**
     * Parse relay status JSON response
     */
    private void parseRelayStatus(String json) {
        try {
            JsonObject statusObj = gson.fromJson(json, JsonObject.class);
            JsonArray devicesArray = statusObj.getAsJsonArray("devices");

            Set<String> newConnectedDevices = new HashSet<>();

            if (devicesArray != null) {
                for (int i = 0; i < devicesArray.size(); i++) {
                    JsonObject deviceObj = devicesArray.get(i).getAsJsonObject();
                    String callsign = deviceObj.get("callsign").getAsString();
                    if (callsign != null && !callsign.isEmpty()) {
                        newConnectedDevices.add(callsign.toUpperCase());
                    }
                }
            }

            synchronized (this) {
                connectedDevices = newConnectedDevices;
            }

            Log.d(TAG, "✓ Updated relay status: " + connectedDevices.size() + " devices connected");

        } catch (Exception e) {
            Log.e(TAG, "Error parsing relay status", e);
        }
    }

    /**
     * Clear connected devices list
     */
    private void clearConnectedDevices() {
        synchronized (this) {
            connectedDevices.clear();
        }
    }

    /**
     * Schedule next status update
     */
    private void scheduleNextUpdate() {
        if (isRunning) {
            handler.postDelayed(this::updateRelayStatus, UPDATE_INTERVAL_MS);
        }
    }
}

/*
 * Copyright (c) geogram
 * License: Apache-2.0
 */
package offgrid.geogram.p2p;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import offgrid.geogram.settings.ConfigManager;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket client for connecting to the device relay server
 *
 * @author brito
 */
public class DeviceRelayClient extends WebSocketListener {

    private static final String TAG = "Relay/Client";
    private static final int PING_INTERVAL_MS = 60000; // 1 minute
    private static final int RECONNECT_DELAY_SHORT_MS = 5000; // 5 seconds for first attempts
    private static final int RECONNECT_DELAY_LONG_MS = 60000; // 60 seconds after several failures
    private static final int FAST_RECONNECT_ATTEMPTS = 3; // Number of fast reconnect attempts before slowing down

    // Broadcast action for relay connection status changes
    public static final String ACTION_RELAY_STATUS_CHANGED = "offgrid.geogram.RELAY_STATUS_CHANGED";
    public static final String EXTRA_IS_CONNECTED = "is_connected";

    private static DeviceRelayClient instance;

    private Context context;
    private OkHttpClient okHttpClient;
    private WebSocket webSocket;
    private Handler handler;
    private String serverUrl;
    private String callsign;
    private boolean isConnected = false;
    private boolean shouldConnect = false;
    private int reconnectAttempts = 0; // Track number of failed reconnection attempts

    private DeviceRelayClient(Context context) {
        this.context = context.getApplicationContext();
        this.handler = new Handler(Looper.getMainLooper());

        this.okHttpClient = new OkHttpClient.Builder()
                .pingInterval(PING_INTERVAL_MS, TimeUnit.MILLISECONDS)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS) // No read timeout for WebSocket
                .build();
    }

    public static synchronized DeviceRelayClient getInstance(Context context) {
        if (instance == null) {
            instance = new DeviceRelayClient(context);
        }
        return instance;
    }

    /**
     * Start the relay client
     */
    public void start() {
        Log.i(TAG, "═══════════════════════════════════════════════════════");
        Log.i(TAG, "RELAY CLIENT START");
        Log.i(TAG, "═══════════════════════════════════════════════════════");

        ConfigManager configManager = ConfigManager.getInstance(context);
        boolean relayEnabled = configManager.getConfig().isDeviceRelayEnabled();

        Log.i(TAG, "Relay enabled: " + relayEnabled);

        if (!relayEnabled) {
            Log.w(TAG, "✗ Device relay is DISABLED in settings");
            return;
        }

        this.serverUrl = configManager.getConfig().getDeviceRelayServerUrl();
        this.callsign = configManager.getConfig().getCallsign();

        Log.i(TAG, "Server URL: " + serverUrl);
        Log.i(TAG, "Callsign: " + callsign);

        if (callsign == null || callsign.isEmpty()) {
            Log.e(TAG, "✗ Cannot start relay client: callsign not set");
            return;
        }

        shouldConnect = true;
        Log.i(TAG, "✓ Starting relay client connection...");
        connect();
    }

    /**
     * Stop the relay client
     */
    public void stop() {
        shouldConnect = false;
        isConnected = false;
        if (webSocket != null) {
            webSocket.close(1000, "Client shutting down");
            webSocket = null;
        }
        handler.removeCallbacksAndMessages(null);
    }

    /**
     * Connect to relay server
     */
    private void connect() {
        if (!shouldConnect) {
            Log.w(TAG, "Skipping connection (shouldConnect=false)");
            return;
        }

        if (serverUrl == null || serverUrl.isEmpty()) {
            Log.e(TAG, "✗ Server URL not configured");
            return;
        }

        Log.i(TAG, "→ Connecting to relay server: " + serverUrl);

        Request request = new Request.Builder()
                .url(serverUrl)
                .build();

        webSocket = okHttpClient.newWebSocket(request, this);
        Log.d(TAG, "WebSocket connection initiated");
    }

    /**
     * Reconnect after delay with exponential backoff
     */
    private void reconnect() {
        if (!shouldConnect) {
            return;
        }

        reconnectAttempts++;

        // Use shorter delay for first few attempts, then switch to longer delay
        int delay;
        if (reconnectAttempts <= FAST_RECONNECT_ATTEMPTS) {
            delay = RECONNECT_DELAY_SHORT_MS;
        } else {
            delay = RECONNECT_DELAY_LONG_MS;
        }

        Log.d(TAG, "Reconnecting in " + (delay / 1000) + " seconds... (attempt " + reconnectAttempts + ")");
        handler.postDelayed(this::connect, delay);
    }

    // WebSocketListener methods

    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        Log.i(TAG, "═══════════════════════════════════════════════════════");
        Log.i(TAG, "✓ WEBSOCKET CONNECTED TO RELAY SERVER");
        Log.i(TAG, "═══════════════════════════════════════════════════════");
        Log.i(TAG, "Response code: " + response.code());
        Log.i(TAG, "Protocol: " + response.protocol());
        isConnected = true;

        // Reset reconnection attempts on successful connection
        reconnectAttempts = 0;

        // Broadcast connection status change
        broadcastStatusChange(true);

        // Send registration message
        DeviceRelayMessage register = DeviceRelayMessage.createRegister(callsign);
        String json = register.toJson();
        webSocket.send(json);
        Log.i(TAG, "→ Sent REGISTER message with callsign: " + callsign);
        Log.d(TAG, "   JSON: " + json);
    }

    @Override
    public void onMessage(WebSocket webSocket, String text) {
        try {
            Log.d(TAG, "← Received WebSocket message: " + text);
            DeviceRelayMessage message = DeviceRelayMessage.fromJson(text);
            if (message == null) {
                Log.e(TAG, "✗ Failed to parse message: " + text);
                return;
            }

            Log.i(TAG, "← Message type: " + message.type);

            switch (message.type) {
                case REGISTER:
                    Log.i(TAG, "✓ REGISTRATION CONFIRMED for callsign: " + message.callsign);
                    break;

                case HTTP_REQUEST:
                    Log.i(TAG, "← HTTP_REQUEST: " + message.method + " " + message.path);
                    handleHttpRequest(webSocket, message);
                    break;

                case PING:
                    Log.d(TAG, "← PING (sending PONG)");
                    webSocket.send(DeviceRelayMessage.createPong().toJson());
                    break;

                case PONG:
                    Log.d(TAG, "← PONG");
                    break;

                case ERROR:
                    Log.e(TAG, "✗ Server error: " + message.error);
                    break;

                default:
                    Log.w(TAG, "← Unknown message type: " + message.type);
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "✗ Error handling message", e);
        }
    }

    @Override
    public void onClosing(WebSocket webSocket, int code, String reason) {
        Log.w(TAG, "WebSocket closing: code=" + code + ", reason='" + reason + "'");
        isConnected = false;
    }

    @Override
    public void onClosed(WebSocket webSocket, int code, String reason) {
        Log.w(TAG, "═══════════════════════════════════════════════════════");
        Log.w(TAG, "WEBSOCKET CLOSED");
        Log.w(TAG, "═══════════════════════════════════════════════════════");
        Log.w(TAG, "Code: " + code);
        Log.w(TAG, "Reason: " + reason);
        isConnected = false;

        // Broadcast connection status change
        broadcastStatusChange(false);

        reconnect();
    }

    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        Log.e(TAG, "═══════════════════════════════════════════════════════");
        Log.e(TAG, "✗ WEBSOCKET CONNECTION FAILED");
        Log.e(TAG, "═══════════════════════════════════════════════════════");
        Log.e(TAG, "Error: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        if (response != null) {
            Log.e(TAG, "Response code: " + response.code());
            Log.e(TAG, "Response message: " + response.message());
        } else {
            Log.e(TAG, "Response: null (connection failed before HTTP response)");
        }
        Log.e(TAG, "Stack trace:", t);
        isConnected = false;

        // Broadcast connection status change
        broadcastStatusChange(false);

        reconnect();
    }

    /**
     * Handle HTTP request from server
     */
    private void handleHttpRequest(WebSocket webSocket, DeviceRelayMessage request) {
        Log.d(TAG, "→ Processing HTTP request: " + request.method + " " + request.path);

        // Execute request in background thread
        new Thread(() -> {
            try {
                // Build local URL
                ConfigManager configManager = ConfigManager.getInstance(context);
                int port = configManager.getConfig().getHttpApiPort();
                String url = "http://localhost:" + port + request.path;

                Log.d(TAG, "Executing local request: " + url);

                // Execute local HTTP request
                Request.Builder requestBuilder = new Request.Builder().url(url);

                // Add headers
                if (request.headers != null && !request.headers.isEmpty()) {
                    Map<String, String> headers = parseHeaders(request.headers);
                    for (Map.Entry<String, String> entry : headers.entrySet()) {
                        requestBuilder.addHeader(entry.getKey(), entry.getValue());
                    }
                }

                // Add body if present
                if ("POST".equalsIgnoreCase(request.method) || "PUT".equalsIgnoreCase(request.method)) {
                    requestBuilder.method(request.method, okhttp3.RequestBody.create(
                            request.body != null ? request.body : "",
                            okhttp3.MediaType.get("application/json")));
                } else {
                    requestBuilder.method(request.method, null);
                }

                // Execute request
                OkHttpClient localClient = new OkHttpClient.Builder()
                        .connectTimeout(5, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .build();

                try (Response response = localClient.newCall(requestBuilder.build()).execute()) {
                    int statusCode = response.code();
                    String responseBody;
                    String responseHeaders;

                    // Check if response is binary (image, video, etc.)
                    String contentType = response.header("Content-Type", "");
                    boolean isBinary = contentType.startsWith("image/") ||
                                      contentType.startsWith("video/") ||
                                      contentType.startsWith("audio/") ||
                                      contentType.startsWith("application/octet-stream");

                    if (isBinary && response.body() != null) {
                        // For binary responses, Base64 encode to prevent corruption
                        byte[] bytes = response.body().bytes();
                        responseBody = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP);

                        // Add header to indicate Base64 encoding
                        java.util.Map<String, java.util.List<String>> headers = response.headers().toMultimap();
                        headers.put("X-Binary-Encoded", java.util.Arrays.asList("base64"));
                        responseHeaders = encodeHeaders(headers);

                        Log.d(TAG, "← Encoded binary response (" + bytes.length + " bytes) as Base64");
                    } else {
                        // Text responses can be sent as-is
                        responseBody = response.body() != null ? response.body().string() : "";
                        responseHeaders = encodeHeaders(response.headers().toMultimap());
                    }

                    // Send response back to server
                    DeviceRelayMessage relayResponse = DeviceRelayMessage.createHttpResponse(
                            request.requestId,
                            statusCode,
                            responseHeaders,
                            responseBody
                    );

                    webSocket.send(relayResponse.toJson());
                    Log.d(TAG, "← Sent HTTP response: " + statusCode);

                } catch (IOException e) {
                    Log.e(TAG, "Error executing local request", e);
                    sendErrorResponse(webSocket, request.requestId, 502, "Local API error: " + e.getMessage());
                }

            } catch (Exception e) {
                Log.e(TAG, "Error processing HTTP request", e);
                sendErrorResponse(webSocket, request.requestId, 500, "Internal error: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Send error response
     */
    private void sendErrorResponse(WebSocket webSocket, String requestId, int statusCode, String error) {
        DeviceRelayMessage response = DeviceRelayMessage.createHttpResponse(
                requestId,
                statusCode,
                "{}",
                "{\"error\": \"" + error + "\"}"
        );
        webSocket.send(response.toJson());
    }

    /**
     * Parse JSON headers string to Map
     */
    private Map<String, String> parseHeaders(String headersJson) {
        try {
            return new com.google.gson.Gson().fromJson(headersJson, Map.class);
        } catch (Exception e) {
            Log.e(TAG, "Error parsing headers", e);
            return new HashMap<>();
        }
    }

    /**
     * Encode headers Map to JSON string
     */
    private String encodeHeaders(Map<String, java.util.List<String>> headers) {
        Map<String, String> flatHeaders = new HashMap<>();
        for (Map.Entry<String, java.util.List<String>> entry : headers.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                flatHeaders.put(entry.getKey(), entry.getValue().get(0));
            }
        }
        return new com.google.gson.Gson().toJson(flatHeaders);
    }

    /**
     * Check if connected to relay server
     */
    public boolean isConnected() {
        return isConnected;
    }

    /**
     * Get connection status
     */
    public String getStatus() {
        if (isConnected) {
            return "Connected to " + serverUrl;
        } else if (shouldConnect) {
            return "Connecting...";
        } else {
            return "Disconnected";
        }
    }

    /**
     * Broadcast relay connection status change
     */
    private void broadcastStatusChange(boolean isConnected) {
        Intent intent = new Intent(ACTION_RELAY_STATUS_CHANGED);
        intent.putExtra(EXTRA_IS_CONNECTED, isConnected);
        context.sendBroadcast(intent);
        Log.d(TAG, "Broadcasted relay status change: " + (isConnected ? "CONNECTED" : "DISCONNECTED"));
    }
}

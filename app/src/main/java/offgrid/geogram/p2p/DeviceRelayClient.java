/*
 * Copyright (c) geogram
 * License: Apache-2.0
 */
package offgrid.geogram.p2p;

import android.content.Context;
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

    private static final String TAG = "DeviceRelayClient";
    private static final int PING_INTERVAL_MS = 60000; // 1 minute
    private static final int RECONNECT_DELAY_MS = 5000; // 5 seconds

    private static DeviceRelayClient instance;

    private Context context;
    private OkHttpClient okHttpClient;
    private WebSocket webSocket;
    private Handler handler;
    private String serverUrl;
    private String callsign;
    private boolean isConnected = false;
    private boolean shouldConnect = false;

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
        ConfigManager configManager = ConfigManager.getInstance(context);

        if (!configManager.getConfig().isDeviceRelayEnabled()) {
            Log.d(TAG, "Device relay is disabled in settings");
            return;
        }

        this.serverUrl = configManager.getConfig().getDeviceRelayServerUrl();
        this.callsign = configManager.getConfig().getCallsign();

        if (callsign == null || callsign.isEmpty()) {
            Log.e(TAG, "Cannot start relay client: callsign not set");
            return;
        }

        shouldConnect = true;
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
            return;
        }

        if (serverUrl == null || serverUrl.isEmpty()) {
            Log.e(TAG, "Server URL not configured");
            return;
        }

        Log.d(TAG, "Connecting to relay server: " + serverUrl);

        Request request = new Request.Builder()
                .url(serverUrl)
                .build();

        webSocket = okHttpClient.newWebSocket(request, this);
    }

    /**
     * Reconnect after delay
     */
    private void reconnect() {
        if (!shouldConnect) {
            return;
        }

        Log.d(TAG, "Reconnecting in " + (RECONNECT_DELAY_MS / 1000) + " seconds...");
        handler.postDelayed(this::connect, RECONNECT_DELAY_MS);
    }

    // WebSocketListener methods

    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        Log.d(TAG, "✓ WebSocket connected to relay server");
        isConnected = true;

        // Send registration message
        DeviceRelayMessage register = DeviceRelayMessage.createRegister(callsign);
        webSocket.send(register.toJson());
        Log.d(TAG, "→ Sent REGISTER message with callsign: " + callsign);
    }

    @Override
    public void onMessage(WebSocket webSocket, String text) {
        try {
            DeviceRelayMessage message = DeviceRelayMessage.fromJson(text);
            if (message == null) {
                Log.e(TAG, "Failed to parse message: " + text);
                return;
            }

            Log.d(TAG, "← Received message type: " + message.type);

            switch (message.type) {
                case REGISTER:
                    Log.d(TAG, "✓ Registration confirmed for callsign: " + message.callsign);
                    break;

                case HTTP_REQUEST:
                    handleHttpRequest(webSocket, message);
                    break;

                case PING:
                    // Respond with PONG
                    webSocket.send(DeviceRelayMessage.createPong().toJson());
                    break;

                case PONG:
                    // Heartbeat acknowledged
                    break;

                case ERROR:
                    Log.e(TAG, "Server error: " + message.error);
                    break;

                default:
                    Log.w(TAG, "Unknown message type: " + message.type);
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling message", e);
        }
    }

    @Override
    public void onClosing(WebSocket webSocket, int code, String reason) {
        Log.d(TAG, "WebSocket closing: " + code + " - " + reason);
        isConnected = false;
    }

    @Override
    public void onClosed(WebSocket webSocket, int code, String reason) {
        Log.d(TAG, "WebSocket closed: " + code + " - " + reason);
        isConnected = false;
        reconnect();
    }

    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        Log.e(TAG, "WebSocket error: " + t.getMessage(), t);
        isConnected = false;
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
                    String responseBody = response.body() != null ? response.body().string() : "";
                    String responseHeaders = encodeHeaders(response.headers().toMultimap());

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
}

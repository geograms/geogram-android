/*
 * Copyright (c) geogram
 * License: Apache-2.0
 */
package offgrid.geogram.p2p;

import com.google.gson.Gson;

/**
 * Message format for device relay communication with server
 *
 * @author brito
 */
public class DeviceRelayMessage {

    public enum Type {
        // Device → Server
        REGISTER,      // Device registers with callsign
        HTTP_RESPONSE, // Device sends HTTP response back to server
        PING,          // Device heartbeat

        // Server → Device
        HTTP_REQUEST,  // Server forwards HTTP request to device
        PONG,          // Server heartbeat response
        ERROR          // Error message
    }

    public Type type;
    public String requestId;      // Unique ID to match requests/responses
    public String callsign;       // Device callsign (for REGISTER)
    public String error;          // Error message (for ERROR type)

    // HTTP request fields (for HTTP_REQUEST)
    public String method;         // GET, POST, etc.
    public String path;           // /api/devices, etc.
    public String headers;        // JSON-encoded headers
    public String body;           // Request body

    // HTTP response fields (for HTTP_RESPONSE)
    public int statusCode;        // 200, 404, etc.
    public String responseHeaders; // JSON-encoded headers
    public String responseBody;    // Response body

    private static final Gson gson = new Gson();

    public DeviceRelayMessage() {
    }

    public DeviceRelayMessage(Type type) {
        this.type = type;
    }

    public String toJson() {
        return gson.toJson(this);
    }

    public static DeviceRelayMessage fromJson(String json) {
        try {
            return gson.fromJson(json, DeviceRelayMessage.class);
        } catch (Exception e) {
            return null;
        }
    }

    public static DeviceRelayMessage createRegister(String callsign) {
        DeviceRelayMessage msg = new DeviceRelayMessage(Type.REGISTER);
        msg.callsign = callsign;
        return msg;
    }

    public static DeviceRelayMessage createHttpResponse(String requestId, int statusCode, String headers, String body) {
        DeviceRelayMessage msg = new DeviceRelayMessage(Type.HTTP_RESPONSE);
        msg.requestId = requestId;
        msg.statusCode = statusCode;
        msg.responseHeaders = headers;
        msg.responseBody = body;
        return msg;
    }

    public static DeviceRelayMessage createPing() {
        return new DeviceRelayMessage(Type.PING);
    }

    public static DeviceRelayMessage createPong() {
        return new DeviceRelayMessage(Type.PONG);
    }
}

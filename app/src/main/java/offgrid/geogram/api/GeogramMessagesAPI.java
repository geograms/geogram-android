package offgrid.geogram.api;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import offgrid.geogram.core.Log;
import offgrid.geogram.util.nostr.Bech32;
import offgrid.geogram.util.nostr.NostrException;
import offgrid.geogram.util.nostr.NostrUtil;
import offgrid.geogram.util.nostr.PrivateKey;
import offgrid.geogram.util.nostr.Schnorr;

/**
 * API client for Geogram Messages (1:1 and group conversations) via NOSTR protocol
 * Communicates with https://api.geogram.radio/nostr
 */
public class GeogramMessagesAPI {

    private static final String TAG = "GeogramMessagesAPI";
    private static final String API_ENDPOINT = "https://api.geogram.radio/nostr";
    private static final String API_ENDPOINT_DEV = "http://localhost:8080/nostr";
    private static final boolean DEBUG = false;

    /**
     * Get list of conversations (peers) for a user
     *
     * @param callsign User's callsign
     * @param nsec NOSTR private key (nsec format)
     * @param npub NOSTR public key (npub format)
     * @return List of conversation IDs (peer names/group names)
     */
    public static List<String> getConversationList(
            String callsign,
            String nsec,
            String npub) throws IOException, JSONException {

        String endpoint = DEBUG ? API_ENDPOINT_DEV : API_ENDPOINT;

        Log.d(TAG, "Getting conversation list for: " + callsign);

        // Build the payload JSON string manually to avoid double-escaping issues
        String payloadJson = String.format(
                "{\"action\":\"messages_list\",\"callsign\":\"%s\",\"path\":\"\"}",
                callsign);

        // Build NOSTR event
        JSONObject event = buildNostrEvent(payloadJson, nsec, npub);

        // Build JSON manually to avoid forward slash escaping
        String eventJson = buildEventJsonManually(event);
        String response = sendPostRequest(endpoint, eventJson);

        Log.d(TAG, "Conversation list response: " + response);

        // Parse response
        return parseConversationListResponse(response);
    }

    /**
     * Get messages for a specific conversation
     *
     * @param callsign User's callsign
     * @param peer Peer ID (conversation ID)
     * @param nsec NOSTR private key (nsec format)
     * @param npub NOSTR public key (npub format)
     * @return Markdown-formatted conversation content
     */
    public static String getConversationMessages(
            String callsign,
            String peer,
            String nsec,
            String npub) throws IOException, JSONException {

        String endpoint = DEBUG ? API_ENDPOINT_DEV : API_ENDPOINT;

        // Build the path: /messages/<peer>-chat.md (following HTML implementation)
        String path = "/messages/" + peer + "-chat.md";

        Log.d(TAG, "Getting messages for conversation: " + peer + " (path: " + path + ")");

        // Build the payload JSON string manually to avoid double-escaping issues
        // org.json.JSONObject escapes forward slashes which can cause issues when nested
        String payloadJson = String.format(
                "{\"action\":\"messages_get\",\"callsign\":\"%s\",\"path\":\"%s\"}",
                callsign, path);

        Log.d(TAG, "Payload JSON: " + payloadJson);

        // Build NOSTR event
        JSONObject event = buildNostrEvent(payloadJson, nsec, npub);

        // Build the final JSON manually to avoid JSONObject's forward slash escaping
        // The signature was calculated with escaped slashes, but the server expects unescaped
        // So we manually build the JSON to ensure no slash escaping
        String eventJson = buildEventJsonManually(event);

        Log.d(TAG, "Event JSON (manual): " + eventJson.substring(0, Math.min(250, eventJson.length())));

        String response = sendPostRequest(endpoint, eventJson);

        Log.d(TAG, "Conversation messages raw response: " + response);

        // Parse response
        String markdown = parseMessagesResponse(response);

        Log.d(TAG, "Conversation messages markdown length: " + markdown.length());
        Log.d(TAG, "Conversation messages markdown content: " + markdown);

        return markdown;
    }

    /**
     * Send a message in a conversation
     *
     * @param callsign User's callsign
     * @param peer Peer ID (conversation ID)
     * @param message Message content (raw text, not formatted)
     * @param nsec NOSTR private key (nsec format)
     * @param npub NOSTR public key (npub format)
     * @return true if successful
     */
    public static boolean sendMessage(
            String callsign,
            String peer,
            String message,
            String nsec,
            String npub) throws IOException, JSONException {

        String endpoint = DEBUG ? API_ENDPOINT_DEV : API_ENDPOINT;

        // Build the path: /messages/<peer>-chat.md (following HTML implementation)
        String path = "/messages/" + peer + "-chat.md";

        Log.d(TAG, "Sending message to: " + peer + " (path: " + path + ")");

        // Build the payload JSON string manually to avoid double-escaping issues
        // Need to escape the message content for JSON
        String escapedMessage = message
                .replace("\\", "\\\\")  // Escape backslashes first
                .replace("\"", "\\\"")  // Escape quotes
                .replace("\n", "\\n")   // Escape newlines
                .replace("\r", "\\r")   // Escape carriage returns
                .replace("\t", "\\t");  // Escape tabs

        String payloadJson = String.format(
                "{\"action\":\"messages_write\",\"callsign\":\"%s\",\"path\":\"%s\",\"content\":\"%s\"}",
                callsign, path, escapedMessage);

        // Build NOSTR event with kind 1 (following HTML implementation)
        JSONObject event = buildNostrEventForWrite(payloadJson, nsec, npub);

        // Build JSON manually to avoid forward slash escaping
        String eventJson = buildEventJsonManually(event);
        String response = sendPostRequest(endpoint, eventJson);

        Log.d(TAG, "Send message response: " + response);

        // Parse response
        JSONObject jsonResponse = new JSONObject(response);
        String result = jsonResponse.optString("result");

        return "OK".equals(result);
    }

    /**
     * Build a NOSTR event with kind 1 for writing messages
     * (Following HTML implementation which uses kind 1 for messages_write)
     */
    private static JSONObject buildNostrEventForWrite(
            String content,
            String nsec,
            String npub) throws JSONException {
        return buildNostrEventWithKind(content, nsec, npub, 1);
    }

    /**
     * Build a NOSTR event with signature (without geo tags for messages)
     */
    private static JSONObject buildNostrEvent(
            String content,
            String nsec,
            String npub) throws JSONException {
        return buildNostrEventWithKind(content, nsec, npub, 30000);
    }

    /**
     * Build a NOSTR event with signature and specified kind
     */
    private static JSONObject buildNostrEventWithKind(
            String content,
            String nsec,
            String npub,
            int kind) throws JSONException {

        try {
            // Convert nsec to hex private key
            String hexPrivateKey;
            if (nsec.startsWith("nsec")) {
                hexPrivateKey = Bech32.fromBech32(nsec);
            } else {
                hexPrivateKey = nsec;
            }

            PrivateKey privateKey = new PrivateKey(hexPrivateKey);
            byte[] privKeyBytes = privateKey.getRawData();

            // Get public key in hex
            byte[] pubKeyBytes = Schnorr.genPubKey(privKeyBytes);
            String pubKeyHex = NostrUtil.bytesToHex(pubKeyBytes);

            // Build event
            JSONObject event = new JSONObject();
            event.put("kind", kind); // Kind 30000 for messages_get/list, kind 1 for messages_write
            event.put("created_at", System.currentTimeMillis() / 1000);
            event.put("pubkey", pubKeyHex);
            event.put("content", content);

            // Add tags
            JSONArray tags = new JSONArray();
            tags.put(new JSONArray().put("client").put("geogram-android"));
            event.put("tags", tags);

            // Calculate event ID (SHA256 of serialized event)
            String serialized = serializeEvent(event);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(serialized.getBytes(StandardCharsets.UTF_8));
            String eventId = NostrUtil.bytesToHex(hash);
            event.put("id", eventId);

            // Sign the event
            byte[] auxRand = NostrUtil.createRandomByteArray(32);
            byte[] signature = Schnorr.sign(hash, privKeyBytes, auxRand);
            String signatureHex = NostrUtil.bytesToHex(signature);
            event.put("sig", signatureHex);

            return event;

        } catch (NostrException e) {
            Log.e(TAG, "Error decoding NOSTR keys: " + e.getMessage());
            throw new JSONException("Failed to decode NOSTR keys: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Error building NOSTR event: " + e.getMessage());
            throw new JSONException("Failed to build NOSTR event: " + e.getMessage());
        }
    }

    /**
     * Serialize event for ID calculation (NIP-01 format)
     * Build manually to avoid forward slash escaping (matches JavaScript JSON.stringify behavior)
     */
    private static String serializeEvent(JSONObject event) throws JSONException {
        StringBuilder sb = new StringBuilder();
        sb.append("[");

        // 0 - reserved
        sb.append("0,");

        // pubkey
        sb.append("\"").append(event.getString("pubkey")).append("\",");

        // created_at
        sb.append(event.getLong("created_at")).append(",");

        // kind
        sb.append(event.getInt("kind")).append(",");

        // tags - this will still use JSONArray.toString() but tags rarely have paths
        sb.append(event.getJSONArray("tags").toString()).append(",");

        // content - escape properly but NOT forward slashes
        String content = event.getString("content");
        String escapedContent = content
                .replace("\\", "\\\\")  // Escape backslashes first!
                .replace("\"", "\\\"")  // Escape quotes
                .replace("\n", "\\n")   // Escape newlines
                .replace("\r", "\\r")   // Escape carriage returns
                .replace("\t", "\\t")   // Escape tabs
                .replace("\b", "\\b")   // Escape backspace
                .replace("\f", "\\f");  // Escape formfeed
        sb.append("\"").append(escapedContent).append("\"");

        sb.append("]");
        return sb.toString();
    }

    /**
     * Build event JSON manually without forward slash escaping
     * This matches what JavaScript's JSON.stringify does (no slash escaping by default)
     */
    private static String buildEventJsonManually(JSONObject event) throws JSONException {
        StringBuilder sb = new StringBuilder();
        sb.append("{");

        // kind
        sb.append("\"kind\":").append(event.getInt("kind")).append(",");

        // created_at
        sb.append("\"created_at\":").append(event.getLong("created_at")).append(",");

        // pubkey
        sb.append("\"pubkey\":\"").append(event.getString("pubkey")).append("\",");

        // content - escape quotes and backslashes but NOT forward slashes
        String content = event.getString("content");
        String escapedContent = content
                .replace("\\", "\\\\")  // Escape backslashes
                .replace("\"", "\\\"")  // Escape quotes
                .replace("\n", "\\n")   // Escape newlines
                .replace("\r", "\\r")   // Escape carriage returns
                .replace("\t", "\\t");  // Escape tabs
        sb.append("\"content\":\"").append(escapedContent).append("\",");

        // tags
        sb.append("\"tags\":").append(event.getJSONArray("tags").toString()).append(",");

        // id
        sb.append("\"id\":\"").append(event.getString("id")).append("\",");

        // sig
        sb.append("\"sig\":\"").append(event.getString("sig")).append("\"");

        sb.append("}");
        return sb.toString();
    }

    /**
     * Send HTTP POST request
     */
    private static String sendPostRequest(String endpoint, String jsonBody) throws IOException {
        URL url = new URL(endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);

        // Write request body
        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = jsonBody.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        // Read response
        int responseCode = conn.getResponseCode();
        StringBuilder response = new StringBuilder();

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(
                        responseCode >= 400 ? conn.getErrorStream() : conn.getInputStream(),
                        StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line.trim());
            }
        }

        if (responseCode >= 400) {
            throw new IOException("HTTP " + responseCode + ": " + response.toString());
        }

        return response.toString();
    }

    /**
     * Parse conversation list response
     */
    private static List<String> parseConversationListResponse(String response) throws JSONException {
        List<String> conversations = new ArrayList<>();

        JSONObject jsonResponse = new JSONObject(response);
        String content = jsonResponse.optString("content", "");

        // Content is semicolon-separated list of conversation IDs
        if (!content.isEmpty()) {
            String[] parts = content.split(";");
            for (String part : parts) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    conversations.add(trimmed);
                }
            }
        }

        Log.d(TAG, "Parsed " + conversations.size() + " conversations");
        return conversations;
    }

    /**
     * Parse messages response
     */
    private static String parseMessagesResponse(String response) throws JSONException {
        JSONObject jsonResponse = new JSONObject(response);

        // Check if this is an error response
        String result = jsonResponse.optString("result", "");
        if ("error".equals(result)) {
            String details = jsonResponse.optString("details", "Unknown error");
            Log.e(TAG, "Server returned error: " + details);
            throw new JSONException("Server error: " + details);
        }

        // Check if this is an invalid request response
        String request = jsonResponse.optString("request", "");
        if ("invalid".equals(request)) {
            Log.e(TAG, "Server rejected request as invalid. Full response: " + response);
            throw new JSONException("Server rejected request as invalid");
        }

        return jsonResponse.optString("content", "");
    }
}

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

import offgrid.geogram.apps.chat.ChatMessage;
import offgrid.geogram.apps.chat.ChatMessageType;
import offgrid.geogram.core.Log;
import offgrid.geogram.util.nostr.Bech32;
import offgrid.geogram.util.nostr.NostrException;
import offgrid.geogram.util.nostr.NostrUtil;
import offgrid.geogram.util.nostr.PrivateKey;
import offgrid.geogram.util.nostr.Schnorr;

/**
 * API client for Geogram chat via NOSTR protocol
 * Communicates with https://api.geogram.radio/nostr
 */
public class GeogramChatAPI {

    private static final String TAG = "GeogramChatAPI";
    private static final String API_ENDPOINT = "https://api.geogram.radio/nostr";
    private static final String API_ENDPOINT_DEV = "http://localhost:8080/nostr";
    private static final boolean DEBUG = false; // Set to true for local testing

    /**
     * Read chat messages within a radius from specified coordinates
     *
     * @param lat Latitude
     * @param lon Longitude
     * @param radiusKm Radius in kilometers
     * @param callsign User's callsign
     * @param nsec NOSTR private key (nsec format)
     * @param npub NOSTR public key (npub format)
     * @return List of chat messages
     */
    public static List<ChatMessage> readMessages(
            double lat,
            double lon,
            int radiusKm,
            String callsign,
            String nsec,
            String npub) throws IOException, JSONException {

        String endpoint = DEBUG ? API_ENDPOINT_DEV : API_ENDPOINT;

        // Build the payload (lat/lon go in tags, not in content)
        JSONObject payload = new JSONObject();
        payload.put("action", "chat_read");
        payload.put("callsign", callsign);
        payload.put("path", "");  // Empty string to avoid forward slash escaping issues
        payload.put("message", "");
        payload.put("radius", String.valueOf(radiusKm));

        // Build NOSTR event
        JSONObject event = buildNostrEvent(lat, lon, payload.toString(), nsec, npub);

        // Send request
        String response = sendPostRequest(endpoint, event.toString());

        // Parse response
        return parseReadResponse(response);
    }

    /**
     * Write a chat message
     *
     * @param lat Latitude
     * @param lon Longitude
     * @param message Message text
     * @param callsign User's callsign
     * @param nsec NOSTR private key (nsec format)
     * @param npub NOSTR public key (npub format)
     * @return true if successful
     */
    public static boolean writeMessage(
            double lat,
            double lon,
            String message,
            String callsign,
            String nsec,
            String npub) throws IOException, JSONException {

        String endpoint = DEBUG ? API_ENDPOINT_DEV : API_ENDPOINT;

        // Build the payload (lat/lon go in tags, not in content)
        JSONObject payload = new JSONObject();
        payload.put("action", "chat_write");
        payload.put("callsign", callsign);
        payload.put("path", "");  // Empty string to avoid forward slash escaping issues
        payload.put("message", message);

        Log.d(TAG, "Write message payload: " + payload.toString());

        // Build NOSTR event
        JSONObject event = buildNostrEvent(lat, lon, payload.toString(), nsec, npub);

        Log.d(TAG, "NOSTR event (without sig): " + event.toString());

        // Send request
        String response = sendPostRequest(endpoint, event.toString());

        Log.d(TAG, "Server response: " + response);

        // Parse response
        JSONObject jsonResponse = new JSONObject(response);
        String result = jsonResponse.optString("result");
        String request = jsonResponse.optString("request");

        Log.d(TAG, "Result: " + result + ", Request status: " + request);

        if ("invalid".equals(request)) {
            Log.e(TAG, "Server returned 'invalid' - full response: " + response);
        }

        return "OK".equals(result);
    }

    /**
     * Build a NOSTR event with signature
     */
    private static JSONObject buildNostrEvent(
            double lat,
            double lon,
            String content,
            String nsec,
            String npub) throws JSONException {

        try {
            // Convert nsec to hex private key
            String hexPrivateKey;
            if (nsec.startsWith("nsec")) {
                // Decode bech32 nsec to hex
                hexPrivateKey = Bech32.fromBech32(nsec);
            } else {
                // Already in hex format
                hexPrivateKey = nsec;
            }

            Log.d(TAG, "nsec (first 10 chars): " + nsec.substring(0, Math.min(10, nsec.length())) + "...");
            Log.d(TAG, "npub (first 10 chars): " + npub.substring(0, Math.min(10, npub.length())) + "...");

            PrivateKey privateKey = new PrivateKey(hexPrivateKey);
            byte[] privKeyBytes = privateKey.getRawData();

            // Get public key in hex
            byte[] pubKeyBytes = Schnorr.genPubKey(privKeyBytes);
            String pubKeyHex = NostrUtil.bytesToHex(pubKeyBytes);

            // Build event
            JSONObject event = new JSONObject();
            event.put("kind", 1);
            event.put("created_at", System.currentTimeMillis() / 1000);
            event.put("pubkey", pubKeyHex);
            event.put("content", content);

            // Add tags (use US locale to ensure dot as decimal separator)
            JSONArray tags = new JSONArray();
            tags.put(new JSONArray().put("app").put("geogram-android"));
            tags.put(new JSONArray().put("g").put(String.format(Locale.US, "geo:%.6f,%.6f", lat, lon)));
            event.put("tags", tags);

            // Calculate event ID (SHA256 of serialized event)
            String serialized = serializeEvent(event);
            Log.d(TAG, "Serialized event for ID: " + serialized);

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(serialized.getBytes(StandardCharsets.UTF_8));
            String eventId = NostrUtil.bytesToHex(hash);
            event.put("id", eventId);

            Log.d(TAG, "Event ID: " + eventId);
            Log.d(TAG, "Derived pubkey from nsec: " + pubKeyHex);

            // Verify npub matches derived pubkey
            try {
                String npubHex = Bech32.fromBech32(npub);
                if (!npubHex.equals(pubKeyHex)) {
                    Log.e(TAG, "WARNING: npub doesn't match nsec!");
                    Log.e(TAG, "npub decodes to: " + npubHex);
                    Log.e(TAG, "nsec derives to: " + pubKeyHex);
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not decode npub for verification: " + e.getMessage());
            }

            // Sign the event
            byte[] auxRand = NostrUtil.createRandomByteArray(32);
            byte[] signature = Schnorr.sign(hash, privKeyBytes, auxRand);
            String signatureHex = NostrUtil.bytesToHex(signature);
            event.put("sig", signatureHex);

            Log.d(TAG, "Signature: " + signatureHex);
            Log.d(TAG, "Built NOSTR event: " + event.toString());
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
     * CRITICAL: Must match server's Gson serialization exactly - no spaces!
     */
    private static String serializeEvent(JSONObject event) throws JSONException {
        JSONArray arr = new JSONArray();
        arr.put(0); // reserved
        arr.put(event.getString("pubkey"));
        arr.put(event.getLong("created_at"));
        arr.put(event.getInt("kind"));
        arr.put(event.getJSONArray("tags"));
        arr.put(event.getString("content"));

        // JSONArray.toString() might add spaces, but server uses Gson with no spaces
        // We need to ensure NO SPACES in the serialization
        String serialized = arr.toString();

        // Remove any spaces after colons and commas that JSONArray might add
        // Note: We must NOT remove spaces inside the content string itself
        return serialized;
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

        Log.d(TAG, "API Response: " + response.toString());
        return response.toString();
    }

    /**
     * Parse the read messages response
     */
    private static List<ChatMessage> parseReadResponse(String response) throws JSONException {
        List<ChatMessage> messages = new ArrayList<>();
        JSONObject jsonResponse = new JSONObject(response);

        if (!"OK".equals(jsonResponse.optString("result"))) {
            Log.i(TAG, "API returned non-OK result: " + response);
            return messages;
        }

        JSONArray details = jsonResponse.optJSONArray("details");
        if (details == null) {
            return messages;
        }

        for (int i = 0; i < details.length(); i++) {
            JSONObject detail = details.getJSONObject(i);

            String author = detail.optString("author", "UNKNOWN");
            String content = detail.optString("content", "");

            ChatMessage message = new ChatMessage(author, content);

            // Parse timestamp (format: "2025-09-30 13:41_54")
            String timestamp = detail.optString("timestamp", "");
            message.setTimestamp(parseTimestamp(timestamp));

            // Tag as internet message
            message.setMessageType(ChatMessageType.INTERNET);

            messages.add(message);
        }

        Log.d(TAG, "Parsed " + messages.size() + " messages from API");
        return messages;
    }

    /**
     * Parse timestamp from server format to milliseconds
     * Format: "2025-09-30 13:41_54"
     */
    private static long parseTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) {
            return System.currentTimeMillis();
        }

        try {
            // Replace underscore with colon for parsing
            timestamp = timestamp.replace("_", ":");

            // Use SimpleDateFormat for proper date parsing
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            java.util.Date date = sdf.parse(timestamp);

            if (date != null) {
                return date.getTime();
            } else {
                return System.currentTimeMillis();
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to parse timestamp: " + timestamp + " - " + e.getMessage());
            return System.currentTimeMillis();
        }
    }
}

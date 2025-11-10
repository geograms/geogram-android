package offgrid.geogram.relay;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;

import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import offgrid.geogram.util.nostr.NostrUtil;
import offgrid.geogram.util.nostr.PublicKey;

/**
 * Relay message data model.
 *
 * Represents a message in the Geogram relay system, using the markdown format
 * specified in relay-protocol.md.
 *
 * Format:
 * > YYYY-MM-DD HH:MM_SS -- SENDER-CALLSIGN
 * Message content here.
 *
 * --> to: RECIPIENT-CALLSIGN
 * --> id: message-id-hash
 * --> type: private
 * --> priority: normal
 * --> ttl: 604800
 * --> signature: abc123...
 */
public class RelayMessage {

    private static final String TAG = "RelayMessage";

    // Message metadata
    private String id;              // SHA-256 hex (64 chars)
    private String fromCallsign;
    private String toCallsign;
    private long timestamp;         // Unix timestamp (seconds)
    private String content;         // Message body

    // Message fields
    private String type;            // private, broadcast, emergency, etc.
    private String priority;        // urgent, normal, low
    private long ttl;               // Time-to-live (seconds)
    private String signature;       // Cryptographic signature

    // NOSTR fields
    private String fromNpub;
    private String toNpub;

    // Relay metadata
    private List<String> relayPath; // List of relay nodes
    private String location;        // Geographic coordinates
    private int hopCount;

    // Attachments
    private List<RelayAttachment> attachments;

    // Additional metadata
    private Map<String, String> customFields;

    // Storage metadata (not part of message format)
    private long receivedAt;        // When this relay received it
    private String receivedVia;     // bluetooth, internet, lora, etc.
    private boolean delivered;      // Has it been delivered to destination?

    public RelayMessage() {
        this.relayPath = new ArrayList<>();
        this.attachments = new ArrayList<>();
        this.customFields = new HashMap<>();
        this.type = "private";
        this.priority = "normal";
        this.ttl = 604800; // Default 7 days
        this.hopCount = 0;
        this.delivered = false;
    }

    /**
     * Parse a relay message from markdown format.
     *
     * @param markdown Markdown-formatted relay message
     * @return Parsed RelayMessage object, or null if parsing fails
     */
    public static RelayMessage parseMarkdown(String markdown) {
        if (markdown == null || markdown.trim().isEmpty()) {
            return null;
        }

        try {
            RelayMessage message = new RelayMessage();
            String[] lines = markdown.split("\n");

            // Parse header (> YYYY-MM-DD HH:MM_SS -- SENDER-CALLSIGN)
            Pattern headerPattern = Pattern.compile("^>\\s+(\\d{4}-\\d{2}-\\d{2})\\s+(\\d{2}):(\\d{2})_(\\d{2})\\s+--\\s+(.+)$");
            Matcher headerMatcher = headerPattern.matcher(lines[0].trim());

            if (!headerMatcher.matches()) {
                Log.e(TAG, "Invalid message header: " + lines[0]);
                return null;
            }

            String date = headerMatcher.group(1);
            String hour = headerMatcher.group(2);
            String minute = headerMatcher.group(3);
            String second = headerMatcher.group(4);
            message.fromCallsign = headerMatcher.group(5).trim();

            // Convert to Unix timestamp
            message.timestamp = parseTimestamp(date, hour, minute, second);

            // Parse content and metadata
            StringBuilder contentBuilder = new StringBuilder();
            boolean inContent = true;
            int lineIndex = 1;

            while (lineIndex < lines.length) {
                String line = lines[lineIndex];

                if (line.trim().startsWith("-->")) {
                    inContent = false;
                    // Parse metadata field
                    String metadataLine = line.trim().substring(3).trim();
                    int colonIndex = metadataLine.indexOf(':');
                    if (colonIndex > 0) {
                        String key = metadataLine.substring(0, colonIndex).trim();
                        String value = metadataLine.substring(colonIndex + 1).trim();
                        message.parseMetadataField(key, value);
                    }
                } else if (line.trim().startsWith("## ATTACHMENT:")) {
                    // Start of attachment section
                    RelayAttachment attachment = RelayAttachment.parseFromMarkdown(lines, lineIndex);
                    if (attachment != null) {
                        message.attachments.add(attachment);
                        // Skip to end of attachment
                        while (lineIndex < lines.length && !lines[lineIndex].trim().equals("# ATTACHMENT_DATA_END")) {
                            lineIndex++;
                        }
                    }
                } else if (inContent && lineIndex > 0) {
                    // Content line
                    if (contentBuilder.length() > 0) {
                        contentBuilder.append("\n");
                    }
                    contentBuilder.append(line);
                }

                lineIndex++;
            }

            message.content = contentBuilder.toString().trim();

            return message;

        } catch (Exception e) {
            Log.e(TAG, "Error parsing markdown: " + e.getMessage());
            return null;
        }
    }

    /**
     * Parse timestamp from date/time components.
     */
    private static long parseTimestamp(String date, String hour, String minute, String second) {
        try {
            String[] dateParts = date.split("-");
            int year = Integer.parseInt(dateParts[0]);
            int month = Integer.parseInt(dateParts[1]);
            int day = Integer.parseInt(dateParts[2]);
            int h = Integer.parseInt(hour);
            int m = Integer.parseInt(minute);
            int s = Integer.parseInt(second);

            // Convert to Unix timestamp (simplified - using approx calculation)
            // In production, use java.time.Instant or Calendar
            long epochDays = (year - 1970) * 365L + (month - 1) * 30L + day;
            return epochDays * 86400L + h * 3600L + m * 60L + s;
        } catch (Exception e) {
            Log.e(TAG, "Error parsing timestamp: " + e.getMessage());
            return System.currentTimeMillis() / 1000;
        }
    }

    /**
     * Parse a metadata field and set appropriate property.
     */
    private void parseMetadataField(String key, String value) {
        switch (key) {
            case "to":
                this.toCallsign = value;
                break;
            case "id":
                this.id = value;
                break;
            case "type":
                this.type = value;
                break;
            case "priority":
                this.priority = value;
                break;
            case "ttl":
                try {
                    this.ttl = Long.parseLong(value);
                } catch (NumberFormatException e) {
                    this.ttl = 604800; // Default 7 days
                }
                break;
            case "signature":
                this.signature = value;
                break;
            case "from-npub":
                this.fromNpub = value;
                break;
            case "to-npub":
                this.toNpub = value;
                break;
            case "location":
                this.location = value;
                break;
            case "hop-count":
                try {
                    this.hopCount = Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    this.hopCount = 0;
                }
                break;
            case "received-via":
                this.receivedVia = value;
                break;
            default:
                // Custom field
                this.customFields.put(key, value);
                break;
        }
    }

    /**
     * Serialize message to markdown format.
     */
    public String toMarkdown() {
        StringBuilder sb = new StringBuilder();

        // Header
        String timeString = formatTimestamp(timestamp);
        sb.append("> ").append(timeString).append(" -- ").append(fromCallsign).append("\n");

        // Content
        sb.append(content).append("\n\n");

        // Metadata fields
        sb.append("--> to: ").append(toCallsign).append("\n");
        sb.append("--> id: ").append(id).append("\n");
        sb.append("--> type: ").append(type).append("\n");
        sb.append("--> priority: ").append(priority).append("\n");
        sb.append("--> ttl: ").append(ttl).append("\n");

        if (fromNpub != null) {
            sb.append("--> from-npub: ").append(fromNpub).append("\n");
        }
        if (toNpub != null) {
            sb.append("--> to-npub: ").append(toNpub).append("\n");
        }
        if (location != null) {
            sb.append("--> location: ").append(location).append("\n");
        }
        if (hopCount > 0) {
            sb.append("--> hop-count: ").append(hopCount).append("\n");
        }
        if (receivedVia != null) {
            sb.append("--> received-via: ").append(receivedVia).append("\n");
        }

        // Custom fields
        for (Map.Entry<String, String> entry : customFields.entrySet()) {
            sb.append("--> ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }

        if (signature != null) {
            sb.append("--> signature: ").append(signature).append("\n");
        }

        // Attachments
        for (int i = 0; i < attachments.size(); i++) {
            sb.append("\n");
            sb.append(attachments.get(i).toMarkdown(i));
        }

        return sb.toString();
    }

    /**
     * Format timestamp as YYYY-MM-DD HH:MM_SS.
     */
    private String formatTimestamp(long unixTimestamp) {
        // Simplified conversion
        // In production, use java.time.Instant or SimpleDateFormat
        long days = unixTimestamp / 86400;
        long remainingSeconds = unixTimestamp % 86400;
        int hours = (int) (remainingSeconds / 3600);
        int minutes = (int) ((remainingSeconds % 3600) / 60);
        int seconds = (int) (remainingSeconds % 60);

        int year = 1970 + (int) (days / 365);
        int month = ((int) (days % 365) / 30) + 1;
        int day = ((int) (days % 365) % 30) + 1;

        return String.format("%04d-%02d-%02d %02d:%02d_%02d", year, month, day, hours, minutes, seconds);
    }

    /**
     * Generate NOSTR-style message ID.
     *
     * Format: [0, pubkey, created_at, kind, tags, content]
     *
     * @param fromNpub Sender's npub
     * @param toNpub Recipient's npub
     * @param timestamp Unix timestamp (seconds)
     * @param content Message content
     * @return SHA-256 hex string (64 chars)
     */
    public static String generateMessageId(String fromNpub, String toNpub,
                                           long timestamp, String content) {
        try {
            // Convert npub to hex pubkey
            PublicKey fromPubKey = new PublicKey(fromNpub);
            PublicKey toPubKey = new PublicKey(toNpub);

            String fromPubkeyHex = fromPubKey.toBech32String();
            String toPubkeyHex = toPubKey.toBech32String();

            // Build JSON array: [0, pubkey, created_at, kind, tags, content]
            JSONArray eventData = new JSONArray();
            eventData.put(0);
            eventData.put(fromPubkeyHex);
            eventData.put(timestamp);
            eventData.put(30078); // Kind for relay messages

            // Tags
            JSONArray tags = new JSONArray();
            JSONArray pTag = new JSONArray();
            pTag.put("p");
            pTag.put(toPubkeyHex);
            tags.put(pTag);

            JSONArray tTag = new JSONArray();
            tTag.put("t");
            tTag.put("relay");
            tags.put(tTag);

            eventData.put(tags);
            eventData.put(content);

            // Compact JSON (no whitespace)
            String jsonStr = eventData.toString();

            // SHA-256 hash
            byte[] hash = NostrUtil.sha256(jsonStr.getBytes());
            return NostrUtil.bytesToHex(hash);

        } catch (NoSuchAlgorithmException | JSONException e) {
            Log.e(TAG, "Error generating message ID: " + e.getMessage());
            return null;
        }
    }

    /**
     * Check if message has expired based on TTL.
     */
    public boolean isExpired() {
        long now = System.currentTimeMillis() / 1000;
        return (timestamp + ttl) < now;
    }

    /**
     * Check if this relay should accept/forward this message.
     */
    public boolean shouldAccept(RelaySettings settings) {
        // Check if already expired
        if (isExpired()) {
            return false;
        }

        // Check message type filtering
        String acceptedTypes = settings.getAcceptedMessageTypes();

        if (acceptedTypes.equals("text_only") && !attachments.isEmpty()) {
            return false; // Reject messages with attachments
        }

        if (acceptedTypes.equals("text_and_images")) {
            // Check if all attachments are images
            for (RelayAttachment att : attachments) {
                if (!att.getMimeType().startsWith("image/")) {
                    return false;
                }
            }
        }

        // Check total size against limits
        long totalSize = getTotalSize();
        long maxSize = 1048576; // 1 MB default

        return totalSize <= maxSize;
    }

    /**
     * Get total message size (content + attachments).
     */
    public long getTotalSize() {
        long size = content != null ? content.length() : 0;
        for (RelayAttachment att : attachments) {
            size += att.getSize();
        }
        return size;
    }

    // Getters and setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFromCallsign() {
        return fromCallsign;
    }

    public void setFromCallsign(String fromCallsign) {
        this.fromCallsign = fromCallsign;
    }

    public String getToCallsign() {
        return toCallsign;
    }

    public void setToCallsign(String toCallsign) {
        this.toCallsign = toCallsign;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public long getTtl() {
        return ttl;
    }

    public void setTtl(long ttl) {
        this.ttl = ttl;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public String getFromNpub() {
        return fromNpub;
    }

    public void setFromNpub(String fromNpub) {
        this.fromNpub = fromNpub;
    }

    public String getToNpub() {
        return toNpub;
    }

    public void setToNpub(String toNpub) {
        this.toNpub = toNpub;
    }

    public List<String> getRelayPath() {
        return relayPath;
    }

    public void addRelayNode(String node) {
        this.relayPath.add(node);
        this.hopCount = this.relayPath.size();
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public int getHopCount() {
        return hopCount;
    }

    public List<RelayAttachment> getAttachments() {
        return attachments;
    }

    public void addAttachment(RelayAttachment attachment) {
        this.attachments.add(attachment);
    }

    public long getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(long receivedAt) {
        this.receivedAt = receivedAt;
    }

    public String getReceivedVia() {
        return receivedVia;
    }

    public void setReceivedVia(String receivedVia) {
        this.receivedVia = receivedVia;
    }

    public boolean isDelivered() {
        return delivered;
    }

    public void setDelivered(boolean delivered) {
        this.delivered = delivered;
    }

    public void setCustomField(String key, String value) {
        this.customFields.put(key, value);
    }

    public String getCustomField(String key) {
        return this.customFields.get(key);
    }
}

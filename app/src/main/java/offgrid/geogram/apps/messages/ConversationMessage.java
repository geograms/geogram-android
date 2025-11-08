package offgrid.geogram.apps.messages;

/**
 * Represents a single message within a conversation
 * Parsed from markdown format: ">metadata\ncontent"
 */
public class ConversationMessage {
    private String meta;        // Metadata line (contains author and timestamp)
    private String content;     // Message content
    private String author;      // Extracted author from metadata
    private boolean fromSelf;   // True if message is from current user
    private long timestamp;     // Message timestamp

    public ConversationMessage(String meta, String content, String currentUser) {
        this.meta = meta;
        this.content = content;
        this.author = extractAuthor(meta);
        this.fromSelf = this.author.equals(currentUser);
        this.timestamp = System.currentTimeMillis(); // Can be enhanced with actual parsing
    }

    /**
     * Extract author from metadata line
     * Format: "-- X1ABCD" or similar
     */
    private String extractAuthor(String meta) {
        if (meta == null || meta.isEmpty()) {
            return "Unknown";
        }

        // Look for pattern: -- CALLSIGN
        int dashIndex = meta.indexOf("--");
        if (dashIndex >= 0) {
            String afterDash = meta.substring(dashIndex + 2).trim();
            // Extract first word (callsign)
            int spaceIndex = afterDash.indexOf(' ');
            if (spaceIndex > 0) {
                return afterDash.substring(0, spaceIndex).trim();
            } else {
                return afterDash.trim();
            }
        }

        return "Unknown";
    }

    public String getMeta() {
        return meta;
    }

    public String getContent() {
        return content;
    }

    public String getAuthor() {
        return author;
    }

    public boolean isFromSelf() {
        return fromSelf;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}

package offgrid.geogram.devices.chat;

import java.util.ArrayList;

public class ChatMessage {

    public String authorId;
    public String message;
    public long timestamp;
    public boolean delivered = false;
    public boolean read = false;
    // SHA1 list of attachments
    public ArrayList<String> attachments = new ArrayList<>();

    public ChatMessage(String authorId, String message) {
        this.authorId = authorId;
        this.message = message;
        this.timestamp = System.currentTimeMillis();
    }

    public String getMessage() {
        return message;
    }

    public String getAuthorId() {
        return authorId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setDelivered(boolean delivered) {
        this.delivered = delivered;
    }
}

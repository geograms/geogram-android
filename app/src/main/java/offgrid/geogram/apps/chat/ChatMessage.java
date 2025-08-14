package offgrid.geogram.apps.chat;

import java.util.ArrayList;
import java.util.Objects;

import offgrid.geogram.ble.BluetoothMessage;
import offgrid.geogram.old.bluetooth_old.broadcast.BroadcastMessage;

public class ChatMessage implements Comparable<ChatMessage> {

    public String authorId, destinationId;
    public String message;
    public long timestamp;
    public boolean delivered = false;
    public boolean read = false;
    public boolean isWrittenByMe = false;

    // define the message type, by default is only data
    public ChatMessageType messageType = ChatMessageType.DATA;
    // SHA1 list of attachments
    public ArrayList<String> attachments = new ArrayList<>();

    public ChatMessage(String authorId, String message) {
        this.authorId = authorId;
        this.message = message;
        this.timestamp = System.currentTimeMillis();
    }

    public String getMessage() { return message; }

    public String getAuthorId() { return authorId; }

    public long getTimestamp() { return timestamp; }

    public void setDelivered(boolean delivered) { this.delivered = delivered; }

    /** Newest-first ordering (descending timestamp). Stable tie-breakers to avoid Set collisions. */
    @Override
    public int compareTo(ChatMessage other) {
        int byTimeDesc = Long.compare(other.timestamp, this.timestamp);
        if (byTimeDesc != 0) return byTimeDesc;

        int byAuthor = safeStr(this.authorId).compareTo(safeStr(other.authorId));
        if (byAuthor != 0) return byAuthor;

        int byMsg = safeStr(this.message).compareTo(safeStr(other.message));
        if (byMsg != 0) return byMsg;

        // Final tiebreaker to keep TreeSet deterministic even for identical fields
        return Integer.compare(System.identityHashCode(this), System.identityHashCode(other));
    }

    private static String safeStr(String s) { return s == null ? "" : s; }

    /** Equality based on immutable identity of a message: timestamp + authorId + message. */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChatMessage)) return false;
        ChatMessage that = (ChatMessage) o;
        return timestamp == that.timestamp
                && Objects.equals(authorId, that.authorId)
                && Objects.equals(message, that.message);
    }

    public static ChatMessage convert(BluetoothMessage message){
        ChatMessage output = new ChatMessage(message.getIdFromSender(), message.getMessage());
        output.destinationId = message.getIdDestination();
        output.setMessage(message.getMessage());
        output.setTimestamp(message.getTimeStamp());
        return output;
    }

    public static ChatMessage convert(BroadcastMessage message) {
        ChatMessage output = new ChatMessage(message.getDeviceId(), message.getMessage());
        output.setDestinationId(message.getDeviceId());
        output.setMessage(message.getMessage());
        output.setTimestamp(message.getTimestamp());
        return output;
    }

    public void setAuthorId(String authorId) {
        this.authorId = authorId;
    }

    public String getDestinationId() {
        return destinationId;
    }

    public void setDestinationId(String destinationId) {
        this.destinationId = destinationId;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isDelivered() {
        return delivered;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public boolean isWrittenByMe() {
        return isWrittenByMe;
    }

    public void setWrittenByMe(boolean writtenByMe) {
        isWrittenByMe = writtenByMe;
    }

    public ChatMessageType getMessageType() {
        return messageType;
    }

    public void setMessageType(ChatMessageType messageType) {
        this.messageType = messageType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(authorId, message, timestamp);
    }
}

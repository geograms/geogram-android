package offgrid.geogram.apps.messages;

/**
 * Represents a conversation (1:1 or group chat)
 */
public class Conversation {
    private String peerId;              // Conversation ID (callsign or group name)
    private String displayName;         // Display name for the conversation
    private String lastMessage;         // Last message content
    private long lastMessageTime;       // Timestamp of last message
    private int unreadCount;            // Number of unread messages
    private boolean isGroup;            // True if this is a group conversation

    public Conversation(String peerId) {
        this.peerId = peerId;
        this.displayName = peerId;
        this.lastMessage = "";
        this.lastMessageTime = 0;
        this.unreadCount = 0;
        this.isGroup = peerId.startsWith("group-");
    }

    public String getPeerId() {
        return peerId;
    }

    public void setPeerId(String peerId) {
        this.peerId = peerId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
    }

    public long getLastMessageTime() {
        return lastMessageTime;
    }

    public void setLastMessageTime(long lastMessageTime) {
        this.lastMessageTime = lastMessageTime;
    }

    public int getUnreadCount() {
        return unreadCount;
    }

    public void setUnreadCount(int unreadCount) {
        this.unreadCount = unreadCount;
    }

    public boolean isGroup() {
        return isGroup;
    }

    public void setGroup(boolean group) {
        isGroup = group;
    }
}

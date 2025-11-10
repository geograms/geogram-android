package offgrid.geogram.relay;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Relay message storage manager.
 *
 * Provides file-based persistence for relay messages using the following structure:
 * - relay/inbox/    - Messages received from other relays, not yet delivered
 * - relay/outbox/   - Messages to be sent to other relays
 * - relay/sent/     - Messages that have been successfully delivered
 *
 * Each message is stored as a markdown file named by its message ID.
 */
public class RelayStorage {

    private static final String TAG = "RelayStorage";

    private static final String RELAY_DIR = "relay";
    private static final String INBOX_DIR = "inbox";
    private static final String OUTBOX_DIR = "outbox";
    private static final String SENT_DIR = "sent";

    private final Context context;
    private final File relayDir;
    private final File inboxDir;
    private final File outboxDir;
    private final File sentDir;

    public RelayStorage(Context context) {
        this.context = context;

        // Get app's files directory
        File filesDir = context.getFilesDir();

        // Create relay directory structure
        this.relayDir = new File(filesDir, RELAY_DIR);
        this.inboxDir = new File(relayDir, INBOX_DIR);
        this.outboxDir = new File(relayDir, OUTBOX_DIR);
        this.sentDir = new File(relayDir, SENT_DIR);

        // Ensure directories exist
        createDirectories();
    }

    /**
     * Create relay directory structure if it doesn't exist.
     */
    private void createDirectories() {
        if (!relayDir.exists() && !relayDir.mkdirs()) {
            Log.e(TAG, "Failed to create relay directory");
        }
        if (!inboxDir.exists() && !inboxDir.mkdirs()) {
            Log.e(TAG, "Failed to create inbox directory");
        }
        if (!outboxDir.exists() && !outboxDir.mkdirs()) {
            Log.e(TAG, "Failed to create outbox directory");
        }
        if (!sentDir.exists() && !sentDir.mkdirs()) {
            Log.e(TAG, "Failed to create sent directory");
        }
    }

    /**
     * Save a message to the specified folder.
     *
     * @param message The message to save
     * @param folder The folder to save to (inbox, outbox, or sent)
     * @return true if successful, false otherwise
     */
    public boolean saveMessage(RelayMessage message, String folder) {
        if (message == null || message.getId() == null) {
            Log.e(TAG, "Cannot save message: message or ID is null");
            return false;
        }

        File targetDir = getDirectoryForFolder(folder);
        if (targetDir == null) {
            Log.e(TAG, "Invalid folder: " + folder);
            return false;
        }

        File messageFile = new File(targetDir, message.getId() + ".md");

        try {
            // Serialize message to markdown
            String markdown = message.toMarkdown();

            // Write to file
            try (FileOutputStream fos = new FileOutputStream(messageFile)) {
                fos.write(markdown.getBytes());
            }

            Log.d(TAG, "Saved message " + message.getId() + " to " + folder);
            return true;

        } catch (IOException e) {
            Log.e(TAG, "Error saving message: " + e.getMessage());
            return false;
        }
    }

    /**
     * Load a message by ID from any folder.
     *
     * @param messageId The message ID to load
     * @return The loaded message, or null if not found
     */
    public RelayMessage getMessage(String messageId) {
        if (messageId == null) {
            return null;
        }

        // Search all folders
        String[] folders = {INBOX_DIR, OUTBOX_DIR, SENT_DIR};
        for (String folder : folders) {
            RelayMessage message = getMessage(messageId, folder);
            if (message != null) {
                return message;
            }
        }

        return null;
    }

    /**
     * Load a message by ID from a specific folder.
     *
     * @param messageId The message ID to load
     * @param folder The folder to load from
     * @return The loaded message, or null if not found
     */
    public RelayMessage getMessage(String messageId, String folder) {
        if (messageId == null) {
            return null;
        }

        File targetDir = getDirectoryForFolder(folder);
        if (targetDir == null) {
            return null;
        }

        File messageFile = new File(targetDir, messageId + ".md");
        if (!messageFile.exists()) {
            return null;
        }

        try {
            // Read file content
            byte[] bytes = new byte[(int) messageFile.length()];
            try (FileInputStream fis = new FileInputStream(messageFile)) {
                int bytesRead = fis.read(bytes);
                if (bytesRead != bytes.length) {
                    Log.w(TAG, "Incomplete read for message " + messageId);
                }
            }

            String markdown = new String(bytes);
            return RelayMessage.parseMarkdown(markdown);

        } catch (IOException e) {
            Log.e(TAG, "Error loading message: " + e.getMessage());
            return null;
        }
    }

    /**
     * List all messages in a folder.
     *
     * @param folder The folder to list
     * @return List of message IDs
     */
    public List<String> listMessages(String folder) {
        List<String> messageIds = new ArrayList<>();

        File targetDir = getDirectoryForFolder(folder);
        if (targetDir == null || !targetDir.exists()) {
            return messageIds;
        }

        File[] files = targetDir.listFiles((dir, name) -> name.endsWith(".md"));
        if (files != null) {
            for (File file : files) {
                String filename = file.getName();
                String messageId = filename.substring(0, filename.length() - 3); // Remove .md
                messageIds.add(messageId);
            }
        }

        return messageIds;
    }

    /**
     * List all messages in a folder, sorted by modification time.
     *
     * @param folder The folder to list
     * @param newestFirst If true, return newest messages first; otherwise oldest first
     * @return List of message IDs
     */
    public List<String> listMessagesSorted(String folder, boolean newestFirst) {
        File targetDir = getDirectoryForFolder(folder);
        if (targetDir == null || !targetDir.exists()) {
            return new ArrayList<>();
        }

        File[] files = targetDir.listFiles((dir, name) -> name.endsWith(".md"));
        if (files == null || files.length == 0) {
            return new ArrayList<>();
        }

        // Sort by last modified time
        Arrays.sort(files, new Comparator<File>() {
            @Override
            public int compare(File f1, File f2) {
                long diff = f1.lastModified() - f2.lastModified();
                if (newestFirst) {
                    diff = -diff;
                }
                return diff > 0 ? 1 : (diff < 0 ? -1 : 0);
            }
        });

        List<String> messageIds = new ArrayList<>();
        for (File file : files) {
            String filename = file.getName();
            String messageId = filename.substring(0, filename.length() - 3); // Remove .md
            messageIds.add(messageId);
        }

        return messageIds;
    }

    /**
     * Delete a message from a specific folder.
     *
     * @param messageId The message ID to delete
     * @param folder The folder to delete from
     * @return true if deleted, false otherwise
     */
    public boolean deleteMessage(String messageId, String folder) {
        if (messageId == null) {
            return false;
        }

        File targetDir = getDirectoryForFolder(folder);
        if (targetDir == null) {
            return false;
        }

        File messageFile = new File(targetDir, messageId + ".md");
        if (messageFile.exists()) {
            boolean deleted = messageFile.delete();
            if (deleted) {
                Log.d(TAG, "Deleted message " + messageId + " from " + folder);
            }
            return deleted;
        }

        return false;
    }

    /**
     * Move a message from one folder to another.
     *
     * @param messageId The message ID to move
     * @param fromFolder Source folder
     * @param toFolder Destination folder
     * @return true if successful, false otherwise
     */
    public boolean moveMessage(String messageId, String fromFolder, String toFolder) {
        // Load message from source folder
        RelayMessage message = getMessage(messageId, fromFolder);
        if (message == null) {
            Log.e(TAG, "Cannot move message: not found in " + fromFolder);
            return false;
        }

        // Save to destination folder
        if (!saveMessage(message, toFolder)) {
            Log.e(TAG, "Failed to save message to " + toFolder);
            return false;
        }

        // Delete from source folder
        if (!deleteMessage(messageId, fromFolder)) {
            Log.w(TAG, "Failed to delete message from " + fromFolder);
            // Message was copied but not removed - not ideal but acceptable
        }

        Log.d(TAG, "Moved message " + messageId + " from " + fromFolder + " to " + toFolder);
        return true;
    }

    /**
     * Get total storage used by relay messages.
     *
     * @return Total bytes used
     */
    public long getTotalStorageUsed() {
        return getDirectorySize(relayDir);
    }

    /**
     * Get storage used by a specific folder.
     *
     * @param folder The folder to check
     * @return Total bytes used
     */
    public long getFolderStorageUsed(String folder) {
        File targetDir = getDirectoryForFolder(folder);
        if (targetDir == null) {
            return 0;
        }
        return getDirectorySize(targetDir);
    }

    /**
     * Count messages in a folder.
     *
     * @param folder The folder to count
     * @return Number of messages
     */
    public int getMessageCount(String folder) {
        File targetDir = getDirectoryForFolder(folder);
        if (targetDir == null || !targetDir.exists()) {
            return 0;
        }

        File[] files = targetDir.listFiles((dir, name) -> name.endsWith(".md"));
        return files != null ? files.length : 0;
    }

    /**
     * Delete expired messages from all folders.
     *
     * @return Number of messages deleted
     */
    public int deleteExpiredMessages() {
        int deleted = 0;

        String[] folders = {INBOX_DIR, OUTBOX_DIR, SENT_DIR};
        for (String folder : folders) {
            List<String> messageIds = listMessages(folder);
            for (String messageId : messageIds) {
                RelayMessage message = getMessage(messageId, folder);
                if (message != null && message.isExpired()) {
                    if (deleteMessage(messageId, folder)) {
                        deleted++;
                    }
                }
            }
        }

        Log.d(TAG, "Deleted " + deleted + " expired messages");
        return deleted;
    }

    /**
     * Prune old messages to free up space.
     * Deletes oldest low-priority messages first.
     *
     * @param bytesToFree Minimum bytes to free
     * @return Actual bytes freed
     */
    public long pruneOldMessages(long bytesToFree) {
        long bytesFreed = 0;

        // Get all messages sorted by age (oldest first)
        List<MessageInfo> allMessages = new ArrayList<>();

        String[] folders = {INBOX_DIR, OUTBOX_DIR, SENT_DIR};
        for (String folder : folders) {
            List<String> messageIds = listMessagesSorted(folder, false); // oldest first
            for (String messageId : messageIds) {
                RelayMessage message = getMessage(messageId, folder);
                if (message != null) {
                    File messageFile = new File(getDirectoryForFolder(folder), messageId + ".md");
                    allMessages.add(new MessageInfo(messageId, folder, message, messageFile.length()));
                }
            }
        }

        // Sort by priority (low priority first), then by age
        allMessages.sort(new Comparator<MessageInfo>() {
            @Override
            public int compare(MessageInfo m1, MessageInfo m2) {
                // Priority order: low < normal < urgent
                int p1 = getPriorityValue(m1.message.getPriority());
                int p2 = getPriorityValue(m2.message.getPriority());

                if (p1 != p2) {
                    return p1 - p2; // Lower priority first
                }

                // Same priority - older first
                return Long.compare(m1.message.getTimestamp(), m2.message.getTimestamp());
            }

            private int getPriorityValue(String priority) {
                if ("urgent".equalsIgnoreCase(priority)) return 3;
                if ("normal".equalsIgnoreCase(priority)) return 2;
                return 1; // low or unknown
            }
        });

        // Delete messages until we've freed enough space
        for (MessageInfo info : allMessages) {
            if (bytesFreed >= bytesToFree) {
                break;
            }

            if (deleteMessage(info.messageId, info.folder)) {
                bytesFreed += info.size;
                Log.d(TAG, "Pruned message " + info.messageId + " (" + info.size + " bytes)");
            }
        }

        Log.d(TAG, "Pruned " + bytesFreed + " bytes");
        return bytesFreed;
    }

    /**
     * Clear all messages from a folder.
     *
     * @param folder The folder to clear
     * @return Number of messages deleted
     */
    public int clearFolder(String folder) {
        int deleted = 0;

        List<String> messageIds = listMessages(folder);
        for (String messageId : messageIds) {
            if (deleteMessage(messageId, folder)) {
                deleted++;
            }
        }

        Log.d(TAG, "Cleared " + deleted + " messages from " + folder);
        return deleted;
    }

    /**
     * Get the directory for a folder name.
     */
    private File getDirectoryForFolder(String folder) {
        if (folder == null) {
            return null;
        }

        switch (folder) {
            case "inbox":
                return inboxDir;
            case "outbox":
                return outboxDir;
            case "sent":
                return sentDir;
            default:
                return null;
        }
    }

    /**
     * Calculate total size of a directory recursively.
     */
    private long getDirectorySize(File directory) {
        if (directory == null || !directory.exists()) {
            return 0;
        }

        long size = 0;
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    size += file.length();
                } else if (file.isDirectory()) {
                    size += getDirectorySize(file);
                }
            }
        }

        return size;
    }

    /**
     * Helper class for message pruning.
     */
    private static class MessageInfo {
        String messageId;
        String folder;
        RelayMessage message;
        long size;

        MessageInfo(String messageId, String folder, RelayMessage message, long size) {
            this.messageId = messageId;
            this.folder = folder;
            this.message = message;
            this.size = size;
        }
    }

    // Getters for directory paths (useful for testing)

    public File getRelayDir() {
        return relayDir;
    }

    public File getInboxDir() {
        return inboxDir;
    }

    public File getOutboxDir() {
        return outboxDir;
    }

    public File getSentDir() {
        return sentDir;
    }
}

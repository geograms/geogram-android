package offgrid.geogram.database;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import offgrid.geogram.apps.messages.Conversation;
import offgrid.geogram.core.Log;

/**
 * Caches conversation list and messages for offline access
 * Singleton pattern to ensure consistent state
 */
public final class DatabaseConversations {
    private static final String TAG = "DatabaseConversations";
    private static final String CONVERSATIONS_FILE = "conversations.json";
    private static final String MESSAGES_DIR = "conversation_messages";

    // Singleton
    private DatabaseConversations() {}
    private static final class Holder { static final DatabaseConversations I = new DatabaseConversations(); }
    public static DatabaseConversations getInstance() { return Holder.I; }

    private Context appCtx;
    private volatile boolean initialized = false;
    private final Object lock = new Object();

    /**
     * Initialize with application context
     */
    public void init(@NonNull Context context) {
        synchronized (lock) {
            if (initialized) return;
            this.appCtx = context.getApplicationContext();

            // Ensure messages directory exists
            File messagesDir = new File(appCtx.getFilesDir(), MESSAGES_DIR);
            if (!messagesDir.exists()) {
                messagesDir.mkdirs();
            }

            initialized = true;
            Log.d(TAG, "DatabaseConversations initialized");
        }
    }

    private void ensureInitialized() {
        if (!initialized) {
            throw new IllegalStateException("DatabaseConversations not initialized. Call init(context) first.");
        }
    }

    /**
     * Save conversation list to cache
     */
    public void saveConversationList(@NonNull List<Conversation> conversations) {
        synchronized (lock) {
            ensureInitialized();

            try {
                JSONArray arr = new JSONArray();
                for (Conversation conv : conversations) {
                    JSONObject obj = new JSONObject();
                    obj.put("peerId", conv.getPeerId());
                    obj.put("displayName", conv.getDisplayName());
                    obj.put("lastMessage", conv.getLastMessage());
                    obj.put("lastMessageTime", conv.getLastMessageTime());
                    obj.put("unreadCount", conv.getUnreadCount());
                    obj.put("isGroup", conv.isGroup());
                    arr.put(obj);
                }

                writeJsonToFile(CONVERSATIONS_FILE, arr);
                Log.d(TAG, "Saved " + conversations.size() + " conversations to cache");
            } catch (Exception e) {
                Log.e(TAG, "Failed to save conversation list: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Load conversation list from cache
     */
    @NonNull
    public List<Conversation> loadConversationList() {
        synchronized (lock) {
            ensureInitialized();

            List<Conversation> result = new ArrayList<>();
            try {
                File file = new File(appCtx.getFilesDir(), CONVERSATIONS_FILE);
                if (!file.exists()) {
                    Log.d(TAG, "No cached conversations found");
                    return result;
                }

                byte[] data = readFileFully(file);
                JSONArray arr = new JSONArray(new String(data, StandardCharsets.UTF_8));

                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    String peerId = obj.getString("peerId");

                    Conversation conv = new Conversation(peerId);
                    conv.setDisplayName(obj.optString("displayName", peerId));
                    conv.setLastMessage(obj.optString("lastMessage", ""));
                    conv.setLastMessageTime(obj.optLong("lastMessageTime", 0));
                    conv.setUnreadCount(obj.optInt("unreadCount", 0));
                    conv.setGroup(obj.optBoolean("isGroup", false));

                    result.add(conv);
                }

                Log.d(TAG, "Loaded " + result.size() + " conversations from cache");
            } catch (Exception e) {
                Log.e(TAG, "Failed to load conversation list: " + e.getMessage(), e);
            }

            return result;
        }
    }

    /**
     * Save messages for a specific conversation
     */
    public void saveConversationMessages(@NonNull String peerId, @NonNull String markdown) {
        synchronized (lock) {
            ensureInitialized();

            try {
                File messagesDir = new File(appCtx.getFilesDir(), MESSAGES_DIR);
                File messageFile = new File(messagesDir, sanitizeFilename(peerId) + ".txt");

                try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(messageFile))) {
                    out.write(markdown.getBytes(StandardCharsets.UTF_8));
                }

                Log.d(TAG, "Saved messages for " + peerId + " (" + markdown.length() + " bytes)");
            } catch (Exception e) {
                Log.e(TAG, "Failed to save messages for " + peerId + ": " + e.getMessage(), e);
            }
        }
    }

    /**
     * Load messages for a specific conversation
     */
    @Nullable
    public String loadConversationMessages(@NonNull String peerId) {
        synchronized (lock) {
            ensureInitialized();

            try {
                File messagesDir = new File(appCtx.getFilesDir(), MESSAGES_DIR);
                File messageFile = new File(messagesDir, sanitizeFilename(peerId) + ".txt");

                if (!messageFile.exists()) {
                    Log.d(TAG, "No cached messages for " + peerId);
                    return null;
                }

                byte[] data = readFileFully(messageFile);
                String markdown = new String(data, StandardCharsets.UTF_8);

                Log.d(TAG, "Loaded messages for " + peerId + " (" + markdown.length() + " bytes)");
                return markdown;
            } catch (Exception e) {
                Log.e(TAG, "Failed to load messages for " + peerId + ": " + e.getMessage(), e);
                return null;
            }
        }
    }

    /**
     * Clear all cached data
     */
    public void clearAll() {
        synchronized (lock) {
            ensureInitialized();

            // Delete conversations file
            File conversationsFile = new File(appCtx.getFilesDir(), CONVERSATIONS_FILE);
            if (conversationsFile.exists()) {
                conversationsFile.delete();
            }

            // Delete all message files
            File messagesDir = new File(appCtx.getFilesDir(), MESSAGES_DIR);
            if (messagesDir.exists() && messagesDir.isDirectory()) {
                File[] files = messagesDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        file.delete();
                    }
                }
            }

            Log.d(TAG, "Cleared all cached conversations and messages");
        }
    }

    /**
     * Get cache statistics for debugging
     */
    public Map<String, Object> getCacheStats() {
        synchronized (lock) {
            ensureInitialized();

            Map<String, Object> stats = new HashMap<>();

            // Conversation count
            File conversationsFile = new File(appCtx.getFilesDir(), CONVERSATIONS_FILE);
            stats.put("hasConversationList", conversationsFile.exists());
            stats.put("conversationListSize", conversationsFile.length());

            // Message file count
            File messagesDir = new File(appCtx.getFilesDir(), MESSAGES_DIR);
            if (messagesDir.exists() && messagesDir.isDirectory()) {
                File[] files = messagesDir.listFiles();
                stats.put("messageFileCount", files != null ? files.length : 0);
            } else {
                stats.put("messageFileCount", 0);
            }

            return stats;
        }
    }

    // ---- Helper methods ----

    private void writeJsonToFile(String filename, JSONArray json) throws IOException {
        File file = new File(appCtx.getFilesDir(), filename);
        File tmp = new File(appCtx.getFilesDir(), filename + ".tmp");

        byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);

        try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(tmp))) {
            out.write(bytes);
            out.flush();
        }

        // Atomic-ish replace
        if (!tmp.renameTo(file)) {
            // Fallback: direct write
            try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(file))) {
                out.write(bytes);
                out.flush();
            }
        }
    }

    private static byte[] readFileFully(File file) throws IOException {
        int hinted = (int) Math.min(Math.max(file.length(), 0L), 1 << 20); // cap at 1MB
        if (hinted <= 0) hinted = 16 * 1024;

        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(file));
             ByteArrayOutputStream out = new ByteArrayOutputStream(hinted)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }

    /**
     * Sanitize peer ID for use as filename
     */
    private String sanitizeFilename(String peerId) {
        // Replace any characters that might be problematic in filenames
        return peerId.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}

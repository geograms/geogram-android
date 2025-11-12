package offgrid.geogram.database;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import offgrid.geogram.apps.chat.ChatMessage;
import offgrid.geogram.apps.chat.ChatMessageType;

/**
 * Records all the BLE messages that are sent and received.
 * Singleton (holds Application Context only; no Activity leak).
 *
 * Behavior:
 * - Keeps messages in a TreeSet (newest-first via ChatMessage#compareTo).
 * - Capacity limited to MAX_MESSAGES (oldest trimmed automatically).
 * - Avoids duplicates (authorId + timestamp + message).
 * - On add, message is enqueued for disk persistence.
 * - A background task flushes the queue to disk once per minute (batched I/O).
 * - On init, loads messages from disk into the TreeSet.
 */
public final class DatabaseMessages {
    private static final String TAG = "DatabaseMessages";
    private static final int MAX_MESSAGES = 10_000;
    private static final long FLUSH_PERIOD_SECONDS = 60L;
    private static final String FILE_NAME = "messages.json";

    // -------- Singleton --------
    private DatabaseMessages() {}

    public TreeSet<ChatMessage> getMessages() {
        return this.messages;
    }

    /**
     * Get message statistics for a specific conversation/peer
     * @param peerId The conversation identifier (group name or callsign)
     * @return ConversationStats with message count, last message, and unread count
     */
    public ConversationStats getConversationStats(String peerId) {
        synchronized (lock) {
            ensureInitialized();
            int totalCount = 0;
            int unreadCount = 0;
            ChatMessage lastMessage = null;

            Log.d(TAG, "Getting stats for conversation: " + peerId);

            // Create snapshot to avoid concurrent modification issues
            List<ChatMessage> snapshot = new ArrayList<>(messages);

            for (ChatMessage msg : snapshot) {
                // Check if message belongs to this conversation
                // Match by destinationId (for groups and direct messages)
                // Also match by authorId for direct conversations
                boolean belongsToConversation = false;

                // Try exact match first
                if (peerId.equals(msg.destinationId) || peerId.equals(msg.authorId)) {
                    belongsToConversation = true;
                }
                // Try with "group-" prefix for group messages
                else if (msg.destinationId != null &&
                         (msg.destinationId.equals("group-" + peerId) ||
                          peerId.equals("group-" + msg.destinationId))) {
                    belongsToConversation = true;
                }
                // Try without "group-" prefix
                else if (peerId.startsWith("group-") && msg.destinationId != null &&
                         msg.destinationId.equals(peerId.substring(6))) {
                    belongsToConversation = true;
                }

                if (belongsToConversation) {
                    totalCount++;
                    if (!msg.read && !msg.isWrittenByMe) {
                        unreadCount++;
                    }
                    if (lastMessage == null || msg.timestamp > lastMessage.timestamp) {
                        lastMessage = msg;
                    }
                }
            }

            Log.d(TAG, "Conversation " + peerId + " has " + totalCount + " messages, " + unreadCount + " unread");
            return new ConversationStats(totalCount, unreadCount, lastMessage);
        }
    }

    /**
     * Mark all messages in a conversation as read
     * @param peerId The conversation identifier
     */
    public void markConversationAsRead(String peerId) {
        synchronized (lock) {
            ensureInitialized();
            int markedCount = 0;

            for (ChatMessage msg : messages) {
                // Check if message belongs to this conversation
                boolean belongsToConversation = false;

                if (peerId.equals(msg.destinationId) || peerId.equals(msg.authorId)) {
                    belongsToConversation = true;
                } else if (msg.destinationId != null &&
                           (msg.destinationId.equals("group-" + peerId) ||
                            peerId.equals("group-" + msg.destinationId))) {
                    belongsToConversation = true;
                } else if (peerId.startsWith("group-") && msg.destinationId != null &&
                           msg.destinationId.equals(peerId.substring(6))) {
                    belongsToConversation = true;
                }

                // Mark as read if it belongs to this conversation and was not written by me
                if (belongsToConversation && !msg.isWrittenByMe && !msg.read) {
                    msg.read = true;
                    markedCount++;
                }
            }

            if (markedCount > 0) {
                Log.d(TAG, "Marked " + markedCount + " messages as read for conversation: " + peerId);
                // Immediately flush to disk to persist the read status
                // Don't wait for the 60-second periodic flush
                writeAllToDisk(new ArrayList<>(messages));
            }
        }
    }

    /**
     * Container for conversation statistics
     */
    public static class ConversationStats {
        public final int totalMessages;
        public final int unreadCount;
        public final ChatMessage lastMessage;

        public ConversationStats(int totalMessages, int unreadCount, ChatMessage lastMessage) {
            this.totalMessages = totalMessages;
            this.unreadCount = unreadCount;
            this.lastMessage = lastMessage;
        }
    }

    private static final class Holder { static final DatabaseMessages I = new DatabaseMessages(); }
    public static DatabaseMessages getInstance() { return Holder.I; }

    // -------- State --------
    private Context appCtx; // Application context
    private final Object lock = new Object();

    // In-memory store (newest first since ChatMessage.compareTo sorts DESC by timestamp)
    private final TreeSet<ChatMessage> messages = new TreeSet<>();

    // Deduplicate key set (authorId|timestamp|message)
    private final HashSet<String> dedupe = new HashSet<>();

    // Queue of items added since last flush (we batch I/O)
    private final ArrayDeque<ChatMessage> pending = new ArrayDeque<>();

    // Background flusher
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
                @Override public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "DatabaseMessagesFlusher");
                    t.setDaemon(true);
                    return t;
                }
            });
    private ScheduledFuture<?> flushTask;

    private volatile boolean initialized = false;

    // -------- Public API --------

    /** Initialize with any Context (Application context is retained). Safe to call multiple times. */
    public void init(@NonNull Context context) {
        synchronized (lock) {
            if (initialized) return;
            this.appCtx = context.getApplicationContext();

            // Load existing messages from disk
            try {
                ArrayList<ChatMessage> loaded = readAllFromDisk();
                for (ChatMessage m : loaded) {
                    String key = keyOf(m);
                    if (dedupe.add(key)) {
                        messages.add(m);
                    }
                }
                trimToCapacityLocked();
                Log.d(TAG, "Loaded " + messages.size() + " messages from disk");
            } catch (Exception e) {
                Log.w(TAG, "Failed to load messages: " + e.getMessage(), e);
            }

            // Start periodic flush
            if (flushTask == null) {
                flushTask = scheduler.scheduleAtFixedRate(this::flushIfNeededSafe,
                        FLUSH_PERIOD_SECONDS, FLUSH_PERIOD_SECONDS, TimeUnit.SECONDS);
            }
            initialized = true;
        }
    }

    /** Add a single message; returns true if added (i.e., not a duplicate). */
    public boolean add(@NonNull ChatMessage msg) {
        Objects.requireNonNull(msg, "msg");
        synchronized (lock) {
            ensureInitialized();
            String key = keyOf(msg);
            boolean wasAdded = dedupe.add(key);

            if (!wasAdded) {
                // Message already exists - check if we need to add a new channel
                ChatMessage existing = findMessageByKey(key);
                Log.d(TAG, "DUPLICATE DETECTED - Key: '" + key + "', New channel: " + msg.messageType +
                      ", Existing found: " + (existing != null) +
                      ", TreeSet size: " + messages.size());

                if (existing != null && msg.messageType != null && msg.messageType != ChatMessageType.DATA) {
                    if (!existing.hasChannel(msg.messageType)) {
                        existing.addChannel(msg.messageType);
                        Log.d(TAG, "CHANNEL ADDED - Key: '" + key + "', Added channel: " + msg.messageType +
                              ", Total channels: " + existing.channels + ", TreeSet size: " + messages.size());
                        pending.addLast(existing); // queue for disk update
                        return true; // Channel was added
                    } else {
                        Log.d(TAG, "CHANNEL ALREADY EXISTS - Key: '" + key + "', Channel: " + msg.messageType +
                              ", Existing channels: " + existing.channels);
                    }
                }

                Log.d(TAG, "DUPLICATE BLOCKED - Key: '" + key + "', Message: '" +
                      (msg.message != null && msg.message.length() > 20 ? msg.message.substring(0, 20) + "..." : msg.message) +
                      "' from '" + msg.authorId + "', TreeSet size: " + messages.size());
                return false; // duplicate
            }

            Log.d(TAG, "ADDED - Key: '" + key + "', Message: '" +
                  (msg.message != null && msg.message.length() > 20 ? msg.message.substring(0, 20) + "..." : msg.message) +
                  "' from '" + msg.authorId + "', Channel: " + msg.messageType);

            messages.add(msg);
            trimToCapacityLocked();
            pending.addLast(msg); // queue for disk write
            return true;
        }
    }

    /** Find an existing message by its dedupe key */
    private ChatMessage findMessageByKey(String key) {
        for (ChatMessage m : messages) {
            if (key.equals(keyOf(m))) {
                return m;
            }
        }
        return null;
    }

    /** Add a batch of messages; returns number actually added (non-duplicates). */
    public int addAll(@NonNull Iterable<ChatMessage> batch) {
        int added = 0;
        synchronized (lock) {
            ensureInitialized();
            for (ChatMessage m : batch) {
                if (m == null) continue;
                String key = keyOf(m);
                if (dedupe.add(key)) {
                    messages.add(m);
                    pending.addLast(m);
                    added++;
                }
            }
            trimToCapacityLocked();
        }
        return added;
    }

    /** Snapshot copy (newest first). */
    public ArrayList<ChatMessage> snapshot() {
        synchronized (lock) {
            ensureInitialized();
            return new ArrayList<>(messages);
        }
    }

    /** Number of messages currently kept in memory. */
    public int size() {
        synchronized (lock) {
            return messages.size();
        }
    }

    /** Force a synchronous flush (discouraged on UI thread). */
    public void flushNow() {
        flushIfNeededSafe();
    }

    /** Shut down background task (e.g., on process exit). */
    public void shutdown() {
        synchronized (lock) {
            if (flushTask != null) {
                flushTask.cancel(false);
                flushTask = null;
            }
        }
        scheduler.shutdownNow();
        // Best-effort final flush
        flushIfNeededSafe();
    }

    // -------- Internals --------

    private void ensureInitialized() {
        if (!initialized) {
            throw new IllegalStateException("DatabaseMessages not initialized. Call init(context) first.");
        }
    }

    /** Keep only the newest MAX_MESSAGES; remove from the bottom (oldest). */
    private void trimToCapacityLocked() {
        while (messages.size() > MAX_MESSAGES) {
            ChatMessage oldest = messages.pollLast(); // bottom = oldest due to DESC comparator
            if (oldest != null) {
                dedupe.remove(keyOf(oldest));
            }
        }
    }

    /** Build a dedupe key (authorId|timestamp|message). */
    private static String keyOf(ChatMessage m) {
        // Normalize all parts to handle whitespace and casing differences
        String a = m.authorId == null ? "" : m.authorId.trim();
        String dest = m.destinationId == null ? "" : m.destinationId.trim();
        String msg = m.getMessage() == null ? "" : m.getMessage().trim();
        // Don't use timestamp in key - it changes on every parse
        // Use destinationId + authorId + message content for stable deduplication
        return dest + "|" + a + "|" + msg;
    }

    /** Thread-safe wrapper for flush; never throws. */
    private void flushIfNeededSafe() {
        try {
            ArrayList<ChatMessage> toPersist = null;
            synchronized (lock) {
                if (pending.isEmpty() || appCtx == null) return;
                // Drain the queue; we write a fresh snapshot (safer than appending)
                pending.clear();
                toPersist = new ArrayList<>(messages);
            }
            writeAllToDisk(toPersist);
        } catch (Throwable t) {
            Log.e(TAG, "Flush failed", t);
        }
    }

    // ---- Disk I/O ----

    private File dataFile() {
        return new File(appCtx.getFilesDir(), FILE_NAME);
    }

    /** Write the full set to disk as JSON array. */
    private void writeAllToDisk(@NonNull ArrayList<ChatMessage> all) {
        JSONArray arr = new JSONArray();
        for (ChatMessage m : all) {
            arr.put(serialize(m));
        }
        byte[] bytes = arr.toString().getBytes(StandardCharsets.UTF_8);

        File tmp = new File(appCtx.getFilesDir(), FILE_NAME + ".tmp");
        File dst = dataFile();

        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(tmp, false))) {
            bos.write(bytes);
            bos.flush();
        } catch (Exception e) {
            Log.e(TAG, "write tmp failed", e);
            return;
        }

        // Atomic-ish replace
        if (!tmp.renameTo(dst)) {
            // Fallback: direct write
            try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(dst, false))) {
                bos.write(bytes);
                bos.flush();
            } catch (Exception e) {
                Log.e(TAG, "write fallback failed", e);
            }
        }
        Log.d(TAG, "Wrote " + all.size() + " messages to disk");
    }

    /** Read all messages from disk; compatible with older Android (no readAllBytes). */
    private ArrayList<ChatMessage> readAllFromDisk() {
        File f = dataFile();
        ArrayList<ChatMessage> out = new ArrayList<>();
        if (!f.exists()) return out;

        byte[] data;
        try {
            data = readFully(f);
        } catch (IOException e) {
            Log.w(TAG, "read failed", e);
            return out;
        }

        try {
            JSONArray arr = new JSONArray(new String(data, StandardCharsets.UTF_8));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                ChatMessage m = deserialize(o);
                if (m != null) out.add(m);
            }
        } catch (JSONException je) {
            Log.w(TAG, "parse failed", je);
        }
        Log.d(TAG, "Read " + out.size() + " messages from disk");
        return out;
    }

    /** Read a file fully into memory using a classic loop (API 14+ compatible). */
    private static byte[] readFully(@NonNull File file) throws IOException {
        int hinted = (int) Math.min(Math.max(file.length(), 0L), 1 << 20); // cap initial buf at 1MiB
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

    // ---- JSON (org.json) ----

    private static JSONObject serialize(ChatMessage m) {
        JSONObject o = new JSONObject();
        try {
            o.put("authorId", m.authorId);
            o.put("destinationId", m.destinationId);
            o.put("message", m.message);
            o.put("timestamp", m.timestamp);
            o.put("delivered", m.delivered);
            o.put("read", m.read);
            o.put("isWrittenByMe", m.isWrittenByMe);
            o.put("messageType", m.messageType != null ? m.messageType.name() : ChatMessageType.DATA.name());

            // Serialize channels
            JSONArray channelsArray = new JSONArray();
            if (m.channels != null) {
                for (ChatMessageType channel : m.channels) {
                    channelsArray.put(channel.name());
                }
            }
            o.put("channels", channelsArray);

            JSONArray atts = new JSONArray();
            if (m.attachments != null) {
                for (String s : m.attachments) atts.put(s);
            }
            o.put("attachments", atts);
        } catch (JSONException ignore) { }
        return o;
    }

    private static ChatMessage deserialize(JSONObject o) {
        try {
            String authorId = o.optString("authorId", null);
            String message = o.optString("message", null);
            long timestamp = o.getLong("timestamp");

            ChatMessage m = new ChatMessage(authorId, message);
            m.destinationId = o.optString("destinationId", null);
            m.timestamp = timestamp;
            m.delivered = o.optBoolean("delivered", false);
            m.read = o.optBoolean("read", false);
            m.isWrittenByMe = o.optBoolean("isWrittenByMe", false);

            String type = o.optString("messageType", ChatMessageType.DATA.name());
            try { m.messageType = ChatMessageType.valueOf(type); }
            catch (Exception ignored) { m.messageType = ChatMessageType.DATA; }

            // Deserialize channels
            JSONArray channelsArray = o.optJSONArray("channels");
            if (channelsArray != null) {
                for (int i = 0; i < channelsArray.length(); i++) {
                    String channelName = channelsArray.optString(i, null);
                    if (channelName != null) {
                        try {
                            m.channels.add(ChatMessageType.valueOf(channelName));
                        } catch (Exception ignored) { }
                    }
                }
            } else {
                // Backward compatibility: if no channels array, use messageType
                if (m.messageType != null && m.messageType != ChatMessageType.DATA) {
                    m.channels.add(m.messageType);
                }
            }

            JSONArray atts = o.optJSONArray("attachments");
            if (atts != null) {
                for (int i = 0; i < atts.length(); i++) {
                    String s = atts.optString(i, null);
                    if (s != null) m.attachments.add(s);
                }
            }
            return m;
        } catch (Exception e) {
            Log.w(TAG, "deserialize failed: " + e.getMessage());
            return null;
        }
    }
}

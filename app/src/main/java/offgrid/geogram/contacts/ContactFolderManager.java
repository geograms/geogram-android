package offgrid.geogram.contacts;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import offgrid.geogram.core.Log;

/**
 * Manages the contact folder structure on disk.
 *
 * Inspired by the server's CallSignDatabase.java, this class provides
 * access to contact folders and their contents.
 *
 * Directory structure:
 * /data/data/offgrid.geogram.geogram/files/contacts/<CALLSIGN>/
 * ├── profile.json
 * ├── chat/
 * │   ├── 2025-01.ndjson
 * │   └── attachments/
 * └── relay/
 *     ├── inbox/
 *     ├── outbox/
 *     └── sent/
 */
public class ContactFolderManager {

    private static final String TAG = "ContactFolderManager";
    private static final String CONTACTS_DIR = "contacts";
    private static final String PROFILE_FILE = "profile.json";
    private static final String CHAT_DIR = "chat";
    private static final String RELAY_DIR = "relay";
    private static final String INBOX_DIR = "inbox";
    private static final String OUTBOX_DIR = "outbox";
    private static final String SENT_DIR = "sent";

    private final Context context;
    private final File baseDir;

    public ContactFolderManager(Context context) {
        this.context = context;
        this.baseDir = new File(context.getFilesDir(), CONTACTS_DIR);

        // Ensure base directory exists
        if (!baseDir.exists()) {
            if (!baseDir.mkdirs()) {
                Log.e(TAG, "Failed to create contacts directory: " + baseDir);
            }
        }
    }

    // --- Callsign Normalization ---

    /**
     * Normalize callsign to uppercase and trim whitespace.
     */
    public static String normalizeCallsign(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Validate callsign format.
     * Must be < 10 characters, start with 2 alphanumeric chars.
     */
    public static boolean isValidCallsign(String callsign) {
        if (callsign == null || callsign.isEmpty()) {
            return false;
        }

        String cs = normalizeCallsign(callsign);

        // Length check
        if (cs.length() >= 10) {
            return false;
        }

        // First two must be alphanumeric
        if (cs.length() < 2) {
            return false;
        }

        char c1 = cs.charAt(0);
        char c2 = cs.charAt(1);

        if (!Character.isLetterOrDigit(c1) || !Character.isLetterOrDigit(c2)) {
            return false;
        }

        // Remaining chars: alphanumeric or dash
        for (int i = 2; i < cs.length(); i++) {
            char c = cs.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '-') {
                return false;
            }
        }

        return true;
    }

    // --- Directory Access ---

    /**
     * Get the base contacts directory containing all contact folders.
     */
    public File getContactsDir() {
        return baseDir;
    }

    /**
     * Get contact's root directory.
     */
    public File getContactDir(String callsign) {
        String cs = normalizeCallsign(callsign);
        return new File(baseDir, cs);
    }

    /**
     * Get chat directory for contact.
     */
    public File getChatDir(String callsign) {
        return new File(getContactDir(callsign), CHAT_DIR);
    }

    /**
     * Get relay directory for contact.
     */
    public File getRelayDir(String callsign) {
        return new File(getContactDir(callsign), RELAY_DIR);
    }

    /**
     * Get relay inbox directory.
     */
    public File getRelayInboxDir(String callsign) {
        return new File(getRelayDir(callsign), INBOX_DIR);
    }

    /**
     * Get relay outbox directory.
     */
    public File getRelayOutboxDir(String callsign) {
        return new File(getRelayDir(callsign), OUTBOX_DIR);
    }

    /**
     * Get relay sent directory.
     */
    public File getRelaySentDir(String callsign) {
        return new File(getRelayDir(callsign), SENT_DIR);
    }

    /**
     * Ensure all directories exist for a contact.
     */
    public boolean ensureContactStructure(String callsign) {
        try {
            File contactDir = getContactDir(callsign);
            File chatDir = getChatDir(callsign);
            File relayDir = getRelayDir(callsign);
            File inboxDir = getRelayInboxDir(callsign);
            File outboxDir = getRelayOutboxDir(callsign);
            File sentDir = getRelaySentDir(callsign);

            return contactDir.mkdirs() | contactDir.exists() &&
                   chatDir.mkdirs() | chatDir.exists() &&
                   relayDir.mkdirs() | relayDir.exists() &&
                   inboxDir.mkdirs() | inboxDir.exists() &&
                   outboxDir.mkdirs() | outboxDir.exists() &&
                   sentDir.mkdirs() | sentDir.exists();
        } catch (Exception e) {
            Log.e(TAG, "Failed to create contact structure for " + callsign + ": " + e.getMessage());
            return false;
        }
    }

    // --- Profile Operations ---

    /**
     * Save contact profile to disk.
     */
    public boolean saveProfile(String callsign, ContactProfile profile) {
        if (profile == null) {
            return false;
        }

        ensureContactStructure(callsign);
        File profileFile = new File(getContactDir(callsign), PROFILE_FILE);

        try {
            String json = profile.toJson();
            Files.write(profileFile.toPath(), json.getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to save profile for " + callsign + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Load contact profile from disk.
     * Returns null if profile doesn't exist.
     */
    public ContactProfile loadProfile(String callsign) {
        File profileFile = new File(getContactDir(callsign), PROFILE_FILE);

        if (!profileFile.exists()) {
            return null;
        }

        try {
            String json = new String(Files.readAllBytes(profileFile.toPath()), StandardCharsets.UTF_8);
            return ContactProfile.fromJson(json);
        } catch (IOException e) {
            Log.e(TAG, "Failed to load profile for " + callsign + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Get or create contact profile.
     * If profile doesn't exist, creates a new one with basic info.
     */
    public ContactProfile getOrCreateProfile(String callsign) {
        ContactProfile profile = loadProfile(callsign);

        if (profile == null) {
            profile = new ContactProfile();
            profile.setCallsign(normalizeCallsign(callsign));
            profile.setFirstTimeSeen(System.currentTimeMillis());
            profile.setLastUpdated(System.currentTimeMillis());
            saveProfile(callsign, profile);
        }

        return profile;
    }

    /**
     * Delete contact and all associated data.
     */
    public boolean deleteContact(String callsign) {
        File contactDir = getContactDir(callsign);
        return deleteRecursive(contactDir);
    }

    private boolean deleteRecursive(File file) {
        if (!file.exists()) {
            return false;
        }

        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }

        return file.delete();
    }

    // --- Content Type Detection ---

    /**
     * Determine what type of content exists for a contact.
     */
    public ContactContentType getContentType(String callsign) {
        int chatCount = getChatMessageCount(callsign);
        int relayCount = getRelayMessageCount(callsign);

        if (chatCount > 0 && relayCount > 0) {
            return ContactContentType.BOTH;
        } else if (chatCount > 0) {
            return ContactContentType.CHAT_ONLY;
        } else if (relayCount > 0) {
            return ContactContentType.RELAY_ONLY;
        } else {
            return ContactContentType.NONE;
        }
    }

    /**
     * Get approximate count of chat messages for a contact.
     * Counts NDJSON files in chat directory.
     */
    public int getChatMessageCount(String callsign) {
        File chatDir = getChatDir(callsign);
        if (!chatDir.exists() || !chatDir.isDirectory()) {
            return 0;
        }

        int count = 0;
        File[] files = chatDir.listFiles((dir, name) -> name.endsWith(".ndjson"));

        if (files != null) {
            for (File file : files) {
                try {
                    // Count lines in each NDJSON file
                    long lines = Files.lines(file.toPath()).count();
                    count += lines;
                } catch (IOException e) {
                    Log.e(TAG, "Failed to count messages in " + file.getName() + ": " + e.getMessage());
                }
            }
        }

        return count;
    }

    /**
     * Get count of relay messages for a contact (inbox + outbox + sent).
     */
    public int getRelayMessageCount(String callsign) {
        int inboxCount = countFilesInDir(getRelayInboxDir(callsign));
        int outboxCount = countFilesInDir(getRelayOutboxDir(callsign));
        int sentCount = countFilesInDir(getRelaySentDir(callsign));

        return inboxCount + outboxCount + sentCount;
    }

    /**
     * Get count of relay inbox messages for a contact.
     */
    public int getRelayInboxCount(String callsign) {
        return countFilesInDir(getRelayInboxDir(callsign));
    }

    /**
     * Get count of relay outbox messages for a contact.
     */
    public int getRelayOutboxCount(String callsign) {
        return countFilesInDir(getRelayOutboxDir(callsign));
    }

    /**
     * Get count of relay sent messages for a contact.
     */
    public int getRelaySentCount(String callsign) {
        return countFilesInDir(getRelaySentDir(callsign));
    }

    private int countFilesInDir(File dir) {
        if (!dir.exists() || !dir.isDirectory()) {
            return 0;
        }

        File[] files = dir.listFiles(File::isFile);
        return files != null ? files.length : 0;
    }

    // --- List Operations ---

    /**
     * List all contacts (callsigns with directories).
     */
    public List<String> listContacts() {
        List<String> contacts = new ArrayList<>();

        if (!baseDir.exists() || !baseDir.isDirectory()) {
            return contacts;
        }

        File[] dirs = baseDir.listFiles(File::isDirectory);
        if (dirs != null) {
            for (File dir : dirs) {
                contacts.add(dir.getName());
            }
        }

        return contacts;
    }

    /**
     * Check if contact exists (has a directory).
     */
    public boolean contactExists(String callsign) {
        File contactDir = getContactDir(callsign);
        return contactDir.exists() && contactDir.isDirectory();
    }

    // --- Enums ---

    public enum ContactContentType {
        NONE,        // No messages
        CHAT_ONLY,   // Only chat messages
        RELAY_ONLY,  // Only relay messages
        BOTH         // Both chat and relay messages
    }
}

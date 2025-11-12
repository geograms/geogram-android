package offgrid.geogram.contacts;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class ContactFolderManagerTest {

    private Context context;
    private ContactFolderManager manager;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        manager = new ContactFolderManager(context);

        // Clean up any existing test data
        File baseDir = new File(context.getFilesDir(), "contacts");
        deleteRecursive(baseDir);
    }

    private void deleteRecursive(File file) {
        if (file.exists()) {
            if (file.isDirectory()) {
                File[] children = file.listFiles();
                if (children != null) {
                    for (File child : children) {
                        deleteRecursive(child);
                    }
                }
            }
            file.delete();
        }
    }

    @Test
    public void testNormalizeCallsign() {
        assertEquals("CR7BBQ", ContactFolderManager.normalizeCallsign("cr7bbq"));
        assertEquals("CR7BBQ", ContactFolderManager.normalizeCallsign("CR7BBQ"));
        assertEquals("CR7BBQ", ContactFolderManager.normalizeCallsign("  cr7bbq  "));
        assertEquals("", ContactFolderManager.normalizeCallsign(null));
    }

    @Test
    public void testIsValidCallsign() {
        // Valid callsigns
        assertTrue(ContactFolderManager.isValidCallsign("CR7BBQ"));
        assertTrue(ContactFolderManager.isValidCallsign("AB123"));
        assertTrue(ContactFolderManager.isValidCallsign("X2DEVS"));
        assertTrue(ContactFolderManager.isValidCallsign("K5-ABC"));

        // Invalid callsigns
        assertFalse(ContactFolderManager.isValidCallsign(null));
        assertFalse(ContactFolderManager.isValidCallsign(""));
        assertFalse(ContactFolderManager.isValidCallsign("A"));        // Too short
        assertFalse(ContactFolderManager.isValidCallsign("ABCDEFGHIJ")); // Too long (10+)
        assertFalse(ContactFolderManager.isValidCallsign("-ABC"));     // First char not alphanumeric
        assertFalse(ContactFolderManager.isValidCallsign("A-BC"));     // Second char not alphanumeric
        assertFalse(ContactFolderManager.isValidCallsign("AB.XYZ"));   // Invalid char (.)
        assertFalse(ContactFolderManager.isValidCallsign("AB XYZ"));   // Space not allowed
    }

    @Test
    public void testGetContactDir() {
        File dir = manager.getContactDir("CR7BBQ");
        assertNotNull(dir);
        assertTrue(dir.getPath().endsWith("contacts/CR7BBQ"));
    }

    @Test
    public void testGetChatDir() {
        File dir = manager.getChatDir("CR7BBQ");
        assertNotNull(dir);
        assertTrue(dir.getPath().endsWith("contacts/CR7BBQ/chat"));
    }

    @Test
    public void testGetRelayDir() {
        File dir = manager.getRelayDir("CR7BBQ");
        assertNotNull(dir);
        assertTrue(dir.getPath().endsWith("contacts/CR7BBQ/relay"));
    }

    @Test
    public void testGetRelayInboxDir() {
        File dir = manager.getRelayInboxDir("CR7BBQ");
        assertNotNull(dir);
        assertTrue(dir.getPath().endsWith("contacts/CR7BBQ/relay/inbox"));
    }

    @Test
    public void testGetRelayOutboxDir() {
        File dir = manager.getRelayOutboxDir("CR7BBQ");
        assertNotNull(dir);
        assertTrue(dir.getPath().endsWith("contacts/CR7BBQ/relay/outbox"));
    }

    @Test
    public void testGetRelaySentDir() {
        File dir = manager.getRelaySentDir("CR7BBQ");
        assertNotNull(dir);
        assertTrue(dir.getPath().endsWith("contacts/CR7BBQ/relay/sent"));
    }

    @Test
    public void testEnsureContactStructure() {
        String callsign = "CR7BBQ";

        boolean success = manager.ensureContactStructure(callsign);
        assertTrue(success);

        // Verify all directories were created
        assertTrue(manager.getContactDir(callsign).exists());
        assertTrue(manager.getChatDir(callsign).exists());
        assertTrue(manager.getRelayDir(callsign).exists());
        assertTrue(manager.getRelayInboxDir(callsign).exists());
        assertTrue(manager.getRelayOutboxDir(callsign).exists());
        assertTrue(manager.getRelaySentDir(callsign).exists());
    }

    @Test
    public void testSaveAndLoadProfile() {
        String callsign = "CR7BBQ";

        ContactProfile profile = new ContactProfile();
        profile.setCallsign(callsign);
        profile.setName("Test User");
        profile.setNpub("npub1test...");
        profile.setDescription("Test description");
        profile.setMessagesArchived(42);

        // Save profile
        boolean saved = manager.saveProfile(callsign, profile);
        assertTrue(saved);

        // Load profile
        ContactProfile loaded = manager.loadProfile(callsign);
        assertNotNull(loaded);
        assertEquals("CR7BBQ", loaded.getCallsign());
        assertEquals("Test User", loaded.getName());
        assertEquals("npub1test...", loaded.getNpub());
        assertEquals("Test description", loaded.getDescription());
        assertEquals(42, loaded.getMessagesArchived());
    }

    @Test
    public void testLoadProfile_NotExists() {
        ContactProfile profile = manager.loadProfile("NONEXISTENT");
        assertNull(profile);
    }

    @Test
    public void testSaveProfile_Null() {
        boolean saved = manager.saveProfile("CR7BBQ", null);
        assertFalse(saved);
    }

    @Test
    public void testGetOrCreateProfile_Exists() {
        String callsign = "CR7BBQ";

        // First create a profile
        ContactProfile original = new ContactProfile();
        original.setCallsign(callsign);
        original.setName("Original Name");
        manager.saveProfile(callsign, original);

        // GetOrCreate should return existing
        ContactProfile loaded = manager.getOrCreateProfile(callsign);
        assertNotNull(loaded);
        assertEquals("Original Name", loaded.getName());
    }

    @Test
    public void testGetOrCreateProfile_NotExists() {
        String callsign = "NEWCALL";

        // GetOrCreate should create new
        ContactProfile profile = manager.getOrCreateProfile(callsign);
        assertNotNull(profile);
        assertEquals("NEWCALL", profile.getCallsign());
        assertTrue(profile.getFirstTimeSeen() > 0);
        assertTrue(profile.getLastUpdated() > 0);

        // Verify it was saved
        ContactProfile loaded = manager.loadProfile(callsign);
        assertNotNull(loaded);
        assertEquals("NEWCALL", loaded.getCallsign());
    }

    @Test
    public void testDeleteContact() {
        String callsign = "CR7BBQ";

        // Create contact with data
        manager.ensureContactStructure(callsign);
        ContactProfile profile = new ContactProfile();
        profile.setCallsign(callsign);
        manager.saveProfile(callsign, profile);

        assertTrue(manager.contactExists(callsign));

        // Delete contact
        boolean deleted = manager.deleteContact(callsign);
        assertTrue(deleted);
        assertFalse(manager.contactExists(callsign));

        // Delete again should return false
        deleted = manager.deleteContact(callsign);
        assertFalse(deleted);
    }

    @Test
    public void testGetContentType_None() {
        String callsign = "CR7BBQ";
        manager.ensureContactStructure(callsign);

        ContactFolderManager.ContactContentType type = manager.getContentType(callsign);
        assertEquals(ContactFolderManager.ContactContentType.NONE, type);
    }

    @Test
    public void testGetContentType_ChatOnly() throws IOException {
        String callsign = "CR7BBQ";
        manager.ensureContactStructure(callsign);

        // Create a chat message file
        File chatFile = new File(manager.getChatDir(callsign), "2025-01.ndjson");
        Files.write(chatFile.toPath(), "message1\nmessage2\n".getBytes(StandardCharsets.UTF_8));

        ContactFolderManager.ContactContentType type = manager.getContentType(callsign);
        assertEquals(ContactFolderManager.ContactContentType.CHAT_ONLY, type);
    }

    @Test
    public void testGetContentType_RelayOnly() throws IOException {
        String callsign = "CR7BBQ";
        manager.ensureContactStructure(callsign);

        // Create a relay message file
        File relayFile = new File(manager.getRelayInboxDir(callsign), "msg001.md");
        Files.write(relayFile.toPath(), "Test message".getBytes(StandardCharsets.UTF_8));

        ContactFolderManager.ContactContentType type = manager.getContentType(callsign);
        assertEquals(ContactFolderManager.ContactContentType.RELAY_ONLY, type);
    }

    @Test
    public void testGetContentType_Both() throws IOException {
        String callsign = "CR7BBQ";
        manager.ensureContactStructure(callsign);

        // Create chat message
        File chatFile = new File(manager.getChatDir(callsign), "2025-01.ndjson");
        Files.write(chatFile.toPath(), "message1\n".getBytes(StandardCharsets.UTF_8));

        // Create relay message
        File relayFile = new File(manager.getRelayInboxDir(callsign), "msg001.md");
        Files.write(relayFile.toPath(), "Test message".getBytes(StandardCharsets.UTF_8));

        ContactFolderManager.ContactContentType type = manager.getContentType(callsign);
        assertEquals(ContactFolderManager.ContactContentType.BOTH, type);
    }

    @Test
    public void testGetChatMessageCount() throws IOException {
        String callsign = "CR7BBQ";
        manager.ensureContactStructure(callsign);

        // Initially 0
        assertEquals(0, manager.getChatMessageCount(callsign));

        // Create NDJSON files with messages
        File chatDir = manager.getChatDir(callsign);
        File file1 = new File(chatDir, "2025-01.ndjson");
        Files.write(file1.toPath(), "line1\nline2\nline3\n".getBytes(StandardCharsets.UTF_8));

        File file2 = new File(chatDir, "2025-02.ndjson");
        Files.write(file2.toPath(), "line4\nline5\n".getBytes(StandardCharsets.UTF_8));

        // Should count 5 total lines
        assertEquals(5, manager.getChatMessageCount(callsign));
    }

    @Test
    public void testGetRelayMessageCount() throws IOException {
        String callsign = "CR7BBQ";
        manager.ensureContactStructure(callsign);

        // Initially 0
        assertEquals(0, manager.getRelayMessageCount(callsign));
        assertEquals(0, manager.getRelayInboxCount(callsign));
        assertEquals(0, manager.getRelayOutboxCount(callsign));
        assertEquals(0, manager.getRelaySentCount(callsign));

        // Create relay messages
        File inbox = manager.getRelayInboxDir(callsign);
        File outbox = manager.getRelayOutboxDir(callsign);
        File sent = manager.getRelaySentDir(callsign);

        Files.write(new File(inbox, "msg001.md").toPath(), "test".getBytes());
        Files.write(new File(inbox, "msg002.md").toPath(), "test".getBytes());
        Files.write(new File(outbox, "msg003.md").toPath(), "test".getBytes());
        Files.write(new File(sent, "msg004.md").toPath(), "test".getBytes());

        // Check counts
        assertEquals(2, manager.getRelayInboxCount(callsign));
        assertEquals(1, manager.getRelayOutboxCount(callsign));
        assertEquals(1, manager.getRelaySentCount(callsign));
        assertEquals(4, manager.getRelayMessageCount(callsign));
    }

    @Test
    public void testListContacts() {
        // Initially empty
        List<String> contacts = manager.listContacts();
        assertNotNull(contacts);
        assertTrue(contacts.isEmpty());

        // Create some contacts
        manager.ensureContactStructure("CR7BBQ");
        manager.ensureContactStructure("X2DEVS");
        manager.ensureContactStructure("K5ABC");

        // List should contain all
        contacts = manager.listContacts();
        assertEquals(3, contacts.size());
        assertTrue(contacts.contains("CR7BBQ"));
        assertTrue(contacts.contains("X2DEVS"));
        assertTrue(contacts.contains("K5ABC"));
    }

    @Test
    public void testContactExists() {
        String callsign = "CR7BBQ";

        assertFalse(manager.contactExists(callsign));

        manager.ensureContactStructure(callsign);
        assertTrue(manager.contactExists(callsign));

        manager.deleteContact(callsign);
        assertFalse(manager.contactExists(callsign));
    }
}

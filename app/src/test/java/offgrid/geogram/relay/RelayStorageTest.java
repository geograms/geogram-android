package offgrid.geogram.relay;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.File;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for RelayStorage.
 */
@RunWith(RobolectricTestRunner.class)
public class RelayStorageTest {

    private RelayStorage storage;

    @Before
    public void setUp() {
        storage = new RelayStorage(RuntimeEnvironment.getApplication());

        // Clean up any existing test data
        storage.clearFolder("inbox");
        storage.clearFolder("outbox");
        storage.clearFolder("sent");
    }

    @Test
    public void testDirectoriesCreated() {
        assertNotNull("Relay directory should exist", storage.getRelayDir());
        assertTrue("Relay directory should be created", storage.getRelayDir().exists());
        assertTrue("Inbox directory should be created", storage.getInboxDir().exists());
        assertTrue("Outbox directory should be created", storage.getOutboxDir().exists());
        assertTrue("Sent directory should be created", storage.getSentDir().exists());
    }

    @Test
    public void testSaveMessage_ToInbox() {
        RelayMessage message = createTestMessage("msg001");

        boolean saved = storage.saveMessage(message, "inbox");

        assertTrue("Message should be saved successfully", saved);

        // Verify file was created
        File messageFile = new File(storage.getInboxDir(), "msg001.md");
        assertTrue("Message file should exist", messageFile.exists());
    }

    @Test
    public void testSaveMessage_ToOutbox() {
        RelayMessage message = createTestMessage("msg002");

        boolean saved = storage.saveMessage(message, "outbox");

        assertTrue("Message should be saved successfully", saved);

        File messageFile = new File(storage.getOutboxDir(), "msg002.md");
        assertTrue("Message file should exist", messageFile.exists());
    }

    @Test
    public void testSaveMessage_ToSent() {
        RelayMessage message = createTestMessage("msg003");

        boolean saved = storage.saveMessage(message, "sent");

        assertTrue("Message should be saved successfully", saved);

        File messageFile = new File(storage.getSentDir(), "msg003.md");
        assertTrue("Message file should exist", messageFile.exists());
    }

    @Test
    public void testSaveMessage_NullMessage() {
        boolean saved = storage.saveMessage(null, "inbox");

        assertFalse("Should not save null message", saved);
    }

    @Test
    public void testSaveMessage_NullId() {
        RelayMessage message = new RelayMessage();
        message.setId(null);
        message.setContent("Test");

        boolean saved = storage.saveMessage(message, "inbox");

        assertFalse("Should not save message with null ID", saved);
    }

    @Test
    public void testSaveMessage_InvalidFolder() {
        RelayMessage message = createTestMessage("msg004");

        boolean saved = storage.saveMessage(message, "invalid-folder");

        assertFalse("Should not save to invalid folder", saved);
    }

    @Test
    public void testGetMessage_FromInbox() {
        RelayMessage original = createTestMessage("msg005");
        storage.saveMessage(original, "inbox");

        RelayMessage loaded = storage.getMessage("msg005", "inbox");

        assertNotNull("Message should be loaded", loaded);
        assertEquals("msg005", loaded.getId());
        assertEquals(original.getContent(), loaded.getContent());
        assertEquals(original.getFromCallsign(), loaded.getFromCallsign());
        assertEquals(original.getToCallsign(), loaded.getToCallsign());
    }

    @Test
    public void testGetMessage_AnyFolder() {
        RelayMessage msg1 = createTestMessage("msg006");
        RelayMessage msg2 = createTestMessage("msg007");
        RelayMessage msg3 = createTestMessage("msg008");

        storage.saveMessage(msg1, "inbox");
        storage.saveMessage(msg2, "outbox");
        storage.saveMessage(msg3, "sent");

        // Should find message regardless of folder
        assertNotNull("Should find msg006", storage.getMessage("msg006"));
        assertNotNull("Should find msg007", storage.getMessage("msg007"));
        assertNotNull("Should find msg008", storage.getMessage("msg008"));
    }

    @Test
    public void testGetMessage_NotFound() {
        RelayMessage loaded = storage.getMessage("nonexistent", "inbox");

        assertNull("Should return null for non-existent message", loaded);
    }

    @Test
    public void testGetMessage_NullId() {
        RelayMessage loaded = storage.getMessage(null, "inbox");

        assertNull("Should return null for null ID", loaded);
    }

    @Test
    public void testListMessages() {
        storage.saveMessage(createTestMessage("msg009"), "inbox");
        storage.saveMessage(createTestMessage("msg010"), "inbox");
        storage.saveMessage(createTestMessage("msg011"), "inbox");

        List<String> messages = storage.listMessages("inbox");

        assertEquals("Should have 3 messages", 3, messages.size());
        assertTrue("Should contain msg009", messages.contains("msg009"));
        assertTrue("Should contain msg010", messages.contains("msg010"));
        assertTrue("Should contain msg011", messages.contains("msg011"));
    }

    @Test
    public void testListMessages_EmptyFolder() {
        List<String> messages = storage.listMessages("inbox");

        assertNotNull("Should return empty list", messages);
        assertEquals("Should have 0 messages", 0, messages.size());
    }

    @Test
    public void testListMessages_InvalidFolder() {
        List<String> messages = storage.listMessages("invalid");

        assertNotNull("Should return empty list", messages);
        assertEquals("Should have 0 messages", 0, messages.size());
    }

    @Test
    public void testListMessagesSorted_NewestFirst() throws InterruptedException {
        // Save messages with delays to ensure different timestamps
        storage.saveMessage(createTestMessage("msg012"), "inbox");
        Thread.sleep(10);
        storage.saveMessage(createTestMessage("msg013"), "inbox");
        Thread.sleep(10);
        storage.saveMessage(createTestMessage("msg014"), "inbox");

        List<String> messages = storage.listMessagesSorted("inbox", true);

        assertEquals("Should have 3 messages", 3, messages.size());
        assertEquals("Newest should be first", "msg014", messages.get(0));
        assertEquals("Oldest should be last", "msg012", messages.get(2));
    }

    @Test
    public void testListMessagesSorted_OldestFirst() throws InterruptedException {
        storage.saveMessage(createTestMessage("msg015"), "outbox");
        Thread.sleep(10);
        storage.saveMessage(createTestMessage("msg016"), "outbox");
        Thread.sleep(10);
        storage.saveMessage(createTestMessage("msg017"), "outbox");

        List<String> messages = storage.listMessagesSorted("outbox", false);

        assertEquals("Should have 3 messages", 3, messages.size());
        assertEquals("Oldest should be first", "msg015", messages.get(0));
        assertEquals("Newest should be last", "msg017", messages.get(2));
    }

    @Test
    public void testDeleteMessage() {
        storage.saveMessage(createTestMessage("msg018"), "inbox");

        boolean deleted = storage.deleteMessage("msg018", "inbox");

        assertTrue("Message should be deleted", deleted);
        assertNull("Message should not exist", storage.getMessage("msg018", "inbox"));
    }

    @Test
    public void testDeleteMessage_NotFound() {
        boolean deleted = storage.deleteMessage("nonexistent", "inbox");

        assertFalse("Should return false for non-existent message", deleted);
    }

    @Test
    public void testDeleteMessage_NullId() {
        boolean deleted = storage.deleteMessage(null, "inbox");

        assertFalse("Should return false for null ID", deleted);
    }

    @Test
    public void testMoveMessage() {
        storage.saveMessage(createTestMessage("msg019"), "inbox");

        boolean moved = storage.moveMessage("msg019", "inbox", "sent");

        assertTrue("Message should be moved", moved);
        assertNull("Message should not be in inbox", storage.getMessage("msg019", "inbox"));
        assertNotNull("Message should be in sent", storage.getMessage("msg019", "sent"));
    }

    @Test
    public void testMoveMessage_NotFound() {
        boolean moved = storage.moveMessage("nonexistent", "inbox", "sent");

        assertFalse("Should return false for non-existent message", moved);
    }

    @Test
    public void testMoveMessage_MultipleSteps() {
        storage.saveMessage(createTestMessage("msg020"), "outbox");

        // Move through all folders
        assertTrue("Should move to inbox", storage.moveMessage("msg020", "outbox", "inbox"));
        assertNotNull("Should be in inbox", storage.getMessage("msg020", "inbox"));

        assertTrue("Should move to sent", storage.moveMessage("msg020", "inbox", "sent"));
        assertNotNull("Should be in sent", storage.getMessage("msg020", "sent"));
        assertNull("Should not be in inbox", storage.getMessage("msg020", "inbox"));
    }

    @Test
    public void testGetMessageCount() {
        storage.saveMessage(createTestMessage("msg021"), "inbox");
        storage.saveMessage(createTestMessage("msg022"), "inbox");
        storage.saveMessage(createTestMessage("msg023"), "inbox");

        int count = storage.getMessageCount("inbox");

        assertEquals("Should have 3 messages", 3, count);
    }

    @Test
    public void testGetMessageCount_EmptyFolder() {
        int count = storage.getMessageCount("inbox");

        assertEquals("Should have 0 messages", 0, count);
    }

    @Test
    public void testGetTotalStorageUsed() {
        storage.saveMessage(createTestMessage("msg024"), "inbox");
        storage.saveMessage(createTestMessage("msg025"), "outbox");

        long totalSize = storage.getTotalStorageUsed();

        assertTrue("Total size should be > 0", totalSize > 0);
    }

    @Test
    public void testGetFolderStorageUsed() {
        storage.saveMessage(createTestMessage("msg026"), "inbox");
        storage.saveMessage(createTestMessage("msg027"), "inbox");

        long inboxSize = storage.getFolderStorageUsed("inbox");
        long outboxSize = storage.getFolderStorageUsed("outbox");

        assertTrue("Inbox size should be > 0", inboxSize > 0);
        assertEquals("Outbox size should be 0", 0, outboxSize);
    }

    @Test
    public void testDeleteExpiredMessages() {
        // Create expired message (use large time difference to avoid timestamp conversion issues)
        RelayMessage expired = createTestMessage("expired001");
        expired.setTimestamp((System.currentTimeMillis() / 1000) - 2592000); // 30 days ago
        expired.setTtl(86400); // 1 day TTL (expired 29 days ago)
        storage.saveMessage(expired, "inbox");

        // Create non-expired message
        RelayMessage valid = createTestMessage("valid001");
        valid.setTimestamp(System.currentTimeMillis() / 1000);
        valid.setTtl(604800); // 7 days TTL
        storage.saveMessage(valid, "inbox");

        int deleted = storage.deleteExpiredMessages();

        assertEquals("Should delete 1 expired message", 1, deleted);
        assertNull("Expired message should be deleted", storage.getMessage("expired001", "inbox"));
        assertNotNull("Valid message should remain", storage.getMessage("valid001", "inbox"));
    }

    @Test
    public void testDeleteExpiredMessages_MultipleMessages() {
        // Create 3 expired messages (use large time difference)
        for (int i = 1; i <= 3; i++) {
            RelayMessage expired = createTestMessage("expired00" + i);
            expired.setTimestamp((System.currentTimeMillis() / 1000) - 2592000); // 30 days ago
            expired.setTtl(86400); // 1 day TTL (expired 29 days ago)
            storage.saveMessage(expired, "inbox");
        }

        // Create 2 valid messages
        for (int i = 1; i <= 2; i++) {
            RelayMessage valid = createTestMessage("valid00" + i);
            valid.setTimestamp(System.currentTimeMillis() / 1000);
            valid.setTtl(604800); // 7 days TTL
            storage.saveMessage(valid, "inbox");
        }

        int deleted = storage.deleteExpiredMessages();

        assertEquals("Should delete 3 expired messages", 3, deleted);
        assertEquals("Should have 2 valid messages remaining", 2, storage.getMessageCount("inbox"));
    }

    @Test
    public void testPruneOldMessages() {
        // Create messages with different priorities and ages
        RelayMessage lowPriority = createTestMessage("low001");
        lowPriority.setPriority("low");
        lowPriority.setTimestamp((System.currentTimeMillis() / 1000) - 3600); // 1 hour ago
        storage.saveMessage(lowPriority, "inbox");

        RelayMessage normalPriority = createTestMessage("normal001");
        normalPriority.setPriority("normal");
        normalPriority.setTimestamp(System.currentTimeMillis() / 1000);
        storage.saveMessage(normalPriority, "inbox");

        // Calculate size of one message approximately
        long sizeBeforePrune = storage.getFolderStorageUsed("inbox");

        // Prune enough to remove at least one message
        long bytesFreed = storage.pruneOldMessages(100);

        assertTrue("Should free some bytes", bytesFreed > 0);
        assertTrue("Should delete low priority message first",
                storage.getMessage("low001", "inbox") == null);
        assertNotNull("Should keep normal priority message",
                storage.getMessage("normal001", "inbox"));
    }

    @Test
    public void testPruneOldMessages_ByPriority() {
        // Create messages with different priorities
        RelayMessage urgent = createTestMessage("urgent001");
        urgent.setPriority("urgent");
        urgent.setTimestamp((System.currentTimeMillis() / 1000) - 7200); // Oldest
        storage.saveMessage(urgent, "inbox");

        RelayMessage normal = createTestMessage("normal002");
        normal.setPriority("normal");
        normal.setTimestamp((System.currentTimeMillis() / 1000) - 3600);
        storage.saveMessage(normal, "inbox");

        RelayMessage low = createTestMessage("low002");
        low.setPriority("low");
        low.setTimestamp(System.currentTimeMillis() / 1000); // Newest
        storage.saveMessage(low, "inbox");

        // Prune to force deletion
        long totalSize = storage.getTotalStorageUsed();
        storage.pruneOldMessages(totalSize / 2);

        // Low priority should be deleted first, even though it's newest
        assertNull("Low priority should be deleted first", storage.getMessage("low002", "inbox"));

        // Urgent should remain, even though it's oldest
        assertNotNull("Urgent priority should be kept", storage.getMessage("urgent001", "inbox"));
    }

    @Test
    public void testClearFolder() {
        storage.saveMessage(createTestMessage("msg028"), "inbox");
        storage.saveMessage(createTestMessage("msg029"), "inbox");
        storage.saveMessage(createTestMessage("msg030"), "inbox");

        int cleared = storage.clearFolder("inbox");

        assertEquals("Should clear 3 messages", 3, cleared);
        assertEquals("Folder should be empty", 0, storage.getMessageCount("inbox"));
    }

    @Test
    public void testClearFolder_EmptyFolder() {
        int cleared = storage.clearFolder("inbox");

        assertEquals("Should clear 0 messages", 0, cleared);
    }

    @Test
    public void testRoundTrip_SaveAndLoad() {
        // Create message with all fields populated
        RelayMessage original = createTestMessage("roundtrip001");
        original.setFromCallsign("ALICE-K5XYZ");
        original.setToCallsign("BOB-W6ABC");
        original.setContent("Round trip test message");
        original.setType("private");
        original.setPriority("urgent");
        original.setTtl(86400);
        original.setFromNpub("npub1alice...");
        original.setToNpub("npub1bob...");
        original.setLocation("40.7128,-74.0060");
        original.addRelayNode("RELAY1-K5ABC");

        // Save and load
        storage.saveMessage(original, "inbox");
        RelayMessage loaded = storage.getMessage("roundtrip001", "inbox");

        // Verify all fields
        assertNotNull("Message should be loaded", loaded);
        assertEquals(original.getId(), loaded.getId());
        assertEquals(original.getFromCallsign(), loaded.getFromCallsign());
        assertEquals(original.getToCallsign(), loaded.getToCallsign());
        assertEquals(original.getContent(), loaded.getContent());
        assertEquals(original.getType(), loaded.getType());
        assertEquals(original.getPriority(), loaded.getPriority());
        assertEquals(original.getTtl(), loaded.getTtl());
        assertEquals(original.getFromNpub(), loaded.getFromNpub());
        assertEquals(original.getToNpub(), loaded.getToNpub());
        assertEquals(original.getLocation(), loaded.getLocation());
        assertEquals(original.getHopCount(), loaded.getHopCount());
    }

    @Test
    public void testRoundTrip_WithAttachments() {
        RelayMessage original = createTestMessage("attach001");

        // Add attachment
        RelayAttachment attachment = new RelayAttachment();
        attachment.setMimeType("image/jpeg");
        attachment.setFilename("photo.jpg");
        attachment.setData(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10});
        attachment.calculateChecksum();
        original.addAttachment(attachment);

        // Save and load
        storage.saveMessage(original, "inbox");
        RelayMessage loaded = storage.getMessage("attach001", "inbox");

        // Verify attachment
        assertNotNull("Message should be loaded", loaded);
        assertEquals("Should have 1 attachment", 1, loaded.getAttachments().size());

        RelayAttachment loadedAttachment = loaded.getAttachments().get(0);
        assertEquals(attachment.getMimeType(), loadedAttachment.getMimeType());
        assertEquals(attachment.getFilename(), loadedAttachment.getFilename());
        assertArrayEquals(attachment.getData(), loadedAttachment.getData());
        assertEquals(attachment.getChecksum(), loadedAttachment.getChecksum());
    }

    @Test
    public void testMultipleFolders_Independence() {
        // Save same ID to different folders (not realistic but tests independence)
        RelayMessage msg1 = createTestMessage("test001");
        msg1.setContent("Content for inbox");

        RelayMessage msg2 = createTestMessage("test001");
        msg2.setContent("Content for outbox");

        storage.saveMessage(msg1, "inbox");
        storage.saveMessage(msg2, "outbox");

        RelayMessage loaded1 = storage.getMessage("test001", "inbox");
        RelayMessage loaded2 = storage.getMessage("test001", "outbox");

        assertNotNull("Inbox message should exist", loaded1);
        assertNotNull("Outbox message should exist", loaded2);
        assertEquals("Content for inbox", loaded1.getContent());
        assertEquals("Content for outbox", loaded2.getContent());
    }

    // Helper method to create test messages

    private RelayMessage createTestMessage(String id) {
        RelayMessage message = new RelayMessage();
        message.setId(id);
        message.setFromCallsign("TEST-K5XYZ");
        message.setToCallsign("DEST-W6ABC");
        message.setContent("Test message content for " + id);
        message.setType("private");
        message.setPriority("normal");
        message.setTtl(604800); // 7 days
        message.setTimestamp(System.currentTimeMillis() / 1000);
        return message;
    }
}

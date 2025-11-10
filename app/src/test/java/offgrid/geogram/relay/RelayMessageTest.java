package offgrid.geogram.relay;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import static org.junit.Assert.*;

/**
 * Unit tests for RelayMessage.
 */
@RunWith(RobolectricTestRunner.class)
public class RelayMessageTest {

    private RelaySettings mockSettings;

    @Before
    public void setUp() {
        mockSettings = new RelaySettings(RuntimeEnvironment.getApplication());
        mockSettings.setAcceptedMessageTypes("everything");
    }

    @Test
    public void testParseMarkdown_BasicMessage() {
        String markdown = "> 2025-11-10 14:30_00 -- ALICE-K5XYZ\n" +
                "Hello, this is a test message.\n\n" +
                "--> to: BOB-W6ABC\n" +
                "--> id: abc123def456\n" +
                "--> type: private\n" +
                "--> priority: normal\n" +
                "--> ttl: 604800\n";

        RelayMessage message = RelayMessage.parseMarkdown(markdown);

        assertNotNull("Message should not be null", message);
        assertEquals("ALICE-K5XYZ", message.getFromCallsign());
        assertEquals("BOB-W6ABC", message.getToCallsign());
        assertEquals("Hello, this is a test message.", message.getContent());
        assertEquals("private", message.getType());
        assertEquals("normal", message.getPriority());
        assertEquals(604800, message.getTtl());
        assertEquals("abc123def456", message.getId());
    }

    @Test
    public void testParseMarkdown_MultilineContent() {
        String markdown = "> 2025-11-10 14:30_00 -- ALICE-K5XYZ\n" +
                "Line 1 of content\n" +
                "Line 2 of content\n" +
                "Line 3 of content\n\n" +
                "--> to: BOB-W6ABC\n" +
                "--> id: test123\n" +
                "--> type: private\n";

        RelayMessage message = RelayMessage.parseMarkdown(markdown);

        assertNotNull(message);
        String expectedContent = "Line 1 of content\nLine 2 of content\nLine 3 of content";
        assertEquals(expectedContent, message.getContent());
    }

    @Test
    public void testParseMarkdown_WithNpubFields() {
        String markdown = "> 2025-11-10 14:30_00 -- ALICE-K5XYZ\n" +
                "Test content\n\n" +
                "--> to: BOB-W6ABC\n" +
                "--> id: test456\n" +
                "--> type: private\n" +
                "--> from-npub: npub1alice...\n" +
                "--> to-npub: npub1bob...\n";

        RelayMessage message = RelayMessage.parseMarkdown(markdown);

        assertNotNull(message);
        assertEquals("npub1alice...", message.getFromNpub());
        assertEquals("npub1bob...", message.getToNpub());
    }

    @Test
    public void testParseMarkdown_WithLocation() {
        String markdown = "> 2025-11-10 14:30_00 -- ALICE-K5XYZ\n" +
                "At the summit!\n\n" +
                "--> to: BOB-W6ABC\n" +
                "--> id: test789\n" +
                "--> type: private\n" +
                "--> location: 40.7128,-74.0060\n" +
                "--> hop-count: 3\n";

        RelayMessage message = RelayMessage.parseMarkdown(markdown);

        assertNotNull(message);
        assertEquals("40.7128,-74.0060", message.getLocation());
        assertEquals(3, message.getHopCount());
    }

    @Test
    public void testToMarkdown_BasicMessage() {
        RelayMessage message = new RelayMessage();
        message.setFromCallsign("ALICE-K5XYZ");
        message.setToCallsign("BOB-W6ABC");
        message.setContent("Test message content");
        message.setType("private");
        message.setPriority("normal");
        message.setTtl(604800);
        message.setId("test123");
        message.setTimestamp(1699603500); // Sample timestamp

        String markdown = message.toMarkdown();

        assertNotNull(markdown);
        assertTrue(markdown.contains("ALICE-K5XYZ"));
        assertTrue(markdown.contains("BOB-W6ABC"));
        assertTrue(markdown.contains("Test message content"));
        assertTrue(markdown.contains("--> to: BOB-W6ABC"));
        assertTrue(markdown.contains("--> id: test123"));
        assertTrue(markdown.contains("--> type: private"));
        assertTrue(markdown.contains("--> priority: normal"));
        assertTrue(markdown.contains("--> ttl: 604800"));
    }

    @Test
    public void testRoundTrip_ParseAndSerialize() {
        String original = "> 2025-11-10 14:30_00 -- ALICE-K5XYZ\n" +
                "Round trip test message.\n\n" +
                "--> to: BOB-W6ABC\n" +
                "--> id: roundtrip123\n" +
                "--> type: private\n" +
                "--> priority: urgent\n" +
                "--> ttl: 86400\n";

        RelayMessage message = RelayMessage.parseMarkdown(original);
        assertNotNull(message);

        String serialized = message.toMarkdown();
        assertNotNull(serialized);

        // Parse again
        RelayMessage message2 = RelayMessage.parseMarkdown(serialized);
        assertNotNull(message2);

        // Compare key fields
        assertEquals(message.getFromCallsign(), message2.getFromCallsign());
        assertEquals(message.getToCallsign(), message2.getToCallsign());
        assertEquals(message.getContent(), message2.getContent());
        assertEquals(message.getType(), message2.getType());
        assertEquals(message.getPriority(), message2.getPriority());
        assertEquals(message.getTtl(), message2.getTtl());
        assertEquals(message.getId(), message2.getId());
    }

    @Test
    public void testIsExpired_NotExpired() {
        RelayMessage message = new RelayMessage();
        message.setTimestamp(System.currentTimeMillis() / 1000); // Now
        message.setTtl(3600); // 1 hour from now

        assertFalse("Message should not be expired", message.isExpired());
    }

    @Test
    public void testIsExpired_Expired() {
        RelayMessage message = new RelayMessage();
        message.setTimestamp((System.currentTimeMillis() / 1000) - 7200); // 2 hours ago
        message.setTtl(3600); // 1 hour TTL (so expired 1 hour ago)

        assertTrue("Message should be expired", message.isExpired());
    }

    @Test
    public void testShouldAccept_TextOnly_NoAttachments() {
        mockSettings.setAcceptedMessageTypes("text_only");

        RelayMessage message = new RelayMessage();
        message.setTimestamp(System.currentTimeMillis() / 1000);
        message.setTtl(3600);
        message.setContent("Plain text message");

        assertTrue("Should accept text-only message", message.shouldAccept(mockSettings));
    }

    @Test
    public void testShouldAccept_TextOnly_WithAttachments() {
        mockSettings.setAcceptedMessageTypes("text_only");

        RelayMessage message = new RelayMessage();
        message.setTimestamp(System.currentTimeMillis() / 1000);
        message.setTtl(3600);
        message.setContent("Message with attachment");

        RelayAttachment attachment = new RelayAttachment();
        attachment.setMimeType("image/jpeg");
        attachment.setFilename("photo.jpg");
        attachment.setData(new byte[1000]);
        message.addAttachment(attachment);

        assertFalse("Should reject message with attachments in text_only mode",
                message.shouldAccept(mockSettings));
    }

    @Test
    public void testShouldAccept_TextAndImages_WithImageAttachment() {
        mockSettings.setAcceptedMessageTypes("text_and_images");

        RelayMessage message = new RelayMessage();
        message.setTimestamp(System.currentTimeMillis() / 1000);
        message.setTtl(3600);
        message.setContent("Message with image");

        RelayAttachment attachment = new RelayAttachment();
        attachment.setMimeType("image/jpeg");
        attachment.setFilename("photo.jpg");
        attachment.setData(new byte[1000]);
        message.addAttachment(attachment);

        assertTrue("Should accept message with image attachment",
                message.shouldAccept(mockSettings));
    }

    @Test
    public void testShouldAccept_TextAndImages_WithNonImageAttachment() {
        mockSettings.setAcceptedMessageTypes("text_and_images");

        RelayMessage message = new RelayMessage();
        message.setTimestamp(System.currentTimeMillis() / 1000);
        message.setTtl(3600);
        message.setContent("Message with video");

        RelayAttachment attachment = new RelayAttachment();
        attachment.setMimeType("video/mp4");
        attachment.setFilename("video.mp4");
        attachment.setData(new byte[1000]);
        message.addAttachment(attachment);

        assertFalse("Should reject message with non-image attachment in text_and_images mode",
                message.shouldAccept(mockSettings));
    }

    @Test
    public void testShouldAccept_Everything_WithAnyAttachment() {
        mockSettings.setAcceptedMessageTypes("everything");

        RelayMessage message = new RelayMessage();
        message.setTimestamp(System.currentTimeMillis() / 1000);
        message.setTtl(3600);
        message.setContent("Message with video");

        RelayAttachment attachment = new RelayAttachment();
        attachment.setMimeType("video/mp4");
        attachment.setFilename("video.mp4");
        attachment.setData(new byte[1000]);
        message.addAttachment(attachment);

        assertTrue("Should accept message with any attachment in everything mode",
                message.shouldAccept(mockSettings));
    }

    @Test
    public void testShouldAccept_ExpiredMessage() {
        mockSettings.setAcceptedMessageTypes("everything");

        RelayMessage message = new RelayMessage();
        message.setTimestamp((System.currentTimeMillis() / 1000) - 7200); // 2 hours ago
        message.setTtl(3600); // 1 hour TTL (expired)
        message.setContent("Expired message");

        assertFalse("Should reject expired message", message.shouldAccept(mockSettings));
    }

    @Test
    public void testGetTotalSize_NoAttachments() {
        RelayMessage message = new RelayMessage();
        message.setContent("Short message");

        assertEquals("Short message".length(), message.getTotalSize());
    }

    @Test
    public void testGetTotalSize_WithAttachments() {
        RelayMessage message = new RelayMessage();
        message.setContent("Message with attachments");

        RelayAttachment att1 = new RelayAttachment();
        att1.setData(new byte[1000]);
        message.addAttachment(att1);

        RelayAttachment att2 = new RelayAttachment();
        att2.setData(new byte[2000]);
        message.addAttachment(att2);

        long expectedSize = "Message with attachments".length() + 1000 + 2000;
        assertEquals(expectedSize, message.getTotalSize());
    }

    @Test
    public void testAddRelayNode() {
        RelayMessage message = new RelayMessage();

        assertEquals(0, message.getHopCount());

        message.addRelayNode("RELAY1-K5ABC");
        assertEquals(1, message.getHopCount());
        assertEquals(1, message.getRelayPath().size());
        assertEquals("RELAY1-K5ABC", message.getRelayPath().get(0));

        message.addRelayNode("RELAY2-W6DEF");
        assertEquals(2, message.getHopCount());
        assertEquals(2, message.getRelayPath().size());
    }

    @Test
    public void testCustomFields() {
        RelayMessage message = new RelayMessage();

        message.setCustomField("custom-field-1", "value1");
        message.setCustomField("custom-field-2", "value2");

        assertEquals("value1", message.getCustomField("custom-field-1"));
        assertEquals("value2", message.getCustomField("custom-field-2"));
        assertNull(message.getCustomField("non-existent"));
    }

    @Test
    public void testParseMarkdown_InvalidHeader() {
        String markdown = "Invalid header format\n" +
                "Content here\n\n" +
                "--> to: BOB\n";

        RelayMessage message = RelayMessage.parseMarkdown(markdown);

        assertNull("Should return null for invalid header", message);
    }

    @Test
    public void testParseMarkdown_EmptyContent() {
        String markdown = "";

        RelayMessage message = RelayMessage.parseMarkdown(markdown);

        assertNull("Should return null for empty content", message);
    }

    @Test
    public void testParseMarkdown_NullContent() {
        RelayMessage message = RelayMessage.parseMarkdown(null);

        assertNull("Should return null for null content", message);
    }
}

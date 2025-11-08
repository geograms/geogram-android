package offgrid.geogram.api;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import offgrid.geogram.apps.messages.Conversation;
import offgrid.geogram.apps.messages.ConversationMessage;
import offgrid.geogram.apps.messages.MarkdownParser;

import static org.junit.Assert.*;

/**
 * Unit tests for GeogramMessagesAPI
 *
 * These tests verify:
 * 1. Conversation list retrieval
 * 2. Message retrieval for specific conversations (e.g., X2DEVS)
 * 3. Markdown parsing of conversation messages
 *
 * NOTE: These tests require:
 * - Internet connection
 * - Valid test credentials (nsec/npub)
 * - Server running at api.geogram.radio
 */
public class GeogramMessagesAPITest {

    // Test credentials - replace with actual test credentials
    private static final String TEST_CALLSIGN = "X10AL3";
    private static final String TEST_NSEC = "nsec1vl029mgpspedva04g90vltkh6fvh240zqtv9k0t9af8935ke9laqsnlfe5";
    private static final String TEST_NPUB = "npub1z8ft9jkd65vz88qfp966k8rk4x7gqpdjwrwlj4v35d8s3e74r79qn04wzp";

    @Before
    public void setUp() {
        // Setup runs before each test
    }

    /**
     * Test conversation list retrieval
     * This should return a list of conversation IDs including X2DEVS
     */
    @Test
    public void testGetConversationList() throws IOException, JSONException {
        System.out.println("\n=== Testing Conversation List Retrieval ===");

        List<String> conversations = GeogramMessagesAPI.getConversationList(
                TEST_CALLSIGN,
                TEST_NSEC,
                TEST_NPUB
        );

        // Verify response
        assertNotNull("Conversation list should not be null", conversations);

        // Log results
        System.out.println("Found " + conversations.size() + " conversations:");
        for (String peerId : conversations) {
            System.out.println("  - " + peerId);
        }

        // Verify X2DEVS is in the list (common group for developers)
        boolean hasX2DEVS = conversations.stream().anyMatch(p -> p.equals("X2DEVS"));
        System.out.println("\nX2DEVS group present: " + hasX2DEVS);

        assertTrue("Conversation list should contain X2DEVS", hasX2DEVS);
    }

    /**
     * Test retrieving messages from X2DEVS group
     * This is the main test to verify conversation parsing
     */
    @Test
    public void testGetX2DEVSMessages() throws IOException, JSONException {
        System.out.println("\n=== Testing X2DEVS Conversation Messages ===");

        String peerId = "X2DEVS";

        // Get raw markdown from API
        String markdown = GeogramMessagesAPI.getConversationMessages(
                TEST_CALLSIGN,
                peerId,
                TEST_NSEC,
                TEST_NPUB
        );

        // Verify we got content
        assertNotNull("Markdown content should not be null", markdown);
        assertFalse("Markdown content should not be empty", markdown.isEmpty());

        System.out.println("\n--- Raw Markdown Response ---");
        System.out.println(markdown);
        System.out.println("--- End Raw Markdown (length: " + markdown.length() + " chars) ---\n");

        // Parse the markdown into messages
        List<ConversationMessage> messages = MarkdownParser.parseConversation(markdown, TEST_CALLSIGN);

        // Verify parsing
        assertNotNull("Parsed messages should not be null", messages);

        System.out.println("Parsed " + messages.size() + " messages from X2DEVS:");
        System.out.println();

        // Display each parsed message
        for (int i = 0; i < messages.size(); i++) {
            ConversationMessage msg = messages.get(i);
            System.out.println("Message " + (i + 1) + ":");
            System.out.println("  Author: " + msg.getAuthor());
            System.out.println("  From Self: " + msg.isFromSelf());
            System.out.println("  Content: " + msg.getContent());
            System.out.println("  Metadata: " + msg.getMeta());
            System.out.println();
        }

        // Verify we got at least some messages (X2DEVS should have activity)
        assertTrue("X2DEVS should have at least 1 message", messages.size() > 0);

        // Verify message structure
        for (ConversationMessage msg : messages) {
            assertNotNull("Message author should not be null", msg.getAuthor());
            assertFalse("Message author should not be empty", msg.getAuthor().isEmpty());
            assertFalse("Message author should not be 'Unknown'", msg.getAuthor().equals("Unknown"));
            assertNotNull("Message content should not be null", msg.getContent());
        }

        System.out.println("✓ All " + messages.size() + " messages have valid author and content");
    }

    /**
     * Test markdown parsing with a known format
     * This tests the parser logic independently
     */
    @Test
    public void testMarkdownParserWithKnownFormat() {
        System.out.println("\n=== Testing Markdown Parser with Known Format ===");

        // Create test markdown in expected format
        String testMarkdown = ">-- X1ABCD\n" +
                "Hello from user 1\n" +
                "This is a multi-line message\n" +
                "\n" +
                ">-- X1EFGH\n" +
                "Reply from user 2\n" +
                "\n" +
                ">-- X1ABCD\n" +
                "Another message from user 1\n";

        System.out.println("Test markdown:");
        System.out.println(testMarkdown);

        // Parse it
        List<ConversationMessage> messages = MarkdownParser.parseConversation(testMarkdown, "X1ABCD");

        // Verify parsing
        assertEquals("Should parse 3 messages", 3, messages.size());

        // Verify first message
        ConversationMessage msg1 = messages.get(0);
        assertEquals("First message author should be X1ABCD", "X1ABCD", msg1.getAuthor());
        assertTrue("First message should be from self", msg1.isFromSelf());
        assertEquals("First message content", "Hello from user 1\nThis is a multi-line message", msg1.getContent());

        // Verify second message
        ConversationMessage msg2 = messages.get(1);
        assertEquals("Second message author should be X1EFGH", "X1EFGH", msg2.getAuthor());
        assertFalse("Second message should not be from self", msg2.isFromSelf());
        assertEquals("Second message content", "Reply from user 2", msg2.getContent());

        // Verify third message
        ConversationMessage msg3 = messages.get(2);
        assertEquals("Third message author should be X1ABCD", "X1ABCD", msg3.getAuthor());
        assertTrue("Third message should be from self", msg3.isFromSelf());
        assertEquals("Third message content", "Another message from user 1", msg3.getContent());

        System.out.println("✓ Markdown parser correctly parsed all messages");
    }

    /**
     * Test message formatting
     * Verify that formatMessage creates correct markdown
     */
    @Test
    public void testMessageFormatting() {
        System.out.println("\n=== Testing Message Formatting ===");

        String author = "X1TEST";
        String content = "This is a test message\nWith multiple lines";

        String formatted = MarkdownParser.formatMessage(author, content);

        System.out.println("Formatted message:");
        System.out.println(formatted);

        // Verify format
        assertTrue("Should start with >--", formatted.startsWith(">-- "));
        assertTrue("Should contain author", formatted.contains(author));
        assertTrue("Should contain content", formatted.contains(content));
        assertTrue("Should end with double newline", formatted.endsWith("\n\n"));

        String expected = ">-- X1TEST\nThis is a test message\nWith multiple lines\n\n";
        assertEquals("Format should match expected", expected, formatted);

        System.out.println("✓ Message formatting is correct");
    }

    /**
     * Test parsing edge cases
     */
    @Test
    public void testParsingEdgeCases() {
        System.out.println("\n=== Testing Parsing Edge Cases ===");

        // Test empty markdown
        List<ConversationMessage> empty = MarkdownParser.parseConversation("", "X1TEST");
        assertEquals("Empty markdown should return empty list", 0, empty.size());

        // Test null markdown
        List<ConversationMessage> nullMd = MarkdownParser.parseConversation(null, "X1TEST");
        assertEquals("Null markdown should return empty list", 0, nullMd.size());

        // Test markdown with only metadata, no content
        String metadataOnly = ">-- X1TEST\n\n";
        List<ConversationMessage> metaOnly = MarkdownParser.parseConversation(metadataOnly, "X1TEST");
        assertEquals("Metadata-only should parse 1 message", 1, metaOnly.size());
        assertEquals("Content should be empty", "", metaOnly.get(0).getContent());

        // Test message with special characters
        String specialChars = ">-- X1TEST\nMessage with special chars: !@#$%^&*(){}[]<>?\n\n";
        List<ConversationMessage> special = MarkdownParser.parseConversation(specialChars, "X1TEST");
        assertEquals("Should parse message with special chars", 1, special.size());
        assertTrue("Content should contain special chars",
                special.get(0).getContent().contains("!@#$%^&*(){}[]<>?"));

        System.out.println("✓ All edge cases handled correctly");
    }

    /**
     * Integration test - send and receive a message
     *
     * CAUTION: This writes to the actual server
     * Uncomment @Test to enable
     */
    // @Test
    public void testSendMessageToX2DEVS() throws IOException, JSONException {
        System.out.println("\n=== Testing Send Message to X2DEVS ===");

        String peerId = "X2DEVS";
        String testMessage = "Test message from Android unit test at " + System.currentTimeMillis();

        // Format the message
        String formatted = MarkdownParser.formatMessage(TEST_CALLSIGN, testMessage);

        System.out.println("Sending message to " + peerId + ":");
        System.out.println(formatted);

        // Send the message
        boolean success = GeogramMessagesAPI.sendMessage(
                TEST_CALLSIGN,
                peerId,
                formatted,
                TEST_NSEC,
                TEST_NPUB
        );

        assertTrue("Send operation should succeed", success);
        System.out.println("✓ Message sent successfully");

        // Wait a moment for server to process
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Try to read it back
        String markdown = GeogramMessagesAPI.getConversationMessages(
                TEST_CALLSIGN,
                peerId,
                TEST_NSEC,
                TEST_NPUB
        );

        assertTrue("Response should contain our message", markdown.contains(testMessage));
        System.out.println("✓ Message successfully retrieved from server");
    }
}

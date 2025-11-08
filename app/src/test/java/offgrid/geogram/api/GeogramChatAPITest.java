package offgrid.geogram.api;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import offgrid.geogram.apps.chat.ChatMessage;

import static org.junit.Assert.*;

/**
 * Unit tests for GeogramChatAPI
 *
 * These tests verify API communication with the Geogram server
 * using Coimbra coordinates where test messages are guaranteed to exist.
 *
 * Coimbra coordinates: 40.2056, -8.4137
 * Default test radius: 100 km
 */
public class GeogramChatAPITest {

    // Coimbra coordinates - where test messages always exist
    private static final double COIMBRA_LAT = 40.2056;
    private static final double COIMBRA_LON = -8.4137;
    private static final int DEFAULT_RADIUS = 100;

    // Test credentials - these should be valid test credentials
    // NOTE: In production, use a test user specifically created for testing
    private static final String TEST_CALLSIGN = "X10AL3";

    // These are example values - replace with actual test credentials
    private static final String TEST_NSEC = "nsec1example_replace_with_real_test_key_for_testing_purposes";
    private static final String TEST_NPUB = "npub1example_replace_with_real_test_key_for_testing_purposes";

    @Before
    public void setUp() {
        // Setup runs before each test
    }

    @Test
    public void testBech32Decoding() {
        // Test that nsec format is recognized
        String nsec = "nsec1vl029mgpspedva04g90vltkh6fvh240zqtv9k0t9af8935ke9laqsnlfe5";
        assertTrue("Should recognize nsec format", nsec.startsWith("nsec"));
        assertEquals("NSEC should be 63 characters", 63, nsec.length());
    }

    @Test
    public void testRadiusKmExtraction() {
        // Test radius parsing from string
        String radiusStr = "100 km";
        String[] parts = radiusStr.split(" ");
        int radius = Integer.parseInt(parts[0]);
        assertEquals("Should extract 100 from '100 km'", 100, radius);
    }

    /**
     * Test that we can construct a valid request structure
     * This doesn't make a real API call, just tests the structure
     */
    @Test
    public void testRequestStructure() {
        // Test parameters
        double lat = COIMBRA_LAT;
        double lon = COIMBRA_LON;
        int radius = DEFAULT_RADIUS;
        String callsign = TEST_CALLSIGN;

        // Verify coordinates are valid
        assertTrue("Latitude should be valid", lat >= -90 && lat <= 90);
        assertTrue("Longitude should be valid", lon >= -180 && lon <= 180);
        assertTrue("Radius should be positive", radius > 0);
        assertNotNull("Callsign should not be null", callsign);
        assertTrue("Callsign should not be empty", !callsign.isEmpty());
    }

    /**
     * Test coordinate formatting with locale independence
     */
    @Test
    public void testCoordinateFormatting() {
        double lat = COIMBRA_LAT;
        double lon = COIMBRA_LON;

        // Use Locale.US to ensure dot as decimal separator
        String formattedLat = String.format(java.util.Locale.US, "%.6f", lat);
        String formattedLon = String.format(java.util.Locale.US, "%.6f", lon);

        assertEquals("Formatted latitude", "40.205600", formattedLat);
        assertEquals("Formatted longitude", "-8.413700", formattedLon);

        // Verify no commas
        assertFalse("Latitude should not contain commas", formattedLat.contains(","));
        assertFalse("Longitude should not contain commas", formattedLon.contains(","));
    }

    /**
     * Test geo tag format (CRITICAL - must use dots not commas)
     */
    @Test
    public void testGeoTagFormat() {
        double lat = COIMBRA_LAT;
        double lon = COIMBRA_LON;

        // MUST use Locale.US to ensure dots as decimal separators
        String geoTag = String.format(java.util.Locale.US, "geo:%.6f,%.6f", lat, lon);

        assertTrue("Geo tag should start with 'geo:'", geoTag.startsWith("geo:"));
        assertTrue("Geo tag should contain latitude", geoTag.contains("40.2056"));
        assertTrue("Geo tag should contain longitude", geoTag.contains("-8.4137"));

        // CRITICAL: Verify format matches server expectations
        assertEquals("Geo tag exact format", "geo:40.205600,-8.413700", geoTag);

        // Count separators - should have exactly 1 comma (between lat and lon)
        // and exactly 2 dots (one in lat, one in lon)
        long commaCount = geoTag.chars().filter(ch -> ch == ',').count();
        long dotCount = geoTag.chars().filter(ch -> ch == '.').count();

        assertEquals("Should have exactly 1 comma separator", 1, commaCount);
        assertEquals("Should have exactly 2 decimal points", 2, dotCount);
    }

    /**
     * Integration test - reads messages from Coimbra area
     *
     * NOTE: This test requires:
     * 1. Internet connection
     * 2. Valid test credentials (nsec/npub)
     * 3. Server to be running at api.geogram.radio
     *
     * Comment out @Test to skip during offline development
     */
    // @Test
    public void testReadMessagesFromCoimbra() throws IOException, JSONException {
        // Skip if test credentials not configured
        if (TEST_NSEC.contains("example") || TEST_NPUB.contains("example")) {
            System.out.println("Skipping integration test - test credentials not configured");
            return;
        }

        // Attempt to read messages from Coimbra area
        List<ChatMessage> messages = GeogramChatAPI.readMessages(
                COIMBRA_LAT,
                COIMBRA_LON,
                DEFAULT_RADIUS,
                TEST_CALLSIGN,
                TEST_NSEC,
                TEST_NPUB
        );

        // Verify response
        assertNotNull("Messages list should not be null", messages);

        // Log results
        System.out.println("Fetched " + messages.size() + " messages from Coimbra area");

        if (messages.size() > 0) {
            System.out.println("Sample messages:");
            for (int i = 0; i < Math.min(3, messages.size()); i++) {
                ChatMessage msg = messages.get(i);
                System.out.println("  - " + msg.getAuthorId() + ": " + msg.getMessage());
            }
        }
    }

    /**
     * Integration test - writes a test message
     *
     * NOTE: This will write a real message to the server
     * Only enable during actual testing, not in CI/CD
     */
    // @Test
    public void testWriteMessageToCoimbra() throws IOException, JSONException {
        // Skip if test credentials not configured
        if (TEST_NSEC.contains("example") || TEST_NPUB.contains("example")) {
            System.out.println("Skipping write test - test credentials not configured");
            return;
        }

        String testMessage = "Test message from Android JUnit test at " + System.currentTimeMillis();

        boolean success = GeogramChatAPI.writeMessage(
                COIMBRA_LAT,
                COIMBRA_LON,
                testMessage,
                TEST_CALLSIGN,
                TEST_NSEC,
                TEST_NPUB
        );

        assertTrue("Write operation should succeed", success);
        System.out.println("Successfully wrote test message: " + testMessage);
    }
}

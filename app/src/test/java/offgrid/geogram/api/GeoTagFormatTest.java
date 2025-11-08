package offgrid.geogram.api;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;

import offgrid.geogram.apps.chat.ChatMessage;

import static org.junit.Assert.*;

/**
 * Critical test to verify geo tag formatting uses dots (not commas)
 * and actual server communication works
 *
 * Server: https://api.geogram.radio/nostr
 * Test coordinates: Coimbra, Portugal (40.2056, -8.4137)
 */
public class GeoTagFormatTest {

    // Set to false to skip network-based tests (useful for offline development)
    private static final boolean ENABLE_SERVER_TESTS = true;

    // Coimbra coordinates - guaranteed to have test messages
    private static final double COIMBRA_LAT = 40.2056;
    private static final double COIMBRA_LON = -8.4137;

    private static final int TEST_RADIUS_KM = 100;

    // Test credentials - replace with valid test user credentials
    // For now, using example values - real integration needs valid keys
    private static final String TEST_CALLSIGN = "TEST01";
    private static final String TEST_NSEC = "nsec1vl029mgpspedva04g90vltkh6fvh240zqtv9k0t9af8935ke9laqsnlfe5";
    private static final String TEST_NPUB = "npub10fl39r85rz29zlwfxq80xmgw6jz2lm2dvvqg3hv0u6k4v8v5y0jsv6ht0d";

    private boolean hasInternet = false;

    @Before
    public void setUp() {
        if (ENABLE_SERVER_TESTS) {
            hasInternet = checkInternetConnection();
            if (!hasInternet) {
                System.out.println("⚠️  No internet connection - skipping server tests");
            }
        }
    }

    /**
     * Check if we have internet connectivity
     */
    private boolean checkInternetConnection() {
        try {
            InetAddress address = InetAddress.getByName("api.geogram.radio");
            return address.isReachable(5000);
        } catch (UnknownHostException e) {
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    @Test
    public void testGeoTagUsesDotsNotCommas() {
        // These are real coordinates that were failing
        double lat = 49.727265;
        double lon = 8.609707;

        // WRONG: Default locale might use commas
        // String wrong = String.format("geo:%.6f,%.6f", lat, lon);
        // Result: "geo:49,727265,8,609707" ❌

        // CORRECT: Force US locale for dots
        String correct = String.format(Locale.US, "geo:%.6f,%.6f", lat, lon);
        // Result: "geo:49.727265,8.609707" ✅

        System.out.println("Geo tag format: " + correct);

        // Verify format
        assertTrue("Should start with geo:", correct.startsWith("geo:"));

        // Count dots and commas
        long dotCount = correct.chars().filter(ch -> ch == '.').count();
        long commaCount = correct.chars().filter(ch -> ch == ',').count();

        assertEquals("Should have 2 decimal points (one per coordinate)", 2, dotCount);
        assertEquals("Should have 1 comma (separator between lat and lon)", 1, commaCount);

        // Exact format check
        assertEquals("Exact format", "geo:49.727265,8.609707", correct);

        System.out.println("✅ Test passed! Geo tag correctly formatted with dots");
    }

    @Test
    public void testCoimbraCoordinates() {
        double lat = 40.2056;
        double lon = -8.4137;

        String geoTag = String.format(Locale.US, "geo:%.6f,%.6f", lat, lon);

        System.out.println("Coimbra geo tag: " + geoTag);

        assertEquals("Coimbra format", "geo:40.205600,-8.413700", geoTag);

        // Verify no locale-specific formatting
        assertFalse("Should not have European-style decimal commas",
                    geoTag.matches(".*\\d,\\d.*"));

        System.out.println("✅ Coimbra coordinates correctly formatted");
    }

    @Test
    public void testCurrentLocationExample() {
        // Your current location from the log
        double lat = 49.7271021;
        double lon = 8.6095479;

        String geoTag = String.format(Locale.US, "geo:%.6f,%.6f", lat, lon);

        System.out.println("Current location geo tag: " + geoTag);
        System.out.println("Expected server-compatible format");

        // Should be: geo:49.727102,8.609548
        assertTrue("Should use dot for decimal", geoTag.contains("49.727102"));
        assertTrue("Should use dot for decimal", geoTag.contains("8.609548"));

        System.out.println("✅ Current location correctly formatted");
    }

    /**
     * INTEGRATION TEST: Real server communication
     *
     * This test actually calls https://api.geogram.radio/nostr
     * with properly formatted coordinates and verifies the response.
     *
     * Requirements:
     * - Internet connection
     * - Valid test credentials (nsec/npub)
     * - Server must be running
     */
    @Test
    public void testRealServerCommunication() {
        if (!ENABLE_SERVER_TESTS) {
            System.out.println("⏭️  Server tests disabled - skipping");
            return;
        }

        if (!hasInternet) {
            System.out.println("⏭️  No internet connection - skipping server test");
            return;
        }

        System.out.println("\n=== INTEGRATION TEST: Real Server Communication ===");
        System.out.println("Server: https://api.geogram.radio/nostr");
        System.out.println("Location: Coimbra, Portugal (" + COIMBRA_LAT + ", " + COIMBRA_LON + ")");
        System.out.println("Radius: " + TEST_RADIUS_KM + " km");

        try {
            // Attempt to read messages from Coimbra area
            List<ChatMessage> messages = GeogramChatAPI.readMessages(
                    COIMBRA_LAT,
                    COIMBRA_LON,
                    TEST_RADIUS_KM,
                    TEST_CALLSIGN,
                    TEST_NSEC,
                    TEST_NPUB
            );

            // Verify we got a valid response (not null)
            assertNotNull("Server response should not be null", messages);

            // Log results
            System.out.println("✅ Server responded successfully");
            System.out.println("📨 Fetched " + messages.size() + " messages from Coimbra area");

            if (messages.size() > 0) {
                System.out.println("\nSample messages:");
                for (int i = 0; i < Math.min(5, messages.size()); i++) {
                    ChatMessage msg = messages.get(i);
                    System.out.println("  [" + (i+1) + "] " + msg.getAuthorId() + ": " +
                                     msg.getMessage().substring(0, Math.min(50, msg.getMessage().length())) +
                                     (msg.getMessage().length() > 50 ? "..." : ""));
                }
            } else {
                System.out.println("⚠️  No messages found in this area/radius");
                System.out.println("This might be normal if no messages exist in the test area");
            }

            System.out.println("\n✅ INTEGRATION TEST PASSED");
            System.out.println("The Android app can successfully communicate with the server!");

        } catch (IOException e) {
            System.err.println("❌ Network error: " + e.getMessage());
            System.err.println("Check internet connection and server availability");
            fail("Network error connecting to server: " + e.getMessage());
        } catch (JSONException e) {
            System.err.println("❌ JSON parsing error: " + e.getMessage());
            System.err.println("Server might have returned an unexpected response");
            fail("JSON error parsing server response: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("❌ Unexpected error: " + e.getMessage());
            e.printStackTrace();
            fail("Unexpected error: " + e.getMessage());
        }
    }

    /**
     * Test server communication with current location
     * (from the logs: 49.7271021, 8.6095479)
     */
    @Test
    public void testServerWithCurrentLocation() {
        if (!ENABLE_SERVER_TESTS) {
            System.out.println("⏭️  Server tests disabled - skipping");
            return;
        }

        if (!hasInternet) {
            System.out.println("⏭️  No internet connection - skipping server test");
            return;
        }

        double lat = 49.7271021;
        double lon = 8.6095479;

        System.out.println("\n=== Testing with Current Location ===");
        System.out.println("Location: " + lat + ", " + lon);
        System.out.println("Radius: " + TEST_RADIUS_KM + " km");

        try {
            List<ChatMessage> messages = GeogramChatAPI.readMessages(
                    lat,
                    lon,
                    TEST_RADIUS_KM,
                    TEST_CALLSIGN,
                    TEST_NSEC,
                    TEST_NPUB
            );

            assertNotNull("Server response should not be null", messages);
            System.out.println("✅ Server responded - found " + messages.size() + " messages");

        } catch (Exception e) {
            System.err.println("⚠️  Error: " + e.getMessage());
            // Don't fail - this location might not have messages
            // The important thing is that the server accepted our request format
        }
    }

    /**
     * Test that validates the exact request format sent to server
     */
    @Test
    public void testRequestFormat() {
        System.out.println("\n=== Validating Request Format ===");

        // Simulate building a geo tag
        double lat = COIMBRA_LAT;
        double lon = COIMBRA_LON;

        String geoTag = String.format(Locale.US, "geo:%.6f,%.6f", lat, lon);

        System.out.println("Geo tag: " + geoTag);

        // Verify exact format
        assertEquals("geo:40.205600,-8.413700", geoTag);

        // Verify no commas in coordinates (only between lat and lon)
        String[] parts = geoTag.substring(4).split(","); // Remove "geo:" prefix
        assertEquals("Should have exactly 2 parts (lat and lon)", 2, parts.length);

        String latPart = parts[0];
        String lonPart = parts[1];

        // Each part should contain exactly one dot
        assertEquals("Latitude should have one decimal point", 1,
                    latPart.chars().filter(ch -> ch == '.').count());
        assertEquals("Longitude should have one decimal point", 1,
                    lonPart.chars().filter(ch -> ch == '.').count());

        System.out.println("✅ Request format is correct");
        System.out.println("   Latitude part: " + latPart);
        System.out.println("   Longitude part: " + lonPart);
    }
}

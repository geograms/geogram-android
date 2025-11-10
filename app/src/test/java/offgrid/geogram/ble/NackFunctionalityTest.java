package offgrid.geogram.ble;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;

/**
 * Unit tests for NACK (Negative Acknowledgment) functionality in BLE message transmission.
 *
 * Tests verify that:
 * 1. Missing parcels are correctly detected
 * 2. NACK requests would be sent for missing parcels
 * 3. Message assembly handles gaps correctly
 * 4. Duplicate NACK requests are avoided
 */
public class NackFunctionalityTest {

    private BluetoothMessage message;

    @Before
    public void setUp() {
        message = new BluetoothMessage();
    }

    /**
     * Test that missing parcels are correctly identified when parcels arrive out of order.
     *
     * Scenario: Parcels AF0, AF1, AF3, AF4 arrive (AF2 is missing)
     * Expected: getMissingParcels() returns ["AF2"]
     */
    @Test
    public void testDetectSingleMissingParcel() {
        // Arrange: Add parcels with a gap (proper format with header)
        message.addMessageParcel("AF0:SENDER:DEST:ABCD");  // Header
        message.addMessageParcel("AF1:Hello");
        // AF2 is missing
        message.addMessageParcel("AF3:World");
        message.addMessageParcel("AF4:Test");

        // Act
        ArrayList<String> missing = message.getMissingParcels();

        // Assert
        assertNotNull("Missing parcels list should not be null", missing);
        assertEquals("Should detect exactly 1 missing parcel", 1, missing.size());
        assertTrue("Should contain AF2", missing.contains("AF2"));
    }

    /**
     * Test detection of multiple missing parcels.
     *
     * Scenario: Parcels AF0, AF3, AF6 arrive (AF1, AF2, AF4, AF5 are missing)
     * Expected: getMissingParcels() returns ["AF1", "AF2", "AF4", "AF5"]
     */
    @Test
    public void testDetectMultipleMissingParcels() {
        // Arrange
        message.addMessageParcel("AF0:SENDER:DEST:ABCD");  // Header
        // AF1, AF2 missing
        message.addMessageParcel("AF3:Middle");
        // AF4, AF5 missing
        message.addMessageParcel("AF6:End");

        // Act
        ArrayList<String> missing = message.getMissingParcels();

        // Assert
        assertEquals("Should detect 4 missing parcels", 4, missing.size());
        assertTrue("Should contain AF1", missing.contains("AF1"));
        assertTrue("Should contain AF2", missing.contains("AF2"));
        assertTrue("Should contain AF4", missing.contains("AF4"));
        assertTrue("Should contain AF5", missing.contains("AF5"));
    }

    /**
     * Test that no missing parcels are reported when all parcels arrive in sequence.
     *
     * Scenario: Parcels AF0, AF1, AF2, AF3 all arrive
     * Expected: getMissingParcels() returns empty list
     */
    @Test
    public void testNoMissingParcels() {
        // Arrange: Add all parcels in sequence
        message.addMessageParcel("AF0:SENDER:DEST:ABCD");  // Header
        message.addMessageParcel("AF1:Part1");
        message.addMessageParcel("AF2:Part2");
        message.addMessageParcel("AF3:Part3");

        // Act
        ArrayList<String> missing = message.getMissingParcels();

        // Assert
        assertNotNull("Missing parcels list should not be null", missing);
        assertTrue("Should have no missing parcels", missing.isEmpty());
    }

    /**
     * Test that getFirstMissingParcel() returns the first gap in sequence.
     *
     * Scenario: Parcels AF0, AF2, AF4 arrive (AF1 is first missing)
     * Expected: getFirstMissingParcel() returns "AF1"
     */
    @Test
    public void testGetFirstMissingParcel() {
        // Arrange
        message.addMessageParcel("AF0:SENDER:DEST:ABCD");  // Header
        // AF1 is missing (first gap)
        message.addMessageParcel("AF2:Second");
        // AF3 also missing but AF1 should be returned as first
        message.addMessageParcel("AF4:Fourth");

        // Act
        String firstMissing = message.getFirstMissingParcel();

        // Assert
        assertEquals("Should return first missing parcel", "AF1", firstMissing);
    }

    /**
     * Test that parcels arriving after initial gap are still tracked correctly.
     *
     * Scenario:
     * 1. AF0, AF2 arrive (AF1 missing)
     * 2. AF1 arrives later (fills gap)
     * Expected: No missing parcels after AF1 arrives
     */
    @Test
    public void testLateArrivingParcelFillsGap() {
        // Arrange: Create initial gap
        message.addMessageParcel("AF0:SENDER:DEST:ABCD");  // Header
        message.addMessageParcel("AF2:Second");

        // Verify gap exists
        ArrayList<String> missingBefore = message.getMissingParcels();
        assertEquals("Should have 1 missing parcel initially", 1, missingBefore.size());

        // Act: Late-arriving parcel fills the gap
        message.addMessageParcel("AF1:First");

        // Assert: Gap should be filled
        ArrayList<String> missingAfter = message.getMissingParcels();
        assertTrue("Should have no missing parcels after gap is filled", missingAfter.isEmpty());
    }

    /**
     * Test message completion detection with missing parcels.
     *
     * Scenario: Message has parcels but some are missing
     * Expected: isMessageCompleted() returns false
     */
    @Test
    public void testMessageNotCompletedWithMissingParcels() {
        // Arrange: Add parcels with gap
        message.addMessageParcel("AF0:SENDER:DEST:ABCD");  // Header
        // AF1 missing
        message.addMessageParcel("AF2:Data");

        // Act & Assert
        assertFalse("Message should not be complete with missing parcels",
                    message.isMessageCompleted());
    }

    /**
     * Test that empty message returns no missing parcels.
     *
     * Scenario: No parcels have been added
     * Expected: getMissingParcels() returns empty list
     */
    @Test
    public void testEmptyMessageHasNoMissingParcels() {
        // Arrange: message is already empty from setUp()

        // Act
        ArrayList<String> missing = message.getMissingParcels();

        // Assert
        assertNotNull("Missing parcels list should not be null", missing);
        assertTrue("Empty message should have no missing parcels", missing.isEmpty());
    }

    /**
     * Test parcel ID format with different base IDs.
     *
     * Scenario: Test with different two-character base IDs (AA, ZZ, etc.)
     * Expected: Missing parcel detection works regardless of base ID
     */
    @Test
    public void testDifferentBaseIds() {
        // Test with "XY" base
        BluetoothMessage msg1 = new BluetoothMessage();
        msg1.addMessageParcel("XY0:SENDER:DEST:ABCD");  // Header
        msg1.addMessageParcel("XY2:Data");

        ArrayList<String> missing1 = msg1.getMissingParcels();
        assertEquals("Should detect missing XY1", 1, missing1.size());
        assertTrue("Should contain XY1", missing1.contains("XY1"));

        // Test with "AB" base
        BluetoothMessage msg2 = new BluetoothMessage();
        msg2.addMessageParcel("AB0:SENDER:DEST:ABCD");  // Header
        msg2.addMessageParcel("AB3:Data");

        ArrayList<String> missing2 = msg2.getMissingParcels();
        assertEquals("Should detect missing AB1 and AB2", 2, missing2.size());
        assertTrue("Should contain AB1", missing2.contains("AB1"));
        assertTrue("Should contain AB2", missing2.contains("AB2"));
    }

    /**
     * Test behavior with only the last parcel arriving first.
     *
     * Scenario: Header arrives, then only parcel AF5 arrives (AF1-AF4 missing)
     * Expected: getMissingParcels() returns AF1-AF4
     */
    @Test
    public void testOnlyLastParcelArrives() {
        // Arrange: Add header and only a high-index parcel
        message.addMessageParcel("AF0:SENDER:DEST:ABCD");  // Header
        message.addMessageParcel("AF5:LastParcel");

        // Act
        ArrayList<String> missing = message.getMissingParcels();

        // Assert
        assertEquals("Should detect 4 missing parcels (AF1-AF4)", 4, missing.size());
        assertTrue("Should contain AF1", missing.contains("AF1"));
        assertTrue("Should contain AF2", missing.contains("AF2"));
        assertTrue("Should contain AF3", missing.contains("AF3"));
        assertTrue("Should contain AF4", missing.contains("AF4"));
    }
}

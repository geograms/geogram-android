package offgrid.geogram.relay;

import android.util.Base64;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.security.NoSuchAlgorithmException;

import offgrid.geogram.util.nostr.NostrUtil;

import static org.junit.Assert.*;

/**
 * Unit tests for RelayAttachment.
 */
@RunWith(RobolectricTestRunner.class)
public class RelayAttachmentTest {

    @Test
    public void testToMarkdown() {
        RelayAttachment attachment = new RelayAttachment();
        attachment.setMimeType("image/jpeg");
        attachment.setFilename("photo.jpg");
        attachment.setData(new byte[]{1, 2, 3, 4, 5});
        attachment.calculateChecksum();

        String markdown = attachment.toMarkdown(0);

        assertNotNull(markdown);
        assertTrue(markdown.contains("## ATTACHMENT: 0"));
        assertTrue(markdown.contains("- mime-type: image/jpeg"));
        assertTrue(markdown.contains("- filename: photo.jpg"));
        assertTrue(markdown.contains("- size: 5"));
        assertTrue(markdown.contains("- encoding: base64"));
        assertTrue(markdown.contains("- checksum: sha256:"));
        assertTrue(markdown.contains("# ATTACHMENT_DATA_START"));
        assertTrue(markdown.contains("# ATTACHMENT_DATA_END"));
    }

    @Test
    public void testParseFromMarkdown() {
        String[] lines = {
                "## ATTACHMENT: 0",
                "- mime-type: image/jpeg",
                "- filename: test.jpg",
                "- size: 10",
                "- encoding: base64",
                "- checksum: sha256:abc123",
                "",
                "# ATTACHMENT_DATA_START",
                "AQIDBAUGBwgJ",  // Base64 for bytes 1-9
                "# ATTACHMENT_DATA_END"
        };

        RelayAttachment attachment = RelayAttachment.parseFromMarkdown(lines, 0);

        assertNotNull(attachment);
        assertEquals("image/jpeg", attachment.getMimeType());
        assertEquals("test.jpg", attachment.getFilename());
        assertEquals(10, attachment.getSize());
        assertEquals("base64", attachment.getEncoding());
        assertEquals("sha256:abc123", attachment.getChecksum());
        assertNotNull(attachment.getData());
    }

    @Test
    public void testCalculateChecksum() throws NoSuchAlgorithmException {
        byte[] testData = {1, 2, 3, 4, 5};

        RelayAttachment attachment = new RelayAttachment();
        attachment.setData(testData);
        attachment.calculateChecksum();

        String checksum = attachment.getChecksum();
        assertNotNull(checksum);
        assertTrue(checksum.startsWith("sha256:"));

        // Calculate expected checksum
        byte[] hash = NostrUtil.sha256(testData);
        String expectedHex = NostrUtil.bytesToHex(hash);
        String expected = "sha256:" + expectedHex;

        assertEquals(expected, checksum);
    }

    @Test
    public void testVerifyChecksum_Valid() throws NoSuchAlgorithmException {
        byte[] testData = {1, 2, 3, 4, 5};

        RelayAttachment attachment = new RelayAttachment();
        attachment.setData(testData);
        attachment.calculateChecksum();

        assertTrue("Checksum should be valid", attachment.verifyChecksum());
    }

    @Test
    public void testVerifyChecksum_Invalid() {
        byte[] testData = {1, 2, 3, 4, 5};

        RelayAttachment attachment = new RelayAttachment();
        attachment.setData(testData);
        attachment.setChecksum("sha256:invalidhex");

        assertFalse("Checksum should be invalid", attachment.verifyChecksum());
    }

    @Test
    public void testVerifyChecksum_ModifiedData() throws NoSuchAlgorithmException {
        byte[] originalData = {1, 2, 3, 4, 5};

        RelayAttachment attachment = new RelayAttachment();
        attachment.setData(originalData);
        attachment.calculateChecksum();

        // Modify data after calculating checksum
        attachment.setData(new byte[]{1, 2, 3, 4, 6}); // Changed last byte

        assertFalse("Checksum should not match modified data",
                attachment.verifyChecksum());
    }

    @Test
    public void testGetBase64Data() {
        byte[] testData = {1, 2, 3, 4, 5};

        RelayAttachment attachment = new RelayAttachment();
        attachment.setData(testData);

        String base64 = attachment.getBase64Data();
        assertNotNull(base64);

        // Decode and verify
        byte[] decoded = Base64.decode(base64, Base64.NO_WRAP);
        assertArrayEquals(testData, decoded);
    }

    @Test
    public void testSetBase64Data() {
        byte[] testData = {1, 2, 3, 4, 5};
        String base64 = Base64.encodeToString(testData, Base64.NO_WRAP);

        RelayAttachment attachment = new RelayAttachment();
        attachment.setBase64Data(base64);

        assertArrayEquals(testData, attachment.getData());
        assertEquals(5, attachment.getSize());
    }

    @Test
    public void testRoundTrip_Base64() {
        byte[] originalData = new byte[100];
        for (int i = 0; i < 100; i++) {
            originalData[i] = (byte) i;
        }

        RelayAttachment attachment = new RelayAttachment();
        attachment.setData(originalData);

        // Convert to base64
        String base64 = attachment.getBase64Data();

        // Create new attachment and set from base64
        RelayAttachment attachment2 = new RelayAttachment();
        attachment2.setBase64Data(base64);

        // Compare
        assertArrayEquals(originalData, attachment2.getData());
    }

    @Test
    public void testRoundTrip_ParseAndSerialize() throws NoSuchAlgorithmException {
        byte[] testData = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};

        RelayAttachment original = new RelayAttachment();
        original.setMimeType("image/png");
        original.setFilename("test.png");
        original.setData(testData);
        original.calculateChecksum();

        // Serialize
        String markdown = original.toMarkdown(0);

        // Parse
        String[] lines = markdown.split("\n");
        RelayAttachment parsed = RelayAttachment.parseFromMarkdown(lines, 0);

        assertNotNull(parsed);
        assertEquals(original.getMimeType(), parsed.getMimeType());
        assertEquals(original.getFilename(), parsed.getFilename());
        assertEquals(original.getChecksum(), parsed.getChecksum());
        assertArrayEquals(original.getData(), parsed.getData());
    }

    @Test
    public void testSetData_UpdatesSize() {
        RelayAttachment attachment = new RelayAttachment();

        byte[] data1 = new byte[10];
        attachment.setData(data1);
        assertEquals(10, attachment.getSize());

        byte[] data2 = new byte[20];
        attachment.setData(data2);
        assertEquals(20, attachment.getSize());
    }

    @Test
    public void testSetData_Null() {
        RelayAttachment attachment = new RelayAttachment();
        attachment.setData(null);

        assertEquals(0, attachment.getSize());
        assertNull(attachment.getData());
    }

    @Test
    public void testVerifyChecksum_NullData() {
        RelayAttachment attachment = new RelayAttachment();
        attachment.setChecksum("sha256:abc123");

        assertFalse("Should return false for null data", attachment.verifyChecksum());
    }

    @Test
    public void testVerifyChecksum_NullChecksum() {
        RelayAttachment attachment = new RelayAttachment();
        attachment.setData(new byte[]{1, 2, 3});

        assertFalse("Should return false for null checksum", attachment.verifyChecksum());
    }

    @Test
    public void testLargeAttachment() throws NoSuchAlgorithmException {
        // Test with 1MB attachment
        byte[] largeData = new byte[1024 * 1024]; // 1 MB
        for (int i = 0; i < largeData.length; i++) {
            largeData[i] = (byte) (i % 256);
        }

        RelayAttachment attachment = new RelayAttachment();
        attachment.setMimeType("application/octet-stream");
        attachment.setFilename("large-file.bin");
        attachment.setData(largeData);
        attachment.calculateChecksum();

        // Verify checksum
        assertTrue("Checksum should be valid for large file",
                attachment.verifyChecksum());

        // Verify size
        assertEquals(1024 * 1024, attachment.getSize());

        // Serialize and verify it doesn't fail
        String markdown = attachment.toMarkdown(0);
        assertNotNull(markdown);
        assertTrue(markdown.length() > 0);
    }
}

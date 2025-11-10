package offgrid.geogram.relay;

import android.util.Base64;
import android.util.Log;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import offgrid.geogram.util.nostr.NostrUtil;

/**
 * Relay message attachment.
 *
 * Represents a file attachment in a relay message, using base64 encoding
 * for binary data.
 */
public class RelayAttachment {

    private static final String TAG = "RelayAttachment";

    private String mimeType;        // image/jpeg, audio/mp3, etc.
    private String filename;
    private long size;              // Bytes
    private String encoding;        // Always "base64"
    private String checksum;        // sha256:hex
    private byte[] data;            // Decoded binary data

    public RelayAttachment() {
        this.encoding = "base64";
    }

    /**
     * Parse attachment from markdown lines.
     *
     * @param lines All markdown lines
     * @param startIndex Index of "## ATTACHMENT:" line
     * @return Parsed attachment, or null if parse fails
     */
    public static RelayAttachment parseFromMarkdown(String[] lines, int startIndex) {
        try {
            RelayAttachment attachment = new RelayAttachment();

            // Skip "## ATTACHMENT:" line
            int index = startIndex + 1;

            // Parse metadata fields
            while (index < lines.length && lines[index].trim().startsWith("-")) {
                String line = lines[index].trim().substring(1).trim();
                int colonIndex = line.indexOf(':');
                if (colonIndex > 0) {
                    String key = line.substring(0, colonIndex).trim();
                    String value = line.substring(colonIndex + 1).trim();

                    switch (key) {
                        case "mime-type":
                            attachment.mimeType = value;
                            break;
                        case "filename":
                            attachment.filename = value;
                            break;
                        case "size":
                            attachment.size = Long.parseLong(value);
                            break;
                        case "encoding":
                            attachment.encoding = value;
                            break;
                        case "checksum":
                            attachment.checksum = value;
                            break;
                    }
                }
                index++;
            }

            // Find "# ATTACHMENT_DATA_START"
            while (index < lines.length && !lines[index].trim().equals("# ATTACHMENT_DATA_START")) {
                index++;
            }
            index++; // Move to first data line

            // Read base64 data until "# ATTACHMENT_DATA_END"
            StringBuilder base64Data = new StringBuilder();
            while (index < lines.length && !lines[index].trim().equals("# ATTACHMENT_DATA_END")) {
                base64Data.append(lines[index].trim());
                index++;
            }

            // Decode base64 data
            attachment.data = Base64.decode(base64Data.toString(), Base64.NO_WRAP);

            return attachment;

        } catch (Exception e) {
            Log.e(TAG, "Error parsing attachment: " + e.getMessage());
            return null;
        }
    }

    /**
     * Serialize attachment to markdown format.
     *
     * @param index Attachment index (0-based)
     * @return Markdown string
     */
    public String toMarkdown(int index) {
        StringBuilder sb = new StringBuilder();

        sb.append("## ATTACHMENT: ").append(index).append("\n");
        sb.append("- mime-type: ").append(mimeType).append("\n");
        sb.append("- filename: ").append(filename).append("\n");
        sb.append("- size: ").append(size).append("\n");
        sb.append("- encoding: ").append(encoding).append("\n");
        sb.append("- checksum: ").append(checksum).append("\n");
        sb.append("\n");
        sb.append("# ATTACHMENT_DATA_START\n");
        sb.append(getBase64Data()).append("\n");
        sb.append("# ATTACHMENT_DATA_END\n");

        return sb.toString();
    }

    /**
     * Verify checksum matches data.
     */
    public boolean verifyChecksum() {
        if (checksum == null || data == null) {
            return false;
        }

        try {
            // Extract hex from "sha256:hex" format
            String expectedHex = checksum;
            if (checksum.startsWith("sha256:")) {
                expectedHex = checksum.substring(7);
            }

            // Calculate SHA-256 hash of data
            byte[] hash = NostrUtil.sha256(data);
            String actualHex = NostrUtil.bytesToHex(hash);

            return expectedHex.equalsIgnoreCase(actualHex);

        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Error verifying checksum: " + e.getMessage());
            return false;
        }
    }

    /**
     * Calculate and set checksum from current data.
     */
    public void calculateChecksum() {
        if (data == null) {
            return;
        }

        try {
            byte[] hash = NostrUtil.sha256(data);
            String hex = NostrUtil.bytesToHex(hash);
            this.checksum = "sha256:" + hex;
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Error calculating checksum: " + e.getMessage());
        }
    }

    /**
     * Get data as base64 string for markdown serialization.
     */
    public String getBase64Data() {
        if (data == null) {
            return "";
        }
        return Base64.encodeToString(data, Base64.NO_WRAP);
    }

    /**
     * Set data from base64 string.
     */
    public void setBase64Data(String base64) {
        this.data = Base64.decode(base64, Base64.NO_WRAP);
        this.size = data.length;
    }

    // Getters and setters

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getEncoding() {
        return encoding;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
        this.size = data != null ? data.length : 0;
    }
}

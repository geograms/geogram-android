package offgrid.geogram.i2p;

import android.content.Context;
import android.util.Base64;
import offgrid.geogram.core.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * Manages I2P destination (cryptographic identity) for this device.
 *
 * An I2P destination consists of:
 * - Public key for encryption
 * - Signing public key for authentication
 * - Certificate data
 *
 * The destination can be represented as:
 * - Full destination: 516+ character Base64 string
 * - Base32 address: 52 characters ending in .b32.i2p
 */
public class I2PDestination {
    private static final String TAG = "I2PDestination";
    private static final String DEST_FILE = "destination.dat";
    private static final String DEST_DIR = "i2p";

    // Simplified destination structure for initial implementation
    // In a full implementation, this would include proper I2P key generation
    private Context context;
    private String base32Address;
    private byte[] destinationBytes;

    public I2PDestination(Context context) {
        this.context = context.getApplicationContext();
        loadOrGenerateDestination();
    }

    /**
     * Load existing destination from storage or generate new one
     */
    private void loadOrGenerateDestination() {
        File destFile = getDestinationFile();

        if (destFile.exists()) {
            Log.i(TAG, "Loading existing I2P destination");
            loadDestination(destFile);
        } else {
            Log.i(TAG, "Generating new I2P destination");
            generateDestination();
            saveDestination(destFile);
        }

        // Compute base32 address from destination
        if (destinationBytes != null) {
            this.base32Address = computeBase32Address(destinationBytes);
            Log.i(TAG, "I2P address: " + base32Address);
        }
    }

    /**
     * Generate new I2P destination
     *
     * Note: This is a simplified implementation for Phase 1.
     * A production implementation should use proper I2P key generation
     * with EdDSA signing keys and ElGamal encryption keys.
     */
    private void generateDestination() {
        try {
            // Generate random destination bytes
            // In production, this should be proper I2P destination with:
            // - 256 bytes for public key
            // - 128 bytes for signing key
            // - Certificate data
            SecureRandom random = new SecureRandom();
            destinationBytes = new byte[387]; // Standard I2P destination size
            random.nextBytes(destinationBytes);

            Log.i(TAG, "Generated new I2P destination (" + destinationBytes.length + " bytes)");
        } catch (Exception e) {
            Log.e(TAG, "Failed to generate I2P destination: " + e.getMessage());
        }
    }

    /**
     * Load destination from file
     */
    private void loadDestination(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int bytesRead = fis.read(data);

            if (bytesRead > 0) {
                this.destinationBytes = data;
                Log.i(TAG, "Loaded I2P destination from storage (" + bytesRead + " bytes)");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to load I2P destination: " + e.getMessage());
            generateDestination(); // Fallback to new generation
        }
    }

    /**
     * Save destination to file
     */
    private void saveDestination(File file) {
        try {
            file.getParentFile().mkdirs();

            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(destinationBytes);
            }

            Log.i(TAG, "Saved I2P destination to storage");
        } catch (IOException e) {
            Log.e(TAG, "Failed to save I2P destination: " + e.getMessage());
        }
    }

    /**
     * Compute Base32 address from destination bytes
     *
     * The Base32 address is derived from the SHA-256 hash of the destination
     * and encoded in Base32, resulting in a 52-character .b32.i2p address.
     */
    private String computeBase32Address(byte[] destination) {
        try {
            // Hash the destination
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(destination);

            // Take first 32 bytes and encode to Base32
            String base32 = base32Encode(hash);

            // Truncate to 52 characters and add .b32.i2p suffix
            if (base32.length() > 52) {
                base32 = base32.substring(0, 52);
            }

            return base32.toLowerCase() + ".b32.i2p";
        } catch (Exception e) {
            Log.e(TAG, "Failed to compute base32 address: " + e.getMessage());
            return "invalid.b32.i2p";
        }
    }

    /**
     * Simple Base32 encoding (RFC 4648)
     *
     * Note: This is a basic implementation. Production code should use
     * a proper Base32 library or I2P's own Base32 implementation.
     */
    private String base32Encode(byte[] data) {
        final String BASE32_ALPHABET = "abcdefghijklmnopqrstuvwxyz234567";
        StringBuilder result = new StringBuilder();

        int buffer = 0;
        int bufferLength = 0;

        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bufferLength += 8;

            while (bufferLength >= 5) {
                bufferLength -= 5;
                int index = (buffer >> bufferLength) & 0x1F;
                result.append(BASE32_ALPHABET.charAt(index));
            }
        }

        // Handle remaining bits
        if (bufferLength > 0) {
            int index = (buffer << (5 - bufferLength)) & 0x1F;
            result.append(BASE32_ALPHABET.charAt(index));
        }

        return result.toString();
    }

    /**
     * Get destination file path
     */
    private File getDestinationFile() {
        File i2pDir = new File(context.getFilesDir(), DEST_DIR);
        return new File(i2pDir, DEST_FILE);
    }

    /**
     * Get Base32 address (e.g., ukeu3k5o...dnkdq.b32.i2p)
     */
    public String getBase32Address() {
        return base32Address;
    }

    /**
     * Get full Base64 destination string
     */
    public String getFullDestination() {
        if (destinationBytes == null) {
            return null;
        }
        return Base64.encodeToString(destinationBytes, Base64.NO_WRAP);
    }

    /**
     * Get destination bytes
     */
    public byte[] getDestinationBytes() {
        return destinationBytes;
    }

    /**
     * Check if destination is valid
     */
    public boolean isValid() {
        return destinationBytes != null && base32Address != null;
    }
}

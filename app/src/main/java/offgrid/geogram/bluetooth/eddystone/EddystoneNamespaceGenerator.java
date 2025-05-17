package offgrid.geogram.bluetooth.eddystone;

import static offgrid.geogram.util.WiFiUtils.compareSsidHash;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

public class EddystoneNamespaceGenerator {

    public static String generateNamespaceId(String ssid, String password) {
        // Step 1: Hash the SSID to generate 2 bytes
        CRC32 crc32 = new CRC32();
        crc32.update(ssid.getBytes(StandardCharsets.UTF_8));
        long crcValue = crc32.getValue();
        byte[] ssidHash = new byte[2];
        ssidHash[0] = (byte) ((crcValue >> 8) & 0xFF); // Extract high byte
        ssidHash[1] = (byte) (crcValue & 0xFF);        // Extract low byte

        // Step 2: Process the password to ensure 8 bytes
        byte[] passwordBytes = password.getBytes(StandardCharsets.UTF_8);
        byte[] passwordPart = new byte[8];
        for (int i = 0; i < passwordPart.length; i++) {
            passwordPart[i] = (i < passwordBytes.length) ? passwordBytes[i] : 0x00; // Pad if too short
        }

        // Step 3: Combine SSID hash and password into 10-byte Namespace ID
        byte[] namespaceId = new byte[10];
        System.arraycopy(ssidHash, 0, namespaceId, 0, 2);  // First 2 bytes: SSID hash
        System.arraycopy(passwordPart, 0, namespaceId, 2, 8); // Next 8 bytes: Password

        // Step 4: Convert byte array to hexadecimal string
        StringBuilder hexNamespace = new StringBuilder();
        for (byte b : namespaceId) {
            hexNamespace.append(String.format("%02X", b));
        }

        return hexNamespace.toString();
    }

    public static String[] extractNamespaceDetails(String namespaceId) {
        if (namespaceId.length() != 20) {
            throw new IllegalArgumentException("Invalid Namespace ID. Must be 20 hex characters.");
        }

        // Extract the first 2 bytes as the SSID hash
        String ssidHash = namespaceId.substring(0, 4);

        // Extract the remaining 16 characters (8 bytes) as the password
        StringBuilder password = new StringBuilder();
        for (int i = 4; i < namespaceId.length(); i += 2) {
            int byteValue = Integer.parseInt(namespaceId.substring(i, i + 2), 16);
            password.append((char) byteValue);
        }

        return new String[] { ssidHash, password.toString().trim() };
    }


    public static void main(String[] args) {
        String ssid = "DIRECT-eN-Android_ObGr";
        String password = "AGUqgbC5";

        // Generate Namespace ID
        String namespaceId = generateNamespaceId(ssid, password);
        System.out.println("Generated Namespace ID: " + namespaceId);

        // Extract details from Namespace ID
        String[] details = extractNamespaceDetails(namespaceId);
        System.out.println("SSID Hash: " + details[0]);
        System.out.println("Password: " + details[1]);

        // Compare SSID hash
        boolean isMatch = compareSsidHash(ssid, details[0]);
        System.out.println("Does the SSID match the hash? " + isMatch);
    }


}

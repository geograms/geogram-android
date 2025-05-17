package offgrid.geogram.core.old.old;

/**
 * Generates a UUID for Bluetooth beacons.
 *
 * The UUID is constructed using several predefined parts, each with a fixed length:
 * - idApp: 2 characters: AA
 * - idProduct: 2 characters: BB
 * - idPub: 4 characters: CCCC
 * - idLocation: 4 characters: DDDD
 * - idMisc: 16 characters: MMMMMMMMMMMMMMMM
 * - idSSID: 2 characters: EE
 * - idPassword: 8 characters: FFFFFFFF
 *
 * The final UUID follows the standard format: 8-4-4-4-12.
 *
 * Example UUID:
 * AA-BBCCCC-DDDDMMMM-MMMMMMMM-EEFFFFFFFF
 */
public class GenerateUUID {

    // Public static variables for customization
    public static String idApp = "AA";       // 2 characters
    public static String idProduct = "BB";  // 2 characters
    public static String idPub = "CCCC";    // 4 characters
    public static String idLocation = "DDDD"; // 4 characters
    public static String idSSID = "EE";     // 2 characters
    public static String idPassword = "FFFFFFFF"; // 8 characters
    public static String idMisc = "MMMMMMMMMMMMMMMM"; // 16 characters (fixed size)

    /**
     * Generates a valid UUID string to use as an identifier for BLE.
     *
     * @return A UUID string.
     */
    public static String generateUUID() {
        // Validate lengths
        if (idApp.length() != 2 || idProduct.length() != 2 || idPub.length() != 4
                || idLocation.length() != 4 || idSSID.length() != 2 || idPassword.length() != 8 || idMisc.length() != 16) {
            throw new IllegalArgumentException("One or more ID parts have invalid lengths.");
        }

        // Assemble the UUID
        String uuid = idApp + idProduct + idPub + idLocation + idMisc + idSSID + idPassword;

        // Insert dashes to match UUID format (8-4-4-4-12)
        return uuid.substring(0, 8) + "-" + uuid.substring(8, 12) + "-" + uuid.substring(12, 16) + "-"
                + uuid.substring(16, 20) + "-" + uuid.substring(20);
    }
}

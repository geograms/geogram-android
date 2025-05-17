package offgrid.geogram.core.old.old;

import offgrid.geogram.wifi.WiFiCommon;

/**
 * Generates messages specific to the Eddystone beacon.
 */
public class GenerateMessage {
    private static final String TAG = "MessageGenerator";

    public static String generateShareSSID() {
        String ssid = WiFiCommon.ssid;
        String password = WiFiCommon.passphrase;

        if(ssid == null){
            ssid = "NONE";
        }else{
            ssid = computeHexadecimalHash(ssid);
        }


        if(password == null){
            password = "NONE";
        }

        return ssid + ":" + password;
    }


    /**
     * Create a 4-character hexadecimal hash from a given string.
     * This is useful to reduce the size of the SSID that is provided
     * by the bluetooth beacon.
     * @param input a text to encode
     * @return
     */
    public static String computeHexadecimalHash(String input) {
        String chars = "0123456789abcdef";
        int hash = 0;

        // Compute hash using simple additive and bitwise operations
        for (int i = 0; i < input.length(); i++) {
            hash = (hash * 31 + input.charAt(i)) % 65536; // Keep hash within 16-bit range
        }

        // Generate a 4-character hash
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            result.append(chars.charAt(hash % chars.length())); // Map hash to hexadecimal characters
            hash /= chars.length(); // Adjust hash for next character
        }

        return result.toString();
    }

}

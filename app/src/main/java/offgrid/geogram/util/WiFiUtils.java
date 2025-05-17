package offgrid.geogram.util;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class WiFiUtils {

    /**
     * Generates a 2-byte hash from the given SSID and returns it as both a byte array and a string.
     *
     * @param ssid the SSID string
     * @return a string representing the 2-byte hash in hexadecimal format
     */
    public static String generateHashSSID(String ssid) {
        CRC32 crc32 = new CRC32();
        crc32.update(ssid.getBytes(StandardCharsets.UTF_8));
        long crcValue = crc32.getValue();

        // Extract the two bytes from the CRC32 value
        byte[] ssidHash = new byte[2];
        ssidHash[0] = (byte) ((crcValue >> 8) & 0xFF); // High byte
        ssidHash[1] = (byte) (crcValue & 0xFF);        // Low byte

        // Convert the byte array to a hexadecimal string
        return String.format("%02X%02X", ssidHash[0], ssidHash[1]);
    }


    public static boolean compareSsidHash(String ssid, String hash) {
        if (hash.length() != 4) {
            throw new IllegalArgumentException("Invalid hash. Must be 4 hex characters.");
        }

        // Generate the hash for the given SSID
        CRC32 crc32 = new CRC32();
        crc32.update(ssid.getBytes(StandardCharsets.UTF_8));
        long crcValue = crc32.getValue();
        String generatedHash = String.format("%02X%02X", (crcValue >> 8) & 0xFF, crcValue & 0xFF);

        // Compare the generated hash with the provided hash
        return generatedHash.equalsIgnoreCase(hash);
    }


    public static void disableCertificateValidation() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {}

                        @Override
                        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {}

                        @Override
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return new java.security.cert.X509Certificate[0];
                        }
                    }
            };

            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

            // Ignore hostname verification
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}

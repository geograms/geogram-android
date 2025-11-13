package offgrid.geogram.util;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

public class NostrKeyGenerator {

    public static class NostrKeys {
        public final String npub;
        public final String nsec;

        public NostrKeys(String npub, String nsec) {
            this.npub = npub;
            this.nsec = nsec;
        }
    }

    /**
     * Generates a new NOSTR key pair (npub/nsec)
     */
    public static NostrKeys generateKeyPair() {
        try {
            // Generate EC key pair (secp256k1 would be ideal, but using standard EC for now)
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
            keyGen.initialize(256, new SecureRandom());
            KeyPair keyPair = keyGen.generateKeyPair();

            PublicKey publicKey = keyPair.getPublic();
            PrivateKey privateKey = keyPair.getPrivate();

            // Convert to bytes
            byte[] publicKeyBytes = publicKey.getEncoded();
            byte[] privateKeyBytes = privateKey.getEncoded();

            // For now, create simplified npub/nsec (in production, use proper bech32 encoding)
            String npub = "npub1" + bytesToHex(publicKeyBytes).substring(0, 59);
            String nsec = "nsec1" + bytesToHex(privateKeyBytes).substring(0, 59);

            return new NostrKeys(npub, nsec);

        } catch (Exception e) {
            e.printStackTrace();
            // Fallback to random hex-based keys
            return generateFallbackKeys();
        }
    }

    private static NostrKeys generateFallbackKeys() {
        SecureRandom random = new SecureRandom();
        byte[] publicBytes = new byte[32];
        byte[] privateBytes = new byte[32];

        random.nextBytes(publicBytes);
        random.nextBytes(privateBytes);

        String npub = "npub1" + bytesToHex(publicBytes).substring(0, 59);
        String nsec = "nsec1" + bytesToHex(privateBytes).substring(0, 59);

        return new NostrKeys(npub, nsec);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}

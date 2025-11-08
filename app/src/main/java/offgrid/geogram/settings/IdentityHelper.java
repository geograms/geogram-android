package offgrid.geogram.settings;

import offgrid.geogram.util.nostr.Identity;
import offgrid.geogram.util.nostr.PrivateKey;
import offgrid.geogram.util.nostr.PublicKey;

/**
 * Helper class for generating and managing Nostr identities and callsigns.
 * Callsigns are derived from npub in the format: X1 + first 4 chars after 'npub1'
 */
public class IdentityHelper {

    /**
     * Generate a new random Nostr identity (npub/nsec pair) and derive callsign.
     * @return NostrIdentity containing npub, nsec, and callsign
     */
    public static NostrIdentity generateNewIdentity() {
        // Generate random Nostr keypair
        Identity identity = Identity.generateRandomIdentity();

        // Get npub and nsec in bech32 format
        String npub = identity.getPublicKey().toBech32String();
        String nsec = identity.getPrivateKey().toBech32String();

        // Derive callsign from npub
        String callsign = deriveCallsignFromNpub(npub);

        return new NostrIdentity(npub, nsec, callsign);
    }

    /**
     * Derive callsign from npub.
     * Format: X1 + first 4 characters after 'npub1'
     * Example: npub1abcd... -> X1ABCD
     *
     * @param npub The npub string (must start with "npub1")
     * @return Callsign in format X1XXXX
     */
    public static String deriveCallsignFromNpub(String npub) {
        if (npub == null || !npub.toLowerCase().startsWith("npub1")) {
            throw new IllegalArgumentException("Invalid npub format");
        }

        // Extract data after 'npub1'
        String data = npub.substring(5); // Skip "npub1"

        // Take first 4 characters and uppercase
        String suffix = data.substring(0, Math.min(4, data.length())).toUpperCase();

        // Ensure we have exactly 4 characters (pad with X if needed)
        while (suffix.length() < 4) {
            suffix += "X";
        }

        return "X1" + suffix;
    }

    /**
     * Data class holding Nostr identity components
     */
    public static class NostrIdentity {
        public final String npub;
        public final String nsec;
        public final String callsign;

        public NostrIdentity(String npub, String nsec, String callsign) {
            this.npub = npub;
            this.nsec = nsec;
            this.callsign = callsign;
        }
    }
}

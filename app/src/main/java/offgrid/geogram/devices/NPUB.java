package offgrid.geogram.devices;

/**
 * Defines an NPUB identity that is associated to a user.
 */
public class NPUB {

    // the public key itself
    private String value = null;

    // in case of leak, don't trust new info from this pubkey
    private boolean valid = true;
    // any messages after this date should be discarded
    private long invalidSince = -1;
}

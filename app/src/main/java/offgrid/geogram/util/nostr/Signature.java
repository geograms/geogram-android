package offgrid.geogram.util.nostr;

/**
 *
 * @author squirrel
 */
public class Signature {
    
    private byte[] rawData;

    private PublicKey pubKey;

    public byte[] getRawData() {
        return rawData;
    }

    public void setRawData(byte[] rawData) {
        this.rawData = rawData;
    }

    public PublicKey getPubKey() {
        return pubKey;
    }

    public void setPubKey(PublicKey pubKey) {
        this.pubKey = pubKey;
    }
    

    @Override
    public String toString() {
        return NostrUtil.bytesToHex(rawData);
    }
}

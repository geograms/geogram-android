package offgrid.geogram.util.nostr;


/**
 *
 * @author squirrel
 */
public class PublicKey extends BaseKey {

    public PublicKey(byte[] rawData) {
        super(KeyType.PUBLIC, rawData, Bech32Prefix.NPUB);
    }

    public PublicKey(String hexPubKey) {
    	super(KeyType.PUBLIC, NostrUtil.hexToBytes(hexPubKey), Bech32Prefix.NPUB);
    }    

    @Override
    public byte[] getRawData() {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }
}

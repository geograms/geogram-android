package offgrid.geogram.util.nostr;


import java.util.Arrays;

/**
 * @author squirrel
 */
public abstract class BaseKey implements IKey {

    protected final KeyType type;

    protected final byte[] rawData;

    protected final Bech32Prefix prefix;

    public BaseKey(KeyType type, byte[] rawData, Bech32Prefix prefix) {
        this.type = type;
        this.rawData = rawData;
        this.prefix = prefix;
    }

    @Override
    public byte[] getRawData() {
        return rawData;
    }

    
    @Override
    public String toBech32String() {
        try {
            return Bech32.toBech32(prefix, rawData);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public String toString() {
        return toHexString();
    }

    public String toHexString() {
        return NostrUtil.bytesToHex(rawData);
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 31 * hash + this.type.hashCode();
        hash = 31 * hash + (this.prefix == null ? 0 : this.prefix.hashCode());
        hash = 31 * hash + (this.rawData == null ? 0 : Arrays.hashCode(this.rawData));
        return hash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;

        // null check
        if (o == null)
            return false;

        // type check and cast
        if (getClass() != o.getClass())
            return false;

        BaseKey baseKey = (BaseKey) o;

        // field comparison
        return Arrays.equals(rawData, baseKey.rawData);
    }
}

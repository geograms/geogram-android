package offgrid.geogram.util.nostr;


/**
 * @author squirrel
 */
public class Identity {

    private final PrivateKey privateKey;

    private Identity(PrivateKey privateKey) {
        this.privateKey = privateKey;
    }

    public PrivateKey getPrivateKey() {
        return privateKey;
    }

    @Deprecated(forRemoval = true)
    public static Identity getInstance(PrivateKey privateKey) {
        return new Identity(privateKey);
    }

    public static Identity create(PrivateKey privateKey) {
        return new Identity(privateKey);
    }

    @Deprecated(forRemoval = true)
    public static Identity getInstance(String privateKey) {
        return new Identity(new PrivateKey(privateKey));
    }

    public static Identity create(String privateKey) {
        return new Identity(new PrivateKey(privateKey));
    }

    /**
     * @return A strong pseudo random identity
     */
    public static Identity generateRandomIdentity() {
        return new Identity(PrivateKey.generateRandomPrivKey());
    }

    public PublicKey getPublicKey() {
        try {
            return new PublicKey(Schnorr.genPubKey(
                    this.getPrivateKey().getRawData())
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

//    public Signature sign(ISignable signable)  {
//        if (signable instanceof GenericEvent genericEvent) {
//            try {
//                return signEvent(genericEvent);
//            } catch (Exception ex) {
//                throw new RuntimeException(ex);
//            }
//        } else if (signable instanceof DelegationTag delegationTag) {
//            try {
//                return signDelegationTag(delegationTag);
//            } catch (Exception ex) {
//                throw new RuntimeException(ex);
//            }
//        }
//        throw new RuntimeException();
//    }
//
//    private Signature signEvent(GenericEvent event) throws Exception {
//        event.update();
//        log.log(Level.FINER, "Serialized event: {0}", new String(event.get_serializedEvent()));
//        final var signedHashedSerializedEvent = Schnorr.sign(NostrUtil.sha256(event.get_serializedEvent()), this.getPrivateKey().getRawData(), generateAuxRand());
//        final Signature signature = new Signature();
//        signature.setRawData(signedHashedSerializedEvent);
//        signature.setPubKey(getPublicKey());
//        event.setSignature(signature);
//        return signature;
//    }
//
//    private Signature signDelegationTag(DelegationTag delegationTag) throws Exception {
//        final var signedHashedToken = Schnorr.sign(NostrUtil.sha256(delegationTag.getToken().getBytes(StandardCharsets.UTF_8)), this.getPrivateKey().getRawData(), generateAuxRand());
//        final Signature signature = new Signature();
//        signature.setRawData(signedHashedToken);
//        signature.setPubKey(getPublicKey());
//        delegationTag.setSignature(signature);
//        return signature;
//    }

    private byte[] generateAuxRand() {
        return NostrUtil.createRandomByteArray(32);
    }

}
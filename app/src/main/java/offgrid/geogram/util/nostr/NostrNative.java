/*
 * Native implementation of the NOSTR identity
 *
 * Copyright (c) Nostrium contributors
 * License: Apache-2.0
 */
package offgrid.geogram.util.nostr;


/**
 * @author Brito
 * @date: 2024-12-24
 * @location: Germany
 */
public class NostrNative {

  

    public static void main(String[] args) throws Exception {
        // Generate private key (32 bytes)
        Identity user = Identity.generateRandomIdentity();
        String nsec = user.getPrivateKey().toBech32String();
        String npub = user.getPublicKey().toBech32String();

        // Print generated NSEC and NPUB
        System.out.println("Generated NSEC: " + nsec);
        System.out.println("Generated NPUB: " + npub);

        // Validate NPUB generation from NSEC
        
    }

}

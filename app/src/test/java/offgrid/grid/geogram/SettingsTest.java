package offgrid.grid.geogram;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import offgrid.geogram.util.nostr.Identity;
import offgrid.geogram.settings.SettingsUser;

/**
 * Test the Settings class
 */
public class SettingsTest {

    @Test
    public void testBoundaries() {

        // test the NOSTR data
        Identity user = Identity.generateRandomIdentity();
        String nsec = user.getPrivateKey().toBech32String();
        String npub = user.getPublicKey().toBech32String();

        SettingsUser settings = new SettingsUser();

        assertNull(settings.getNsec());
        settings.setNsec(nsec);
        assertEquals(nsec, settings.getNsec());
    }
}
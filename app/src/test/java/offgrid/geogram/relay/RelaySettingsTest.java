package offgrid.geogram.relay;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import static org.junit.Assert.*;

/**
 * Unit tests for RelaySettings.
 */
@RunWith(RobolectricTestRunner.class)
public class RelaySettingsTest {

    private Context context;
    private RelaySettings settings;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        settings = new RelaySettings(context);

        // Reset to defaults
        settings.setRelayEnabled(false);
        settings.setDiskSpaceLimitMB(1024);
        settings.setAutoAcceptEnabled(false);
        settings.setAcceptedMessageTypes("text_only");
    }

    @Test
    public void testDefaultValues() {
        RelaySettings freshSettings = new RelaySettings(context);

        assertFalse("Relay should be disabled by default",
                freshSettings.isRelayEnabled());
        assertEquals("Default disk space should be 1GB",
                1024, freshSettings.getDiskSpaceLimitMB());
        assertFalse("Auto-accept should be disabled by default",
                freshSettings.isAutoAcceptEnabled());
        assertEquals("Default message types should be text_only",
                "text_only", freshSettings.getAcceptedMessageTypes());
    }

    @Test
    public void testRelayEnabled() {
        assertFalse(settings.isRelayEnabled());

        settings.setRelayEnabled(true);
        assertTrue(settings.isRelayEnabled());

        settings.setRelayEnabled(false);
        assertFalse(settings.isRelayEnabled());
    }

    @Test
    public void testDiskSpaceLimitMB() {
        assertEquals(1024, settings.getDiskSpaceLimitMB());

        settings.setDiskSpaceLimitMB(2048);
        assertEquals(2048, settings.getDiskSpaceLimitMB());

        settings.setDiskSpaceLimitMB(100);
        assertEquals(100, settings.getDiskSpaceLimitMB());

        settings.setDiskSpaceLimitMB(10240);
        assertEquals(10240, settings.getDiskSpaceLimitMB());
    }

    @Test
    public void testDiskSpaceLimitBytes() {
        settings.setDiskSpaceLimitMB(1024);
        assertEquals(1024L * 1024L * 1024L, settings.getDiskSpaceLimitBytes());

        settings.setDiskSpaceLimitMB(512);
        assertEquals(512L * 1024L * 1024L, settings.getDiskSpaceLimitBytes());

        settings.setDiskSpaceLimitMB(100);
        assertEquals(100L * 1024L * 1024L, settings.getDiskSpaceLimitBytes());
    }

    @Test
    public void testAutoAcceptEnabled() {
        assertFalse(settings.isAutoAcceptEnabled());

        settings.setAutoAcceptEnabled(true);
        assertTrue(settings.isAutoAcceptEnabled());

        settings.setAutoAcceptEnabled(false);
        assertFalse(settings.isAutoAcceptEnabled());
    }

    @Test
    public void testAcceptedMessageTypes() {
        assertEquals("text_only", settings.getAcceptedMessageTypes());

        settings.setAcceptedMessageTypes("text_and_images");
        assertEquals("text_and_images", settings.getAcceptedMessageTypes());

        settings.setAcceptedMessageTypes("everything");
        assertEquals("everything", settings.getAcceptedMessageTypes());

        settings.setAcceptedMessageTypes("text_only");
        assertEquals("text_only", settings.getAcceptedMessageTypes());
    }

    @Test
    public void testPersistence() {
        // Set values
        settings.setRelayEnabled(true);
        settings.setDiskSpaceLimitMB(2048);
        settings.setAutoAcceptEnabled(true);
        settings.setAcceptedMessageTypes("text_and_images");

        // Create new instance (should load from SharedPreferences)
        RelaySettings settings2 = new RelaySettings(context);

        // Verify values persisted
        assertTrue(settings2.isRelayEnabled());
        assertEquals(2048, settings2.getDiskSpaceLimitMB());
        assertTrue(settings2.isAutoAcceptEnabled());
        assertEquals("text_and_images", settings2.getAcceptedMessageTypes());
    }

    @Test
    public void testMultipleUpdates() {
        // Update multiple times
        settings.setDiskSpaceLimitMB(500);
        settings.setDiskSpaceLimitMB(1000);
        settings.setDiskSpaceLimitMB(1500);
        settings.setDiskSpaceLimitMB(2000);

        // Should have latest value
        assertEquals(2000, settings.getDiskSpaceLimitMB());

        // Create new instance to verify persistence
        RelaySettings settings2 = new RelaySettings(context);
        assertEquals(2000, settings2.getDiskSpaceLimitMB());
    }

    @Test
    public void testBoundaryValues() {
        // Test minimum disk space
        settings.setDiskSpaceLimitMB(100);
        assertEquals(100, settings.getDiskSpaceLimitMB());
        assertEquals(100L * 1024L * 1024L, settings.getDiskSpaceLimitBytes());

        // Test maximum disk space
        settings.setDiskSpaceLimitMB(10240);
        assertEquals(10240, settings.getDiskSpaceLimitMB());
        assertEquals(10240L * 1024L * 1024L, settings.getDiskSpaceLimitBytes());
    }

    @Test
    public void testIndependentSettings() {
        // Set all settings to non-default values
        settings.setRelayEnabled(true);
        settings.setDiskSpaceLimitMB(2048);
        settings.setAutoAcceptEnabled(true);
        settings.setAcceptedMessageTypes("everything");

        // Change one setting
        settings.setRelayEnabled(false);

        // Verify other settings unchanged
        assertEquals(2048, settings.getDiskSpaceLimitMB());
        assertTrue(settings.isAutoAcceptEnabled());
        assertEquals("everything", settings.getAcceptedMessageTypes());
    }

    @Test
    public void testConcurrentInstances() {
        RelaySettings settings1 = new RelaySettings(context);
        RelaySettings settings2 = new RelaySettings(context);

        // Change setting in first instance
        settings1.setRelayEnabled(true);

        // Create new instance to verify it sees the change
        RelaySettings settings3 = new RelaySettings(context);
        assertTrue(settings3.isRelayEnabled());
    }
}

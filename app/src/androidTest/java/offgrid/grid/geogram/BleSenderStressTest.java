package offgrid.grid.geogram;

import static org.junit.Assert.*;

import android.content.Context;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import offgrid.geogram.ble.BluetoothMessage;
import offgrid.geogram.ble.BluetoothSender;
import offgrid.geogram.core.Central;

/**
 * Multi-device BLE stress test - SENDER role
 *
 * This test should run on Device 1 (Samsung)
 *
 * Test scenarios:
 * 1. Send small messages (single parcel)
 * 2. Send medium messages (2-3 parcels)
 * 3. Send large messages (10+ parcels)
 * 4. Send rapid-fire messages to test queue handling
 * 5. Send messages with special characters and checksums
 *
 * Run with: adb -s R58M91ETKFE shell am instrument -w -e class offgrid.grid.geogram.BleSenderStressTest off.grid.geogram.test/androidx.test.runner.AndroidJUnitRunner
 */
public class BleSenderStressTest {
    private static final String TAG = "BleSenderStressTest";
    private Context context;
    private BluetoothSender sender;

    // Test messages with varying complexity
    private static final String SHORT_MSG = "PING";
    private static final String MEDIUM_MSG = "This is a medium length message designed to span multiple BLE parcels for testing purposes.";
    private static final String LONG_MSG = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.";

    @Before
    public void setup() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        sender = BluetoothSender.getInstance(context);
        sender.stop(); // Stop any previous instance
        sender.start(); // Start fresh

        Log.i(TAG, "=== BLE SENDER STRESS TEST STARTING ===");
        Log.i(TAG, "Device: Samsung SM-G398FN");
        Log.i(TAG, "Waiting for receiver to be ready...");

        // Give receiver time to start listening
        sleep(3000);
    }

    @Test
    public void test01_sendSingleParcelMessage() {
        Log.i(TAG, "\n=== TEST 1: Single Parcel Message ===");

        BluetoothMessage msg = new BluetoothMessage("SENDER", "RECEIVER", SHORT_MSG, true);

        Log.i(TAG, "Message ID: " + msg.getId());
        Log.i(TAG, "Parcels: " + msg.getMessageParcelsTotal());
        Log.i(TAG, "Content: " + SHORT_MSG);

        assertEquals("Single message should have 1 parcel", 1, msg.getMessageParcelsTotal());

        sender.sendMessage(msg);
        sleep(2000); // Wait for transmission

        Log.i(TAG, "✓ Single parcel sent");
    }

    @Test
    public void test02_sendMediumMessage() {
        Log.i(TAG, "\n=== TEST 2: Medium Message (Multiple Parcels) ===");

        BluetoothMessage msg = new BluetoothMessage("SENDER", "RECEIVER", MEDIUM_MSG, false);

        Log.i(TAG, "Message ID: " + msg.getId());
        Log.i(TAG, "Parcels: " + msg.getMessageParcelsTotal());
        Log.i(TAG, "Checksum: " + msg.getChecksum());
        Log.i(TAG, "Content length: " + MEDIUM_MSG.length() + " chars");

        assertTrue("Medium message should have multiple parcels", msg.getMessageParcelsTotal() > 1);

        sender.sendMessage(msg);
        sleep(msg.getMessageParcelsTotal() * 1000); // 1 sec per parcel

        Log.i(TAG, "✓ Medium message sent (" + msg.getMessageParcelsTotal() + " parcels)");
    }

    @Test
    public void test03_sendLargeMessage() {
        Log.i(TAG, "\n=== TEST 3: Large Message (Many Parcels) ===");

        BluetoothMessage msg = new BluetoothMessage("SENDER", "RECEIVER", LONG_MSG, false);

        Log.i(TAG, "Message ID: " + msg.getId());
        Log.i(TAG, "Parcels: " + msg.getMessageParcelsTotal());
        Log.i(TAG, "Checksum: " + msg.getChecksum());
        Log.i(TAG, "Content length: " + LONG_MSG.length() + " chars");

        assertTrue("Large message should have 10+ parcels", msg.getMessageParcelsTotal() >= 10);

        sender.sendMessage(msg);
        sleep(msg.getMessageParcelsTotal() * 1000); // 1 sec per parcel

        Log.i(TAG, "✓ Large message sent (" + msg.getMessageParcelsTotal() + " parcels)");
    }

    @Test
    public void test04_sendRapidFireMessages() {
        Log.i(TAG, "\n=== TEST 4: Rapid Fire (Queue Stress) ===");

        int messageCount = 5;
        List<BluetoothMessage> messages = new ArrayList<>();

        for (int i = 0; i < messageCount; i++) {
            String content = "RapidFire-" + i + ": " + MEDIUM_MSG;
            BluetoothMessage msg = new BluetoothMessage("SENDER", "RECEIVER", content, false);
            messages.add(msg);

            Log.i(TAG, "Queueing message " + (i + 1) + "/" + messageCount + " - ID: " + msg.getId());
        }

        // Send all messages rapidly
        for (BluetoothMessage msg : messages) {
            sender.sendMessage(msg);
            sleep(100); // Very short delay to stress the queue
        }

        // Wait for all to be transmitted
        int totalParcels = 0;
        for (BluetoothMessage msg : messages) {
            totalParcels += msg.getMessageParcelsTotal();
        }

        Log.i(TAG, "Total parcels queued: " + totalParcels);
        sleep(totalParcels * 1000); // 1 sec per parcel

        Log.i(TAG, "✓ Rapid fire test complete");
    }

    @Test
    public void test05_sendSpecialCharacters() {
        Log.i(TAG, "\n=== TEST 5: Special Characters & Edge Cases ===");

        String[] specialMessages = {
            "Emoji test: 🚀📱🔥",
            "Unicode: 日本語テスト",
            "Symbols: !@#$%^&*()_+-=[]{}|;':\",./<>?",
            "Newlines:\nLine1\nLine2\nLine3",
            "Tabs:\tTab1\tTab2\tTab3"
        };

        for (String content : specialMessages) {
            BluetoothMessage msg = new BluetoothMessage("SENDER", "RECEIVER", content, false);

            Log.i(TAG, "Sending: " + content.substring(0, Math.min(20, content.length())) + "...");
            Log.i(TAG, "  ID: " + msg.getId() + ", Parcels: " + msg.getMessageParcelsTotal());

            sender.sendMessage(msg);
            sleep(msg.getMessageParcelsTotal() * 1000);
        }

        Log.i(TAG, "✓ Special characters test complete");
    }

    @Test
    public void test99_cleanup() {
        Log.i(TAG, "\n=== SENDER TEST CLEANUP ===");
        sleep(2000); // Let receiver catch up
        sender.stop();
        Log.i(TAG, "=== BLE SENDER STRESS TEST COMPLETE ===");
    }

    private void sleep(int milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Log.e(TAG, "Sleep interrupted", e);
        }
    }
}

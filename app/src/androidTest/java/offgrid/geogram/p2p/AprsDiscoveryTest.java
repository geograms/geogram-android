package offgrid.geogram.p2p;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

/**
 * Integration test for APRS-IS peer discovery.
 * Tests actual connectivity to APRS-IS and message send/receive.
 */
public class AprsDiscoveryTest {

    private static final String APRS_SERVER = "rotate.aprs2.net";
    private static final int APRS_PORT = 14580; // Standard port for sending
    private static final int APRS_FULL_FEED_PORT = 10152; // Full feed port for listening
    private static final String TEST_CALLSIGN = "X1ABCD"; // Valid amateur radio callsign for testing
    private static final String TEST_PEER_ID = "12D3KooWTest123456789ABCDEFGH";
    private static final int TIMEOUT_SECONDS = 30;

    /**
     * Calculate APRS passcode for a callsign
     * Algorithm from: http://www.aprs.org/aprs11/correctness.txt
     */
    private int calculatePasscode(String callsign) {
        callsign = callsign.toUpperCase().split("-")[0]; // use base callsign only

        int hash = 0x73e2;
        for (int i = 0; i < callsign.length(); i++) {
            hash ^= callsign.charAt(i) << 8;
            if (++i < callsign.length()) {
                hash ^= callsign.charAt(i);
            }
        }
        return hash & 0x7FFF; // 15-bit positive integer
    }

    /**
     * Test APRS-IS connectivity by connecting and logging in
     */
    @Test
    public void testAprsIsConnection() throws Exception {
        android.util.Log.i("P2P/Test", "Testing APRS-IS connection...");

        try (Socket socket = new Socket(APRS_SERVER, APRS_PORT)) {
            BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            BufferedWriter out = new BufferedWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII));

            // Read server banner
            String banner = in.readLine();
            android.util.Log.i("P2P/Test", "Server banner: " + banner);
            assertNotNull("Should receive server banner", banner);
            assertTrue("Banner should contain 'aprsc'", banner.contains("aprsc"));

            // Login (read-only with pass -1)
            String login = "user " + TEST_CALLSIGN + " pass -1 vers GeogramTest 1.0";
            android.util.Log.i("P2P/Test", "Sending login: " + login);
            out.write(login);
            out.newLine();
            out.flush();

            // Read login response
            String response = in.readLine();
            android.util.Log.i("P2P/Test", "Login response: " + response);
            assertNotNull("Should receive login response", response);

            android.util.Log.i("P2P/Test", "APRS-IS connection test PASSED");

        } catch (Exception e) {
            android.util.Log.e("P2P/Test", "Connection failed: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Test sending and receiving GEOGRAM P2P announcement
     * This is the full integration test
     */
    @Test
    public void testSendAndReceiveAnnouncement() throws Exception {
        android.util.Log.i("P2P/Test", "Testing APRS-IS announcement send/receive...");

        CountDownLatch receivedLatch = new CountDownLatch(1);
        AtomicBoolean announcementReceived = new AtomicBoolean(false);
        String expectedMessage = "GEOGRAM P2P " + TEST_PEER_ID;

        // Start listener thread
        Thread listenerThread = new Thread(() -> {
            try (Socket socket = new Socket(APRS_SERVER, APRS_FULL_FEED_PORT)) {
                BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                BufferedWriter out = new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII));

                // Read server banner
                String banner = in.readLine();
                android.util.Log.i("P2P/Test", "Listener connected to full feed port: " + banner);

                // Login as listener (read-only) with buddy filter for our test callsign
                // Filter b/X1ABCD receives only packets from X1ABCD
                String login = "user LISTENER pass -1 vers GeogramTest 1.0 filter b/X1ABCD";
                android.util.Log.i("P2P/Test", "Listener login with buddy filter: " + login);
                out.write(login);
                out.newLine();
                out.flush();

                // Read login response
                String loginResp = in.readLine();
                android.util.Log.i("P2P/Test", "Listener login response: " + loginResp);
                android.util.Log.i("P2P/Test", "Listener waiting for announcements...");

                // Read messages
                String line;
                long startTime = System.currentTimeMillis();
                int messageCount = 0;
                while ((line = in.readLine()) != null) {
                    // Timeout after 30 seconds
                    if (System.currentTimeMillis() - startTime > TIMEOUT_SECONDS * 1000) {
                        android.util.Log.i("P2P/Test", "Timeout reached. Total messages received: " + messageCount);
                        break;
                    }

                    messageCount++;
                    // Log first 10 messages and every 100th message to see what we're receiving
                    if (messageCount <= 10 || messageCount % 100 == 0) {
                        android.util.Log.i("P2P/Test", "Message #" + messageCount + ": " + line);
                    }

                    // Check if this is our announcement
                    if (line.contains(expectedMessage)) {
                        android.util.Log.i("P2P/Test", "✓ Found our announcement in message #" + messageCount + "!");
                        announcementReceived.set(true);
                        receivedLatch.countDown();
                        break;
                    }
                }
                android.util.Log.i("P2P/Test", "Listener finished. Total messages: " + messageCount);

            } catch (Exception e) {
                android.util.Log.e("P2P/Test", "Listener error: " + e.getMessage());
            }
        });
        listenerThread.start();

        // Wait a bit for listener to connect
        Thread.sleep(2000);

        // Send announcement
        android.util.Log.i("P2P/Test", "Sending announcement...");
        try (Socket socket = new Socket(APRS_SERVER, APRS_PORT)) {
            BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            BufferedWriter out = new BufferedWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII));

            // Login with valid passcode for sending (don't read banner first - just like geogram-server)
            int passcode = calculatePasscode(TEST_CALLSIGN);
            String login = "user " + TEST_CALLSIGN + " pass " + passcode + " vers GeogramTest 1.0";
            android.util.Log.i("P2P/Test", "Sender login: " + login + " (passcode=" + passcode + ")");
            out.write(login);
            out.newLine();
            out.flush();

            // Wait for login to be processed (don't read response - just like geogram-server)
            Thread.sleep(500);

            // Send APRS status message
            // Format: CALLSIGN>DESTINATION,TCPIP*:>message
            String aprsMessage = TEST_CALLSIGN + ">" + TEST_CALLSIGN + ",TCPIP*:>" + expectedMessage;
            android.util.Log.i("P2P/Test", "Sending APRS message: " + aprsMessage);
            out.write(aprsMessage);
            out.newLine();
            out.flush();

            android.util.Log.i("P2P/Test", "Announcement sent! Waiting for listener to receive it...");
        }

        // Wait for announcement to be received
        boolean received = receivedLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        listenerThread.interrupt();

        assertTrue("Should receive our own announcement within " + TIMEOUT_SECONDS + " seconds", received);
        assertTrue("Announcement should be detected", announcementReceived.get());

        android.util.Log.i("P2P/Test", "Send/receive test PASSED!");
    }

    /**
     * Test the AprsPeerDiscovery class message parsing
     */
    @Test
    public void testAprsPeerDiscoveryParsing() {
        android.util.Log.i("P2P/Test", "Testing AprsPeerDiscovery message parsing...");

        AprsPeerDiscovery discovery = new AprsPeerDiscovery(
            InstrumentationRegistry.getInstrumentation().getTargetContext(),
            "12D3KooWLocalPeer",
            "NOCALL"
        );

        AtomicBoolean peerDiscovered = new AtomicBoolean(false);
        final String[] discoveredPeerId = new String[1];

        discovery.addListener(peerInfo -> {
            android.util.Log.i("P2P/Test", "Peer discovered: " + peerInfo.peerId);
            peerDiscovered.set(true);
            discoveredPeerId[0] = peerInfo.peerId;
        });

        // Simulate receiving an APRS message with peer announcement
        String testMessage = "X1ABCD>X1ABCD,TCPIP*:>GEOGRAM P2P 12D3KooWRemotePeer123";
        discovery.handleAprsMessage(testMessage);

        assertTrue("Should discover peer from APRS message", peerDiscovered.get());
        assertEquals("Should extract correct peer ID", "12D3KooWRemotePeer123", discoveredPeerId[0]);

        android.util.Log.i("P2P/Test", "Message parsing test PASSED!");
    }

    /**
     * Test passcode calculation
     */
    @Test
    public void testPasscodeCalculation() {
        // Known test vectors verified with geogram-server implementation
        assertEquals("X1ABCD passcode should be 10709", 10709, calculatePasscode("X1ABCD"));
        assertEquals("N0CALL passcode should be 13023", 13023, calculatePasscode("N0CALL"));
        assertEquals("W1ABC passcode should be 9873", 9873, calculatePasscode("W1ABC"));

        android.util.Log.i("P2P/Test", "Passcode calculation test PASSED!");
    }
}

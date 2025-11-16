package offgrid.geogram.p2p;

import android.content.Context;
import offgrid.geogram.core.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * APRS-IS connection service for libp2p peer discovery.
 *
 * This service:
 * - Connects to APRS-IS network (rotate.aprs2.net:14580)
 * - Sends periodic P2P announcements (every 30 minutes)
 * - Listens for packets FROM X1* callsigns using filter p/X1
 * - Forwards received messages to P2PService for peer discovery
 */
public class AprsIsService {
    private static final String TAG = "P2P/APRS-IS";

    // APRS-IS server settings
    private static final String APRS_SERVER = "rotate.aprs2.net";
    private static final int APRS_PORT = 14580;

    // Announcement interval (30 minutes as per APRS best practices)
    private static final long ANNOUNCE_INTERVAL_MINUTES = 30;

    // Filter to receive messages FROM X1* callsigns (for libp2p discovery)
    // p/X1 = prefix filter for packets from callsigns starting with X1
    // This catches all announcements from Geogram devices (which all use X1* callsigns)
    private static final String APRS_FILTER = "p/X1";

    private final Context context;
    private final String callsign;
    private final int passcode;
    private final String peerAnnouncementMessage;
    private final AprsMessageListener messageListener;

    private ScheduledExecutorService scheduler;
    private Thread listenerThread;
    private AtomicBoolean isRunning = new AtomicBoolean(false);
    private AtomicBoolean shouldReconnect = new AtomicBoolean(true);

    // Connection state
    private Socket socket;
    private BufferedWriter out;
    private BufferedReader in;

    /**
     * Listener interface for incoming APRS messages
     */
    public interface AprsMessageListener {
        void onAprsMessage(String message);
        void onConnectionStateChanged(boolean connected);
    }

    /**
     * Create APRS-IS service
     *
     * @param context Application context
     * @param callsign APRS callsign (e.g., "X1ABCD")
     * @param peerAnnouncementMessage Full announcement message (e.g., "GEOGRAM P2P 12D3KooW...")
     * @param listener Message listener
     */
    public AprsIsService(Context context, String callsign, String peerAnnouncementMessage,
                         AprsMessageListener listener) {
        this.context = context.getApplicationContext();
        this.callsign = callsign;
        this.passcode = calculatePasscode(callsign);
        this.peerAnnouncementMessage = peerAnnouncementMessage;
        this.messageListener = listener;
    }

    /**
     * Calculate APRS passcode for a callsign
     * Algorithm from: http://www.aprs.org/aprs11/correctness.txt
     */
    private static int calculatePasscode(String callsign) {
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
     * Start APRS-IS service
     */
    public void start() {
        if (isRunning.get()) {
            Log.w(TAG, "APRS-IS service already running");
            return;
        }

        Log.i(TAG, "╔═══════════════════════════════════════════════════════╗");
        Log.i(TAG, "║ STARTING APRS-IS SERVICE                             ║");
        Log.i(TAG, "╠═══════════════════════════════════════════════════════╣");
        Log.i(TAG, "║ Server:   " + APRS_SERVER + ":" + APRS_PORT);
        Log.i(TAG, "║ Callsign: " + callsign);
        Log.i(TAG, "║ Passcode: " + passcode);
        Log.i(TAG, "║ Filter:   " + APRS_FILTER);
        Log.i(TAG, "║ Peer ID:  " + peerAnnouncementMessage);
        Log.i(TAG, "║ Interval: " + ANNOUNCE_INTERVAL_MINUTES + " minutes");
        Log.i(TAG, "╚═══════════════════════════════════════════════════════╝");

        isRunning.set(true);
        shouldReconnect.set(true);

        // Start listener thread (handles connection and receiving)
        listenerThread = new Thread(this::connectionLoop);
        listenerThread.setName("APRS-IS-Listener");
        listenerThread.start();

        // Start announcement scheduler
        // Note: First announcement is sent immediately after connection in connect()
        // Scheduler handles subsequent periodic announcements
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(
            this::sendAnnouncement,
            ANNOUNCE_INTERVAL_MINUTES,  // Wait full interval before first scheduled announcement
            ANNOUNCE_INTERVAL_MINUTES,
            TimeUnit.MINUTES
        );

        Log.i(TAG, "APRS-IS service started");
    }

    /**
     * Stop APRS-IS service
     */
    public void stop() {
        if (!isRunning.get()) {
            Log.w(TAG, "APRS-IS service not running");
            return;
        }

        Log.i(TAG, "Stopping APRS-IS service...");

        shouldReconnect.set(false);
        isRunning.set(false);

        // Stop scheduler
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                scheduler.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
            }
            scheduler = null;
        }

        // Stop listener thread
        if (listenerThread != null) {
            listenerThread.interrupt();
            try {
                listenerThread.join(5000);
            } catch (InterruptedException e) {
                // Ignore
            }
            listenerThread = null;
        }

        // Close connection
        closeConnection();

        Log.i(TAG, "APRS-IS service stopped");
    }

    /**
     * Connection loop - maintains connection and receives messages
     */
    private void connectionLoop() {
        while (shouldReconnect.get()) {
            try {
                connect();

                // Read messages
                String line;
                while ((line = in.readLine()) != null && isRunning.get()) {
                    handleIncomingMessage(line);
                }

                Log.w(TAG, "Connection closed by server");

            } catch (Exception e) {
                if (isRunning.get()) {
                    Log.e(TAG, "Connection error: " + e.getMessage());
                }
            } finally {
                closeConnection();
            }

            // Wait before reconnecting
            if (shouldReconnect.get()) {
                Log.i(TAG, "Reconnecting in 30 seconds...");
                try {
                    Thread.sleep(30000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }

    /**
     * Connect to APRS-IS server
     */
    private void connect() throws Exception {
        Log.i(TAG, "Connecting to " + APRS_SERVER + ":" + APRS_PORT + "...");

        socket = new Socket(APRS_SERVER, APRS_PORT);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
        out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII));

        // Read server banner
        String banner = in.readLine();
        Log.i(TAG, "Server: " + banner);

        // Login with filter
        String login = "user " + callsign + " pass " + passcode +
                      " vers Geogram-P2P 1.0 filter " + APRS_FILTER;
        Log.i(TAG, "Sending login: " + login);

        out.write(login);
        out.newLine();
        out.flush();

        // Read login response
        String loginResp = in.readLine();
        Log.i(TAG, "Login response: " + loginResp);

        if (loginResp != null && loginResp.contains("verified")) {
            Log.i(TAG, "╔═══════════════════════════════════════════════════════╗");
            Log.i(TAG, "║ ✓ CONNECTED TO APRS-IS NETWORK                       ║");
            Log.i(TAG, "╠═══════════════════════════════════════════════════════╣");
            Log.i(TAG, "║ Status:     AUTHENTICATED");
            Log.i(TAG, "║ Callsign:   " + callsign);
            Log.i(TAG, "║ Filter:     " + APRS_FILTER + " (packets FROM X1* callsigns)");
            Log.i(TAG, "║ Server:     " + APRS_SERVER);
            Log.i(TAG, "╚═══════════════════════════════════════════════════════╝");

            if (messageListener != null) {
                messageListener.onConnectionStateChanged(true);
            }

            // Send immediate announcement after successful connection
            // This ensures an announcement goes out every time the app starts
            Log.i(TAG, "Sending immediate announcement after connection...");
            sendAnnouncement();

        } else {
            throw new Exception("Login failed: " + loginResp);
        }
    }

    /**
     * Close APRS-IS connection
     */
    private void closeConnection() {
        boolean wasConnected = (socket != null && socket.isConnected());

        try {
            if (out != null) {
                out.close();
                out = null;
            }
        } catch (Exception e) {
            // Ignore
        }

        try {
            if (in != null) {
                in.close();
                in = null;
            }
        } catch (Exception e) {
            // Ignore
        }

        try {
            if (socket != null) {
                socket.close();
                socket = null;
            }
        } catch (Exception e) {
            // Ignore
        }

        if (wasConnected) {
            Log.w(TAG, "╔═══════════════════════════════════════════════════════╗");
            Log.w(TAG, "║ ✗ DISCONNECTED FROM APRS-IS NETWORK                  ║");
            Log.w(TAG, "╚═══════════════════════════════════════════════════════╝");
        }

        if (messageListener != null) {
            messageListener.onConnectionStateChanged(false);
        }
    }

    /**
     * Handle incoming APRS message
     */
    private void handleIncomingMessage(String message) {
        // Skip server messages (starting with #)
        if (message.startsWith("#")) {
            Log.d(TAG, "Server message: " + message);
            return;
        }

        // Log received APRS messages prominently
        Log.i(TAG, "═══════════════════════════════════════════════════════");
        Log.i(TAG, "RECEIVED APRS MESSAGE FROM NETWORK:");
        Log.i(TAG, "═══════════════════════════════════════════════════════");
        Log.i(TAG, "Full message content:");
        Log.i(TAG, message);
        Log.i(TAG, "───────────────────────────────────────────────────────");

        // Try to extract sender callsign
        if (message.contains(">")) {
            String sender = message.substring(0, message.indexOf(">"));
            Log.i(TAG, "From callsign: " + sender);
        }

        // Check if it's a peer announcement
        if (message.contains("p2p-")) {
            Log.i(TAG, "Type: P2P PEER ANNOUNCEMENT ✓");
            // Extract the peer ID for display
            int peerStart = message.indexOf("p2p-");
            if (peerStart != -1) {
                String afterP2p = message.substring(peerStart + 4);
                String peerId = afterP2p.split("\\s+")[0];
                Log.i(TAG, "Peer ID: " + peerId);
            }
        } else {
            Log.i(TAG, "Type: Regular APRS message (not P2P announcement)");
        }

        Log.i(TAG, "═══════════════════════════════════════════════════════");

        // Forward to listener
        if (messageListener != null) {
            messageListener.onAprsMessage(message);
        } else {
            Log.w(TAG, "WARNING: No message listener registered!");
        }
    }

    /**
     * Send P2P announcement to APRS-IS
     */
    private void sendAnnouncement() {
        if (!isRunning.get()) {
            Log.w(TAG, "Cannot send announcement - service not running");
            return;
        }

        if (out == null) {
            Log.w(TAG, "Cannot send announcement - not connected to APRS-IS");
            Log.w(TAG, "Waiting for connection to be established...");
            return;
        }

        try {
            // Format: CALLSIGN>X11GEO,TCPIP*:>message
            // This is a status message (starts with >) containing our P2P announcement
            // X11GEO = X1 (Geogram prefix) + 1GEO (Geogram identifier) = valid 6-char callsign
            String aprsMessage = callsign + ">X11GEO,TCPIP*:>" + peerAnnouncementMessage;

            Log.i(TAG, "");
            Log.i(TAG, "═══════════════════════════════════════════════════════");
            Log.i(TAG, "SENDING APRS MESSAGE TO NETWORK:");
            Log.i(TAG, "═══════════════════════════════════════════════════════");
            Log.i(TAG, "  Message: " + aprsMessage);
            Log.i(TAG, "  Callsign: " + callsign);
            Log.i(TAG, "  Peer ID: " + peerAnnouncementMessage);
            Log.i(TAG, "  Time: " + new java.util.Date().toString());
            Log.i(TAG, "───────────────────────────────────────────────────────");
            Log.i(TAG, "  Verify at: https://aprs.fi/#!call=a%2F" + callsign + "&timerange=3600&tail=3600");
            Log.i(TAG, "═══════════════════════════════════════════════════════");

            out.write(aprsMessage);
            out.newLine();
            out.flush();

            Log.i(TAG, "✓ ANNOUNCEMENT TRANSMITTED TO APRS-IS NETWORK");
            Log.i(TAG, "✓ Check aprs.fi in 1-2 minutes to verify message arrived");
            Log.i(TAG, "");

        } catch (Exception e) {
            Log.e(TAG, "");
            Log.e(TAG, "✗ FAILED TO SEND ANNOUNCEMENT: " + e.getMessage());
            Log.e(TAG, "✗ Connection may need to be re-established");
            Log.e(TAG, "");
            // Connection will be reset by the connection loop
        }
    }

    /**
     * Check if service is running
     */
    public boolean isRunning() {
        return isRunning.get();
    }

    /**
     * Check if connected to APRS-IS
     */
    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }
}

package offgrid.geogram.i2p;

import android.content.Context;
import offgrid.geogram.core.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Low-level SAM (Simple Anonymous Messaging) protocol implementation.
 *
 * The SAM bridge provides an application interface to I2P using a
 * text-based protocol similar to SMTP.
 *
 * SAM Protocol Basics:
 * - Connect to localhost:7656
 * - Send: HELLO VERSION MIN=3.0 MAX=3.3
 * - Receive: HELLO REPLY RESULT=OK VERSION=3.3
 * - Create session, establish streams, send datagrams
 */
public class SAMBridge {
    private static final String TAG = "SAMBridge";
    private static final String SAM_HOST = "127.0.0.1";
    private static final int SAM_PORT = 7656;
    private static final String SAM_VERSION_MIN = "3.0";
    private static final String SAM_VERSION_MAX = "3.3";

    private Context context;
    private Socket samSocket;
    private BufferedReader reader;
    private BufferedWriter writer;
    private boolean connected;
    private String sessionId;

    public SAMBridge(Context context) {
        this.context = context.getApplicationContext();
        this.connected = false;
    }

    /**
     * Connect to SAM bridge
     *
     * @return true if connection successful
     */
    public boolean connect() {
        if (connected) {
            Log.w(TAG, "Already connected to SAM bridge");
            return true;
        }

        try {
            Log.i(TAG, "Connecting to SAM bridge at " + SAM_HOST + ":" + SAM_PORT);

            // Connect to SAM bridge
            samSocket = new Socket(SAM_HOST, SAM_PORT);
            samSocket.setSoTimeout(30000); // 30 second timeout

            reader = new BufferedReader(
                new InputStreamReader(samSocket.getInputStream(), StandardCharsets.UTF_8)
            );
            writer = new BufferedWriter(
                new OutputStreamWriter(samSocket.getOutputStream(), StandardCharsets.UTF_8)
            );

            // Send HELLO handshake
            String helloCommand = "HELLO VERSION MIN=" + SAM_VERSION_MIN + " MAX=" + SAM_VERSION_MAX + "\n";
            writer.write(helloCommand);
            writer.flush();

            // Read response
            String response = reader.readLine();
            Log.d(TAG, "SAM HELLO response: " + response);

            if (response != null && response.contains("RESULT=OK")) {
                connected = true;
                Log.i(TAG, "Connected to SAM bridge");
                return true;
            } else {
                Log.e(TAG, "SAM HELLO failed: " + response);
                disconnect();
                return false;
            }

        } catch (IOException e) {
            Log.e(TAG, "Failed to connect to SAM bridge: " + e.getMessage());
            Log.e(TAG, "Make sure I2P router is running with SAM bridge enabled on port 7656");
            disconnect();
            return false;
        }
    }

    /**
     * Create a SAM session
     *
     * @param nickname Session nickname
     * @param destination Base64 destination (or "TRANSIENT" for temporary)
     * @param style Session style ("STREAM", "DATAGRAM", or "RAW")
     * @return true if session created successfully
     */
    public boolean createSession(String nickname, String destination, String style) {
        if (!connected) {
            Log.e(TAG, "Not connected to SAM bridge");
            return false;
        }

        try {
            // SESSION CREATE command
            String command = "SESSION CREATE " +
                "STYLE=" + style + " " +
                "ID=" + nickname + " " +
                "DESTINATION=" + destination + "\n";

            writer.write(command);
            writer.flush();

            // Read response
            String response = reader.readLine();
            Log.d(TAG, "SAM SESSION CREATE response: " + response);

            if (response != null && response.contains("RESULT=OK")) {
                sessionId = nickname;
                Log.i(TAG, "Created SAM session: " + nickname);
                return true;
            } else {
                Log.e(TAG, "SAM SESSION CREATE failed: " + response);
                return false;
            }

        } catch (IOException e) {
            Log.e(TAG, "Failed to create SAM session: " + e.getMessage());
            return false;
        }
    }

    /**
     * Create a STREAM session for HTTP communication
     *
     * @param nickname Session nickname
     * @param destination Base64 destination
     * @return true if successful
     */
    public boolean createStreamSession(String nickname, String destination) {
        return createSession(nickname, destination, "STREAM");
    }

    /**
     * Connect to remote I2P destination via STREAM
     *
     * @param destination Remote I2P destination (.b32.i2p or full base64)
     * @return Socket connected to remote destination, or null on failure
     */
    public Socket connectStream(String destination) {
        if (!connected || sessionId == null) {
            Log.e(TAG, "No active SAM session");
            return null;
        }

        try {
            // STREAM CONNECT command
            String command = "STREAM CONNECT " +
                "ID=" + sessionId + " " +
                "DESTINATION=" + destination + " " +
                "SILENT=false\n";

            writer.write(command);
            writer.flush();

            // Read response
            String response = reader.readLine();
            Log.d(TAG, "SAM STREAM CONNECT response: " + response);

            if (response != null && response.contains("RESULT=OK")) {
                Log.i(TAG, "Connected to I2P destination: " + destination);
                // Return the existing socket for streaming
                return samSocket;
            } else {
                Log.e(TAG, "SAM STREAM CONNECT failed: " + response);
                return null;
            }

        } catch (IOException e) {
            Log.e(TAG, "Failed to connect stream: " + e.getMessage());
            return null;
        }
    }

    /**
     * Disconnect from SAM bridge
     */
    public void disconnect() {
        Log.i(TAG, "Disconnecting from SAM bridge");

        try {
            if (writer != null) {
                writer.close();
            }
            if (reader != null) {
                reader.close();
            }
            if (samSocket != null) {
                samSocket.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error closing SAM connection: " + e.getMessage());
        } finally {
            connected = false;
            sessionId = null;
            writer = null;
            reader = null;
            samSocket = null;
        }
    }

    /**
     * Check if connected to SAM bridge
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * Get active session ID
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * Send raw SAM command and get response
     *
     * @param command SAM command string
     * @return Response string, or null on error
     */
    public String sendCommand(String command) {
        if (!connected) {
            Log.e(TAG, "Not connected to SAM bridge");
            return null;
        }

        try {
            writer.write(command + "\n");
            writer.flush();

            String response = reader.readLine();
            Log.d(TAG, "SAM command: " + command);
            Log.d(TAG, "SAM response: " + response);

            return response;
        } catch (IOException e) {
            Log.e(TAG, "Failed to send SAM command: " + e.getMessage());
            return null;
        }
    }
}

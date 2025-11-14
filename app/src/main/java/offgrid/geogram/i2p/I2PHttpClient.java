package offgrid.geogram.i2p;

import android.content.Context;
import offgrid.geogram.core.Log;

import net.i2p.client.streaming.I2PSocket;
import net.i2p.client.streaming.I2PSocketManager;
import net.i2p.data.Destination;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/**
 * HTTP client that routes requests through embedded I2P router.
 *
 * Uses I2PSocket from the embedded router to establish connections
 * to remote I2P destinations and sends HTTP requests through them.
 *
 * Key differences from regular HTTP:
 * - Higher latency (30-90 seconds for first connection)
 * - Longer timeouts required
 * - Destination addresses are .b32.i2p or full Base64
 */
public class I2PHttpClient {
    private static final String TAG = "I2P/HttpClient";
    private static final int I2P_CONNECT_TIMEOUT = 90000; // 90 seconds
    private static final int I2P_READ_TIMEOUT = 60000; // 60 seconds

    private Context context;
    private I2PService i2pService;

    public I2PHttpClient(Context context) {
        this.context = context.getApplicationContext();
        this.i2pService = I2PService.getInstance(context);
    }

    /**
     * Perform HTTP GET request over I2P
     *
     * @param i2pDestination Remote I2P destination (.b32.i2p or Base64)
     * @param path HTTP path (e.g., "/api/profile")
     * @return Response body as string, or null on error
     */
    public String get(String i2pDestination, String path) {
        return request("GET", i2pDestination, path, null);
    }

    /**
     * Perform HTTP POST request over I2P
     *
     * @param i2pDestination Remote I2P destination
     * @param path HTTP path
     * @param body Request body
     * @return Response body as string, or null on error
     */
    public String post(String i2pDestination, String path, String body) {
        return request("POST", i2pDestination, path, body);
    }

    /**
     * Download file over I2P
     *
     * @param i2pDestination Remote I2P destination
     * @param path File path
     * @return File data as byte array, or null on error
     */
    public byte[] downloadFile(String i2pDestination, String path) {
        if (!i2pService.isI2PReady()) {
            Log.e(TAG, "I2P not ready");
            return null;
        }

        I2PSocketManager socketManager = i2pService.getSocketManager();
        if (socketManager == null) {
            Log.e(TAG, "I2P socket manager not available");
            return null;
        }

        I2PSocket socket = null;
        try {
            Log.i(TAG, "Downloading file via I2P: " + i2pDestination + path);

            // Parse destination
            Destination dest = parseDestination(i2pDestination);
            if (dest == null) {
                Log.e(TAG, "Invalid I2P destination: " + i2pDestination);
                return null;
            }

            // Connect to I2P destination
            socket = socketManager.connect(dest);
            if (socket == null) {
                Log.e(TAG, "Failed to connect to I2P destination");
                return null;
            }

            socket.setReadTimeout(I2P_READ_TIMEOUT);

            // Send HTTP GET request
            BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)
            );

            writer.write("GET " + path + " HTTP/1.1\r\n");
            writer.write("Host: " + i2pDestination + "\r\n");
            writer.write("Connection: close\r\n");
            writer.write("\r\n");
            writer.flush();

            // Read response headers
            BufferedReader headerReader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
            );

            String statusLine = headerReader.readLine();
            if (statusLine == null || !statusLine.contains("200")) {
                Log.e(TAG, "HTTP error: " + statusLine);
                return null;
            }

            // Skip headers
            String line;
            while ((line = headerReader.readLine()) != null && !line.isEmpty()) {
                // Skip header lines
            }

            // Read binary body
            InputStream inputStream = socket.getInputStream();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int bytesRead;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            byte[] fileData = outputStream.toByteArray();
            Log.i(TAG, "Downloaded " + fileData.length + " bytes via I2P");

            return fileData;

        } catch (Exception e) {
            Log.e(TAG, "I2P file download failed: " + e.getMessage());
            e.printStackTrace();
            return null;
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing socket: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Internal method to perform HTTP request over I2P
     */
    private String request(String method, String i2pDestination, String path, String body) {
        if (!i2pService.isI2PReady()) {
            Log.e(TAG, "I2P not ready");
            return null;
        }

        I2PSocketManager socketManager = i2pService.getSocketManager();
        if (socketManager == null) {
            Log.e(TAG, "I2P socket manager not available");
            return null;
        }

        I2PSocket socket = null;
        try {
            Log.i(TAG, "Sending " + method + " request via I2P: " + i2pDestination + path);

            // Parse destination
            Destination dest = parseDestination(i2pDestination);
            if (dest == null) {
                Log.e(TAG, "Invalid I2P destination: " + i2pDestination);
                return null;
            }

            // Connect to I2P destination
            socket = socketManager.connect(dest);
            if (socket == null) {
                Log.e(TAG, "Failed to connect to I2P destination");
                return null;
            }

            socket.setReadTimeout(I2P_READ_TIMEOUT);

            // Build and send HTTP request
            BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)
            );

            writer.write(method + " " + path + " HTTP/1.1\r\n");
            writer.write("Host: " + i2pDestination + "\r\n");
            writer.write("User-Agent: Geogram-I2P/1.0\r\n");

            if (body != null && !body.isEmpty()) {
                writer.write("Content-Type: application/json\r\n");
                writer.write("Content-Length: " + body.getBytes(StandardCharsets.UTF_8).length + "\r\n");
            }

            writer.write("Connection: close\r\n");
            writer.write("\r\n");

            if (body != null && !body.isEmpty()) {
                writer.write(body);
            }

            writer.flush();

            // Read HTTP response
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
            );

            // Read status line
            String statusLine = reader.readLine();
            if (statusLine == null) {
                Log.e(TAG, "No response from I2P destination");
                return null;
            }

            Log.d(TAG, "I2P HTTP response: " + statusLine);

            // Read headers
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                // Skip header lines
                Log.d(TAG, "Header: " + line);
            }

            // Read response body
            StringBuilder responseBody = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                responseBody.append(line).append("\n");
            }

            String response = responseBody.toString().trim();

            if (statusLine.contains("200")) {
                Log.i(TAG, "I2P HTTP request successful (" + response.length() + " bytes)");
                return response;
            } else {
                Log.e(TAG, "I2P HTTP request failed: " + statusLine);
                return null;
            }

        } catch (Exception e) {
            Log.e(TAG, "I2P HTTP request failed: " + e.getMessage());
            e.printStackTrace();
            return null;
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing socket: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Parse I2P destination string to Destination object
     *
     * @param destString .b32.i2p or full Base64 destination
     * @return Destination object or null if invalid
     */
    private Destination parseDestination(String destString) {
        try {
            // If it's a .b32.i2p address, need to look it up
            // For now, we'll require full Base64 destinations
            // TODO: Add naming service lookup for .b32.i2p addresses

            if (destString.endsWith(".b32.i2p")) {
                Log.w(TAG, "b32.i2p lookup not yet implemented, need full Base64 destination");
                return null;
            }

            // Try to parse as full Base64 destination
            Destination dest = new Destination();
            dest.fromBase64(destString);
            return dest;

        } catch (Exception e) {
            Log.e(TAG, "Failed to parse destination: " + e.getMessage());
            return null;
        }
    }

    /**
     * Test I2P connection to a destination
     *
     * @param i2pDestination I2P destination to test
     * @return true if connection successful
     */
    public boolean testConnection(String i2pDestination) {
        Log.i(TAG, "Testing I2P connection to: " + i2pDestination);

        String response = get(i2pDestination, "/");

        if (response != null) {
            Log.i(TAG, "I2P connection test successful");
            return true;
        } else {
            Log.w(TAG, "I2P connection test failed");
            return false;
        }
    }
}

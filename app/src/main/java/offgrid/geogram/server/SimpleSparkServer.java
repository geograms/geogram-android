package offgrid.geogram.server;

import static offgrid.geogram.core.Messages.log;
import static spark.Spark.*;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import offgrid.geogram.core.Log;
import offgrid.geogram.util.JsonUtils;
import offgrid.geogram.wifi.comm.WiFiReceiver;
import offgrid.geogram.wifi.messages.Message;

import java.net.ServerSocket;

public class SimpleSparkServer implements Runnable {

    private static final String TAG_ID = "offgrid-server";
    private static final int SERVER_PORT = 5050;
    private static final Gson gson = new Gson();
    private volatile boolean isRunning = false;

    @Override
    public void run() {
        startServer();
    }

    // Start the Spark server
    public synchronized void startServer() {
        if (isRunning) {
            Log.i(TAG_ID, "Server is already running.");
            return;
        }

        ipAddress("0.0.0.0"); // Allow access from all network interfaces
        port(SERVER_PORT); // Set the port to SERVER_PORT

        // Define the root route (/) to handle JSON POST requests
        post("/", (req, res) -> {
            res.type("application/json");
            String text = req.body();
            Log.d(TAG_ID, "Received POST request at root route: " + text);
            Message message;
            try {
                message = gson.fromJson(text, Message.class);
            } catch (Exception e) {
                message = null;
            }

            if (message == null) {
                res.status(400);
                return gson.toJson(createErrorResponse("Invalid JSON input"));
            }

            Message reply = WiFiReceiver.processReceivedMessage(message);
            if (reply != null) {
                // Send back a reply JSON
                return JsonUtils.convertToJsonText(reply);
            } else {
                res.status(400);
                return gson.toJson(createErrorResponse("Invalid message input"));
            }
        });

        // Define a GET route for handling normal HTTP requests
        get("/", (req, res) -> {
            res.type("text/html");

            log(TAG_ID, "Received GET request at root route.");

            // Simple HTML response
            return "<html>" +
                    "<body>" +
                    "<h1>Welcome to SimpleSparkServer</h1>" +
                    "<p>The server is up and running.</p>" +
                    "</body>" +
                    "</html>";
        });

        log(TAG_ID, "Server is running on http://localhost:" + SERVER_PORT + "/");
        isRunning = true;
    }

    // Stop the Spark server
    public synchronized boolean stopServer() {
        if (!isRunning) {
            Log.i(TAG_ID, "Server is not running.");
            return true;
        }

        stop(); // Stop the Spark server
        isRunning = false;
        Log.i(TAG_ID, "Server stop initiated.");

        // Wait for Spark to completely release the port
        long startTime = System.currentTimeMillis();
        while (!isPortAvailable(SERVER_PORT)) {
            if (System.currentTimeMillis() - startTime > 5000) { // Timeout after 5 seconds
                Log.e(TAG_ID, "Port " + SERVER_PORT + " was not released within 5 seconds.");
                return false;
            }
            try {
                Thread.sleep(100); // Small delay to ensure complete shutdown
            } catch (InterruptedException e) {
                Log.e(TAG_ID, "Interrupted while waiting for server to stop");
            }
        }

        Log.i(TAG_ID, "Server has been stopped and port is released.");
        return true;
    }

    // Check if a port is available
    private boolean isPortAvailable(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            serverSocket.setReuseAddress(true);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // Helper method to create an error response JSON object
    private JsonObject createErrorResponse(String message) {
        JsonObject errorResponse = new JsonObject();
        errorResponse.addProperty("error", message);
        return errorResponse;
    }
}

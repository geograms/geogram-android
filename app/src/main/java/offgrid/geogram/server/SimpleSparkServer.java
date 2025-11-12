package offgrid.geogram.server;

import static offgrid.geogram.core.Messages.log;
import static spark.Spark.*;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import offgrid.geogram.ble.BluetoothSender;
import offgrid.geogram.core.Central;
import offgrid.geogram.core.Log;
import offgrid.geogram.util.JsonUtils;
// Removed (legacy Google Play Services code) - import offgrid.geogram.old.wifi.comm.WiFiReceiver;
// Removed (legacy Google Play Services code) - import offgrid.geogram.old.wifi.messages.Message;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class SimpleSparkServer implements Runnable {

    private static final String TAG_ID = "offgrid-server";
    private static final int SERVER_PORT = 45678;
    private static final String BUILD_TIMESTAMP = "2025-01-12T00:10:00Z"; // Updated on each build
    private static final String API_VERSION = "0.4.4"; // Increment on API changes
    private static final Gson gson = new Gson();
    private volatile boolean isRunning = false;
    private android.content.Context context;

    public SimpleSparkServer() {
        this.context = null;
    }

    public SimpleSparkServer(android.content.Context context) {
        this.context = context;
    }

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

        // Define a GET route for handling normal HTTP requests
        get("/", (req, res) -> {
            res.type("text/html");

            log(TAG_ID, "Received GET request at root route.");

            // Simple HTML response
            return "<html>" +
                    "<body>" +
                    "<h1>Geogram HTTP API Server</h1>" +
                    "<p>The server is running on port " + SERVER_PORT + "</p>" +
                    "<h2>Available Endpoints:</h2>" +
                    "<ul>" +
                    "<li>POST /api/ble/send - Send a BLE message</li>" +
                    "<li>GET /api/logs - Get recent log messages</li>" +
                    "<li>GET /api/logs/file - Get log file contents</li>" +
                    "<li>GET /api/status - Get server status</li>" +
                    "</ul>" +
                    "</body>" +
                    "</html>";
        });

        // API endpoint to send BLE messages
        post("/api/ble/send", (req, res) -> {
            res.type("application/json");

            try {
                // Parse JSON request body
                String body = req.body();
                JsonObject jsonRequest = gson.fromJson(body, JsonObject.class);

                if (jsonRequest == null || !jsonRequest.has("message")) {
                    res.status(400);
                    return gson.toJson(createErrorResponse("Missing 'message' field in request body"));
                }

                String message = jsonRequest.get("message").getAsString();

                if (message == null || message.isEmpty()) {
                    res.status(400);
                    return gson.toJson(createErrorResponse("Message cannot be empty"));
                }

                // Send the BLE message
                if (context == null) {
                    res.status(503);
                    return gson.toJson(createErrorResponse("Server context not initialized"));
                }

                BluetoothSender sender = BluetoothSender.getInstance(context);
                sender.sendMessage(message);

                Log.i(TAG_ID, "API: BLE message sent via HTTP: " + message);

                // Create success response
                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.addProperty("message", "BLE message queued for broadcast");
                response.addProperty("sent_message", message);

                res.status(200);
                return gson.toJson(response);

            } catch (Exception e) {
                Log.e(TAG_ID, "Error sending BLE message via API: " + e.getMessage());
                res.status(500);
                return gson.toJson(createErrorResponse("Error: " + e.getMessage()));
            }
        });

        // API endpoint to get recent logs
        get("/api/logs", (req, res) -> {
            res.type("application/json");

            try {
                // Get limit parameter (default 100, max 5000)
                String limitParam = req.queryParams("limit");
                int limit = 100;
                if (limitParam != null) {
                    try {
                        limit = Integer.parseInt(limitParam);
                        limit = Math.min(limit, 5000); // Cap at 5000
                        limit = Math.max(limit, 1);    // Min 1
                    } catch (NumberFormatException e) {
                        // Keep default
                    }
                }

                // Get the logs
                List<String> allLogs = new ArrayList<>(Log.logMessages);
                List<String> recentLogs;

                if (allLogs.size() <= limit) {
                    recentLogs = allLogs;
                } else {
                    // Get the last 'limit' messages
                    recentLogs = allLogs.subList(allLogs.size() - limit, allLogs.size());
                }

                // Create response
                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.addProperty("count", recentLogs.size());
                response.addProperty("total_logs", allLogs.size());
                response.add("logs", gson.toJsonTree(recentLogs));

                log(TAG_ID, "API: Returned " + recentLogs.size() + " log messages");

                res.status(200);
                return gson.toJson(response);

            } catch (Exception e) {
                Log.e(TAG_ID, "Error retrieving logs via API: " + e.getMessage());
                res.status(500);
                return gson.toJson(createErrorResponse("Error: " + e.getMessage()));
            }
        });

        // API endpoint to get log file contents
        get("/api/logs/file", (req, res) -> {
            res.type("application/json");

            try {
                String logFilePath = Log.getLogFilePath();

                if (logFilePath.equals("Not initialized")) {
                    res.status(503);
                    return gson.toJson(createErrorResponse("Log file not initialized yet"));
                }

                File logFile = new File(logFilePath);

                if (!logFile.exists()) {
                    res.status(404);
                    return gson.toJson(createErrorResponse("Log file not found"));
                }

                // Read the file
                String content = new String(Files.readAllBytes(logFile.toPath()));

                // Get tail parameter to return only last N lines
                String tailParam = req.queryParams("tail");
                if (tailParam != null) {
                    try {
                        int tailLines = Integer.parseInt(tailParam);
                        String[] lines = content.split("\n");
                        if (lines.length > tailLines) {
                            // Get last N lines
                            StringBuilder sb = new StringBuilder();
                            for (int i = lines.length - tailLines; i < lines.length; i++) {
                                sb.append(lines[i]).append("\n");
                            }
                            content = sb.toString();
                        }
                    } catch (NumberFormatException e) {
                        // Ignore, return full content
                    }
                }

                // Create response
                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.addProperty("file_path", logFilePath);
                response.addProperty("file_size", logFile.length());
                response.addProperty("content", content);

                log(TAG_ID, "API: Returned log file (" + logFile.length() + " bytes)");

                res.status(200);
                return gson.toJson(response);

            } catch (IOException e) {
                Log.e(TAG_ID, "Error reading log file via API: " + e.getMessage());
                res.status(500);
                return gson.toJson(createErrorResponse("Error reading log file: " + e.getMessage()));
            } catch (Exception e) {
                Log.e(TAG_ID, "Error in log file endpoint: " + e.getMessage());
                res.status(500);
                return gson.toJson(createErrorResponse("Error: " + e.getMessage()));
            }
        });

        // API endpoint to get server status
        get("/api/status", (req, res) -> {
            res.type("application/json");

            try {
                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.addProperty("server", "Geogram HTTP API");
                response.addProperty("port", SERVER_PORT);
                response.addProperty("version", API_VERSION);
                response.addProperty("build_timestamp", BUILD_TIMESTAMP);
                response.addProperty("running", true);

                res.status(200);
                return gson.toJson(response);

            } catch (Exception e) {
                res.status(500);
                return gson.toJson(createErrorResponse("Error: " + e.getMessage()));
            }
        });

        log(TAG_ID, "Server is running on http://0.0.0.0:" + SERVER_PORT + "/");
        log(TAG_ID, "API endpoints available at /api/ble/send, /api/logs, /api/logs/file, /api/status");
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

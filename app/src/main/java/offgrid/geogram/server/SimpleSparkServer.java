package offgrid.geogram.server;

import static offgrid.geogram.core.Messages.log;
import static spark.Spark.*;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import offgrid.geogram.ble.BluetoothSender;
import offgrid.geogram.core.Central;
import offgrid.geogram.core.Log;
import offgrid.geogram.util.JsonUtils;
import offgrid.geogram.relay.RelayStorage;
import offgrid.geogram.relay.RelayMessage;
import offgrid.geogram.database.DatabaseMessages;
import offgrid.geogram.apps.chat.ChatMessage;
import offgrid.geogram.devices.DeviceManager;
import offgrid.geogram.devices.Device;
import offgrid.geogram.devices.EventConnected;
import offgrid.geogram.models.Collection;
import offgrid.geogram.models.CollectionFile;
import offgrid.geogram.models.CollectionSecurity;
import offgrid.geogram.util.CollectionLoader;
// Removed (legacy Google Play Services code) - import offgrid.geogram.old.wifi.comm.WiFiReceiver;
// Removed (legacy Google Play Services code) - import offgrid.geogram.old.wifi.messages.Message;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.ByteArrayOutputStream;

public class SimpleSparkServer implements Runnable {

    private static final String TAG_ID = "offgrid-server";
    private static final int SERVER_PORT = 45678;
    private static final String BUILD_TIMESTAMP = "2025-01-12T07:00:00Z"; // Updated on each build
    private static final String API_VERSION = "0.5.8"; // Increment on API changes
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
                    "<p>API Version: " + API_VERSION + "</p>" +
                    "<h2>Available Endpoints:</h2>" +
                    "<h3>General</h3>" +
                    "<ul>" +
                    "<li>GET /api/status - Get server status</li>" +
                    "</ul>" +
                    "<h3>BLE Messaging</h3>" +
                    "<ul>" +
                    "<li>POST /api/ble/send - Send a BLE message</li>" +
                    "</ul>" +
                    "<h3>Logs</h3>" +
                    "<ul>" +
                    "<li>GET /api/logs - Get recent log messages (supports ?filter= and ?limit=)</li>" +
                    "<li>GET /api/logs/file - Get log file contents</li>" +
                    "</ul>" +
                    "<h3>Relay Messages</h3>" +
                    "<ul>" +
                    "<li>POST /api/relay/send - Send a message to relay storage</li>" +
                    "<li>GET /api/relay/inbox - Get messages from relay inbox</li>" +
                    "<li>GET /api/relay/outbox - Get messages from relay outbox</li>" +
                    "<li>GET /api/relay/message/:messageId - Get specific message by ID</li>" +
                    "<li>DELETE /api/relay/message/:messageId - Delete message from relay</li>" +
                    "</ul>" +
                    "<h3>Relay Sync (WiFi-based)</h3>" +
                    "<ul>" +
                    "<li>POST /api/relay/sync/inventory - Receive inventory from remote device</li>" +
                    "<li>POST /api/relay/sync/request - Receive message request from remote device</li>" +
                    "<li>POST /api/relay/sync/message - Receive relay message from remote device</li>" +
                    "</ul>" +
                    "<h3>Devices</h3>" +
                    "<ul>" +
                    "<li>GET /api/devices/nearby - List nearby devices detected via BLE</li>" +
                    "</ul>" +
                    "<h3>Group Messages</h3>" +
                    "<ul>" +
                    "<li>GET /api/groups - List all conversation groups</li>" +
                    "<li>GET /api/groups/:callsign/messages - Get messages for a group/conversation</li>" +
                    "<li>POST /api/groups/:callsign/messages - Send message to a group</li>" +
                    "<li>GET /api/groups/:callsign/info - Get group/conversation info</li>" +
                    "</ul>" +
                    "<h3>Profile</h3>" +
                    "<ul>" +
                    "<li>GET /api/profile - Get device profile (nickname, description)</li>" +
                    "<li>GET /api/profile/picture - Get profile picture</li>" +
                    "</ul>" +
                    "<h3>Collections</h3>" +
                    "<ul>" +
                    "<li>GET /api/collections - List all public collections</li>" +
                    "<li>GET /api/collections/count - Get count of public collections</li>" +
                    "<li>GET /api/collections/:npub - Get collection metadata</li>" +
                    "<li>GET /api/collections/:npub/files - Browse collection files</li>" +
                    "<li>GET /api/collections/:npub/file/* - Download a specific file</li>" +
                    "<li>GET /api/collections/:npub/thumbnail/* - Get image thumbnail (200x200)</li>" +
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

                // Handle message received via WiFi
                if (context == null) {
                    res.status(503);
                    return gson.toJson(createErrorResponse("Server context not initialized"));
                }

                // Extract sender info from request
                String senderIp = req.ip();
                Log.i(TAG_ID, "API: Message received via WiFi from " + senderIp + ": " + message);

                // Extract sender's callsign from JSON payload (preferred method)
                String senderCallsign = null;
                try {
                    if (jsonRequest.has("callsign") && !jsonRequest.get("callsign").isJsonNull()) {
                        senderCallsign = jsonRequest.get("callsign").getAsString();
                        Log.i(TAG_ID, "Extracted callsign from payload: " + senderCallsign);
                    }
                } catch (Exception e) {
                    Log.w(TAG_ID, "Failed to extract callsign from payload: " + e.getMessage());
                }

                // Fallback: Try to find sender by IP lookup if callsign not in payload
                if (senderCallsign == null || senderCallsign.isEmpty()) {
                    offgrid.geogram.wifi.WiFiDiscoveryService wifiService =
                        offgrid.geogram.wifi.WiFiDiscoveryService.getInstance(context);

                    for (java.util.Map.Entry<String, String> entry : wifiService.getDiscoveredDevices().entrySet()) {
                        if (entry.getValue().equals(senderIp)) {
                            senderCallsign = entry.getKey();
                            Log.i(TAG_ID, "Found callsign via IP lookup: " + senderCallsign);
                            break;
                        }
                    }
                }

                // Last resort: If we still can't find the sender, use IP-based identifier
                if (senderCallsign == null || senderCallsign.isEmpty()) {
                    senderCallsign = "WIFI-" + senderIp.replace(".", "-");
                    Log.w(TAG_ID, "Using IP-based fallback identifier: " + senderCallsign);
                }

                Log.i(TAG_ID, "WiFi message from " + senderCallsign + " (" + senderIp + "): " + message);

                // Save WiFi message to database
                offgrid.geogram.apps.chat.ChatMessage wifiMessage =
                    new offgrid.geogram.apps.chat.ChatMessage(senderCallsign, message);
                wifiMessage.setWrittenByMe(false); // NOT written by me - received from another device
                wifiMessage.setTimestamp(System.currentTimeMillis());
                wifiMessage.setMessageType(offgrid.geogram.apps.chat.ChatMessageType.WIFI);
                wifiMessage.addChannel(offgrid.geogram.apps.chat.ChatMessageType.WIFI);
                wifiMessage.setDestinationId("ANY");
                offgrid.geogram.database.DatabaseMessages.getInstance().add(wifiMessage);
                offgrid.geogram.database.DatabaseMessages.getInstance().flushNow();

                Log.i(TAG_ID, "Saved WiFi message to database with WIFI channel tag");

                // Immediately trigger UI refresh to show WiFi message without polling delay
                if (offgrid.geogram.core.Central.getInstance() != null &&
                    offgrid.geogram.core.Central.getInstance().broadcastChatFragment != null) {
                    offgrid.geogram.core.Central.getInstance().broadcastChatFragment.refreshMessagesFromDatabase();
                    Log.i(TAG_ID, "Triggered immediate UI refresh for WiFi message");
                }

                // Check if we should show notification (considers both foreground state and chat visibility)
                boolean shouldShowNotification = offgrid.geogram.apps.chat.ChatNotificationManager.shouldShowNotification(context);

                if (!shouldShowNotification) {
                    // Chat is visible in foreground - mark message as read immediately
                    Log.i(TAG_ID, "Chat is visible in foreground, marking WiFi message as read immediately");
                    wifiMessage.setRead(true);
                    offgrid.geogram.database.DatabaseMessages.getInstance().flushNow();
                } else {
                    // App in background or chat not visible - update counter and show notification
                    offgrid.geogram.MainActivity mainActivity = offgrid.geogram.MainActivity.getInstance();
                    if (mainActivity != null) {
                        mainActivity.runOnUiThread(() -> {
                            mainActivity.updateChatCount();
                            Log.i(TAG_ID, "Updated chat counter badge for WiFi message");
                        });
                    }

                    // Show Android notification
                    Log.i(TAG_ID, "Showing notification for WiFi message");
                    offgrid.geogram.apps.chat.ChatNotificationManager.getInstance(context)
                        .showUnreadMessagesNotification();
                }

                // DON'T rebroadcast via BLE - this creates duplicate messages
                // WiFi messages should stay on WiFi, BLE messages on BLE
                // If sender wanted BLE coverage, they would send via BLE too

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

                // Get filter parameter (case-insensitive keyword search)
                String filter = req.queryParams("filter");

                // Get the logs
                List<String> allLogs = new ArrayList<>(Log.logMessages);
                List<String> filteredLogs = allLogs;

                // Apply filter if provided
                if (filter != null && !filter.isEmpty()) {
                    String filterLower = filter.toLowerCase();
                    filteredLogs = new ArrayList<>();
                    for (String logEntry : allLogs) {
                        if (logEntry.toLowerCase().contains(filterLower)) {
                            filteredLogs.add(logEntry);
                        }
                    }
                }

                // Apply limit
                List<String> recentLogs;
                if (filteredLogs.size() <= limit) {
                    recentLogs = filteredLogs;
                } else {
                    // Get the last 'limit' messages
                    recentLogs = filteredLogs.subList(filteredLogs.size() - limit, filteredLogs.size());
                }

                // Create response
                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.addProperty("count", recentLogs.size());
                response.addProperty("total_logs", allLogs.size());
                response.addProperty("filtered_count", filteredLogs.size());
                if (filter != null && !filter.isEmpty()) {
                    response.addProperty("filter", filter);
                }
                response.add("logs", gson.toJsonTree(recentLogs));

                log(TAG_ID, "API: Returned " + recentLogs.size() + " log messages" +
                    (filter != null ? " (filtered by: " + filter + ")" : ""));

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

                // Include device callsign for WiFi discovery
                if (context != null && Central.getInstance() != null && Central.getInstance().getSettings() != null) {
                    String callsign = Central.getInstance().getSettings().getCallsign();
                    if (callsign != null && !callsign.isEmpty()) {
                        response.addProperty("callsign", callsign);
                    }
                }

                res.status(200);
                return gson.toJson(response);

            } catch (Exception e) {
                res.status(500);
                return gson.toJson(createErrorResponse("Error: " + e.getMessage()));
            }
        });

        // API endpoint for relay ping (used to check device reachability via relay)
        get("/api/ping", (req, res) -> {
            res.type("application/json");

            try {
                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.addProperty("pong", true);
                response.addProperty("timestamp", System.currentTimeMillis());

                res.status(200);
                return gson.toJson(response);

            } catch (Exception e) {
                res.status(500);
                return gson.toJson(createErrorResponse("Error: " + e.getMessage()));
            }
        });

        // ========== RELAY MESSAGE ENDPOINTS ==========

        // POST /api/relay/send - Send a message to relay storage
        post("/api/relay/send", (req, res) -> {
            res.type("application/json");

            try {
                if (context == null) {
                    res.status(503);
                    return gson.toJson(createErrorResponse("Server context not initialized"));
                }

                // Parse JSON request body
                String body = req.body();
                JsonObject jsonRequest = gson.fromJson(body, JsonObject.class);

                if (jsonRequest == null || !jsonRequest.has("recipient") || !jsonRequest.has("message")) {
                    res.status(400);
                    return gson.toJson(createErrorResponse("Missing 'recipient' or 'message' field"));
                }

                String recipient = jsonRequest.get("recipient").getAsString();
                String messageContent = jsonRequest.get("message").getAsString();
                String messageId = jsonRequest.has("messageId") ?
                    jsonRequest.get("messageId").getAsString() : null;

                if (recipient == null || recipient.isEmpty() || messageContent == null || messageContent.isEmpty()) {
                    res.status(400);
                    return gson.toJson(createErrorResponse("Recipient and message cannot be empty"));
                }

                // Get local device callsign
                String fromCallsign = null;
                try {
                    if (Central.getInstance() != null && Central.getInstance().getSettings() != null) {
                        fromCallsign = Central.getInstance().getSettings().getIdDevice();
                    }
                } catch (Exception e) {
                    Log.w(TAG_ID, "Could not get local callsign: " + e.getMessage());
                }

                if (fromCallsign == null || fromCallsign.isEmpty()) {
                    res.status(500);
                    return gson.toJson(createErrorResponse("Could not determine local device callsign"));
                }

                // Create relay message
                RelayMessage relayMsg = new RelayMessage();
                relayMsg.setFromCallsign(fromCallsign);
                relayMsg.setToCallsign(recipient);
                relayMsg.setContent(messageContent);
                relayMsg.setTimestamp(System.currentTimeMillis() / 1000); // Unix timestamp in seconds

                // Generate message ID if not provided
                if (messageId != null && !messageId.isEmpty()) {
                    relayMsg.setId(messageId);
                } else {
                    // Generate unique ID: SENDER-TIMESTAMP
                    String generatedId = fromCallsign + "-" + System.currentTimeMillis();
                    relayMsg.setId(generatedId);
                }

                // Save to outbox
                RelayStorage storage = new RelayStorage(context);
                boolean saved = storage.saveMessage(relayMsg, "outbox");

                if (!saved) {
                    res.status(500);
                    return gson.toJson(createErrorResponse("Failed to save message to outbox"));
                }

                Log.i(TAG_ID, "API: Relay message saved to outbox for " + recipient);

                // Create success response
                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.addProperty("message", "Relay message saved to outbox");
                response.addProperty("messageId", relayMsg.getId());
                response.addProperty("recipient", recipient);

                res.status(200);
                return gson.toJson(response);

            } catch (Exception e) {
                Log.e(TAG_ID, "Error saving relay message: " + e.getMessage());
                res.status(500);
                return gson.toJson(createErrorResponse("Error: " + e.getMessage()));
            }
        });

        // GET /api/relay/inbox - Get messages from relay inbox
        get("/api/relay/inbox", (req, res) -> {
            res.type("application/json");

            try {
                if (context == null) {
                    res.status(503);
                    return gson.toJson(createErrorResponse("Server context not initialized"));
                }

                // Get limit parameter (default 50)
                String limitParam = req.queryParams("limit");
                int limit = 50;
                if (limitParam != null) {
                    try {
                        limit = Integer.parseInt(limitParam);
                        limit = Math.max(limit, 1);
                    } catch (NumberFormatException e) {
                        // Keep default
                    }
                }

                RelayStorage storage = new RelayStorage(context);
                List<String> messageIds = storage.listMessagesSorted("inbox", true); // newest first

                // Apply limit
                if (messageIds.size() > limit) {
                    messageIds = messageIds.subList(0, limit);
                }

                // Load full message objects
                List<JsonObject> messages = new ArrayList<>();
                for (String msgId : messageIds) {
                    RelayMessage msg = storage.getMessage(msgId, "inbox");
                    if (msg != null) {
                        JsonObject msgJson = new JsonObject();
                        msgJson.addProperty("id", msg.getId());
                        msgJson.addProperty("from", msg.getFromCallsign());
                        msgJson.addProperty("to", msg.getToCallsign());
                        msgJson.addProperty("content", msg.getContent());
                        msgJson.addProperty("timestamp", msg.getTimestamp());
                        msgJson.addProperty("type", msg.getType());
                        msgJson.addProperty("priority", msg.getPriority());
                        messages.add(msgJson);
                    }
                }

                // Create response
                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.addProperty("count", messages.size());
                response.addProperty("folder", "inbox");
                response.add("messages", gson.toJsonTree(messages));

                Log.i(TAG_ID, "API: Returned " + messages.size() + " inbox messages");

                res.status(200);
                return gson.toJson(response);

            } catch (Exception e) {
                Log.e(TAG_ID, "Error retrieving inbox messages: " + e.getMessage());
                res.status(500);
                return gson.toJson(createErrorResponse("Error: " + e.getMessage()));
            }
        });

        // GET /api/relay/outbox - Get messages from relay outbox
        get("/api/relay/outbox", (req, res) -> {
            res.type("application/json");

            try {
                if (context == null) {
                    res.status(503);
                    return gson.toJson(createErrorResponse("Server context not initialized"));
                }

                // Get limit parameter (default 50)
                String limitParam = req.queryParams("limit");
                int limit = 50;
                if (limitParam != null) {
                    try {
                        limit = Integer.parseInt(limitParam);
                        limit = Math.max(limit, 1);
                    } catch (NumberFormatException e) {
                        // Keep default
                    }
                }

                RelayStorage storage = new RelayStorage(context);
                List<String> messageIds = storage.listMessagesSorted("outbox", true); // newest first

                // Apply limit
                if (messageIds.size() > limit) {
                    messageIds = messageIds.subList(0, limit);
                }

                // Load full message objects
                List<JsonObject> messages = new ArrayList<>();
                for (String msgId : messageIds) {
                    RelayMessage msg = storage.getMessage(msgId, "outbox");
                    if (msg != null) {
                        JsonObject msgJson = new JsonObject();
                        msgJson.addProperty("id", msg.getId());
                        msgJson.addProperty("from", msg.getFromCallsign());
                        msgJson.addProperty("to", msg.getToCallsign());
                        msgJson.addProperty("content", msg.getContent());
                        msgJson.addProperty("timestamp", msg.getTimestamp());
                        msgJson.addProperty("type", msg.getType());
                        msgJson.addProperty("priority", msg.getPriority());
                        messages.add(msgJson);
                    }
                }

                // Create response
                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.addProperty("count", messages.size());
                response.addProperty("folder", "outbox");
                response.add("messages", gson.toJsonTree(messages));

                Log.i(TAG_ID, "API: Returned " + messages.size() + " outbox messages");

                res.status(200);
                return gson.toJson(response);

            } catch (Exception e) {
                Log.e(TAG_ID, "Error retrieving outbox messages: " + e.getMessage());
                res.status(500);
                return gson.toJson(createErrorResponse("Error: " + e.getMessage()));
            }
        });

        // GET /api/relay/message/:messageId - Get specific message by ID
        get("/api/relay/message/:messageId", (req, res) -> {
            res.type("application/json");

            try {
                if (context == null) {
                    res.status(503);
                    return gson.toJson(createErrorResponse("Server context not initialized"));
                }

                String messageId = req.params(":messageId");
                if (messageId == null || messageId.isEmpty()) {
                    res.status(400);
                    return gson.toJson(createErrorResponse("Message ID is required"));
                }

                RelayStorage storage = new RelayStorage(context);
                RelayMessage msg = storage.getMessage(messageId);

                if (msg == null) {
                    res.status(404);
                    return gson.toJson(createErrorResponse("Message not found"));
                }

                // Create detailed response
                JsonObject msgJson = new JsonObject();
                msgJson.addProperty("id", msg.getId());
                msgJson.addProperty("from", msg.getFromCallsign());
                msgJson.addProperty("to", msg.getToCallsign());
                msgJson.addProperty("content", msg.getContent());
                msgJson.addProperty("timestamp", msg.getTimestamp());
                msgJson.addProperty("type", msg.getType());
                msgJson.addProperty("priority", msg.getPriority());
                msgJson.addProperty("ttl", msg.getTtl());
                msgJson.addProperty("hopCount", msg.getHopCount());

                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.add("message", msgJson);

                Log.i(TAG_ID, "API: Retrieved message " + messageId);

                res.status(200);
                return gson.toJson(response);

            } catch (Exception e) {
                Log.e(TAG_ID, "Error retrieving message: " + e.getMessage());
                res.status(500);
                return gson.toJson(createErrorResponse("Error: " + e.getMessage()));
            }
        });

        // DELETE /api/relay/message/:messageId - Delete message from relay
        delete("/api/relay/message/:messageId", (req, res) -> {
            res.type("application/json");

            try {
                if (context == null) {
                    res.status(503);
                    return gson.toJson(createErrorResponse("Server context not initialized"));
                }

                String messageId = req.params(":messageId");
                if (messageId == null || messageId.isEmpty()) {
                    res.status(400);
                    return gson.toJson(createErrorResponse("Message ID is required"));
                }

                RelayStorage storage = new RelayStorage(context);

                // Try deleting from all folders
                boolean deleted = false;
                String deletedFrom = null;
                String[] folders = {"inbox", "outbox", "sent"};
                for (String folder : folders) {
                    if (storage.deleteMessage(messageId, folder)) {
                        deleted = true;
                        deletedFrom = folder;
                        break;
                    }
                }

                if (!deleted) {
                    res.status(404);
                    return gson.toJson(createErrorResponse("Message not found"));
                }

                Log.i(TAG_ID, "API: Deleted message " + messageId + " from " + deletedFrom);

                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.addProperty("message", "Message deleted successfully");
                response.addProperty("messageId", messageId);
                response.addProperty("deletedFrom", deletedFrom);

                res.status(200);
                return gson.toJson(response);

            } catch (Exception e) {
                Log.e(TAG_ID, "Error deleting message: " + e.getMessage());
                res.status(500);
                return gson.toJson(createErrorResponse("Error: " + e.getMessage()));
            }
        });

        // ========== RELAY SYNC ENDPOINTS (WiFi-based sync protocol) ==========

        // POST /api/relay/sync/inventory - Receive inventory from remote device
        post("/api/relay/sync/inventory", (req, res) -> {
            res.type("application/json");

            try {
                if (context == null) {
                    res.status(503);
                    return gson.toJson(createErrorResponse("Server context not initialized"));
                }

                // Parse JSON request body
                String body = req.body();
                JsonObject jsonRequest = gson.fromJson(body, JsonObject.class);

                if (jsonRequest == null || !jsonRequest.has("remoteDeviceId") || !jsonRequest.has("inventory")) {
                    res.status(400);
                    return gson.toJson(createErrorResponse("Missing 'remoteDeviceId' or 'inventory' field"));
                }

                String remoteDeviceId = jsonRequest.get("remoteDeviceId").getAsString();
                String inventoryData = jsonRequest.get("inventory").getAsString();

                Log.i(TAG_ID, "API: Received relay sync inventory from " + remoteDeviceId);
                Log.i(TAG_ID, "Inventory data: " + inventoryData);

                // Process inventory via RelayMessageSync
                offgrid.geogram.relay.RelayMessageSync relaySync =
                    offgrid.geogram.relay.RelayMessageSync.getInstance(context);
                relaySync.handleWiFiInventory(remoteDeviceId, inventoryData);

                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.addProperty("message", "Inventory processed");

                res.status(200);
                return gson.toJson(response);

            } catch (Exception e) {
                Log.e(TAG_ID, "Error processing relay inventory: " + e.getMessage());
                res.status(500);
                return gson.toJson(createErrorResponse("Error: " + e.getMessage()));
            }
        });

        // POST /api/relay/sync/request - Receive message request from remote device
        post("/api/relay/sync/request", (req, res) -> {
            res.type("application/json");

            try {
                if (context == null) {
                    res.status(503);
                    return gson.toJson(createErrorResponse("Server context not initialized"));
                }

                // Parse JSON request body
                String body = req.body();
                JsonObject jsonRequest = gson.fromJson(body, JsonObject.class);

                if (jsonRequest == null || !jsonRequest.has("remoteDeviceId") || !jsonRequest.has("messageId")) {
                    res.status(400);
                    return gson.toJson(createErrorResponse("Missing 'remoteDeviceId' or 'messageId' field"));
                }

                String remoteDeviceId = jsonRequest.get("remoteDeviceId").getAsString();
                String messageId = jsonRequest.get("messageId").getAsString();

                Log.i(TAG_ID, "API: Received relay message request from " + remoteDeviceId + " for message " + messageId);

                // Process request via RelayMessageSync
                offgrid.geogram.relay.RelayMessageSync relaySync =
                    offgrid.geogram.relay.RelayMessageSync.getInstance(context);
                relaySync.handleWiFiRequest(remoteDeviceId, messageId);

                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.addProperty("message", "Request processed");

                res.status(200);
                return gson.toJson(response);

            } catch (Exception e) {
                Log.e(TAG_ID, "Error processing relay request: " + e.getMessage());
                res.status(500);
                return gson.toJson(createErrorResponse("Error: " + e.getMessage()));
            }
        });

        // POST /api/relay/sync/message - Receive relay message from remote device
        post("/api/relay/sync/message", (req, res) -> {
            res.type("application/json");

            try {
                if (context == null) {
                    res.status(503);
                    return gson.toJson(createErrorResponse("Server context not initialized"));
                }

                // Parse JSON request body
                String body = req.body();
                JsonObject jsonRequest = gson.fromJson(body, JsonObject.class);

                if (jsonRequest == null || !jsonRequest.has("remoteDeviceId") || !jsonRequest.has("markdown")) {
                    res.status(400);
                    return gson.toJson(createErrorResponse("Missing 'remoteDeviceId' or 'markdown' field"));
                }

                String remoteDeviceId = jsonRequest.get("remoteDeviceId").getAsString();
                String markdown = jsonRequest.get("markdown").getAsString();

                Log.i(TAG_ID, "API: Received relay message from " + remoteDeviceId);
                Log.i(TAG_ID, "Message size: " + markdown.length() + " bytes");

                // Process relay message via RelayMessageSync
                offgrid.geogram.relay.RelayMessageSync relaySync =
                    offgrid.geogram.relay.RelayMessageSync.getInstance(context);
                relaySync.handleWiFiRelayMessage(remoteDeviceId, markdown);

                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.addProperty("message", "Relay message processed");

                res.status(200);
                return gson.toJson(response);

            } catch (Exception e) {
                Log.e(TAG_ID, "Error processing relay message: " + e.getMessage());
                res.status(500);
                return gson.toJson(createErrorResponse("Error: " + e.getMessage()));
            }
        });

        // ========== DEVICES ENDPOINT ==========

        // GET /api/devices/nearby - List nearby devices detected via BLE
        get("/api/devices/nearby", (req, res) -> {
            res.type("application/json");

            try {
                if (context == null) {
                    res.status(503);
                    return gson.toJson(createErrorResponse("Server context not initialized"));
                }

                // Get limit parameter (default 50)
                String limitParam = req.queryParams("limit");
                int limit = 50;
                if (limitParam != null) {
                    try {
                        limit = Integer.parseInt(limitParam);
                        limit = Math.max(limit, 1);
                    } catch (NumberFormatException e) {
                        // Keep default
                    }
                }

                // Get devices from DeviceManager
                DeviceManager deviceManager = DeviceManager.getInstance();
                java.util.TreeSet<Device> devices = deviceManager.getDevicesSpotted();

                // Convert to list and apply limit
                List<JsonObject> deviceList = new ArrayList<>();
                int count = 0;
                for (Device device : devices) {
                    if (count >= limit) break;

                    JsonObject deviceJson = new JsonObject();
                    deviceJson.addProperty("callsign", device.ID);
                    deviceJson.addProperty("deviceType", device.deviceType.toString());
                    deviceJson.addProperty("displayName", device.getDisplayName());

                    // Get latest connection event
                    if (!device.connectedEvents.isEmpty()) {
                        EventConnected latestEvent = device.connectedEvents.last();
                        deviceJson.addProperty("lastSeen", latestEvent.latestTimestamp());
                        deviceJson.addProperty("connectionType", latestEvent.connectionType.toString());

                        // Add location if available
                        if (latestEvent.geocode != null) {
                            deviceJson.addProperty("geocode", latestEvent.geocode);
                            if (latestEvent.lat != null && latestEvent.lon != null) {
                                deviceJson.addProperty("latitude", latestEvent.lat);
                                deviceJson.addProperty("longitude", latestEvent.lon);
                            }
                        }

                        // Estimate distance/RSSI if available (simplified - actual RSSI would need BLE data)
                        // For now, we'll show connection count as a proxy for signal strength
                        deviceJson.addProperty("connectionCount", latestEvent.timestamps.size());
                    } else {
                        deviceJson.addProperty("lastSeen", device.latestTimestamp());
                    }

                    // Add device model and version if available
                    if (device.getDeviceModel() != null) {
                        deviceJson.addProperty("deviceModel", device.getDeviceModel().toString());
                    }
                    if (device.getDeviceVersion() != null) {
                        deviceJson.addProperty("deviceVersion", device.getDeviceVersion());
                    }

                    deviceList.add(deviceJson);
                    count++;
                }

                // Create response
                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.addProperty("count", deviceList.size());
                response.addProperty("total_devices", devices.size());
                response.add("devices", gson.toJsonTree(deviceList));

                Log.i(TAG_ID, "API: Returned " + deviceList.size() + " nearby devices");

                res.status(200);
                return gson.toJson(response);

            } catch (Exception e) {
                Log.e(TAG_ID, "Error retrieving nearby devices: " + e.getMessage());
                res.status(500);
                return gson.toJson(createErrorResponse("Error: " + e.getMessage()));
            }
        });

        // ========== GROUP MESSAGES ENDPOINTS ==========

        // GET /api/groups - List all conversation groups
        get("/api/groups", (req, res) -> {
            res.type("application/json");

            try {
                if (context == null) {
                    res.status(503);
                    return gson.toJson(createErrorResponse("Server context not initialized"));
                }

                DatabaseMessages db = DatabaseMessages.getInstance();
                List<ChatMessage> allMessages = db.snapshot();

                // Build a map of conversations with their stats
                java.util.Map<String, ConversationInfo> conversations = new java.util.HashMap<>();

                for (ChatMessage msg : allMessages) {
                    // Determine conversation ID (use destinationId for groups, authorId for direct messages)
                    String conversationId = null;

                    if (msg.destinationId != null && !msg.destinationId.isEmpty()) {
                        conversationId = msg.destinationId;
                    } else if (msg.authorId != null && !msg.authorId.isEmpty()) {
                        conversationId = msg.authorId;
                    }

                    if (conversationId == null) continue;

                    // Get or create conversation info
                    ConversationInfo info = conversations.get(conversationId);
                    if (info == null) {
                        info = new ConversationInfo(conversationId);
                        conversations.put(conversationId, info);
                    }

                    // Update stats
                    info.messageCount++;
                    if (!msg.read && !msg.isWrittenByMe) {
                        info.unreadCount++;
                    }
                    if (info.lastMessageTime < msg.timestamp) {
                        info.lastMessageTime = msg.timestamp;
                        info.lastMessage = msg.message;
                    }
                }

                // Convert to JSON list
                List<JsonObject> groupList = new ArrayList<>();
                for (ConversationInfo info : conversations.values()) {
                    JsonObject groupJson = new JsonObject();
                    groupJson.addProperty("callsign", info.callsign);
                    groupJson.addProperty("messageCount", info.messageCount);
                    groupJson.addProperty("unreadCount", info.unreadCount);
                    groupJson.addProperty("lastMessageTime", info.lastMessageTime);
                    groupJson.addProperty("lastMessage", info.lastMessage);
                    groupList.add(groupJson);
                }

                // Sort by last message time (newest first)
                groupList.sort((a, b) -> Long.compare(
                    b.get("lastMessageTime").getAsLong(),
                    a.get("lastMessageTime").getAsLong()
                ));

                // Create response
                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.addProperty("count", groupList.size());
                response.add("groups", gson.toJsonTree(groupList));

                Log.i(TAG_ID, "API: Returned " + groupList.size() + " conversation groups");

                res.status(200);
                return gson.toJson(response);

            } catch (Exception e) {
                Log.e(TAG_ID, "Error retrieving groups: " + e.getMessage());
                res.status(500);
                return gson.toJson(createErrorResponse("Error: " + e.getMessage()));
            }
        });

        // GET /api/groups/:callsign/messages - Get messages for a group/conversation
        get("/api/groups/:callsign/messages", (req, res) -> {
            res.type("application/json");

            try {
                if (context == null) {
                    res.status(503);
                    return gson.toJson(createErrorResponse("Server context not initialized"));
                }

                String callsign = req.params(":callsign");
                if (callsign == null || callsign.isEmpty()) {
                    res.status(400);
                    return gson.toJson(createErrorResponse("Callsign is required"));
                }

                // Get limit parameter (default 50)
                String limitParam = req.queryParams("limit");
                int limit = 50;
                if (limitParam != null) {
                    try {
                        limit = Integer.parseInt(limitParam);
                        limit = Math.max(limit, 1);
                    } catch (NumberFormatException e) {
                        // Keep default
                    }
                }

                // Get offset parameter (default 0)
                String offsetParam = req.queryParams("offset");
                int offset = 0;
                if (offsetParam != null) {
                    try {
                        offset = Integer.parseInt(offsetParam);
                        offset = Math.max(offset, 0);
                    } catch (NumberFormatException e) {
                        // Keep default
                    }
                }

                DatabaseMessages db = DatabaseMessages.getInstance();
                List<ChatMessage> allMessages = db.snapshot();

                // Filter messages for this conversation
                List<ChatMessage> conversationMessages = new ArrayList<>();
                for (ChatMessage msg : allMessages) {
                    boolean belongsToConversation = false;

                    // Match by destinationId or authorId
                    if (callsign.equals(msg.destinationId) || callsign.equals(msg.authorId)) {
                        belongsToConversation = true;
                    }
                    // Try with "group-" prefix
                    else if (msg.destinationId != null &&
                             (msg.destinationId.equals("group-" + callsign) ||
                              callsign.equals("group-" + msg.destinationId))) {
                        belongsToConversation = true;
                    }
                    // Try without "group-" prefix
                    else if (callsign.startsWith("group-") && msg.destinationId != null &&
                             msg.destinationId.equals(callsign.substring(6))) {
                        belongsToConversation = true;
                    }

                    if (belongsToConversation) {
                        conversationMessages.add(msg);
                    }
                }

                // Apply offset and limit (messages are already sorted newest first)
                int totalMessages = conversationMessages.size();
                int startIndex = Math.min(offset, totalMessages);
                int endIndex = Math.min(startIndex + limit, totalMessages);
                List<ChatMessage> paginatedMessages = conversationMessages.subList(startIndex, endIndex);

                // Convert to JSON
                List<JsonObject> messageList = new ArrayList<>();
                for (ChatMessage msg : paginatedMessages) {
                    JsonObject msgJson = new JsonObject();
                    msgJson.addProperty("authorId", msg.authorId);
                    msgJson.addProperty("destinationId", msg.destinationId);
                    msgJson.addProperty("message", msg.message);
                    msgJson.addProperty("timestamp", msg.timestamp);
                    msgJson.addProperty("delivered", msg.delivered);
                    msgJson.addProperty("read", msg.read);
                    msgJson.addProperty("isWrittenByMe", msg.isWrittenByMe);
                    msgJson.addProperty("messageType", msg.messageType != null ? msg.messageType.toString() : "DATA");
                    messageList.add(msgJson);
                }

                // Create response
                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.addProperty("callsign", callsign);
                response.addProperty("count", messageList.size());
                response.addProperty("total_messages", totalMessages);
                response.addProperty("offset", offset);
                response.add("messages", gson.toJsonTree(messageList));

                Log.i(TAG_ID, "API: Returned " + messageList.size() + " messages for " + callsign);

                res.status(200);
                return gson.toJson(response);

            } catch (Exception e) {
                Log.e(TAG_ID, "Error retrieving group messages: " + e.getMessage());
                res.status(500);
                return gson.toJson(createErrorResponse("Error: " + e.getMessage()));
            }
        });

        // POST /api/groups/:callsign/messages - Send message to a group
        post("/api/groups/:callsign/messages", (req, res) -> {
            res.type("application/json");

            try {
                if (context == null) {
                    res.status(503);
                    return gson.toJson(createErrorResponse("Server context not initialized"));
                }

                String callsign = req.params(":callsign");
                if (callsign == null || callsign.isEmpty()) {
                    res.status(400);
                    return gson.toJson(createErrorResponse("Callsign is required"));
                }

                // Parse JSON request body
                String body = req.body();
                JsonObject jsonRequest = gson.fromJson(body, JsonObject.class);

                if (jsonRequest == null || !jsonRequest.has("message")) {
                    res.status(400);
                    return gson.toJson(createErrorResponse("Missing 'message' field"));
                }

                String messageContent = jsonRequest.get("message").getAsString();
                if (messageContent == null || messageContent.isEmpty()) {
                    res.status(400);
                    return gson.toJson(createErrorResponse("Message cannot be empty"));
                }

                // Send via BLE
                BluetoothSender sender = BluetoothSender.getInstance(context);
                sender.sendMessage(messageContent);

                // Create chat message and save to database
                ChatMessage chatMsg = new ChatMessage(Central.getInstance().getSettings().getCallsign(), messageContent);
                chatMsg.destinationId = callsign;
                chatMsg.isWrittenByMe = true;
                chatMsg.delivered = false;

                DatabaseMessages db = DatabaseMessages.getInstance();
                db.add(chatMsg);

                Log.i(TAG_ID, "API: Sent message to group " + callsign);

                // Create success response
                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.addProperty("message", "Message sent to group");
                response.addProperty("callsign", callsign);
                response.addProperty("timestamp", chatMsg.timestamp);

                res.status(200);
                return gson.toJson(response);

            } catch (Exception e) {
                Log.e(TAG_ID, "Error sending group message: " + e.getMessage());
                res.status(500);
                return gson.toJson(createErrorResponse("Error: " + e.getMessage()));
            }
        });

        // GET /api/groups/:callsign/info - Get group/conversation info
        get("/api/groups/:callsign/info", (req, res) -> {
            res.type("application/json");

            try {
                if (context == null) {
                    res.status(503);
                    return gson.toJson(createErrorResponse("Server context not initialized"));
                }

                String callsign = req.params(":callsign");
                if (callsign == null || callsign.isEmpty()) {
                    res.status(400);
                    return gson.toJson(createErrorResponse("Callsign is required"));
                }

                DatabaseMessages db = DatabaseMessages.getInstance();
                DatabaseMessages.ConversationStats stats = db.getConversationStats(callsign);

                // Create response
                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.addProperty("callsign", callsign);
                response.addProperty("messageCount", stats.totalMessages);
                response.addProperty("unreadCount", stats.unreadCount);

                if (stats.lastMessage != null) {
                    response.addProperty("lastMessageTime", stats.lastMessage.timestamp);
                    response.addProperty("lastMessage", stats.lastMessage.message);
                    response.addProperty("lastMessageAuthor", stats.lastMessage.authorId);
                }

                Log.i(TAG_ID, "API: Returned info for group " + callsign);

                res.status(200);
                return gson.toJson(response);

            } catch (Exception e) {
                Log.e(TAG_ID, "Error retrieving group info: " + e.getMessage());
                res.status(500);
                return gson.toJson(createErrorResponse("Error: " + e.getMessage()));
            }
        });

        // ========== COLLECTIONS ENDPOINTS ==========

        // GET /api/collections - List all public collections
        get("/api/collections", (req, res) -> {
            res.type("application/json");

            try {
                if (context == null) {
                    res.status(503);
                    return gson.toJson(createErrorResponse("Server context not initialized"));
                }

                // Get requesting device's IP address
                String requestIp = req.ip();
                Log.d(TAG_ID, "API: /api/collections request from IP: " + requestIp);

                // Try to find the requesting device's npub by IP address
                String requestingNpub = null;
                offgrid.geogram.devices.Device requestingDevice = findDeviceByIp(requestIp);
                if (requestingDevice != null) {
                    requestingNpub = requestingDevice.getProfileNpub();
                    Log.d(TAG_ID, "API: Found requesting device " + requestingDevice.ID + " with npub: " + (requestingNpub != null ? "present" : "null"));
                }

                // Load all collections
                List<Collection> allCollections = CollectionLoader.loadCollectionsFromAppStorage(context);

                // Filter collections based on visibility and permissions
                List<JsonObject> accessibleCollections = new ArrayList<>();
                for (Collection collection : allCollections) {
                    CollectionSecurity security = collection.getSecurity();
                    if (security == null) {
                        continue;
                    }

                    boolean isAccessible = false;

                    // Check visibility
                    if (security.getVisibility() == CollectionSecurity.Visibility.PUBLIC) {
                        // Public collections are accessible to everyone
                        isAccessible = true;
                    } else if (security.getVisibility() == CollectionSecurity.Visibility.GROUP) {
                        // Group collections: check if requesting npub is in whitelist
                        if (requestingNpub != null && !requestingNpub.isEmpty()) {
                            List<String> whitelist = security.getWhitelistedUsers();
                            if (whitelist != null && whitelist.contains(requestingNpub)) {
                                isAccessible = true;
                                Log.d(TAG_ID, "API: Collection " + collection.getId() + " accessible via group permission for npub");
                            }
                        }
                    }
                    // PRIVATE collections are never accessible via API

                    if (isAccessible) {
                        JsonObject collectionJson = new JsonObject();
                        collectionJson.addProperty("id", collection.getId());
                        collectionJson.addProperty("title", collection.getTitle());
                        collectionJson.addProperty("description", collection.getDescription());
                        collectionJson.addProperty("filesCount", collection.getFilesCount());
                        collectionJson.addProperty("totalSize", collection.getTotalSize());
                        collectionJson.addProperty("formattedSize", collection.getFormattedSize());
                        collectionJson.addProperty("updated", collection.getUpdated());
                        accessibleCollections.add(collectionJson);
                    }
                }

                // Create response
                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.addProperty("count", accessibleCollections.size());
                response.add("collections", gson.toJsonTree(accessibleCollections));

                Log.i(TAG_ID, "API: Returned " + accessibleCollections.size() + " accessible collections (requestingNpub: " + (requestingNpub != null ? "present" : "null") + ")");

                res.status(200);
                return gson.toJson(response);

            } catch (Exception e) {
                Log.e(TAG_ID, "Error retrieving collections: " + e.getMessage(), e);
                res.status(500);
                return gson.toJson(createErrorResponse("Error: " + e.getMessage()));
            }
        });

        // GET /api/profile - Get device profile (nickname, description, profile picture)
        get("/api/profile", (req, res) -> {
            res.type("application/json");

            try {
                if (context == null) {
                    res.status(503);
                    return gson.toJson(createErrorResponse("Server context not initialized"));
                }

                // Get profile data
                String nickname = offgrid.geogram.util.ProfilePreferences.getNickname(context);
                String description = offgrid.geogram.util.ProfilePreferences.getDescription(context);
                String imagePath = offgrid.geogram.util.ProfilePreferences.getProfileImagePath(context);

                // Get preferred color and npub from settings
                offgrid.geogram.settings.SettingsUser settings = offgrid.geogram.core.Central.getInstance().getSettings();
                String preferredColor = "";
                String npub = "";
                if (settings != null) {
                    if (settings.getPreferredColor() != null) {
                        preferredColor = settings.getPreferredColor();
                    }
                    if (settings.getNpub() != null) {
                        npub = settings.getNpub();
                    }
                }

                // Create response
                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.addProperty("nickname", nickname != null && !nickname.isEmpty() ? nickname : "");
                response.addProperty("description", description != null && !description.isEmpty() ? description : "");
                response.addProperty("preferredColor", preferredColor);
                response.addProperty("npub", npub);
                response.addProperty("hasProfilePicture", imagePath != null && !imagePath.isEmpty() && new java.io.File(imagePath).exists());

                Log.i(TAG_ID, "API: Returned profile data");

                res.status(200);
                return gson.toJson(response);

            } catch (Exception e) {
                Log.e(TAG_ID, "Error getting profile: " + e.getMessage());
                res.status(500);
                return gson.toJson(createErrorResponse("Error: " + e.getMessage()));
            }
        });

        // GET /api/profile/picture - Get profile picture
        get("/api/profile/picture", (req, res) -> {
            try {
                if (context == null) {
                    res.status(503);
                    res.type("application/json");
                    return gson.toJson(createErrorResponse("Server context not initialized"));
                }

                String imagePath = offgrid.geogram.util.ProfilePreferences.getProfileImagePath(context);

                if (imagePath == null || imagePath.isEmpty()) {
                    res.status(404);
                    res.type("application/json");
                    return gson.toJson(createErrorResponse("No profile picture set"));
                }

                java.io.File imageFile = new java.io.File(imagePath);
                if (!imageFile.exists()) {
                    res.status(404);
                    res.type("application/json");
                    return gson.toJson(createErrorResponse("Profile picture file not found"));
                }

                // Serve the image
                res.type("image/jpeg");
                try (java.io.FileInputStream fis = new java.io.FileInputStream(imageFile);
                     java.io.OutputStream out = res.raw().getOutputStream()) {

                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                    out.flush();
                }

                Log.i(TAG_ID, "API: Served profile picture");
                res.status(200);
                return res;

            } catch (Exception e) {
                Log.e(TAG_ID, "Error serving profile picture: " + e.getMessage());
                res.status(500);
                res.type("application/json");
                return gson.toJson(createErrorResponse("Error: " + e.getMessage()));
            }
        });

        // GET /api/collections/count - Get count of public collections
        get("/api/collections/count", (req, res) -> {
            res.type("application/json");

            try {
                if (context == null) {
                    res.status(503);
                    return gson.toJson(createErrorResponse("Server context not initialized"));
                }

                // Load all collections
                List<Collection> allCollections = CollectionLoader.loadCollectionsFromAppStorage(context);

                // Count only public collections
                int publicCount = 0;
                for (Collection collection : allCollections) {
                    CollectionSecurity security = collection.getSecurity();
                    if (security != null && security.isPubliclyAccessible()) {
                        publicCount++;
                    }
                }

                // Create response
                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.addProperty("count", publicCount);

                Log.i(TAG_ID, "API: Returned public collections count: " + publicCount);

                res.status(200);
                return gson.toJson(response);

            } catch (Exception e) {
                Log.e(TAG_ID, "Error counting collections: " + e.getMessage());
                res.status(500);
                return gson.toJson(createErrorResponse("Error: " + e.getMessage()));
            }
        });

        // GET /api/collections/:npub - Get collection metadata
        get("/api/collections/:npub", (req, res) -> {
            res.type("application/json");

            try {
                if (context == null) {
                    res.status(503);
                    return gson.toJson(createErrorResponse("Server context not initialized"));
                }

                String npub = req.params(":npub");
                if (npub == null || npub.isEmpty()) {
                    res.status(400);
                    return gson.toJson(createErrorResponse("Collection npub is required"));
                }

                // Load all collections and find the requested one
                List<Collection> allCollections = CollectionLoader.loadCollectionsFromAppStorage(context);
                Collection requestedCollection = null;

                for (Collection collection : allCollections) {
                    if (npub.equals(collection.getId())) {
                        requestedCollection = collection;
                        break;
                    }
                }

                if (requestedCollection == null) {
                    res.status(404);
                    return gson.toJson(createErrorResponse("Collection not found"));
                }

                // Check if collection is publicly accessible
                CollectionSecurity security = requestedCollection.getSecurity();
                if (security == null || !security.isPubliclyAccessible()) {
                    res.status(403);
                    return gson.toJson(createErrorResponse("Collection is not publicly accessible"));
                }

                // Create detailed response
                JsonObject collectionJson = new JsonObject();
                collectionJson.addProperty("id", requestedCollection.getId());
                collectionJson.addProperty("title", requestedCollection.getTitle());
                collectionJson.addProperty("description", requestedCollection.getDescription());
                collectionJson.addProperty("filesCount", requestedCollection.getFilesCount());
                collectionJson.addProperty("totalSize", requestedCollection.getTotalSize());
                collectionJson.addProperty("formattedSize", requestedCollection.getFormattedSize());
                collectionJson.addProperty("updated", requestedCollection.getUpdated());

                // Add security/permissions info
                JsonObject securityJson = new JsonObject();
                securityJson.addProperty("visibility", security.getVisibility().getValue());
                securityJson.addProperty("can_comment", security.isCanUsersComment());
                securityJson.addProperty("can_like", security.isCanUsersLike());
                securityJson.addProperty("can_dislike", security.isCanUsersDislike());
                securityJson.addProperty("can_rate", security.isCanUsersRate());
                collectionJson.add("permissions", securityJson);

                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.add("collection", collectionJson);

                Log.i(TAG_ID, "API: Returned metadata for collection " + npub);

                res.status(200);
                return gson.toJson(response);

            } catch (Exception e) {
                Log.e(TAG_ID, "Error retrieving collection metadata: " + e.getMessage());
                res.status(500);
                return gson.toJson(createErrorResponse("Error: " + e.getMessage()));
            }
        });

        // GET /api/collections/:npub/files - Browse collection files
        get("/api/collections/:npub/files", (req, res) -> {
            res.type("application/json");

            try {
                if (context == null) {
                    res.status(503);
                    return gson.toJson(createErrorResponse("Server context not initialized"));
                }

                String npub = req.params(":npub");
                if (npub == null || npub.isEmpty()) {
                    res.status(400);
                    return gson.toJson(createErrorResponse("Collection npub is required"));
                }

                // Get optional path parameter for browsing subdirectories
                String path = req.queryParams("path");
                if (path == null) {
                    path = "";
                }

                // Load collection
                List<Collection> allCollections = CollectionLoader.loadCollectionsFromAppStorage(context);
                Collection requestedCollection = null;

                for (Collection collection : allCollections) {
                    if (npub.equals(collection.getId())) {
                        requestedCollection = collection;
                        break;
                    }
                }

                if (requestedCollection == null) {
                    res.status(404);
                    return gson.toJson(createErrorResponse("Collection not found"));
                }

                // Get requesting device's npub for permission check
                String requestIp = req.ip();
                String requestingNpub = null;
                offgrid.geogram.devices.Device requestingDevice = findDeviceByIp(requestIp);
                if (requestingDevice != null) {
                    requestingNpub = requestingDevice.getProfileNpub();
                }

                // Check if requesting device has access to this collection
                CollectionSecurity security = requestedCollection.getSecurity();
                if (!hasCollectionAccess(security, requestingNpub)) {
                    res.status(403);
                    Log.d(TAG_ID, "API: Access denied to collection " + npub + " for IP " + requestIp + " (npub: " + (requestingNpub != null ? "present" : "null") + ")");
                    return gson.toJson(createErrorResponse("Access denied to this collection"));
                }

                // Get files in the requested path
                List<JsonObject> filesList = new ArrayList<>();
                for (CollectionFile file : requestedCollection.getFiles()) {
                    String filePath = file.getPath();
                    String fileDir = filePath.contains("/") ?
                        filePath.substring(0, filePath.lastIndexOf("/")) : "";

                    // Only include files in the requested directory
                    if (path.equals(fileDir)) {
                        JsonObject fileJson = new JsonObject();
                        fileJson.addProperty("name", file.getName());
                        fileJson.addProperty("path", file.getPath());
                        fileJson.addProperty("type", file.isDirectory() ? "directory" : "file");
                        if (!file.isDirectory()) {
                            fileJson.addProperty("size", file.getSize());
                            fileJson.addProperty("mimeType", file.getMimeType());
                        }
                        filesList.add(fileJson);
                    }
                }

                // Create response
                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.addProperty("collection_id", npub);
                response.addProperty("path", path);
                response.addProperty("count", filesList.size());
                response.add("files", gson.toJsonTree(filesList));

                Log.i(TAG_ID, "API: Returned " + filesList.size() + " files for collection " + npub);

                res.status(200);
                return gson.toJson(response);

            } catch (Exception e) {
                Log.e(TAG_ID, "Error browsing collection files: " + e.getMessage());
                res.status(500);
                return gson.toJson(createErrorResponse("Error: " + e.getMessage()));
            }
        });

        // GET /api/collections/:npub/file/* - Download a specific file
        get("/api/collections/:npub/file/*", (req, res) -> {
            try {
                if (context == null) {
                    res.status(503);
                    res.type("application/json");
                    return gson.toJson(createErrorResponse("Server context not initialized"));
                }

                String npub = req.params(":npub");
                String filePath = req.splat()[0]; // Get the wildcard path

                if (npub == null || npub.isEmpty() || filePath == null || filePath.isEmpty()) {
                    res.status(400);
                    res.type("application/json");
                    return gson.toJson(createErrorResponse("Collection npub and file path are required"));
                }

                // Security: Prevent path traversal attacks
                if (filePath.contains("..") || filePath.startsWith("/")) {
                    res.status(403);
                    res.type("application/json");
                    return gson.toJson(createErrorResponse("Invalid file path"));
                }

                // Load collection
                List<Collection> allCollections = CollectionLoader.loadCollectionsFromAppStorage(context);
                Collection requestedCollection = null;

                for (Collection collection : allCollections) {
                    if (npub.equals(collection.getId())) {
                        requestedCollection = collection;
                        break;
                    }
                }

                if (requestedCollection == null) {
                    res.status(404);
                    res.type("application/json");
                    return gson.toJson(createErrorResponse("Collection not found"));
                }

                // Get requesting device's npub for permission check
                String requestIp = req.ip();
                String requestingNpub = null;
                offgrid.geogram.devices.Device requestingDevice = findDeviceByIp(requestIp);
                if (requestingDevice != null) {
                    requestingNpub = requestingDevice.getProfileNpub();
                }

                // Check if requesting device has access to this collection
                CollectionSecurity security = requestedCollection.getSecurity();
                if (!hasCollectionAccess(security, requestingNpub)) {
                    res.status(403);
                    res.type("application/json");
                    Log.d(TAG_ID, "API: Access denied to file in collection " + npub + " for IP " + requestIp + " (npub: " + (requestingNpub != null ? "present" : "null") + ")");
                    return gson.toJson(createErrorResponse("Access denied to this collection"));
                }

                // Construct file path and check if it exists
                File collectionRoot = new File(requestedCollection.getStoragePath());
                File requestedFile = new File(collectionRoot, filePath);

                // Additional security: Ensure file is within collection directory
                String canonicalCollectionPath = collectionRoot.getCanonicalPath();
                String canonicalFilePath = requestedFile.getCanonicalPath();
                if (!canonicalFilePath.startsWith(canonicalCollectionPath)) {
                    res.status(403);
                    res.type("application/json");
                    return gson.toJson(createErrorResponse("Access denied: file outside collection"));
                }

                // Security: Don't serve files from extra/ directory or collection.js
                // Exception: Allow extra/tree-data.js for remote browsing
                if (filePath.equals("collection.js") ||
                    (filePath.startsWith("extra/") && !filePath.equals("extra/tree-data.js"))) {
                    res.status(403);
                    res.type("application/json");
                    return gson.toJson(createErrorResponse("Access denied: system file"));
                }

                if (!requestedFile.exists() || !requestedFile.isFile()) {
                    res.status(404);
                    res.type("application/json");
                    return gson.toJson(createErrorResponse("File not found"));
                }

                // Determine MIME type
                String mimeType = "application/octet-stream";
                String fileName = requestedFile.getName();
                if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
                    mimeType = "image/jpeg";
                } else if (fileName.endsWith(".png")) {
                    mimeType = "image/png";
                } else if (fileName.endsWith(".gif")) {
                    mimeType = "image/gif";
                } else if (fileName.endsWith(".pdf")) {
                    mimeType = "application/pdf";
                } else if (fileName.endsWith(".txt")) {
                    mimeType = "text/plain";
                } else if (fileName.endsWith(".html")) {
                    mimeType = "text/html";
                } else if (fileName.endsWith(".json")) {
                    mimeType = "application/json";
                } else if (fileName.endsWith(".mp3")) {
                    mimeType = "audio/mpeg";
                } else if (fileName.endsWith(".mp4")) {
                    mimeType = "video/mp4";
                }

                Log.i(TAG_ID, "API: Serving file " + filePath + " from collection " + npub);

                // Read file content
                byte[] fileContent = Files.readAllBytes(requestedFile.toPath());

                // Set response properties BEFORE accessing raw()
                res.status(200);
                res.type(mimeType);
                res.header("Content-Disposition", "inline; filename=\"" + fileName + "\"");
                res.header("Content-Length", String.valueOf(fileContent.length));

                // Get raw response and write bytes directly
                try {
                    javax.servlet.http.HttpServletResponse rawResponse = res.raw();
                    rawResponse.getOutputStream().write(fileContent);
                    rawResponse.getOutputStream().flush();
                } catch (Exception writeEx) {
                    Log.e(TAG_ID, "Error writing file to response: " + writeEx.getMessage());
                    throw writeEx;
                }

                return null;

            } catch (Exception e) {
                Log.e(TAG_ID, "Error serving file: " + e.getMessage());
                res.status(500);
                res.type("application/json");
                return gson.toJson(createErrorResponse("Error: " + e.getMessage()));
            }
        });

        // GET /api/collections/:npub/thumbnail/* - Get image thumbnail
        get("/api/collections/:npub/thumbnail/*", (req, res) -> {
            try {
                if (context == null) {
                    res.status(503);
                    res.type("application/json");
                    return gson.toJson(createErrorResponse("Server context not initialized"));
                }

                String npub = req.params(":npub");
                String filePath = req.splat()[0]; // Get the wildcard path

                if (npub == null || npub.isEmpty() || filePath == null || filePath.isEmpty()) {
                    res.status(400);
                    res.type("application/json");
                    return gson.toJson(createErrorResponse("Collection npub and file path are required"));
                }

                // Security: Prevent path traversal attacks
                if (filePath.contains("..") || filePath.startsWith("/")) {
                    res.status(403);
                    res.type("application/json");
                    return gson.toJson(createErrorResponse("Invalid file path"));
                }

                // Load collection
                List<Collection> allCollections = CollectionLoader.loadCollectionsFromAppStorage(context);
                Collection requestedCollection = null;

                for (Collection collection : allCollections) {
                    if (npub.equals(collection.getId())) {
                        requestedCollection = collection;
                        break;
                    }
                }

                if (requestedCollection == null) {
                    res.status(404);
                    res.type("application/json");
                    return gson.toJson(createErrorResponse("Collection not found"));
                }

                // Get requesting device's npub for permission check
                String requestIp = req.ip();
                String requestingNpub = null;
                offgrid.geogram.devices.Device requestingDevice = findDeviceByIp(requestIp);
                if (requestingDevice != null) {
                    requestingNpub = requestingDevice.getProfileNpub();
                }

                // Check if requesting device has access to this collection
                CollectionSecurity security = requestedCollection.getSecurity();
                if (!hasCollectionAccess(security, requestingNpub)) {
                    res.status(403);
                    res.type("application/json");
                    Log.d(TAG_ID, "API: Access denied to thumbnail in collection " + npub + " for IP " + requestIp + " (npub: " + (requestingNpub != null ? "present" : "null") + ")");
                    return gson.toJson(createErrorResponse("Access denied to this collection"));
                }

                // Construct file path and check if it exists
                File collectionRoot = new File(requestedCollection.getStoragePath());
                File requestedFile = new File(collectionRoot, filePath);

                // Additional security: Ensure file is within collection directory
                String canonicalCollectionPath = collectionRoot.getCanonicalPath();
                String canonicalFilePath = requestedFile.getCanonicalPath();
                if (!canonicalFilePath.startsWith(canonicalCollectionPath)) {
                    res.status(403);
                    res.type("application/json");
                    return gson.toJson(createErrorResponse("Access denied: file outside collection"));
                }

                // Security: Don't serve files from extra/ directory
                if (filePath.startsWith("extra/")) {
                    res.status(403);
                    res.type("application/json");
                    return gson.toJson(createErrorResponse("Access denied: system file"));
                }

                if (!requestedFile.exists() || !requestedFile.isFile()) {
                    res.status(404);
                    res.type("application/json");
                    return gson.toJson(createErrorResponse("File not found"));
                }

                // Check if file is an image
                String fileName = requestedFile.getName().toLowerCase();
                if (!fileName.endsWith(".jpg") && !fileName.endsWith(".jpeg") &&
                    !fileName.endsWith(".png") && !fileName.endsWith(".gif")) {
                    res.status(400);
                    res.type("application/json");
                    return gson.toJson(createErrorResponse("File is not an image"));
                }

                Log.i(TAG_ID, "API: Generating thumbnail for " + filePath + " from collection " + npub);

                // Load and downsample image to create thumbnail
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(requestedFile.getAbsolutePath(), options);

                // Calculate sample size for 200x200 thumbnail
                int targetSize = 200;
                int width = options.outWidth;
                int height = options.outHeight;
                int inSampleSize = 1;

                if (height > targetSize || width > targetSize) {
                    final int halfHeight = height / 2;
                    final int halfWidth = width / 2;
                    while ((halfHeight / inSampleSize) >= targetSize && (halfWidth / inSampleSize) >= targetSize) {
                        inSampleSize *= 2;
                    }
                }

                options.inSampleSize = inSampleSize;
                options.inJustDecodeBounds = false;

                // Decode with downsampling
                Bitmap thumbnail = BitmapFactory.decodeFile(requestedFile.getAbsolutePath(), options);

                if (thumbnail == null) {
                    res.status(500);
                    res.type("application/json");
                    return gson.toJson(createErrorResponse("Failed to decode image"));
                }

                // Compress to JPEG
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                thumbnail.compress(Bitmap.CompressFormat.JPEG, 85, baos);
                byte[] thumbnailBytes = baos.toByteArray();
                thumbnail.recycle(); // Free bitmap memory

                // Set response properties
                res.status(200);
                res.type("image/jpeg");
                res.header("Content-Length", String.valueOf(thumbnailBytes.length));
                res.header("Cache-Control", "public, max-age=86400"); // Cache for 24 hours

                // Write thumbnail bytes
                try {
                    javax.servlet.http.HttpServletResponse rawResponse = res.raw();
                    rawResponse.getOutputStream().write(thumbnailBytes);
                    rawResponse.getOutputStream().flush();
                } catch (Exception writeEx) {
                    Log.e(TAG_ID, "Error writing thumbnail to response: " + writeEx.getMessage());
                    throw writeEx;
                }

                return null;

            } catch (Exception e) {
                Log.e(TAG_ID, "Error serving thumbnail: " + e.getMessage());
                res.status(500);
                res.type("application/json");
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

    /**
     * Find a device by IP address
     * Looks through WiFi discovery service to match IP to device callsign/ID
     */
    private offgrid.geogram.devices.Device findDeviceByIp(String requestIp) {
        try {
            if (context == null || requestIp == null) {
                return null;
            }

            // Get all known devices
            java.util.TreeSet<offgrid.geogram.devices.Device> devices =
                offgrid.geogram.devices.DeviceManager.getInstance().getDevicesSpotted();

            // Check each device to see if its IP matches
            offgrid.geogram.wifi.WiFiDiscoveryService wifiService =
                offgrid.geogram.wifi.WiFiDiscoveryService.getInstance(context);

            for (offgrid.geogram.devices.Device device : devices) {
                String deviceIp = wifiService.getDeviceIp(device.ID);
                if (deviceIp != null && deviceIp.equals(requestIp)) {
                    return device;
                }
            }

            return null;
        } catch (Exception e) {
            Log.e(TAG_ID, "Error finding device by IP: " + e.getMessage());
            return null;
        }
    }

    /**
     * Check if requesting device has access to a collection
     * Checks PUBLIC, PRIVATE, and GROUP permissions
     */
    private boolean hasCollectionAccess(CollectionSecurity security, String requestingNpub) {
        if (security == null) {
            return false;
        }

        // Check visibility
        if (security.getVisibility() == CollectionSecurity.Visibility.PUBLIC) {
            // Public collections are accessible to everyone
            return true;
        } else if (security.getVisibility() == CollectionSecurity.Visibility.GROUP) {
            // Group collections: check if requesting npub is in whitelist
            if (requestingNpub != null && !requestingNpub.isEmpty()) {
                List<String> whitelist = security.getWhitelistedUsers();
                if (whitelist != null && whitelist.contains(requestingNpub)) {
                    return true;
                }
            }
        }
        // PRIVATE collections are never accessible via API
        return false;
    }

    // Helper class for conversation statistics
    private static class ConversationInfo {
        String callsign;
        int messageCount = 0;
        int unreadCount = 0;
        long lastMessageTime = 0;
        String lastMessage = null;

        ConversationInfo(String callsign) {
            this.callsign = callsign;
        }
    }
}

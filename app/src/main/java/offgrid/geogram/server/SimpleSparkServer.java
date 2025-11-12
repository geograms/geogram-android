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
    private static final String BUILD_TIMESTAMP = "2025-01-12T06:00:00Z"; // Updated on each build
    private static final String API_VERSION = "0.5.7"; // Increment on API changes
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

                // Create relay message
                RelayMessage relayMsg = new RelayMessage();
                relayMsg.setToCallsign(recipient);
                relayMsg.setContent(messageContent);
                relayMsg.setTimestamp(System.currentTimeMillis() / 1000); // Unix timestamp in seconds

                if (messageId != null && !messageId.isEmpty()) {
                    relayMsg.setId(messageId);
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

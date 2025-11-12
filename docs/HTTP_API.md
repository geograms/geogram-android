# Geogram HTTP API Documentation

## Overview

The Geogram Android app runs an HTTP server on port **45678** that provides REST API endpoints for automating BLE (Bluetooth Low Energy) operations and accessing application logs. This server starts automatically with the BackgroundService when enabled in settings and runs on all network interfaces (0.0.0.0).

**Base URL:** `http://<device-ip>:45678`

## Enabling the HTTP API

The HTTP API can be enabled or disabled from the Settings panel:

1. Open the Geogram app
2. Navigate to **Settings**
3. Find the **HTTP API Server** section
4. Toggle the **Enable HTTP API** switch
5. Restart the app for changes to take effect

The Settings panel displays:
- **Enable/Disable toggle** - Turn the HTTP API server on or off
- **Server URL** - The current IP address and port (e.g., `http://192.168.1.100:45678`)
- **Copy button** - Copy the server URL to clipboard
- **Share button** - Share the server URL and endpoint information via other apps

**Note:** The HTTP API is enabled by default for convenience during development and testing.

## API Endpoints

### 1. Root Endpoint

**GET /** - Server information and available endpoints

**Response:** HTML page with server information and list of available endpoints

**Example:**
```bash
curl http://localhost:45678/
```

---

### 2. Server Status

**GET /api/status** - Get server status and version information

**Response:**
```json
{
  "success": true,
  "server": "Geogram HTTP API",
  "port": 45678,
  "version": "0.4.0",
  "running": true
}
```

**Example:**
```bash
curl http://localhost:45678/api/status
```

---

### 3. Send BLE Message

**POST /api/ble/send** - Queue a message for BLE broadcast

**Request Body:**
```json
{
  "message": "Your message here"
}
```

**Success Response (200):**
```json
{
  "success": true,
  "message": "BLE message queued for broadcast",
  "sent_message": "Your message here"
}
```

**Error Responses:**
- **400 Bad Request:** Missing or empty message field
- **503 Service Unavailable:** Server context not initialized

**Example:**
```bash
curl -X POST http://localhost:45678/api/ble/send \
  -H "Content-Type: application/json" \
  -d '{"message": "Hello BLE!"}'
```

**Notes:**
- Messages are queued for broadcast via BLE advertising
- The sender will use the configured callsign from app settings
- Messages are split into parcels (max 40 characters per parcel)
- BLE uses time-sliced interleaving (sends 3 parcels, then listens for 5 seconds)

---

### 4. Get Recent Logs

**GET /api/logs** - Retrieve recent log messages from memory

**Query Parameters:**
- `limit` (optional): Number of log messages to return (default: 100, max: 5000)
- `filter` (optional): Keyword to filter logs (case-insensitive search in log message content)

**Response:**
```json
{
  "success": true,
  "count": 100,
  "total_logs": 2543,
  "filtered_count": 150,
  "filter": "GATT",
  "logs": [
    "12:34:56 [BluetoothSender] GATT connection established",
    "12:34:57 [BluetoothSender] GATT characteristic discovered",
    ...
  ]
}
```

**Example:**
```bash
# Get last 100 logs (default)
curl http://localhost:45678/api/logs

# Get last 500 logs
curl http://localhost:45678/api/logs?limit=500

# Get all available logs (max 5000)
curl http://localhost:45678/api/logs?limit=5000

# Filter logs by keyword (case-insensitive)
curl http://localhost:45678/api/logs?filter=GATT&limit=50

# Filter for errors
curl http://localhost:45678/api/logs?filter=error&limit=100
```

---

### 5. Get Log File Contents

**GET /api/logs/file** - Retrieve the complete log file from disk

**Query Parameters:**
- `tail` (optional): Return only the last N lines of the log file

**Success Response (200):**
```json
{
  "success": true,
  "file_path": "/data/user/0/offgrid.geogram/files/app_debug.log",
  "file_size": 2048576,
  "content": "01-15 12:34:56.789 I [BluetoothSender] Message sent\n..."
}
```

**Error Responses:**
- **503 Service Unavailable:** Log file not initialized yet
- **404 Not Found:** Log file not found
- **500 Internal Server Error:** Error reading log file

**Example:**
```bash
# Get entire log file
curl http://localhost:45678/api/logs/file

# Get last 100 lines of log file
curl http://localhost:45678/api/logs/file?tail=100
```

**Notes:**
- The log file has a maximum size of 5 MB
- When the file exceeds 5 MB, it is automatically trimmed to 1 MB (keeping the most recent logs)
- Log file format: `MM-DD HH:mm:ss.SSS LEVEL [TAG] message`
- Timestamps include milliseconds for precision

---

### 6. Relay Message Endpoints

#### POST /api/relay/send

Send a message to relay storage (outbox).

**Request Body:**
```json
{
  "recipient": "X1ADK0",
  "message": "Hello from the relay!",
  "messageId": "optional-custom-id"
}
```

**Success Response (200):**
```json
{
  "success": true,
  "message": "Relay message saved to outbox",
  "messageId": "abc123def456...",
  "recipient": "X1ADK0"
}
```

**Example:**
```bash
curl -X POST http://localhost:45678/api/relay/send \
  -H "Content-Type: application/json" \
  -d '{"recipient": "X1ADK0", "message": "Test relay message"}'
```

---

#### GET /api/relay/inbox

Get messages from relay inbox.

**Query Parameters:**
- `limit` (optional): Number of messages to return (default: 50)

**Response:**
```json
{
  "success": true,
  "count": 5,
  "folder": "inbox",
  "messages": [
    {
      "id": "abc123...",
      "from": "Y2BKL1",
      "to": "X1ADK0",
      "content": "Message content",
      "timestamp": 1705056789,
      "type": "private",
      "priority": "normal"
    }
  ]
}
```

**Example:**
```bash
# Get last 50 inbox messages (default)
curl http://localhost:45678/api/relay/inbox

# Get last 100 inbox messages
curl http://localhost:45678/api/relay/inbox?limit=100
```

---

#### GET /api/relay/outbox

Get messages from relay outbox.

**Query Parameters:**
- `limit` (optional): Number of messages to return (default: 50)

**Response:**
```json
{
  "success": true,
  "count": 3,
  "folder": "outbox",
  "messages": [
    {
      "id": "def456...",
      "from": "X1ADK0",
      "to": "Y2BKL1",
      "content": "Outbound message",
      "timestamp": 1705056890,
      "type": "private",
      "priority": "normal"
    }
  ]
}
```

**Example:**
```bash
curl http://localhost:45678/api/relay/outbox?limit=50
```

---

#### GET /api/relay/message/:messageId

Get a specific relay message by ID.

**Response:**
```json
{
  "success": true,
  "message": {
    "id": "abc123...",
    "from": "Y2BKL1",
    "to": "X1ADK0",
    "content": "Message content",
    "timestamp": 1705056789,
    "type": "private",
    "priority": "normal",
    "ttl": 604800,
    "hopCount": 2
  }
}
```

**Error Response (404):**
```json
{
  "error": "Message not found"
}
```

**Example:**
```bash
curl http://localhost:45678/api/relay/message/abc123def456
```

---

#### DELETE /api/relay/message/:messageId

Delete a relay message by ID from any folder (inbox, outbox, or sent).

**Success Response (200):**
```json
{
  "success": true,
  "message": "Message deleted successfully",
  "messageId": "abc123...",
  "deletedFrom": "inbox"
}
```

**Error Response (404):**
```json
{
  "error": "Message not found"
}
```

**Example:**
```bash
curl -X DELETE http://localhost:45678/api/relay/message/abc123def456
```

---

### 7. Devices Endpoint

#### GET /api/devices/nearby

List nearby devices detected via BLE and other connection methods.

**Query Parameters:**
- `limit` (optional): Number of devices to return (default: 50)

**Response:**
```json
{
  "success": true,
  "count": 10,
  "total_devices": 15,
  "devices": [
    {
      "callsign": "X1ADK0",
      "deviceType": "HT_PORTABLE",
      "displayName": "Geogram App 0.4.0",
      "lastSeen": 1705056890123,
      "connectionType": "BLE",
      "geocode": "9q8y",
      "latitude": "37.7749",
      "longitude": "-122.4194",
      "connectionCount": 5,
      "deviceModel": "APP",
      "deviceVersion": "0.4.0"
    }
  ]
}
```

**Example:**
```bash
# Get last 50 nearby devices (default)
curl http://localhost:45678/api/devices/nearby

# Get last 100 nearby devices
curl http://localhost:45678/api/devices/nearby?limit=100
```

**Notes:**
- Devices are sorted by most recent connection (newest first)
- `lastSeen` is a Unix timestamp in milliseconds
- `connectionCount` indicates how many times the device has been spotted
- `geocode` and location fields are only present if the device has location data
- `deviceModel` and `deviceVersion` are only present if the device broadcasts this information

---

### 8. Group Messages Endpoints

#### GET /api/groups

List all conversation groups/channels.

**Response:**
```json
{
  "success": true,
  "count": 5,
  "groups": [
    {
      "callsign": "global-geo-chat",
      "messageCount": 150,
      "unreadCount": 3,
      "lastMessageTime": 1705056890123,
      "lastMessage": "Hello from the chat!"
    }
  ]
}
```

**Example:**
```bash
curl http://localhost:45678/api/groups
```

**Notes:**
- Groups are sorted by last message time (newest first)
- Includes both named groups and direct message conversations
- `lastMessageTime` is a Unix timestamp in milliseconds

---

#### GET /api/groups/:callsign/messages

Get messages for a specific group or conversation.

**Query Parameters:**
- `limit` (optional): Number of messages to return (default: 50)
- `offset` (optional): Pagination offset (default: 0)

**Response:**
```json
{
  "success": true,
  "callsign": "global-geo-chat",
  "count": 20,
  "total_messages": 150,
  "offset": 0,
  "messages": [
    {
      "authorId": "X1ADK0",
      "destinationId": "global-geo-chat",
      "message": "Hello everyone!",
      "timestamp": 1705056890123,
      "delivered": true,
      "read": true,
      "isWrittenByMe": false,
      "messageType": "DATA"
    }
  ]
}
```

**Example:**
```bash
# Get last 50 messages (default)
curl http://localhost:45678/api/groups/global-geo-chat/messages

# Get messages with pagination
curl http://localhost:45678/api/groups/global-geo-chat/messages?limit=20&offset=40

# Get last 100 messages
curl http://localhost:45678/api/groups/X1ADK0/messages?limit=100
```

**Notes:**
- Messages are sorted by timestamp (newest first)
- Works with both group names and direct message callsigns
- Supports pagination for large conversations

---

#### POST /api/groups/:callsign/messages

Send a message to a specific group or conversation.

**Request Body:**
```json
{
  "message": "Hello from the API!"
}
```

**Success Response (200):**
```json
{
  "success": true,
  "message": "Message sent to group",
  "callsign": "global-geo-chat",
  "timestamp": 1705056890123
}
```

**Example:**
```bash
# Send to a group
curl -X POST http://localhost:45678/api/groups/global-geo-chat/messages \
  -H "Content-Type: application/json" \
  -d '{"message": "Hello from API!"}'

# Send direct message
curl -X POST http://localhost:45678/api/groups/X1ADK0/messages \
  -H "Content-Type: application/json" \
  -d '{"message": "Private message"}'
```

**Notes:**
- The message is broadcast via BLE and saved to the local database
- The message will use the configured callsign from app settings
- Messages are queued for BLE broadcast (not sent instantly)

---

#### GET /api/groups/:callsign/info

Get information about a specific group or conversation.

**Response:**
```json
{
  "success": true,
  "callsign": "global-geo-chat",
  "messageCount": 150,
  "unreadCount": 3,
  "lastMessageTime": 1705056890123,
  "lastMessage": "Hello!",
  "lastMessageAuthor": "X1ADK0"
}
```

**Example:**
```bash
curl http://localhost:45678/api/groups/global-geo-chat/info
```

**Notes:**
- Returns statistics about the conversation
- Includes unread message count
- Shows the last message and its author

---

## Usage Examples

### Testing BLE Messaging

```bash
# Send a test BLE message
curl -X POST http://localhost:45678/api/ble/send \
  -H "Content-Type: application/json" \
  -d '{"message": "Test message from API"}'

# Check logs to verify the message was sent
curl http://localhost:45678/api/logs?limit=50
```

### Automated Testing Script

```bash
#!/bin/bash

# Send multiple BLE messages for testing
for i in {1..10}; do
  curl -X POST http://localhost:45678/api/ble/send \
    -H "Content-Type: application/json" \
    -d "{\"message\": \"Test message $i\"}" \
    -s | jq .
  sleep 2
done

# Get logs to verify all messages were sent
curl http://localhost:45678/api/logs?limit=100 -s | jq '.logs | .[]' | grep "Test message"
```

### Log Monitoring

```bash
# Continuously monitor logs (polling every 2 seconds)
while true; do
  clear
  curl -s http://localhost:45678/api/logs?limit=50 | jq -r '.logs | .[]'
  sleep 2
done
```

---

## Architecture Notes

### Server Lifecycle

1. **Startup:** The HTTP server is automatically started by `BackgroundService.onCreate()` when the app starts, but only if enabled in settings
2. **Port:** The server runs on port 45678 and listens on all network interfaces (0.0.0.0)
3. **Threading:** The server runs in a separate thread to avoid blocking the main application
4. **Persistence:** The server continues running as long as the BackgroundService is active (even when the app is in the background)
5. **Settings:** The HTTP API can be enabled/disabled from the Settings panel. Changes require an app restart to take effect

### BLE Integration

- The `/api/ble/send` endpoint interfaces with the `BluetoothSender` singleton
- Messages are queued and broadcast using BLE LE Advertiser
- BLE uses a time-sliced approach: sends 3 parcels, then listens for 5 seconds
- Each message may be split into multiple parcels (40 character limit per parcel)
- Messages include callsign, checksum, and message ID for reliability

### Logging System

- **In-Memory Logs:** The app maintains up to 5000 recent log messages in memory (CopyOnWriteArrayList)
- **Log File:** All logs are also written to `app_debug.log` in the app's private storage
- **Log Levels:** DEBUG (D), INFO (I), WARN (W), ERROR (E)
- **Format:** Logs include timestamp, level, tag, and message

---

## Security Considerations

- The HTTP server runs without authentication - only use on trusted networks
- The server listens on all network interfaces (0.0.0.0) for testing convenience
- For production use, consider adding authentication or restricting to localhost (127.0.0.1)
- The server does not use HTTPS - all traffic is unencrypted

---

## Troubleshooting

### Server Not Responding

1. **Check if HTTP API is enabled:**
   - Open Settings in the app
   - Verify that "Enable HTTP API" toggle is ON
   - If you just enabled it, restart the app

2. Check if the BackgroundService is running:
   ```bash
   adb shell dumpsys activity services offgrid.geogram.core.BackgroundService
   ```

3. Verify the server started successfully in logs:
   ```bash
   adb logcat | grep "offgrid-server"
   ```
   You should see: "HTTP API server started"

   If you see "HTTP API server disabled in settings", then the feature is turned off in Settings.

4. Check if the port is bound:
   ```bash
   adb shell netstat -an | grep 45678
   ```

### BLE Messages Not Sending

1. Verify Bluetooth permissions are granted
2. Check if Bluetooth is enabled on the device
3. Review logs for BLE errors:
   ```bash
   curl http://localhost:45678/api/logs?limit=100 | jq -r '.logs | .[]' | grep "Bluetooth"
   ```

### Cannot Access from Another Device

1. Ensure both devices are on the same network
2. Check firewall settings on the Android device
3. Verify the device IP address:
   ```bash
   adb shell ip addr show wlan0
   ```

---

## Implementation Files

- **Server:** `/app/src/main/java/offgrid/geogram/server/SimpleSparkServer.java`
- **Service:** `/app/src/main/java/offgrid/geogram/core/BackgroundService.java`
- **BLE Sender:** `/app/src/main/java/offgrid/geogram/ble/BluetoothSender.java`
- **Logging:** `/app/src/main/java/offgrid/geogram/core/Log.java`
- **Settings:** `/app/src/main/java/offgrid/geogram/settings/SettingsUser.java`
- **Settings UI:** `/app/src/main/java/offgrid/geogram/settings/SettingsFragment.java`
- **Network Utilities:** `/app/src/main/java/offgrid/geogram/util/NetworkUtils.java`

---

## Version History

- **v0.5.0** (2025-01-12): Major API expansion with relay, devices, and groups
  - Enhanced GET /api/logs endpoint with filter parameter for keyword search
  - Added relay message endpoints:
    - POST /api/relay/send - Send message to relay storage
    - GET /api/relay/inbox - Get inbox messages
    - GET /api/relay/outbox - Get outbox messages
    - GET /api/relay/message/:messageId - Get specific message
    - DELETE /api/relay/message/:messageId - Delete message
  - Added devices endpoint:
    - GET /api/devices/nearby - List nearby BLE devices with details
  - Added group/conversation message endpoints:
    - GET /api/groups - List all conversations
    - GET /api/groups/:callsign/messages - Get conversation messages (with pagination)
    - POST /api/groups/:callsign/messages - Send message to conversation
    - GET /api/groups/:callsign/info - Get conversation statistics
  - Updated home page HTML with categorized endpoint listing

- **v0.4.0** (2025-01-11): Initial HTTP API implementation
  - Added POST /api/ble/send endpoint
  - Added GET /api/logs endpoint with limit parameter
  - Added GET /api/logs/file endpoint with tail parameter
  - Added GET /api/status endpoint
  - Changed server port from 5050 to 45678
  - Added Settings panel integration:
    - Enable/disable HTTP API toggle
    - Display server IP address and port
    - Copy URL to clipboard button
    - Share URL via Android share intent
  - Added NetworkUtils for IP address detection

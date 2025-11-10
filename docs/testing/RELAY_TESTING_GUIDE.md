# Relay Testing Guide

This guide explains how to test the Geogram relay functionality in the Android app.

## Overview

The relay system provides store-and-forward messaging capabilities. Messages are stored locally and synced via Bluetooth when devices come within range.

## Testing the Relay UI

### 1. Access Relay Settings

**From Main Screen:**
- Tap the **Relay icon** (connected nodes) in the top action bar
- The icon is located between the Messages button and the Devices button
- Badge shows current message count

**From Navigation Menu:**
- Tap the menu icon (☰)
- Select "Relay" from the navigation drawer

### 2. Enable the Relay

In the Relay Settings screen:
1. Toggle **"Enable Relay"** switch to ON
2. You should see a toast notification: "Relay enabled"
3. The badge on the main screen will update color (green when active)

### 3. Configure Settings

**Disk Space Limit:**
- Drag the slider to set storage limit (100MB to 10GB)
- Value updates in real-time
- Changes save automatically

**Auto-Accept Messages:**
- Toggle ON to automatically accept relayed messages
- Toggle OFF to require manual acceptance (future feature)

**Message Types:**
- Select from dropdown:
  - **Everything** - Accept all message types
  - **Text Only** - Reject messages with attachments
  - **Text and Images** - Accept text and image attachments only

## Creating Test Messages

The Relay Settings screen includes a **"Testing Tools"** section with three buttons:

### Add to Inbox
Creates a single test message in the **inbox** folder (messages received from other relays).

**What it creates:**
- From: `TEST-K5ABC`
- To: `DEST-W6XYZ`
- Content: Timestamp of creation
- Priority: Normal
- TTL: 7 days

### Add to Outbox
Creates a single test message in the **outbox** folder (messages queued to send to other relays).

**Same format as inbox message.**

### Add 5 Test
Creates multiple test messages at once:
- **2 messages in inbox:**
  - From: `REMOTE-K5000`, `REMOTE-K5001`
  - To: `LOCAL-W6ABC`
  - Priorities: Urgent and Normal
- **3 messages in outbox:**
  - From: `LOCAL-K5XYZ`
  - To: `REMOTE-W6000`, `REMOTE-W6001`, `REMOTE-W6002`
  - One message includes a test image attachment

**After creating messages:**
- Status display updates immediately
- Badge on main screen updates
- Storage usage increases

## Monitoring Relay Status

The **Relay Status** section shows real-time statistics:

- **Inbox:** Messages received, awaiting delivery
- **Outbox:** Messages queued to relay
- **Sent:** Messages successfully relayed
- **Storage Used:** Total disk space used

**Auto-refresh:** Status updates every 5 seconds while the screen is visible.

## Managing Messages

### View Messages
Tap **"View Messages"** to see a summary dialog with:
- Message counts per folder
- Total storage usage

### Clear Sent Messages
Tap **"Clear Sent"** to delete all sent messages:
- Confirmation dialog appears
- Cannot be undone
- Frees up storage space

## Testing BLE Synchronization

### Single Device Testing
1. Enable relay
2. Create test messages using "Add 5 Test"
3. Observe status updates
4. Messages are stored in: `/data/data/offgrid.geogram.geogram/files/relay/`

### Two Device Testing (Future)

**Device A (Sender):**
1. Enable relay
2. Create messages in outbox
3. Keep Geogram running

**Device B (Receiver):**
1. Enable relay
2. Bring within Bluetooth range of Device A
3. Devices will automatically sync:
   - Device A sends inventory of outbox messages
   - Device B requests missing messages
   - Messages transfer via BLE
   - Device A moves messages to sent folder
   - Device B saves messages to inbox folder

**Expected behavior:**
- Badge updates on both devices
- Status counts change
- Toast notifications (when implemented)

## Viewing Message Files

Messages are stored as markdown files:

**Directory structure:**
```
/data/data/offgrid.geogram.geogram/files/relay/
├── inbox/      (received messages)
├── outbox/     (messages to send)
└── sent/       (successfully relayed)
```

**File naming:**
- Each message: `<message-id>.md`
- Example: `TEST-1699603500123.md`

**To view on device:**
```bash
adb shell
cd /data/data/offgrid.geogram.geogram/files/relay
ls -la inbox/
cat inbox/TEST-*.md
```

## Testing Message Filtering

### Text Only Mode
1. Set "Message Types" to "Text Only"
2. Create test messages with "Add 5 Test"
3. Messages with attachments should be rejected during sync

### Text and Images Mode
1. Set "Message Types" to "Text and Images"
2. Only image attachments accepted
3. Other attachment types rejected

## Testing Storage Limits

1. Set disk space limit to 100MB (minimum)
2. Create many test messages using "Add 5 Test" multiple times
3. Monitor storage usage
4. When limit reached, oldest low-priority messages pruned automatically

## Testing Message Expiration

Test messages have 7-day TTL (604800 seconds):

1. Create test messages
2. Manually edit message timestamp to be old:
   ```bash
   adb shell
   # Edit the timestamp in the markdown file
   ```
3. Expired messages automatically deleted by garbage collector

## Troubleshooting

**Badge not updating?**
- Check if relay is enabled
- Try returning to main screen and reopening relay

**Messages not syncing?**
- Verify Bluetooth is enabled
- Check both devices have relay enabled
- Ensure devices are within BLE range (< 10 meters)
- Check message type filtering settings

**Storage not increasing?**
- Verify messages were created (check logs)
- Try "View Messages" to see counts
- Check file system directly with adb

**Can't create messages?**
- Check storage permissions
- Ensure app has write access
- Check disk space limit setting

## Next Steps

Once single-device testing works:
1. Test with two physical devices
2. Test BLE synchronization
3. Test message filtering
4. Test storage limits
5. Test in production scenarios (hiking, events, etc.)

## Useful ADB Commands

```bash
# View relay messages
adb shell ls /data/data/offgrid.geogram.geogram/files/relay/inbox/
adb shell ls /data/data/offgrid.geogram.geogram/files/relay/outbox/

# Read a message
adb shell cat /data/data/offgrid.geogram.geogram/files/relay/inbox/TEST-*.md

# Clear all messages
adb shell rm /data/data/offgrid.geogram.geogram/files/relay/*/*.md

# Check storage usage
adb shell du -sh /data/data/offgrid.geogram.geogram/files/relay/

# Monitor logs
adb logcat | grep -E "Relay|RelayMessage|RelaySync"
```

## Testing Checklist

- [ ] Enable/disable relay toggle works
- [ ] Disk space slider updates value
- [ ] Auto-accept toggle persists
- [ ] Message type filter changes
- [ ] Create test message in inbox
- [ ] Create test message in outbox
- [ ] Create multiple test messages
- [ ] Status display shows correct counts
- [ ] Storage usage displays correctly
- [ ] Badge updates on main screen
- [ ] View messages dialog works
- [ ] Clear sent messages works
- [ ] Settings persist across app restart
- [ ] Messages survive app restart
- [ ] Two-device sync (when available)

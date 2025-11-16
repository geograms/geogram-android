# APRS-IS P2P Discovery Integration Guide

## Overview

The P2P service now uses **APRS-IS** for peer discovery instead of libp2p GossipSub. This provides:

- ✅ Zero server maintenance (uses federated APRS-IS infrastructure)
- ✅ Works with existing APRS integration
- ✅ Persistent peer cache (survives app restarts)
- ✅ Simple, reliable discovery mechanism

## How It Works

### 1. Peer Announcement via APRS-IS

Geogram devices announce their presence:
- **Immediately** after connecting to APRS-IS (on every app start)
- Every **30 minutes** thereafter

```
X1ABCD>X11GEO,TCPIP*:>p2p-12D3KooWFTef23s8k1n68TtY5kzKjeDXu2NhpW3vAxJSesZH3zrz
```

Format breakdown:
- `X1ABCD` = Sender's X1 callsign (user-configured)
- `X11GEO` = Application ID (valid 6-char APRS callsign)
- `TCPIP*` = Internet gateway
- `>` = Status message indicator
- `p2p-` = Discovery keyword (shortened format)
- `12D3Koo...` = libp2p Peer ID

### 2. Discovery Process

1. **Startup**: Load cached peers from disk (up to 7 days old)
2. **Listening**: Monitor APRS-IS for "p2p-" messages
3. **Discovery**: Extract Peer IDs from announcements
4. **Caching**: Save discovered peers to SharedPreferences
5. **Notification**: Alert listeners about new/cached peers

### 3. Peer Cache

Discovered peers are cached in SharedPreferences:
- **Storage**: `/data/data/offgrid.geogram.geogram/shared_prefs/aprs_peer_cache.xml`
- **Format**: JSON array of peer objects
- **Expiry**: 7 days (peers don't change IDs often)
- **Benefits**: Instant peer list on app startup

## Integration Steps

### Step 1: Connect APRS Message Receiver

In your APRS-IS message receiver, forward all incoming messages to P2PService:

```java
// When receiving APRS messages
public void onAprsMessageReceived(String aprsMessage) {
    P2PService p2pService = P2PService.getInstance(context);
    p2pService.handleAprsMessage(aprsMessage);
}
```

### Step 2: Send Periodic Announcements

**NOTE**: AprsIsService now handles this automatically! You don't need to manually send announcements.

The P2P service now includes AprsIsService which:
- Connects to APRS-IS using the user's X1 callsign
- Sends announcements automatically every 30 minutes
- Format: `X1ABCD>X11GEO,TCPIP*:>p2p-<peerId>`

If you need to integrate manually:

```java
// This is now handled internally by AprsIsService
// But if needed for reference:
String callsign = "X1ABCD"; // User's callsign
String peerId = "12D3KooW..."; // libp2p peer ID
String aprsMessage = String.format(
    "%s>X11GEO,TCPIP*:>p2p-%s",
    callsign,
    peerId
);
```

### Step 3: Handle Discovered Peers

Currently, discovered peers are logged. To use them:

```java
// In P2PService.java, update onPeerDiscovered():
private void onPeerDiscovered(AprsPeerDiscovery.PeerInfo peerInfo) {
    Log.i(TAG, "Peer discovered: " + peerInfo.peerId);

    // TODO: Your logic here:
    // - Attempt libp2p connection
    // - Enable data sync with this peer
    // - Update UI to show online peers
    // - etc.
}
```

## Testing

### Manual Test

1. **Check announcements** in logs:
   ```
   adb logcat -s P2P/APRSDiscovery:*
   ```

2. **Verify message format**:
   ```
   Announcement message: p2p-12D3KooWFTef23s8k1n68TtY5kzKjeDXu2NhpW3vAxJSesZH3zrz
   ```

3. **Simulate discovery** by testing APRS-IS connection:
   - Enable P2P in settings
   - Check logs for "SENDING APRS MESSAGE TO NETWORK"
   - Verify message appears on aprs.fi using the provided link
   - Use second device or simulate with:
   ```java
   // Note: AprsIsService now handles this automatically
   // This is just for testing/debugging
   String testMessage = "X1TEST>X11GEO,TCPIP*:>p2p-12D3KooWTest123456789";
   // The service will receive this via APRS-IS filter p/X1
   ```

4. **Check cache** by restarting app and verifying cached peers load

### Two-Device Test

1. Install on two phones
2. Both connect to APRS-IS
3. Both enable P2P in settings
4. Check logs for discovery messages
5. Verify each phone discovers the other

## File Overview

### New Files

- **`AprsPeerDiscovery.java`**: Core discovery logic
  - Handles announcements
  - Manages peer cache
  - Provides discovery events

### Modified Files

- **`P2PService.java`**:
  - Replaced GossipSub with APRS discovery
  - Added `handleAprsMessage()` for incoming messages
  - Added `getAprsAnnouncementMessage()` for outgoing announcements

## Next Steps

### Immediate

1. **Integrate with existing APRS code** in geogram-server repository
2. **Test discovery** between two devices
3. **Implement peer connection logic** when peers are discovered

### Future Enhancements

1. **Metadata in announcements**: Add battery level, location, capabilities
2. **Message exchange via APRS**: Use APRS directed messages for P2P data
3. **Fallback to libp2p**: Try direct connections, fallback to APRS relay
4. **Dynamic announcement interval**: Adjust based on battery/activity

## APRS-IS Resources

- **APRS-IS Servers**: `rotate.aprs2.net:14580`
- **Protocol Docs**: http://www.aprs-is.net/
- **Message Format**: APRS Protocol Reference
- **Test Tools**: aprs.fi for viewing announcements

## Benefits of APRS Approach

| Feature | GossipSub (previous) | APRS-IS (current) |
|---------|---------------------|-------------------|
| Infrastructure | Requires bootstrap nodes | Uses existing APRS-IS |
| Maintenance | Bootstrap nodes can go offline | Federated, always available |
| Dependencies | Complex libp2p stack | Existing APRS integration |
| Debugging | Hard to trace messages | Visible on aprs.fi |
| Cache | None | 7-day persistent cache |
| Battery | Always-on mesh | Periodic announcements |

## Troubleshooting

**Discovery not working?**
- Check APRS-IS connection is active
- Verify announcement format in logs
- Confirm `handleAprsMessage()` is being called
- Check cache file exists and has valid JSON

**Peers not persisting?**
- Check SharedPreferences file exists
- Verify cache expiry (7 days)
- Look for JSON parsing errors in logs

**High battery usage?**
- Reduce announcement interval
- Check APRS-IS connection management
- Verify scheduler is properly shutdown on stop

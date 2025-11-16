# P2P/APRS Log Filtering Guide

## Overview

All P2P and APRS-related log messages use tags containing **"P2P"** for easy filtering.

## Log Tags

All P2P components use consistent tagging:

| Component | Tag | Purpose |
|-----------|-----|---------|
| AprsIsService | `P2P/APRS-IS` | APRS-IS network connection |
| AprsPeerDiscovery | `P2P/APRSDiscovery` | Peer discovery and caching |
| P2PService | `P2P/Service` | libp2p core service |
| BatteryMonitorService | `P2P/Battery` | Battery-based disconnect |

## Filtering Logs

### Using adb logcat

```bash
# Filter all P2P-related logs
adb logcat -s "P2P/*"

# Filter specific component
adb logcat -s "P2P/APRS-IS"

# Filter multiple components
adb logcat -s "P2P/APRS-IS" "P2P/APRSDiscovery"

# Colored output with grep
adb logcat | grep --color=always "P2P"
```

### Using Android Studio Logcat

1. Open **Logcat** tab
2. In filter box, enter: `tag:P2P`
3. Or use regex: `tag:P2P.*`

## Key Log Messages

### 1. APRS-IS Connection

```
╔═══════════════════════════════════════════════════════╗
║ STARTING APRS-IS SERVICE                             ║
╠═══════════════════════════════════════════════════════╣
║ Server:   rotate.aprs2.net:14580
║ Callsign: X1ABCD
║ Passcode: 10709
║ Filter:   p/X1
║ Peer ID:  GEOGRAM P2P 12D3KooW...
║ Interval: 30 minutes
╚═══════════════════════════════════════════════════════╝
```

```
╔═══════════════════════════════════════════════════════╗
║ ✓ CONNECTED TO APRS-IS NETWORK                       ║
╠═══════════════════════════════════════════════════════╣
║ Status:     AUTHENTICATED
║ Callsign:   X1ABCD
║ Filter:     p/X1 (packets FROM X1* callsigns)
║ Server:     rotate.aprs2.net
╚═══════════════════════════════════════════════════════╝
```

### 2. Sending APRS Messages

**IMPORTANT**: An announcement is sent **immediately** after successful APRS-IS connection, then every 30 minutes.

```
═══════════════════════════════════════════════════════
SENDING APRS MESSAGE TO NETWORK:
═══════════════════════════════════════════════════════
  Message: X1ABCD>X11GEO,TCPIP*:>p2p-12D3KooW...
  Callsign: X1ABCD
  Peer ID: p2p-12D3KooW...
  Time: Fri Nov 15 18:30:15 CET 2025
───────────────────────────────────────────────────────
  Verify at: https://aprs.fi/#!call=a%2FX1ABCD&timerange=3600&tail=3600
═══════════════════════════════════════════════════════
✓ ANNOUNCEMENT TRANSMITTED TO APRS-IS NETWORK
✓ Check aprs.fi in 1-2 minutes to verify message arrived
```

### 3. Receiving APRS Messages

```
───────────────────────────────────────────────────────
RECEIVED APRS MESSAGE FROM NETWORK:
  Raw: X1TEST>X11GEO,TCPIP*:>p2p-12D3KooWTest123
  From: X1TEST
  Type: P2P PEER ANNOUNCEMENT
───────────────────────────────────────────────────────
```

### 4. Peer Discovery

```
╔═══════════════════════════════════════════════════════╗
║ NEW PEER DISCOVERED VIA APRS-IS                      ║
╠═══════════════════════════════════════════════════════╣
║ Peer ID:  12D3KooWTest123456789ABCDEFGH
║ Callsign: X1TEST
║ Time:     Fri Nov 15 17:45:23 CET 2025
║ Total peers in cache: 3
╚═══════════════════════════════════════════════════════╝
```

### 5. Cached Peers

```
╔═══════════════════════════════════════════════════════╗
║ LOADED CACHED PEERS FROM STORAGE                     ║
╠═══════════════════════════════════════════════════════╣
║ Valid peers:   5
║ Expired peers: 2
╚═══════════════════════════════════════════════════════╝
```

### 6. Disconnection

```
╔═══════════════════════════════════════════════════════╗
║ ✗ DISCONNECTED FROM APRS-IS NETWORK                  ║
╚═══════════════════════════════════════════════════════╝
```

## Manual Verification on APRS-IS Network

After seeing "SENDING APRS MESSAGE" in logs, you can verify it was received by the network:

### Option 1: aprs.fi

The log message includes a direct link:
```
Verify at: https://aprs.fi/#!call=a%2FX1ABCD&timerange=3600&tail=3600
```

Click this URL to see your messages on aprs.fi

### Option 2: findu.com

```
http://www.findu.com/cgi-bin/find.cgi?call=X1ABCD
```

### Option 3: APRS Direct

```
https://www.aprsdirect.com/details/X1ABCD
```

### Option 4: Raw APRS-IS Connection

Connect directly to verify:
```bash
telnet rotate.aprs2.net 14580

# Login as read-only
user N0CALL pass -1 vers test 1.0 filter g/X1ABCD

# You should see your announcements
```

## Expected Message Format

Your announcements should appear as:
```
X1ABCD>X11GEO,TCPIP*:>p2p-12D3KooWFTef23s8k1n68TtY5kzKjeDXu2NhpW3vAxJSesZH3zrz
```

Where:
- `X1ABCD` = **Your personal X1 callsign** (configured in Settings)
- `X11GEO` = Destination (Geogram identifier - valid 6-char APRS callsign)
- `TCPIP*` = Came via internet
- `>` = Status message indicator
- `p2p-12D3KooW...` = Your peer announcement with libp2p ID

**IMPORTANT**: Each user must have their own unique X1 callsign (e.g., X1ABCD, X1TEST, X1QRST) configured in Settings. This callsign identifies your device on the APRS-IS network and is required for peer discovery to work.

## Callsign Configuration

### Setting Your X1 Callsign

Each user must configure their **unique X1 callsign** in Geogram Settings:

1. Open Geogram Settings
2. Find "Callsign" field
3. Enter your X1 callsign (format: `X1XXXX` where X is A-Z or 0-9)
   - Examples: `X1ABCD`, `X1TEST`, `X1QRST`
4. Save settings

**Why X1 prefix?**
- The APRS-IS filter `p/X1` catches packets FROM callsigns starting with X1
- All Geogram users must use X1* callsigns for peer discovery to work
- Your specific callsign (e.g., X1ABCD) uniquely identifies your device

### Checking Your Callsign

Look for this in the logs when P2P starts:
```
P2P/APRS-IS: Callsign: X1ABCD
```

If you see:
```
P2P/Service: Cannot start APRS peer discovery: No valid X1 callsign configured!
```

Then you need to configure your callsign in Settings.

## Troubleshooting

### No "SENDING APRS MESSAGE" logs?

**Expected behavior**: You should see "SENDING APRS MESSAGE TO NETWORK" within seconds of app startup.

Check these in order:
1. **Callsign configured**: Must have valid X1 callsign in Settings
   - Look for: `P2P/Service: Callsign: X1ABCD`
   - If missing: Configure your X1 callsign in Settings

2. **P2P service enabled**: Look for "STARTING APRS-IS SERVICE"
   - If missing: Enable P2P in Settings

3. **Connection succeeded**: Look for "✓ CONNECTED TO APRS-IS NETWORK"
   - Should appear within 5-10 seconds of starting
   - If missing: Check internet connection

4. **Announcement sent**: Look for "SENDING APRS MESSAGE TO NETWORK"
   - Should appear **immediately** after connection success
   - Includes timestamp and verification URL
   - If missing: Check logs for errors

5. **Transmission confirmed**: Look for "✓ ANNOUNCEMENT TRANSMITTED"
   - If you see "✗ FAILED TO SEND", connection was lost

### Expected Log Sequence on App Start

```
P2P/APRS-IS: ╔═══════════════════════════════════════════════════════╗
P2P/APRS-IS: ║ STARTING APRS-IS SERVICE                             ║
P2P/APRS-IS: ...
P2P/APRS-IS: Connecting to rotate.aprs2.net:14580...
P2P/APRS-IS: ╔═══════════════════════════════════════════════════════╗
P2P/APRS-IS: ║ ✓ CONNECTED TO APRS-IS NETWORK                       ║
P2P/APRS-IS: ...
P2P/APRS-IS: Sending immediate announcement after connection...
P2P/APRS-IS: ═══════════════════════════════════════════════════════
P2P/APRS-IS: SENDING APRS MESSAGE TO NETWORK:
P2P/APRS-IS: ...
P2P/APRS-IS: ✓ ANNOUNCEMENT TRANSMITTED TO APRS-IS NETWORK
```

This entire sequence should complete in **5-15 seconds** after app startup.

### No "RECEIVED APRS MESSAGE" logs?

Check:
1. Filter is correct: Should see `Filter: p/X1`
2. Other devices are using X1* callsigns
3. Network connectivity

### Messages not appearing on aprs.fi?

Wait 1-2 minutes for propagation. APRS-IS network may take time to update.

## Log Level Recommendations

### Development
```bash
adb logcat *:S P2P/*:V  # Verbose P2P logs only
```

### Production
```bash
adb logcat *:E P2P/*:I  # Info level for P2P, errors for rest
```

### Debug Peer Discovery
```bash
adb logcat *:S P2P/APRS-IS:V P2P/APRSDiscovery:V
```

# BLE Data Transmission Mechanism - Technical Documentation

## Executive Summary

The Android app implements a sophisticated BLE (Bluetooth Low Energy) broadcast messaging system that enables off-grid communication. The system uses BLE advertising to broadcast messages and BLE scanning to receive them. Large messages are automatically split into smaller parcels (max 23 bytes per parcel), each parcel is transmitted sequentially with time-sliced interleaving to allow listening windows, and a NACK-based recovery system handles missing parcels.

## 1. System Architecture Overview

### Core Components

The BLE transmission system consists of five primary components:

1. **BluetoothMessage.java** - Message structure and parcel management
2. **BluetoothSender.java** - BLE advertising and transmission queue
3. **BluetoothListener.java** - BLE scanning and reception
4. **BluetoothCentral.java** - System initialization and coordination
5. **Missing Parcel Recovery System** - NACK requests and message archiving

**Code Locations:**
- `/home/brito/code/geogram/geogram-android/app/src/main/java/offgrid/geogram/ble/`

---

## 2. BluetoothMessage.java - Message Structure

**File:** `/home/brito/code/geogram/geogram-android/app/src/main/java/offgrid/geogram/ble/BluetoothMessage.java`

### 2.1 Message Fields

```java
private String id;              // 2-character random ID (e.g., "AV", "KP")
private String idFromSender;    // Sender's callsign/device ID
private String idDestination;   // Recipient ID (typically "ANY" for broadcast)
private String message;         // The actual message content
private String checksum;        // 4-letter checksum for integrity
private TreeMap<String, String> messageBox;  // Stores all parcels (key=parcelId)
private long timeStamp;         // Message creation timestamp
private boolean messageCompleted;  // True when all parcels received and verified
```

### 2.2 Message ID Generation

**Method:** `generateRandomId()` (lines 119-124)

Generates a unique 2-character ID using random letters A-Z:
```java
Random random = new Random();
char firstChar = (char) ('A' + random.nextInt(26));  // A-Z
char secondChar = (char) ('A' + random.nextInt(26)); // A-Z
return "" + firstChar + secondChar;  // Example: "AV", "KP", "ZZ"
```

### 2.3 Parcel Size Limits

**Constant:** `TEXT_LENGTH_PER_PARCEL = maxSizeOfMessages = 40` (line 24)

- Maximum payload per parcel: **40 characters**
- Total BLE advertising packet: **23 bytes maximum** (after encoding)
- The 40-character limit accounts for parcel headers and encoding overhead

### 2.4 Checksum Calculation

**Method:** `calculateChecksum(String data)` (lines 60-77)

**Algorithm:**
1. Sum ASCII values of all characters in the message
2. Convert sum to base-26 representation using 4 letters (A-Z)
3. Example: "Hello World" → "KPBA"

```java
int sum = 0;
for (char c : data.toCharArray()) {
    sum += c;  // Sum ASCII values
}
// Convert to 4-letter checksum
char[] checksum = new char[4];
for (int i = 0; i < 4; i++) {
    checksum[i] = (char) ('A' + (sum % 26));
    sum /= 26;
}
```

**Limitations:**
- Simple additive checksum provides basic integrity checking
- Vulnerable to collision attacks (different messages can produce same checksum)
- Does not detect reordered characters

### 2.5 Parcel Format

#### Single-Parcel Messages (Commands)

Used for short commands like location pings or system commands.

**Format:** `>+CALLSIGN@GEOCODE#MODEL` or `>/repeat AV2`

**Examples:**
- `>+053156@RY19-IUZS#Android Phone` (location message)
- `>+KO6JZI` (simple ping)
- `>/repeat AV2` (NACK request)

**Characteristics:**
- No splitting required
- Starts with `>` prefix
- Special prefixes: `+` (location), `/` (command)

#### Multi-Parcel Messages

Used for longer messages that exceed 40 characters.

**Header Parcel (Index 0):**
```
Format: >[ID]0:[SENDER]:[DESTINATION]:[CHECKSUM]
Example: >AV0:CR7BBQ:ANY:KPBA
```

**Data Parcels (Index 1+):**
```
Format: >[ID][INDEX]:[DATA]
Example: >AV1:Hello World, this is a test message
Example: >AV2: that continues in the second parcel.
```

### 2.6 Message Splitting Process

**Method:** `splitDataIntoParcels()` (lines 83-113)

**Algorithm:**
1. Calculate total parcels needed: `ceil(messageLength / 40)`
2. Create header parcel (index 0) with metadata
3. Split message into 40-character chunks
4. Create data parcels (index 1, 2, 3, ...)
5. Store all parcels in TreeMap with key = parcelId

**Example:**
```
Message: "An example of a long message to break into multiple parcels."
Length: 61 characters
Parcels needed: ceil(61/40) = 2 data parcels + 1 header = 3 total

Parcel 0: AV0:CR7BBQ:ANY:KPBA
Parcel 1: AV1:An example of a long message to break
Parcel 2: AV2:into multiple parcels.
```

### 2.7 Message Reconstruction

**Method:** `addMessageParcel(String messageParcel)` (lines 178-283)

**Process Flow:**

1. **Receive Parcel** - Extract parcel ID and index
2. **Duplicate Check** - Skip if parcel already received (line 202)
3. **Add to MessageBox** - Store parcel in TreeMap
4. **Parse Header** (index 0) - Extract sender, destination, checksum (lines 229-234)
5. **Check Completeness** - Use `getMissingParcels()` to detect gaps (lines 251-255)
6. **Reconstruct Message** - If no gaps, concatenate all data parcels (lines 258-270)
7. **Verify Checksum** - Compare calculated vs. received checksum (lines 274-279)
8. **Mark Complete** - Set `messageCompleted = true` (line 282)

**Critical Optimization (lines 249-255):**
```java
// Optimization: Check if there are any missing parcels before attempting reconstruction
ArrayList<String> missing = getMissingParcels();
if(!missing.isEmpty()){
    return;  // Don't attempt reconstruction yet
}
```

This prevents unnecessary reconstruction attempts on every parcel arrival.

### 2.8 Missing Parcel Detection

**Method:** `getMissingParcels()` (lines 355-388)

**Algorithm:**
1. Find the highest parcel index received (maxSeen)
2. Check all indices from 0 to maxSeen-1 for gaps
3. Return list of missing parcel IDs

**Example:**
```
Received: AV0, AV1, AV3, AV5
maxSeen = 5
Missing: [AV2, AV4]
```

**Important Note:** Only detects "past gaps" - does not request future parcels beyond maxSeen.

**Method:** `getFirstMissingParcel()` (lines 321-352)

Returns the first gap in sequence, used for NACK requests.

---

## 3. BluetoothSender.java - Transmission System

**File:** `/home/brito/code/geogram/geogram-android/app/src/main/java/offgrid/geogram/ble/BluetoothSender.java`

### 3.1 Core Architecture

**Pattern:** Singleton with message queue

**Key Components:**
- `Queue<String> messageQueue` - FIFO queue for parcels (line 46)
- `Handler handler` - Manages timing and callbacks (line 40)
- `BluetoothLeAdvertiser advertiser` - Android BLE advertising API (line 41)
- `boolean isSending` - Prevents concurrent transmissions (line 47)
- `int parcelsSentInBatch` - Counter for time-sliced interleaving (line 48)

### 3.2 Timing Parameters

**Critical Constants (BluetoothCentral.java lines 33-41):**

```java
advertiseDurationMillis = 1000        // 1 second per parcel
selfIntervalSeconds = 60              // Self-advertise every 60 seconds
parcelsBeforeListening = 3            // Send 3 parcels, then listen
listeningWindowMillis = 5000          // 5 second listening window
selfAdvertiseThrottleThreshold = 5    // Skip self-advertise if queue >= 5 parcels
maxSizeOfMessages = 40                // 40 characters per parcel
```

**Timing Diagram:**
```
Time (seconds):  0    1    2    3    4    5    6    7    8    9    10
                 |----|----|----|----|----|----|----|----|----|----|
Parcel 1:        [TX ]
Parcel 2:             [TX ]
Parcel 3:                  [TX ]
Listening Window:               [----LISTEN----]
Parcel 4:                                       [TX ]
Parcel 5:                                            [TX ]
```

### 3.3 Queue Management

**Method:** `sendMessage(BluetoothMessage msg)` (lines 134-164)

**Process:**
1. **Create BluetoothMessage** - Split into parcels
2. **Prefix Parcels** - Add `>` prefix if not present (line 144)
3. **Duplicate Prevention** - Check if parcel already in queue (line 148)
4. **Queue Parcels** - Add all new parcels to queue (line 152)
5. **Trigger Transmission** - Call `tryToSendNext()` (line 162)

**Duplicate Prevention (lines 148-159):**
```java
if (messageQueue.contains(parcel)) {
    duplicateCount++;
    Log.d(TAG, "Duplicate parcel skipped (already in queue): " + parcel);
} else {
    messageQueue.offer(parcel);
    addedCount++;
}
```

This prevents sending the same parcel multiple times (important for periodic messages like pings).

### 3.4 BLE Advertising Mechanism

**Method:** `tryToSendNext()` (lines 166-248)

**Process Flow:**

1. **Pre-Transmission Checks** (lines 167-177)
   - Is running? Not paused? Not already sending? Queue not empty?
   - Has BLUETOOTH_ADVERTISE permission?

2. **Build Advertising Data** (lines 179-184)
   - Convert message to bytes (UTF-8)
   - Truncate to 23 bytes if necessary (lines 291-293)
   - Create AdvertiseData with service UUID

3. **Configure Advertising Settings** (lines 180-184)
   ```java
   AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY   // Fastest advertising
   AdvertiseSettings.ADVERTISE_TX_POWER_HIGH      // Maximum range
   setConnectable(false)                          // Broadcast only
   ```

4. **Pause Listener** (line 229)
   - Stop scanning during transmission (radio can't TX and RX simultaneously)

5. **Start Advertising** (line 232)
   - Android BLE API: `advertiser.startAdvertising(settings, data, callback)`

6. **Schedule Stop** (line 193-215)
   - Post delayed task (1000ms = `advertiseDurationMillis`)
   - Stop advertising
   - Remove parcel from queue
   - Resume listening
   - Check batch limit for time-slicing

7. **Failsafe Timeout** (lines 235-242)
   - If no callback within 1 second, force stop and retry
   - Prevents deadlock scenarios

### 3.5 Time-Sliced Interleaving

**Purpose:** Allow devices to both transmit and receive messages efficiently

**Implementation (lines 204-214):**

```java
parcelsSentInBatch++;
if (parcelsSentInBatch >= BluetoothCentral.parcelsBeforeListening && !messageQueue.isEmpty()) {
    Log.i(TAG, "Batch limit reached (" + parcelsSentInBatch + " parcels).
                Starting listening window. Queue size: " + messageQueue.size());
    parcelsSentInBatch = 0;  // Reset counter
    handler.postDelayed(() -> {
        Log.i(TAG, "Listening window complete. Resuming transmission.");
        tryToSendNext();
    }, BluetoothCentral.listeningWindowMillis);  // 5 seconds
} else {
    tryToSendNext();  // Continue sending immediately
}
```

**Behavior:**
- Send 3 parcels consecutively (3 seconds)
- Force 5-second listening window
- Resume transmission
- Repeat cycle

**Benefits:**
- Prevents one device from monopolizing the channel
- Allows devices to receive NACK requests
- Improves message delivery success rate

### 3.6 Self-Advertising (Periodic Beacons)

**Purpose:** Periodically broadcast presence/location without external trigger

**Implementation:** `selfAdvertiseTask` Runnable (lines 50-64)

**Process:**
1. Check if running and not paused
2. Check queue size - skip if queue >= 5 parcels (throttling)
3. Send self message (e.g., location ping)
4. Schedule next self-advertise in 60 seconds

**Throttling Logic (lines 55-56):**
```java
if (messageQueue.size() >= BluetoothCentral.selfAdvertiseThrottleThreshold) {
    Log.i(TAG, "Self-advertise skipped: queue congested");
}
```

Prevents self-advertising from interfering with important message transmission.

### 3.7 Error Handling

**Failure Scenarios:**

1. **Advertising Start Failure** (lines 219-225)
   - Log error code
   - Increment batch counter (maintain timing)
   - Try next message

2. **Security Exception** (lines 244-247)
   - Missing BLUETOOTH_ADVERTISE permission
   - Log error, reset state

3. **Callback Timeout** (lines 235-242)
   - Failsafe timer (1 second)
   - Force stop advertising
   - Continue to next parcel

---

## 4. BluetoothListener.java - Reception System

**File:** `/home/brito/code/geogram/geogram-android/app/src/main/java/offgrid/geogram/ble/BluetoothListener.java`

### 4.1 Core Architecture

**Pattern:** Singleton with continuous scanning

**Key Components:**
- `BluetoothLeScanner scanner` - Android BLE scanning API (line 28)
- `Map<String, Long> recentMessages` - Duplicate detection cache (line 35)
- `ScanCallback scanCallback` - Handles scan results (line 117)
- `boolean isPaused` - Tracks pause state for coordination with sender (line 30)

### 4.2 Duplicate Detection

**Constants (lines 32-33):**
```java
DUPLICATE_INTERVAL_MS = 3000   // Ignore duplicates within 3 seconds
MESSAGE_EXPIRY_MS = 60000      // Discard messages older than 60 seconds
```

**Algorithm (lines 138-156):**

1. **Clean Up Old Entries** (lines 141-147)
   - Remove entries older than 60 seconds
   - Prevents memory growth

2. **Check for Duplicates** (lines 150-153)
   ```java
   Long lastSeen = recentMessages.get(textPayload);
   if (lastSeen != null && now - lastSeen < DUPLICATE_INTERVAL_MS) {
       return;  // Skip duplicate
   }
   ```

3. **Update Timestamp** (line 156)
   - Record current time for this message

**Rationale:**
- BLE advertising repeats rapidly (10-100ms intervals)
- Same device may receive same parcel 10-30 times in 1 second
- 3-second window balances duplicate prevention vs. legitimate retransmissions

### 4.3 BLE Scanning Configuration

**Method:** `startListening()` (lines 52-68)

**Settings (lines 57-59):**
```java
ScanSettings.Builder()
    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)  // Fastest scanning
    .build()
```

**Scan Filter:** `null` (line 62) - Scans all BLE devices (no filtering)

**Why No Filtering?**
- Service UUID filtering often misses packets
- Software-based filtering more reliable
- Performance impact negligible

### 4.4 Parcel Reception

**Callback:** `scanCallback.onScanResult()` (lines 118-167)

**Process Flow:**

1. **Extract Raw Data** (lines 120-124)
   - Device address, RSSI (signal strength), scan record bytes

2. **Parse Service Data** (lines 127-135)
   ```java
   for (byte[] data : result.getScanRecord().getServiceData().values()) {
       String decoded = tryDecodeText(data);  // UTF-8 decode
       if (decoded != null && decoded.startsWith(">")) {
           textPayload = decoded;
           break;
       }
   }
   ```

3. **Validate Parcel** (line 137)
   - Must start with `>` prefix

4. **Duplicate Check** (lines 138-153)
   - Use timestamp-based deduplication

5. **Trigger Event** (line 159)
   - `EventControl.startEvent(EventType.BLUETOOTH_MESSAGE_RECEIVED, textPayload)`
   - Forwards to EventBleMessageReceived handler

6. **Log Reception** (lines 161-165)
   - Log device address and message content

### 4.5 Pause/Resume Coordination

**Purpose:** Synchronize with sender (radio can't TX and RX simultaneously)

**Methods:**
- `pauseListening()` (lines 83-94) - Stop scanning temporarily
- `resumeListening()` (lines 96-101) - Restart scanning

**Usage by Sender:**
```java
// BluetoothSender.java line 229
BluetoothListener.getInstance(context).pauseListening();  // Before advertising

// BluetoothSender.java line 201
BluetoothListener.getInstance(context).resumeListening();  // After advertising
```

**Critical for Performance:**
- Prevents interference between TX and RX
- Ensures clean signal transmission
- Maximizes range and reliability

---

## 5. Known Reliability Issues

### Issue 1: No Archive Event Registration

**Problem:** EventArchiveMessageBLE is never registered with EventControl

**Impact:**
- Messages may not be archived for NACK recovery
- NACK requests will fail with "Unable to find archived message"

**Evidence:**
- EventArchiveMessageBLE.java exists but is never instantiated
- BluetoothCentral.java does not register this event

**Location:** BluetoothCentral.java setupEvents()

---

### Issue 2: No Checksum Retry

**Problem:** Checksum mismatch causes silent message discard

**Impact:**
- Receiver has all parcels but corrupted data
- No NACK request sent (all parcels present)
- Message lost without notification

**Code (BluetoothMessage.java lines 276-279):**
```java
if(currentChecksum.equals(this.checksum) == false){
    Log.i(TAG, "Checksum mismatch: expected " + this.checksum + ", got " + currentChecksum);
    return;  // Silent discard - no retry
}
```

---

### Issue 3: No Message Reconstruction Timeout

**Problem:** Partial messages are stored indefinitely in EventBleMessageReceived.messages HashMap

**Impact:**
- Memory leak if messages never complete
- HashMap grows unbounded

**Code (EventBleMessageReceived.java lines 25, 62-71):**
```java
HashMap<String, BluetoothMessage> messages = new HashMap<>();  // Never cleaned up

if(messages.containsKey(id)){
    msg = messages.get(id);
}else {
    msg = new BluetoothMessage();
    messages.put(id, msg);  // Never removed if incomplete
}
```

**Missing:** No timeout mechanism to remove stale incomplete messages

---

### Issue 4: NACK Storm Risk

**Problem:** Multiple receivers can send duplicate NACK requests for same parcel

**Impact:**
- Sender's queue flooded with duplicate `/repeat` commands
- Same parcel transmitted 10-20 times
- Channel congestion

**No Mitigation:** Code has no NACK deduplication or rate limiting

---

### Issue 5: Archive Size Limit

**Problem:** Archive limited to 1000 messages, cleared entirely on overflow

**Impact:**
- In high-traffic scenarios, archive clears completely
- All NACK requests fail until archive rebuilds
- 1-hour expiry may be too long (memory usage)

**Code (MissingMessagesBLE.java lines 74-77):**
```java
if(messagesArchived.size() >= maxMessagesArchived){
    Log.i(TAG, "Emergency cleanup: clearing all archived messages due to spam");
    messagesArchived.clear();  // Nuclear option
}
```

---

### Issue 6: No ACK System

**Problem:** Sender never knows if messages were received successfully

**Impact:**
- No confirmation of delivery
- No automatic retry for failed messages
- Relies entirely on receiver-initiated NACKs

**Evidence:** BLUETOOTH_ACKNOWLEDGE_RECEIVED event is commented out (BluetoothCentral.java line 86)

---

### Issue 7: Duplicate Detection Window Too Short

**Problem:** 3-second duplicate window may reject legitimate retransmissions

**Scenario:**
1. Device A sends parcel AV2 at T=0
2. Device B receives AV2 at T=0.5s, sends NACK for AV1
3. Device A retransmits AV1 at T=2s
4. Device B receives retransmitted AV2 at T=3.1s ← Accepted (outside window)
5. But if B received at T=2.9s ← Rejected as duplicate

**Impact:** NACK retransmissions may be incorrectly filtered

---

## 6. Timing and Reliability Analysis

### 6.1 All Timeout Values

| Parameter | Value | Location | Purpose |
|-----------|-------|----------|---------|
| advertiseDurationMillis | 1000ms | BluetoothCentral.java:33 | BLE advertising duration per parcel |
| selfIntervalSeconds | 60s | BluetoothCentral.java:35 | Self-advertise interval (location pings) |
| parcelsBeforeListening | 3 | BluetoothCentral.java:39 | Parcels before forced listening window |
| listeningWindowMillis | 5000ms | BluetoothCentral.java:40 | Duration of forced listening window |
| selfAdvertiseThrottleThreshold | 5 | BluetoothCentral.java:41 | Skip self-advertise if queue >= N |
| DUPLICATE_INTERVAL_MS | 3000ms | BluetoothListener.java:32 | Ignore duplicate parcels within N ms |
| MESSAGE_EXPIRY_MS | 60000ms | BluetoothListener.java:33 | Expire duplicate detection entries after N ms |
| Archive Expiry | 3600000ms (1hr) | MissingMessagesBLE.java:51 | Remove archived messages after 1 hour |
| Failsafe Timeout | 1000ms | BluetoothSender.java:235-242 | Force stop advertising if no callback |

### 6.2 Transmission Speed Calculations

**Single Parcel:**
- Advertising duration: 1000ms
- Total time: **1 second**

**Multi-Parcel Message (10 parcels):**
- Time without listening windows: 10 × 1000ms = 10 seconds
- Listening windows: ceil(10/3) = 4 windows × 5000ms = 20 seconds
- **Total time: 30 seconds**

**Throughput:**
- Raw data: 40 chars/second = **320 bits/second**
- With time-slicing: ~13 chars/second = **104 bits/second**

---

## 7. Complete Message Flow Diagram

### 7.1 Successful Multi-Parcel Transmission

```
┌─────────────┐                                    ┌─────────────┐
│  Sender A   │                                    │  Receiver B │
└──────┬──────┘                                    └──────┬──────┘
       │                                                  │
       │ 1. Create BluetoothMessage                      │
       │    "Hello World from device A"                  │
       │    Split into parcels (2)                       │
       │                                                  │
       │ 2. Queue parcels                                │
       │    >AV0:A:ANY:KPBA                             │
       │    >AV1:Hello World from device A              │
       │                                                  │
       │ 3. Start advertising parcel 0                   │
       ├─────────[BLE Advertising]─────────────────────>│
       │        >AV0:A:ANY:KPBA                         │
       │                                                  │
       │                                                  │ 4. Scan receives parcel 0
       │                                                  │    Add to messageBox
       │                                                  │    Parse header: sender=A, checksum=KPBA
       │                                                  │    getMissingParcels() → [AV1]
       │                                                  │    Wait for more...
       │                                                  │
       │ 5. Stop advertising (1s elapsed)                │
       │    Resume listening                             │
       │                                                  │
       │ 6. Start advertising parcel 1                   │
       ├─────────[BLE Advertising]─────────────────────>│
       │        >AV1:Hello World from device A          │
       │                                                  │
       │                                                  │ 7. Scan receives parcel 1
       │                                                  │    Add to messageBox
       │                                                  │    getMissingParcels() → []
       │                                                  │    All parcels received!
       │                                                  │
       │                                                  │ 8. Reconstruct message
       │                                                  │    Concatenate: "Hello World from device A"
       │                                                  │    Calculate checksum: KPBA ✓
       │                                                  │    Mark as complete
       │                                                  │
       │                                                  │ 9. Trigger BLE_BROADCAST_RECEIVED
       │                                                  │    Save to database
       │                                                  │    Display in UI
       │                                                  │
```

### 7.2 Missing Parcel with NACK Recovery

```
┌─────────────┐                                    ┌─────────────┐
│  Sender A   │                                    │  Receiver B │
└──────┬──────┘                                    └──────┬──────┘
       │                                                  │
       │ 1. Queue parcels (3)                            │
       │    >AV0:A:ANY:KPBA                             │
       │    >AV1:First parcel                           │
       │    >AV2:Second parcel                          │
       │                                                  │
       │ 2. Transmit parcel 0                            │
       ├─────────[BLE Advertising]─────────────────────>│
       │        >AV0:A:ANY:KPBA                         │ Received ✓
       │                                                  │
       │ 3. Transmit parcel 1                            │
       ├─────────[BLE Advertising]─────X─────────────   │
       │        >AV1:First parcel              LOST!     │ Not received
       │                                                  │
       │ 4. Transmit parcel 2                            │
       ├─────────[BLE Advertising]─────────────────────>│
       │        >AV2:Second parcel                      │ Received ✓
       │                                                  │
       │                                                  │ 5. Receiver checks parcels
       │                                                  │    messageBox: AV0, AV2
       │                                                  │    getMissingParcels() → [AV1]
       │                                                  │    Gap detected!
       │                                                  │
       │                                                  │ 6. Send NACK request
       │<────────[BLE Advertising]──────────────────────┤
       │        >/repeat AV1                             │
       │                                                  │
       │ 7. Receive NACK request                         │
       │    Parse: messageId=AV, parcelNumber=1         │
       │    Lookup archive: messagesArchived["AV"]      │
       │    Get parcel: parcels[1] = ">AV1:First parcel"│
       │    Re-queue parcel                              │
       │                                                  │
       │ 8. Retransmit parcel 1                          │
       ├─────────[BLE Advertising]─────────────────────>│
       │        >AV1:First parcel                       │ Received ✓
       │                                                  │
       │                                                  │ 9. Complete message
       │                                                  │    getMissingParcels() → []
       │                                                  │    Reconstruct + verify checksum
       │                                                  │    Mark as complete
       │                                                  │    Save to database
       │                                                  │
```

---

## 8. Recommendations for Improvement

### High Priority

1. **Register Archive Event**
   - Add `EventControl.addEvent()` for EventArchiveMessageBLE in BluetoothCentral.setupEvents()
   - Or implement direct archiving in BluetoothSender.sendMessage()

2. **Implement Message Reconstruction Timeout**
   - Clean up incomplete messages after 2-5 minutes
   - Prevent memory leak in EventBleMessageReceived.messages HashMap

3. **Add Checksum Retry Logic**
   - On checksum mismatch, request all parcels again via NACK
   - Log to database for diagnostic purposes

### Medium Priority

4. **NACK Deduplication**
   - Track recently sent NACK requests (3-second window)
   - Prevent duplicate NACK requests for same parcel

5. **ACK System**
   - Implement sender-side tracking of message delivery
   - Automatic retry for unacknowledged messages after 10-15 seconds

6. **Optimize Archive Size**
   - Implement LRU eviction instead of emergency clear
   - Reduce expiry time to 15-30 minutes (balance memory vs. NACK window)

### Low Priority

7. **Increase Duplicate Window**
   - Increase DUPLICATE_INTERVAL_MS to 5-7 seconds
   - Better accommodate NACK retransmissions

8. **Add Parcel Sequence Numbers**
   - Validate parcel ordering independent of TreeMap
   - Detect reordering attacks or corruption

9. **Implement Stronger Checksum**
   - Use CRC16 or MD5 instead of simple additive checksum
   - Detect transposition and bit-flip errors

10. **Add Transmission Metrics**
    - Track success/failure rates
    - Monitor NACK request frequency
    - Measure average message completion time

---

## Document Metadata

**Generated:** 2025-11-11
**Android App:** Geogram Off-Grid Messaging
**BLE Stack:** Android BLE Advertising API (Eddystone-compatible)
**Max Throughput:** ~104 bits/second (with time-slicing)
**Max Message Size:** Unlimited (40 chars per parcel × N parcels)
**Reliability:** Best-effort with receiver-initiated NACK recovery

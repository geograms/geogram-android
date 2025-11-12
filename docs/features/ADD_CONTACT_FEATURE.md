# Add Contact Feature

## Overview

The "Add Contact" feature allows users to manually add contacts to their message list by entering a callsign. The app will attempt to fetch profile information from the server, but will create a basic contact folder even if the profile is not found.

## User Interface

### Location
- **Messages Screen** (MessagesFragment)
- **Button**: White "+" FloatingActionButton in bottom-right corner

### Flow

1. **User clicks "+" button**
   - Dialog appears: "Add Contact"
   - Input field: Enter callsign (auto-uppercase)
   - Example hint: "e.g., CR7BBQ"

2. **Callsign Validation**
   - Must be < 10 characters
   - First two characters: alphanumeric (A-Z, 0-9)
   - Remaining characters: alphanumeric or dash (-)
   - Invalid examples: "-ABC", "A-BC", "ABCDEFGHIJ"
   - Valid examples: "CR7BBQ", "X2DEVS", "K5-ABC"

3. **Server Query**
   - URL: `https://geogram.offgrid.network/profile/{CALLSIGN}`
   - Method: GET
   - Timeout: 10 seconds
   - Progress: "Fetching profile for {CALLSIGN}..."

4. **Profile Processing**

   **Case A: Profile Found on Server**
   - Server returns profile.json with:
     - callsign
     - name
     - npub (NOSTR public key)
     - description
     - hasProfilePic
     - messagesArchived
     - lastUpdated
     - firstTimeSeen
     - profileType
     - profileVisibility
   - Toast: "Added contact: {CALLSIGN} (from server)"

   **Case B: Profile Not Found on Server**
   - Creates basic profile locally:
     ```json
     {
       "callsign": "{CALLSIGN}",
       "name": "",
       "npub": null,
       "description": "",
       "hasProfilePic": false,
       "messagesArchived": 0,
       "firstTimeSeen": 1704067200000,
       "lastUpdated": 1704067200000,
       "profileType": "PERSON",
       "profileVisibility": "PUBLIC"
     }
     ```
   - Toast: "Added contact: {CALLSIGN} (not on server)"

5. **Folder Structure Creation**

   Regardless of server response, creates:
   ```
   /data/data/offgrid.geogram.geogram/files/contacts/{CALLSIGN}/
   ├── profile.json          ← from server or basic
   ├── chat/                 ← for chat messages
   │   └── (empty initially)
   └── relay/                ← for relay messages
       ├── inbox/            ← received messages
       ├── outbox/           ← messages to send
       └── sent/             ← successfully relayed
   ```

6. **Navigation**
   - Automatically navigates to ContactDetailFragment
   - Shows contact folder view with:
     - Chat tab (empty)
     - Relay Messages tab (empty)

## Use Cases

### Use Case 1: Add Known Contact from Server
**Scenario**: User wants to add a contact who has a profile on the server

1. User clicks "+"
2. Enters "CR7BBQ"
3. App fetches profile from server
4. Folder created with full profile information
5. User can immediately start chatting or sending relay messages

### Use Case 2: Add Contact Not on Server
**Scenario**: User wants to add a contact who doesn't have a server profile yet

1. User clicks "+"
2. Enters "X2DEVS"
3. App tries server (404 Not Found)
4. Folder created with basic profile
5. User can still chat and send relay messages
6. Profile will be updated if contact later registers on server

### Use Case 3: Add Contact for Future Communication
**Scenario**: User knows someone's callsign but wants to prepare for future contact

1. User clicks "+"
2. Enters callsign
3. Folder structure ready
4. User can pre-compose relay messages in outbox
5. Messages will be sent during next BLE sync

### Use Case 4: Emergency Contact Setup
**Scenario**: User needs to set up contacts while offline

1. No internet connection
2. User clicks "+" and enters callsigns
3. Basic folders created locally
4. Can compose relay messages offline
5. Everything syncs when connectivity restored

## Technical Details

### Server API
- **Endpoint**: `GET /profile/{CALLSIGN}`
- **Response**: JSON profile or 404
- **Timeout**: 10 seconds
- **Error Handling**: Creates basic profile on failure

### Profile Validation
```java
public static boolean isValidCallsign(String callsign) {
    // Length: 2-9 characters
    if (callsign.length() < 2 || callsign.length() >= 10) return false;

    // First two: alphanumeric
    char c1 = callsign.charAt(0);
    char c2 = callsign.charAt(1);
    if (!Character.isLetterOrDigit(c1)) return false;
    if (!Character.isLetterOrDigit(c2)) return false;

    // Remaining: alphanumeric or dash
    for (int i = 2; i < callsign.length(); i++) {
        char c = callsign.charAt(i);
        if (!Character.isLetterOrDigit(c) && c != '-') return false;
    }

    return true;
}
```

### Folder Creation
```java
public boolean ensureContactStructure(String callsign) {
    File contactDir = getContactDir(callsign);
    File chatDir = getChatDir(callsign);
    File relayDir = getRelayDir(callsign);
    File inboxDir = getRelayInboxDir(callsign);
    File outboxDir = getRelayOutboxDir(callsign);
    File sentDir = getRelaySentDir(callsign);

    return contactDir.mkdirs() &&
           chatDir.mkdirs() &&
           relayDir.mkdirs() &&
           inboxDir.mkdirs() &&
           outboxDir.mkdirs() &&
           sentDir.mkdirs();
}
```

### Profile Merging
If a contact is later found on server, the profile is merged using smart logic:
- Newer `lastUpdated` wins for current fields
- Missing fields are filled in
- `messagesArchived` takes max value
- `firstTimeSeen` takes earliest value
- Associated profiles are unioned

## Benefits

### 1. Flexibility
- Works with or without server connection
- No dependency on centralized server for basic functionality

### 2. Resilience
- Offline-first approach
- Can set up contacts in advance of connectivity

### 3. Decentralization
- Doesn't require server registration to communicate
- P2P relay messaging works regardless of server status

### 4. Emergency Preparedness
- Can add contacts manually in disaster scenarios
- Pre-configure communication channels before events

### 5. Privacy
- Optional server registration
- Can operate completely offline if desired

## Error Handling

### Network Errors
- **No Internet**: Creates basic profile, user notified
- **Timeout**: Falls back to basic profile after 10 seconds
- **Server Down**: Creates basic profile, user notified

### Validation Errors
- **Empty Callsign**: "Please enter a callsign"
- **Invalid Format**: "Invalid callsign format"
- **Folder Creation Failed**: "Failed to create contact folder"
- **Save Failed**: "Failed to save profile"

### User Feedback
All operations provide clear Toast messages:
- Success: "Added contact: {CALLSIGN} (from server)" or "(not on server)"
- Progress: "Fetching profile for {CALLSIGN}..."
- Errors: Specific error message explaining what went wrong

## Future Enhancements

### 1. QR Code Scanning
- Scan QR code with callsign and npub
- Auto-populate contact information

### 2. NFC Contact Exchange
- Tap phones to exchange contact info
- Automatic folder creation

### 3. Batch Import
- Import multiple contacts from CSV/JSON
- Useful for event organizers

### 4. Contact Discovery
- List nearby contacts via BLE
- Add contacts from proximity

### 5. Server-Side Search
- Search server directory by name or location
- Add contacts by criteria other than callsign

## Testing

### Manual Testing Checklist

- [ ] Click "+" button opens dialog
- [ ] Empty callsign shows error
- [ ] Invalid callsign shows error
- [ ] Valid callsign on server adds contact
- [ ] Valid callsign not on server creates basic profile
- [ ] Folder structure created correctly
- [ ] Navigation to ContactDetailFragment works
- [ ] Both tabs (Chat/Relay) accessible
- [ ] Can send chat messages to new contact
- [ ] Can create relay messages for new contact
- [ ] Profile.json saved correctly
- [ ] Offline mode creates basic profile
- [ ] Network timeout handled gracefully

### Test Callsigns

**On Server** (if CR7BBQ exists):
- CR7BBQ → should fetch full profile

**Not on Server**:
- TEST01 → should create basic profile
- X9ABCD → should create basic profile

**Invalid**:
- "" → error
- "A" → error (too short)
- "ABCDEFGHIJ" → error (too long)
- "-ABC" → error (invalid first char)
- "AB.XYZ" → error (invalid char)

## Implementation Files

- `ProfileAPI.java` - Server communication
- `MessagesFragment.java` - UI and user interaction
- `ContactFolderManager.java` - Folder structure management
- `ContactProfile.java` - Profile data model
- `ContactDetailFragment.java` - Contact folder view
- `fragment_messages.xml` - UI layout with FAB

## Conclusion

The Add Contact feature provides a flexible, resilient way to manually add contacts to the messaging system. It works both online and offline, queries the server for profile information when available, but doesn't depend on it. This design aligns with the decentralized, offline-first philosophy of the Geogram project.

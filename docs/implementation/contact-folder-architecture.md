# Contact Folder Architecture

## Overview

Inspired by the server's CallSignDatabase ZIP archive approach, each contact on Android gets a **self-contained directory structure** that acts as a "folder" containing all data for that contact.

## Motivation

From server implementation (`CallSignDatabase.java`):
- Each callsign gets its own ZIP archive: `A/B/ABC123.zip`
- Inside: `profile.json`, `events/` folder, structured subdirectories
- Self-contained, organized, easy to manage

Android adaptation:
- Use **regular directories** instead of ZIP (lighter weight)
- Same organizational benefits
- Clear separation between chat and relay messages

## Directory Structure

```
/data/data/offgrid.geogram.geogram/files/contacts/<CALLSIGN>/
├── profile.json                  # Contact profile data
├── chat/                         # Chat messages (NOSTR-style)
│   ├── 2025-01.ndjson           # Messages by month (newline-delimited JSON)
│   ├── 2025-02.ndjson
│   └── attachments/             # Chat attachments
│       ├── <hash>.jpg
│       └── <hash>.png
└── relay/                        # Relay messages (email-style)
    ├── inbox/                    # Messages received via relay
    │   ├── <message-id>.md
    │   └── <message-id>.md
    ├── outbox/                   # Messages queued to send
    │   └── <message-id>.md
    └── sent/                     # Successfully relayed messages
        └── <message-id>.md
```

### Key Features

1. **Self-contained**: All data for a contact in one place
2. **Organized**: Chat vs relay clearly separated
3. **Scalable**: Each contact's data is independent
4. **Easy cleanup**: Delete contact = delete folder
5. **Portable**: Can zip and export entire contact folder

## Data Models

### profile.json

```json
{
  "callsign": "ABC123",
  "name": "Alice",
  "npub": "npub1...",
  "description": "Friend from hiking group",
  "hasProfilePic": true,
  "messagesArchived": 127,
  "lastUpdated": 1704067200,
  "firstTimeSeen": 1672531200,
  "profileType": "PERSON",
  "profileVisibility": "PUBLIC"
}
```

### chat/2025-01.ndjson

Newline-delimited JSON, one message per line:

```json
{"id":"<hash>","from":"npub1...","to":"npub2...","timestamp":1704067200,"message":"Hello!","type":"text"}
{"id":"<hash>","from":"npub2...","to":"npub1...","timestamp":1704067260,"message":"Hi there!","type":"text"}
```

### relay/inbox/<message-id>.md

Relay messages use the existing markdown format (see `RelayMessage.java`):

```markdown
> 2025-11-10 14:30_00 -- LOCAL-K5ABC to REMOTE-W6XYZ

Subject: Weather Update

The storm is approaching from the north. Stay safe.

---
FROM: LOCAL-K5ABC
TO: REMOTE-W6XYZ
TYPE: private
PRIORITY: urgent
TTL: 604800
TIMESTAMP: 1731250200
HOP_COUNT: 0
```

## UI Navigation Flow

### Before (Current)

```
MessagesFragment (list of conversations)
  ↓ click on contact
ConversationChatFragment (chat view)
```

### After (New Folder-Based)

```
MessagesFragment (list of contacts)
  ↓ click on contact
ContactDetailFragment (folder view)
  ├── Chat tab
  │   └── ConversationChatFragment (existing)
  └── Relay Messages tab
      └── RelayMessagesListFragment (new, email-style)
          ↓ click on relay message
          RelayMessageThreadFragment (view/reply)
```

### Special Cases

**Contact with only chat messages:**
- Opens directly to ConversationChatFragment (skip folder view)

**Contact with only relay messages:**
- Opens to ContactDetailFragment with Relay tab selected

**Contact with both:**
- Opens to ContactDetailFragment with tabs visible

## Implementation Components

### 1. ContactFolderManager.java

Manages contact folder structure, similar to server's `CallSignDatabase.java`:

```java
public class ContactFolderManager {
    private final Context context;
    private final File baseDir;

    public ContactFolderManager(Context context) {
        this.context = context;
        this.baseDir = new File(context.getFilesDir(), "contacts");
    }

    // Get contact's root directory
    public File getContactDir(String callsign) {
        return new File(baseDir, normalizeCallsign(callsign));
    }

    // Get chat directory
    public File getChatDir(String callsign) {
        return new File(getContactDir(callsign), "chat");
    }

    // Get relay directory
    public File getRelayDir(String callsign) {
        return new File(getContactDir(callsign), "relay");
    }

    // Save profile
    public void saveProfile(String callsign, ContactProfile profile) {
        File profileFile = new File(getContactDir(callsign), "profile.json");
        // ... save JSON
    }

    // Load profile
    public ContactProfile loadProfile(String callsign) {
        File profileFile = new File(getContactDir(callsign), "profile.json");
        // ... load JSON
    }

    // Check what types of messages exist
    public ContactContentType getContentType(String callsign) {
        boolean hasChat = getChatMessageCount(callsign) > 0;
        boolean hasRelay = getRelayMessageCount(callsign) > 0;

        if (hasChat && hasRelay) return ContactContentType.BOTH;
        if (hasChat) return ContactContentType.CHAT_ONLY;
        if (hasRelay) return ContactContentType.RELAY_ONLY;
        return ContactContentType.NONE;
    }
}
```

### 2. ContactProfile.java

Java model matching the server's `Profile.java` structure:

```java
public class ContactProfile {
    private String callsign = "";
    private String name = "";
    private String npub = "";
    private String description = "";
    private boolean hasProfilePic = false;
    private long messagesArchived = 0;
    private long lastUpdated = -1;
    private long firstTimeSeen = -1;

    // JSON serialization
    public String toJson() { /* GSON */ }
    public static ContactProfile fromJson(String json) { /* GSON */ }

    // Merge from another profile (server logic)
    public boolean mergeFrom(ContactProfile other) { /* ... */ }
}
```

### 3. ContactDetailFragment.java

Shows folder view with tabs:

```java
public class ContactDetailFragment extends Fragment {
    private String callsign;
    private TabLayout tabLayout;
    private ViewPager2 viewPager;

    public static ContactDetailFragment newInstance(String callsign) {
        // ...
    }

    @Override
    public View onCreateView(...) {
        // Setup tabs: "Chat" and "Relay Messages"
        // ViewPager with fragments for each tab
    }

    // Adapter provides:
    // - Position 0: ConversationChatFragment (existing)
    // - Position 1: RelayMessagesListFragment (new)
}
```

### 4. RelayMessagesListFragment.java

Email-style list of relay messages for this contact:

```java
public class RelayMessagesListFragment extends Fragment {
    private String callsign;
    private RecyclerView messagesRecyclerView;
    private RelayMessageListAdapter adapter;

    // Load messages from:
    // contacts/<callsign>/relay/inbox/*.md
    // contacts/<callsign>/relay/outbox/*.md
    // contacts/<callsign>/relay/sent/*.md

    // Show each message with:
    // - Subject/title
    // - From/To
    // - Timestamp
    // - Delivery status icon (sent/delivered/read)
    // - Priority indicator
}
```

### 5. RelayMessageThreadFragment.java

View and reply to specific relay message:

```java
public class RelayMessageThreadFragment extends Fragment {
    private String messageId;
    private RelayMessage message;

    // Display:
    // - Full message content
    // - Metadata (from, to, timestamp, priority, TTL)
    // - Delivery status timeline
    // - Reply button
    // - Forward button

    // Reply creates new message in outbox:
    // - To: original sender
    // - Subject: Re: <original subject>
    // - References: original message ID
}
```

### 6. DeliveryStatus Tracking

Track relay message delivery progress:

```java
public class DeliveryStatus {
    public enum Status {
        DRAFT,       // Being composed
        QUEUED,      // In outbox, waiting to send
        RELAYING,    // Being transmitted via BLE
        SENT,        // Successfully sent to relay
        DELIVERED,   // Confirmed received by destination
        READ         // Read receipt received
    }

    private String messageId;
    private Status status;
    private List<DeliveryEvent> timeline;

    public static class DeliveryEvent {
        long timestamp;
        Status status;
        String details; // e.g., "Relayed via CR7BBQ"
    }
}
```

## Migration Strategy

### Phase 1: Create ContactFolderManager
- Implement directory structure management
- Create ContactProfile model
- Unit tests for folder operations

### Phase 2: Migrate Chat Messages
- Update DatabaseMessages to use contact folders
- Convert existing messages to NDJSON format
- Keep backward compatibility with existing database

### Phase 3: Migrate Relay Messages
- Update RelayStorage to use contact-specific folders
- Move from global relay/inbox to contacts/<callsign>/relay/inbox
- Update RelayFragment to show global relay status

### Phase 4: Build Contact Detail UI
- Create ContactDetailFragment
- Create RelayMessagesListFragment
- Create RelayMessageThreadFragment

### Phase 5: Update Navigation
- Modify MessagesFragment to navigate to ContactDetailFragment
- Smart navigation based on ContactContentType
- Update MainActivity relay button to show global relay screen

### Phase 6: Delivery Status
- Implement DeliveryStatus tracking
- Add status indicators to UI
- Update RelayMessageSync to track delivery events

## Benefits

1. **Organization**: Clear separation of chat vs relay messages
2. **Scalability**: Each contact's data is isolated and manageable
3. **Performance**: No need to scan global message database
4. **Features**: Easier to implement per-contact backups, exports, deletion
5. **Consistency**: Matches server architecture pattern
6. **User Experience**: Folder metaphor is intuitive and familiar

## Technical Considerations

### Storage

- **Chat messages**: NDJSON format (append-only, efficient for logs)
- **Relay messages**: Markdown format (human-readable, existing format)
- **Attachments**: Separate subdirectories with content-addressed filenames

### Caching

- Keep ContactProfile objects in memory cache (LRU)
- Lazy-load message lists when folder is opened
- Cache message counts for MessagesFragment

### Synchronization

- Global relay sync (existing RelayMessageSync) handles BLE transfer
- On receive: route message to correct contact folder based on sender
- On send: collect from all contact outboxes

### Indexing

- Create lightweight index file for fast lookups:
  ```json
  {
    "contacts": [
      {
        "callsign": "ABC123",
        "lastActivity": 1704067200,
        "chatMessages": 45,
        "relayMessages": 12,
        "unread": 3
      }
    ]
  }
  ```

## Example Usage

```java
// Initialize manager
ContactFolderManager cfm = new ContactFolderManager(context);

// Save a chat message
File chatDir = cfm.getChatDir("CR7BBQ");
String monthFile = "2025-11.ndjson";
File messagesFile = new File(chatDir, monthFile);
// Append message as JSON line

// Save a relay message
RelayMessage relayMsg = new RelayMessage();
relayMsg.setId("TEST-" + System.currentTimeMillis());
relayMsg.setFromCallsign("CR7BBQ");
relayMsg.setToCallsign("X2DEVS");
// ...
File relayInbox = new File(cfm.getRelayDir("CR7BBQ"), "inbox");
File msgFile = new File(relayInbox, relayMsg.getId() + ".md");
Files.write(msgFile.toPath(), relayMsg.toMarkdown().getBytes());

// Check what content exists
ContactContentType type = cfm.getContentType("CR7BBQ");
if (type == ContactContentType.BOTH) {
    // Show ContactDetailFragment with tabs
} else if (type == ContactContentType.CHAT_ONLY) {
    // Navigate directly to ConversationChatFragment
}

// Load profile
ContactProfile profile = cfm.loadProfile("CR7BBQ");
if (profile == null) {
    // Create new profile
    profile = new ContactProfile();
    profile.setCallsign("CR7BBQ");
    profile.setFirstTimeSeen(System.currentTimeMillis());
}
```

## Testing Strategy

### Unit Tests

- ContactFolderManager operations
- ContactProfile serialization/merging
- Directory creation and cleanup
- Content type detection

### Integration Tests

- Message migration from old to new structure
- Relay message routing to contact folders
- Chat message append operations
- Profile updates and persistence

### UI Tests

- Navigation between MessagesFragment and ContactDetailFragment
- Tab switching in ContactDetailFragment
- Relay message list display
- Message thread viewing and replying

## Future Enhancements

1. **Export Contact**: Zip entire contact folder for backup/sharing
2. **Import Contact**: Import zipped contact folder
3. **Contact Merging**: Merge duplicate contacts using Profile.mergeFrom() logic
4. **Search**: Full-text search across all contact messages
5. **Statistics**: Per-contact analytics (message frequency, response time, etc.)

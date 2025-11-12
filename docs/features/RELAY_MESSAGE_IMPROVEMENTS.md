# Relay Message Improvements

## Overview

Three major improvements to the relay messaging system:

1. **Reply Functionality**: Full implementation of replying to relay messages
2. **Menu Cleanup**: Removed duplicate "Relay" menu item
3. **Status Visibility**: Relay messages now visible in relay status counts

## 1. Reply Functionality

### User Experience

When viewing a relay message, clicking "Reply" opens a compose dialog:

```
Reply to CR7BBQ

Original Subject:
Meeting reminder

Subject (optional):
[Re: Meeting reminder]

Message:
[Enter your reply...]

[Send] [Cancel]
```

### Features

**Auto-populated Fields**:
- To: Set to original sender's callsign
- Subject: Pre-filled with "Re: [original subject]" if subject exists
- Smart "Re:" handling: Won't add multiple "Re:" prefixes

**Subject Handling**:
```java
if (message.getSubject().startsWith("Re: ")) {
    // Don't add "Re: " multiple times
    replySubject = message.getSubject();
} else {
    replySubject = "Re: " + message.getSubject();
}
```

**Reply Flow**:
1. User clicks "Reply" button
2. Dialog shows original subject for context
3. User enters reply message
4. Reply saved to sender's outbox
5. Returns to message list
6. Toast: "Reply sent to outbox"

### Implementation

**RelayMessageThreadFragment.java**:

```java
private void replyToMessage() {
    if (message == null) return;

    AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
    builder.setTitle("Reply to " + message.getFromCallsign());

    // Show original subject for context
    if (message.getSubject() != null && !message.getSubject().isEmpty()) {
        // Display original subject in gray text
    }

    // Subject input pre-filled with "Re: "
    EditText subjectInput = new EditText(requireContext());
    if (message.getSubject() != null && !message.getSubject().isEmpty()) {
        String replySubject = message.getSubject().startsWith("Re: ")
            ? message.getSubject()
            : "Re: " + message.getSubject();
        subjectInput.setText(replySubject);
    }

    // Message content input
    EditText contentInput = new EditText(requireContext());
    contentInput.setHint("Enter your reply...");

    builder.setPositiveButton("Send", (dialog, which) -> {
        sendReply(message.getFromCallsign(), subject, content);
    });
}

private void sendReply(String toCallsign, String subject, String content) {
    // Create reply message
    RelayMessage replyMessage = new RelayMessage();
    replyMessage.setId(fromCallsign + "-" + timestamp);
    replyMessage.setFromCallsign(fromCallsign);
    replyMessage.setToCallsign(toCallsign);

    if (subject != null && !subject.isEmpty()) {
        replyMessage.setSubject(subject);
    }

    replyMessage.setContent(content);
    replyMessage.setPriority("normal");

    // Save to contact's relay outbox
    File outboxDir = folderManager.getRelayOutboxDir(toCallsign);
    File messageFile = new File(outboxDir, messageId + ".md");
    Files.write(messageFile.toPath(),
                replyMessage.toMarkdown().getBytes(UTF_8));

    Toast.makeText(requireContext(), "Reply sent to outbox", Toast.LENGTH_SHORT).show();
    getActivity().getSupportFragmentManager().popBackStack();
}
```

### Use Cases

**Use Case 1: Reply to Emergency Notification**
- Original: "Emergency: Road Closure" → "Main road blocked"
- Reply: "Re: Emergency: Road Closure" → "Taking alternate route, ETA 30min"

**Use Case 2: Reply Without Subject**
- Original: (no subject) → "Can you meet at 3pm?"
- Reply: (optional subject) → "Yes, see you then!"

**Use Case 3: Reply Chain**
- Message 1: "Meeting reminder"
- Reply 1: "Re: Meeting reminder"
- Reply 2: "Re: Meeting reminder" (doesn't become "Re: Re: Meeting reminder")

## 2. Remove Duplicate Relay Menu

### Problem

The "Relay" menu item in the navigation drawer duplicated functionality now available through contact folders:
- Old: Menu → Relay → View messages
- New: Messages → Contact → Relay Messages tab

### Solution

**Commented out menu item** in `navigation_menu.xml`:
```xml
<!--
<item
    android:id="@+id/nav_relay"
    android:icon="@drawable/ic_tethering"
    android:title="Relay" />
-->
```

**Disabled handler** in `MainActivity.java`:
```java
if (item.getItemId() == R.id.nav_settings) {
    transaction.replace(R.id.fragment_container, SettingsFragment.getInstance());
// Relay menu removed - relay messages now accessed through contact folders
// } else if (item.getItemId() == R.id.nav_relay) {
//     transaction.replace(R.id.fragment_container, new RelayFragment());
} else if (item.getItemId() == R.id.nav_debug) {
    transaction.replace(R.id.fragment_container, new DebugFragment());
```

### Benefits

1. **No Duplicate Navigation**: Single clear path to relay messages
2. **Contact-Centric**: Relay messages organized by contact
3. **Less Menu Clutter**: Cleaner navigation drawer
4. **Maintains Relay Settings**: Global relay settings still accessible (can be re-enabled if needed)

### Menu Structure

**Before**:
```
☰ Menu
├── Settings
├── Relay          ← Removed (duplicate)
├── Log
└── About
```

**After**:
```
☰ Menu
├── Settings
├── Log
└── About

Messages → [Contact] → Relay Messages tab
```

## 3. Relay Messages Visible in Status

### Problem

Relay status (shown via old Relay menu or status badge) only counted messages in the global relay folder (`/files/relay/`), missing all contact-specific messages (`/files/contacts/{CALLSIGN}/relay/`).

### Solution

**Updated RelayFragment.java** to count messages from both locations:

```java
private void updateStatus() {
    // Get message counts from both global and contact-specific folders
    int inboxCount = storage.getMessageCount("inbox") +
                     getContactRelayMessageCount("inbox");
    int outboxCount = storage.getMessageCount("outbox") +
                      getContactRelayMessageCount("outbox");
    int sentCount = storage.getMessageCount("sent") +
                    getContactRelayMessageCount("sent");

    textInboxCount.setText(String.valueOf(inboxCount));
    textOutboxCount.setText(String.valueOf(outboxCount));
    textSentCount.setText(String.valueOf(sentCount));

    // Get storage used from both locations
    long storageBytes = storage.getTotalStorageUsed() +
                        getContactRelayStorageUsed();
    textStorageUsed.setText(formatStorageSize(storageBytes));
}
```

**Helper Methods**:

```java
/**
 * Count relay messages in all contact folders for a specific folder type.
 */
private int getContactRelayMessageCount(String folderType) {
    int count = 0;
    File contactsDir = folderManager.getContactsDir();
    if (!contactsDir.exists() || !contactsDir.isDirectory()) {
        return 0;
    }

    File[] contactFolders = contactsDir.listFiles(File::isDirectory);
    if (contactFolders == null) return 0;

    for (File contactFolder : contactFolders) {
        String callsign = contactFolder.getName();
        File relayFolder;

        if ("inbox".equalsIgnoreCase(folderType)) {
            relayFolder = folderManager.getRelayInboxDir(callsign);
        } else if ("outbox".equalsIgnoreCase(folderType)) {
            relayFolder = folderManager.getRelayOutboxDir(callsign);
        } else if ("sent".equalsIgnoreCase(folderType)) {
            relayFolder = folderManager.getRelaySentDir(callsign);
        } else {
            continue;
        }

        if (relayFolder.exists() && relayFolder.isDirectory()) {
            File[] messages = relayFolder.listFiles((dir, name) -> name.endsWith(".md"));
            if (messages != null) {
                count += messages.length;
            }
        }
    }
    return count;
}

/**
 * Calculate total storage used by all contact relay messages.
 */
private long getContactRelayStorageUsed() {
    long totalBytes = 0;
    File contactsDir = folderManager.getContactsDir();

    for (File contactFolder : contactsDir.listFiles(File::isDirectory)) {
        String callsign = contactFolder.getName();
        File relayDir = folderManager.getRelayDir(callsign);

        if (relayDir.exists() && relayDir.isDirectory()) {
            totalBytes += calculateDirectorySize(relayDir);
        }
    }
    return totalBytes;
}
```

### Status Display

**Relay Status Screen** (if accessed directly):
```
┌─────────────────────────┐
│ Relay Status            │
├─────────────────────────┤
│ Inbox:    5 messages    │  ← Global + Contact folders
│ Outbox:   3 messages    │  ← Global + Contact folders
│ Sent:     12 messages   │  ← Global + Contact folders
│ Storage:  2.4 MB        │  ← Global + Contact folders
└─────────────────────────┘
```

**Badge Updates** (if enabled):
- Main activity badge shows total outbox count
- Includes both global and contact-specific messages

### Implementation Details

**Added to RelayFragment.java**:
- Import `ContactFolderManager`
- Field: `private ContactFolderManager folderManager;`
- Initialize in `onCreateView()`: `folderManager = new ContactFolderManager(requireContext());`
- New method: `getContactRelayMessageCount(String folderType)`
- New method: `getContactRelayStorageUsed()`
- New method: `calculateDirectorySize(File directory)`

**Added to ContactFolderManager.java**:
```java
/**
 * Get the base contacts directory containing all contact folders.
 */
public File getContactsDir() {
    return baseDir;
}
```

### Benefits

1. **Accurate Counts**: Shows all relay messages, not just global ones
2. **Better Visibility**: Users see full relay message activity
3. **Storage Tracking**: Correctly calculates storage used by all relay messages
4. **Unified View**: Single status for both global and contact-specific messages

## Testing

### Test Case 1: Reply to Message

1. Navigate to contact with relay messages
2. Switch to "Relay Messages" tab
3. Click on a message with subject
4. Click "Reply"
5. Verify subject pre-filled with "Re: [original]"
6. Enter reply message
7. Click "Send"

**Expected**:
- Reply appears in outbox
- Returns to message list
- Toast shows "Reply sent to outbox"

### Test Case 2: Menu Navigation

1. Open navigation drawer (☰ menu)
2. Verify "Relay" menu item not visible
3. Navigate to Messages
4. Select contact
5. Verify "Relay Messages" tab present

**Expected**:
- No duplicate relay access
- Clear path through contact folders

### Test Case 3: Status Visibility

1. Create relay messages in contact folders
2. Access relay status (if available)
3. Verify counts include contact folder messages

**Expected**:
- Inbox/Outbox/Sent counts include contact messages
- Storage calculation includes contact relay folders

## Files Modified

### 1. Reply Functionality
- **RelayMessageThreadFragment.java**
  - Added `replyToMessage()` implementation
  - Added `sendReply()` method
  - Added EditText import

### 2. Menu Cleanup
- **navigation_menu.xml**
  - Commented out nav_relay item
- **MainActivity.java**
  - Commented out nav_relay handler

### 3. Status Visibility
- **RelayFragment.java**
  - Added ContactFolderManager import and field
  - Updated `updateStatus()` to include contact folders
  - Added `getContactRelayMessageCount()`
  - Added `getContactRelayStorageUsed()`
  - Added `calculateDirectorySize()`
- **ContactFolderManager.java**
  - Added `getContactsDir()` method

## Build Status

✅ **BUILD SUCCESSFUL**
- No compilation errors
- All imports resolved
- All methods implemented

## Conclusion

These three improvements enhance the relay messaging system by:
1. **Enabling conversations**: Users can reply to messages, creating email-like threads
2. **Simplifying navigation**: Single clear path to relay messages through contacts
3. **Improving visibility**: All relay messages counted and displayed in status

The system now provides a complete, intuitive relay messaging experience integrated seamlessly with the contact folder architecture.

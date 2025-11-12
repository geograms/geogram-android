# Fix: Relay Message Visibility and Tab Navigation Issues

## Problem Statement

Two issues were reported after implementing the relay message composition feature:

1. **Relay messages not visible**: After composing a relay message, it didn't appear in the message list
2. **Tab navigation broken**: Unable to switch between Chat and Relay Messages tabs when viewing a contact

## Root Causes

### Issue 1: Message Loading Mismatch

**Problem**: Messages were being saved to contact-specific folders but loaded from a different location.

- **Save location**: `/files/contacts/{CALLSIGN}/relay/outbox/{message-id}.md`
- **Load location**: RelayStorage was looking in `/files/relay/outbox/` (global relay folder)

The `addMessagesFromFolder()` method in RelayMessagesListFragment was using `relayStorage.getMessage()` which expected files in the global relay folder structure, but compose functionality was writing to contact-specific folders.

### Issue 2: Duplicate Headers and Navigation Interference

**Problem**: ConversationChatFragment had its own header with back button, which interfered when embedded in ContactDetailFragment's ViewPager2.

- ContactDetailFragment has a header with back button (lines 10-56 in fragment_contact_detail.xml)
- ConversationChatFragment also has a header with back button (lines 9-44 in fragment_conversation_chat.xml)
- The back button in ConversationChatFragment calls `popBackStack()`, interfering with tab navigation
- ViewPager2 user input may not have been explicitly enabled

## Solutions Implemented

### Fix 1: Direct Markdown File Reading

**File**: `RelayMessagesListFragment.java` (lines 165-197)

Changed from using RelayStorage API to reading markdown files directly from contact folders:

```java
private void addMessagesFromFolder(List<RelayMessageItem> messageItems, File folder, String folderName) {
    if (!folder.exists() || !folder.isDirectory()) {
        return;
    }

    File[] files = folder.listFiles((dir, name) -> name.endsWith(".md"));
    if (files == null) {
        return;
    }

    for (File file : files) {
        try {
            // Read markdown file directly from contact folder
            String markdown = new String(
                java.nio.file.Files.readAllBytes(file.toPath()),
                java.nio.charset.StandardCharsets.UTF_8
            );

            // Parse markdown to RelayMessage
            RelayMessage message = RelayMessage.parseMarkdown(markdown);

            if (message != null) {
                // Only include messages to/from this contact
                if (message.getFromCallsign().equalsIgnoreCase(callsign) ||
                    message.getToCallsign().equalsIgnoreCase(callsign)) {
                    messageItems.add(new RelayMessageItem(message, folderName));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading message from " + file.getName() + ": " + e.getMessage());
        }
    }
}
```

**Benefits**:
- Reads from the same location where messages are saved
- No dependency on RelayStorage path configuration
- Works with contact-specific folder structure

### Fix 2: Embedded Mode for ConversationChatFragment

**Files Modified**:
- `ConversationChatFragment.java`
- `ContactDetailFragment.java`

#### Changes to ConversationChatFragment

1. **Added embedded mode parameter** (lines 43-44):
   ```java
   private static final String ARG_EMBEDDED = "embedded";
   private boolean embedded = false;
   ```

2. **Updated newInstance() methods** (lines 64-75):
   ```java
   public static ConversationChatFragment newInstance(String peerId) {
       return newInstance(peerId, false);
   }

   public static ConversationChatFragment newInstance(String peerId, boolean embedded) {
       ConversationChatFragment fragment = new ConversationChatFragment();
       Bundle args = new Bundle();
       args.putString(ARG_PEER_ID, peerId);
       args.putBoolean(ARG_EMBEDDED, embedded);
       fragment.setArguments(args);
       return fragment;
   }
   ```

3. **Hide header when embedded** (lines 100-118):
   ```java
   // Hide header when embedded in ContactDetailFragment
   if (embedded) {
       View headerLayout = view.findViewById(R.id.conversation_header).getParent() instanceof View
           ? (View) view.findViewById(R.id.conversation_header).getParent()
           : null;
       if (headerLayout != null) {
           headerLayout.setVisibility(View.GONE);
       }
   } else {
       // Set conversation header
       conversationHeader.setText(peerId);

       // Setup back button
       btnBack.setOnClickListener(v -> {
           if (getActivity() != null) {
               getActivity().getSupportFragmentManager().popBackStack();
           }
       });
   }
   ```

#### Changes to ContactDetailFragment

1. **Enable ViewPager2 user input** (line 124):
   ```java
   viewPager.setUserInputEnabled(true);
   ```

2. **Use embedded mode for chat fragment** (line 220):
   ```java
   return ConversationChatFragment.newInstance(callsign, true);
   ```

## Testing Verification

### Test Case 1: Compose and View Relay Message

1. Navigate to contact detail (e.g., CR7BBQ)
2. Switch to "Relay Messages" tab
3. Click "+" FAB
4. Compose message with:
   - To: CR7BBQ (pre-filled)
   - Priority: Normal
   - Content: "Test message"
5. Click "Send"

**Expected Results**:
- Toast: "Message queued for relay"
- Message immediately appears in list with:
  - Outbox badge
  - Priority indicator
  - Timestamp
  - Content preview
  - Status icon (⏱ clock for queued)

### Test Case 2: Tab Navigation

1. Navigate to contact detail
2. Verify Chat tab loads without duplicate header
3. Swipe left to switch to Relay Messages tab
4. Swipe right to switch back to Chat tab
5. Tap tab headers to switch between tabs
6. Verify no back button in chat content area

**Expected Results**:
- Only one header visible (ContactDetailFragment's header)
- Smooth swipe navigation between tabs
- Tab indicator follows current tab
- No navigation interference

## Files Changed

1. **RelayMessagesListFragment.java**
   - Lines 165-197: Updated `addMessagesFromFolder()` to read markdown files directly

2. **ConversationChatFragment.java**
   - Line 44: Added `ARG_EMBEDDED` constant
   - Line 48: Added `embedded` field
   - Lines 64-75: Updated `newInstance()` methods
   - Lines 82-83: Read embedded parameter in `onCreate()`
   - Lines 100-118: Hide header when embedded

3. **ContactDetailFragment.java**
   - Line 124: Enable ViewPager2 user input
   - Line 220: Use embedded mode for ConversationChatFragment

## Build Status

✅ Build successful
✅ No compilation errors
✅ No new warnings introduced

## Benefits

1. **Unified folder structure**: Both chat and relay messages use contact-specific folders consistently
2. **Clean UI**: No duplicate headers when using tabbed interface
3. **Proper navigation**: ViewPager2 swipe gestures work correctly
4. **Reusable components**: ConversationChatFragment can work standalone or embedded
5. **Offline-first**: Messages saved locally are immediately visible

## Related Documentation

- [Add Contact Feature](../features/ADD_CONTACT_FEATURE.md)
- [Contact Folder Architecture](../architecture/CONTACT_FOLDERS.md)
- [Relay Message Format](../../docs/relay-protocol.md)

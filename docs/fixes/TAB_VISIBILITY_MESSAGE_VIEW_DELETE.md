# Fix: Tab Visibility, Message Viewing, and Delete Functionality

## Problem Statement

Three usability issues were identified:

1. **Tabs not always visible**: Users couldn't tell there was a way to switch between Chat and Relay Messages
2. **Can't view message contents**: Clicking on a relay message didn't show the full message
3. **No delete option**: No way to delete relay messages

## Root Causes

### Issue 1: Tab Visibility Logic

**Problem**: ContactDetailFragment had logic to hide tabs based on content type.

- When contact had only chat messages → tabs hidden, stuck in chat view
- When contact had only relay messages → tabs hidden, stuck in relay view
- Users had no visual indicator that both chat and relay messaging were available

**Location**: `ContactDetailFragment.java` lines 144-183

### Issue 2: Message Viewing Broken

**Problem**: RelayMessageThreadFragment was using RelayStorage API which expected global folder structure, but messages were stored in contact-specific folders.

- Message stored at: `/files/contacts/{CALLSIGN}/relay/outbox/{message-id}.md`
- Loading from: `/files/relay/outbox/{message-id}.md` (wrong location)
- Fragment wasn't receiving callsign parameter, couldn't construct correct path

**Location**: `RelayMessageThreadFragment.java` loadMessage() method

### Issue 3: Delete Using Wrong Path

**Problem**: Delete functionality also used RelayStorage API pointing to wrong location.

- Similar path mismatch as viewing issue
- Would attempt to delete from global folder, not contact folder

**Location**: `RelayMessageThreadFragment.java` deleteMessage() method

## Solutions Implemented

### Fix 1: Always Show Tabs

**File**: `ContactDetailFragment.java` (lines 144-176)

Changed logic to always show tabs, only adjusting which tab is default:

```java
/**
 * Check what type of content exists and adjust UI accordingly.
 * Always show tabs so users know they can switch between chat and relay messages.
 */
private void checkContentTypeAndAdjustUI() {
    new Thread(() -> {
        ContactFolderManager.ContactContentType contentType = folderManager.getContentType(callsign);

        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                // Always show tabs so users can see both options
                tabLayout.setVisibility(View.VISIBLE);

                // Set default tab based on content
                switch (contentType) {
                    case RELAY_ONLY:
                        // Start on relay messages tab if only relay exists
                        viewPager.setCurrentItem(1, false);
                        Log.d(TAG, "Contact has only relay messages - showing relay tab");
                        break;

                    case CHAT_ONLY:
                    case BOTH:
                    case NONE:
                    default:
                        // Default to chat tab
                        Log.d(TAG, "Contact content type: " + contentType);
                        break;
                }
            });
        }
    }).start();
}
```

**Benefits**:
- Users always see both "Chat" and "Relay Messages" tabs
- Can swipe or tap to switch between them
- Default tab intelligently selected based on content

### Fix 2: Message Viewing from Contact Folders

**Files Modified**:
- `RelayMessageThreadFragment.java`
- `RelayMessagesListFragment.java`

#### Changes to RelayMessageThreadFragment

1. **Added callsign parameter** (lines 41-48, 63-70, 77-83):
   ```java
   private static final String ARG_CALLSIGN = "callsign";
   private String callsign;

   public static RelayMessageThreadFragment newInstance(String messageId, String folder, String callsign) {
       RelayMessageThreadFragment fragment = new RelayMessageThreadFragment();
       Bundle args = new Bundle();
       args.putString(ARG_MESSAGE_ID, messageId);
       args.putString(ARG_FOLDER, folder);
       args.putString(ARG_CALLSIGN, callsign);
       fragment.setArguments(args);
       return fragment;
   }
   ```

2. **Added ContactFolderManager** (lines 24, 62, 86):
   ```java
   import offgrid.geogram.contacts.ContactFolderManager;

   private ContactFolderManager folderManager;

   folderManager = new ContactFolderManager(requireContext());
   ```

3. **Updated loadMessage() to read from contact folders** (lines 123-187):
   ```java
   private void loadMessage() {
       new Thread(() -> {
           try {
               // Construct path to message file in contact folder
               java.io.File messageDir;
               if ("inbox".equalsIgnoreCase(folder)) {
                   messageDir = folderManager.getRelayInboxDir(callsign);
               } else if ("outbox".equalsIgnoreCase(folder)) {
                   messageDir = folderManager.getRelayOutboxDir(callsign);
               } else if ("sent".equalsIgnoreCase(folder)) {
                   messageDir = folderManager.getRelaySentDir(callsign);
               } else {
                   Log.e(TAG, "Unknown folder: " + folder);
                   return;
               }

               java.io.File messageFile = new java.io.File(messageDir, messageId + ".md");

               if (!messageFile.exists()) {
                   // Show error and go back
                   return;
               }

               // Read and parse markdown file
               String markdown = new String(
                   java.nio.file.Files.readAllBytes(messageFile.toPath()),
                   java.nio.charset.StandardCharsets.UTF_8
               );

               message = RelayMessage.parseMarkdown(markdown);

               if (message == null) {
                   // Show error and go back
                   return;
               }

               // Update UI on main thread
               if (getActivity() != null) {
                   getActivity().runOnUiThread(this::displayMessage);
               }

           } catch (Exception e) {
               Log.e(TAG, "Error loading message: " + e.getMessage());
               // Show error toast
           }
       }).start();
   }
   ```

#### Changes to RelayMessagesListFragment

**Updated fragment navigation call** (lines 199-214):
```java
private void onMessageClick(RelayMessageItem item) {
    // Navigate to message thread view
    if (getActivity() != null) {
        RelayMessageThreadFragment fragment = RelayMessageThreadFragment.newInstance(
            item.message.getId(),
            item.folder.toLowerCase(),
            callsign  // <-- Added callsign parameter
        );

        getActivity().getSupportFragmentManager()
            .beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit();
    }
}
```

### Fix 3: Delete from Contact Folders

**File**: `RelayMessageThreadFragment.java` (lines 264-320)

Updated deleteMessage() to delete directly from contact folder:

```java
private void deleteMessage() {
    new AlertDialog.Builder(requireContext())
        .setTitle("Delete Message")
        .setMessage("Are you sure you want to delete this message? This cannot be undone.")
        .setPositiveButton("Delete", (dialog, which) -> {
            new Thread(() -> {
                try {
                    // Construct path to message file in contact folder
                    java.io.File messageDir;
                    if ("inbox".equalsIgnoreCase(folder)) {
                        messageDir = folderManager.getRelayInboxDir(callsign);
                    } else if ("outbox".equalsIgnoreCase(folder)) {
                        messageDir = folderManager.getRelayOutboxDir(callsign);
                    } else if ("sent".equalsIgnoreCase(folder)) {
                        messageDir = folderManager.getRelaySentDir(callsign);
                    } else {
                        Log.e(TAG, "Unknown folder: " + folder);
                        return;
                    }

                    java.io.File messageFile = new java.io.File(messageDir, messageId + ".md");
                    boolean deleted = messageFile.delete();

                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (deleted) {
                                Toast.makeText(requireContext(),
                                    "Message deleted",
                                    Toast.LENGTH_SHORT).show();

                                // Return to previous screen
                                if (getActivity() != null) {
                                    getActivity().getSupportFragmentManager().popBackStack();
                                }
                            } else {
                                Toast.makeText(requireContext(),
                                    "Failed to delete message",
                                    Toast.LENGTH_SHORT).show();
                            }
                        });
                    }

                } catch (Exception e) {
                    Log.e(TAG, "Error deleting message: " + e.getMessage());
                    // Show error toast
                }
            }).start();
        })
        .setNegativeButton("Cancel", null)
        .show();
}
```

**Features**:
- Confirmation dialog before deleting
- Deletes file from correct contact folder
- Shows success/failure toast
- Returns to message list after successful deletion

## Testing Verification

### Test Case 1: Tab Visibility

1. Add new contact (e.g., TEST01)
2. Navigate to contact detail

**Expected Results**:
- ✅ Both "Chat" and "Relay Messages" tabs visible
- ✅ Can tap either tab to switch
- ✅ Can swipe left/right to switch
- ✅ Tab indicator shows current tab

### Test Case 2: View Message Contents

1. Navigate to contact with relay messages
2. Switch to "Relay Messages" tab
3. Click on any message in the list

**Expected Results**:
- ✅ Full message view opens
- ✅ Shows From/To/Timestamp
- ✅ Shows Priority with colored indicator
- ✅ Shows full message content
- ✅ Shows attachments (if any)
- ✅ Reply and Delete buttons visible

### Test Case 3: Delete Message

1. Open any relay message (per Test Case 2)
2. Click "Delete" button
3. Confirm deletion in dialog

**Expected Results**:
- ✅ Confirmation dialog appears
- ✅ "Cancel" dismisses dialog without deleting
- ✅ "Delete" removes message
- ✅ Returns to message list
- ✅ Message no longer appears in list
- ✅ File deleted from disk

### Test Case 4: Tab Persistence

1. Navigate to contact
2. Switch to "Relay Messages" tab
3. Compose a message
4. Return to contact detail

**Expected Results**:
- ✅ Tabs still visible (not hidden after compose)
- ✅ Can still switch between tabs
- ✅ New message appears in Outbox

## Files Changed

1. **ContactDetailFragment.java**
   - Lines 144-176: Updated `checkContentTypeAndAdjustUI()` to always show tabs

2. **RelayMessageThreadFragment.java**
   - Line 24: Added ContactFolderManager import
   - Lines 44, 48: Added callsign parameter and field
   - Lines 62, 86: Initialize ContactFolderManager
   - Lines 63-70: Updated newInstance() signature
   - Lines 77-83: Read callsign from arguments
   - Lines 123-187: Updated loadMessage() to read from contact folders
   - Lines 264-320: Updated deleteMessage() to delete from contact folders

3. **RelayMessagesListFragment.java**
   - Lines 199-214: Pass callsign to RelayMessageThreadFragment.newInstance()

## Build Status

✅ Build successful
✅ No compilation errors
✅ No new warnings introduced

## Benefits

1. **Better discoverability**: Users immediately see both chat and relay messaging options
2. **Consistent navigation**: Tabs always work the same way regardless of content
3. **Full message viewing**: Users can read complete relay messages with all metadata
4. **Message management**: Users can delete unwanted messages
5. **Data consistency**: All operations work with correct contact-specific folder structure

## Related Documentation

- [Relay Message Visibility and Navigation](RELAY_MESSAGE_VISIBILITY_AND_NAVIGATION.md)
- [Add Contact Feature](../features/ADD_CONTACT_FEATURE.md)
- [Contact Folder Architecture](../architecture/CONTACT_FOLDERS.md)

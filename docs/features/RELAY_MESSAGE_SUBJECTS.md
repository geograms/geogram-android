# Relay Message Subjects (Optional Email-Style Titles)

## Overview

Relay messages now support optional subjects/titles, similar to email. This helps users quickly identify message topics without reading the full content.

## Key Features

- **Optional**: Subjects are not required - messages work fine without them
- **Email-like**: Follows familiar email subject pattern
- **Prominent Display**: Subjects shown in bold in message list and detail views
- **Stored in Metadata**: Part of the relay message markdown format

## User Interface

### Compose Dialog

When composing a new relay message, the dialog now includes:

```
To: [CALLSIGN]
Subject (optional): [text input]
Priority: [spinner]
Message: [text area]
```

**Subject Field**:
- Label: "Subject (optional):"
- Hint: "e.g., Meeting reminder"
- Single line input
- Can be left empty
- Accepts any text with sentence capitalization

### Message List View

Messages display subjects prominently:

**With Subject**:
```
From: CR7BBQ                    Jan 10, 14:30
Meeting reminder
Let's meet at the usual place...
[Priority] [Folder] [Status]
```

**Without Subject**:
```
From: CR7BBQ                    Jan 10, 14:30
Let's meet at the usual place tomorrow at 3pm
[Priority] [Folder] [Status]
```

**Display Logic**:
- If subject present: Show subject in bold, followed by content preview (up to 50 chars)
- If no subject: Show content as before
- Content preview truncated with "..." if longer than 50 chars

### Message Detail View

Full message view shows subject separately:

```
From: CR7BBQ
To: X2DEVS
Date: Jan 10, 2025 14:30
Priority: Normal

Subject: Meeting reminder    ← Shown in bold if present

─────────────────────────

Let's meet at the usual place tomorrow at 3pm.
Don't forget to bring the documents we discussed.
```

**Display**:
- Subject shown between priority and divider
- Bold, white text (16sp)
- Only visible if subject exists
- Hidden (View.GONE) if no subject

## Message Format

### Markdown Structure

Subjects are stored in the relay message markdown format:

```markdown
> 2025-01-10 14:30_45 -- CR7BBQ
Let's meet at the usual place tomorrow at 3pm.

--> to: X2DEVS
--> id: CR7BBQ-1704902445000
--> subject: Meeting reminder
--> type: private
--> priority: normal
--> ttl: 604800
```

**Key Points**:
- Subject appears after `id` and before `type`
- Only included if subject is not empty
- Stored as plain text in the `--> subject: ` metadata field

### Parsing

The `parseMetadataField()` method handles subject extraction:

```java
case "subject":
    this.subject = value;
    break;
```

### Serialization

The `toMarkdown()` method includes subject when present:

```java
if (subject != null && !subject.trim().isEmpty()) {
    sb.append("--> subject: ").append(subject).append("\n");
}
```

## Implementation Details

### RelayMessage Class

**New Field**:
```java
private String subject;  // Optional subject/title (like email)
```

**Methods**:
```java
public String getSubject()
public void setSubject(String subject)
```

### Compose Dialog (RelayMessagesListFragment)

**Subject Input**:
```java
EditText subjectInput = new EditText(requireContext());
subjectInput.setHint("e.g., Meeting reminder");
subjectInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
subjectInput.setSingleLine(true);
```

**Message Creation**:
```java
private void composeRelayMessage(String toCallsign, String subject, String content, String priority) {
    // ...
    if (subject != null && !subject.isEmpty()) {
        message.setSubject(subject);
    }
    // ...
}
```

### Message List Adapter

**Display Logic**:
```java
String subject = message.getSubject();
String content = message.getContent();

if (subject != null && !subject.isEmpty()) {
    displayText = subject;
    if (content != null && !content.isEmpty()) {
        String contentPreview = content.length() > 50
            ? content.substring(0, 50) + "..."
            : content;
        displayText = subject + "\n" + contentPreview;
    }
} else if (content != null && !content.isEmpty()) {
    displayText = content;
} else {
    displayText = "(No content)";
}

textContent.setText(displayText);
```

### Message Detail Fragment

**Subject TextView**:
```xml
<TextView
    android:id="@+id/text_subject"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:text="Subject: Meeting reminder"
    android:textColor="@color/white"
    android:textSize="16sp"
    android:textStyle="bold"
    android:paddingTop="8dp"
    android:paddingBottom="8dp"
    android:visibility="gone" />
```

**Display Logic**:
```java
String subject = message.getSubject();
if (subject != null && !subject.isEmpty()) {
    textSubject.setText("Subject: " + subject);
    textSubject.setVisibility(View.VISIBLE);
} else {
    textSubject.setVisibility(View.GONE);
}
```

## Use Cases

### Use Case 1: Emergency Notifications

**Scenario**: Emergency responder needs to send urgent updates

**Subject**: "Emergency: Road Closure"
**Content**: Detailed information about the road closure, alternate routes, and expected duration.

**Benefit**: Recipients immediately see the emergency nature without reading full message.

### Use Case 2: Event Coordination

**Scenario**: Organizing a community meeting

**Subject**: "Community Meeting - Jan 15"
**Content**: Agenda, time, location, and preparation notes.

**Benefit**: Easy to identify among other messages, clear topic at a glance.

### Use Case 3: Supply Requests

**Scenario**: Requesting specific supplies

**Subject**: "Need: First Aid Supplies"
**Content**: Detailed list of required items, quantities, and urgency.

**Benefit**: Quick scanning of message list shows what's needed without opening each message.

### Use Case 4: Status Updates

**Scenario**: Providing status updates

**Subject**: "Status: All Clear"
**Content**: Detailed situation report and next steps.

**Benefit**: Immediate understanding of current situation.

### Use Case 5: Simple Messages

**Scenario**: Quick informal message

**Subject**: (none)
**Content**: "See you at 3pm tomorrow"

**Benefit**: No need for subject on simple messages - keeps it fast and informal.

## Benefits

1. **Quick Scanning**: See message topics without opening each one
2. **Better Organization**: Group related messages by subject
3. **Familiar Pattern**: Email-like interface that users already understand
4. **Optional**: No forced workflow change - use subjects when helpful
5. **Backward Compatible**: Messages without subjects work exactly as before
6. **Search/Filter Ready**: Foundation for future search by subject feature

## Design Decisions

### Why Optional?

Many relay messages are simple, informal communications that don't need subjects. Making subjects optional ensures:
- Fast composition for quick messages
- No barriers to sending urgent information
- Natural use pattern - add subjects when they add value

### Why Show Subject in List?

Primary display of subjects in message list (rather than content) because:
- Subject is the primary identifier (like email inbox)
- Content serves as additional context/preview
- Users scan subjects first to find relevant messages
- Matches mental model from email

### Why Store in Metadata?

Subject stored as metadata field (not in content) because:
- Clean separation of concerns
- Easy to parse and extract
- Consistent with other message properties (priority, type)
- Enables future features like subject-only search

## Future Enhancements

### Potential Features

1. **Subject Templates**: Pre-defined subjects for common message types
2. **Subject Search**: Filter messages by subject keywords
3. **Subject-Based Threading**: Group messages by matching subjects
4. **Subject Length Limit**: Enforce reasonable subject length (e.g., 50-100 chars)
5. **Auto-Subject Generation**: Suggest subjects based on content analysis
6. **Reply Subject Handling**: Automatic "Re: " prefix for replies

## Testing

### Test Cases

**Test 1: Compose with Subject**
1. Open relay messages for contact
2. Click "+" to compose
3. Enter subject: "Test Subject"
4. Enter message content
5. Send

**Expected**: Message appears in list with subject displayed prominently

**Test 2: Compose without Subject**
1. Open relay messages for contact
2. Click "+" to compose
3. Leave subject empty
4. Enter message content
5. Send

**Expected**: Message appears in list with only content displayed

**Test 3: View Message with Subject**
1. Click on message with subject

**Expected**: Detail view shows subject in bold, separate from content

**Test 4: View Message without Subject**
1. Click on message without subject

**Expected**: Detail view shows only content, no subject line visible

**Test 5: Markdown Persistence**
1. Compose message with subject
2. View the .md file directly

**Expected**: File contains `--> subject: ` metadata line

**Test 6: Mixed Message List**
1. Create messages with and without subjects
2. View message list

**Expected**: Messages with subjects show subject + preview, messages without show only content

## Files Modified

1. **RelayMessage.java**
   - Added `subject` field
   - Added `getSubject()` and `setSubject()` methods
   - Updated `toMarkdown()` to include subject
   - Updated `parseMetadataField()` to parse subject

2. **RelayMessagesListFragment.java**
   - Added subject input field to compose dialog
   - Updated `composeRelayMessage()` signature to accept subject
   - Updated message list adapter to display subjects
   - Enhanced content preview logic

3. **RelayMessageThreadFragment.java**
   - Added `textSubject` TextView field
   - Initialized subject view in `onCreateView()`
   - Updated `displayMessage()` to show subject

4. **fragment_relay_message_thread.xml**
   - Added subject TextView with proper styling
   - Hidden by default (visibility="gone")

## Conclusion

The optional subject field enhances relay messages without adding complexity. Users can compose messages exactly as before, or add subjects when organizing information. The familiar email-like pattern makes the feature immediately intuitive, while the optional nature keeps casual messaging fast and informal.

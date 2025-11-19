# AI Chat UI Implementation - Geogram Android

## Summary

Added a complete AI chatbot user interface to Geogram Android app. The UI is fully implemented and ready for backend integration with the Cactus SDK.

---

## Changes Made

### 1. New Icon

**File**: `app/src/main/res/drawable/ic_robot.xml`

Replaced scary robot icon with a friendly chat bubble design featuring:
- Chat bubble with three dots (typing indicator style)
- Two sparkles to indicate AI/magic
- Clean, modern, friendly appearance

### 2. Main Action Bar Button

**File**: `app/src/main/res/layout/activity_main.xml`

Added AI Chat button to the top action bar:
- **Location**: After Collections button, before device counter
- **Icon**: Friendly chat bubble with sparkles
- **Color**: White tint matching app theme
- **Position**: Line 128-137

### 3. AI Chat Fragment

**File**: `app/src/main/java/offgrid/geogram/fragments/AiChatFragment.java`

Complete chat interface with:
- **RecyclerView** for chat messages
- **Empty state** with friendly icon and "Start a conversation with AI" text
- **File attachment** support (UI ready, backend pending)
- **Settings button** in top-right corner
- **Input area** with text field and send button
- **File preview** area (shows selected file before sending)

Key features:
- File picker integration
- Activity result handling for file selection
- View binding ready for Kotlin migration
- Proper lifecycle management

### 4. Chat Layout

**File**: `app/src/main/res/layout/fragment_ai_chat.xml`

Professional chat UI with:
- **Top bar**: Title "AI Assistant" + Settings cog button
- **Chat area**: RecyclerView for messages (empty state included)
- **File preview**: Shows selected file with remove button
- **Input row**:
  - Attach file button (paperclip icon)
  - Multi-line text input
  - Send button

Design details:
- Dark theme (#2B2B2B background)
- Rounded corners on inputs (12dp radius)
- Elevated input container (8dp shadow)
- Responsive layout with proper padding

### 5. Message Item Layout

**File**: `app/src/main/res/layout/item_ai_chat_message.xml`

Message bubble design:
- **Avatar**: Robot icon for AI messages (optional)
- **Content**: Message text with proper spacing
- **File attachment**: Preview for attached files
- **Timestamp**: 11sp gray text
- **Bubble**: Rounded background (#454545)

### 6. Supporting Assets

**Created**:
- `ic_attach.xml` - Paperclip icon for file attachment
- `message_bubble_background.xml` - Rounded background for messages

**Already existed**:
- `ic_send.xml` - Send button icon
- `ic_close.xml` - Remove file button
- `ic_settings.xml` - Settings cog
- `ic_file.xml` - File preview icon

### 7. Navigation Integration

**File**: `app/src/main/java/offgrid/geogram/MainActivity.java`

Added navigation:
- **Import**: Added AiChatFragment import (line 51)
- **Click handler**: Lines 441-448
- **Fragment transaction**: Opens AiChatFragment on button click

---

## UI Components Details

### Top Bar

```
┌─────────────────────────────────────────┐
│ ← AI Assistant                    ⚙️    │
└─────────────────────────────────────────┘
```

- Back button (from fragment stack)
- Title "AI Assistant"
- Settings cog (right-aligned)

### Empty State

```
┌─────────────────────────────────────────┐
│                                         │
│              💬✨                       │
│                                         │
│      Start a conversation with AI       │
│                                         │
└─────────────────────────────────────────┘
```

- Chat bubble icon with sparkles
- Centered text
- Low opacity (30-50%) for subtle appearance

### File Preview (when file selected)

```
┌─────────────────────────────────────────┐
│ 📄 document.pdf                      ✕  │
└─────────────────────────────────────────┘
```

- File icon
- Filename (truncated with ellipsis)
- Remove button

### Input Area

```
┌─────────────────────────────────────────┐
│ 📎 [ Type your message...        ] ➤   │
└─────────────────────────────────────────┘
```

- Attach button (paperclip)
- Text input (expandable up to 5 lines)
- Send button (arrow)

### Message Bubbles

**User Message** (right-aligned):
```
                        ┌────────────────┐
                        │ Hello!         │
                        │ 12:34 PM       │
                        └────────────────┘
```

**AI Message** (left-aligned):
```
┌────────────────┐
│ 🤖 Hi there!   │
│ 12:34 PM       │
└────────────────┘
```

---

## Current State

### ✅ Implemented (UI Only)

1. Complete chat interface layout
2. File attachment UI (picker works)
3. Settings button placement
4. Empty state with icon
5. Message list (RecyclerView ready)
6. Input area with send button
7. Navigation from main bar

### ⏳ Pending (Backend)

1. **Message sending** - Shows toast "Message sending (not implemented yet)"
2. **AI response generation** - Needs Cactus SDK integration
3. **Message adapter** - RecyclerView adapter not set yet
4. **Settings dialog** - Shows toast "AI Settings (not implemented yet)"
5. **Message persistence** - Save chat history
6. **Streaming responses** - Token-by-token display

---

## File Structure

```
app/src/main/
├── java/offgrid/geogram/
│   ├── MainActivity.java                    # Modified: Added AI chat nav
│   └── fragments/
│       └── AiChatFragment.java              # New: Chat UI
└── res/
    ├── drawable/
    │   ├── ic_robot.xml                     # Modified: New friendly icon
    │   ├── ic_attach.xml                    # New: Paperclip icon
    │   └── message_bubble_background.xml    # New: Rounded background
    └── layout/
        ├── activity_main.xml                # Modified: Added AI button
        ├── fragment_ai_chat.xml             # New: Chat layout
        └── item_ai_chat_message.xml         # New: Message bubble
```

---

## Integration Points

### For Backend Implementation

The UI is ready for integration. You'll need to:

1. **Create RecyclerView Adapter**:
   ```java
   public class ChatMessageAdapter extends RecyclerView.Adapter<...> {
       // Display user and AI messages
       // Handle different message types
       // Show timestamps
   }
   ```

2. **Wire up sendMessage()**:
   ```java
   private void sendMessage(String message) {
       // Add message to list
       // Call AI API/SDK
       // Stream response tokens
       // Update UI
   }
   ```

3. **Implement Settings**:
   ```java
   private void showAiSettings() {
       // Model selection
       // Context size
       // Temperature
       // Max tokens
   }
   ```

4. **Add Message Persistence**:
   ```java
   // Save to SharedPreferences or Database
   // Load on fragment resume
   // Clear chat option
   ```

### Recommended Next Steps

**With Cactus SDK (see cactus-integration-analysis.md)**:

1. Add Kotlin support
2. Convert AiChatFragment to Kotlin
3. Create CactusManager wrapper
4. Implement ViewModel with coroutines
5. Wire up streaming responses
6. Add model download UI

**Without Cactus (API-based)**:

1. Create retrofit/OkHttp client
2. Implement streaming SSE parser
3. Add API key management
4. Handle network errors
5. Add offline mode notice

---

## Design Decisions

### Why This Layout?

1. **Top Bar with Settings**: Standard Android pattern, users expect settings in top-right
2. **Empty State**: Welcoming, explains what the feature does
3. **File Preview**: Shows user what will be sent, allows removal
4. **Bottom Input**: Thumb-friendly on phones, always accessible
5. **Rounded Bubbles**: Modern, friendly, clear visual separation

### Color Scheme

- **Background**: `#2B2B2B` (dark gray)
- **Input Container**: `#353535` (slightly lighter)
- **Message Bubbles**: `#454545` (medium gray)
- **Text**: `#FFFFFF` (white)
- **Placeholders**: `#888888` (medium gray)
- **Descriptions**: `#CCCCCC` (light gray)

Matches existing Geogram dark theme.

### Accessibility

- **Contrast**: All text meets WCAG AA standards
- **Touch Targets**: All buttons 40-48dp (recommended 48dp)
- **Content Descriptions**: All ImageButtons have descriptions
- **Keyboard**: Input field works with hardware keyboard

---

## Testing Checklist

- [x] Button appears in main bar
- [x] Button opens AI chat fragment
- [x] Empty state displays correctly
- [x] Input field accepts text
- [x] Send button is clickable
- [x] File picker opens
- [x] Selected file shows in preview
- [x] Remove file button works
- [x] Settings button is clickable
- [ ] Messages display in RecyclerView (pending adapter)
- [ ] Streaming responses work (pending backend)
- [ ] Settings dialog works (pending implementation)

---

## Next Steps

See `cactus-integration-analysis.md` for complete backend integration plan.

---

*Document created: 2025-11-17*
*Status: UI Complete, Backend Pending*
*Project: Geogram Android*

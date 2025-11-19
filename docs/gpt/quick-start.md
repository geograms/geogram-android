# Quick Start Guide - AI Integration

## TL;DR

**What we built**: Complete AI chat UI in Geogram Android

**What's next**: Integrate Cactus SDK for local AI inference

**Time needed**: 3-4 weeks

**Difficulty**: Medium (requires Kotlin basics)

---

## Current Status

### ✅ Done (Today)

1. **UI Complete** - Professional chat interface
2. **Icon Changed** - Friendly chat bubble instead of scary robot
3. **Navigation Added** - Button in main bar opens AI chat
4. **Documentation Created** - 3 comprehensive guides + SDK source code
5. **File Structure Ready** - Organized for Kotlin integration

### ⏳ Not Done (Yet)

1. **AI Responses** - Shows "not implemented" toast
2. **Message Display** - RecyclerView adapter not set
3. **Settings** - Empty placeholder
4. **Kotlin Support** - Need to add Kotlin plugin
5. **Cactus SDK** - Need to add dependency

---

## Test the UI

1. **Build and install** the APK (already built)
2. **Click** the chat bubble icon (✨💬) in top bar
3. **See** the AI Assistant screen
4. **Try**:
   - Type a message and press send → Shows toast
   - Click attach button → Opens file picker
   - Click settings → Shows toast
   - Select a file → Shows in preview area

---

## Next Steps (Simple Version)

### Step 1: Add Kotlin (30 minutes)

**File**: `app/build.gradle` (NOT .kts)

```gradle
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android' version '1.9.20'  // ADD THIS
}

dependencies {
    // ... existing dependencies ...

    // ADD THESE:
    implementation 'org.jetbrains.kotlin:kotlin-stdlib:1.9.20'
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
    implementation 'com.cactuscompute:cactus:1.0.1-beta'
}

android {
    // ... existing config ...

    kotlinOptions {  // ADD THIS
        jvmTarget = '17'
    }
}
```

**Sync Gradle** and verify build succeeds.

### Step 2: Initialize Cactus (15 minutes)

**File**: `MainActivity.java`

Add import:
```java
import com.cactus.CactusContextInitializer;
```

In `onCreate()`, add after `super.onCreate()`:
```java
CactusContextInitializer.initialize(this);
```

### Step 3: Test Basic Functionality (30 minutes)

Create a test file to verify SDK works:

**File**: `app/src/main/java/offgrid/geogram/ai/CactusTest.kt` (NEW)

```kotlin
package offgrid.geogram.ai

import com.cactus.CactusLM
import com.cactus.CactusInitParams
import com.cactus.ChatMessage

suspend fun testCactus() {
    val lm = CactusLM()

    // Download model (only first time)
    println("Downloading model...")
    lm.downloadModel("qwen3-0.6")

    // Initialize
    println("Loading model...")
    lm.initializeModel(CactusInitParams(model = "qwen3-0.6"))

    // Generate response
    println("Generating response...")
    val result = lm.generateCompletion(
        messages = listOf(
            ChatMessage("Say hello!", "user")
        )
    )

    println("AI says: ${result.text}")

    lm.unload()
}
```

Call from MainActivity:
```java
import kotlinx.coroutines.GlobalScope;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.launch;
import offgrid.geogram.ai.CactusTestKt;

// In onCreate():
GlobalScope.launch(Dispatchers.IO, () -> {
    CactusTestKt.testCactus();
    return null;
});
```

**Expected**: Logcat shows AI response after ~30 seconds

### Step 4: Wire Up UI (2-3 days)

This is the big step. See `cactus-integration-analysis.md` for detailed guide.

**Summary**:
1. Convert AiChatFragment to Kotlin
2. Create CactusManager wrapper
3. Create AiChatViewModel
4. Create RecyclerView adapter
5. Wire everything together

---

## Minimal Working Example

**Want to see it work ASAP?** Here's the absolute minimum:

### 1. Add Dependencies (as above)

### 2. Simple Test Activity

Create `TestAiActivity.kt`:

```kotlin
package offgrid.geogram

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.cactus.*
import kotlinx.coroutines.*

class TestAiActivity : AppCompatActivity() {
    private lateinit var resultText: TextView
    private lateinit var askButton: Button
    private var lm: CactusLM? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Simple layout
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            padding = 50
        }

        resultText = TextView(this).apply {
            text = "Initializing..."
        }

        askButton = Button(this).apply {
            text = "Ask AI"
            isEnabled = false
            setOnClickListener { askAi() }
        }

        layout.addView(resultText)
        layout.addView(askButton)
        setContentView(layout)

        // Initialize AI
        GlobalScope.launch(Dispatchers.IO) {
            try {
                lm = CactusLM()
                lm?.downloadModel("qwen3-0.6")
                lm?.initializeModel(CactusInitParams(model = "qwen3-0.6"))

                withContext(Dispatchers.Main) {
                    resultText.text = "Ready!"
                    askButton.isEnabled = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    resultText.text = "Error: ${e.message}"
                }
            }
        }
    }

    private fun askAi() {
        GlobalScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                resultText.text = "Thinking..."
            }

            try {
                val result = lm?.generateCompletion(
                    messages = listOf(
                        ChatMessage("Tell me a joke", "user")
                    )
                )

                withContext(Dispatchers.Main) {
                    resultText.text = result?.text ?: "No response"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    resultText.text = "Error: ${e.message}"
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        lm?.unload()
    }
}
```

Add to AndroidManifest.xml:
```xml
<activity android:name=".TestAiActivity" />
```

Launch from anywhere:
```java
startActivity(new Intent(this, TestAiActivity.class));
```

**Result**: Simple screen with "Ask AI" button that generates responses

---

## Common Issues

### Issue 1: Build Fails

**Error**: `Could not find com.cactuscompute:cactus:1.0.1-beta`

**Fix**: Add Maven Central to repositories:

```gradle
// settings.gradle or build.gradle
repositories {
    mavenCentral()  // ADD THIS
    google()
    // ...
}
```

### Issue 2: Slow First Run

**Symptom**: App hangs for 30+ seconds

**Explanation**: First run downloads 400MB model

**Fix**: Show progress dialog or do background download

### Issue 3: Out of Memory

**Error**: `OutOfMemoryError` when loading model

**Fix**: Model needs 1GB+ RAM. Check device has 3GB+ total RAM.

### Issue 4: Kotlin Not Found

**Error**: `Cannot resolve symbol 'kotlin'`

**Fix**:
1. Sync Gradle
2. Invalidate caches: File → Invalidate Caches → Invalidate and Restart
3. Rebuild project

---

## Performance Expectations

### Model Download
- **Size**: ~400MB (qwen3-0.6)
- **Time**: 1-5 minutes on WiFi
- **Happens**: Only once (cached)

### Model Loading
- **Memory**: ~1GB RAM
- **Time**: 5-15 seconds
- **Happens**: Each app start

### Response Generation
- **Speed**: 2-4 tokens/second on mid-range phones
- **Example**: "Hello, how are you?" (~5 tokens) = ~2 seconds
- **Longer**: 100-token response = ~30 seconds

### Device Requirements
- **Minimum**: Android 7.0 (API 24), 3GB RAM, ARM64
- **Recommended**: Android 10+, 4GB+ RAM
- **Optimal**: Android 12+, 6GB+ RAM, recent SoC

---

## Development Tips

### 1. Use Logcat

Monitor AI responses:
```bash
adb logcat | grep -i cactus
```

### 2. Test on Real Device

Emulators may be slow or incompatible. Use physical phone with ARM64.

### 3. Start Small

Don't convert entire AiChatFragment at once. Start with:
1. Simple test activity (see above)
2. Then CactusManager wrapper
3. Then ViewModel
4. Finally full UI

### 4. Use Smaller Model

For testing, try `gemma3-270m`:
- Faster downloads (~150MB)
- Faster responses (~5-8 tok/sec)
- Less capable but good for testing

### 5. Check Examples

The cactus-kotlin/example/ folder has working code for:
- Basic completion
- Streaming responses
- Function calling
- File processing
- Full chat UI

Copy-paste and adapt as needed!

---

## Quick Reference

### Import Statements

```kotlin
import com.cactus.CactusLM
import com.cactus.CactusSTT
import com.cactus.CactusInitParams
import com.cactus.CactusCompletionParams
import com.cactus.ChatMessage
import com.cactus.InferenceMode
```

### Basic API Calls

```kotlin
// Initialize
val lm = CactusLM()
lm.downloadModel("qwen3-0.6")
lm.initializeModel(CactusInitParams(model = "qwen3-0.6"))

// Generate
val result = lm.generateCompletion(
    messages = listOf(ChatMessage("Hello", "user"))
)

// Stream
lm.generateCompletion(
    messages = ...,
    onToken = { token, _ -> print(token) }
)

// Cleanup
lm.unload()
```

### File Locations

```
Downloaded models: /data/data/offgrid.geogram/files/cactus/models/
Cached files: /data/data/offgrid.geogram/cache/cactus/
```

---

## Timeline

### Week 1: Setup ✅ CURRENT
- [x] Add Kotlin support
- [ ] Add Cactus dependency
- [ ] Test basic functionality
- [ ] Create CactusManager wrapper

### Week 2: Backend
- [ ] Create Kotlin AI package
- [ ] Implement ViewModel
- [ ] Test streaming responses
- [ ] Handle errors

### Week 3: Integration
- [ ] Convert Fragment to Kotlin
- [ ] Create message adapter
- [ ] Wire up UI to ViewModel
- [ ] Implement settings

### Week 4: Polish
- [ ] Performance tuning
- [ ] Error handling
- [ ] Device compatibility
- [ ] User testing

---

## Help

**Stuck?** Check these files:
1. `cactus-integration-analysis.md` - Detailed implementation guide
2. `ai-chat-ui-implementation.md` - UI documentation
3. `cactus-kotlin/example/` - Working example code

**Still stuck?** Create an issue with:
- Error message (full logcat)
- Device info (model, Android version, RAM)
- Steps to reproduce
- What you've tried

---

**Good luck!** 🚀

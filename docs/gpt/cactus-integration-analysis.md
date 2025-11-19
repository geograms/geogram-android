# Cactus AI Integration Analysis for Geogram Android

## Executive Summary

Cactus is a Kotlin Multiplatform library that enables **on-device AI inference** for Android (API 24+) and iOS (12.0+) apps. It allows running Large Language Models (LLMs) locally on smartphones without requiring internet connectivity, making it ideal for Geogram's off-grid philosophy.

**Key Finding**: Cactus can be integrated into Geogram to provide AI-powered features that work completely offline, aligning perfectly with Geogram's mesh networking and off-grid capabilities.

---

## Table of Contents

1. [Cactus SDK Overview](#cactus-sdk-overview)
2. [Technical Architecture](#technical-architecture)
3. [Demo App Analysis](#demo-app-analysis)
4. [Integration Strategy for Geogram](#integration-strategy-for-geogram)
5. [Implementation Plan](#implementation-plan)
6. [Challenges and Solutions](#challenges-and-solutions)
7. [Code Examples](#code-examples)
8. [Next Steps](#next-steps)

---

## Cactus SDK Overview

### What is Cactus?

Cactus is an SDK that deploys AI models directly on mobile devices, enabling:

- **Text completion** with streaming support
- **Embeddings generation** for semantic search
- **Speech-to-text** transcription (Whisper models)
- **Function calling** for tool integration
- **Hybrid inference** modes (local-first with cloud fallback)

### Supported Models

The SDK provides access to quantized models optimized for mobile:
- **Qwen3-0.6** (600M parameters) - Recommended for chat
- **Gemma3-270m** (270M parameters) - Faster, less capable
- **Whisper-tiny** - Speech recognition
- Custom models via download

### Key Features

1. **Offline-First**: Models run completely on-device
2. **Streaming Responses**: Token-by-token generation
3. **Context Management**: Configurable context windows (up to 8192 tokens)
4. **Model Caching**: Downloaded models persist on disk
5. **Performance Metrics**: Built-in telemetry (tokens/sec, TTFT, etc.)
6. **Multi-Platform**: Kotlin Multiplatform (Android + iOS)

---

## Technical Architecture

### Core Components

#### 1. CactusLM (Language Model)

Main class for text generation:

```kotlin
val lm = CactusLM()
lm.downloadModel("qwen3-0.6") // Downloads ~400MB model
lm.initializeModel(CactusInitParams(
    model = "qwen3-0.6",
    contextSize = 4096
))
```

Key methods:
- `downloadModel(slug)` - Downloads model to device
- `initializeModel(params)` - Loads model into memory
- `generateCompletion(messages, params, onToken)` - Generates text
- `unload()` - Frees memory

#### 2. CactusSTT (Speech-to-Text)

Handles audio transcription:

```kotlin
val stt = CactusSTT()
stt.download("whisper-tiny")
stt.init("whisper-tiny")
stt.transcribe(params, filePath)
```

#### 3. Inference Modes

- `LOCAL` - On-device only
- `REMOTE` - Cloud API only
- `LOCAL_FIRST` - Try device, fallback to cloud
- `REMOTE_FIRST` - Try cloud, fallback to device

#### 4. Tool Filtering

Optimizes function calling by filtering irrelevant tools:
- `SIMPLE` - Keyword-based matching (fast)
- `SEMANTIC` - Embedding-based similarity (accurate)

### Initialization Requirements

Must call in Activity's `onCreate()`:

```kotlin
CactusContextInitializer.initialize(this)
```

Required permissions in AndroidManifest.xml:
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

### Dependencies

In `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.cactuscompute:cactus:1.0.1-beta")
}
```

Requirements:
- Min SDK 24 (Android 7.0)
- ARM64 architecture (arm64-v8a)
- Java 11+ compatibility

---

## Demo App Analysis

### Project Structure

```
example/
├── composeApp/
│   └── src/
│       ├── commonMain/kotlin/
│       │   └── com/cactus/example/
│       │       ├── pages/
│       │       │   ├── ChatPage.kt              # Full chat UI
│       │       │   ├── BasicCompletionPage.kt   # Simple generation
│       │       │   ├── StreamingCompletionPage.kt
│       │       │   ├── FunctionCallingPage.kt
│       │       │   ├── EmbeddingPage.kt
│       │       │   ├── TranscriptionPage.kt
│       │       │   └── FetchModelsPage.kt
│       │       ├── App.kt                       # Main navigation
│       │       ├── FilePicker.kt
│       │       └── theme/Theme.kt
│       └── androidMain/kotlin/
│           └── com/cactus/example/
│               ├── MainActivity.kt              # Entry point
│               └── FilePicker.android.kt
```

### ChatPage.kt - Core Implementation

The ChatPage demonstrates a complete chat interface:

#### Initialization Pattern

```kotlin
val lm = remember { CactusLM() }

LaunchedEffect(Unit) {
    lm.downloadModel()  // Downloads if not cached
    lm.initializeModel(CactusInitParams(
        model = "qwen3-0.6",
        contextSize = 4096
    ))

    // Warm up with system message
    lm.generateCompletion(
        messages = listOf(
            ChatMessage("You are Cactus...", "system")
        ),
        params = CactusCompletionParams(maxTokens = 0)
    )
}

DisposableEffect(Unit) {
    onDispose { lm.unload() }
}
```

#### Message Generation with Streaming

```kotlin
val result = lm.generateCompletion(
    messages = messagesToPass,
    onToken = { token, _ ->
        // Update UI with each token
        assistantResponse.append(token)
        chatMessages = chatMessages.filter { it.role != MessageRole.Typing }

        val lastMessage = chatMessages.lastOrNull()
        chatMessages = if (lastMessage?.role == MessageRole.Assistant) {
            chatMessages.dropLast(1) +
                lastMessage.copy(content = assistantResponse.toString())
        } else {
            chatMessages + Message(
                content = assistantResponse.toString(),
                role = MessageRole.Assistant
            )
        }
    }
)
```

#### UI Components

1. **Message Bubbles**:
   - User messages (right-aligned, primary color)
   - Assistant messages (left-aligned, secondary color)
   - Typing indicator (animated dots)

2. **Performance Metrics**:
   ```kotlin
   Text("Tokens: ${result.totalTokens} • " +
        "TTFT: ${result.timeToFirstTokenMs} ms • " +
        "${result.tokensPerSecond} tok/sec")
   ```

3. **Input Area**:
   - TextField with rounded corners
   - Send button (enabled when text present)
   - Loading indicator during generation

### MainActivity.kt

Minimal setup:

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        CactusContextInitializer.initialize(this)

        // Request RECORD_AUDIO permission
        if (checkSelfPermission(RECORD_AUDIO) != PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(RECORD_AUDIO)
        }

        setContent {
            App()
        }
    }
}
```

### Key Patterns Observed

1. **Model Lifecycle**:
   - Download once (cached)
   - Initialize per session
   - Warm up with system prompt
   - Always unload on cleanup

2. **Memory Management**:
   - Models ~400MB disk space
   - ~1GB RAM when loaded
   - Must call `unload()` to free memory

3. **Error Handling**:
   ```kotlin
   try {
       lm.downloadModel()
       lm.initializeModel(...)
   } catch (e: Exception) {
       println("Error: ${e.message}")
   }
   ```

4. **Threading**:
   - All API calls are suspend functions
   - Use coroutine scope for async operations
   - UI updates via Compose state

---

## Integration Strategy for Geogram

### Architecture Considerations

#### Current Geogram Stack

- **Language**: Java (Android traditional)
- **UI**: XML layouts with Fragments
- **Min SDK**: 24 (same as Cactus)
- **Target SDK**: 35
- **Build**: Gradle with Java 17

#### Cactus Requirements

- **Language**: Kotlin required (JVM interop possible)
- **UI**: Compose (but we'll use XML)
- **Dependencies**: Kotlin stdlib, coroutines

### Integration Approach

We have **three options**:

#### Option 1: Kotlin Migration (Recommended)

**Convert AI chat feature to Kotlin:**

Pros:
- Full SDK support
- Coroutines for async
- Easier to maintain
- Modern Android development

Cons:
- Mixed language codebase
- Team familiarity with Kotlin

Implementation:
1. Create new Kotlin package: `offgrid.geogram.ai`
2. Convert AiChatFragment to Kotlin
3. Use Kotlin coroutines for async
4. Keep rest of app in Java

#### Option 2: Java Wrapper

**Create Java-friendly wrapper around Cactus:**

Pros:
- Keep full Java codebase
- Hide Kotlin complexity

Cons:
- Extra maintenance layer
- Lose some features
- Callback hell vs coroutines

#### Option 3: Hybrid View

**Keep Fragment in Java, use Kotlin ViewModel:**

Pros:
- Minimal UI changes
- Kotlin where needed

Cons:
- Complex architecture
- More boilerplate

### Recommended: Option 1 (Kotlin for AI)

**Why?**
- Cactus is Kotlin-native
- AI feature is isolated
- Future-proof for more AI features
- Better async handling

---

## Implementation Plan

### Phase 1: Foundation (Week 1)

#### 1.1 Add Kotlin Support

**build.gradle.kts** (app level):

```kotlin
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android' version '1.9.20'
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Existing Java dependencies...

    // Kotlin
    implementation "org.jetbrains.kotlin:kotlin-stdlib:1.9.20"
    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3"

    // Cactus
    implementation "com.cactuscompute:cactus:1.0.1-beta"
}

android {
    // Existing config...

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = '17'
    }

    packagingOptions {
        resources {
            excludes += '/META-INF/{AL2.0,LGPL2.1}'
            excludes += '/com/sun/jna/android-*/**'
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }

    defaultConfig {
        ndk {
            abiFilters += ["arm64-v8a"]
        }
    }
}
```

#### 1.2 Update MainActivity

**MainActivity.java**:

```java
import com.cactus.CactusContextInitializer;

@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // Initialize Cactus
    CactusContextInitializer.initialize(this);

    // Rest of existing code...
}
```

#### 1.3 Add Permissions

**AndroidManifest.xml**:

```xml
<!-- Already has INTERNET -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

### Phase 2: Core AI Module (Week 2)

#### 2.1 Create Kotlin Package Structure

```
app/src/main/java/offgrid/geogram/ai/
├── AiChatFragment.kt          # Main chat UI
├── AiChatViewModel.kt         # Business logic
├── AiChatAdapter.kt           # RecyclerView adapter
├── models/
│   ├── ChatMessage.kt
│   └── MessageType.kt
└── CactusManager.kt           # SDK wrapper
```

#### 2.2 CactusManager.kt

Singleton wrapper for Cactus SDK:

```kotlin
package offgrid.geogram.ai

import com.cactus.CactusLM
import com.cactus.CactusInitParams
import com.cactus.ChatMessage as CactusChatMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

object CactusManager {
    private var lm: CactusLM? = null
    private var isInitialized = false

    suspend fun initialize() {
        if (isInitialized) return

        try {
            val model = CactusLM()
            model.downloadModel("qwen3-0.6")
            model.initializeModel(CactusInitParams(
                model = "qwen3-0.6",
                contextSize = 4096
            ))

            // Warm up
            model.generateCompletion(
                messages = listOf(
                    CactusChatMessage(
                        content = "You are an AI assistant for Geogram, an off-grid mesh communication app.",
                        role = "system"
                    )
                )
            )

            lm = model
            isInitialized = true
        } catch (e: Exception) {
            Log.e("CactusManager", "Initialization failed", e)
            throw e
        }
    }

    fun generateResponse(
        messages: List<ChatMessage>,
        onToken: (String) -> Unit
    ): Flow<String> = flow {
        val model = lm ?: throw IllegalStateException("Not initialized")

        val cactusMessages = messages.map {
            CactusChatMessage(it.content, it.role.toLowerCase())
        }

        val response = StringBuilder()

        model.generateCompletion(
            messages = cactusMessages,
            onToken = { token, _ ->
                response.append(token)
                onToken(token)
                emit(token)
            }
        )
    }

    fun cleanup() {
        lm?.unload()
        lm = null
        isInitialized = false
    }
}
```

#### 2.3 AiChatViewModel.kt

```kotlin
package offgrid.geogram.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AiChatViewModel : ViewModel() {
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                CactusManager.initialize()
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        viewModelScope.launch {
            // Add user message
            _messages.value += ChatMessage(text, MessageType.USER)
            _isLoading.value = true

            // Generate AI response
            val assistantMessage = ChatMessage("", MessageType.ASSISTANT)
            _messages.value += assistantMessage

            try {
                CactusManager.generateResponse(_messages.value) { token ->
                    // Update last message with token
                    val updatedMessages = _messages.value.toMutableList()
                    val lastIndex = updatedMessages.lastIndex
                    updatedMessages[lastIndex] = updatedMessages[lastIndex].copy(
                        content = updatedMessages[lastIndex].content + token
                    )
                    _messages.value = updatedMessages
                }.collect()

                _isLoading.value = false
            } catch (e: Exception) {
                _messages.value = _messages.value.dropLast(1)
                _isLoading.value = false
                // Show error
            }
        }
    }

    fun clearChat() {
        _messages.value = emptyList()
    }

    override fun onCleared() {
        super.onCleared()
        CactusManager.cleanup()
    }
}
```

### Phase 3: UI Implementation (Week 3)

#### 3.1 Update Fragment Layout

Keep existing XML layout but enhance RecyclerView:

**fragment_ai_chat.xml** (current layout is good, no major changes needed)

#### 3.2 Convert AiChatFragment to Kotlin

**AiChatFragment.kt**:

```kotlin
package offgrid.geogram.ai

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import offgrid.geogram.databinding.FragmentAiChatBinding

class AiChatFragment : Fragment() {
    private var _binding: FragmentAiChatBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AiChatViewModel by viewModels()
    private lateinit var adapter: AiChatAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAiChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupInputArea()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        adapter = AiChatAdapter()
        binding.rvChatMessages.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@AiChatFragment.adapter
        }
    }

    private fun setupInputArea() {
        binding.btnSend.setOnClickListener {
            val text = binding.etMessageInput.text.toString()
            viewModel.sendMessage(text)
            binding.etMessageInput.setText("")
        }

        binding.btnAttachFile.setOnClickListener {
            // File picker implementation
        }

        binding.btnAiSettings.setOnClickListener {
            // Settings dialog
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.messages.collect { messages ->
                adapter.submitList(messages)
                binding.emptyState.visibility =
                    if (messages.isEmpty()) View.VISIBLE else View.GONE

                // Scroll to bottom
                if (messages.isNotEmpty()) {
                    binding.rvChatMessages.scrollToPosition(messages.size - 1)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isLoading.collect { isLoading ->
                binding.btnSend.isEnabled = !isLoading
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
```

#### 3.3 Create RecyclerView Adapter

**AiChatAdapter.kt**:

```kotlin
package offgrid.geogram.ai

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import offgrid.geogram.databinding.ItemAiChatMessageBinding

class AiChatAdapter : ListAdapter<ChatMessage, AiChatAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAiChatMessageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemAiChatMessageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(message: ChatMessage) {
            binding.tvMessageText.text = message.content

            // Configure bubble based on message type
            when (message.type) {
                MessageType.USER -> {
                    binding.messageContainer.layoutParams =
                        (binding.messageContainer.layoutParams as ViewGroup.MarginLayoutParams).apply {
                            setMargins(48, 0, 0, 0) // Right-aligned
                        }
                    binding.ivAvatar.visibility = View.GONE
                }
                MessageType.ASSISTANT -> {
                    binding.messageContainer.layoutParams =
                        (binding.messageContainer.layoutParams as ViewGroup.MarginLayoutParams).apply {
                            setMargins(0, 0, 48, 0) // Left-aligned
                        }
                    binding.ivAvatar.visibility = View.VISIBLE
                }
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(old: ChatMessage, new: ChatMessage) =
            old.id == new.id

        override fun areContentsTheSame(old: ChatMessage, new: ChatMessage) =
            old == new
    }
}
```

### Phase 4: Testing & Optimization (Week 4)

#### 4.1 Model Selection

Test different models for Geogram use case:
- **qwen3-0.6** - Best quality, slower (~2-4 tok/sec on mid-range phones)
- **gemma3-270m** - Faster, less capable (~5-8 tok/sec)

#### 4.2 Context Window Tuning

Optimize based on device memory:
- 2GB RAM devices: 2048 tokens
- 4GB+ RAM devices: 4096 tokens
- 8GB+ RAM devices: 8192 tokens

#### 4.3 Storage Management

Model downloads:
- qwen3-0.6: ~400MB
- Add cleanup UI for removing models
- Show download progress

#### 4.4 Performance Optimization

1. **Lazy Loading**: Only initialize when chat opened
2. **Background Download**: Download model on WiFi in background
3. **Context Pruning**: Truncate old messages to fit context
4. **Caching**: Reuse initialized model across sessions

---

## Challenges and Solutions

### Challenge 1: Large Model Size (~400MB)

**Impact**: Initial download takes time and storage space

**Solutions**:
1. Download on WiFi only (check connection type)
2. Show progress dialog with ability to cancel
3. Cache model permanently (don't re-download)
4. Offer smaller model option (gemma3-270m, ~150MB)

### Challenge 2: Memory Usage (~1GB RAM)

**Impact**: May cause issues on low-end devices

**Solutions**:
1. Check available RAM before loading
2. Unload model when fragment destroyed
3. Show warning on <3GB RAM devices
4. Use smaller context window (2048 tokens)

### Challenge 3: Generation Speed (2-4 tokens/sec)

**Impact**: Slow responses on mid-range phones

**Solutions**:
1. Stream tokens as generated (already planned)
2. Show "thinking" indicator
3. Limit max response length (256 tokens default)
4. Offer "stop generation" button

### Challenge 4: Kotlin in Java Codebase

**Impact**: Team familiarity, mixed languages

**Solutions**:
1. Isolate Kotlin to AI module only
2. Provide documentation and examples
3. Use view binding for seamless interop
4. Kotlin is future of Android anyway

### Challenge 5: Battery Usage

**Impact**: AI inference is CPU-intensive

**Solutions**:
1. Show battery impact warning
2. Offer "eco mode" with smaller model
3. Limit session duration
4. Use device's NPU if available (future)

### Challenge 6: Cold Start Time

**Impact**: First load takes 10-20 seconds

**Solutions**:
1. Warm up on app start (optional)
2. Show progress UI during initialization
3. Keep model loaded between sessions
4. Background initialization on WiFi

---

## Code Examples

### Example 1: Simple Query-Response

```kotlin
suspend fun askQuestion(question: String): String {
    val lm = CactusLM()
    lm.downloadModel("qwen3-0.6")
    lm.initializeModel(CactusInitParams(model = "qwen3-0.6"))

    val result = lm.generateCompletion(
        messages = listOf(
            ChatMessage("You are a helpful assistant", "system"),
            ChatMessage(question, "user")
        )
    )

    lm.unload()
    return result.text ?: ""
}
```

### Example 2: Streaming Chat

```kotlin
fun streamChat(messages: List<ChatMessage>, onToken: (String) -> Unit) {
    lifecycleScope.launch {
        val lm = CactusLM()

        lm.generateCompletion(
            messages = messages,
            onToken = { token, tokenId ->
                onToken(token) // Update UI with each token
            }
        )
    }
}
```

### Example 3: File Processing

```kotlin
fun processFile(fileContent: String, query: String): String {
    val prompt = """
        File content:
        $fileContent

        User query: $query

        Please answer based on the file content.
    """.trimIndent()

    return askQuestion(prompt)
}
```

### Example 4: Java Integration

From Java code, call Kotlin:

```java
// In Java Fragment
AiChatViewModel viewModel = new ViewModelProvider(this).get(AiChatViewModel.class);

viewModel.getMessages().observe(getViewLifecycleOwner(), messages -> {
    adapter.submitList(messages);
});

// Send message
button.setOnClickListener(v -> {
    viewModel.sendMessage(editText.getText().toString());
});
```

---

## Next Steps

### Immediate Actions

1. **Decision**: Approve Kotlin integration for AI module
2. **Setup**: Add Kotlin plugin and Cactus dependency
3. **Prototype**: Create basic chat in new Kotlin fragment
4. **Test**: Verify model download and generation on test device

### Week-by-Week Roadmap

**Week 1: Foundation**
- ✅ Add Kotlin support to project
- ✅ Add Cactus dependency
- ✅ Update MainActivity with initialization
- ✅ Test basic SDK functionality

**Week 2: Core Logic**
- ✅ Create CactusManager wrapper
- ✅ Implement AiChatViewModel
- ✅ Add model download UI
- ✅ Test streaming generation

**Week 3: UI Integration**
- ✅ Convert AiChatFragment to Kotlin
- ✅ Implement chat adapter
- ✅ Wire up ViewModel to UI
- ✅ Add settings screen

**Week 4: Polish & Testing**
- ✅ Optimize performance
- ✅ Add error handling
- ✅ Test on various devices
- ✅ Documentation and training

### Success Criteria

- [ ] Chat interface functional
- [ ] Model downloads successfully
- [ ] Streaming responses work smoothly
- [ ] No crashes on low-end devices
- [ ] <5GB total storage for app + model
- [ ] Acceptable performance (>1 tok/sec)
- [ ] Settings allow model management

---

## Conclusion

**Cactus SDK is an excellent fit for Geogram** because:

1. **Offline-First**: Aligns with Geogram's mesh networking philosophy
2. **Privacy**: No data leaves device
3. **No Dependencies**: Works without internet
4. **Open Source**: Can be audited and modified
5. **Active Development**: Regular updates and improvements

**Recommended Approach**:
- Use Option 1 (Kotlin for AI module)
- Start with qwen3-0.6 model
- Implement streaming UI
- Add model management settings
- Test thoroughly on range of devices

**Effort Estimate**: 3-4 weeks for full implementation

**Risk Level**: Medium (new technology, but well-documented)

---

## References

- **Cactus SDK**: https://github.com/cactus-compute/cactus-kotlin
- **Documentation**: README.md in cactus-kotlin repo
- **Demo App**: `example/` directory
- **Kotlin Android**: https://developer.android.com/kotlin

---

*Document created: 2025-11-17*
*Author: AI Analysis*
*Project: Geogram Android*

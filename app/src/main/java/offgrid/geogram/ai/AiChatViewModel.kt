package offgrid.geogram.ai

import android.app.Application
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import offgrid.geogram.ai.models.AiChatSettings
import offgrid.geogram.ai.models.ChatMessage
import offgrid.geogram.ai.models.MessageType
import android.util.Log

/**
 * ViewModel for the AI Chat screen.
 * Manages chat messages and interaction with CactusManager.
 */
class AiChatViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "GPT-AiChatViewModel"

    // Settings management
    private val prefsManager = AiChatPreferences(application)
    private var currentSettings: AiChatSettings = prefsManager.loadSettings()

    // LiveData for messages
    private val _messages = MutableLiveData<List<ChatMessage>>(emptyList())
    val messages: LiveData<List<ChatMessage>> = _messages

    // LiveData for AI status
    private val _aiStatus = MutableLiveData<CactusManager.Status>(CactusManager.Status.UNINITIALIZED)
    val aiStatus: LiveData<CactusManager.Status> = _aiStatus

    // LiveData for status message
    private val _statusMessage = MutableLiveData<String>("")
    val statusMessage: LiveData<String> = _statusMessage

    // LiveData for errors
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    // LiveData for Whisper ready status
    private val _whisperReady = MutableLiveData<Boolean>(false)
    val whisperReady: LiveData<Boolean> = _whisperReady

    // Track if initialization is in progress
    private var isInitializing = false

    // Track current recording placeholder
    private var currentRecordingPlaceholderId: String? = null

    // Track if recording is in progress
    private var isRecordingInProgress = false

    init {
        // Set up status listener
        CactusManager.setStatusListener { status, message ->
            _aiStatus.postValue(status)
            _statusMessage.postValue(message)
        }

        Log.i(TAG, "Loaded settings: model=${currentSettings.modelName}, " +
                "maxTokens=${currentSettings.maxTokens}, temp=${currentSettings.temperature}")
    }

    /**
     * Get current AI chat settings.
     */
    fun getSettings(): AiChatSettings = currentSettings

    /**
     * Update AI chat settings.
     * Automatically reinitializes the AI if model or context changes.
     */
    fun updateSettings(newSettings: AiChatSettings) {
        val modelChanged = currentSettings.modelName != newSettings.modelName
        val contextChanged = currentSettings.contextSize != newSettings.contextSize
        val gpuChanged = currentSettings.gpuLayers != newSettings.gpuLayers
        val threadsChanged = currentSettings.cpuThreads != newSettings.cpuThreads
        val whisperModelChanged = currentSettings.whisperModel != newSettings.whisperModel

        currentSettings = newSettings
        prefsManager.saveSettings(newSettings)

        Log.i(TAG, "Settings updated: model=${newSettings.modelName}, " +
                "maxTokens=${newSettings.maxTokens}, temp=${newSettings.temperature}, " +
                "showThinking=${newSettings.showThinking}, whisperModel=${newSettings.whisperModel}")

        // If model, context, GPU, or CPU settings changed, reinitialize
        if (modelChanged || contextChanged || gpuChanged || threadsChanged) {
            viewModelScope.launch {
                try {
                    // Unload current model
                    CactusManager.unload()

                    // Reinitialize with new settings
                    initializeAI()
                } catch (e: Exception) {
                    Log.e(TAG, "Error reloading AI with new settings", e)
                    _error.postValue("Failed to apply settings: ${e.message}")
                }
            }
        }

        // If Whisper model changed, reinitialize Whisper
        if (whisperModelChanged) {
            viewModelScope.launch {
                try {
                    Log.i(TAG, "Reinitializing Whisper with new model: ${newSettings.whisperModel}")
                    _whisperReady.postValue(false)
                    WhisperManager.cleanup()
                    WhisperManager.downloadModel(newSettings.whisperModel)
                    WhisperManager.initializeModel(newSettings.whisperModel)
                    _whisperReady.postValue(true)
                } catch (e: Exception) {
                    Log.e(TAG, "Error reloading Whisper with new model", e)
                    _error.postValue("Failed to load voice model: ${e.message}")
                    _whisperReady.postValue(false)
                }
            }
        }
    }

    /**
     * Check if device has enough storage space.
     */
    private fun hasEnoughStorage(requiredMB: Long): Pair<Boolean, Long> {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
            val availableMB = availableBytes / (1024 * 1024)
            val hasSpace = availableMB >= requiredMB
            Pair(hasSpace, availableMB)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking storage", e)
            Pair(true, 0) // Assume space is available if check fails
        }
    }

    /**
     * Initialize the AI model.
     * This should be called when the chat screen is opened.
     */
    fun initializeAI() {
        if (isInitializing || CactusManager.isReady()) {
            Log.d(TAG, "AI already initializing or ready, skipping")
            return
        }

        isInitializing = true
        viewModelScope.launch {
            try {
                Log.i(TAG, "Starting AI initialization...")

                // Check storage
                val MODEL_SIZE_MB = 500L // Approximate size needed (model + cache)
                val (hasSpace, availableMB) = hasEnoughStorage(MODEL_SIZE_MB)

                if (!hasSpace) {
                    val errorMsg = "Insufficient storage: ${availableMB} MB available, ${MODEL_SIZE_MB} MB required. Please free up space."
                    _error.postValue(errorMsg)
                    addSystemMessage("✗ $errorMsg")
                    isInitializing = false
                    return@launch
                }

                Log.i(TAG, "Storage OK: ${availableMB} MB available")

                // Download and initialize the model
                val DOWNLOAD_SIZE_MB = 400 // Approximate download size of qwen3-0.6
                CactusManager.downloadModel(
                    modelName = currentSettings.modelName,
                    onProgress = { progress ->
                        val percent = (progress * 100).toInt()
                        val downloadedMB = (DOWNLOAD_SIZE_MB * progress).toInt()
                        _statusMessage.postValue("Downloading model: $percent%")
                    }
                )

                CactusManager.initializeModel(
                    modelName = currentSettings.modelName,
                    contextSize = currentSettings.contextSize,
                    gpuLayers = currentSettings.gpuLayers,
                    cpuThreads = currentSettings.cpuThreads
                )

                Log.i(TAG, "AI initialization complete")

                // Also initialize Whisper for voice input
                initializeWhisper()

            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize AI", e)
                val errorMsg = "Failed to initialize AI: ${e.message ?: "Unknown error"}"
                _error.postValue(errorMsg)
                addSystemMessage("✗ $errorMsg")
            } finally {
                isInitializing = false
            }
        }
    }

    /**
     * Initialize Whisper for speech-to-text.
     * This is called automatically after AI initialization.
     */
    private fun initializeWhisper() {
        viewModelScope.launch {
            try {
                Log.i(TAG, "Starting Whisper initialization with model: ${currentSettings.whisperModel}")

                WhisperManager.downloadModel(currentSettings.whisperModel)
                WhisperManager.initializeModel(currentSettings.whisperModel)

                _whisperReady.postValue(true)
                Log.i(TAG, "Whisper initialization complete")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize Whisper", e)
                _whisperReady.postValue(false)
            }
        }
    }

    /**
     * Start voice recording - called when user presses mic button.
     * Immediately starts recording audio. No UI messages shown yet.
     */
    fun startVoiceRecording() {
        if (!WhisperManager.isReady()) {
            _error.postValue("Voice input not ready. Please wait for initialization.")
            return
        }

        if (isRecordingInProgress) {
            Log.w(TAG, "Recording already in progress, ignoring start request")
            return
        }

        Log.i(TAG, "Starting voice recording - capturing audio now")
        isRecordingInProgress = true
        currentRecordingPlaceholderId = null

        // Start recording in background with long max duration
        // User will press button again to stop it manually
        viewModelScope.launch {
            try {
                Log.d(TAG, "Calling WhisperManager.transcribeFromMicrophone() - recording starts NOW")
                val result = WhisperManager.transcribeFromMicrophone(
                    sampleRate = 16000,
                    maxDuration = 60000, // 60 seconds max (user will stop manually before this)
                    maxSilenceDuration = 60000 // Very long silence duration so it doesn't auto-stop
                )

                // Recording completed (either by user pressing stop button or timeout)
                isRecordingInProgress = false
                Log.d(TAG, "Recording completed, processing transcription result")

                if (result.success && !result.text.isNullOrBlank()) {
                    val transcribedText = result.text!!
                    Log.i(TAG, "Transcription successful: $transcribedText")

                    // Check if we already have a processing message placeholder
                    if (currentRecordingPlaceholderId != null) {
                        // We have the "Thinking..." placeholder
                        // First, add user message BEFORE the thinking placeholder
                        insertMessageBeforePlaceholder(ChatMessage.user(transcribedText), currentRecordingPlaceholderId!!)

                        // Generate AI response, replacing the "Thinking..." message
                        generateResponseToTranscription(transcribedText, currentRecordingPlaceholderId!!)
                    } else {
                        // No placeholder yet (user didn't click stop button, timeout happened)
                        // Add user message normally, then create placeholder
                        val userMessage = ChatMessage.user(transcribedText)
                        addMessage(userMessage)

                        val processingMessage = ChatMessage.assistant("Thinking...", isStreaming = true)
                        addMessage(processingMessage)
                        generateResponseToTranscription(transcribedText, processingMessage.id)
                    }
                } else {
                    val errorMsg = result.text ?: "Failed to transcribe audio"
                    _error.postValue("Transcription failed: $errorMsg")
                    Log.e(TAG, "Transcription failed: $errorMsg")

                    // Remove the processing message on error if it exists
                    if (currentRecordingPlaceholderId != null) {
                        removeMessageById(currentRecordingPlaceholderId!!)
                        currentRecordingPlaceholderId = null
                    }
                }
            } catch (e: Exception) {
                isRecordingInProgress = false
                Log.e(TAG, "Error during voice recording", e)
                _error.postValue("Voice input error: ${e.message}")

                // Remove the processing message on error if it exists
                if (currentRecordingPlaceholderId != null) {
                    removeMessageById(currentRecordingPlaceholderId!!)
                    currentRecordingPlaceholderId = null
                }
            }
        }
    }

    /**
     * Stop voice recording and start showing "Thinking..." UI.
     * This manually stops the ongoing recording and shows processing state.
     */
    fun stopVoiceRecording() {
        if (!isRecordingInProgress) {
            Log.w(TAG, "No recording in progress, ignoring stop request")
            return
        }

        Log.i(TAG, "stopVoiceRecording called - showing Thinking balloon and stopping Whisper")

        // Show the "Thinking..." balloon FIRST (before stopping, so UI updates immediately)
        val processingMessage = ChatMessage.assistant("Thinking...", isStreaming = true)
        addMessage(processingMessage)
        currentRecordingPlaceholderId = processingMessage.id

        // Stop the ongoing recording on a background thread to avoid blocking UI
        viewModelScope.launch(Dispatchers.IO) {
            WhisperManager.stop()
            Log.d(TAG, "Whisper stop() completed on background thread")
        }

        // The transcription result will be handled in the startVoiceRecording() coroutine
        // when WhisperManager.transcribeFromMicrophone() returns
    }

    /**
     * Update a user message by ID
     */
    private fun updateUserMessage(messageId: String, newText: String) {
        val currentMessages = _messages.value?.toMutableList() ?: return
        val index = currentMessages.indexOfFirst { it.id == messageId }
        if (index >= 0) {
            currentMessages[index] = currentMessages[index].withContent(newText)
            _messages.postValue(currentMessages)
        }
    }

    /**
     * Update an assistant message by ID
     */
    private fun updateAssistantMessage(messageId: String, newText: String) {
        val currentMessages = _messages.value?.toMutableList() ?: return
        val index = currentMessages.indexOfFirst { it.id == messageId }
        if (index >= 0) {
            currentMessages[index] = currentMessages[index].withContent(newText)
            _messages.postValue(currentMessages)
        }
    }

    /**
     * Update an assistant message by ID immediately (synchronous)
     */
    private fun updateAssistantMessageImmediate(messageId: String, newText: String) {
        val currentMessages = _messages.value?.toMutableList() ?: return
        val index = currentMessages.indexOfFirst { it.id == messageId }
        if (index >= 0) {
            currentMessages[index] = currentMessages[index].withContent(newText)
            _messages.value = currentMessages  // Use value for immediate update
        }
    }

    /**
     * Remove a message by ID
     */
    private fun removeMessageById(messageId: String) {
        val currentMessages = _messages.value?.toMutableList() ?: return
        currentMessages.removeAll { it.id == messageId }
        _messages.postValue(currentMessages)
    }

    /**
     * Generate AI response for transcribed text using existing placeholder
     */
    private fun generateResponseToTranscription(text: String, existingPlaceholderId: String) {
        // Create the user message that will be used for context
        val userMessage = ChatMessage.user(text)
        generateResponse(userMessage, existingPlaceholderId)
    }

    /**
     * Send a message to the AI.
     *
     * @param text The message text
     * @param attachments Optional file attachments
     */
    fun sendMessage(text: String, attachments: List<Uri> = emptyList()) {
        if (text.isBlank() && attachments.isEmpty()) {
            return
        }

        // Add user message
        val userMessage = ChatMessage.user(text, attachments)
        addMessage(userMessage)

        // Log the user message
        Log.i(TAG, "User message: ${text.take(100)}")

        // Generate AI response (pass the user message to ensure it's included)
        generateResponse(userMessage)
    }

    /**
     * Generate AI response to the conversation.
     * @param latestUserMessage The most recent user message (to ensure it's included in context)
     * @param existingPlaceholderId Optional ID of existing placeholder to reuse
     */
    private fun generateResponse(latestUserMessage: ChatMessage? = null, existingPlaceholderId: String? = null) {
        if (!CactusManager.isReady()) {
            val status = CactusManager.getStatus()
            val message = when (status) {
                CactusManager.Status.UNINITIALIZED -> "AI is starting up. Please wait..."
                CactusManager.Status.DOWNLOADING -> "AI model is downloading. Please wait..."
                CactusManager.Status.LOADING -> "AI model is loading. Please wait..."
                CactusManager.Status.ERROR -> "AI initialization failed. Please restart the app."
                else -> "AI not ready. Please wait..."
            }
            addSystemMessage("⏳ $message")
            return
        }

        viewModelScope.launch {
            try {
                // Get messages for context BEFORE adding placeholder (excluding system messages)
                var contextMessages = _messages.value?.filter {
                    it.type != MessageType.SYSTEM && !it.isStreaming
                }?.toMutableList() ?: mutableListOf()

                // Ensure the latest user message is included (in case LiveData hasn't updated yet)
                if (latestUserMessage != null) {
                    // Check if it's already in the list
                    val alreadyIncluded = contextMessages.any { it.id == latestUserMessage.id }
                    if (!alreadyIncluded) {
                        contextMessages.add(latestUserMessage)
                        Log.d(TAG, "Added latest user message to context (LiveData lag)")
                    }
                }

                // Log what we're sending to AI
                Log.i(TAG, "Sending ${contextMessages.size} messages to AI:")
                contextMessages.forEach { msg ->
                    val preview = msg.content.take(50).replace("\n", " ")
                    Log.i(TAG, "  -> ${msg.type}: $preview${if (msg.content.length > 50) "..." else ""}")
                }

                // Use existing placeholder or create a new one
                val assistantMessageId = if (existingPlaceholderId != null) {
                    // Keep the existing placeholder as-is (still shows "Thinking" animation)
                    existingPlaceholderId
                } else {
                    // Create a new placeholder for the assistant's response with "Thinking" to trigger animation
                    val assistantMessage = ChatMessage.assistant("Thinking...", isStreaming = true)
                    addMessage(assistantMessage)
                    assistantMessage.id
                }

                // Generate response with streaming
                val response = StringBuilder()
                val showThinking = currentSettings.showThinking

                CactusManager.generateCompletion(
                    messages = contextMessages,
                    systemPrompt = currentSettings.systemPrompt,
                    temperature = currentSettings.temperature,
                    maxTokens = currentSettings.maxTokens,
                    onToken = { token ->
                        // Append token to response
                        response.append(token)

                        // Filter thinking content if showThinking is disabled
                        var displayContent = if (!showThinking) {
                            filterThinkingContent(response.toString())
                        } else {
                            response.toString()
                        }

                        // Clean up formatting characters
                        displayContent = cleanMarkdown(displayContent)

                        // Only update if we have actual content (keeps "Thinking" animation until content arrives)
                        if (displayContent.isNotBlank()) {
                            // Update the specific assistant message by ID (stops animation when content changes)
                            if (existingPlaceholderId != null) {
                                updateAssistantMessage(assistantMessageId, displayContent)
                            } else {
                                // Update the last message (the assistant's message) with new content
                                updateLastAssistantMessage(displayContent)
                            }
                        }
                    }
                )

                // Mark streaming as complete
                completeLastAssistantMessage()

                // Log the complete AI response
                val finalResponse = if (!showThinking) {
                    filterThinkingContent(response.toString())
                } else {
                    response.toString()
                }
                val cleanedResponse = cleanMarkdown(finalResponse)
                Log.i(TAG, "AI response: $cleanedResponse")
                Log.d(TAG, "Response generated: ${response.length} characters")

            } catch (e: Exception) {
                Log.e(TAG, "Error generating response", e)
                _error.postValue("Error: ${e.message}")

                // Remove the placeholder message and add an error message
                removeLastMessage()
                addSystemMessage("Error generating response: ${e.message}")
            }
        }
    }

    /**
     * Add a message to the chat.
     */
    private fun addMessage(message: ChatMessage) {
        val currentMessages = _messages.value?.toMutableList() ?: mutableListOf()
        currentMessages.add(message)
        _messages.value = currentMessages  // Use setValue instead of postValue for immediate update
    }

    /**
     * Insert a message before a specific message (identified by ID).
     * Used to insert user message before the "Thinking..." placeholder.
     */
    private fun insertMessageBeforePlaceholder(message: ChatMessage, placeholderId: String) {
        val currentMessages = _messages.value?.toMutableList() ?: mutableListOf()
        val placeholderIndex = currentMessages.indexOfFirst { it.id == placeholderId }

        if (placeholderIndex >= 0) {
            // Insert the message right before the placeholder
            currentMessages.add(placeholderIndex, message)
            Log.d(TAG, "Inserted message before placeholder at index $placeholderIndex")
        } else {
            // Placeholder not found, just add to the end
            currentMessages.add(message)
            Log.w(TAG, "Placeholder not found, added message to end")
        }

        _messages.value = currentMessages
    }

    /**
     * Add a system message.
     */
    private fun addSystemMessage(text: String) {
        addMessage(ChatMessage.system(text))
    }

    /**
     * Update the last system message with new content.
     */
    private fun updateLastSystemMessage(text: String) {
        val currentMessages = _messages.value?.toMutableList() ?: return

        // Find the last system message
        val lastIndex = currentMessages.indexOfLast {
            it.type == MessageType.SYSTEM
        }

        if (lastIndex >= 0) {
            currentMessages[lastIndex] = currentMessages[lastIndex].withContent(text)
            _messages.postValue(currentMessages)
        }
    }

    /**
     * Update the last assistant message with streaming content.
     */
    private fun updateLastAssistantMessage(content: String) {
        val currentMessages = _messages.value?.toMutableList() ?: return

        // Find the last assistant message that is streaming
        val lastIndex = currentMessages.indexOfLast {
            it.type == MessageType.ASSISTANT && it.isStreaming
        }

        if (lastIndex >= 0) {
            currentMessages[lastIndex] = currentMessages[lastIndex].withContent(content)
            _messages.postValue(currentMessages)
        }
    }

    /**
     * Mark the last assistant message as complete (stop streaming).
     */
    private fun completeLastAssistantMessage() {
        val currentMessages = _messages.value?.toMutableList() ?: return

        val lastIndex = currentMessages.indexOfLast {
            it.type == MessageType.ASSISTANT && it.isStreaming
        }

        if (lastIndex >= 0) {
            currentMessages[lastIndex] = currentMessages[lastIndex].completeStreaming()
            _messages.postValue(currentMessages)
        }
    }

    /**
     * Remove the last message (used when an error occurs).
     */
    private fun removeLastMessage() {
        val currentMessages = _messages.value?.toMutableList() ?: return
        if (currentMessages.isNotEmpty()) {
            currentMessages.removeAt(currentMessages.size - 1)
            _messages.postValue(currentMessages)
        }
    }

    /**
     * Filter out thinking/reasoning content from AI response.
     * Removes content within <think>, <thinking>, or <reasoning> tags.
     */
    private fun filterThinkingContent(content: String): String {
        var filtered = content

        // Remove <think>...</think> blocks
        filtered = filtered.replace(Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL), "")

        // Remove <thinking>...</thinking> blocks
        filtered = filtered.replace(Regex("<thinking>.*?</thinking>", RegexOption.DOT_MATCHES_ALL), "")

        // Remove <reasoning>...</reasoning> blocks
        filtered = filtered.replace(Regex("<reasoning>.*?</reasoning>", RegexOption.DOT_MATCHES_ALL), "")

        // Also handle incomplete tags (during streaming)
        if (!currentSettings.showThinking) {
            // If we're in the middle of a thinking block, hide everything after the opening tag
            if (filtered.contains("<think>") && !filtered.contains("</think>")) {
                filtered = filtered.substringBefore("<think>")
            }
            if (filtered.contains("<thinking>") && !filtered.contains("</thinking>")) {
                filtered = filtered.substringBefore("<thinking>")
            }
            if (filtered.contains("<reasoning>") && !filtered.contains("</reasoning>")) {
                filtered = filtered.substringBefore("<reasoning>")
            }
        }

        return filtered.trim()
    }

    /**
     * Clean up markdown and special formatting characters from text
     */
    private fun cleanMarkdown(content: String): String {
        var cleaned = content

        // Remove replacement character (�) and other invisible/control characters
        cleaned = cleaned.replace("\uFFFD", "") // Unicode replacement character
        cleaned = cleaned.replace("�", "")

        // Remove LaTeX formatting characters
        cleaned = cleaned.replace("$", "")

        // Remove markdown bold (**text** or __text__)
        cleaned = cleaned.replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
        cleaned = cleaned.replace(Regex("__(.+?)__"), "$1")

        // Remove markdown italic (*text* or _text_)
        cleaned = cleaned.replace(Regex("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)"), "$1")
        cleaned = cleaned.replace(Regex("(?<!_)_(?!_)(.+?)(?<!_)_(?!_)"), "$1")

        // Remove markdown headers (# ## ### etc)
        cleaned = cleaned.replace(Regex("^#{1,6}\\s+", RegexOption.MULTILINE), "")

        // Remove markdown code blocks (```code```)
        cleaned = cleaned.replace(Regex("```.*?```", RegexOption.DOT_MATCHES_ALL), "")

        // Remove inline code (`code`)
        cleaned = cleaned.replace(Regex("`(.+?)`"), "$1")

        // Remove markdown links [text](url) -> text
        cleaned = cleaned.replace(Regex("\\[(.+?)\\]\\(.+?\\)"), "$1")

        // Remove HTML-like tags that might appear (like <?>)
        cleaned = cleaned.replace(Regex("<[^>]+>"), "")

        return cleaned.trim()
    }

    /**
     * Clear all messages.
     */
    fun clearMessages() {
        _messages.value = emptyList()
        addSystemMessage("Chat cleared. Start a new conversation!")
    }

    /**
     * Clear error state.
     */
    fun clearError() {
        _error.value = null
    }

    /**
     * Clean up resources when ViewModel is destroyed.
     */
    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            try {
                CactusManager.unload()
            } catch (e: Exception) {
                Log.e(TAG, "Error unloading model in onCleared", e)
            }
        }
    }
}

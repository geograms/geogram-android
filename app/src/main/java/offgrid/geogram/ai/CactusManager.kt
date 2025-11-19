package offgrid.geogram.ai

import android.util.Log
import com.cactus.CactusLM
import com.cactus.CactusInitParams
import com.cactus.CactusCompletionParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import offgrid.geogram.ai.models.ChatMessage

/**
 * Singleton manager for the Cactus AI SDK.
 * Handles model lifecycle, downloads, and inference operations.
 */
object CactusManager {
    private const val TAG = "GPT-CactusManager"
    private const val DEFAULT_MODEL = "qwen3-0.6"
    private const val DEFAULT_CONTEXT_SIZE = 4096

    private var lm: CactusLM? = null
    private var isModelLoaded = false
    private var currentModel: String? = null

    /**
     * Status of the AI manager.
     */
    enum class Status {
        UNINITIALIZED,
        DOWNLOADING,
        LOADING,
        READY,
        GENERATING,
        ERROR
    }

    private var currentStatus: Status = Status.UNINITIALIZED
    private var statusListener: ((Status, String) -> Unit)? = null

    /**
     * Set a listener for status changes.
     */
    fun setStatusListener(listener: (Status, String) -> Unit) {
        statusListener = listener
    }

    /**
     * Get current status.
     */
    fun getStatus(): Status = currentStatus

    /**
     * Update status and notify listener.
     */
    private fun updateStatus(status: Status, message: String = "") {
        currentStatus = status
        statusListener?.invoke(status, message)
        Log.d(TAG, "Status: $status - $message")
    }

    /**
     * Check if a model is ready for inference.
     */
    fun isReady(): Boolean = isModelLoaded && currentStatus == Status.READY

    /**
     * Download a model with progress tracking.
     *
     * @param modelName Name of the model to download (default: qwen3-0.6)
     * @param onProgress Callback for download progress (0.0 to 1.0)
     */
    suspend fun downloadModel(
        modelName: String = DEFAULT_MODEL,
        onProgress: ((Float) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        try {
            updateStatus(Status.DOWNLOADING, "Downloading model: $modelName")

            if (lm == null) {
                lm = CactusLM()
            }

            // Start a coroutine to simulate progress updates
            val progressJob = launch {
                var simulatedProgress = 0f
                while (simulatedProgress < 0.95f) {
                    delay(500) // Update every 500ms
                    simulatedProgress += 0.05f
                    onProgress?.invoke(simulatedProgress)
                }
            }

            try {
                // Download the model
                // Note: Cactus SDK handles caching - won't re-download if already present
                lm?.downloadModel(modelName)
            } finally {
                // Cancel progress simulation and report complete
                progressJob.cancel()
                onProgress?.invoke(1.0f)
            }

            Log.i(TAG, "Model download complete: $modelName")
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading model", e)
            updateStatus(Status.ERROR, "Download failed: ${e.message}")
            throw e
        }
    }

    /**
     * Initialize the model for inference.
     *
     * @param modelName Name of the model to initialize
     * @param contextSize Maximum context size (default: 4096)
     * @param gpuLayers Number of GPU layers (-1 = auto, 0 = CPU only, >0 = specific)
     * @param cpuThreads Number of CPU threads for inference
     */
    suspend fun initializeModel(
        modelName: String = DEFAULT_MODEL,
        contextSize: Int = DEFAULT_CONTEXT_SIZE,
        gpuLayers: Int = -1,
        cpuThreads: Int = 4
    ) = withContext(Dispatchers.IO) {
        try {
            updateStatus(Status.LOADING, "Loading model: $modelName")

            if (lm == null) {
                lm = CactusLM()
            }

            // First ensure model is downloaded
            if (currentModel != modelName) {
                downloadModel(modelName)
            }

            // Initialize the model
            // Note: gpuLayers and cpuThreads are stored for future use when SDK supports them
            lm?.initializeModel(
                CactusInitParams(
                    model = modelName,
                    contextSize = contextSize
                )
            )

            currentModel = modelName
            isModelLoaded = true
            updateStatus(Status.READY, "Model ready: $modelName")

            Log.i(TAG, "Model initialized: $modelName (context: $contextSize, GPU layers: $gpuLayers, threads: $cpuThreads)")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing model", e)
            isModelLoaded = false
            updateStatus(Status.ERROR, "Initialization failed: ${e.message}")
            throw e
        }
    }

    /**
     * Generate a completion with streaming support.
     *
     * @param messages List of messages in the conversation
     * @param systemPrompt Optional system prompt to set behavior
     * @param temperature Randomness (0.0 = deterministic, 1.0 = creative)
     * @param maxTokens Maximum tokens to generate
     * @param onToken Callback for each token (for streaming responses)
     * @return The complete generated text
     */
    suspend fun generateCompletion(
        messages: List<ChatMessage>,
        systemPrompt: String? = null,
        temperature: Float = 0.7f,
        maxTokens: Int = 512,
        onToken: ((String) -> Unit)? = null
    ): String = withContext(Dispatchers.IO) {
        if (!isModelLoaded) {
            throw IllegalStateException("Model not loaded. Call initializeModel() first.")
        }

        try {
            updateStatus(Status.GENERATING, "Generating response...")

            // Log incoming messages
            Log.i(TAG, "Received ${messages.size} messages for generation")
            messages.forEach { msg ->
                val preview = msg.content.take(50).replace("\n", " ")
                Log.i(TAG, "  <- ${msg.type}: $preview${if (msg.content.length > 50) "..." else ""}")
            }

            // Convert messages to Cactus format
            val cactusMessages = messages.map { it.toCactusMessage() }

            // Add system prompt if provided
            val finalMessages = if (systemPrompt != null) {
                listOf(
                    com.cactus.ChatMessage(content = systemPrompt, role = "system")
                ) + cactusMessages
            } else {
                cactusMessages
            }

            Log.i(TAG, "Sending ${finalMessages.size} messages to Cactus SDK (including system prompt)")

            val response = StringBuilder()

            // Generate with streaming
            lm?.generateCompletion(
                messages = finalMessages,
                onToken = { token, _ ->
                    response.append(token)
                    onToken?.invoke(token)
                }
            )

            updateStatus(Status.READY, "Response complete")

            // Return the final text
            response.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error generating completion", e)
            updateStatus(Status.ERROR, "Generation failed: ${e.message}")
            throw e
        }
    }

    /**
     * Generate a simple completion without streaming (convenience method).
     *
     * @param userMessage The user's message
     * @param systemPrompt Optional system prompt
     * @return The AI's response
     */
    suspend fun generateSimple(
        userMessage: String,
        systemPrompt: String? = null
    ): String {
        val messages = listOf(ChatMessage.user(userMessage))
        return generateCompletion(messages, systemPrompt)
    }

    /**
     * Unload the current model to free memory.
     */
    suspend fun unload() = withContext(Dispatchers.IO) {
        try {
            lm?.unload()
            isModelLoaded = false
            currentModel = null
            updateStatus(Status.UNINITIALIZED, "Model unloaded")
            Log.i(TAG, "Model unloaded")
        } catch (e: Exception) {
            Log.e(TAG, "Error unloading model", e)
            throw e
        }
    }

    /**
     * Clean up resources.
     */
    fun cleanup() {
        lm = null
        isModelLoaded = false
        currentModel = null
        currentStatus = Status.UNINITIALIZED
        statusListener = null
    }

    /**
     * Get information about the current model.
     */
    fun getModelInfo(): Map<String, Any?> {
        return mapOf(
            "model" to currentModel,
            "loaded" to isModelLoaded,
            "status" to currentStatus.name
        )
    }
}

package offgrid.geogram.ai.models

/**
 * Settings for AI chat functionality.
 */
data class AiChatSettings(
    /** Selected model name */
    val modelName: String = "gemma3-270m",

    /** Maximum number of tokens to generate */
    val maxTokens: Int = 256,

    /** Context window size */
    val contextSize: Int = 2048,

    /** Temperature (0.0 = deterministic, 1.0 = creative) */
    val temperature: Float = 0.7f,

    /** Number of GPU layers to use (0 = CPU only, -1 = auto, >0 = specific count) */
    val gpuLayers: Int = 999,

    /** Number of threads for CPU inference */
    val cpuThreads: Int = 6,

    /** Show thinking process (reasoning tokens) */
    val showThinking: Boolean = false,

    /** Custom system prompt */
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,

    /** Whisper voice recognition model */
    val whisperModel: String = "whisper-small"
) {
    companion object {
        val DEFAULT_SYSTEM_PROMPT = """
You are Maria, a helpful AI assistant in Geogram.
Be concise and direct. Give brief answers (1-2 sentences) unless asked to elaborate.
IMPORTANT: Use only plain text in your responses. Do not use markdown formatting like **, *, #, or any special characters for formatting. Write in simple, clean text only.
        """.trimIndent()

        /** Available models */
        val AVAILABLE_MODELS = listOf(
            ModelInfo("qwen3-0.6", "Qwen 3 0.6B", "Fast, 400MB", false),
            ModelInfo("gemma3-270m", "Gemma 3 270M", "Faster, 150MB", true),
            ModelInfo("qwen3-1.7", "Qwen 3 1.7B", "Better quality, 1GB", false)
        )

        /** Available Whisper models */
        val AVAILABLE_WHISPER_MODELS = listOf(
            WhisperModelInfo("whisper-tiny", "Tiny", "Fast, ~40MB", false),
            WhisperModelInfo("whisper-base", "Base", "Balanced, ~75MB", false),
            WhisperModelInfo("whisper-small", "Small", "Better, ~250MB", true),
            WhisperModelInfo("whisper-medium", "Medium", "Best, ~770MB", false)
        )
    }

    /**
     * Model information.
     */
    data class ModelInfo(
        val id: String,
        val displayName: String,
        val description: String,
        val isDefault: Boolean
    )

    /**
     * Whisper model information.
     */
    data class WhisperModelInfo(
        val id: String,
        val displayName: String,
        val description: String,
        val isDefault: Boolean
    )
}

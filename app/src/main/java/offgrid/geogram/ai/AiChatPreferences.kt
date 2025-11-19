package offgrid.geogram.ai

import android.content.Context
import android.content.SharedPreferences
import offgrid.geogram.ai.models.AiChatSettings

/**
 * Manages AI chat settings persistence using SharedPreferences.
 */
class AiChatPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "ai_chat_settings",
        Context.MODE_PRIVATE
    )

    /**
     * Load settings from SharedPreferences.
     */
    fun loadSettings(): AiChatSettings {
        return AiChatSettings(
            modelName = prefs.getString(KEY_MODEL_NAME, "qwen3-0.6") ?: "qwen3-0.6",
            maxTokens = prefs.getInt(KEY_MAX_TOKENS, 512),
            contextSize = prefs.getInt(KEY_CONTEXT_SIZE, 4096),
            temperature = prefs.getFloat(KEY_TEMPERATURE, 0.7f),
            gpuLayers = prefs.getInt(KEY_GPU_LAYERS, -1),
            cpuThreads = prefs.getInt(KEY_CPU_THREADS, 4),
            showThinking = prefs.getBoolean(KEY_SHOW_THINKING, false),
            systemPrompt = prefs.getString(KEY_SYSTEM_PROMPT, AiChatSettings.DEFAULT_SYSTEM_PROMPT)
                ?: AiChatSettings.DEFAULT_SYSTEM_PROMPT
        )
    }

    /**
     * Save settings to SharedPreferences.
     */
    fun saveSettings(settings: AiChatSettings) {
        prefs.edit().apply {
            putString(KEY_MODEL_NAME, settings.modelName)
            putInt(KEY_MAX_TOKENS, settings.maxTokens)
            putInt(KEY_CONTEXT_SIZE, settings.contextSize)
            putFloat(KEY_TEMPERATURE, settings.temperature)
            putInt(KEY_GPU_LAYERS, settings.gpuLayers)
            putInt(KEY_CPU_THREADS, settings.cpuThreads)
            putBoolean(KEY_SHOW_THINKING, settings.showThinking)
            putString(KEY_SYSTEM_PROMPT, settings.systemPrompt)
            apply()
        }
    }

    /**
     * Reset settings to defaults.
     */
    fun resetSettings() {
        saveSettings(AiChatSettings())
    }

    companion object {
        private const val KEY_MODEL_NAME = "model_name"
        private const val KEY_MAX_TOKENS = "max_tokens"
        private const val KEY_CONTEXT_SIZE = "context_size"
        private const val KEY_TEMPERATURE = "temperature"
        private const val KEY_GPU_LAYERS = "gpu_layers"
        private const val KEY_CPU_THREADS = "cpu_threads"
        private const val KEY_SHOW_THINKING = "show_thinking"
        private const val KEY_SYSTEM_PROMPT = "system_prompt"
    }
}

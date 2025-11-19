package offgrid.geogram.ai

import android.util.Log
import com.cactus.CactusSTT
import com.cactus.SpeechRecognitionParams
import com.cactus.SpeechRecognitionResult
import com.cactus.TranscriptionProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Singleton manager for Whisper speech-to-text functionality.
 * Handles model lifecycle, downloads, and transcription operations.
 */
object WhisperManager {
    private const val TAG = "GPT-WhisperManager"
    private const val DEFAULT_MODEL = "whisper-tiny"

    private var stt: CactusSTT? = null
    private var isModelLoaded = false
    private var currentModel: String? = null

    /**
     * Status of the Whisper manager.
     */
    enum class Status {
        UNINITIALIZED,
        DOWNLOADING,
        LOADING,
        READY,
        RECORDING,
        TRANSCRIBING,
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
     * Check if Whisper is ready for transcription.
     */
    fun isReady(): Boolean = isModelLoaded && currentStatus == Status.READY

    /**
     * Download a Whisper model.
     *
     * @param modelName Name of the model to download (default: whisper-tiny)
     */
    suspend fun downloadModel(
        modelName: String = DEFAULT_MODEL
    ) = withContext(Dispatchers.IO) {
        try {
            updateStatus(Status.DOWNLOADING, "Downloading Whisper model: $modelName")

            if (stt == null) {
                stt = CactusSTT(TranscriptionProvider.WHISPER)
            }

            // Download the model
            val success = stt?.download(modelName) ?: false

            if (!success) {
                throw Exception("Failed to download model")
            }

            Log.i(TAG, "Model download complete: $modelName")
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading Whisper model", e)
            updateStatus(Status.ERROR, "Download failed: ${e.message}")
            throw e
        }
    }

    /**
     * Initialize the Whisper model for transcription.
     *
     * @param modelName Name of the model to initialize
     */
    suspend fun initializeModel(
        modelName: String = DEFAULT_MODEL
    ) = withContext(Dispatchers.IO) {
        try {
            updateStatus(Status.LOADING, "Loading Whisper model: $modelName")

            if (stt == null) {
                stt = CactusSTT(TranscriptionProvider.WHISPER)
            }

            // First ensure model is downloaded
            if (currentModel != modelName) {
                val isDownloaded = stt?.isModelDownloaded(modelName) ?: false
                if (!isDownloaded) {
                    downloadModel(modelName)
                }
            }

            // Initialize the model
            val success = stt?.init(modelName) ?: false

            if (!success) {
                throw Exception("Failed to initialize model")
            }

            currentModel = modelName
            isModelLoaded = true
            updateStatus(Status.READY, "Whisper ready: $modelName")

            Log.i(TAG, "Whisper model initialized: $modelName")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Whisper model", e)
            isModelLoaded = false
            updateStatus(Status.ERROR, "Initialization failed: ${e.message}")
            throw e
        }
    }

    /**
     * Transcribe audio from microphone.
     *
     * @param sampleRate Audio sample rate (default: 16000 Hz)
     * @param maxDuration Maximum recording duration in milliseconds (default: 30 seconds)
     * @param maxSilenceDuration Maximum silence duration before auto-stop in ms (default: 3 seconds)
     * @return Transcription result
     */
    suspend fun transcribeFromMicrophone(
        sampleRate: Int = 16000,
        maxDuration: Int = 30000,
        maxSilenceDuration: Int = 3000
    ): SpeechRecognitionResult = withContext(Dispatchers.IO) {
        if (!isModelLoaded) {
            throw IllegalStateException("Whisper model not loaded. Call initializeModel() first.")
        }

        try {
            updateStatus(Status.RECORDING, "Listening...")

            val params = SpeechRecognitionParams(
                sampleRate = sampleRate,
                maxDuration = maxDuration.toLong(),
                maxSilenceDuration = maxSilenceDuration.toLong(),
                model = currentModel
            )

            updateStatus(Status.TRANSCRIBING, "Transcribing audio...")

            val result = stt?.transcribe(params = params)

            if (result != null && result.success) {
                updateStatus(Status.READY, "Transcription complete")
                Log.i(TAG, "Transcription successful: ${result.text?.take(100)}")
                result
            } else {
                val errorMsg = result?.text ?: "Transcription failed"
                updateStatus(Status.ERROR, errorMsg)
                Log.e(TAG, "Transcription failed: $errorMsg")
                result ?: SpeechRecognitionResult(
                    success = false,
                    text = "Transcription failed"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during transcription", e)
            updateStatus(Status.ERROR, "Transcription error: ${e.message}")
            throw e
        }
    }

    /**
     * Transcribe audio from a file.
     *
     * @param filePath Path to the audio file
     * @param sampleRate Audio sample rate (default: 16000 Hz)
     * @return Transcription result
     */
    suspend fun transcribeFromFile(
        filePath: String,
        sampleRate: Int = 16000
    ): SpeechRecognitionResult = withContext(Dispatchers.IO) {
        if (!isModelLoaded) {
            throw IllegalStateException("Whisper model not loaded. Call initializeModel() first.")
        }

        try {
            updateStatus(Status.TRANSCRIBING, "Transcribing file...")

            val params = SpeechRecognitionParams(
                sampleRate = sampleRate,
                model = currentModel
            )

            val result = stt?.transcribe(
                params = params,
                filePath = filePath
            )

            if (result != null && result.success) {
                updateStatus(Status.READY, "Transcription complete")
                Log.i(TAG, "File transcription successful: ${result.text?.take(100)}")
                result
            } else {
                val errorMsg = result?.text ?: "File transcription failed"
                updateStatus(Status.ERROR, errorMsg)
                Log.e(TAG, "File transcription failed: $errorMsg")
                result ?: SpeechRecognitionResult(
                    success = false,
                    text = "File transcription failed"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during file transcription", e)
            updateStatus(Status.ERROR, "Transcription error: ${e.message}")
            throw e
        }
    }

    /**
     * Stop ongoing transcription.
     */
    fun stop() {
        try {
            stt?.stop()
            if (currentStatus == Status.RECORDING || currentStatus == Status.TRANSCRIBING) {
                updateStatus(Status.READY, "Transcription stopped")
            }
            Log.i(TAG, "Transcription stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping transcription", e)
        }
    }

    /**
     * Clean up resources.
     */
    fun cleanup() {
        stop()
        stt = null
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

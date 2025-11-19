package offgrid.geogram.ai

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.DialogFragment
import offgrid.geogram.R
import offgrid.geogram.ai.models.AiChatSettings

/**
 * Dialog for configuring AI chat settings.
 */
class AiSettingsDialog(
    private val currentSettings: AiChatSettings,
    private val onSettingsChanged: (AiChatSettings) -> Unit
) : DialogFragment() {

    private lateinit var spinnerModel: Spinner
    private lateinit var seekMaxTokens: SeekBar
    private lateinit var tvMaxTokens: TextView
    private lateinit var seekTemperature: SeekBar
    private lateinit var tvTemperature: TextView
    private lateinit var spinnerContextSize: Spinner
    private lateinit var spinnerGpuLayers: Spinner
    private lateinit var seekCpuThreads: SeekBar
    private lateinit var tvCpuThreads: TextView
    private lateinit var spinnerWhisperModel: Spinner
    private lateinit var switchShowThinking: SwitchCompat
    private lateinit var toolbar: Toolbar

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_ai_settings, null)

        initViews(view)
        loadSettingsFromData(currentSettings)
        setupListeners()

        // Create fullscreen dialog
        return AlertDialog.Builder(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            .setView(view)
            .create()
    }

    private fun initViews(view: View) {
        toolbar = view.findViewById(R.id.toolbar)
        spinnerModel = view.findViewById(R.id.spinner_model)
        seekMaxTokens = view.findViewById(R.id.seek_max_tokens)
        tvMaxTokens = view.findViewById(R.id.tv_max_tokens)
        seekTemperature = view.findViewById(R.id.seek_temperature)
        tvTemperature = view.findViewById(R.id.tv_temperature)
        spinnerContextSize = view.findViewById(R.id.spinner_context_size)
        spinnerGpuLayers = view.findViewById(R.id.spinner_gpu_layers)
        seekCpuThreads = view.findViewById(R.id.seek_cpu_threads)
        tvCpuThreads = view.findViewById(R.id.tv_cpu_threads)
        spinnerWhisperModel = view.findViewById(R.id.spinner_whisper_model)
        switchShowThinking = view.findViewById(R.id.switch_show_thinking)

        // Setup toolbar back button - save settings when leaving
        toolbar.setNavigationOnClickListener {
            saveSettings()
        }

        val btnReset = view.findViewById<Button>(R.id.btn_reset)
        btnReset.setOnClickListener { resetSettings() }

        setupSpinners()
    }

    private fun setupSpinners() {
        // Model Spinner
        val modelNames = AiChatSettings.AVAILABLE_MODELS.map { "${it.displayName} - ${it.description}" }
        val modelAdapter = ArrayAdapter(
            requireContext(),
            R.layout.spinner_item_light,
            modelNames
        )
        modelAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_light)
        spinnerModel.adapter = modelAdapter

        // Context Size Spinner
        val contextSizes = listOf("2048", "4096", "8192")
        val contextAdapter = ArrayAdapter(
            requireContext(),
            R.layout.spinner_item_light,
            contextSizes
        )
        contextAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_light)
        spinnerContextSize.adapter = contextAdapter

        // GPU Layers Spinner
        val gpuOptions = listOf("Auto", "CPU Only", "16 Layers", "32 Layers", "All Layers")
        val gpuAdapter = ArrayAdapter(
            requireContext(),
            R.layout.spinner_item_light,
            gpuOptions
        )
        gpuAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_light)
        spinnerGpuLayers.adapter = gpuAdapter

        // Whisper Model Spinner
        val whisperNames = AiChatSettings.AVAILABLE_WHISPER_MODELS.map { "${it.displayName} - ${it.description}" }
        val whisperAdapter = ArrayAdapter(
            requireContext(),
            R.layout.spinner_item_light,
            whisperNames
        )
        whisperAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_light)
        spinnerWhisperModel.adapter = whisperAdapter
    }

    private fun setupListeners() {
        seekMaxTokens.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // Round to nearest 32
                val rounded = (progress / 32) * 32
                tvMaxTokens.text = rounded.toString()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekTemperature.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val temp = progress / 100f
                tvTemperature.text = String.format("%.2f", temp)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekCpuThreads.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val threads = progress.coerceAtLeast(1)
                tvCpuThreads.text = threads.toString()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun resetSettings() {
        val defaults = AiChatSettings()
        loadSettingsFromData(defaults)
        Toast.makeText(requireContext(), "Settings reset to defaults", Toast.LENGTH_SHORT).show()
    }

    private fun loadSettingsFromData(settings: AiChatSettings) {
        // Model
        val modelIndex = AiChatSettings.AVAILABLE_MODELS.indexOfFirst {
            it.id == settings.modelName
        }.coerceAtLeast(0)
        spinnerModel.setSelection(modelIndex)

        // Max Tokens
        seekMaxTokens.progress = settings.maxTokens
        tvMaxTokens.text = settings.maxTokens.toString()

        // Temperature
        seekTemperature.progress = (settings.temperature * 100).toInt()
        tvTemperature.text = String.format("%.2f", settings.temperature)

        // Context Size
        val contextIndex = when (settings.contextSize) {
            2048 -> 0
            4096 -> 1
            8192 -> 2
            else -> 0
        }
        spinnerContextSize.setSelection(contextIndex)

        // GPU Layers
        val gpuIndex = when (settings.gpuLayers) {
            -1 -> 0 // Auto
            0 -> 1  // CPU Only
            16 -> 2
            32 -> 3
            999 -> 4 // All
            else -> 0
        }
        spinnerGpuLayers.setSelection(gpuIndex)

        // CPU Threads
        seekCpuThreads.progress = settings.cpuThreads
        tvCpuThreads.text = settings.cpuThreads.toString()

        // Whisper Model
        val whisperIndex = AiChatSettings.AVAILABLE_WHISPER_MODELS.indexOfFirst {
            it.id == settings.whisperModel
        }.coerceAtLeast(0)
        spinnerWhisperModel.setSelection(whisperIndex)

        // Show Thinking
        switchShowThinking.isChecked = settings.showThinking
    }

    private fun saveSettings() {
        // Get model
        val selectedModelId = AiChatSettings.AVAILABLE_MODELS[spinnerModel.selectedItemPosition].id

        // Get context size
        val contextSize = when (spinnerContextSize.selectedItemPosition) {
            0 -> 2048
            1 -> 4096
            2 -> 8192
            else -> 2048
        }

        // Get GPU layers
        val gpuLayers = when (spinnerGpuLayers.selectedItemPosition) {
            0 -> -1  // Auto
            1 -> 0   // CPU Only
            2 -> 16
            3 -> 32
            4 -> 999 // All
            else -> -1
        }

        // Get Whisper model
        val selectedWhisperModel = AiChatSettings.AVAILABLE_WHISPER_MODELS[spinnerWhisperModel.selectedItemPosition].id

        val newSettings = AiChatSettings(
            modelName = selectedModelId,
            maxTokens = (seekMaxTokens.progress / 32) * 32, // Rounded to 32
            contextSize = contextSize,
            temperature = seekTemperature.progress / 100f,
            gpuLayers = gpuLayers,
            cpuThreads = seekCpuThreads.progress.coerceAtLeast(1),
            showThinking = switchShowThinking.isChecked,
            systemPrompt = currentSettings.systemPrompt, // Keep existing prompt
            whisperModel = selectedWhisperModel
        )

        onSettingsChanged(newSettings)
        dismiss()
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        // This ensures settings are saved even if user presses back button or touches outside
        if (!isStateSaved) {
            saveSettings()
        }
    }

    private var isStateSaved = false

    override fun dismiss() {
        isStateSaved = true
        super.dismiss()
    }
}

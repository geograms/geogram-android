package offgrid.geogram.fragments

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import offgrid.geogram.R
import offgrid.geogram.ai.AiChatViewModel
import offgrid.geogram.ai.AiSettingsDialog
import offgrid.geogram.ai.ChatMessageAdapter
import offgrid.geogram.ai.WhisperManager
import offgrid.geogram.core.Log

/**
 * Fragment for AI Chat Assistant
 * Provides a chat interface for interacting with an AI assistant using Cactus SDK
 */
class AiChatFragment : Fragment() {

    companion object {
        private const val TAG = "GPT-AiChatFragment"
        private const val RECORD_AUDIO_PERMISSION_REQUEST = 1002
    }

    // Voice button states
    private enum class VoiceButtonState {
        IDLE,           // Ready to record
        RECORDING,      // Currently recording
        PROCESSING      // Transcribing or AI generating
    }

    // ViewModel
    private lateinit var viewModel: AiChatViewModel

    // UI Components
    private lateinit var rvChatMessages: RecyclerView
    private lateinit var etMessageInput: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var btnVoiceInput: ImageButton
    private lateinit var btnAiSettings: ImageButton
    private lateinit var emptyState: View

    // Adapter
    private lateinit var chatAdapter: ChatMessageAdapter

    // Voice recording state
    private var voiceButtonState: VoiceButtonState = VoiceButtonState.IDLE

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_ai_chat, container, false)

        // Initialize ViewModel
        viewModel = ViewModelProvider(this)[AiChatViewModel::class.java]

        // Initialize views
        initViews(view)

        // Setup RecyclerView
        setupRecyclerView()

        // Setup click listeners
        setupClickListeners()

        // Observe ViewModel
        observeViewModel()

        // Initialize AI
        viewModel.initializeAI()

        Log.i(TAG, "AI Chat fragment created")
        return view
    }

    private fun initViews(view: View) {
        rvChatMessages = view.findViewById(R.id.rv_chat_messages)
        etMessageInput = view.findViewById(R.id.et_message_input)
        btnSend = view.findViewById(R.id.btn_send)
        btnVoiceInput = view.findViewById(R.id.btn_voice_input)
        btnAiSettings = view.findViewById(R.id.btn_ai_settings)
        emptyState = view.findViewById(R.id.empty_state)

        // Disable send button initially until AI is ready
        btnSend.isEnabled = false
        btnSend.alpha = 0.5f

        // Set voice button state based on current Whisper status
        val whisperReady = WhisperManager.isReady()
        btnVoiceInput.isEnabled = whisperReady
        btnVoiceInput.alpha = if (whisperReady) 1.0f else 0.5f
    }

    private fun setupRecyclerView() {
        // Setup adapter
        chatAdapter = ChatMessageAdapter()

        // Setup layout manager
        val layoutManager = LinearLayoutManager(context).apply {
            stackFromEnd = true // Start from bottom (like WhatsApp)
        }

        rvChatMessages.layoutManager = layoutManager
        rvChatMessages.adapter = chatAdapter

        // Auto-scroll to bottom when new message arrives
        chatAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                // Only scroll if there are items
                if (chatAdapter.itemCount > 0) {
                    rvChatMessages.smoothScrollToPosition(chatAdapter.itemCount - 1)
                }
            }

            override fun onItemRangeChanged(positionStart: Int, itemCount: Int) {
                // Auto-scroll when streaming updates
                if (chatAdapter.itemCount > 0) {
                    val lastVisiblePosition = layoutManager.findLastCompletelyVisibleItemPosition()
                    if (lastVisiblePosition == chatAdapter.itemCount - 2 || lastVisiblePosition == chatAdapter.itemCount - 1) {
                        rvChatMessages.scrollToPosition(chatAdapter.itemCount - 1)
                    }
                }
            }
        })
    }

    private fun setupClickListeners() {
        // Send button
        btnSend.setOnClickListener {
            val message = etMessageInput.text.toString().trim()
            if (message.isNotEmpty()) {
                sendMessage(message)
            } else {
                Toast.makeText(context, "Please enter a message", Toast.LENGTH_SHORT).show()
            }
        }

        // Voice input button
        btnVoiceInput.setOnClickListener {
            handleVoiceInput()
        }

        // Settings button
        btnAiSettings.setOnClickListener {
            showAiSettings()
        }
    }

    private fun observeViewModel() {
        // Observe messages
        viewModel.messages.observe(viewLifecycleOwner) { messages ->
            Log.d(TAG, "Messages updated: ${messages.size} messages")

            // Check if we need to force rebind for "Thinking..." animation
            var thinkingIndex = -1
            messages.forEachIndexed { index, msg ->
                Log.d(TAG, "  [$index] ${msg.type}: ${msg.content.take(20)}... (streaming=${msg.isStreaming}, id=${msg.id.take(8)})")

                // Track if this is a "Thinking..." message (animation trigger)
                if (msg.isStreaming && msg.content.contains("Thinking")) {
                    Log.d(TAG, "Detected 'Thinking...' at index $index")
                    thinkingIndex = index
                }
            }

            // Submit the list with a callback that runs AFTER list is committed
            chatAdapter.submitList(messages.toList()) {
                Log.d(TAG, "submitList callback executed, list committed to adapter, thinkingIndex=$thinkingIndex")
                // Now force rebind if we detected a thinking message
                if (thinkingIndex >= 0) {
                    Log.d(TAG, "Inside if block - forcing rebind for thinking message at index $thinkingIndex")
                    Log.d(TAG, "Adapter: $chatAdapter, itemCount: ${chatAdapter.itemCount}")
                    chatAdapter.forceRebindItem(thinkingIndex)
                    Log.d(TAG, "forceRebindItem call returned")
                }
            }

            // Show/hide empty state
            if (messages.isEmpty()) {
                emptyState.visibility = View.VISIBLE
                rvChatMessages.visibility = View.GONE
            } else {
                emptyState.visibility = View.GONE
                rvChatMessages.visibility = View.VISIBLE
            }

            // Update button state based on AI processing
            val isAiThinking = messages.any {
                it.type == offgrid.geogram.ai.models.MessageType.ASSISTANT && it.isStreaming
            }

            // Disable send button while AI is thinking/answering
            if (isAiThinking) {
                btnSend.isEnabled = false
                btnSend.alpha = 0.5f
            } else {
                // Re-enable send button only if AI is ready
                val isReady = viewModel.aiStatus.value == offgrid.geogram.ai.CactusManager.Status.READY
                btnSend.isEnabled = isReady
                btnSend.alpha = if (isReady) 1.0f else 0.5f
            }

            if (isAiThinking && voiceButtonState == VoiceButtonState.IDLE) {
                // AI started thinking, disable voice button
                setVoiceButtonState(VoiceButtonState.PROCESSING)
            } else if (!isAiThinking && voiceButtonState == VoiceButtonState.PROCESSING) {
                // AI finished, return to idle
                setVoiceButtonState(VoiceButtonState.IDLE)
            }
        }

        // Observe AI status and enable/disable send button (only when not thinking)
        viewModel.aiStatus.observe(viewLifecycleOwner) { status ->
            Log.d(TAG, "AI Status: $status")
            val isReady = status == offgrid.geogram.ai.CactusManager.Status.READY

            // Only enable send button if AI is ready AND not currently thinking
            val isAiThinking = viewModel.messages.value?.any {
                it.type == offgrid.geogram.ai.models.MessageType.ASSISTANT && it.isStreaming
            } ?: false

            btnSend.isEnabled = isReady && !isAiThinking
            btnSend.alpha = if (isReady && !isAiThinking) 1.0f else 0.5f
        }

        // Observe status messages
        viewModel.statusMessage.observe(viewLifecycleOwner) { message ->
            if (message.isNotEmpty()) {
                Log.d(TAG, "Status: $message")
                // Optionally show status in UI
            }
        }

        // Observe Whisper ready status
        viewModel.whisperReady.observe(viewLifecycleOwner) { ready ->
            // Update button enabled state based on Whisper readiness
            if (voiceButtonState == VoiceButtonState.IDLE && !ready) {
                btnVoiceInput.isEnabled = false
                btnVoiceInput.alpha = 0.5f
            } else if (voiceButtonState == VoiceButtonState.IDLE && ready) {
                btnVoiceInput.isEnabled = true
                btnVoiceInput.alpha = 1.0f
            }
            Log.d(TAG, "Whisper ready: $ready")
        }

        // Observe errors
        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                viewModel.clearError()

                // Reset recording state on error
                if (voiceButtonState == VoiceButtonState.RECORDING) {
                    setVoiceButtonState(VoiceButtonState.IDLE)
                }
            }
        }
    }

    /**
     * Send message to AI
     */
    private fun sendMessage(message: String) {
        Log.i(TAG, "Sending message: $message")

        // Send through ViewModel
        viewModel.sendMessage(message, emptyList())

        // Clear input
        etMessageInput.text.clear()
    }

    /**
     * Show AI settings dialog
     */
    private fun showAiSettings() {
        Log.i(TAG, "Opening AI settings dialog")

        val currentSettings = viewModel.getSettings()

        val dialog = AiSettingsDialog(currentSettings) { newSettings ->
            viewModel.updateSettings(newSettings)
            Toast.makeText(context, "Settings saved", Toast.LENGTH_SHORT).show()
            Log.i(TAG, "Settings updated: model=${newSettings.modelName}, " +
                    "maxTokens=${newSettings.maxTokens}, temp=${newSettings.temperature}")
        }

        dialog.show(parentFragmentManager, "AiSettingsDialog")
    }

    /**
     * Handle voice input button click
     */
    private fun handleVoiceInput() {
        when (voiceButtonState) {
            VoiceButtonState.RECORDING -> {
                // Already recording - stop it
                stopVoiceRecording()
            }
            VoiceButtonState.IDLE -> {
                // Not recording - check permission and start
                if (checkAndRequestMicrophonePermission()) {
                    startVoiceRecording()
                }
            }
            VoiceButtonState.PROCESSING -> {
                // Currently processing - ignore clicks
                Log.d(TAG, "Ignoring voice button click - currently processing")
            }
        }
    }

    /**
     * Check if microphone permission is granted, request if not
     */
    private fun checkAndRequestMicrophonePermission(): Boolean {
        val context = requireContext()

        return if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Request permission
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.RECORD_AUDIO),
                RECORD_AUDIO_PERMISSION_REQUEST
            )
            false
        } else {
            // Permission already granted
            true
        }
    }

    /**
     * Start voice recording
     */
    private fun startVoiceRecording() {
        Log.i(TAG, "Starting voice recording")
        setVoiceButtonState(VoiceButtonState.RECORDING)

        // Post to ensure UI updates immediately before starting transcription
        btnVoiceInput.post {
            viewModel.startVoiceRecording()
        }
    }

    /**
     * Stop voice recording and start processing
     */
    private fun stopVoiceRecording() {
        Log.i(TAG, "Stopping voice recording")
        setVoiceButtonState(VoiceButtonState.PROCESSING)

        // Post to ensure all UI updates complete before calling ViewModel
        btnVoiceInput.post {
            Log.d(TAG, "UI updated, now stopping Whisper and processing")
            viewModel.stopVoiceRecording()
        }
    }

    /**
     * Set voice button state and update UI accordingly
     */
    private fun setVoiceButtonState(newState: VoiceButtonState) {
        Log.d(TAG, "setVoiceButtonState: $voiceButtonState -> $newState")
        voiceButtonState = newState

        when (newState) {
            VoiceButtonState.IDLE -> {
                // Microphone icon, enabled if Whisper is ready
                btnVoiceInput.isSelected = false
                btnVoiceInput.setImageResource(R.drawable.btn_voice_states)
                btnVoiceInput.clearAnimation()

                val whisperReady = viewModel.whisperReady.value ?: false
                btnVoiceInput.isEnabled = whisperReady
                btnVoiceInput.alpha = if (whisperReady) 1.0f else 0.5f
                btnVoiceInput.contentDescription = "Voice input"
            }
            VoiceButtonState.RECORDING -> {
                // Recording indicator, enabled (to allow stopping)
                btnVoiceInput.isSelected = true
                btnVoiceInput.setImageResource(R.drawable.btn_voice_states)
                btnVoiceInput.isEnabled = true
                btnVoiceInput.alpha = 1.0f
                btnVoiceInput.contentDescription = "Stop recording"

                // Start pulse animation
                val pulseAnimation = android.view.animation.AnimationUtils.loadAnimation(
                    requireContext(),
                    R.anim.pulse_recording
                )
                btnVoiceInput.startAnimation(pulseAnimation)
            }
            VoiceButtonState.PROCESSING -> {
                // Disabled microphone icon while processing
                btnVoiceInput.isSelected = false
                btnVoiceInput.setImageResource(R.drawable.btn_voice_states)
                btnVoiceInput.clearAnimation()
                btnVoiceInput.isEnabled = false
                btnVoiceInput.alpha = 0.5f
                btnVoiceInput.contentDescription = "Processing"
            }
        }
    }

    /**
     * Handle permission request result
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == RECORD_AUDIO_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, start recording
                Log.i(TAG, "Microphone permission granted")
                startVoiceRecording()
            } else {
                // Permission denied
                Log.w(TAG, "Microphone permission denied")
                Toast.makeText(
                    context,
                    "Microphone permission is required for voice input",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}

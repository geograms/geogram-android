package offgrid.geogram.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import offgrid.geogram.R
import offgrid.geogram.ai.models.ChatMessage
import offgrid.geogram.ai.models.MessageType
import java.text.SimpleDateFormat
import java.util.*

/**
 * Adapter for displaying chat messages in a RecyclerView.
 */
class ChatMessageAdapter : ListAdapter<ChatMessage, ChatMessageAdapter.MessageViewHolder>(MessageDiffCallback()) {

    /**
     * Force rebind of a specific item by position
     */
    fun forceRebindItem(position: Int) {
        Log.d("ChatAdapter", "forceRebindItem called: position=$position, itemCount=$itemCount")
        if (position >= 0 && position < itemCount) {
            notifyItemChanged(position)
            Log.d("ChatAdapter", "Called notifyItemChanged($position)")
        } else {
            Log.e("ChatAdapter", "Cannot rebind: position $position is out of bounds (itemCount=$itemCount)")
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ai_chat_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onViewRecycled(holder: MessageViewHolder) {
        super.onViewRecycled(holder)
        holder.cleanup()
    }

    /**
     * ViewHolder for chat messages.
     */
    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageContainer: LinearLayout = itemView.findViewById(R.id.message_container)
        private val avatar: ImageView = itemView.findViewById(R.id.iv_avatar)
        private val messageText: TextView = itemView.findViewById(R.id.tv_message_text)
        private val timestamp: TextView = itemView.findViewById(R.id.tv_timestamp)

        private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        // Animation for thinking dots
        private val handler = Handler(Looper.getMainLooper())
        private var animationRunnable: Runnable? = null
        private var dotAnimationStep = 0
        private var currentMessageId: String? = null  // Track which message is being displayed

        fun bind(message: ChatMessage) {
            // Stop any previous animation if message changed
            if (currentMessageId != message.id) {
                stopDotAnimation()
                currentMessageId = message.id
            }

            // Debug logging
            Log.d("ChatAdapter", "Binding message: type=${message.type}, content=${message.content.take(30)}, id=${message.id}, isStreaming=${message.isStreaming}")

            // Ensure container is visible
            messageContainer.visibility = View.VISIBLE
            itemView.visibility = View.VISIBLE

            // Set up long-press to copy message text
            messageContainer.setOnLongClickListener {
                copyMessageToClipboard(message.content)
                true
            }

            // Set message text
            if (message.isStreaming) {
                if (message.content.contains("Thinking")) {
                    // Start animated dots for thinking indicator
                    Log.d("ChatAdapter", "Starting dot animation for 'Thinking' message")
                    startDotAnimation()
                } else {
                    // Stop animation if it was running
                    stopDotAnimation()
                    // Show content as-is (streaming text)
                    Log.d("ChatAdapter", "Setting streaming content: ${message.content}")
                    messageText.text = message.content
                }
                // Hide timestamp while streaming
                timestamp.visibility = View.GONE
            } else {
                // Stop animation for completed messages
                stopDotAnimation()
                messageText.text = message.content
                // Show timestamp only when streaming is complete
                timestamp.visibility = View.VISIBLE
                timestamp.text = timeFormat.format(Date(message.timestamp))
            }

            // Configure layout based on message type
            when (message.type) {
                MessageType.USER -> {
                    Log.d("ChatAdapter", "Configuring USER message")
                    configureUserMessage()
                }
                MessageType.ASSISTANT -> {
                    Log.d("ChatAdapter", "Configuring ASSISTANT message")
                    configureAssistantMessage(message.isStreaming)
                }
                MessageType.SYSTEM -> {
                    Log.d("ChatAdapter", "Configuring SYSTEM message")
                    configureSystemMessage()
                }
            }
        }

        private fun startDotAnimation() {
            dotAnimationStep = 0
            animationRunnable = object : Runnable {
                override fun run() {
                    // Cycle through different dot patterns
                    val dotPattern = when (dotAnimationStep % 4) {
                        0 -> "●○○"  // First dot up
                        1 -> "○●○"  // Second dot up
                        2 -> "○○●"  // Third dot up
                        else -> "○○○"  // Brief pause
                    }
                    messageText.text = dotPattern
                    dotAnimationStep++
                    handler.postDelayed(this, 400) // Update every 400ms
                }
            }
            handler.post(animationRunnable!!)
        }

        private fun stopDotAnimation() {
            animationRunnable?.let {
                handler.removeCallbacks(it)
                animationRunnable = null
            }
        }

        fun cleanup() {
            stopDotAnimation()
            currentMessageId = null
        }

        private fun copyMessageToClipboard(text: String) {
            val context = itemView.context
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Message", text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Message copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        private fun configureUserMessage() {
            // Align to right
            val params = messageContainer.layoutParams as android.widget.FrameLayout.LayoutParams
            params.gravity = Gravity.END
            messageContainer.layoutParams = params

            // Show user bubble style
            messageContainer.setBackgroundResource(R.drawable.message_bubble_user)

            // Hide avatar
            avatar.visibility = View.GONE

            // User messages use white text
            val context = itemView.context
            messageText.setTextColor(ContextCompat.getColor(context, R.color.white))
            messageText.alpha = 1.0f

            // White timestamp for user messages
            timestamp.setTextColor(ContextCompat.getColor(context, R.color.white))
            timestamp.alpha = 0.7f

            // Align timestamp to the right for user messages
            val timestampParams = timestamp.layoutParams as LinearLayout.LayoutParams
            timestampParams.gravity = Gravity.END
            timestamp.layoutParams = timestampParams

            // Ensure visibility
            messageContainer.visibility = View.VISIBLE
            messageText.visibility = View.VISIBLE
        }

        private fun configureAssistantMessage(isStreaming: Boolean) {
            // Align to left
            val params = messageContainer.layoutParams as android.widget.FrameLayout.LayoutParams
            params.gravity = Gravity.START
            messageContainer.layoutParams = params

            // Show AI bubble style
            messageContainer.setBackgroundResource(R.drawable.message_bubble_ai)

            // Show avatar
            avatar.visibility = View.VISIBLE
            avatar.setImageResource(R.drawable.ic_robot)

            // AI messages use white color
            val context = itemView.context
            messageText.setTextColor(ContextCompat.getColor(context, R.color.white))

            // Align timestamp to the left for assistant messages
            val timestampParams = timestamp.layoutParams as LinearLayout.LayoutParams
            timestampParams.gravity = Gravity.START
            timestamp.layoutParams = timestampParams
            timestamp.setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
            timestamp.alpha = 1.0f

            // Add streaming indicator if needed
            if (isStreaming) {
                messageText.alpha = 0.8f
            } else {
                messageText.alpha = 1.0f
            }
        }

        private fun configureSystemMessage() {
            // Center align
            val params = messageContainer.layoutParams as android.widget.FrameLayout.LayoutParams
            params.gravity = Gravity.CENTER
            messageContainer.layoutParams = params

            // System messages have different style
            messageContainer.setBackgroundResource(R.drawable.message_bubble_system)

            // Hide avatar
            avatar.visibility = View.GONE

            // System messages use muted color
            val context = itemView.context
            messageText.setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
            messageText.alpha = 0.7f

            // Center align timestamp for system messages
            val timestampParams = timestamp.layoutParams as LinearLayout.LayoutParams
            timestampParams.gravity = Gravity.CENTER
            timestamp.layoutParams = timestampParams
            timestamp.setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
            timestamp.alpha = 0.7f
        }
    }

    /**
     * DiffUtil callback for efficient list updates.
     */
    class MessageDiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem == newItem
        }
    }
}

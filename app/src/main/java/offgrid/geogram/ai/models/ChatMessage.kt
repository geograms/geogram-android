package offgrid.geogram.ai.models

import android.net.Uri
import com.cactus.ChatMessage as CactusChatMessage

/**
 * Represents a chat message in the AI conversation.
 *
 * @property id Unique identifier for the message
 * @property content The text content of the message
 * @property type The type of message (USER, ASSISTANT, SYSTEM)
 * @property timestamp When the message was created (milliseconds since epoch)
 * @property attachments Optional list of file URIs attached to the message
 * @property isStreaming Whether this message is currently being streamed (for ASSISTANT messages)
 */
data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val content: String,
    val type: MessageType,
    val timestamp: Long = System.currentTimeMillis(),
    val attachments: List<Uri> = emptyList(),
    val isStreaming: Boolean = false
) {
    /**
     * Convert to Cactus SDK ChatMessage format.
     */
    fun toCactusMessage(): CactusChatMessage {
        return CactusChatMessage(
            content = content,
            role = type.toRole()
        )
    }

    /**
     * Create a copy with updated content (useful for streaming).
     */
    fun withContent(newContent: String): ChatMessage {
        return copy(content = newContent)
    }

    /**
     * Create a copy marking streaming as complete.
     */
    fun completeStreaming(): ChatMessage {
        return copy(isStreaming = false)
    }

    companion object {
        /**
         * Create a user message.
         */
        fun user(content: String, attachments: List<Uri> = emptyList()): ChatMessage {
            return ChatMessage(
                content = content,
                type = MessageType.USER,
                attachments = attachments
            )
        }

        /**
         * Create an assistant message.
         */
        fun assistant(content: String, isStreaming: Boolean = false): ChatMessage {
            return ChatMessage(
                content = content,
                type = MessageType.ASSISTANT,
                isStreaming = isStreaming
            )
        }

        /**
         * Create a system message.
         */
        fun system(content: String): ChatMessage {
            return ChatMessage(
                content = content,
                type = MessageType.SYSTEM
            )
        }

        /**
         * Convert from Cactus SDK ChatMessage.
         */
        fun fromCactusMessage(msg: CactusChatMessage): ChatMessage {
            return ChatMessage(
                content = msg.content,
                type = MessageType.fromRole(msg.role)
            )
        }
    }
}

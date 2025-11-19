package offgrid.geogram.ai.models

/**
 * Represents the type of message in a chat conversation.
 */
enum class MessageType {
    /** Message from the user */
    USER,

    /** Message from the AI assistant */
    ASSISTANT,

    /** System message (e.g., errors, status updates) */
    SYSTEM;

    /**
     * Convert to Cactus SDK role string.
     */
    fun toRole(): String = when (this) {
        USER -> "user"
        ASSISTANT -> "assistant"
        SYSTEM -> "system"
    }

    companion object {
        /**
         * Create MessageType from Cactus SDK role string.
         */
        fun fromRole(role: String): MessageType = when (role.lowercase()) {
            "user" -> USER
            "assistant" -> ASSISTANT
            "system" -> SYSTEM
            else -> SYSTEM
        }
    }
}

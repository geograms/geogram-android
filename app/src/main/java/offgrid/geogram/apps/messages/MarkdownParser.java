package offgrid.geogram.apps.messages;

import java.util.ArrayList;
import java.util.List;

/**
 * Parse markdown-formatted conversations from server
 * Format:
 * >metadata -- AUTHOR
 * message content line 1
 * message content line 2
 * (blank line)
 * >next metadata
 * next message content
 */
public class MarkdownParser {

    /**
     * Parse markdown conversation into list of messages
     *
     * @param markdown Markdown content from server
     * @param currentUser Current user's callsign
     * @return List of ConversationMessage objects
     */
    public static List<ConversationMessage> parseConversation(String markdown, String currentUser) {
        List<ConversationMessage> messages = new ArrayList<>();

        if (markdown == null || markdown.isEmpty()) {
            return messages;
        }

        String[] lines = markdown.split("\\r?\\n");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            // Look for metadata line (starts with >)
            if (line.startsWith(">")) {
                String meta = line.substring(1).trim(); // Remove '>' prefix

                // Collect content lines until blank line or next metadata
                StringBuilder content = new StringBuilder();
                i++; // Move to next line
                while (i < lines.length && !lines[i].trim().isEmpty() && !lines[i].startsWith(">")) {
                    if (content.length() > 0) {
                        content.append("\n");
                    }
                    content.append(lines[i]);
                    i++;
                }
                // Back up one line since outer loop will increment
                i--;

                // Create message
                ConversationMessage message = new ConversationMessage(meta, content.toString(), currentUser);
                messages.add(message);
            }
        }

        return messages;
    }

    /**
     * Format a message for sending to server
     *
     * @param author Message author
     * @param content Message content
     * @return Markdown-formatted message
     */
    public static String formatMessage(String author, String content) {
        // Format: >metadata -- AUTHOR\ncontent\n
        StringBuilder sb = new StringBuilder();
        sb.append(">-- ").append(author).append("\n");
        sb.append(content).append("\n\n");
        return sb.toString();
    }
}

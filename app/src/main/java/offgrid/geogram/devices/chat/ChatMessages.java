package offgrid.geogram.devices.chat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

import offgrid.geogram.core.Log;

public class ChatMessages {
    long messageTimeEarliest = -1;
    ArrayList<ChatMessage> messages = new ArrayList<>();

    public static ChatMessages fromJson(File file) {
        if (file == null || !file.exists()) {
            Log.e("ChatMessages", "File is null or does not exist");
            return null;
        }

        Gson gson = new Gson();
        try (FileReader reader = new FileReader(file)) {
            ChatMessages chatMessages = gson.fromJson(reader, ChatMessages.class);
            if (chatMessages == null) {
                return null;
            }
            return chatMessages;
        } catch (IOException e) {
            Log.e("ChatMessages", "Error loading chat messages from file: " + e.getMessage());
            return null;
        }
    }

    public void add(ChatMessage message) {
        if (messageTimeEarliest == -1 || message.timestamp < messageTimeEarliest) {
            messageTimeEarliest = message.timestamp;
        }
        messages.add(message);
    }

    public void saveToFile(File file) {
        if (file == null) {
            Log.e("ChatMessages", "File is null");
            return;
        }

        Gson gson = new GsonBuilder()
                .setPrettyPrinting() // Ensures formatted JSON
                .disableHtmlEscaping() // Prevents unnecessary escaping
                .create();

        String json = gson.toJson(this);

        try (FileWriter writer = new FileWriter(file)) {
            writer.write(json);
        } catch (IOException e) {
            Log.e("ChatMessages", "Error saving chat messages to file: " + e.getMessage());
        }
    }

    public ChatMessage getMessage(long timestamp, String data) {
        for (ChatMessage chatMessage : messages) {
            if (chatMessage.timestamp == timestamp && chatMessage.message.equals(data)) {
                return chatMessage;
            }
        }
        return null;
    }
}

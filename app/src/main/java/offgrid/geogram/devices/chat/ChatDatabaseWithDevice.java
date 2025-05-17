package offgrid.geogram.devices.chat;

import android.content.Context;

import java.io.File;
import java.util.HashMap;

import offgrid.geogram.core.Log;
import offgrid.geogram.database.BioDatabase;

public class ChatDatabaseWithDevice {

    public static final String TAG = "DeviceIdChat";
    public static final String fileMessages = "messages.json";

    private static ChatDatabaseWithDevice instance;
    private static Context context;

    // Chat messages should be in memory
    private final HashMap<String, ChatMessages> messages = new HashMap<>();

    private ChatDatabaseWithDevice(Context context) {
        ChatDatabaseWithDevice.context = context.getApplicationContext(); // Store application context
    }

    public static synchronized ChatDatabaseWithDevice getInstance(Context context) {
        if (instance == null) {
            instance = new ChatDatabaseWithDevice(context);
        }
        return instance;
    }

    public void addMessage(String deviceId, ChatMessage message) {
        if (deviceId == null) {
            Log.e(TAG, "Device Id is null");
            return;
        }
        if (!messages.containsKey(deviceId)) {
            messages.put(deviceId, new ChatMessages());
        }
        ChatMessages messageBox = messages.get(deviceId);
        if (messageBox == null) {
            Log.e(TAG, "Chat messageBox is null");
            return;
        }
        if (message == null) {
            Log.e(TAG, "Message is null");
            return;
        }
        // add the message itself
        messageBox.add(message);
        saveToDisk(deviceId, messageBox);
    }

    public void saveToDisk(String deviceId, ChatMessages messageBox) {
        if (deviceId == null) {
            Log.e(TAG, "Device Id is null");
            return;
        }
        if (messageBox == null) {
            Log.e(TAG, "Chat messageBox is null");
            return;
        }
        // get the folder
        File folder = BioDatabase.getFolderDeviceId(deviceId, context);
        if (folder == null) {
            Log.e(TAG, "Folder is null");
            return;
        }
        File file = new File(folder, fileMessages);
        try {
            messageBox.saveToFile(file);
            Log.i(TAG, "Saved data to file: " + file.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Failed to save data to file: " + file.getAbsolutePath() + " " + e.getMessage());
        }
    }

    public ChatMessages getMessages(String deviceId) {
        if (deviceId == null) {
            Log.e(TAG, "Device Id is null");
            return null;
        }
        // we have it in memory, provide what we have
        if(this.messages.containsKey(deviceId)){
            return this.messages.get(deviceId);
        }
        // we need to load it from disk?
        File folder = BioDatabase.getFolderDeviceId(deviceId, context);
        if (folder == null) {
            Log.e(TAG, "Folder is null");
            // does not exist, provide an empty one
            ChatMessages box = new ChatMessages();
            messages.put(deviceId, box);
            return box;
        }
        File file = new File(folder, fileMessages);
        if (!file.exists()) {
            Log.e(TAG, "File does not exist: " + file.getAbsolutePath());
            // does not exist, provide an empty one
            ChatMessages box = new ChatMessages();
            messages.put(deviceId, box);
            return box;
        }

        // file exists, load it from disk
        ChatMessages messageBox = ChatMessages.fromJson(file);
        if(messageBox == null){
            Log.e(TAG, "Failed to load chat messages from file: " + file.getAbsolutePath());
            // does not exist, provide an empty one
            ChatMessages box = new ChatMessages();
            messages.put(deviceId, box);
            return box;
        }

        return messageBox;
    }
}

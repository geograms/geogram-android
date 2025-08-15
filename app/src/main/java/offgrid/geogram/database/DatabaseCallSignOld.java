package offgrid.geogram.database;

import android.content.Context;

import java.io.File;
import java.util.HashMap;

import offgrid.geogram.apps.chat.ChatMessage;
import offgrid.geogram.core.Log;
import offgrid.geogram.util.JsonUtils;

/**
 * Provides a folder of messages being received or sent
 */
public class DatabaseCallSignOld {

    public final static String TAG = "DatabaseMessageStream";
    public final static String FOLDER_NAME = "callsigns";
    public final static String FILE_NAME = "data-callsign.json";

    // contacts that were made
    private final HashMap<String, CallSign> list = new HashMap<>();

    private final Context context;

    public DatabaseCallSignOld(Context context) {
        this.context = context;
    }

    /**
     * Gets the base folder for storage.
     *
     * @param context The application context.
     * @return The folder for storing data.
     */
    private File getFolderBase(Context context) {
        File folder = new File(context.getFilesDir(), FOLDER_NAME);
        if (!folder.exists()) {
            if (!folder.mkdirs()) {
                Log.e(TAG, "Failed to create folder: " + FOLDER_NAME);
                return null;
            }
        }
        return folder;
    }

    public void add(CallSign callSign) {
        if (callSign == null) {
            Log.e(TAG, "callSign is null");
            return;
        }
        list.put(callSign.getCallsign(), callSign);
    }

    public static DatabaseCallSignOld loadFromDisk(Context appContext) {
        File folder = new File(appContext.getFilesDir(), FOLDER_NAME);
        File file = new File(folder, FILE_NAME);

        if (file.exists()) {
            DatabaseCallSignOld instance = JsonUtils.parseJson(file, DatabaseCallSignOld.class);
            if (instance != null) {
                Log.i(TAG, "Loaded DatabaseCallSign from: " + file.getAbsolutePath());
                return instance;
            } else {
                Log.e(TAG, "Failed to parse DatabaseCallSign JSON: " + file.getAbsolutePath());
            }
        } else {
            Log.i(TAG, "No existing file. Creating new in-memory DatabaseCallSign.");
        }

        return new DatabaseCallSignOld(appContext);
    }


    /**
     * Saves a discovered thing to the database.
     */
    private void saveToDisk() {
        File folderDevice = getFolderBase(context);
        if (folderDevice == null) {
            return;
        }
        File file = new File(folderDevice, FILE_NAME);

        try {
            String text = JsonUtils.convertToJsonText(this);
            JsonUtils.writeJsonToFile(text, file);
        } catch (Exception e) {
            Log.e(TAG, "Failed to save data to file: " + file.getAbsolutePath() + " " + e.getMessage());
        }

        Log.i(TAG, "Saved data to file: " + file.getAbsolutePath());
    }


    public CallSign getCallSign(String callsign) {
        if (callsign == null) {
            Log.e(TAG, "callsign is null");
            return null;
        }
        return list.getOrDefault(callsign, null);
    }


    /**
     * Check if there is the need to update the database
     * @param chatMessage
     */
    public void update(ChatMessage chatMessage) {
        String callSignName = chatMessage.getAuthorId();
        if (callSignName == null) {
            Log.e(TAG, "callSignName is null");
            return;
        }
        CallSign callSign = getCallSign(callSignName);
        if (callSign == null) {
            callSign = new CallSign();
            // fill up all the needed fields
            callSign.setSeenFirstTime(System.currentTimeMillis());
            callSign.setSeenLastTime(System.currentTimeMillis());
            callSign.setCallsign(callSignName);
            add(callSign);
        }else {
            // update the time stamp
            callSign.setSeenLastTime(System.currentTimeMillis());
            add(callSign);
        }
        this.saveToDisk();
    }
}

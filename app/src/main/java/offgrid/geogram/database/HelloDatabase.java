package offgrid.geogram.database;

import android.content.Context;

import java.io.File;
import java.util.HashMap;

import offgrid.geogram.bluetooth.broadcast.BroadcastSender;
import offgrid.geogram.core.Log;
import offgrid.geogram.util.JsonUtils;
import offgrid.geogram.wifi.messages.MessageHello_v1;

/**
 * Handles all data related to hellos that were sent
 * Each hello is mapped to an NPUB
 */
public class HelloDatabase {

    public static final String TAG = "HelloDatabase";
    public static final String FOLDER_NAME = "hello";
    public static final String FILE_NAME = "hello.json";

    // profiles that said hello so far. The string is the NPUB being used
    private static final HashMap<String, MessageHello_v1> hellos = new HashMap<>();


    /**
     * Gets the base folder for storage.
     *
     * @param context The application context.
     * @return The folder for storing data.
     */
    private static File getFolder(Context context) {
        File folder = new File(context.getFilesDir(), FOLDER_NAME);
        if (!folder.exists()) {
            if (!folder.mkdirs()) {
                Log.e(TAG, "Failed to create folder: " + FOLDER_NAME);
                return null;
            }
        }
        return folder;
    }

    /**
     * Gets the folder associated with a specific device ID.
     *
     * @param npub The NPUB identification
     * @param context  The application context.
     * @return The folder for the specified NPUB.
     */
    public static File getFolderId(String npub, Context context) {
        if (npub == null) {
            Log.e(TAG, "Device ID is null");
            return null;
        }

        if (npub.length() < 3) {
            Log.e(TAG, "ID is too short: " + npub);
            return null;
        }
        String id = npub.substring(0, 3); // First three characters
        File folderBase = getFolder(context);
        if (folderBase == null) {
            Log.e(TAG, "Base folder could not be created");
            return null;
        }
        File folderSection = new File(folderBase, id);
        File folderDevice = new File(folderSection, npub);

        if (!folderDevice.exists()) {
            if (!folderDevice.mkdirs()) {
                Log.e(TAG, "Failed to create folder: " + folderDevice.getAbsolutePath());
                return null;
            }
        }
        return folderDevice;
    }


    /**
     * Saves a discovered thing to the database.
     *
     * @param hello     The hello to save or merge.
     * @param appContext The application context.
     */
    private static void saveToDisk(MessageHello_v1 hello, Context appContext) {
        String UID = hello.getUID();
        if (UID == null) {
            Log.e(TAG, "UID is null");
            return;
        }
        File folderDevice = getFolderId(UID, appContext);
        if (folderDevice == null) {
            return;
        }
        File file = new File(folderDevice, FILE_NAME);

        try {
            String text = JsonUtils.convertToJsonText(hello);
            JsonUtils.writeJsonToFile(text, file);
        } catch (Exception e) {
            Log.e(TAG, "Failed to save data to file: " + file.getAbsolutePath() + " " + e.getMessage());
        }

        Log.i(TAG, "Saved data to file: " + file.getAbsolutePath());
    }


    public static MessageHello_v1 getFromMemory(String UID) {
        if (UID == null) {
            Log.e(TAG, "UID is null");
            return null;
        }
        return hellos.getOrDefault(UID, null);
    }

    /**
     * Retrieves a beacon by its device ID.
     *
     * @param UID   The UID of the hello.
     * @param appContext The application context.
     * @return The hello if found, or null if not.
     */
    public static MessageHello_v1 getFromMemoryOrDisk(String UID, Context appContext) {
        if (UID == null) {
            Log.e(TAG, "UID is null");
            return null;
        }

        // if the device is in memory, add it from there
        if(hellos.containsKey(UID)){
            return hellos.get(UID);
        }

        // seems we really have to read it from disk
        File folderDevice = getFolderId(UID, appContext);
        if (folderDevice == null || !folderDevice.exists()) {
            Log.e(TAG, "Folder does not exist for " + UID);
            return null;
        }

        File file = new File(folderDevice, FILE_NAME);
        if (!file.exists()) {
            Log.e(TAG, "File does not exist: " + file.getAbsolutePath());
            return null;
        }
        Log.i(TAG, "Reading archived hello: " + UID);
        MessageHello_v1 hello = JsonUtils.parseJson(file, MessageHello_v1.class);
        if (hello == null) {
            Log.e(TAG, "Failed to load hello from file: " + file.getAbsolutePath());
            return null;
        }
        // save it in memory for future runs
        hellos.put(UID, hello);
        return hello;
    }

    public static void save(MessageHello_v1 hello, Context context) {
        if(hello == null){
            Log.e(TAG, "Profile is null");
            return;
        }

        String UID = hello.getUID();

        if (UID == null) {
            Log.e(TAG, "UID is null");
        }

        // put the hello on our database
        hellos.put(UID, hello);
        // also save this to disk for later
        saveToDisk(hello, context);
    }




}

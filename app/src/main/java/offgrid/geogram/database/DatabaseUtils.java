package offgrid.geogram.database;

import android.content.Context;

import java.io.File;

import offgrid.geogram.core.Log;

public class DatabaseUtils {

    public final static String TAG = "DatabaseUtils";


    /**
     * Gets the folder associated with a specific device ID.
     *
     * @param FOLDER_NAME The folder name identification
     * @param context  The application context.
     * @return The folder for the specified NPUB.
     */
//    public static File getFolderId(String FOLDER_BASE, String FOLDER_NAME, Context context) {
//        if (FOLDER_NAME == null) {
//            Log.e(TAG, "Device ID is null");
//            return null;
//        }
//
//        if (FOLDER_NAME.length() < 3) {
//            Log.e(TAG, "ID is too short: " + FOLDER_NAME);
//            return null;
//        }
//        String id = FOLDER_NAME.substring(0, 3); // First three characters
//        File folderBase = getFolder(context);
//        if (folderBase == null) {
//            Log.e(TAG, "Base folder could not be created");
//            return null;
//        }
//        File folderSection = new File(folderBase, id);
//        File folderDevice = new File(folderSection, FOLDER_NAME);
//
//        if (!folderDevice.exists()) {
//            if (!folderDevice.mkdirs()) {
//                Log.e(TAG, "Failed to create folder: " + folderDevice.getAbsolutePath());
//                return null;
//            }
//        }
//        return folderDevice;
//    }


    /**
     * Gets the base folder for storage.
     *
     * @param context The application context.
     * @return The folder for storing data.
     */
    public static File getFolder(String FOLDER_NAME, Context context) {
        File folder = new File(context.getFilesDir(), FOLDER_NAME);
        if (!folder.exists()) {
            if (!folder.mkdirs()) {
                Log.e(TAG, "Failed to create folder: " + FOLDER_NAME);
                return null;
            }
        }
        return folder;
    }


}

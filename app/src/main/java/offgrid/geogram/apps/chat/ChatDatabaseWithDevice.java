package offgrid.geogram.apps.chat;

import android.content.Context;
// Removed legacy imports - BioDatabase was part of old code

import java.io.File;

/**
 * DEPRECATED: This class used legacy BioDatabase which depended on Google Play Services
 * Kept for compatibility but functionality is stubbed out
 */
@Deprecated
public class ChatDatabaseWithDevice {

    public static File getFolder(String deviceId, Context context) {
        // Stubbed - BioDatabase removed (Google Play Services dependency)
        return null;
    }

    public static void save(String deviceId, String message, Context context) {
        // Stubbed - BioDatabase removed (Google Play Services dependency)
    }

    public static String load(String deviceId, Context context) {
        // Stubbed - BioDatabase removed (Google Play Services dependency)
        return "";
    }
}

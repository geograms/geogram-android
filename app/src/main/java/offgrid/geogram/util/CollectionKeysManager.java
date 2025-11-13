package offgrid.geogram.util;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.BufferedReader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Manages collection npub/nsec key pairs stored in config.json
 */
public class CollectionKeysManager {

    private static final String CONFIG_FILE = "collection_keys_config.json";

    /**
     * Stores a collection's npub/nsec pair
     */
    public static void storeKeys(Context context, String npub, String nsec) {
        try {
            File configFile = new File(context.getFilesDir(), CONFIG_FILE);
            JSONObject config;

            // Load existing config or create new
            if (configFile.exists()) {
                config = loadConfig(configFile);
            } else {
                config = new JSONObject();
            }

            // Store the key pair
            JSONObject keyPair = new JSONObject();
            keyPair.put("npub", npub);
            keyPair.put("nsec", nsec);
            keyPair.put("created", System.currentTimeMillis());

            config.put(npub, keyPair);

            // Save config
            saveConfig(configFile, config);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Retrieves the nsec for a given npub
     */
    public static String getNsec(Context context, String npub) {
        try {
            File configFile = new File(context.getFilesDir(), CONFIG_FILE);
            if (!configFile.exists()) {
                return null;
            }

            JSONObject config = loadConfig(configFile);
            if (config.has(npub)) {
                JSONObject keyPair = config.getJSONObject(npub);
                return keyPair.getString("nsec");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Checks if we own this collection (have the nsec)
     */
    public static boolean isOwnedCollection(Context context, String npub) {
        return getNsec(context, npub) != null;
    }

    /**
     * Gets all owned collections (npubs we have nsec for)
     */
    public static Map<String, String> getAllOwnedCollections(Context context) {
        Map<String, String> owned = new HashMap<>();

        try {
            File configFile = new File(context.getFilesDir(), CONFIG_FILE);
            if (!configFile.exists()) {
                return owned;
            }

            JSONObject config = loadConfig(configFile);
            Iterator<String> keys = config.keys();

            while (keys.hasNext()) {
                String npub = keys.next();
                JSONObject keyPair = config.getJSONObject(npub);
                String nsec = keyPair.getString("nsec");
                owned.put(npub, nsec);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return owned;
    }

    private static JSONObject loadConfig(File configFile) throws Exception {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line);
            }
        }
        return new JSONObject(content.toString());
    }

    private static void saveConfig(File configFile, JSONObject config) throws Exception {
        try (FileWriter writer = new FileWriter(configFile)) {
            writer.write(config.toString(2)); // Pretty print with indent 2
        }
    }
}

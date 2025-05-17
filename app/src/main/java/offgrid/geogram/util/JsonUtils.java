package offgrid.geogram.util;

import com.google.gson.Gson;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import offgrid.geogram.core.Log;

public class JsonUtils {

    private static String TAG = "JsonUtils";

    public static String convertToJsonText(Object object) {
        if (object == null) {
            return null;
        }
        com.google.gson.Gson gson = new com.google.gson.Gson();
        return gson.toJson(object);
    }


    public static <T> T parseJson(File jsonFile, Class<T> objectClass) {
        if (jsonFile == null || !jsonFile.exists() || !jsonFile.isFile()) {
            return null;
        }
        com.google.gson.Gson gson = new com.google.gson.Gson();
        try (FileReader reader = new FileReader(jsonFile)) {
            return gson.fromJson(reader, objectClass);
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
            return null;
        }
    }

    public static <T> T parseJson(String jsonString, Class<T> objectClass) {
        if (jsonString == null || jsonString.isEmpty()) {
            return null;
        }
        com.google.gson.Gson gson = new com.google.gson.Gson();
        return gson.fromJson(jsonString, objectClass);
    }

    public static boolean writeJsonToFile(Object object, File file) {
        if (object == null || file == null) {
            Log.e("writeJsonToFile", "Object or file is null.");
            return false;
        }
        Gson gson = new Gson();
        try (FileWriter writer = new FileWriter(file)) {
            // Convert the object to JSON and write to the file
            gson.toJson(object, writer);
            Log.i("writeJsonToFile", "JSON written successfully to: " + file.getAbsolutePath());
            return true;
        } catch (IOException e) {
            Log.e("writeJsonToFile", "Error writing JSON to file: " + e.getMessage());
            return false;
        }
    }
}

package offgrid.geogram.settings;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Random;

import offgrid.geogram.old.old.old.GenerateDeviceId;
import offgrid.geogram.util.ASCII;
import offgrid.geogram.util.nostr.Identity;
import offgrid.geogram.util.NicknameGenerator;

public class SettingsLoader {

    private static final String SETTINGS_FILE_NAME = "settings.json";

    // List of permitted colors
    private static final String[] PERMITTED_COLORS = {
            "Black", "Blue", "Green", "Cyan", "Red", "Magenta", "Pink", "Brown",
            "Dark Gray", "Light Blue", "Light Green", "Light Cyan", "Light Red", "Yellow", "White"
    };

    public static SettingsUser loadSettings(Context context) {
        Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();
        File settingsFile = new File(context.getFilesDir(), SETTINGS_FILE_NAME);

        Log.i("SettingsLoader", "Settings file location: " + settingsFile.getAbsolutePath());

        if (settingsFile.exists()) {
            try (FileReader reader = new FileReader(settingsFile)) {
                SettingsUser settings = gson.fromJson(reader, SettingsUser.class);
                if(settings.getNickname() == null || settings.getNickname().isEmpty()){
                    throw new IllegalArgumentException("Nickname cannot be empty");
                }
                Log.i("SettingsLoader", "Settings loaded successfully.");
                return settings;
            } catch (IOException e) {
                Log.e("SettingsLoader", "Failed to read settings file, creating default settings.", e);
                return createDefaultSettings(context);
            }
        } else {
            Log.i("SettingsLoader", "Settings file not found, creating default settings.");
            return createDefaultSettings(context);
        }
    }

    public static SettingsUser createDefaultSettings(Context context) {

            // Generate identity keys
            Identity user = Identity.generateRandomIdentity();
            String nsec = user.getPrivateKey().toBech32String();
            String npub = user.getPublicKey().toBech32String();

            // Create default settings
            SettingsUser defaultSettings = new SettingsUser();
            defaultSettings.setNickname(NicknameGenerator.generateNickname());
            defaultSettings.setIntro(NicknameGenerator.generateIntro());
            // generate the emoticon
            defaultSettings.setEmoticon(ASCII.getRandomOneliner());
            defaultSettings.setInvisibleMode(false);
            defaultSettings.setNsec(nsec);
            defaultSettings.setNpub(npub);
            defaultSettings.setBeaconType("person");
            defaultSettings.setIdGroup(generateRandomNumber());
            // generate the device ID
            String deviceId = GenerateDeviceId.generate(context);
            defaultSettings.setIdDevice(deviceId);
            defaultSettings.setPreferredColor(selectRandomColor()); // Assign a random color
            defaultSettings.setBeaconNickname(generateRandomBeaconNickname()); // Default beacon nickname
        // Save default settings
        if(context != null) {
            saveSettings(context, defaultSettings);
        }
        return defaultSettings;
    }

    public static String generateRandomBeaconNickname() {
        StringBuilder nickname = new StringBuilder("Beacon");
        Random random = new Random();
        for (int i = 0; i < 4; i++) {
            char randomChar = (char) ('A' + random.nextInt(26)); // Generate random uppercase letter
            nickname.append(randomChar);
        }
        return nickname.toString();
    }

    public static String generateRandomNumber() {
        Random random = new Random();
        int number = random.nextInt(100000); // Generates a number between 0 and 99999
        return String.format("%05d", number); // Pads the number to ensure it is 5 digits
    }

    public static String selectRandomColor() {
        Random random = new Random();
        return PERMITTED_COLORS[random.nextInt(PERMITTED_COLORS.length)];
    }

    public static void deleteSettings(Context context) {
        File settingsFile = new File(context.getFilesDir(), SETTINGS_FILE_NAME);
        if (!settingsFile.exists()) {
            Log.e("SettingsLoader", "Settings file not found, cannot delete.");
            return;
        }
        if (settingsFile.delete()) {
            Log.i("SettingsLoader", "Settings file deleted successfully.");
        } else {
            Log.e("SettingsLoader", "Failed to delete settings file.");
        }
    }

    public static void saveSettings(Context context, SettingsUser settings) {
        Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();
        File settingsFile = new File(context.getFilesDir(), SETTINGS_FILE_NAME);

        try (FileWriter writer = new FileWriter(settingsFile)) {
            gson.toJson(settings, writer);
            writer.flush(); // Ensure data is written to disk
            Log.i("SettingsLoader", "Settings saved successfully to: " + settingsFile.getAbsolutePath());
        } catch (IOException e) {
            Log.e("SettingsLoader", "Error saving settings file", e);
        }
    }
}

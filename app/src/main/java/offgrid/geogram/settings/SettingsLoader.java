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

// Removed (legacy Google Play Services code) - import offgrid.geogram.old.old.old.GenerateDeviceId;
import offgrid.geogram.util.ASCII;
import offgrid.geogram.util.NicknameGenerator;

/**
 * @deprecated Use ConfigManager instead. This class is kept for backward compatibility.
 */
@Deprecated
public class SettingsLoader {

    private static final String SETTINGS_FILE_NAME = "settings.json";

    // List of permitted colors
    private static final String[] PERMITTED_COLORS = {
            "Black", "Blue", "Green", "Cyan", "Red", "Magenta", "Pink", "Brown",
            "Dark Gray", "Light Blue", "Light Green", "Light Cyan", "Light Red", "Yellow", "White"
    };

    public static SettingsUser loadSettings(Context context) {
        // Use ConfigManager for new implementation
        ConfigManager configManager = ConfigManager.getInstance(context);
        configManager.initialize();
        return configManager.getSettingsUser();
    }

    public static SettingsUser createDefaultSettings(Context context) {

            // Generate complete identity (npub, nsec, and callsign)
            IdentityHelper.NostrIdentity identity = IdentityHelper.generateNewIdentity();

            // Create default settings
            SettingsUser defaultSettings = new SettingsUser();
            defaultSettings.setNickname(NicknameGenerator.generateNickname());
            defaultSettings.setIntro(NicknameGenerator.generateIntro());
            // generate the emoticon
            defaultSettings.setEmoticon(ASCII.getRandomOneliner());
            defaultSettings.setInvisibleMode(false);
            defaultSettings.setNsec(identity.nsec);
            defaultSettings.setNpub(identity.npub);
            defaultSettings.setCallsign(identity.callsign);
            defaultSettings.setBeaconType("person");
            defaultSettings.setIdGroup(generateRandomNumber());
            // generate the device ID (6 digits)
            // Removed (legacy) - GenerateDeviceId was part of old code
            String deviceId = generateRandomDeviceId();
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

    public static String generateRandomDeviceId() {
        Random random = new Random();
        int number = random.nextInt(900000) + 100000; // Generates a number between 100000 and 999999
        return String.valueOf(number); // Returns 6-digit number as string
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
        // Use ConfigManager for new implementation
        ConfigManager configManager = ConfigManager.getInstance(context);
        configManager.updateConfig(config -> {
            AppConfig newConfig = AppConfig.fromSettingsUser(settings);
            // Copy all fields from newConfig to config
            if (newConfig.getCallsign() != null) config.setCallsign(newConfig.getCallsign());
            if (newConfig.getNickname() != null) config.setNickname(newConfig.getNickname());
            if (newConfig.getIntro() != null) config.setIntro(newConfig.getIntro());
            if (newConfig.getPreferredColor() != null) config.setPreferredColor(newConfig.getPreferredColor());
            if (newConfig.getEmoticon() != null) config.setEmoticon(newConfig.getEmoticon());
            if (newConfig.getNpub() != null) config.setNpub(newConfig.getNpub());
            if (newConfig.getNsec() != null) config.setNsec(newConfig.getNsec());
            if (newConfig.getBeaconNickname() != null) config.setBeaconNickname(newConfig.getBeaconNickname());
            if (newConfig.getBeaconType() != null) config.setBeaconType(newConfig.getBeaconType());
            if (newConfig.getIdGroup() != null) config.setIdGroup(newConfig.getIdGroup());
            config.setInvisibleMode(newConfig.isInvisibleMode());
            config.setChatCommunicationMode(newConfig.getChatCommunicationMode());
            config.setChatRadiusKm(newConfig.getChatRadiusKm());
            config.setHttpApiEnabled(newConfig.isHttpApiEnabled());
        });
        Log.i("SettingsLoader", "Settings saved successfully via ConfigManager");
    }
}

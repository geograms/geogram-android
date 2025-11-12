package offgrid.geogram.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Centralized configuration manager for the Geogram Android app.
 *
 * This class manages all user-defined settings and app configuration through a single
 * config.json file. It provides:
 * - Persistent storage of all settings in JSON format
 * - Thread-safe singleton access
 * - Backward compatibility with SharedPreferences
 * - Migration from old settings format
 * - Default values when config doesn't exist
 */
public class ConfigManager {

    private static final String TAG = "ConfigManager";
    private static final String CONFIG_FILE_NAME = "config.json";
    private static final String OLD_SETTINGS_FILE_NAME = "settings.json";

    // SharedPreferences names for migration
    private static final String RELAY_PREFS_NAME = "relay_settings";
    private static final String APP_PREFS_NAME = "GeogramPrefs";

    private static ConfigManager instance;
    private final Context context;
    private AppConfig config;
    private final Object lock = new Object();
    private final Gson gson;

    /**
     * Private constructor for singleton pattern.
     */
    private ConfigManager(Context context) {
        this.context = context.getApplicationContext();
        this.gson = new GsonBuilder()
                .excludeFieldsWithoutExposeAnnotation()
                .setPrettyPrinting()
                .create();
    }

    /**
     * Get the singleton instance of ConfigManager.
     *
     * @param context Application context
     * @return ConfigManager instance
     */
    public static synchronized ConfigManager getInstance(Context context) {
        if (instance == null) {
            instance = new ConfigManager(context);
        }
        return instance;
    }

    /**
     * Initialize the configuration. This should be called early in the app lifecycle.
     * Loads config from file, or migrates from old format, or creates default config.
     */
    public void initialize() {
        synchronized (lock) {
            File configFile = new File(context.getFilesDir(), CONFIG_FILE_NAME);

            if (configFile.exists()) {
                // Load existing config
                loadConfig();
            } else {
                // Check for old settings format and migrate
                if (needsMigration()) {
                    Log.i(TAG, "Migrating from old settings format to config.json");
                    migrateFromOldSettings();
                } else {
                    // Create default config
                    Log.i(TAG, "No config found, creating default configuration");
                    createDefaultConfig();
                }
            }
        }
    }

    /**
     * Load configuration from config.json file.
     */
    private void loadConfig() {
        File configFile = new File(context.getFilesDir(), CONFIG_FILE_NAME);

        try (FileReader reader = new FileReader(configFile)) {
            config = gson.fromJson(reader, AppConfig.class);

            // Validate critical fields
            if (config.getNpub() == null || config.getNsec() == null || config.getCallsign() == null) {
                Log.w(TAG, "Config missing critical identity fields, regenerating identity");
                IdentityHelper.NostrIdentity identity = IdentityHelper.generateNewIdentity();
                config.setNpub(identity.npub);
                config.setNsec(identity.nsec);
                config.setCallsign(identity.callsign);
                saveConfig();
            }

            Log.i(TAG, "Configuration loaded successfully from: " + configFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Failed to load configuration, creating default", e);
            createDefaultConfig();
        }
    }

    /**
     * Save current configuration to config.json file.
     */
    public void saveConfig() {
        synchronized (lock) {
            File configFile = new File(context.getFilesDir(), CONFIG_FILE_NAME);

            try (FileWriter writer = new FileWriter(configFile)) {
                gson.toJson(config, writer);
                writer.flush();
                Log.i(TAG, "Configuration saved to: " + configFile.getAbsolutePath());
            } catch (IOException e) {
                Log.e(TAG, "Error saving configuration", e);
            }
        }
    }

    /**
     * Check if migration from old settings is needed.
     */
    private boolean needsMigration() {
        File oldSettingsFile = new File(context.getFilesDir(), OLD_SETTINGS_FILE_NAME);
        SharedPreferences relayPrefs = context.getSharedPreferences(RELAY_PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences appPrefs = context.getSharedPreferences(APP_PREFS_NAME, Context.MODE_PRIVATE);

        return oldSettingsFile.exists() ||
               relayPrefs.getAll().size() > 0 ||
               appPrefs.getAll().size() > 0;
    }

    /**
     * Migrate from old settings format (settings.json + SharedPreferences) to new config.json.
     */
    private void migrateFromOldSettings() {
        config = new AppConfig();

        // 1. Migrate from old settings.json (SettingsUser format)
        File oldSettingsFile = new File(context.getFilesDir(), OLD_SETTINGS_FILE_NAME);
        if (oldSettingsFile.exists()) {
            try (FileReader reader = new FileReader(oldSettingsFile)) {
                SettingsUser oldSettings = gson.fromJson(reader, SettingsUser.class);
                config = AppConfig.fromSettingsUser(oldSettings);
                Log.i(TAG, "Migrated settings from settings.json");
            } catch (Exception e) {
                Log.e(TAG, "Failed to migrate from settings.json", e);
            }
        }

        // 2. Migrate relay settings from SharedPreferences
        SharedPreferences relayPrefs = context.getSharedPreferences(RELAY_PREFS_NAME, Context.MODE_PRIVATE);
        if (relayPrefs.getAll().size() > 0) {
            config.setRelayEnabled(relayPrefs.getBoolean("relay_enabled", false));
            config.setRelayDiskSpaceMB(relayPrefs.getInt("disk_space_mb", 1024));
            config.setRelayAutoAccept(relayPrefs.getBoolean("auto_accept", false));
            config.setRelayMessageTypes(relayPrefs.getString("message_types", "text_only"));
            Log.i(TAG, "Migrated relay settings from SharedPreferences");
        }

        // 3. Migrate app preferences from SharedPreferences
        SharedPreferences appPrefs = context.getSharedPreferences(APP_PREFS_NAME, Context.MODE_PRIVATE);
        if (appPrefs.getAll().size() > 0) {
            config.setFirstRun(appPrefs.getBoolean("isFirstRun", true));
            Log.i(TAG, "Migrated app preferences from SharedPreferences");
        }

        // 4. Ensure identity exists
        if (config.getNpub() == null || config.getNsec() == null || config.getCallsign() == null) {
            IdentityHelper.NostrIdentity identity = IdentityHelper.generateNewIdentity();
            if (config.getNpub() == null) config.setNpub(identity.npub);
            if (config.getNsec() == null) config.setNsec(identity.nsec);
            if (config.getCallsign() == null) config.setCallsign(identity.callsign);
        }

        // 5. Save the migrated config
        saveConfig();

        // 6. Clean up old files (optional - keeping for now as backup)
        // oldSettingsFile.delete();
        // relayPrefs.edit().clear().apply();
        // appPrefs.edit().clear().apply();

        Log.i(TAG, "Migration completed successfully");
    }

    /**
     * Create default configuration with generated identity.
     */
    private void createDefaultConfig() {
        config = new AppConfig();

        // Generate complete identity
        IdentityHelper.NostrIdentity identity = IdentityHelper.generateNewIdentity();
        config.setNpub(identity.npub);
        config.setNsec(identity.nsec);
        config.setCallsign(identity.callsign);

        // Set default user settings
        config.setNickname(offgrid.geogram.util.NicknameGenerator.generateNickname());
        config.setIntro(offgrid.geogram.util.NicknameGenerator.generateIntro());
        config.setEmoticon(offgrid.geogram.util.ASCII.getRandomOneliner());
        config.setPreferredColor(selectRandomColor());

        // Set default beacon settings
        config.setBeaconType("person");
        config.setIdGroup(generateRandomNumber());
        config.setIdDevice(generateRandomDeviceId());
        config.setBeaconNickname(generateRandomBeaconNickname());

        // Other defaults are set in AppConfig field initializers

        saveConfig();
        Log.i(TAG, "Default configuration created");
    }

    // ===== Public Getters =====

    /**
     * Get the current configuration.
     *
     * @return Current AppConfig instance
     */
    public AppConfig getConfig() {
        synchronized (lock) {
            if (config == null) {
                initialize();
            }
            return config;
        }
    }

    /**
     * Get SettingsUser for backward compatibility.
     * This allows existing code to continue working without changes.
     */
    public SettingsUser getSettingsUser() {
        return getConfig().toSettingsUser();
    }

    // ===== Update Methods =====

    /**
     * Update configuration and save to disk.
     *
     * @param updater Lambda that modifies the config
     */
    public void updateConfig(ConfigUpdater updater) {
        synchronized (lock) {
            if (config == null) {
                initialize();
            }
            updater.update(config);
            saveConfig();
        }
    }

    /**
     * Functional interface for config updates.
     */
    public interface ConfigUpdater {
        void update(AppConfig config);
    }

    // ===== Convenience Methods =====

    // User Identity
    public String getCallsign() { return getConfig().getCallsign(); }
    public String getNickname() { return getConfig().getNickname(); }
    public String getIntro() { return getConfig().getIntro(); }
    public String getPreferredColor() { return getConfig().getPreferredColor(); }
    public String getEmoticon() { return getConfig().getEmoticon(); }

    // NOSTR Identity
    public String getNpub() { return getConfig().getNpub(); }
    public String getNsec() { return getConfig().getNsec(); }

    // Privacy
    public boolean isInvisibleMode() { return getConfig().isInvisibleMode(); }

    // Chat
    public String getChatCommunicationMode() { return getConfig().getChatCommunicationMode(); }
    public int getChatRadiusKm() { return getConfig().getChatRadiusKm(); }

    // HTTP API
    public boolean isHttpApiEnabled() { return getConfig().isHttpApiEnabled(); }
    public int getHttpApiPort() { return getConfig().getHttpApiPort(); }

    // Relay
    public boolean isRelayEnabled() { return getConfig().isRelayEnabled(); }
    public int getRelayDiskSpaceMB() { return getConfig().getRelayDiskSpaceMB(); }
    public long getRelayDiskSpaceBytes() { return getConfig().getRelayDiskSpaceBytes(); }
    public boolean isRelayAutoAccept() { return getConfig().isRelayAutoAccept(); }
    public String getRelayMessageTypes() { return getConfig().getRelayMessageTypes(); }

    // App
    public boolean isFirstRun() { return getConfig().isFirstRun(); }

    // ===== Helper Methods =====

    private static String selectRandomColor() {
        String[] colors = {
            "Black", "Blue", "Green", "Cyan", "Red", "Magenta", "Pink", "Brown",
            "Dark Gray", "Light Blue", "Light Green", "Light Cyan", "Light Red", "Yellow", "White"
        };
        return colors[new java.util.Random().nextInt(colors.length)];
    }

    private static String generateRandomNumber() {
        java.util.Random random = new java.util.Random();
        int number = random.nextInt(100000);
        return String.format("%05d", number);
    }

    private static String generateRandomDeviceId() {
        java.util.Random random = new java.util.Random();
        int number = random.nextInt(900000) + 100000;
        return String.valueOf(number);
    }

    private static String generateRandomBeaconNickname() {
        StringBuilder nickname = new StringBuilder("Beacon");
        java.util.Random random = new java.util.Random();
        for (int i = 0; i < 4; i++) {
            char randomChar = (char) ('A' + random.nextInt(26));
            nickname.append(randomChar);
        }
        return nickname.toString();
    }

    /**
     * Export current configuration as JSON string.
     * Useful for debugging and backup.
     */
    public String exportConfigAsJson() {
        synchronized (lock) {
            return getConfig().toString();
        }
    }

    /**
     * Get the config file path for debugging.
     */
    public String getConfigFilePath() {
        return new File(context.getFilesDir(), CONFIG_FILE_NAME).getAbsolutePath();
    }
}

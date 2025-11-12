# Configuration Migration to config.json

## Overview

All user-defined settings in the Geogram Android app have been centralized to a single `config.json` file for easier management and debugging. This document explains the migration, new structure, and how to use the ConfigManager.

## What Changed

### Before
Settings were scattered across multiple storage mechanisms:
- **settings.json**: User profile settings (SettingsUser)
- **SharedPreferences (relay_settings)**: Relay settings
- **SharedPreferences (GeogramPrefs)**: App preferences (first run flag)

### After
All settings are now in a single **config.json** file managed by `ConfigManager`.

## File Location

The config.json file is stored in the app's private storage:
```
/data/data/offgrid.geogram/files/config.json
```

You can access this file via:
```java
ConfigManager configManager = ConfigManager.getInstance(context);
String path = configManager.getConfigFilePath();
```

## Configuration Structure

See `docs/config.json.example` for a complete example. The configuration includes:

### User Identity
- `callsign`: Device callsign (format: X1XXXX)
- `nickname`: User nickname (max 15 chars)
- `intro`: User introduction (max 200 chars)
- `preferredColor`: User's preferred color
- `emoticon`: ASCII emoticon

### NOSTR Identity
- `npub`: NOSTR public key (starts with "npub1")
- `nsec`: NOSTR private key (starts with "nsec1", 63 chars)

### Beacon Settings
- `beaconNickname`: Beacon nickname (max 20 chars)
- `beaconType`: Type of beacon (e.g., "person")
- `idGroup`: Group ID (5 digits)
- `idDevice`: Device ID (6 digits)

### Privacy Options
- `invisibleMode`: Whether invisible mode is enabled

### Chat Settings
- `chatCommunicationMode`: Communication mode ("Local only", "Internet only", "Everything")
- `chatRadiusKm`: Chat radius in kilometers (1-500)

### HTTP API Settings
- `httpApiEnabled`: Whether HTTP API is enabled
- `httpApiPort`: HTTP API port number (default: 45678)

### Relay Settings
- `relayEnabled`: Whether relay is enabled
- `relayDiskSpaceMB`: Disk space limit in MB
- `relayAutoAccept`: Whether auto-accept is enabled
- `relayMessageTypes`: Accepted message types ("text_only", "text_and_images", "everything")

### App Settings
- `isFirstRun`: First run flag
- `configVersion`: Configuration version for future migrations

## Using ConfigManager

### Basic Usage

```java
// Get ConfigManager instance (singleton)
ConfigManager configManager = ConfigManager.getInstance(context);

// Initialize (automatically migrates from old format if needed)
configManager.initialize();

// Get a setting
String callsign = configManager.getCallsign();
boolean httpApiEnabled = configManager.isHttpApiEnabled();

// Update a setting
configManager.updateConfig(config -> {
    config.setNickname("NewNickname");
    config.setHttpApiEnabled(true);
});

// Get the entire config object
AppConfig config = configManager.getConfig();
```

### Backward Compatibility

For existing code using `SettingsUser`, ConfigManager provides a compatibility method:

```java
// Old code that uses SettingsUser can continue to work
SettingsUser settings = configManager.getSettingsUser();
```

The `SettingsLoader` class has been updated to use ConfigManager internally, so existing code using `SettingsLoader.loadSettings()` and `SettingsLoader.saveSettings()` will automatically use the new system.

### Thread Safety

ConfigManager is thread-safe. All read and write operations are synchronized internally.

### Saving

Settings are automatically saved to disk when you call `updateConfig()`. You can also manually save:

```java
configManager.saveConfig();
```

## Migration Process

The migration happens automatically on first app launch after the update:

1. **Check for old settings**: ConfigManager checks if `settings.json` or SharedPreferences exist
2. **Migrate data**: All data from old sources is copied to the new `config.json`
3. **Validate**: Ensures critical fields (identity) are present
4. **Save**: Writes the new `config.json`
5. **Keep old files**: Old files are kept as backup (not deleted)

### Migration Details

- **settings.json (SettingsUser)** → All fields copied to config.json
- **relay_settings (SharedPreferences)** → Relay settings copied
- **GeogramPrefs (SharedPreferences)** → First run flag copied
- **Missing identity** → New NOSTR identity generated automatically

## Files Modified

### New Files Created
- `/app/src/main/java/offgrid/geogram/settings/ConfigManager.java` - Main configuration manager
- `/app/src/main/java/offgrid/geogram/settings/AppConfig.java` - Configuration model class
- `/docs/config.json.example` - Example configuration file
- `/docs/CONFIG_MIGRATION.md` - This documentation file

### Files Updated
- `/app/src/main/java/offgrid/geogram/settings/SettingsLoader.java` - Now uses ConfigManager (marked deprecated)
- `/app/src/main/java/offgrid/geogram/relay/RelaySettings.java` - Uses ConfigManager instead of SharedPreferences
- `/app/src/main/java/offgrid/geogram/PermissionsIntroActivity.java` - Uses ConfigManager for first run flag
- `/app/src/main/java/offgrid/geogram/core/Central.java` - Initializes ConfigManager

### Files Unchanged (Backward Compatible)
- `/app/src/main/java/offgrid/geogram/settings/SettingsUser.java` - Still used for compatibility
- `/app/src/main/java/offgrid/geogram/settings/SettingsFragment.java` - Works with ConfigManager through SettingsLoader

## Complete Settings List

Here's a comprehensive list of all settings found across the app:

### User-Defined Settings
1. **callsign** - Device callsign (X1 + 4 chars from npub)
2. **nickname** - User nickname (max 15 characters)
3. **intro** - User introduction (max 200 characters)
4. **preferredColor** - User's preferred color
5. **emoticon** - One-line ASCII emoticon
6. **npub** - NOSTR public key
7. **nsec** - NOSTR private key
8. **beaconNickname** - Beacon nickname (max 20 characters)
9. **beaconType** - Beacon type (e.g., "person")
10. **idGroup** - Group ID (5 digits)
11. **idDevice** - Device ID (6 digits)
12. **invisibleMode** - Privacy mode toggle
13. **chatCommunicationMode** - Communication mode preference
14. **chatRadiusKm** - Chat radius in kilometers
15. **httpApiEnabled** - HTTP API enable/disable
16. **httpApiPort** - HTTP API port number
17. **relayEnabled** - Relay enable/disable
18. **relayDiskSpaceMB** - Relay disk space limit
19. **relayAutoAccept** - Relay auto-accept toggle
20. **relayMessageTypes** - Accepted message types for relay
21. **isFirstRun** - First run flag for permissions intro

### Hardcoded Configuration (Not in config.json)
These are constants that shouldn't be user-configurable:
- Server port: 45678 (defined in SimpleSparkServer)
- API version: 0.4.4 (defined in SimpleSparkServer)
- Contact directories: "contacts", "chat", "relay" (defined in ContactFolderManager)
- Relay directories: "inbox", "outbox", "sent" (defined in RelayStorage)
- Database limits: MAX_MESSAGES = 10,000 (defined in DatabaseMessages)
- Sync intervals: Various timing constants across the app
- Poll intervals: 30 seconds for auto-refresh (MessagesFragment, ConversationChatFragment)
- Location update period: 2 minutes (UpdatedCoordinates)
- Ping interval: 10 seconds (PingDevice)

## Debugging

### Export Configuration
```java
ConfigManager configManager = ConfigManager.getInstance(context);
String json = configManager.exportConfigAsJson();
Log.d("Config", json);
```

### Get Config File Path
```java
String path = configManager.getConfigFilePath();
Log.d("Config", "Config file: " + path);
```

### View Config on Device
Using adb:
```bash
adb shell run-as offgrid.geogram cat /data/data/offgrid.geogram/files/config.json
```

### Manual Migration
If you need to manually trigger migration:
```java
ConfigManager configManager = ConfigManager.getInstance(context);
configManager.initialize(); // This checks for old settings and migrates
```

## Benefits of Centralized Configuration

1. **Single Source of Truth**: All settings in one place
2. **Easy Debugging**: Export config as JSON string
3. **Easier Testing**: Can easily create test configurations
4. **Better Backup/Restore**: Single file to backup
5. **Version Control**: Config version field for future migrations
6. **Human Readable**: JSON format is easy to read and edit
7. **Atomic Updates**: All settings saved together
8. **Thread Safety**: Built-in synchronization

## Backward Compatibility

The implementation maintains full backward compatibility:
- Old code using `SettingsUser` continues to work
- Old code using `SettingsLoader` continues to work
- Old code using `RelaySettings` continues to work
- Automatic migration on first launch
- Old settings files kept as backup

## Future Enhancements

Potential improvements for future versions:
1. Config import/export feature in UI
2. Config reset to defaults in settings
3. Config backup to external storage
4. Config sync across devices
5. Encrypted config for sensitive data
6. Config validation UI with error messages

## Testing

To test the migration:
1. Install old version with existing settings
2. Install new version
3. Launch app - migration happens automatically
4. Verify settings are preserved
5. Check that config.json exists with correct data

## Troubleshooting

### Settings Not Migrating
- Check logcat for "ConfigManager" entries
- Verify old files exist before migration
- Check file permissions

### Settings Not Saving
- Check available disk space
- Verify app has write permissions
- Check logcat for IOException

### Invalid Settings
- ConfigManager validates all settings
- Invalid values throw IllegalArgumentException
- Check logcat for validation errors

## Summary

The migration to `config.json` provides a cleaner, more maintainable approach to configuration management while maintaining full backward compatibility with existing code. All settings are now centralized, making the codebase easier to understand, test, and debug.

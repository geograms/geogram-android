package offgrid.geogram.relay;

import android.content.Context;

import offgrid.geogram.settings.ConfigManager;

/**
 * Relay settings manager.
 *
 * Now uses ConfigManager for centralized configuration management.
 * SharedPreferences have been migrated to config.json.
 */
public class RelaySettings {

    private Context context;
    private ConfigManager configManager;

    public RelaySettings(Context context) {
        this.context = context;
        this.configManager = ConfigManager.getInstance(context);
    }

    /**
     * Check if relay is enabled.
     */
    public boolean isRelayEnabled() {
        return configManager.isRelayEnabled();
    }

    /**
     * Enable or disable relay.
     */
    public void setRelayEnabled(boolean enabled) {
        configManager.updateConfig(config -> config.setRelayEnabled(enabled));
    }

    /**
     * Get disk space limit in MB.
     */
    public int getDiskSpaceLimitMB() {
        return configManager.getRelayDiskSpaceMB();
    }

    /**
     * Set disk space limit in MB.
     */
    public void setDiskSpaceLimitMB(int mb) {
        configManager.updateConfig(config -> config.setRelayDiskSpaceMB(mb));
    }

    /**
     * Get disk space limit in bytes.
     */
    public long getDiskSpaceLimitBytes() {
        return configManager.getRelayDiskSpaceBytes();
    }

    /**
     * Check if auto-accept is enabled.
     */
    public boolean isAutoAcceptEnabled() {
        return configManager.isRelayAutoAccept();
    }

    /**
     * Enable or disable auto-accept.
     */
    public void setAutoAcceptEnabled(boolean enabled) {
        configManager.updateConfig(config -> config.setRelayAutoAccept(enabled));
    }

    /**
     * Get accepted message types.
     *
     * @return "text_only", "text_and_images", or "everything"
     */
    public String getAcceptedMessageTypes() {
        return configManager.getRelayMessageTypes();
    }

    /**
     * Set accepted message types.
     *
     * @param types "text_only", "text_and_images", or "everything"
     */
    public void setAcceptedMessageTypes(String types) {
        configManager.updateConfig(config -> config.setRelayMessageTypes(types));
    }
}

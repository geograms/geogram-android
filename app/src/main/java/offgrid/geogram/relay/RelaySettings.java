package offgrid.geogram.relay;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Relay settings manager.
 *
 * Manages persistent settings for the relay system using SharedPreferences.
 */
public class RelaySettings {

    private static final String PREFS_NAME = "relay_settings";
    private static final String KEY_ENABLED = "relay_enabled";
    private static final String KEY_DISK_SPACE_MB = "disk_space_mb";
    private static final String KEY_AUTO_ACCEPT = "auto_accept";
    private static final String KEY_MESSAGE_TYPES = "message_types";

    private Context context;
    private SharedPreferences prefs;

    public RelaySettings(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Check if relay is enabled.
     */
    public boolean isRelayEnabled() {
        return prefs.getBoolean(KEY_ENABLED, false);
    }

    /**
     * Enable or disable relay.
     */
    public void setRelayEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    /**
     * Get disk space limit in MB.
     */
    public int getDiskSpaceLimitMB() {
        return prefs.getInt(KEY_DISK_SPACE_MB, 1024); // Default 1GB
    }

    /**
     * Set disk space limit in MB.
     */
    public void setDiskSpaceLimitMB(int mb) {
        prefs.edit().putInt(KEY_DISK_SPACE_MB, mb).apply();
    }

    /**
     * Get disk space limit in bytes.
     */
    public long getDiskSpaceLimitBytes() {
        return getDiskSpaceLimitMB() * 1024L * 1024L;
    }

    /**
     * Check if auto-accept is enabled.
     */
    public boolean isAutoAcceptEnabled() {
        return prefs.getBoolean(KEY_AUTO_ACCEPT, false);
    }

    /**
     * Enable or disable auto-accept.
     */
    public void setAutoAcceptEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_ACCEPT, enabled).apply();
    }

    /**
     * Get accepted message types.
     *
     * @return "text_only", "text_and_images", or "everything"
     */
    public String getAcceptedMessageTypes() {
        return prefs.getString(KEY_MESSAGE_TYPES, "text_only");
    }

    /**
     * Set accepted message types.
     *
     * @param types "text_only", "text_and_images", or "everything"
     */
    public void setAcceptedMessageTypes(String types) {
        prefs.edit().putString(KEY_MESSAGE_TYPES, types).apply();
    }
}

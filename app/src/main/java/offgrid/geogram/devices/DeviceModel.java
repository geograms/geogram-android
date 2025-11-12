package offgrid.geogram.devices;

/**
 * Device model codes - compact 3-character codes for identifying device types.
 * Format: CODE-VERSION (e.g., APP-0.4.0, LT1-0.0.1)
 */
public enum DeviceModel {
    APP("APP", "Android Phone"),
    LT1("LT1", "LilyGo T-Dongle"),
    LT2("LT2", "LilyGo T-Deck"),
    RPI("RPI", "Raspberry Pi"),
    ESP("ESP", "ESP32 Device"),
    K5R("K5R", "Quansheng K5 Radio"),
    WTA("WTA", "WTA1 Radio"),
    UNK("UNK", "Unknown Device");

    private final String code;
    private final String displayName;

    DeviceModel(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Get DeviceModel from code string (e.g., "APP" -> DeviceModel.APP)
     */
    public static DeviceModel fromCode(String code) {
        if (code == null || code.isEmpty()) {
            return UNK;
        }

        String normalizedCode = code.trim().toUpperCase();
        for (DeviceModel model : values()) {
            if (model.code.equals(normalizedCode)) {
                return model;
            }
        }
        return UNK;
    }

    /**
     * Parse full device string (e.g., "APP-0.4.0") and return DeviceModel.
     * Returns the model part only, ignoring version.
     */
    public static DeviceModel fromDeviceString(String deviceString) {
        if (deviceString == null || deviceString.isEmpty()) {
            return UNK;
        }

        // Split by dash to get code part
        String[] parts = deviceString.split("-", 2);
        return fromCode(parts[0]);
    }

    /**
     * Extract version from device string (e.g., "APP-0.4.0" -> "0.4.0")
     * Returns null if no version found.
     */
    public static String extractVersion(String deviceString) {
        if (deviceString == null || deviceString.isEmpty()) {
            return null;
        }

        String[] parts = deviceString.split("-", 2);
        return parts.length > 1 ? parts[1] : null;
    }

    /**
     * Get display name with version (e.g., "Android Phone (0.4.0)")
     */
    public String getDisplayNameWithVersion(String version) {
        if (version != null && !version.isEmpty()) {
            return displayName + " (" + version + ")";
        }
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}

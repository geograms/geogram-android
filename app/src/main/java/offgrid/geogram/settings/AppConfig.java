package offgrid.geogram.settings;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;

/**
 * Centralized application configuration model.
 *
 * This class holds all user-defined settings and app configuration.
 * It's designed to be serialized to/from JSON for easy persistence and debugging.
 */
public class AppConfig {

    // ===== User Identity =====

    @Expose
    private String callsign; // X1 + 4 chars derived from npub (e.g., X1ABCD)

    @Expose
    private String nickname; // Limit: 15 characters

    @Expose
    private String intro; // Limit: 200 characters

    @Expose
    private String preferredColor;

    @Expose
    private String emoticon; // one-line ascii representing the user

    // ===== NOSTR Identity =====

    @Expose
    private String npub; // Must start with "npub1" and follow NOSTR spec

    @Expose
    private String nsec; // Must start with "nsec1" and follow NOSTR spec

    // ===== Beacon Settings =====

    @Expose
    private String beaconNickname; // Limit: 20 characters

    @Expose
    private String beaconType;

    @Expose
    private String idGroup; // Limit: 5 characters (numbers only)

    @Expose
    private String idDevice; // Limit: 6 characters (numbers only)

    // ===== Privacy Options =====

    @Expose
    private boolean invisibleMode = false;

    // ===== Chat Settings =====

    @Expose
    private String chatCommunicationMode = "Everything"; // Local only, Internet only, Everything

    @Expose
    private int chatRadiusKm = 100; // Default 100 km

    // ===== HTTP API Settings =====

    @Expose
    private boolean httpApiEnabled = true; // Default enabled for testing

    @Expose
    private int httpApiPort = 45678;

    // ===== Relay Settings =====

    @Expose
    private boolean relayEnabled = false;

    @Expose
    private int relayDiskSpaceMB = 1024; // Default 1GB

    @Expose
    private boolean relayAutoAccept = false;

    @Expose
    private String relayMessageTypes = "text_only"; // text_only, text_and_images, everything

    // ===== Device Relay Settings =====

    @Expose
    private boolean deviceRelayEnabled = true; // Default enabled

    @Expose
    private String deviceRelayServerUrl = "wss://api.geogram.radio:45679"; // Default relay server URL

    // ===== App Settings =====

    @Expose
    private boolean isFirstRun = true;

    @Expose
    private long configVersion = 1; // For future migrations

    // ===== Default Constructor =====

    public AppConfig() {
        // Default values are set in field initializers
    }

    // ===== Getters and Setters =====

    // User Identity

    public String getCallsign() {
        return callsign;
    }

    public void setCallsign(String callsign) {
        if (callsign != null && callsign.matches("X1[A-Z0-9]{4}")) {
            this.callsign = callsign.toUpperCase();
        } else {
            throw new IllegalArgumentException("Callsign must be in format X1XXXX (e.g., X1ABCD)");
        }
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        if (isValidText(nickname, 15)) {
            this.nickname = nickname;
        } else {
            throw new IllegalArgumentException("Nickname must be up to 15 characters");
        }
    }

    public String getIntro() {
        return intro;
    }

    public void setIntro(String intro) {
        if (isValidText(intro, 200)) {
            this.intro = intro;
        } else {
            throw new IllegalArgumentException("Intro must be up to 200 characters");
        }
    }

    public String getPreferredColor() {
        return preferredColor;
    }

    public void setPreferredColor(String preferredColor) {
        this.preferredColor = preferredColor;
    }

    public String getEmoticon() {
        return emoticon;
    }

    public void setEmoticon(String emoticon) {
        this.emoticon = emoticon;
    }

    // NOSTR Identity

    public String getNpub() {
        return npub;
    }

    public void setNpub(String npub) {
        if (npub != null && npub.startsWith("npub1")) {
            this.npub = npub;
        } else {
            throw new IllegalArgumentException("NPUB must start with 'npub1'");
        }
    }

    public String getNsec() {
        return nsec;
    }

    public void setNsec(String nsec) {
        if (nsec == null || !nsec.startsWith("nsec1") || nsec.length() != 63) {
            throw new IllegalArgumentException("NSEC must start with 'nsec1' and have 63 characters");
        }
        this.nsec = nsec;
    }

    // Beacon Settings

    public String getBeaconNickname() {
        return beaconNickname;
    }

    public void setBeaconNickname(String beaconNickname) {
        if (isValidText(beaconNickname, 20)) {
            this.beaconNickname = beaconNickname;
        } else {
            throw new IllegalArgumentException("Beacon nickname must be up to 20 characters");
        }
    }

    public String getBeaconType() {
        return beaconType;
    }

    public void setBeaconType(String beaconType) {
        this.beaconType = beaconType;
    }

    public String getIdGroup() {
        return idGroup;
    }

    public void setIdGroup(String idGroup) {
        if (isValidNumber(idGroup, 5)) {
            this.idGroup = idGroup;
        } else {
            throw new IllegalArgumentException("Group ID must be up to 5 digits");
        }
    }

    public String getIdDevice() {
        return callsign != null ? callsign : idDevice;
    }

    public void setIdDevice(String idDevice) {
        if (idDevice != null && idDevice.length() == 6) {
            this.idDevice = idDevice;
        } else {
            throw new IllegalArgumentException("Device ID must be 6 characters");
        }
    }

    // Privacy Options

    public boolean isInvisibleMode() {
        return invisibleMode;
    }

    public void setInvisibleMode(boolean invisibleMode) {
        this.invisibleMode = invisibleMode;
    }

    // Chat Settings

    public String getChatCommunicationMode() {
        return chatCommunicationMode != null ? chatCommunicationMode : "Everything";
    }

    public void setChatCommunicationMode(String mode) {
        if ("Local only".equals(mode) || "Internet only".equals(mode) || "Everything".equals(mode)) {
            this.chatCommunicationMode = mode;
        } else {
            throw new IllegalArgumentException("Invalid communication mode: " + mode);
        }
    }

    public int getChatRadiusKm() {
        return chatRadiusKm > 0 ? chatRadiusKm : 100;
    }

    public void setChatRadiusKm(int radiusKm) {
        if (radiusKm >= 1 && radiusKm <= 500) {
            this.chatRadiusKm = radiusKm;
        } else {
            throw new IllegalArgumentException("Radius must be between 1 and 500 km");
        }
    }

    // HTTP API Settings

    public boolean isHttpApiEnabled() {
        return httpApiEnabled;
    }

    public void setHttpApiEnabled(boolean httpApiEnabled) {
        this.httpApiEnabled = httpApiEnabled;
    }

    public int getHttpApiPort() {
        return httpApiPort > 0 ? httpApiPort : 45678;
    }

    public void setHttpApiPort(int port) {
        if (port > 0 && port <= 65535) {
            this.httpApiPort = port;
        } else {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }
    }

    // Relay Settings

    public boolean isRelayEnabled() {
        return relayEnabled;
    }

    public void setRelayEnabled(boolean relayEnabled) {
        this.relayEnabled = relayEnabled;
    }

    public int getRelayDiskSpaceMB() {
        return relayDiskSpaceMB > 0 ? relayDiskSpaceMB : 1024;
    }

    public void setRelayDiskSpaceMB(int mb) {
        if (mb > 0) {
            this.relayDiskSpaceMB = mb;
        } else {
            throw new IllegalArgumentException("Disk space must be positive");
        }
    }

    public long getRelayDiskSpaceBytes() {
        return getRelayDiskSpaceMB() * 1024L * 1024L;
    }

    public boolean isRelayAutoAccept() {
        return relayAutoAccept;
    }

    public void setRelayAutoAccept(boolean relayAutoAccept) {
        this.relayAutoAccept = relayAutoAccept;
    }

    public String getRelayMessageTypes() {
        return relayMessageTypes != null ? relayMessageTypes : "text_only";
    }

    public void setRelayMessageTypes(String types) {
        if ("text_only".equals(types) || "text_and_images".equals(types) || "everything".equals(types)) {
            this.relayMessageTypes = types;
        } else {
            throw new IllegalArgumentException("Invalid message types: " + types);
        }
    }

    // Device Relay Settings

    public boolean isDeviceRelayEnabled() {
        return deviceRelayEnabled;
    }

    public void setDeviceRelayEnabled(boolean deviceRelayEnabled) {
        this.deviceRelayEnabled = deviceRelayEnabled;
    }

    public String getDeviceRelayServerUrl() {
        return deviceRelayServerUrl != null ? deviceRelayServerUrl : "wss://api.geogram.radio:45679";
    }

    public void setDeviceRelayServerUrl(String url) {
        if (url != null && (url.startsWith("ws://") || url.startsWith("wss://"))) {
            this.deviceRelayServerUrl = url;
        } else {
            throw new IllegalArgumentException("Relay server URL must start with ws:// or wss://");
        }
    }

    // App Settings

    public boolean isFirstRun() {
        return isFirstRun;
    }

    public void setFirstRun(boolean firstRun) {
        this.isFirstRun = firstRun;
    }

    public long getConfigVersion() {
        return configVersion;
    }

    public void setConfigVersion(long configVersion) {
        this.configVersion = configVersion;
    }

    // ===== Validation Helpers =====

    private boolean isValidText(String input, int maxLength) {
        if (input == null || input.length() > maxLength) {
            return false;
        }
        return true;
    }

    private boolean isValidNumber(String input, int maxLength) {
        if (input == null || input.length() > maxLength) {
            return false;
        }
        return input.matches("\\d*");
    }

    // ===== Conversion Methods =====

    /**
     * Convert to SettingsUser for backward compatibility.
     */
    public SettingsUser toSettingsUser() {
        SettingsUser settings = new SettingsUser();
        if (callsign != null) settings.setCallsign(callsign);
        if (nickname != null) settings.setNickname(nickname);
        if (intro != null) settings.setIntro(intro);
        if (preferredColor != null) settings.setPreferredColor(preferredColor);
        if (emoticon != null) settings.setEmoticon(emoticon);
        if (npub != null) settings.setNpub(npub);
        if (nsec != null) settings.setNsec(nsec);
        if (beaconNickname != null) settings.setBeaconNickname(beaconNickname);
        if (beaconType != null) settings.setBeaconType(beaconType);
        if (idGroup != null) settings.setIdGroup(idGroup);
        if (idDevice != null) settings.setIdDevice(idDevice);
        settings.setInvisibleMode(invisibleMode);
        settings.setChatCommunicationMode(chatCommunicationMode);
        settings.setChatRadiusKm(chatRadiusKm);
        settings.setHttpApiEnabled(httpApiEnabled);
        return settings;
    }

    /**
     * Create from SettingsUser for backward compatibility.
     */
    public static AppConfig fromSettingsUser(SettingsUser settings) {
        AppConfig config = new AppConfig();
        if (settings.getCallsign() != null) config.setCallsign(settings.getCallsign());
        if (settings.getNickname() != null) config.setNickname(settings.getNickname());
        if (settings.getIntro() != null) config.setIntro(settings.getIntro());
        if (settings.getPreferredColor() != null) config.setPreferredColor(settings.getPreferredColor());
        if (settings.getEmoticon() != null) config.setEmoticon(settings.getEmoticon());
        if (settings.getNpub() != null) config.setNpub(settings.getNpub());
        if (settings.getNsec() != null) config.setNsec(settings.getNsec());
        if (settings.getBeaconNickname() != null) config.setBeaconNickname(settings.getBeaconNickname());
        if (settings.getBeaconType() != null) config.setBeaconType(settings.getBeaconType());
        if (settings.getIdGroup() != null) config.setIdGroup(settings.getIdGroup());
        if (settings.getIdDevice() != null && settings.getIdDevice().length() == 6) {
            config.setIdDevice(settings.getIdDevice());
        }
        config.setInvisibleMode(settings.isInvisibleMode());
        config.setChatCommunicationMode(settings.getChatCommunicationMode());
        config.setChatRadiusKm(settings.getChatRadiusKm());
        config.setHttpApiEnabled(settings.isHttpApiEnabled());
        return config;
    }

    // ===== Serialization =====

    @NonNull
    @Override
    public String toString() {
        Gson gson = new GsonBuilder()
                .excludeFieldsWithoutExposeAnnotation()
                .setPrettyPrinting()
                .create();
        return gson.toJson(this);
    }
}

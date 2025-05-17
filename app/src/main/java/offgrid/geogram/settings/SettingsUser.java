package offgrid.geogram.settings;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;

public class SettingsUser {

    // Privacy Options
    @Expose
    private boolean invisibleMode = false;

    // User Preferences
    @Expose
    private String nickname; // Limit: 15 characters

    @Expose
    private String intro; // Limit: 100 characters

    @Expose
    private String preferredColor;

    // NOSTR Identity
    @Expose
    private String npub; // Must start with "npub1" and follow NOSTR spec

    @Expose
    private String nsec; // Must start with "nsec1" and follow NOSTR spec

    // Beacon Preferences
    @Expose
    private String beaconNickname; // Limit: 20 characters

    @Expose
    private String beaconType;

    @Expose
    private String idGroup; // Limit: 5 characters (numbers only)

    @Expose
    private String idDevice; // Limit: 5 characters (numbers only)

    @Expose
    private String emoticon; // one-line ascii representing the user

    // Getters and Setters with Validation
    public boolean isInvisibleMode() {
        return invisibleMode;
    }

    public void setInvisibleMode(boolean invisibleMode) {
        this.invisibleMode = invisibleMode;
    }

    public String getEmoticon() {
        return emoticon;
    }

    public void setEmoticon(String emoticon) {
        this.emoticon = emoticon;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        if (isValidText(nickname, 15)) { // Updated limit to 15
            this.nickname = nickname;
        } else {
            throw new IllegalArgumentException("Nickname must be up to 15 characters with alphanumeric characters");
        }
    }

    public String getIntro() {
        return intro;
    }

    public void setIntro(String intro) {
        if (isValidText(intro, 200)) {
            this.intro = intro;
        } else {
            throw new IllegalArgumentException("Intro must be up to 200 characters and contain only valid characters");
        }
    }

    public String getPreferredColor() {
        return preferredColor;
    }

    public void setPreferredColor(String preferredColor) {
        this.preferredColor = preferredColor; // Assume colors are validated elsewhere
    }

    public String getNpub() {
        return npub;
    }

    public void setNpub(String npub) {
        if (npub != null && npub.startsWith("npub1")) {
            this.npub = npub;
        } else {
            throw new IllegalArgumentException("NPUB must start with 'npub1' and meet length requirements");
        }
    }

    public String getNsec() {
        return nsec;
    }

    public void setNsec(String nsec) {
        if(nsec == null || !nsec.startsWith("nsec1") || nsec.length() != 63){
            throw new IllegalArgumentException("NSEC must start with 'nsec1' and have 63 characters");
        }
        this.nsec = nsec;
    }

    public String getBeaconNickname() {
        return beaconNickname;
    }

    public void setBeaconNickname(String beaconNickname) {
        if (isValidText(beaconNickname, 20)) {
            this.beaconNickname = beaconNickname;
        } else {
            throw new IllegalArgumentException("Beacon nickname must be up to 20 characters and contain only valid characters.");
        }
    }

    public String getBeaconType() {
        return beaconType;
    }

    public void setBeaconType(String beaconType) {
        this.beaconType = beaconType; // Assume validation elsewhere
    }

    public String getIdGroup() {
        return idGroup;
    }

    public void setIdGroup(String idGroup) {
        if (isValidNumber(idGroup, 5)) {
            this.idGroup = idGroup;
        } else {
            throw new IllegalArgumentException("Group ID must be up to 5 digits and contain only numbers.");
        }
    }

    public String getIdDevice() {
        return idDevice;
    }

    public void setIdDevice(String idDevice) {
        if (idDevice != null && idDevice.length() == 6){
            this.idDevice = idDevice;
        } else {
            throw new IllegalArgumentException("Device ID must 6 digits and contain only numbers." +
                    " Provided input: " + idDevice);
        }
    }

    // Utility Methods for Validation
    private boolean isValidText(String input, int maxLength) {
        if (input == null || input.length() > maxLength) {
            return false;
        }
        return true;//input.matches("[a-zA-Z0-9\\-_.+()\\[\\]]*");
    }

    private boolean isValidNumber(String input, int maxLength) {
        if (input == null || input.length() > maxLength) {
            return false;
        }
        return input.matches("\\d*");
    }



    // Pretty Printing with Gson
    @NonNull
    @Override
    public String toString() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(this);
    }
}

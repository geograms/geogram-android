package offgrid.geogram.protocol.data;

import offgrid.geogram.core.Central;
import offgrid.geogram.settings.SettingsUser;

public class BioProfile_v1 {
         /*
            The basic biographical information from a user.
            There exists a class with the same name for low-volume
            data networks like bluetooth. This one is for Wi-Fi
            therefore we are able to include more data.
         */

    private String

            // content values
            deviceId,
            npub,
            nick,       // Limit: 15 characters
            color,      // preferred color from the user
            emoticon;   // one-line ASCII drawing

 
    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getNpub() {
        return npub;
    }

    public void setNpub(String npub) {
        this.npub = npub;
    }

    public String getNick() {
        return nick;
    }

    public void setNick(String nick) {
        this.nick = nick;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getEmoticon() {
        return emoticon;
    }

    public void setEmoticon(String emoticon) {
        this.emoticon = emoticon;
    }

    /**
     * Provides a bio profile with the data from
     * the local user
     * @return a local profile
     */
    public static BioProfile_v1 getLocalUser() {
        BioProfile_v1 bio = new BioProfile_v1();
        SettingsUser settings = Central.getInstance().getSettings();
        String deviceId = settings.getIdDevice();
        bio.setDeviceId(deviceId);
        
        String npub = settings.getNpub();
        bio.setNpub(npub);
        
        String nick = settings.getNickname();
        bio.setNick(nick);
        
        String color = settings.getPreferredColor();
        bio.setColor(color);
        
        String emoticon = settings.getEmoticon();
        bio.setEmoticon(emoticon);
        
        return bio;
    }
}

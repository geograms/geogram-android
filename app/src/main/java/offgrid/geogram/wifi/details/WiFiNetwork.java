package offgrid.geogram.wifi.details;

import com.google.gson.annotations.Expose;

/**
 * Documents the networks reachable at some point
 * It does not mean that they are reachable
 */
public class WiFiNetwork {
    private String SSIDhash;
    @Expose
    private String SSID;
    @Expose
    private String BSSID;
    @Expose
    private String capabilities;
    @Expose
    private String password;
    @Expose
    private long timeFirstSeen;
    @Expose
    private long timeLastSeen;
    @Expose
    private WiFiType type = WiFiType.UNKNOWN;
    @Expose
    private WiFiMobility mobility = WiFiMobility.UNKNOWN;
    @Expose
    private Coordinates positionMostRecent = null;

    public String getSSIDhash() {
        return SSIDhash;
    }

    public void setSSIDhash(String SSIDhash) {
        this.SSIDhash = SSIDhash;
    }

    public String getSSID() {
        return SSID;
    }

    public void setSSID(String SSID) {
        this.SSID = SSID;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setPositionRecent(Coordinates positionApproximate) {
        this.positionMostRecent = positionApproximate;
    }

    public long getTimeFirstSeen() {
        return timeFirstSeen;
    }

    public void setTimeFirstSeen(long timeFirstSeen) {
        this.timeFirstSeen = timeFirstSeen;
    }

    public long getTimeLastSeen() {
        return timeLastSeen;
    }

    public void setTimeLastSeen(long timeLastSeen) {
        this.timeLastSeen = timeLastSeen;
    }

    public WiFiType getType() {
        return type;
    }

    public void setType(WiFiType type) {
        this.type = type;
    }

    public String getBSSID() {
        return BSSID;
    }

    public void setBSSID(String BSSID) {
        this.BSSID = BSSID;
    }

    public String getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(String capabilities) {
        this.capabilities = capabilities;
    }

    public WiFiMobility getMobility() {
        return mobility;
    }

    public void setMobility(WiFiMobility mobility) {
        this.mobility = mobility;
    }

    public Coordinates getPositionMostRecent() {
        return positionMostRecent;
    }

    public void setPositionMostRecent(Coordinates positionMostRecent) {
        this.positionMostRecent = positionMostRecent;
    }

    public boolean isNotMinimallyComplete() {
        if(this.SSID == null || this.SSID.isEmpty()){
            return true;
        }
        if(this.BSSID == null){
            return true;
        }
        return false;
    }


}

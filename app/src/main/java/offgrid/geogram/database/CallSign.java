package offgrid.geogram.database;

import java.util.ArrayList;

/*
    Defines a callsign that was spotted
 */
public class CallSign {
    private String callsign = null;
    private String publicKey = null;
    private String nick = null;
    private String color = null;
    private String icon = null;
    private boolean favorite = false;
    private long
            seenFirstTime = -1,
            seenLastTime = -1;
    private String coordinatesLastSeen = null;

    private ArrayList<String> groups = new ArrayList<>();

    public String getCallsign() {
        return callsign;
    }

    public void setCallsign(String callsign) {
        this.callsign = callsign;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
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

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public boolean isFavorite() {
        return favorite;
    }

    public void setFavorite(boolean favorite) {
        this.favorite = favorite;
    }

    public long getSeenFirstTime() {
        return seenFirstTime;
    }

    public void setSeenFirstTime(long seenFirstTime) {
        this.seenFirstTime = seenFirstTime;
    }

    public long getSeenLastTime() {
        return seenLastTime;
    }

    public void setSeenLastTime(long seenLastTime) {
        this.seenLastTime = seenLastTime;
    }

    public String getCoordinatesLastSeen() {
        return coordinatesLastSeen;
    }

    public void setCoordinatesLastSeen(String coordinatesLastSeen) {
        this.coordinatesLastSeen = coordinatesLastSeen;
    }

    public ArrayList<String> getGroups() {
        return groups;
    }

    public void setGroups(ArrayList<String> groups) {
        this.groups = groups;
    }
}

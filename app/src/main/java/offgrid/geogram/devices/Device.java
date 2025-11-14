package offgrid.geogram.devices;

import androidx.annotation.NonNull;

import java.util.Objects;
import java.util.TreeSet;

public class Device implements Comparable<Device> {

    public final String ID;
    public final DeviceType deviceType;

    // Device model code and version (e.g., APP-0.4.0)
    private DeviceModel deviceModel = null;
    private String deviceVersion = null;

    // Profile information (fetched via WiFi API)
    private String profileNickname = null;
    private String profileDescription = null;
    private String profilePreferredColor = null;
    private String profileNpub = null;
    private android.graphics.Bitmap profilePicture = null;
    private boolean profileFetched = false;
    private long profileFetchTimestamp = 0; // Timestamp when profile was last fetched

    // I2P information (fetched via WiFi API, used for internet connectivity)
    private String i2pDestination = null;           // Base32 .b32.i2p address
    private boolean i2pEnabled = false;             // Whether remote device has I2P enabled
    private boolean i2pReady = false;               // Whether remote device I2P is ready
    private long i2pLastSeen = 0;                   // Timestamp of last I2P info update

    // WiFi reachability
    private boolean isWiFiReachable = true; // Assume reachable by default
    private long lastReachabilityCheck = 0; // Timestamp of last reachability check

    // when was this device located before?
    public final TreeSet<EventConnected> connectedEvents = new TreeSet<>();

    public Device(String ID, DeviceType deviceType) {
        this.ID = Objects.requireNonNull(ID, "ID");
        this.deviceType = Objects.requireNonNull(deviceType, "deviceType");
    }

    /** Get the device model enum, or null if not set. */
    public DeviceModel getDeviceModel() {
        return deviceModel;
    }

    /** Get the device version string, or null if not set. */
    public String getDeviceVersion() {
        return deviceVersion;
    }

    /**
     * Set device model from device string (e.g., "APP-0.4.0").
     * Parses the code and version automatically.
     */
    public void setDeviceModelFromString(String deviceString) {
        if (deviceString != null && !deviceString.isEmpty()) {
            this.deviceModel = DeviceModel.fromDeviceString(deviceString);
            this.deviceVersion = DeviceModel.extractVersion(deviceString);
        }
    }

    /** Get display name: device model with version if available, otherwise device type. */
    public String getDisplayName() {
        if (deviceModel != null) {
            return deviceModel.getDisplayNameWithVersion(deviceVersion);
        }
        return deviceType.toString();
    }

    /** Get profile nickname, or null if not set. */
    public String getProfileNickname() {
        return profileNickname;
    }

    /** Set profile nickname. */
    public void setProfileNickname(String nickname) {
        this.profileNickname = nickname;
    }

    /** Get profile description, or null if not set. */
    public String getProfileDescription() {
        return profileDescription;
    }

    /** Set profile description. */
    public void setProfileDescription(String description) {
        this.profileDescription = description;
    }

    /** Get profile preferred color, or null if not set. */
    public String getProfilePreferredColor() {
        return profilePreferredColor;
    }

    /** Set profile preferred color. */
    public void setProfilePreferredColor(String color) {
        this.profilePreferredColor = color;
    }

    /** Get profile npub, or null if not set. */
    public String getProfileNpub() {
        return profileNpub;
    }

    /** Set profile npub. */
    public void setProfileNpub(String npub) {
        this.profileNpub = npub;
    }

    /** Get I2P destination (Base32 .b32.i2p address). */
    public String getI2PDestination() {
        return i2pDestination;
    }

    /** Set I2P destination. */
    public void setI2PDestination(String destination) {
        this.i2pDestination = destination;
        this.i2pLastSeen = System.currentTimeMillis();
    }

    /** Check if device has I2P destination. */
    public boolean hasI2PDestination() {
        return i2pDestination != null && !i2pDestination.isEmpty();
    }

    /** Check if remote device has I2P enabled. */
    public boolean isI2PEnabled() {
        return i2pEnabled;
    }

    /** Set I2P enabled status. */
    public void setI2PEnabled(boolean enabled) {
        this.i2pEnabled = enabled;
    }

    /** Check if remote device I2P is ready (tunnels established). */
    public boolean isI2PReady() {
        return i2pReady;
    }

    /** Set I2P ready status. */
    public void setI2PReady(boolean ready) {
        this.i2pReady = ready;
    }

    /** Get timestamp when I2P info was last updated. */
    public long getI2PLastSeen() {
        return i2pLastSeen;
    }

    /** Get profile picture bitmap, or null if not set. */
    public android.graphics.Bitmap getProfilePicture() {
        return profilePicture;
    }

    /** Set profile picture bitmap. */
    public void setProfilePicture(android.graphics.Bitmap picture) {
        this.profilePicture = picture;
    }

    /** Check if profile has been fetched and is still valid (not older than 6 hours). */
    public boolean isProfileFetched() {
        if (!profileFetched) {
            return false;
        }

        // Check if profile is older than 6 hours (21600000 milliseconds)
        long currentTime = System.currentTimeMillis();
        long profileAge = currentTime - profileFetchTimestamp;
        long sixHoursInMillis = 6 * 60 * 60 * 1000;

        if (profileAge > sixHoursInMillis) {
            // Profile is stale, mark as not fetched
            profileFetched = false;
            return false;
        }

        return true;
    }

    /** Mark profile as fetched with current timestamp. */
    public void setProfileFetched(boolean fetched) {
        this.profileFetched = fetched;
        if (fetched) {
            this.profileFetchTimestamp = System.currentTimeMillis();
        }
    }

    /** Check if device is reachable via WiFi. */
    public boolean isWiFiReachable() {
        return isWiFiReachable;
    }

    /** Set WiFi reachability status. */
    public void setWiFiReachable(boolean reachable) {
        this.isWiFiReachable = reachable;
        this.lastReachabilityCheck = System.currentTimeMillis();
    }

    /** Get timestamp of last reachability check. */
    public long getLastReachabilityCheck() {
        return lastReachabilityCheck;
    }

    /** Latest (most recent) timestamp across all locations, or Long.MIN_VALUE if none. */
    public long latestTimestamp() {
        if (connectedEvents.isEmpty()) return Long.MIN_VALUE;
        return connectedEvents.last().latestTimestamp(); // ConnectedEvent is ordered by its latest timestamp
    }

    /**
     * Natural order: NEWEST FIRST (descending by latest timestamp).
     * Stable tie-breakers: ID, then deviceType.
     */
    @Override
    public int compareTo(Device other) {
        int byTimeDesc = Long.compare(other.latestTimestamp(), this.latestTimestamp());
        if (byTimeDesc != 0) return byTimeDesc;

        int byId = this.ID.compareTo(other.ID);
        if (byId != 0) return byId;

        return this.deviceType.compareTo(other.deviceType);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Device)) return false;
        Device device = (Device) o;
        return ID.equals(device.ID) && deviceType == device.deviceType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(ID, deviceType);
    }

//    @NonNull
//    @Override
//    public String toString() {
//        return "Device{" +
//                "ID='" + ID + '\'' +
//                ", deviceType=" + deviceType +
//                ", latest=" + latestTimestamp() +
//                ", locations=" + connectedEvents.size() +
//                '}';
//    }


    @NonNull
    @Override
    public String toString(){
        return ID + " (" + deviceType.name() + ")";
    }

    public void addEvent(EventConnected event) {
        // iterate all previous events to update
        for(EventConnected connectedEvent : connectedEvents) {
            // needs to match the same type
            if (connectedEvent.connectionType != event.connectionType) {
                continue;
            }

            // For ping-only events (null geocode), just update timestamp on matching connection type
            if (event.geocode == null && connectedEvent.geocode == null) {
                if (!connectedEvent.containsTimeStamp(event.latestTimestamp())) {
                    // CRITICAL FIX: Remove event from TreeSet before updating timestamp
                    // TreeSets don't automatically re-sort when element's comparison value changes
                    connectedEvents.remove(connectedEvent);
                    connectedEvent.addTimestamp(event.latestTimestamp());
                    // Re-add to TreeSet to trigger re-sorting by updated timestamp
                    connectedEvents.add(connectedEvent);
                }
                return;
            }

            // the coordinates need to match for location events
            if (event.geocode != null && connectedEvent.geocode != null
                    && event.geocode.equalsIgnoreCase(connectedEvent.geocode)) {
                if (!connectedEvent.containsTimeStamp(event.latestTimestamp())) {
                    // CRITICAL FIX: Remove event from TreeSet before updating timestamp
                    // TreeSets don't automatically re-sort when element's comparison value changes
                    connectedEvents.remove(connectedEvent);
                    connectedEvent.addTimestamp(event.latestTimestamp());
                    // Re-add to TreeSet to trigger re-sorting by updated timestamp
                    connectedEvents.add(connectedEvent);
                }
                return;
            }
        }

        // there was no match, so add one
        connectedEvents.add(event);
    }

}

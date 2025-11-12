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

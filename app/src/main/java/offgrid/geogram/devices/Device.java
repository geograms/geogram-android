package offgrid.geogram.devices;

import androidx.annotation.NonNull;

import java.util.Objects;
import java.util.TreeSet;

public class Device implements Comparable<Device> {

    public final String ID;
    public final DeviceType deviceType;

    // when was this device located before?
    public final TreeSet<ConnectedEvent> connectedEvents = new TreeSet<>();

    public Device(String ID, DeviceType deviceType) {
        this.ID = Objects.requireNonNull(ID, "ID");
        this.deviceType = Objects.requireNonNull(deviceType, "deviceType");
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

    @NonNull
    @Override
    public String toString() {
        return "Device{" +
                "ID='" + ID + '\'' +
                ", deviceType=" + deviceType +
                ", latest=" + latestTimestamp() +
                ", locations=" + connectedEvents.size() +
                '}';
    }

    public void addEvent(ConnectedEvent event) {
        // iterate all previous events to update
        for(ConnectedEvent connectedEvent : connectedEvents){
            // needs to match the same type
            if(connectedEvent.connectionType != event.connectionType){
                continue;
            }
            // the coordinates need to match
            if(!connectedEvent.alt.equalsIgnoreCase(event.alt)
                || !connectedEvent.lat.equalsIgnoreCase(event.lat)
                    || !connectedEvent.lon.equalsIgnoreCase(event.lon)
            ){
                continue;
            }
            // coordinates are the same
            // was the same time stamp added before?
            if(connectedEvent.containsTimeStamp(event.latestTimestamp())){
                return;
            }else{
                // it wasn't, so add it up here
                connectedEvent.addTimestamp(event.latestTimestamp());
                return;
            }
        }
        // there was no match, so add one
        connectedEvents.add(event);
    }
}

package offgrid.geogram.devices;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;

/**
 * There was a connection between two devices.
 * Natural order: by most recent timestamp (older first, newer last).
 */
public class EventConnected implements Comparable<EventConnected> {
    public final ArrayList<Long> timestamps = new ArrayList<>();
    public final ConnectionType connectionType;

    public final String lat, lon, alt;

    public EventConnected(ConnectionType connectionType, String lat, String lon, String alt) {
        this.connectionType = Objects.requireNonNull(connectionType, "connectionType");
        this.lat = lat;
        this.lon = lon;
        this.alt = alt;
        // record creation time as first observation
        timestamps.add(System.currentTimeMillis());
    }

    /** Add an observation timestamp (ms since epoch). */
    public void addTimestamp(long epochMillis) {
        timestamps.add(epochMillis);
    }

    /** Convenience: record "now" as a new observation. */
    public void touchNow() {
        addTimestamp(System.currentTimeMillis());
    }

    /** Most recent (max) timestamp in this event, or Long.MIN_VALUE if none. */
    public long latestTimestamp() {
        if (timestamps.isEmpty()) return Long.MIN_VALUE;
        return Collections.max(timestamps);
    }

    /** Natural ordering: ascending by latest timestamp (older first). */
    @Override
    public int compareTo(EventConnected other) {
        int byTime = Long.compare(this.latestTimestamp(), other.latestTimestamp());
        if (byTime != 0) return byTime;
        // stable tie-breakers to keep sort deterministic
        int byType = this.connectionType.compareTo(other.connectionType);
        if (byType != 0) return byType;
        int byLat = Objects.compare(this.lat, other.lat, String::compareTo);
        if (byLat != 0) return byLat;
        int byLon = Objects.compare(this.lon, other.lon, String::compareTo);
        if (byLon != 0) return byLon;
        return Objects.compare(this.alt, other.alt, String::compareTo);
    }

    /** Equality by connection type and fixed location (ignores timestamps). */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EventConnected)) return false;
        EventConnected that = (EventConnected) o;
        return connectionType == that.connectionType
                && Objects.equals(lat, that.lat)
                && Objects.equals(lon, that.lon)
                && Objects.equals(alt, that.alt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(connectionType, lat, lon, alt);
    }

    @Override
    public String toString() {
        return "ConnectedEvent{" +
                "type=" + connectionType +
                ", lat='" + lat + '\'' +
                ", lon='" + lon + '\'' +
                ", alt='" + alt + '\'' +
                ", latest=" + latestTimestamp() +
                ", count=" + timestamps.size() +
                '}';
    }

    public boolean containsTimeStamp(long l) {
        return timestamps.contains(l);
    }
}

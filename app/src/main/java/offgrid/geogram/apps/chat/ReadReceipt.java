package offgrid.geogram.apps.chat;

import java.util.Objects;

/**
 * Represents a read receipt for a chat message.
 * Tracks who read the message and when.
 */
public class ReadReceipt implements Comparable<ReadReceipt> {

    public String callsign;      // Who read the message
    public long timestamp;        // When they read it (Unix milliseconds)

    public ReadReceipt() {
        // Default constructor for JSON serialization
    }

    public ReadReceipt(String callsign, long timestamp) {
        this.callsign = callsign;
        this.timestamp = timestamp;
    }

    public String getCallsign() {
        return callsign;
    }

    public long getTimestamp() {
        return timestamp;
    }

    /** Sort by timestamp (oldest first) */
    @Override
    public int compareTo(ReadReceipt other) {
        int byTime = Long.compare(this.timestamp, other.timestamp);
        if (byTime != 0) return byTime;

        // Tie-breaker by callsign
        return safeStr(this.callsign).compareTo(safeStr(other.callsign));
    }

    private static String safeStr(String s) {
        return s == null ? "" : s;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReadReceipt)) return false;
        ReadReceipt that = (ReadReceipt) o;
        return timestamp == that.timestamp && Objects.equals(callsign, that.callsign);
    }

    @Override
    public int hashCode() {
        return Objects.hash(callsign, timestamp);
    }

    @Override
    public String toString() {
        return "ReadReceipt{callsign='" + callsign + "', timestamp=" + timestamp + "}";
    }
}

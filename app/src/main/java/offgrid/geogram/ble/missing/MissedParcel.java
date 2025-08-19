package offgrid.geogram.ble.missing;

public class MissedParcel {
    final long timeFirstAsked;
    final String parcelNumber;
    final String messageId;

    public MissedParcel(long timeFirstAsked, String parcelNumber, String messageId) {
        this.timeFirstAsked = timeFirstAsked;
        this.parcelNumber = parcelNumber;
        this.messageId = messageId;
    }
}

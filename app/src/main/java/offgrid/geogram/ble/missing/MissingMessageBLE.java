package offgrid.geogram.ble.missing;

public class MissingMessageBLE {
    String parcelId;
    long timeLastSent = -1;

    public MissingMessageBLE(String parcelId){
        this.parcelId = parcelId;
    }

}

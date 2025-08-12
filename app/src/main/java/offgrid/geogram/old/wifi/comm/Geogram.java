package offgrid.geogram.old.wifi.comm;

public class Geogram {

    // to whom we deliver the data
    String targetNPUB;

    // what is delivered
    String data;

    VisibilityType visibility = VisibilityType.PUBLIC;

    // when was the last time we tried to send it
    private final long timeCreated = System.currentTimeMillis();
    private long timeToExpire = -1;

}

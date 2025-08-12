package offgrid.geogram.old.old.old;

public class BeaconManager {

    private static BeaconManager instance;
    private EddystoneBeacon eddystoneBeacon;

    private BeaconManager() {
    }

    public static synchronized BeaconManager getInstance() {
        if (instance == null) {
            instance = new BeaconManager();
        }
        return instance;
    }

    public void setEddystoneBeacon(EddystoneBeacon beacon) {
        this.eddystoneBeacon = beacon;
    }

    public EddystoneBeacon getEddystoneBeacon() {
        return eddystoneBeacon;
    }
}

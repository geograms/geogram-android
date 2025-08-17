package offgrid.geogram.devices;

import java.util.TreeSet;

import offgrid.geogram.database.DatabaseLocations;
import offgrid.geogram.events.EventControl;
import offgrid.geogram.events.EventType;

/**
 * Singleton managing devices found nearby (physically or remotely).
 */
public class DeviceManager {

    // Singleton instance (eager initialization)
    private static final DeviceManager INSTANCE = new DeviceManager();

    private final TreeSet<Device> devicesSpotted = new TreeSet<>();

    /** Private constructor prevents instantiation outside this class. */
    private DeviceManager() { }

    /** Access the single shared instance. */
    public static DeviceManager getInstance() {
        return INSTANCE;
    }

    /** Add a device to the spotted set. */
    public synchronized void addDevice(Device device) {
        devicesSpotted.add(device);
    }

    /** Remove a device from the spotted set. */
    public synchronized void removeDevice(Device device) {
        devicesSpotted.remove(device);
    }

    /** Get a snapshot of the currently spotted devices. */
    public synchronized TreeSet<Device> getDevicesSpotted() {
        return new TreeSet<>(devicesSpotted);
    }

    /** Clear all spotted devices. */
    public synchronized void clear() {
        devicesSpotted.clear();
    }

    public synchronized void addNewLocationEvent(String callsign, DeviceType deviceType, EventConnected event){
        Device deviceFound = null;
        for(Device device : devicesSpotted){
            if(device.ID.equalsIgnoreCase(callsign)){
                deviceFound = device;
                break;
            }
        }
        // when there was no device, add one
        if(deviceFound == null){
            deviceFound = new Device(callsign, deviceType);
            devicesSpotted.add(deviceFound);
        }
        // add the event
        deviceFound.addEvent(event);

        // add this location event to the database
        DatabaseLocations.get().enqueue(
                callsign,
                event.geocode,
                System.currentTimeMillis(),
                null
        );

        // a new event happened with the device
        EventControl.startEvent(EventType.DEVICE_UPDATED, deviceFound);

    }

}

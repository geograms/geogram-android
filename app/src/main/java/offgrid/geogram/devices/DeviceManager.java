package offgrid.geogram.devices;

import android.util.Log;

import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.Executors;

import offgrid.geogram.database.DatabaseDevices;
import offgrid.geogram.database.DatabaseLocations;
import offgrid.geogram.events.EventControl;
import offgrid.geogram.events.EventType;

/**
 * Singleton managing devices found nearby (physically or remotely).
 */
public class DeviceManager {

    private static final String TAG = "DeviceManager";

    // Singleton instance (eager initialization)
    private static final DeviceManager INSTANCE = new DeviceManager();

    private final TreeSet<Device> devicesSpotted = new TreeSet<>();
    private boolean isLoadedFromDatabase = false;

    /** Private constructor prevents instantiation outside this class. */
    private DeviceManager() { }

    /** Access the single shared instance. */
    public static DeviceManager getInstance() {
        return INSTANCE;
    }

    /**
     * Load device history from database (call once after database initialization).
     * Runs on background thread to avoid blocking UI.
     */
    public synchronized void loadFromDatabase() {
        if (isLoadedFromDatabase) {
            Log.d(TAG, "Devices already loaded from database");
            return;
        }

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                List<DatabaseDevices.DeviceRow> deviceRows = DatabaseDevices.get().getAllDevices();
                Log.d(TAG, "Loading " + deviceRows.size() + " devices from database");

                for (DatabaseDevices.DeviceRow row : deviceRows) {
                    Device device = new Device(row.callsign, DeviceType.valueOf(row.deviceType));

                    // Reconstruct event history from ping records
                    List<DatabaseDevices.DevicePingRow> pings = DatabaseDevices.get().getPingsForDevice(row.callsign, 1000);
                    for (DatabaseDevices.DevicePingRow ping : pings) {
                        EventConnected event = new EventConnected(ConnectionType.BLE, ping.geocode);
                        // Override the auto-generated timestamp with the stored one
                        event.timestamps.clear();
                        event.timestamps.add(ping.timestamp);
                        device.connectedEvents.add(event);
                    }

                    synchronized (devicesSpotted) {
                        devicesSpotted.add(device);
                    }
                }

                isLoadedFromDatabase = true;
                Log.d(TAG, "Loaded " + devicesSpotted.size() + " devices from database");

                // Notify UI to update
                EventControl.startEvent(EventType.DEVICE_UPDATED, null);

            } catch (Exception e) {
                Log.e(TAG, "Error loading devices from database", e);
            }
        });
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

        // Save to databases
        // 1. Add location event to DatabaseLocations only if geocode is present
        if(event.geocode != null) {
            DatabaseLocations.get().enqueue(
                    callsign,
                    event.geocode,
                    System.currentTimeMillis(),
                    null
            );
        }

        // 2. Save/update device profile in DatabaseDevices
        long firstSeen = deviceFound.connectedEvents.isEmpty() ?
                System.currentTimeMillis() :
                java.util.Collections.min(deviceFound.connectedEvents).latestTimestamp();
        long lastSeen = deviceFound.latestTimestamp();
        int totalDetections = 0;
        for (EventConnected e : deviceFound.connectedEvents) {
            totalDetections += e.timestamps.size();
        }

        DatabaseDevices.get().saveDevice(
                callsign,
                deviceType.name(),
                firstSeen,
                lastSeen,
                totalDetections,
                null, // npub - will be added later
                null, // alias - will be added later
                null, // tags - will be added later
                null  // notes - will be added later
        );

        // 3. Record this ping/detection event
        DatabaseDevices.get().enqueuePing(
                callsign,
                event.latestTimestamp(),
                event.geocode
        );

        // a new event happened with the device
        EventControl.startEvent(EventType.DEVICE_UPDATED, deviceFound);

    }

}

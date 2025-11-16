package offgrid.geogram.devices;

import android.content.Context;
import android.util.Log;

import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.Executors;

import offgrid.geogram.database.DatabaseDevices;
import offgrid.geogram.database.DatabaseLocations;
import offgrid.geogram.events.EventControl;
import offgrid.geogram.events.EventType;
import offgrid.geogram.relay.RelayMessageSync;

/**
 * Singleton managing devices found nearby (physically or remotely).
 */
public class DeviceManager {

    private static final String TAG = "DeviceManager";

    // Singleton instance (eager initialization)
    private static final DeviceManager INSTANCE = new DeviceManager();

    private final TreeSet<Device> devicesSpotted = new TreeSet<>();
    private boolean isLoadedFromDatabase = false;
    private Context context;

    /** Private constructor prevents instantiation outside this class. */
    private DeviceManager() { }

    /** Initialize with context (call once from Application or MainActivity). */
    public void initialize(Context context) {
        if (this.context == null) {
            this.context = context.getApplicationContext();
        }
    }

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
                    DeviceType deviceType = DeviceType.valueOf(row.deviceType);

                    // Check if device already exists (avoid duplicates)
                    Device device = null;
                    synchronized (devicesSpotted) {
                        for (Device d : devicesSpotted) {
                            if (d.ID.equalsIgnoreCase(row.callsign) && d.deviceType == deviceType) {
                                device = d;
                                break;
                            }
                        }

                        // Create new device if not found
                        if (device == null) {
                            device = new Device(row.callsign, deviceType);
                            device.callsign = row.callsign;  // Store callsign for relay lookups
                            devicesSpotted.add(device);
                        } else {
                            // Ensure callsign is set for existing devices
                            if (device.callsign == null) {
                                device.callsign = row.callsign;
                            }
                        }
                    }

                    // Reconstruct event history from ping records
                    List<DatabaseDevices.DevicePingRow> pings = DatabaseDevices.get().getPingsForDevice(row.callsign, 1000);
                    for (DatabaseDevices.DevicePingRow ping : pings) {
                        // Parse connection type from database (default to BLE for old records)
                        ConnectionType connType;
                        try {
                            connType = ConnectionType.valueOf(ping.connectionType);
                        } catch (Exception e) {
                            connType = ConnectionType.BLE;  // Fallback for invalid/old data
                        }

                        EventConnected event = new EventConnected(connType, ping.geocode);
                        // Override the auto-generated timestamp with the stored one
                        event.timestamps.clear();
                        event.timestamps.add(ping.timestamp);
                        device.connectedEvents.add(event);
                    }
                }

                isLoadedFromDatabase = true;
                Log.d(TAG, "Loaded " + devicesSpotted.size() + " devices from database");

                // Notify UI to update - pass empty object array instead of null
                // This is a general "reload all devices" signal, not for a specific device
                EventControl.startEvent(EventType.DEVICE_UPDATED, new Object[0]);

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

    /** Clear all spotted devices from memory and database. */
    public synchronized void clear() {
        // Clear in-memory list
        devicesSpotted.clear();

        // Clear database (devices and pings)
        DatabaseDevices.get().deleteAllDevices();

        // Reset loaded flag so devices are only added back when actually spotted
        isLoadedFromDatabase = false;

        Log.i(TAG, "Cleared all devices from memory and database");
    }

    public synchronized void addNewLocationEvent(String callsign, DeviceType deviceType, EventConnected event){
        addNewLocationEvent(callsign, deviceType, event, null);
    }

    public synchronized void addNewLocationEvent(String callsign, DeviceType deviceType, EventConnected event, String deviceModel){
        Device deviceFound = null;
        for(Device device : devicesSpotted){
            // Match by callsign only - allow device type to be updated if capability changes
            // (e.g., HT_PORTABLE -> INTERNET_IGATE when relay capability detected)
            if(device.ID.equalsIgnoreCase(callsign)){
                deviceFound = device;
                break;
            }
        }
        // when there was no device, add one
        if(deviceFound == null){
            deviceFound = new Device(callsign, deviceType);
            deviceFound.callsign = callsign;  // Store callsign for relay lookups
            devicesSpotted.add(deviceFound);
        } else {
            // CRITICAL FIX: Remove device from TreeSet before updating
            // TreeSets don't automatically re-sort when element's comparison value changes
            devicesSpotted.remove(deviceFound);
            // Ensure callsign is set (for devices discovered before this field was added)
            if (deviceFound.callsign == null) {
                deviceFound.callsign = callsign;
            }
        }

        // Update device model if provided (e.g., "APP-0.4.0")
        if(deviceModel != null && !deviceModel.isEmpty()){
            deviceFound.setDeviceModelFromString(deviceModel);
        }

        // add the event
        deviceFound.addEvent(event);

        // Re-add to TreeSet to trigger re-sorting by updated timestamp
        devicesSpotted.add(deviceFound);

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

        // 3. Record this ping/detection event with connection type
        DatabaseDevices.get().enqueuePing(
                callsign,
                event.latestTimestamp(),
                event.geocode,
                event.connectionType.name()  // Save the actual connection type (BLE, WIFI, etc.)
        );

        // a new event happened with the device
        EventControl.startEvent(EventType.DEVICE_UPDATED, deviceFound);

        // Trigger relay sync if this is an Internet Relay device
        if (context != null && deviceType == DeviceType.INTERNET_IGATE) {
            try {
                RelayMessageSync relaySync = RelayMessageSync.getInstance(context);
                relaySync.startSync(callsign);
                Log.d(TAG, "Triggered relay sync with " + callsign);
            } catch (Exception e) {
                Log.e(TAG, "Failed to start relay sync: " + e.getMessage());
            }
        }

    }

}

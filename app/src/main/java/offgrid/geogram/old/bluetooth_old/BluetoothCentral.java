package offgrid.geogram.old.bluetooth_old;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.IntentFilter;
import android.os.ParcelUuid;

import java.util.List;
import java.util.UUID;

import offgrid.geogram.old.bluetooth_old.eddystone.DeviceFinder;
import offgrid.geogram.old.bluetooth_old.eddystone.EddyDeviceAdvertise;
import offgrid.geogram.old.bluetooth_old.other.GattServer;
import offgrid.geogram.old.bluetooth_old.watchdog.BluePing;
import offgrid.geogram.old.bluetooth_old.other.old.BluetoothStateReceiver;
import offgrid.geogram.old.bluetooth_old.watchdog.EventBluetoothAcknowledgementReceived;
import offgrid.geogram.old.bluetooth_old.watchdog.EventBluetoothPackageReceived;
import offgrid.geogram.old.bluetooth_old.watchdog.WatchDogMissingParcels;
import offgrid.geogram.old.bluetooth_old.watchdog.WatchDogRestartGATT;
import offgrid.geogram.core.Log;
import offgrid.geogram.events.EventControl;
import offgrid.geogram.events.EventType;

public class BluetoothCentral {

    private static final String TAG = "BluetoothCentral";

    private static BluetoothCentral instance;

    private final Context context;
    private GattServer gattServer;
    private EddyDeviceAdvertise beacon;
    private BluetoothAdapter bluetoothAdapter;
    private boolean isReceiverRegistered = false;

    // Scanning state
    private boolean isScanning = false;

    // Public static UUIDs for external access
    // Eddystone Service UUID
    public static String EDDYSTONE_SERVICE_ID = //"0000FEAA-0000-1000-8000-00805F9B34FB";
                                                "0000feaa-0000-1000-8000-00805f9b34fb";
    public static final ParcelUuid EDDYSTONE_SERVICE_UUID = ParcelUuid.fromString(EDDYSTONE_SERVICE_ID);
    public static final UUID UUID_SERVICE_WALKIETALKIE = UUID.fromString("12345678-1234-5678-1234-56789abcdef0");
    public static final UUID UUID_CHARACTERISTIC_GENERAL = UUID.fromString("abcdef12-3456-7890-abcd-ef1234567890");

    private BluetoothCentral(Context context) {
        this.context = context.getApplicationContext();
        initialize();
        setupEvents();
    }

    private void setupEvents() {
        // handle the case a new package being received as complete
        EventControl.addEvent(EventType.BLUETOOTH_MESSAGE_RECEIVED,
                new EventBluetoothPackageReceived(TAG + "+ packageReceived", context)
        );
        EventControl.addEvent(EventType.BLUETOOTH_ACKNOWLEDGE_RECEIVED,
                new EventBluetoothAcknowledgementReceived(TAG + "+ ackReceived", context)
        );
    }

    /**
     * Singleton access to the BluetoothCentral instance.
     */
    public static synchronized BluetoothCentral getInstance(Context context) {
        if (instance == null) {
            instance = new BluetoothCentral(context);
        }
        return instance;
    }

    /**
     * Initializes the GATT server, beacon, and Bluetooth adapter.
     */
    private void initialize() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        gattServer = GattServer.getInstance(context);
        beacon = EddyDeviceAdvertise.getInstance(context);

        registerBluetoothStateReceiver();

        Log.i(TAG, "BluetoothCentral initialized with GATT server and beacon.");
    }

    /**
     * Registers the BluetoothStateReceiver.
     */
    private void registerBluetoothStateReceiver() {
        if (isReceiverRegistered == false) {
            IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
            BluetoothStateReceiver receiver = BluetoothStateReceiver.getInstance();
            context.registerReceiver(receiver, filter);
            isReceiverRegistered = true;
            Log.i(TAG, "BluetoothStateReceiver registered.");
        } else {
            Log.i(TAG, "BluetoothStateReceiver is already registered.");
        }
    }

    /**
     * Starts the GATT server and beacon if Bluetooth is enabled.
     */
    public void start() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        gattServer = GattServer.getInstance(context);

        if (bluetoothAdapter == null || bluetoothAdapter.isEnabled() == false) {
            Log.i(TAG, "Bluetooth is disabled. Services will not start.");
            return;
        }

        if (gattServer == null) {
            Log.i(TAG,"Starting GATT server.");
            //gattServer = AppBluetoothGattServer.getInstance(context);
            gattServer = GattServer.getInstance(context);
        }
        // always start the gatt server
        //gattServer = GattServer.getInstance(context);

        if (beacon != null && beacon.isAdvertising() == false) {
            beacon.startBeaconDevice();
            Log.i(TAG, "Beacon started.");
        }

        // Start the ping service
        BluePing.getInstance(context).start();

        // start the watchdog for lost parcels
        WatchDogMissingParcels.getInstance().startLoop(context);
        WatchDogRestartGATT.getInstance().startLoop(context);

        // synchronize messages
        if (isScanning == false){
            startScanningForNewBLE();
        }
    }

    /**
     * Stops the GATT server and beacon.
     */
    public void stop() {
        stopScanningForBLE();

        if (beacon != null) {
            beacon.stopBeacon();
            Log.i(TAG, "Beacon stopped.");
        }

        if (gattServer != null) {
            Log.i(TAG, "GATT server will be nullified");
            gattServer.stop();
            gattServer = null;
        }

        // Stop the ping service
        BluePing.getInstance(context).stop();

        // Stop the watchdog for lost parcels
        WatchDogMissingParcels.getInstance().stopLoop();
    }

    /**
     * Starts Bluetooth scanning.
     */
    private void startScanningForNewBLE() {
        if (isScanning) {
            Log.i(TAG, "Scanning is already active.");
            return;
        }

        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
            DeviceFinder beaconFinder = DeviceFinder.getInstance(context);
            beaconFinder.startScanning();
            Log.i(TAG, "Device scanning started");
            isScanning = true;
        } else {
            Log.i(TAG, "Bluetooth is not enabled. Cannot start scanning.");
        }
    }

    /**
     * Stops Bluetooth scanning.
     */
    private void stopScanningForBLE() {
        if (!isScanning) {
            Log.i(TAG, "Scanning is not active.");
            return;
        }

        DeviceFinder beaconFinder = DeviceFinder.getInstance(context);
        beaconFinder.stopScanning();
        isScanning = false;
        Log.i(TAG, "Device scanning stopped from BluetoothCentral.");
    }

    /**
     * Gets the current connected devices.
     */
    public List<BluetoothDevice> getConnectedDevices() {
        return gattServer.getConnectedDevices();
    }

    /**
     * Handles Bluetooth state changes.
     */
    public void handleBluetoothStateChange(boolean isEnabled) {
//        if (isEnabled) {
//            Log.i(TAG, "Bluetooth enabled. Starting services...");
//            start();
//        } else {
//            Log.i(TAG, "Bluetooth disabled. Stopping services...");
//            stop();
//        }
    }

    /**
     * Checks if Bluetooth is available and enabled on the device.
     *
     * @return true if Bluetooth is available and enabled, false otherwise.
     */
    public boolean isBluetoothAvailable() {
        if (bluetoothAdapter == null) {
            Log.i(TAG, "Bluetooth is not supported on this device.");
            return false;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Log.i(TAG, "Bluetooth is not enabled.");
            return false;
        }

        return true;
    }

}

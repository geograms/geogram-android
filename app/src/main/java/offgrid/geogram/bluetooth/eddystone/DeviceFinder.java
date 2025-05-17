package offgrid.geogram.bluetooth.eddystone;

import static offgrid.geogram.bluetooth.BluetoothCentral.EDDYSTONE_SERVICE_UUID;
import static offgrid.geogram.bluetooth.broadcast.BroadcastSender.sendProfileToEveryone;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Handler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import offgrid.geogram.bluetooth.other.comms.Mutex;
import offgrid.geogram.core.Log;
import offgrid.geogram.devices.DeviceReachable;

public class DeviceFinder {

    private static final String TAG = "DeviceFinder";
    //private static final long BEACON_TIMEOUT_MS = 30000; // 30 seconds
    private static final long SCAN_DURATION_MS = 3000; // Scan for 3 seconds
    private static final long SCAN_INTERVAL_MS = 15000; // Pause for 15 seconds between scans

    private static DeviceFinder instance;

    private final Context context;
    private final BluetoothAdapter bluetoothAdapter;
    private final HashMap<String, DeviceReachable> deviceMap = new HashMap<>();
    private boolean isScanning = false;
    private final Handler scanHandler = new Handler();
    private final ScheduledExecutorService scanScheduler = Executors.newSingleThreadScheduledExecutor();

    private DeviceFinder(Context context) {
        this.context = context.getApplicationContext();
        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager != null ? bluetoothManager.getAdapter() : null;
    }

    /**
     * Singleton access to the DeviceFinder instance.
     */
    public static synchronized DeviceFinder getInstance(Context context) {
        if (instance == null) {
            instance = new DeviceFinder(context);
        }
        return instance;
    }

    /**
     * Starts periodic scanning for Eddystone devices.
     */
    public void startScanning() {
        if (isScanning) {
            Log.i(TAG, "Scanning is already active.");
            return;
        }

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Log.i(TAG, "Bluetooth is not enabled. Cannot start scanning.");
            return;
        }

        isScanning = true;
        scheduleScanTask();
        Log.i(TAG, "Started periodic scanning for Eddystone devices.");
    }

    /**
     * Schedules the scan task to run periodically with a fixed delay.
     */
    private void scheduleScanTask() {
        scanScheduler.scheduleWithFixedDelay(() -> {
            if (!isScanning) {
                return;
            }

            // Start scanning
            startSingleScan();

            // Stop scanning after SCAN_DURATION_MS
            scanHandler.postDelayed(this::stopSingleScan, SCAN_DURATION_MS);
        }, 0, SCAN_DURATION_MS + SCAN_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Starts a single scan for Eddystone devices.
     */
    private void startSingleScan() {

        // avoid actions when we are receiving data
        if(Mutex.getInstance().isLocked()){
            return;
        }


        try {
            ScanFilter filter = new ScanFilter.Builder()
                    .setServiceUuid(EDDYSTONE_SERVICE_UUID)
                    .build();

            ScanSettings settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                    .build();

            bluetoothAdapter.getBluetoothLeScanner().startScan(
                    List.of(filter),
                    settings,
                    scanCallback
            );
            //Log.i(TAG, "Started single scan for Eddystone devices.");
        } catch (SecurityException e) {
            Log.i(TAG, "SecurityException while starting scan: " + e.getMessage());
        }

    }

    /**
     * Stops a single scan.
     */
    private void stopSingleScan() {
        try {
            bluetoothAdapter.getBluetoothLeScanner().stopScan(scanCallback);
            //Log.i(TAG, "Stopped single scan for Eddystone devices.");
        } catch (SecurityException e) {
            Log.i(TAG, "SecurityException while stopping scan: " + e.getMessage());
        }
    }

    /**
     * Stops periodic scanning for devices.
     */
    public void stopScanning() {
        if (!isScanning) {
            Log.i(TAG, "Scanning is not active.");
            return;
        }

        isScanning = false;
        scanScheduler.shutdown();
        stopSingleScan();
        Log.i(TAG, "Stopped periodic scanning for devices.");
    }

    /**
     * Checks if scanning is currently active.
     */
    public boolean isScanningActive() {
        return isScanning;
    }

    /**
     * Gets the up-to-date HashMap of discovered devices.
     */
    public HashMap<String, DeviceReachable> getDeviceMap() {
        return deviceMap;
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            processScanResult(result);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult result : results) {
                processScanResult(result);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.i(TAG, "Scan failed with error code: " + errorCode);
        }
    };

    /**
     * Processes a scan result and updates the device map.
     */
    private void processScanResult(ScanResult result) {

        if (result == null || result.getScanRecord() == null) {
            return;
        }

        // Throttle scan result processing
//        long timePassed = System.currentTimeMillis() - timeLastUpdated;
//        if (timePassed > 2000) {
//            timeLastUpdated = System.currentTimeMillis();
//        } else {
//            return;
//        }

        // reserve the bluetooth handler
        Mutex.getInstance().lock();

        byte[] serviceData;
        try{
        serviceData = result.getScanRecord().getServiceData(EDDYSTONE_SERVICE_UUID);
            if (serviceData == null) {
                Mutex.getInstance().unlock();
                return;
            }
        }catch(Exception e){
            Log.e(TAG, "Unable to get service data");
            Mutex.getInstance().unlock();
            return;
        }

        String deviceId = extractInstanceId(serviceData);
        if (deviceId.length() == 12) {
            deviceId = deviceId.substring(0, deviceId.length() - 6);
        }

        String namespaceId = extractNamespaceId(serviceData);

        DeviceReachable deviceFound = deviceMap.get(deviceId);
        if (deviceFound == null) {
            deviceFound = new DeviceReachable();
            deviceFound.setDeviceId(deviceId);
            deviceFound.setNamespaceId(namespaceId);
            deviceFound.setMacAddress(result.getDevice().getAddress());
            deviceFound.setRssi(result.getRssi());
            deviceMap.put(deviceId, deviceFound);
            sendProfileToEveryone(context);
            Log.i(TAG, "New Eddystone device found: " + deviceId + " at " + result.getDevice().getAddress());
            DeviceListing.getInstance().updateList(context);
        }

        deviceFound.setTimeLastFound(System.currentTimeMillis());
        deviceFound.setRssi(result.getRssi());
        deviceFound.setServiceData(serviceData);
        deviceFound.setMacAddress(result.getDevice().getAddress());

        // unlock it
        Mutex.getInstance().unlock();

        // remove old items
        long time = SCAN_INTERVAL_MS + SCAN_DURATION_MS + 1500;
        for(String deviceIdListed : deviceMap.keySet()){
            DeviceReachable deviceListed = deviceMap.get(deviceIdListed);
            long timeMax = deviceListed.getTimeLastFound() + time;
            if(timeMax < System.currentTimeMillis()){
                deviceMap.remove(deviceIdListed);
            }
        }
    }

//    /**
//     * Cleans up devices that haven't been seen for a while.
//     */
//    public void cleanupDisconnectedDevices() {
//        long currentTime = System.currentTimeMillis();
//        Iterator<Map.Entry<String, DeviceReachable>> iterator = deviceMap.entrySet().iterator();
//
//        while (iterator.hasNext()) {
//            Map.Entry<String, DeviceReachable> entry = iterator.next();
//            DeviceReachable device = entry.getValue();
//            if (currentTime - device.getTimeLastFound() > BEACON_TIMEOUT_MS) {
//                Log.i(TAG, "Removing disconnected device: " + entry.getKey());
//                iterator.remove();
//            }
//        }
//    }

    /**
     * Extracts the Namespace ID from service data.
     */
    private String extractNamespaceId(byte[] serviceData) {
        if (serviceData.length < 12) {
            return "Unknown";
        }
        return bytesToHex(serviceData, 2, 10);
    }

    /**
     * Extracts the Instance ID from service data.
     */
    private String extractInstanceId(byte[] serviceData) {
        if (serviceData.length < 18) {
            return "Unknown";
        }
        return bytesToHex(serviceData, 12, 6);
    }

    /**
     * Converts a byte array to a hexadecimal string.
     */
    private String bytesToHex(byte[] bytes, int offset, int length) {
        StringBuilder hexString = new StringBuilder();
        for (int i = offset; i < offset + length; i++) {
            hexString.append(String.format("%02X", bytes[i]));
        }
        return hexString.toString();
    }

    /**
     * Updates a device in the map.
     */
    public void update(DeviceReachable device) {
        deviceMap.put(device.getDeviceId(), device);
    }

    public DeviceReachable getDevice(String deviceId) {
        if(this.getDeviceMap().containsKey(deviceId)  == false){
            return null;
        }
        // return the device Id
        return this.getDeviceMap().get(deviceId);
    }
}
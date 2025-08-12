package offgrid.geogram.old.bluetooth_old.eddystone;

import static offgrid.geogram.old.bluetooth_old.BluetoothCentral.EDDYSTONE_SERVICE_UUID;

import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import java.nio.ByteBuffer;

import offgrid.geogram.old.old.old.GenerateDeviceId;
import offgrid.geogram.core.Log;
import offgrid.geogram.old.wifi.WiFiCommon;

public class EddyDeviceAdvertise {

    private static final String TAG = "EddyDeviceAdvertise";

    private static EddyDeviceAdvertise instance;

    private final Context context;
    private BluetoothLeAdvertiser advertiser;
    private AdvertiseCallback advertiseCallback;
    private boolean isAdvertising;

    private String namespaceId = "00000000000000000000";
    private String instanceId;

    // Default Advertise Settings
    private int advertiseMode = AdvertiseSettings.ADVERTISE_MODE_BALANCED;
    private int txPowerLevel = AdvertiseSettings.ADVERTISE_TX_POWER_HIGH;

    private EddyDeviceAdvertise(Context context) {
        this.context = context.getApplicationContext();
        initializeAdvertiser();
    }

    /**
     * Singleton access to the EddyDeviceAdvertise instance.
     */
    public static synchronized EddyDeviceAdvertise getInstance(Context context) {
        if (instance == null) {
            instance = new EddyDeviceAdvertise(context);
        }
        return instance;
    }

    private void initializeAdvertiser() {
        advertiser = context.getSystemService(Context.BLUETOOTH_SERVICE) instanceof android.bluetooth.BluetoothManager
                ? ((android.bluetooth.BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter().getBluetoothLeAdvertiser()
                : null;

        if (advertiser == null) {
            Log.i(TAG, "BLE advertiser is not available on this device.");
        } else {
            Log.i(TAG, "BLE advertiser initialized.");
        }
    }

    /**
     * Starts the Eddystone beacon advertisement.
     */
    public void startBeaconDevice() {
        if (isAdvertising) {
            Log.i(TAG, "Beacon is already advertising.");
            return;
        }

        if (advertiser == null) {
            Log.i(TAG, "BLE advertiser is not initialized. Cannot start beacon.");
            return;
        }

        if (!checkPermissions()) {
            Log.i(TAG, "Missing required permissions. Cannot start beacon.");
            return;
        }

        try {
            // setup the basic identifiers
            instanceId = GenerateDeviceId.generate(context);

            AdvertiseSettings settings = new AdvertiseSettings.Builder()
                    .setAdvertiseMode(advertiseMode)
                    .setTxPowerLevel(txPowerLevel)
                    .setConnectable(true)
                    .build();


            // add WiFi access and password
            if(WiFiCommon.ssid != null){
                namespaceId = EddystoneNamespaceGenerator.generateNamespaceId(WiFiCommon.ssid, WiFiCommon.passphrase);
            }


            AdvertiseData advertiseData = new AdvertiseData.Builder()
                    .addServiceUuid(EDDYSTONE_SERVICE_UUID)
                    .addServiceData(EDDYSTONE_SERVICE_UUID,
                            buildEddystoneUidFrame(namespaceId))
                    .setIncludeDeviceName(false)
                    .build();

            // what is the advertisement mode?
            Log.i(TAG, "Beacon advertise mode: "
                    + convertAdertiseModeToString(advertiseMode)
            );

            // what is the transmission power level?
            Log.i(TAG, "Beacon power level: "
                    + convertTxPowerLevelToString(txPowerLevel)
            );

            Log.i(TAG, "Beacon namespace ID: " + namespaceId);
            Log.i(TAG, "Device ID: " + instanceId);


            advertiseCallback = new AdvertiseCallback() {
                @Override
                public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                    super.onStartSuccess(settingsInEffect);
                    isAdvertising = true;
                    Log.i(TAG, "Beacon started successfully.");
                }

                @Override
                public void onStartFailure(int errorCode) {
                    super.onStartFailure(errorCode);
                    isAdvertising = false;
                    Log.i(TAG, "Failed to start beacon. Error code: " + errorCode);
                }
            };

            advertiser.startAdvertising(settings, advertiseData, advertiseCallback);
        } catch (SecurityException e) {
            Log.i(TAG, "SecurityException while starting beacon: " + e.getMessage());
        }
    }

    public boolean isAdvertising() {
        return isAdvertising;
    }

    /**
     * Stops the Eddystone beacon advertisement.
     */
    public void stopBeacon() {
        if (!isAdvertising) {
            Log.i(TAG, "Beacon is not currently advertising.");
            return;
        }

        if (advertiser == null || advertiseCallback == null) {
            Log.i(TAG, "BLE advertiser or callback is not initialized.");
            return;
        }

        if (!checkPermissions()) {
            Log.i(TAG, "Missing required permissions. Cannot stop beacon.");
            return;
        }

        try {
            advertiser.stopAdvertising(advertiseCallback);
            isAdvertising = false;
            Log.i(TAG, "Beacon stopped successfully.");
        } catch (SecurityException e) {
            Log.i(TAG, "SecurityException while stopping beacon: " + e.getMessage());
        }
    }

    /**
     * Restarts the beacon advertisement with updated data.
     */
    public void restartBeacon() {
        stopBeacon();
        startBeaconDevice();
    }

    /**
     * Updates the namespace ID for the beacon.
     */
    public void setNamespace(String namespace) {
        this.namespaceId = namespace;
        Log.i(TAG, "Namespace updated: " + namespaceId);
    }

    /**
     * Updates the instance ID for the beacon.
     */
    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
        Log.i(TAG, "Instance ID updated: " + instanceId);
    }

    /**
     * Sets the Advertise Mode dynamically.
     */
    public void setAdvertiseMode(int mode) {
        if (mode < AdvertiseSettings.ADVERTISE_MODE_LOW_POWER || mode > AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY) {
            Log.i(TAG, "Invalid advertise mode. Keeping current mode: " + advertiseMode);
            return;
        }
        this.advertiseMode = mode;
        Log.i(TAG, "Advertise mode updated to: " + advertiseMode);
    }

    /**
     * Sets the Transmission Power Level dynamically.
     */
    public void setTxPowerLevel(int powerLevel) {
        if (powerLevel < AdvertiseSettings.ADVERTISE_TX_POWER_ULTRA_LOW || powerLevel > AdvertiseSettings.ADVERTISE_TX_POWER_HIGH) {
            Log.i(TAG, "Invalid TX power level. Keeping current level: " + txPowerLevel);
            return;
        }
        this.txPowerLevel = powerLevel;
        Log.i(TAG, "TX power level updated to: " + txPowerLevel);
    }

    /**
     * Checks if the required permissions are granted.
     */
    private boolean checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // API 31 or higher
            boolean hasAdvertisePermission =
                    context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE)
                            == PackageManager.PERMISSION_GRANTED;

            if (!hasAdvertisePermission) {
                Log.i(TAG, "Missing BLUETOOTH_ADVERTISE permission.");
            }

            return hasAdvertisePermission;
        } else {
            // Older Android versions do not require runtime permissions for Bluetooth
            return true;
        }
    }

    /**
     * Converts the Advertise Mode value to a human-readable string.
     * @param value The Advertise Mode value.
     * @return A human-readable string representation of the Advertise Mode.
     */
    public static String convertTxPowerLevelToString(int value){
        return switch (value) {
            case AdvertiseSettings.ADVERTISE_TX_POWER_HIGH -> "High";
            case AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM -> "Medium";
            case AdvertiseSettings.ADVERTISE_TX_POWER_LOW -> "Low";
            case AdvertiseSettings.ADVERTISE_TX_POWER_ULTRA_LOW -> "Ultra Low";
            default -> "Unknown";
        };
    }

    public static String convertAdertiseModeToString(int value){
        return switch (value) {
            case AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY -> "Low latency";
            case AdvertiseSettings.ADVERTISE_MODE_BALANCED -> "Balanced";
            case AdvertiseSettings.ADVERTISE_MODE_LOW_POWER -> "Low power";
            default -> "Unknown";
        };
    }


    private byte[] buildEddystoneUidFrame(String namespaceId) {
        if (namespaceId.length() != 20) {
            throw new IllegalArgumentException("Namespace ID must be 20 hex characters.");
        }

        try {
            byte[] namespaceBytes = hexStringToByteArray(namespaceId);

            // Generate a unique Instance ID
            String deviceId = GenerateDeviceId.generate(context);
            byte[] instanceBytes = hexStringToByteArray(deviceId);

            ByteBuffer buffer = ByteBuffer.allocate(20);
            buffer.put((byte) 0x00); // Frame type: UID
            buffer.put((byte) 0x00); // TX power level (placeholder)
            buffer.put(namespaceBytes); // Namespace ID (10 bytes)
            buffer.put(instanceBytes); // Instance ID (6 bytes)
            buffer.put((byte) 0x00); // Reserved byte
            buffer.put((byte) 0x00); // Reserved byte

            return buffer.array();
        } catch (Exception e) {
            Log.e(TAG, "Error building Eddystone UID Frame: " + e.getMessage());
            throw e;
        }
    }

    private byte[] hexStringToByteArray(String hex) {
        int length = hex.length();
        byte[] data = new byte[length / 2];
        for (int i = 0; i < length; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

}

package offgrid.geogram.core.old;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.os.ParcelUuid;
import android.content.Context;
import android.content.pm.PackageManager;
import android.Manifest;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.UUID;

import offgrid.geogram.core.old.old.GenerateUUID;

/**
 * Handles Bluetooth beacon functionality, including generating a unique UUID and starting BLE advertisement.
 */
public class BluetoothBeacon {

    private static final String TAG = "BluetoothBeacon";
    private static final int REQUEST_CODE_PERMISSIONS = 1001;

    private BluetoothLeAdvertiser advertiser;
    private AdvertiseCallback advertiseCallback;

    /**
     * Checks if the necessary Bluetooth permissions are available.
     *
     * @param context The application context.
     * @return True if permissions are granted, false otherwise.
     */
    private boolean hasPermissions(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        ;
    }

    /**
     * Requests the necessary permissions if not already granted.
     *
     * @param context The application context.
     */
    private void requestPermissions(Context context) {
        ActivityCompat.requestPermissions(
                (android.app.Activity) context,
                new String[]{
                        Manifest.permission.BLUETOOTH,
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_ADVERTISE,
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                        Manifest.permission.BLUETOOTH_ADMIN
                },
                REQUEST_CODE_PERMISSIONS
        );
    }

    /**
     * Verifies if BLE is available and enabled on the device.
     *
     * @param context The application context.
     * @return True if BLE is supported and enabled, false otherwise.
     */
    private boolean isBleActive(Context context) {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            android.util.Log.e(TAG, "Bluetooth is not available or not enabled.");
            return false;
        }

        if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            android.util.Log.e(TAG, "BLE is not supported on this device.");
            return false;
        }

        android.util.Log.i(TAG, "BLE is active and supported.");
        return true;
    }

    /**
     * Generates a valid UUID string for BLE advertisement.
     *
     * @return A UUID string.
     */
    private String generateUUID() {
        try {
            // Generate a random 4-character idPub (valid hexadecimal)
            GenerateUUID.idPub = UUID.randomUUID().toString().replace("-", "").substring(0, 4);

            // Ensure idMisc is valid hexadecimal
            GenerateUUID.idMisc = UUID.randomUUID().toString().replace("-", "").substring(0, 16);

            // Validate idSSID and idPassword as hexadecimal
            if (!GenerateUUID.idSSID.matches("[0-9a-fA-F]{2}")) {
                GenerateUUID.idSSID = UUID.randomUUID().toString().replace("-", "").substring(0, 2);
            }
            if (!GenerateUUID.idPassword.matches("[0-9a-fA-F]{8}")) {
                GenerateUUID.idPassword = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            }

            // Assemble the UUID using GenerateUUID components
            String uuid = GenerateUUID.idApp + GenerateUUID.idProduct + GenerateUUID.idPub + GenerateUUID.idLocation
                    + GenerateUUID.idMisc + GenerateUUID.idSSID + GenerateUUID.idPassword;

            // Insert dashes to match UUID format (8-4-4-4-12)
            return uuid.substring(0, 8) + "-" + uuid.substring(8, 12) + "-" + uuid.substring(12, 16) + "-"
                    + uuid.substring(16, 20) + "-" + uuid.substring(20, 32);
        } catch (Exception e) {
            android.util.Log.e(TAG, "Error generating UUID: " + e.getMessage());
            return UUID.randomUUID().toString(); // Fallback UUID
        }
    }

    /**
     * Starts BLE advertising with the generated UUID.
     *
     * @param context The application context.
     */
    public void startAdvertising(Context context) {
        try {
            android.util.Log.i(TAG, "Starting Bluetooth advertising...");

            if (!hasPermissions(context)) {
                android.util.Log.w(TAG, "Permissions are missing. Requesting permissions...");
                requestPermissions(context);
                return;
            }

            if (!isBleActive(context)) {
                android.util.Log.e(TAG, "BLE is not active. Cannot start advertising.");
                return;
            }

            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            advertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
            if (advertiser == null) {
                android.util.Log.e(TAG, "BLE advertising is not supported on this device.");
                throw new UnsupportedOperationException("BLE advertising is not supported on this device.");
            }

            // Stop any ongoing advertisements to ensure a clean start
            stopAdvertising();

            // Generate a UUID for advertisement
            String uuidString = generateUUID();
            android.util.Log.i(TAG, "Generated UUID for advertising: " + uuidString);
            android.util.Log.i(TAG, "Bluetooth MAC Address: " + bluetoothAdapter.getAddress());
            android.util.Log.i(TAG, "Device Name: " + bluetoothAdapter.getName());
            ParcelUuid uuid = ParcelUuid.fromString(uuidString);

            // Create advertise settings
            AdvertiseSettings settings = new AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setConnectable(false)
                    .setTimeout(0)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                    .build();

            // Create advertise data
            AdvertiseData data = new AdvertiseData.Builder()
                    .setIncludeDeviceName(true) // Include the device name for discovery
                    .addServiceUuid(uuid)
                    .addManufacturerData(0xFFFF, new byte[]{0x01, 0x02, 0x03}) // Example manufacturer data
                    .build();

            // Define the callback
            advertiseCallback = new AdvertiseCallback() {
                @Override
                public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                    super.onStartSuccess(settingsInEffect);
                    android.util.Log.i(TAG, "Advertising started successfully.");
                }

                @Override
                public void onStartFailure(int errorCode) {
                    super.onStartFailure(errorCode);
                    android.util.Log.e(TAG, "Advertising failed with error code " + errorCode);
                }
            };

            // Start advertising
            advertiser.startAdvertising(settings, data, advertiseCallback);
        } catch (SecurityException e) {
            android.util.Log.e(TAG, "SecurityException - Permissions not granted or revoked at runtime.");
        }
    }

    /**
     * Stops BLE advertising.
     */
    public void stopAdvertising() {
        try {
            if (advertiser != null && advertiseCallback != null) {
                advertiser.stopAdvertising(advertiseCallback);
                android.util.Log.i(TAG, "Advertising stopped.");
            }
        } catch (SecurityException e) {
            android.util.Log.e(TAG, "Unable to stop advertising due to missing permissions.");
        }
    }
}

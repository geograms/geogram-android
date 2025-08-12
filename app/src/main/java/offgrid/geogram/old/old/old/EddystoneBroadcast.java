package offgrid.geogram.old.old.old;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;

import java.util.UUID;

import offgrid.geogram.core.Log;
import offgrid.geogram.core.PermissionsHelper;

/**
 * Permit the exchange of information between two bluetooth devices.
 */
public class EddystoneBroadcast {

    private static final String TAG = "BeaconCommunication";

    // UUID for the custom characteristic
    private static final UUID CUSTOM_CHARACTERISTIC_UUID = UUID.fromString("abcdef12-3456-7890-abcd-ef1234567890");

    /**
     * Write a text message to a Bluetooth device's custom characteristic.
     *
     * @param context  The application context.
     * @param macAddress The MAC address of the target Bluetooth device.
     * @param message   The message to write to the characteristic.
     */
    public static void writeToCharacteristic(Context context, String macAddress, String message) {
        // Check permissions before proceeding
        if (!PermissionsHelper.hasAllPermissions((android.app.Activity) context)) {
            Log.e(TAG, "Missing necessary permissions. Aborting operation.");
            return;
        }

        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Log.e(TAG, "Bluetooth is not enabled or not supported on this device.");
            return;
        }

        try {
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(macAddress);
            if (device == null) {
                Log.e(TAG, "Device with MAC address " + macAddress + " not found.");
                return;
            }

            device.connectGatt(context, false, new BluetoothGattCallback() {
                @Override
                public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                    if (newState == BluetoothGatt.STATE_CONNECTED) {
                        Log.i(TAG, "Connected to GATT server. Starting service discovery.");
                        gatt.discoverServices();
                    } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                        Log.e(TAG, "Disconnected from GATT server.");
                        gatt.close();
                    }
                }

                @Override
                public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        BluetoothGattService service = gatt.getService(CUSTOM_CHARACTERISTIC_UUID);
                        if (service != null) {
                            BluetoothGattCharacteristic characteristic =
                                    service.getCharacteristic(CUSTOM_CHARACTERISTIC_UUID);
                            if (characteristic != null) {
                                characteristic.setValue(message);
                                gatt.writeCharacteristic(characteristic);
                                Log.i(TAG, "Message written to characteristic: " + message);
                            } else {
                                Log.e(TAG, "Characteristic not found: " + CUSTOM_CHARACTERISTIC_UUID);
                            }
                        } else {
                            Log.e(TAG, "Service not found: " + CUSTOM_CHARACTERISTIC_UUID);
                        }
                    } else {
                        Log.e(TAG, "Service discovery failed with status: " + status);
                    }
                }

                @Override
                public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        Log.i(TAG, "Characteristic write successful.");
                    } else {
                        Log.e(TAG, "Characteristic write failed with status: " + status);
                    }
                    gatt.disconnect();
                }
            });
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException: Missing required permissions. " + e.getMessage());
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "IllegalArgumentException: Invalid MAC address provided. " + e.getMessage());
        }
    }
}

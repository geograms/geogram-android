package offgrid.geogram.old.bluetooth_old;

import static offgrid.geogram.util.BluetoothUtils.refreshDeviceCache;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.util.UUID;

import offgrid.geogram.old.bluetooth_old.other.comms.BlueQueueParcel;
import offgrid.geogram.old.bluetooth_old.other.comms.DataCallbackTemplate;
import offgrid.geogram.old.bluetooth_old.other.comms.Mutex;
import offgrid.geogram.core.Log;

public class Bluecomm {

    private static final String TAG = "Bluecomm";

    private static Bluecomm instance;

    private final Context context;
    private BluetoothGatt bluetoothGatt;

    // UUIDs for service and characteristic
    private static final UUID SERVICE_UUID = BluetoothCentral.UUID_SERVICE_WALKIETALKIE;
    private static final UUID CHARACTERISTIC_UUID = BluetoothCentral.UUID_CHARACTERISTIC_GENERAL;

    public static final int
            delayDiscoverServices = 1000,
            timeBetweenChecks = 1000,
            timeBetweenMessages = 1500,
            maxSizeOfMessages = 14,
            packageTimeToBeActive = 3000;

    private Bluecomm(Context context) {
        this.context = context.getApplicationContext();
        // start the queues when not started already
        BlueQueueReceiving.getInstance(context).start();
        BlueQueueSending.getInstance(context).start();
    }

    /**
     * Singleton access to the GetProfile instance.
     */
    public static synchronized Bluecomm getInstance(Context context) {
        if (instance == null) {
            instance = new Bluecomm(context);
        }
        return instance;
    }

    /**
     * Reads data from the custom characteristic on a specified device.
     *
     * @param macAddress The MAC address of the device to connect to.
     */
    public synchronized void getDataRead(String macAddress, DataCallbackTemplate callback) {
        BluetoothGatt localGatt = null;
        try {
            if (!checkPermissions()) {
                callback.onDataError("Missing required permissions.");
                return;
            }

            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
                callback.onDataError("Bluetooth is not available or enabled.");
                return;
            }

            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(macAddress);
            if (device == null) {
                callback.onDataError("Device not found.");
                return;
            }

            Mutex.getInstance().waitUntilUnlocked();

            localGatt = device.connectGatt(context, false, new BluetoothGattCallback() {
                @Override
                public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                    super.onConnectionStateChange(gatt, status, newState);
                    if (newState == BluetoothGatt.STATE_CONNECTED) {
                        Log.i(TAG, "Connected to GATT server.");
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            Mutex.getInstance().lock();
                            try {
                                gatt.discoverServices();
                            } catch (Exception e) {
                                Log.e(TAG, "Exception during service discovery: " + e.getMessage());
                                closeGatt(gatt);
                                callback.onDataError("Service discovery failed: " + e.getMessage());
                            } finally {
                                Mutex.getInstance().unlock();
                            }
                        }, 1000);
                    } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                        Log.i(TAG, "Disconnected from GATT server.");
                        closeGatt(gatt);
                    }
                }

                @Override
                public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                    super.onServicesDiscovered(gatt, status);
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        BluetoothGattService service = gatt.getService(SERVICE_UUID);
                        if (service == null) {
                            Log.i(TAG, "Service not found: " + SERVICE_UUID);
                            callback.onDataError("Service not found.");
                            closeGatt(gatt);
                            return;
                        }

                        BluetoothGattCharacteristic characteristic = service.getCharacteristic(CHARACTERISTIC_UUID);
                        if (characteristic == null) {
                            Log.i(TAG, "Characteristic not found: " + CHARACTERISTIC_UUID);
                            callback.onDataError("Characteristic not found.");
                            closeGatt(gatt);
                            return;
                        }

                        boolean success = gatt.readCharacteristic(characteristic);
                        if (!success) {
                            Log.i(TAG, "Failed to initiate characteristic read");
                            callback.onDataError("Failed to initiate characteristic read");
                            closeGatt(gatt);
                        }
                    } else {
                        Log.i(TAG, "Failed to discover services. Status: " + status);
                        callback.onDataError("Failed to discover services.");
                        closeGatt(gatt);
                    }
                }

                @Override
                public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                    super.onCharacteristicRead(gatt, characteristic, status);
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        String data = new String(characteristic.getValue());
                        Log.i(TAG, "Characteristic read successfully: " + data);
                        callback.onDataSuccess(data);
                    } else {
                        Log.i(TAG, "Failed to read characteristic. Status: " + status);
                        callback.onDataError("Failed to read characteristic.");
                    }
                    closeGatt(gatt);
                }
            }, BluetoothDevice.TRANSPORT_LE);

            bluetoothGatt = localGatt; // Only assign after successful connection
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException while connecting to device: " + e.getMessage());
            callback.onDataError("Security exception occurred.");
            closeGatt(localGatt);
        } catch (Exception e) {
            Log.e(TAG, "Unexpected exception: " + e.getMessage());
            callback.onDataError("Unexpected error occurred.");
            closeGatt(localGatt);
        }
    }

    /**
     * Just send a write event to a device without waiting for the reply
     * This is useful for cases like broadcasting messages to devices
     */
    public synchronized void writeData(BlueQueueParcel item) {
        if (item == null) {
            return;
        }
        writeData(item.getMacAddress(), item.getData(), new DataCallbackTemplate() {
            @Override
            public void onDataSuccess(String data) {
                Log.i(TAG, "Data sent: " + data);
            }
            @Override
            public void onDataError(String errorMessage) {
                Log.e(TAG, "Error sending data: " + errorMessage);
                // mark the parcel as not sent successfully
                //Log.i(TAG, "Adding this parcel again on the queue to be sent");
                //BlueQueueSending.getInstance(context).addQueueToSend(item);
            }
        });
    }

    /**
     * Just send a write event to a device without waiting for the reply
     * This is useful for cases like broadcasting messages to devices
     */
    public synchronized void writeData(String macAddress, String data) {
        if (data == null) {
            return;
        }
        // avoid sending duplicates
        if (BlueQueueSending.getInstance(context).isAlreadyOnQueueToSend(data, macAddress)) {
            return;
        }
        BlueQueueParcel item = new BlueQueueParcel(macAddress, data);
        BlueQueueSending.getInstance(context).addQueueToSend(item);
    }

    /**
     * Writes data to the custom characteristic on a specified device without waiting for a response.
     *
     * @param macAddress The MAC address of the device to connect to.
     * @param data       The data to write.
     * @param callback   Callback to handle success or failure of the write operation.
     */
    public synchronized void writeData(String macAddress, String data, DataCallbackTemplate callback) {
        BluetoothGatt localGatt = null;
        try {
            if (data == null) {
                Log.e(TAG, "Null data received for write operation to " + macAddress);
                callback.onDataError("Null data received");
                return;
            }

            if (!checkPermissions()) {
                callback.onDataError("Missing required permissions.");
                return;
            }

            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
                callback.onDataError("Bluetooth is not available or enabled.");
                return;
            }

            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(macAddress);
            if (device == null) {
                callback.onDataError("Device not found.");
                return;
            }

            Mutex.getInstance().waitUntilUnlocked();

            localGatt = device.connectGatt(context, false, new BluetoothGattCallback() {
                @Override
                public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                    super.onConnectionStateChange(gatt, status, newState);
                    if (newState == BluetoothGatt.STATE_CONNECTED) {
                        Log.i(TAG, "Connected to GATT server.");
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            Mutex.getInstance().lock();
                            try {
                                gatt.discoverServices();
                            } catch (Exception e) {
                                Log.e(TAG, "Exception during service discovery: " + e.getMessage());
                                closeGatt(gatt);
                                callback.onDataError("Service discovery failed: " + e.getMessage());
                            } finally {
                                Mutex.getInstance().unlock();
                            }
                        }, delayDiscoverServices);
                    } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                        Log.i(TAG, "Disconnected from GATT server.");
                        closeGatt(gatt);
                    }
                }

                @Override
                public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                    Mutex.getInstance().waitUntilUnlocked();
                    super.onServicesDiscovered(gatt, status);
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        BluetoothGattService service = gatt.getService(SERVICE_UUID);
                        if (service == null) {
                            Log.i(TAG, "Service not found: " + SERVICE_UUID);
                            callback.onDataError("Service not found.");
                            closeGatt(gatt);
                            return;
                        }

                        BluetoothGattCharacteristic characteristic = service.getCharacteristic(CHARACTERISTIC_UUID);
                        if (characteristic == null) {
                            Log.i(TAG, "Characteristic not found: " + CHARACTERISTIC_UUID);
                            callback.onDataError("Characteristic not found.");
                            closeGatt(gatt);
                            return;
                        }

                        // Check if the characteristic supports write
                        int properties = characteristic.getProperties();
                        if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE) == 0 &&
                                (properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) == 0) {
                            Log.e(TAG, "Characteristic does not support write operations.");
                            callback.onDataError("Characteristic does not support write operations.");
                            closeGatt(gatt);
                            return;
                        }

                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    int result = gatt.writeCharacteristic(characteristic, data.getBytes(),
                                            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);

                                    if (result != BluetoothGatt.GATT_SUCCESS) {
                                        Log.e(TAG, "GATT write failed with status: " + result);
                                        callback.onDataError("GATT write failed with status: " + result);
                                    } else {
                                        Log.i(TAG, "Write operation initiated successfully.");
                                        callback.onDataSuccess("Write operation initiated.");
                                    }
                                } else {
                                    characteristic.setValue(data.getBytes());
                                    characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                                    boolean success = gatt.writeCharacteristic(characteristic);
                                    if (!success) {
                                        Log.i(TAG, "Failed to initiate write operation.");
                                        callback.onDataError("Failed to initiate write operation.");
                                    } else {
                                        Log.i(TAG, "Write operation initiated successfully.");
                                        callback.onDataSuccess("Write operation initiated.");
                                    }
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Exception during write operation: " + e.getMessage());
                                callback.onDataError("Exception during write operation: " + e.getMessage());
                            } finally {
                                closeGatt(gatt);
                            }
                        }, 300);
                    } else {
                        Log.i(TAG, "Failed to discover services. Status: " + status);
                        callback.onDataError("Failed to discover services.");
                        closeGatt(gatt);
                    }
                }
            }, BluetoothDevice.TRANSPORT_LE);

            bluetoothGatt = localGatt;
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException while connecting to device: " + e.getMessage());
            callback.onDataError("Security exception occurred.");
            closeGatt(localGatt);
        } catch (Exception e) {
            Log.e(TAG, "Unexpected exception: " + e.getMessage());
            callback.onDataError("Unexpected error occurred.");
            closeGatt(localGatt);
        }
    }

    private void closeGatt(BluetoothGatt gatt) {
        if (gatt == null) {
            Log.i(TAG, "BluetoothGatt is already null, skipping close operation.");
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12+
            if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "Missing BLUETOOTH_CONNECT permission. Cannot close GATT.");
                return;
            }
        }

        try {
            Log.i(TAG, "Disconnecting GATT...");
            gatt.disconnect();

            // Optional: Refresh cache before closing to prevent stale services
            if (refreshDeviceCache(gatt)) {
                Log.i(TAG, "Device cache refreshed successfully.");
            } else {
                Log.i(TAG, "Failed to refresh device cache.");
            }

            Log.i(TAG, "Closing GATT...");
            gatt.close();
            Log.i(TAG, "GATT closed successfully.");
        } catch (Exception e) {
            Log.e(TAG, "Exception while closing GATT: " + e.getMessage());
        }
    }



    /**
     * Checks if the required permissions are granted.
     */
    private boolean checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // API 31 or higher
            boolean hasConnectPermission =
                    context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
                            == PackageManager.PERMISSION_GRANTED;

            if (!hasConnectPermission) {
                Log.i(TAG, "Missing BLUETOOTH_CONNECT permission.");
            }

            return hasConnectPermission;
        } else {
            // Older Android versions do not require runtime permissions for Bluetooth
            return true;
        }
    }


}

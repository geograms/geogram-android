package offgrid.geogram.core.old.old;

import static offgrid.geogram.core.old.old.GenerateMessage.generateShareSSID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.ParcelUuid;

import android.content.BroadcastReceiver;
import android.content.Intent;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.os.Parcelable;

import offgrid.geogram.core.Log;

public class EddystoneBeacon {

    private static final String TAG = "EddystoneBeacon";

    // Eddystone Service UUID
    private static final ParcelUuid EDDYSTONE_SERVICE_UUID =
            ParcelUuid.fromString(BeaconDefinitions.EDDYSTONE_SERVICE_UUID);

    // Custom Service and Characteristic UUIDs
    private static final UUID CUSTOM_SERVICE_UUID =
            UUID.fromString(BeaconDefinitions.CUSTOM_SERVICE_UUID);
    private static final UUID CUSTOM_CHARACTERISTIC_UUID =
            UUID.fromString(BeaconDefinitions.CUSTOM_CHARACTERISTIC_UUID);

    private final Context context;
    private BluetoothAdapter bluetoothAdapter;
    private android.bluetooth.le.BluetoothLeAdvertiser advertiser;
    private AdvertiseCallback advertiseCallback;
    private BluetoothGattServer gattServer;
    private final List<BluetoothDevice> connectedDevices = new ArrayList<>();
    private boolean isAdvertising = false;

    // Debounce for broadcast messages
    private static final long MIN_BROADCAST_INTERVAL_MS = 5000;
    private long lastBroadcastTime = 0;

    public EddystoneBeacon(Context context) {
        this.context = context;
        initializeBluetooth();
    }

    public void setDeviceName(String newName) {
        if (bluetoothAdapter == null) {
            Log.e(TAG, "BluetoothAdapter is not initialized. Cannot change device name.");
            return;
        }

        if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Missing BLUETOOTH_CONNECT permission. Cannot change device name.");
            return;
        }

        try {
            boolean result = bluetoothAdapter.setName(newName);
            if (result) {
                Log.i(TAG, "Device name changed to: " + newName);
            } else {
                Log.e(TAG, "Failed to change device name.");
            }
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException while changing device name: " + e.getMessage());
        }
    }

    private void initializeBluetooth() {
        BluetoothManager bluetoothManager =
                (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager == null) {
            Log.e(TAG, "Bluetooth Manager is not available.");
            return;
        }

        bluetoothAdapter = bluetoothManager.getAdapter();

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Log.e(TAG, "Bluetooth is not enabled or not supported on this device.");
            return;
        }

        advertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
        if (advertiser == null) {
            Log.e(TAG, "BLE advertising is not supported on this device.");
        } else {
            Log.i(TAG, "Bluetooth LE Advertiser initialized successfully.");
            Log.i(TAG, "Device MAC Address: " + bluetoothAdapter.getAddress());
        }

        if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Missing BLUETOOTH_CONNECT permission. GATT server cannot be initialized.");
            return;
        }

        try {
            gattServer = bluetoothManager.openGattServer(context, new GattServerCallback());
            if (gattServer == null) {
                Log.e(TAG, "Failed to open GATT server.");
            } else {
                setupGattServer();
            }
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException while opening GATT server: " + e.getMessage());
        }
    }

    private void setupGattServer() {
        if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Missing BLUETOOTH_CONNECT permission. Cannot add GATT service.");
            return;
        }

        try {
            BluetoothGattService customService = new BluetoothGattService(
                    CUSTOM_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);

            BluetoothGattCharacteristic customCharacteristic = new BluetoothGattCharacteristic(
                    CUSTOM_CHARACTERISTIC_UUID,
                    BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_WRITE,
                    BluetoothGattCharacteristic.PERMISSION_READ | BluetoothGattCharacteristic.PERMISSION_WRITE
            );

            customService.addCharacteristic(customCharacteristic);

            gattServer.addService(customService);
            Log.i(TAG, "GATT server setup complete. Custom service added.");
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException while adding GATT service: " + e.getMessage());
        }
    }

    public void startAdvertising(String namespaceId) {
        if (isAdvertising) {
            Log.i(TAG, "Advertising is already started.");
            return;
        }

        if (advertiser == null) {
            Log.e(TAG, "Advertiser is null. Cannot start advertising.");
            return;
        }

        if (!checkPermissions()) {
            Log.e(TAG, "Bluetooth permissions are not granted.");
            return;
        }

        try {
            AdvertiseSettings settings = new AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                    .setConnectable(true)
                    .build();

            AdvertiseData advertiseData = new AdvertiseData.Builder()
                    .addServiceUuid(EDDYSTONE_SERVICE_UUID)
                    .addServiceData(EDDYSTONE_SERVICE_UUID, buildEddystoneUidFrame(namespaceId))
                    .setIncludeDeviceName(false)
                    .build();

            advertiseCallback = new AdvertiseCallback() {
                @Override
                public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                    Log.i(TAG, "Eddystone beacon started successfully.");
                }

                @Override
                public void onStartFailure(int errorCode) {
                    Log.e(TAG, "Failed to start Eddystone beacon. Error code: " + errorCode);
                }
            };

            advertiser.startAdvertising(settings, advertiseData, advertiseCallback);
            isAdvertising = true;
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException while starting advertising: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error while starting advertising: " + e.getMessage());
        }
    }

    public void stopAdvertising() {
        if (!isAdvertising) {
            Log.i(TAG, "Advertising is already stopped.");
            return;
        }

        if (advertiser != null && advertiseCallback != null) {
            try {
                advertiser.stopAdvertising(advertiseCallback);
                Log.i(TAG, "Eddystone beacon stopped.");
            } catch (SecurityException e) {
                Log.e(TAG, "SecurityException while stopping advertising: " + e.getMessage());
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error while stopping advertising: " + e.getMessage());
            }
        }

        if (gattServer != null) {
            try {
                gattServer.close();
                Log.i(TAG, "GATT server closed.");
            } catch (SecurityException e) {
                Log.e(TAG, "SecurityException while closing GATT server: " + e.getMessage());
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error while closing GATT server: " + e.getMessage());
            }
        }
        isAdvertising = false;
    }




    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public boolean broadcastMessage(String message) {
        if (gattServer == null) {
            Log.i(TAG, "GATT server is not initialized.");
            return false;
        }

        if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "Missing BLUETOOTH_CONNECT permission. Cannot broadcast message.");
            return false;
        }

        BluetoothGattService service = gattServer.getService(CUSTOM_SERVICE_UUID);
        if (service == null) {
            Log.i(TAG, "Custom service not found.");
            return false;
        }

        BluetoothGattCharacteristic characteristic = service.getCharacteristic(CUSTOM_CHARACTERISTIC_UUID);
        if (characteristic == null) {
            Log.i(TAG, "Custom characteristic not found.");
            return false;
        }

        characteristic.setValue(message.getBytes());
        Log.i(TAG, "Broadcasting message: " + message);

        // Run the scanning and broadcasting on a background thread
        executorService.submit(() -> {
            try {
                BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
                    Log.i(TAG, "Bluetooth is not enabled or not supported on this device.");
                    return;
                }

                bluetoothAdapter.startDiscovery();

                // Use a BroadcastReceiver for scanning
                BroadcastReceiver receiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        String action = intent.getAction();
                        if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                            Parcelable[] uuids = intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID);

                            if (device != null && uuids != null) {
                                try {
                                    if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                                        for (Parcelable parcelUuid : uuids) {
                                            if (parcelUuid instanceof ParcelUuid) {
                                                ParcelUuid uuid = (ParcelUuid) parcelUuid;
                                                if (uuid.equals(EDDYSTONE_SERVICE_UUID)) {
                                                    if (!connectedDevices.contains(device)) {
                                                        connectedDevices.add(device);
                                                        Log.i(TAG, "Eddystone device found and added: " + device.getAddress());
                                                    }
                                                    break;
                                                }
                                            }
                                        }
                                    } else {
                                        Log.i(TAG, "Missing BLUETOOTH_CONNECT permission. Cannot determine device services.");
                                    }
                                } catch (SecurityException e) {
                                    Log.i(TAG, "SecurityException while accessing device services: " + e.getMessage());
                                }
                            }
                        }
                    }
                };

                // Register the receiver and wait for the scan
                IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
                context.registerReceiver(receiver, filter);

                Thread.sleep(8000); // 8 seconds to collect scan results

                // Unregister the receiver
                context.unregisterReceiver(receiver);

                // Broadcast the message to all connected Eddystone devices
                if (connectedDevices.isEmpty()) {
                    Log.i(TAG, "No Eddystone devices found after scanning. Broadcast aborted.");
                    return;
                }

                for (BluetoothDevice device : connectedDevices) {
                    try {
                        boolean notificationSent = gattServer.notifyCharacteristicChanged(device, characteristic, false);
                        if (notificationSent) {
                            Log.i(TAG, "Message broadcasted to Eddystone device: " + device.getAddress());
                        } else {
                            Log.i(TAG, "Failed to broadcast message to Eddystone device: " + device.getAddress());
                        }
                    } catch (SecurityException e) {
                        Log.i(TAG, "SecurityException while broadcasting message to Eddystone device: " + device.getAddress() + " " + e.getMessage());
                    } catch (Exception e) {
                        Log.i(TAG, "Unexpected error broadcasting message to Eddystone device: " + device.getAddress() + " " + e.getMessage());
                    }
                }
            } catch (InterruptedException e) {
                Log.i(TAG, "Thread interrupted during broadcast: " + e.getMessage());
            }
        });

        return true;
    }







    private boolean checkPermissions() {
        return context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
                context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    private class GattServerCallback extends BluetoothGattServerCallback {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "Missing BLUETOOTH_CONNECT permission for connection state change.");
                return;
            }

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                if (!connectedDevices.contains(device)) {
                    connectedDevices.add(device);
                    Log.i(TAG, "Device connected and added to list: " + device.getAddress());
                } else {
                    Log.i(TAG, "Device already in connectedDevices list: " + device.getAddress());
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connectedDevices.remove(device);
                Log.i(TAG, "Device disconnected and removed from list: " + device.getAddress());
            }
        }


        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
            if (CUSTOM_CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                String message = generateShareSSID();
                try {
                    gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, message.getBytes());
                    Log.i(TAG, "Read request from " + device.getAddress());
                } catch (SecurityException e) {
                    Log.e(TAG, "SecurityException while sending read response: " + e.getMessage());
                }
            }
        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            if (CUSTOM_CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                String received = new String(value);
                Log.i(TAG, "Write request from " + device.getAddress() + ": " + received);
                if (responseNeeded) {
                    try {
                        gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null);
                    } catch (SecurityException e) {
                        Log.e(TAG, "SecurityException while sending write response: " + e.getMessage());
                    }
                }
            }
        }
    }

    private byte[] buildEddystoneUidFrame(String namespaceId) {
        if (namespaceId.length() != 20) {
            throw new IllegalArgumentException("Namespace ID must be 20 hex characters.");
        }

        try {
            byte[] namespaceBytes = hexStringToByteArray(namespaceId);

            // Generate a unique Instance ID
            BeaconDefinitions.deviceId = GenerateDeviceId.generate(context);
            byte[] instanceBytes = hexStringToByteArray(BeaconDefinitions.deviceId);

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

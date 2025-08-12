package offgrid.geogram.old.bluetooth_old.classic;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class BluetoothHandler {

    private static final String TAG = "BluetoothHandler";
    private static final UUID APP_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdef0"); // Random UUID

    private static BluetoothHandler instance;

    private final BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private OutputStream outputStream;
    private InputStream inputStream;
    private final BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();
    private Thread messageSenderThread;

    private BluetoothHandler() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Device does not support Bluetooth.");
        }
    }

    public static synchronized BluetoothHandler getInstance() {
        if (instance == null) {
            instance = new BluetoothHandler();
        }
        return instance;
    }

    public void startDiscovery(Activity activity) {
        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.BLUETOOTH_SCAN}, 1);
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            activity.startActivityForResult(enableBtIntent, 1);
        }
        if (!bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.startDiscovery();
        }
    }

    public void stopDiscovery(Activity activity) {
        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.BLUETOOTH_SCAN}, 1);
            return;
        }

        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }
    }

    public void connectToDevice(String macAddress, Activity activity) {
        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 1);
            return;
        }

        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(macAddress);

        new Thread(() -> {
            try {
                bluetoothSocket = device.createRfcommSocketToServiceRecord(APP_UUID);
                if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                    bluetoothAdapter.cancelDiscovery();
                }
                bluetoothSocket.connect();
                outputStream = bluetoothSocket.getOutputStream();
                inputStream = bluetoothSocket.getInputStream();

                Log.i(TAG, "Connected to " + device.getName());

                startListeningForMessages();
                startSendingMessages();

            } catch (IOException e) {
                Log.e(TAG, "Connection failed: " + e.getMessage());
                closeConnection();
            }
        }).start();
    }

    public void sendMessage(String message) {
        try {
            messageQueue.put(message);
        } catch (InterruptedException e) {
            Log.e(TAG, "Message queue interrupted: " + e.getMessage());
        }
    }

    private void startListeningForMessages() {
        new Thread(() -> {
            byte[] buffer = new byte[1024];
            int bytes;
            while (bluetoothSocket != null && bluetoothSocket.isConnected()) {
                try {
                    bytes = inputStream.read(buffer);
                    String receivedMessage = new String(buffer, 0, bytes);
                    Log.i(TAG, "Received message: " + receivedMessage);
                } catch (IOException e) {
                    Log.e(TAG, "Error reading from input stream: " + e.getMessage());
                    closeConnection();
                    break;
                }
            }
        }).start();
    }

    private void startSendingMessages() {
        messageSenderThread = new Thread(() -> {
            while (bluetoothSocket != null && bluetoothSocket.isConnected()) {
                try {
                    String message = messageQueue.take();
                    outputStream.write(message.getBytes());
                    Log.i(TAG, "Message sent: " + message);
                } catch (IOException | InterruptedException e) {
                    Log.e(TAG, "Error sending message: " + e.getMessage());
                    closeConnection();
                    break;
                }
            }
        });
        messageSenderThread.start();
    }

    public void closeConnection() {
        try {
            if (bluetoothSocket != null) {
                bluetoothSocket.close();
            }
            if (messageSenderThread != null) {
                messageSenderThread.interrupt();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error closing connection: " + e.getMessage());
        }
    }
}

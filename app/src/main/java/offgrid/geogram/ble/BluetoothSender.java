package offgrid.geogram.ble;

import static offgrid.geogram.ble.BluetoothCentral.advertiseDurationMillis;
import static offgrid.geogram.ble.BluetoothCentral.maxSizeOfMessages;
import static offgrid.geogram.ble.BluetoothCentral.selfIntervalSeconds;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.ParcelUuid;

import androidx.annotation.RequiresApi;

import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;

import offgrid.geogram.core.Log;
import offgrid.geogram.events.EventControl;
import offgrid.geogram.events.EventType;

@SuppressLint("ObsoleteSdkInt")
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class BluetoothSender {

    // Self-advertising
    private String selfMessage = null; //">CR7BBQ " + System.currentTimeMillis();
    private static final String TAG = "BluetoothSender";
    private static final UUID SERVICE_UUID = UUID.fromString("0000FEAA-0000-1000-8000-00805F9B34FB");
    private static BluetoothSender instance;
    private final Context context;
    private final Handler handler = new Handler();
    private BluetoothLeAdvertiser advertiser;
    private AdvertiseCallback advertiseCallback;
    private boolean isRunning = false;
    private boolean isPaused = false;

    private final Queue<String> messageQueue = new LinkedList<>();
    private boolean isSending = false;

    private final Runnable selfAdvertiseTask = new Runnable() {
        @Override
        public void run() {
            if (isRunning && !isPaused && selfMessage != null) {
                //selfMessage = ">KO6JZI " + System.currentTimeMillis();
                sendMessage(selfMessage);
                handler.postDelayed(this, selfIntervalSeconds * 1000L);
            }
        }
    };

    private BluetoothSender(Context context) {
        this.context = context.getApplicationContext();
        BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (manager != null && manager.getAdapter() != null) {
            advertiser = manager.getAdapter().getBluetoothLeAdvertiser();
        }
    }

    public static synchronized BluetoothSender getInstance(Context context) {
        if (instance == null) {
            instance = new BluetoothSender(context);
        }
        return instance;
    }

    public void start() {
        if (isRunning) return;
        isRunning = true;
        isPaused = false;
        Log.i(TAG, "Bluetooth sender started.");
        tryToSendNext();

        // Start self-advertise loop
        if (selfMessage != null) {
            handler.post(selfAdvertiseTask);
        }
    }

    public void stop() {
        isRunning = false;
        isPaused = false;
        stopAdvertising();
        messageQueue.clear();
        handler.removeCallbacks(selfAdvertiseTask);
        Log.i(TAG, "BluetoothSender stopped and queue cleared.");
    }

    public void pause() {
        if (!isPaused) {
            isPaused = true;
            stopAdvertising();
            handler.removeCallbacks(selfAdvertiseTask);
            Log.i(TAG, "BluetoothSender paused.");
        }
    }

    public void resume() {
        if (isPaused) {
            isPaused = false;
            Log.i(TAG, "BluetoothSender resumed.");
            tryToSendNext();
            if (selfMessage != null) {
                handler.post(selfAdvertiseTask);
            }
        }
    }

    public void sendMessage(String message) {
        if (message == null || message.isEmpty()) return;
        BluetoothMessage msg = new BluetoothMessage("CR7BBQ-5", "ANY", message);
        sendMessage(msg);
    }
    public void sendMessage(BluetoothMessage msg) {
        // initiate the event for anyone listening
        EventControl.startEvent(EventType.BLE_BROADCAST_SENT, msg);
        Log.i(TAG, "Queued message: " + msg.getOutput());
        for(String parcel : msg.getMessageBox().values()){
            if(parcel.startsWith(">") == false){
                parcel = ">" + parcel;
            }
            messageQueue.offer(parcel);
        }

        if (isRunning && !isPaused) {
            tryToSendNext();
        }
        // need to repeat for sending the last parcel
        if (isRunning && !isPaused) {
            tryToSendNext();
        }
    }

    private void tryToSendNext() {
        if (!isRunning || isPaused || isSending || messageQueue.isEmpty()) {
            return;
        }

        final String message = messageQueue.peek();
        if (message == null) return;

        if (!hasAdvertisePermission()) {
            Log.i(TAG, "Missing BLUETOOTH_ADVERTISE permission. Cannot send BLE message.");
            return;
        }

        AdvertiseData data = buildAdvertiseData(message);
        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(false)
                .build();

        isSending = true;

        AdvertiseCallback callback = new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                Log.i(TAG, "Started advertising message: " + message);

                handler.postDelayed(() -> {
                    stopAdvertising(); // Stop current ad
                    String removed = messageQueue.poll(); // Remove message from queue
                    Log.i(TAG, "Message sent and removed from queue: " + removed);
                    isSending = false;
                    BluetoothListener.getInstance(context).resumeListening();
                    tryToSendNext(); // Try next message
                }, advertiseDurationMillis);
            }

            @Override
            public void onStartFailure(int errorCode) {
                Log.i(TAG, "Failed to advertise message. Error code: " + errorCode);
                stopAdvertising(); // Attempt to clean up
                isSending = false;
                tryToSendNext(); // Try next message
            }
        };

        advertiseCallback = callback; // Keep reference to stop it later
        BluetoothListener.getInstance(context).pauseListening();

        try {
            advertiser.startAdvertising(settings, data, callback);

            // Failsafe: reset isSending in case no callback is triggered
            handler.postDelayed(() -> {
                if (isSending) {
                    Log.i(TAG, "Failsafe triggered: No BLE callback within expected time.");
                    stopAdvertising();
                    isSending = false;
                    tryToSendNext();
                }
            }, advertiseDurationMillis + 1000);

        } catch (SecurityException e) {
            Log.i(TAG, "SecurityException while advertising: " + e.getMessage());
            isSending = false;
        }
    }



    public void setSelfMessage(String message) {
        this.selfMessage = message;
        if (isRunning && !isPaused) {
            handler.removeCallbacks(selfAdvertiseTask);
            handler.post(selfAdvertiseTask);
            Log.i(TAG, "Self message set to: " + message);
        }
    }

    public void setSelfIntervalSeconds(int seconds) {
        if (seconds <= 0) return;
        selfIntervalSeconds = seconds;
        Log.i(TAG, "Self-advertise interval set to: " + seconds + " seconds");

        if (isRunning && !isPaused && selfMessage != null) {
            handler.removeCallbacks(selfAdvertiseTask);
            handler.post(selfAdvertiseTask);
        }
    }


    private void stopAdvertising() {
        if (advertiser != null && advertiseCallback != null) {
            if (!hasAdvertisePermission()) {
                Log.i(TAG, "Missing BLUETOOTH_ADVERTISE permission. Cannot stop BLE advertiser.");
                return;
            }
            try {
                advertiser.stopAdvertising(advertiseCallback);
            } catch (SecurityException e) {
                Log.i(TAG, "SecurityException while stopping advertiser: " + e.getMessage());
            }
        }
        advertiseCallback = null;
        isSending = false;
    }

    private AdvertiseData buildAdvertiseData(String message) {

        if(message.length() >= 24){
            message = message.substring(0, 23);
        }


        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
//        if (bytes.length > 24) {
//            Log.i(TAG, "Message too long, truncating to 24 bytes.");
//            byte[] truncated = new byte[24];
//            System.arraycopy(bytes, 0, truncated, 0, 24);
//            bytes = truncated;
//        }

        return new AdvertiseData.Builder()
                .addServiceUuid(new ParcelUuid(SERVICE_UUID))
                .addServiceData(new ParcelUuid(SERVICE_UUID), bytes)
                .setIncludeDeviceName(false)
                .build();
    }

    private boolean hasAdvertisePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }
}

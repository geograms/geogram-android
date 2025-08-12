package offgrid.geogram.old;

import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.ParcelUuid;

import java.nio.ByteBuffer;

import offgrid.geogram.core.Log;

public class EddystoneBeacon_unused {

    private int intervalSeconds = 10;
    private int durationSeconds = 5;

    private static final String TAG = "EddystoneBeacon";
    public static final String EDDYSTONE_SERVICE_ID = "0000FEAA-0000-1000-8000-00805F9B34FB";

    private static EddystoneBeacon_unused instance;

    private final Context context;
    private BluetoothLeAdvertiser advertiser;
    private AdvertiseCallback advertiseCallback;
    private boolean isAdvertising = false;
    private boolean isPaused = false;


    private final Handler handler = new Handler();

    private EddystoneBeacon_unused(Context context) {
        this.context = context.getApplicationContext();
        BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (manager != null && manager.getAdapter() != null) {
            advertiser = manager.getAdapter().getBluetoothLeAdvertiser();
        }
    }

    public static synchronized EddystoneBeacon_unused getInstance(Context context) {
        if (instance == null) {
            instance = new EddystoneBeacon_unused(context);
        }
        return instance;
    }

    public void setIntervalSeconds(int seconds) {
        this.intervalSeconds = seconds;
    }

    public void setDurationSeconds(int seconds) {
        this.durationSeconds = seconds;
    }

    public void startBeaconing() {
        isPaused = false;
        stopAdvertise(); // clear any existing advertisement

        Runnable advertiseTask = new Runnable() {
            @Override
            public void run() {
                if (isPaused) return;
                Log.i(TAG, "Starting Eddystone Beacon");
                startAdvertise();
                handler.postDelayed(EddystoneBeacon_unused.this::stopAdvertise, durationSeconds * 1000L);
                handler.postDelayed(this, intervalSeconds * 1000L);
            }
        };
        handler.post(advertiseTask);
    }

    public void stopBeaconing() {
        isPaused = false;
        handler.removeCallbacksAndMessages(null);
        stopAdvertise();
    }

    public void pauseBeaconing() {
        if (!isPaused) {
            isPaused = true;
            handler.removeCallbacksAndMessages(null);
            stopAdvertise();
        }
    }

    public void resumeBeaconing() {
        if (isPaused) {
            isPaused = false;
            startBeaconing();
        }
    }

    private void startAdvertise() {
        if (advertiser == null || isAdvertising || !hasAdvertisePermission()) return;

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(false)
                .build();

        byte[] serviceData = buildEddystoneUidFrame("00000000000000000000", "A1B2C3D4E5F6");

        AdvertiseData data = new AdvertiseData.Builder()
                .addServiceUuid(ParcelUuid.fromString(EDDYSTONE_SERVICE_ID))
                .addServiceData(ParcelUuid.fromString(EDDYSTONE_SERVICE_ID), serviceData)
                .setIncludeDeviceName(false)
                .build();

        advertiseCallback = new AdvertiseCallback() { };

        try {
            advertiser.startAdvertising(settings, data, advertiseCallback);
            isAdvertising = true;
        } catch (SecurityException e) {
            isAdvertising = false;
            e.printStackTrace();
        }
    }

    private void stopAdvertise() {
        if (advertiser != null && isAdvertising && advertiseCallback != null) {
            try {
                advertiser.stopAdvertising(advertiseCallback);
                Log.i(TAG, "Stopped the Eddystone Beacon");
            } catch (SecurityException e) {
                e.printStackTrace();
            }
            isAdvertising = false;
        }
    }

    private boolean hasAdvertisePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true; // Permission not required on older versions
    }

    private byte[] buildEddystoneUidFrame(String namespaceHex, String instanceHex) {
        ByteBuffer buffer = ByteBuffer.allocate(20);
        buffer.put((byte) 0x00); // Frame Type: UID
        buffer.put((byte) 0x00); // Calibrated Tx Power

        buffer.put(hexStringToByteArray(namespaceHex)); // 10 bytes
        buffer.put(hexStringToByteArray(instanceHex));  // 6 bytes

        buffer.put((byte) 0x00); // RFU
        buffer.put((byte) 0x00); // RFU

        return buffer.array();
    }

    private byte[] hexStringToByteArray(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2)
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        return data;
    }
}

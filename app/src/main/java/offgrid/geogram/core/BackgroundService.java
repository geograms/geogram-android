package offgrid.geogram.core;

import static offgrid.geogram.MainActivity.activity;
import static offgrid.geogram.core.Central.server;
import static offgrid.geogram.core.Messages.log;
import static offgrid.geogram.wifi.WiFiCommon.peers;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.net.wifi.p2p.WifiP2pDevice;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import offgrid.geogram.MainActivity;
import offgrid.geogram.R;
import offgrid.geogram.bluetooth.eddystone.DeviceListing;
import offgrid.geogram.bluetooth.BluetoothCentral;
import offgrid.geogram.server.SimpleSparkServer;
import offgrid.geogram.wifi.WiFiDirectAdvertiser;
import offgrid.geogram.wifi.old.WiFiDirectDiscovery;
import offgrid.geogram.wifi.WifiScanner;

public class BackgroundService extends Service {

    boolean startWifi = false;

    private static final String TAG = "offgrid-service";
    private static final String CHANNEL_ID = "ForegroundServiceChannel";
    private Handler handler;
    private Runnable logTask;

    private WiFiDirectDiscovery wifiDiscover = null;

    // how long between scans
    private final long intervalSeconds = 10;

    private boolean wifiDiscoverEnabled = true;
    private boolean hasNecessaryPermissions = false;

    @Override
    public void onCreate() {
        super.onCreate();
        log(TAG, "Geogram is starting");
        log(TAG, "Creating the background service");

        // Load settings
        Central.getInstance().loadSettings(this.getApplicationContext());

        createNotificationChannel();
        initializePermissions();


        // Start the web server
//        serverThread = new Thread(new SimpleSparkServer());
//        serverThread.start();

        server = new SimpleSparkServer();
        Thread serverThread = new Thread(server);
        // Start the server
        serverThread.start();


        // start the Wi-Fi hotspot
        if(startWifi) {
            startWiFiAdvertise();
            // start Wi-Fi discovery
            WifiScanner.getInstance(this.getApplicationContext());
            WifiScanner.getInstance(this.getApplicationContext()).startScanning();
        }

        // Initialize Bluetooth services
        startBluetooth();

        // Initialize periodic task
        handler = new Handler();
        logTask = new Runnable() {
            @Override
            public void run() {
                if (hasNecessaryPermissions) {
                    runBackgroundTask();
                } else {
                    log(TAG, "Missing permissions, cannot proceed");
                }
                handler.postDelayed(this, intervalSeconds * 1000); // Repeat every interval
            }
        };
        handler.post(logTask);

        log(TAG, "Geogram was launched");
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Foreground Service Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            serviceChannel.setDescription("Offgrid phone, looking for data and connections");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
                log(TAG, "Notification channel created");
            } else {
                log(TAG, "NotificationManager is null");
            }
        }
    }

    private void initializePermissions() {
        if (activity == null) {
            log(TAG, "Activity is null, skipping permission initialization");
            return;
        }
        hasNecessaryPermissions = PermissionsHelper.requestPermissionsIfNecessary(activity);

        if (!hasNecessaryPermissions) {
            log(TAG, "Permissions are not granted yet. Waiting for user response.");
            PermissionsHelper.requestPermissionsIfNecessary(activity);
        }
    }

    /**
     * Starts the Bluetooth beacon
     */
    private void startBluetooth() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            log(TAG, "Bluetooth is not supported on this device.");
            return;
        } else if (!bluetoothAdapter.isEnabled()) {
            log(TAG, "Bluetooth is disabled. Please turn it on");
            return;
        }
        BluetoothCentral.getInstance(this).start();
    }

    private void startWiFiDiscover() {
        if (wifiDiscoverEnabled) {
            log(TAG, "WiFi discovery initialized");
            wifiDiscover = new WiFiDirectDiscovery(this);
        } else {
            log(TAG, "WiFi discovery disabled");
        }
    }

    private void startWiFiAdvertise() {
        WiFiDirectAdvertiser.getInstance(this.getApplicationContext()).startAdvertising();
        log(TAG, "WiFi advertise initialized");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Geogram Service")
                .setContentText("Service is running in the background")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();

        startForeground(1, notification);

        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        log(TAG, "Service destroyed");

        if (handler != null) {
            handler.removeCallbacks(logTask);
        }

        BluetoothCentral.getInstance(this).stop();
        WiFiDirectAdvertiser.getInstance(this.getApplicationContext()).stopAdvertising();

    }

    private void runBackgroundTask() {
        if (wifiDiscover != null && wifiDiscoverEnabled) {
            wifiDiscover.startDiscovery(1);
            wifiDiscover.stopDiscovery();
            listPeers();
        }

        DeviceListing.getInstance().updateList(this.getApplicationContext());
    }

    private void listPeers() {
        if (peers == null) {
            return;
        }
        for (WifiP2pDevice device : peers.getDeviceList()) {
            log(TAG, "Found P2P available: " + device.deviceName);
        }
    }
}

package offgrid.geogram.core;

import static offgrid.geogram.core.Central.server;
import static offgrid.geogram.core.Messages.log;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import offgrid.geogram.MainActivity;
import offgrid.geogram.R;
import offgrid.geogram.ble.BluetoothCentral;
import offgrid.geogram.server.SimpleSparkServer;

public class BackgroundService extends Service {

    private static final String TAG = "offgrid-service";
    private static final String CHANNEL_ID = "ForegroundServiceChannel";

    private Handler handler;
    private Runnable logTask;

    private final long intervalSeconds = 10;

    @Override
    public void onCreate() {
        super.onCreate();
        log(TAG, "Geogram is starting");
        log(TAG, "Creating the background service");

        // Load settings
        Central.getInstance().loadSettings(this.getApplicationContext());

        createNotificationChannel();

        // Check permissions, do not request
        boolean hasPermissions = PermissionsHelper.hasAllPermissions(getApplicationContext());
        if (!hasPermissions) {
            log(TAG, "Missing runtime permissions — Bluetooth and Wi-Fi will not start.");
        }

        // Start background web server
        server = new SimpleSparkServer();
        new Thread(server).start();

        // Start Bluetooth stack if allowed
        if (hasPermissions) {
            startBluetooth();
        }

        // Start recurring background task
        handler = new Handler();
        logTask = new Runnable() {
            @Override
            public void run() {
                if (PermissionsHelper.hasAllPermissions(getApplicationContext())) {
                    runBackgroundTask();
                } else {
                    log(TAG, "Permissions still missing — skipping task.");
                }
                handler.postDelayed(this, intervalSeconds * 1000);
            }
        };
        handler.post(logTask);

        log(TAG, "Geogram background service started.");
    }

    @SuppressLint("ObsoleteSdkInt")
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
                log(TAG, "Notification channel created.");
            } else {
                log(TAG, "Failed to create notification channel.");
            }
        }
    }

    private void startBluetooth() {
        BluetoothCentral.getInstance(this);
    }

    private void runBackgroundTask() {
        // Add background recurring logic here if needed
        // e.g., updating BLE state, handling queues, telemetry, etc.
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (!PermissionsHelper.hasAllPermissions(getApplicationContext())) {
            log(TAG, "Missing required location permissions — cannot start foreground service.");
            stopSelf(); // Prevent crash
            return START_NOT_STICKY;
        }

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

        try {
            startForeground(1, notification);
        } catch (SecurityException e) {
            log(TAG, "SecurityException when starting foreground service: " + e.getMessage());
            stopSelf(); // Prevent crash loop
            return START_NOT_STICKY;
        }

        return START_STICKY;
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        log(TAG, "Service destroyed");

        if (handler != null) {
            handler.removeCallbacks(logTask);
        }

        // Optional: cleanup BLE or Wi-Fi
        // BluetoothCentral.getInstance(this).stop();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

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
import offgrid.geogram.database.DatabaseLocations;
import offgrid.geogram.database.DatabaseMessages;
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

        // start the databases
        startDatabases();

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

    private void startDatabases() {
        DatabaseMessages.getInstance().init(this.getApplicationContext());
        DatabaseLocations.get().init(getApplicationContext());
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
        // Fetch new messages in background
        fetchNewMessages();
    }

    private void fetchNewMessages() {
        // Run in background thread to avoid blocking
        new Thread(() -> {
            try {
                offgrid.geogram.settings.SettingsUser settings = Central.getInstance().getSettings();
                if (settings == null) {
                    return;
                }

                String callsign = settings.getCallsign();
                String nsec = settings.getNsec();
                String npub = settings.getNpub();

                // Check if user has valid credentials
                if (callsign == null || nsec == null || npub == null) {
                    return;
                }

                // Get conversation list
                java.util.List<String> peerIds = offgrid.geogram.api.GeogramMessagesAPI.getConversationList(callsign, nsec, npub);

                log(TAG, "Fetching messages for " + peerIds.size() + " conversations");

                // Fetch messages for each conversation
                for (String peerId : peerIds) {
                    try {
                        String markdown = offgrid.geogram.api.GeogramMessagesAPI.getConversationMessages(callsign, peerId, nsec, npub);

                        // Save markdown to cache - UI will parse when needed
                        offgrid.geogram.database.DatabaseConversations.getInstance()
                            .saveConversationMessages(peerId, markdown);

                        log(TAG, "Fetched and cached messages for: " + peerId);
                    } catch (Exception e) {
                        // Log errors but continue with other conversations
                        log(TAG, "Error fetching messages for " + peerId + ": " + e.getMessage());
                    }
                }

            } catch (Exception e) {
                log(TAG, "Error in background message fetch: " + e.getMessage());
            }
        }).start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // MUST call startForeground() immediately to avoid crash
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        boolean hasPermissions = PermissionsHelper.hasAllPermissions(getApplicationContext());
        String contentText = hasPermissions
                ? "Service is running in the background"
                : "Waiting for permissions...";

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Geogram Service")
                .setContentText(contentText)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();

        try {
            startForeground(1, notification);
        } catch (SecurityException e) {
            log(TAG, "SecurityException when starting foreground service: " + e.getMessage());
            stopSelf();
            return START_NOT_STICKY;
        }

        // Now check permissions after we've called startForeground()
        if (!hasPermissions) {
            log(TAG, "Service started but missing permissions. Waiting for user to grant them.");
            // Don't stop the service - let it wait for permissions
            // The recurring task in onCreate will check permissions periodically
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

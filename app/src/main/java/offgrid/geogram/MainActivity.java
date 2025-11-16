package offgrid.geogram;

import static offgrid.geogram.core.Messages.log;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.navigation.NavigationView;

import java.lang.ref.WeakReference;

import offgrid.geogram.apps.loops.PingDevice;
import offgrid.geogram.apps.loops.UpdatedCoordinates;
import offgrid.geogram.core.Art;
import offgrid.geogram.core.BackgroundService;
import offgrid.geogram.core.Central;
import offgrid.geogram.core.Log;
import offgrid.geogram.core.PermissionsHelper;
import offgrid.geogram.database.DatabaseConversations;
import offgrid.geogram.database.DatabaseMessages;
import offgrid.geogram.devices.EventConnected;
import offgrid.geogram.devices.ConnectionType;
import offgrid.geogram.devices.DeviceManager;
import offgrid.geogram.devices.DeviceType;
import offgrid.geogram.devices.EventDeviceUpdated;
import offgrid.geogram.events.EventControl;
import offgrid.geogram.events.EventType;
import offgrid.geogram.fragments.AboutFragment;
import offgrid.geogram.fragments.BackupFragment;
import offgrid.geogram.fragments.CollectionsFragment;
import offgrid.geogram.fragments.ConnectionsFragment;
import offgrid.geogram.fragments.DebugFragment;
import offgrid.geogram.fragments.DevicesFragment;
import offgrid.geogram.fragments.DevicesWithinReachFragment;
import offgrid.geogram.fragments.MessagesFragment;
import offgrid.geogram.fragments.RelayFragment;
import offgrid.geogram.p2p.DeviceRelayClient;
import offgrid.geogram.p2p.DeviceRelayChecker;
import offgrid.geogram.settings.SettingsFragment;
import offgrid.geogram.util.BatteryOptimizationHelper;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    // --- Leak-safe singleton accessor (WeakReference) ---
    private static volatile WeakReference<MainActivity> sInstance = new WeakReference<>(null);

    /** Returns the current activity instance if alive; else null. */
    public static @Nullable MainActivity getInstance() {
        return sInstance.get();
    }

    /** Convenience: get the ListView if the Activity is alive; else null. */
    public static @Nullable ListView getBeaconsViewIfAlive() {
        MainActivity a = getInstance();
        return (a == null) ? null : a.beacons;
    }
    // ----------------------------------------------------

    // Keep this non-static to avoid holding UI across process lifetime
    public ListView beacons;
    // This flag can stay static; it doesn't hold context or views.
    private static boolean wasCreatedBefore = false;
    // Track if activity is currently in foreground
    private boolean isInForeground = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        sInstance = new WeakReference<>(this); // publish current instance

        // Initialize file logging for debugging
        Log.initFileLogging(this);

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);

        if (PermissionsHelper.hasAllPermissions(this)) {
            initializeApp();
        } else {
            PermissionsHelper.requestPermissionsIfNecessary(this);
        }
    }

    @Override
    protected void onDestroy() {
        // Stop battery monitor
        offgrid.geogram.battery.BatteryMonitor.getInstance(this).stop();

        // Clear the weak ref if it points to this instance
        MainActivity cur = sInstance.get();
        if (cur == this) {
            sInstance.clear();
            sInstance = new WeakReference<>(null);
        }
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        isInForeground = true; // App is now in foreground

        if (!PermissionsHelper.hasAllPermissions(this)) {
            // If returning from settings and permissions still not granted, show dialog again
            if (wasCreatedBefore) {
                showPermissionDeniedDialog();
            } else {
                PermissionsHelper.requestPermissionsIfNecessary(this);
            }
        } else if (!wasCreatedBefore) {
            initializeApp();
        }

        // Handle intent extras (e.g., from notification tap)
        handleIntent(getIntent());
    }

    @Override
    protected void onPause() {
        super.onPause();
        isInForeground = false; // App is going to background
    }

    /**
     * Handle intent extras, such as opening chat from notification
     */
    private void handleIntent(Intent intent) {
        if (intent != null && intent.getBooleanExtra("open_chat", false)) {
            // Open chat fragment (clear back stack first)
            FragmentManager fragmentManager = getSupportFragmentManager();
            fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
            loadNearbyFragment();
            // Clear the extra so we don't reopen on next resume
            intent.removeExtra("open_chat");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (PermissionsHelper.handlePermissionResult(requestCode, permissions, grantResults)) {
            initializeApp();
        } else {
            handlePermissionDenied(permissions, grantResults);
        }
    }

    private void handlePermissionDenied(@NonNull String[] permissions, @NonNull int[] grantResults) {
        boolean permanentlyDenied = false;
        for (int i = 0; i < permissions.length; i++) {
            if (grantResults[i] == PackageManager.PERMISSION_DENIED &&
                    !shouldShowRequestPermissionRationale(permissions[i])) {
                permanentlyDenied = true;
                break;
            }
        }

        if (permanentlyDenied) {
            showPermissionDeniedDialog();
        } else {
            showPermissionRequiredDialog();
        }
    }

    private void showPermissionRequiredDialog() {
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Permissions Required")
                .setMessage("Geogram requires Bluetooth and Location permissions to function. The app cannot proceed without these permissions.")
                .setCancelable(false)
                .setPositiveButton("Grant Permissions", (d, which) -> {
                    PermissionsHelper.requestPermissionsIfNecessary(this);
                })
                .setNegativeButton("Exit", (d, which) -> {
                    finish();
                })
                .show();

        // Set button text colors to white for better readability
        android.widget.Button positiveButton = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE);
        android.widget.Button negativeButton = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE);
        if (positiveButton != null) {
            positiveButton.setTextColor(getResources().getColor(R.color.white, null));
        }
        if (negativeButton != null) {
            negativeButton.setTextColor(getResources().getColor(R.color.white, null));
        }
    }

    private void showPermissionDeniedDialog() {
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Permissions Denied")
                .setMessage("Some permissions are permanently denied. Please enable Bluetooth and Location permissions in App Settings for Geogram to work.")
                .setCancelable(false)
                .setPositiveButton("Open Settings", (d, which) -> {
                    Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(android.net.Uri.fromParts("package", getPackageName(), null));
                    startActivity(intent);
                })
                .setNegativeButton("Exit", (d, which) -> {
                    finish();
                })
                .show();

        // Set button text colors to white for better readability
        android.widget.Button positiveButton = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE);
        android.widget.Button negativeButton = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE);
        if (positiveButton != null) {
            positiveButton.setTextColor(getResources().getColor(R.color.white, null));
        }
        if (negativeButton != null) {
            negativeButton.setTextColor(getResources().getColor(R.color.white, null));
        }
    }

    private void initializeApp() {
        // Double-check permissions before initializing
        if (!PermissionsHelper.hasAllPermissions(this)) {
            Log.e(TAG, "Cannot initialize app without permissions");
            return;
        }

        Log.i(TAG, "Initializing the app...");

        // Initialize DeviceManager with context for relay sync
        DeviceManager.getInstance().initialize(this);

        beacons = findViewById(R.id.lv_beacons);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        setupNavigationDrawer();
        setupBackPressedHandler();
        setupEvents();
        setupLoops();
        setupBatteryMonitor();
        setupDeviceRelay();

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        checkBluetoothStatus();

        // Initialize device count badge
        updateDeviceCount();

        // Initialize relay count badge
        updateRelayCount();

        // Initialize chat count badge
        updateChatCount();

        if (wasCreatedBefore) {
            // On subsequent calls, just reload the fragment (database already initialized)
            loadNearbyFragment();
            return;
        }

        log("Geogram", Art.logo1());

        // Initialize settings and databases before starting service and loading fragment
        Central.getInstance().loadSettings(getApplicationContext());
        DatabaseMessages.getInstance().init(getApplicationContext());
        DatabaseConversations.getInstance().init(getApplicationContext());
        offgrid.geogram.database.DatabaseLocations.get().init(getApplicationContext());
        offgrid.geogram.database.DatabaseDevices.get().init(getApplicationContext());

        // Load device history from database
        offgrid.geogram.devices.DeviceManager.getInstance().loadFromDatabase();

        startBackgroundService();
        BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(this);
        wasCreatedBefore = true;

        // Load the Nearby chat fragment after settings and database are initialized
        loadNearbyFragment();

        // add a dummy connection for test purposes
        //addDummyConnection();
    }

    private void setupLoops() {
        UpdatedCoordinates.getInstance().start(this);
        PingDevice.getInstance().start();
        // Start WiFi device discovery for local network communication
        offgrid.geogram.wifi.WiFiDiscoveryService.getInstance(this).start();
    }

    private void setupBatteryMonitor() {
        // Find battery footer views
        View batteryFooter = findViewById(R.id.battery_footer);
        if (batteryFooter == null) {
            Log.w(TAG, "Battery footer not found");
            return;
        }

        TextView batteryTimeText = batteryFooter.findViewById(R.id.tv_battery_time_remaining);

        if (batteryTimeText == null) {
            Log.w(TAG, "Battery footer views not found");
            return;
        }

        // Initialize battery monitor
        offgrid.geogram.battery.BatteryMonitor batteryMonitor =
            offgrid.geogram.battery.BatteryMonitor.getInstance(this);

        // Set up listener for battery updates
        batteryMonitor.setListener((currentLevel, estimatedTimeRemaining) -> {
            // Update UI on main thread
            runOnUiThread(() -> {
                // Hide text when calculating
                if ("Calculating...".equals(estimatedTimeRemaining)) {
                    batteryFooter.setVisibility(View.GONE);
                } else {
                    batteryFooter.setVisibility(View.VISIBLE);
                    batteryTimeText.setText("Device can run for " + estimatedTimeRemaining);
                }
            });
        });

        // Start monitoring
        batteryMonitor.start();

        Log.i(TAG, "Battery monitor initialized");
    }

    private void setupDeviceRelay() {
        // Initialize and start device relay client
        DeviceRelayClient relayClient = DeviceRelayClient.getInstance(this);
        relayClient.start();

        // Initialize and start relay status checker
        DeviceRelayChecker relayChecker = DeviceRelayChecker.getInstance(this);
        relayChecker.start();

        Log.i(TAG, "Device relay client and checker initialized");
    }

    private void setupEvents() {
        // add the action to the event
        EventControl.addEvent(EventType.DEVICE_UPDATED,
                new EventDeviceUpdated(TAG + "-device_updated")
        );
    }

    private void addDummyConnection() {
        EventConnected event = new EventConnected(ConnectionType.BLE, "123", "456", "789");
        String callsign = "CR7BBQ";
        DeviceManager.getInstance().addNewLocationEvent(callsign, DeviceType.HT_PORTABLE, event);
    }

    private void setupNavigationDrawer() {
        DrawerLayout drawerLayout = findViewById(R.id.drawer_layout);
        NavigationView navigationView = findViewById(R.id.navigation_view);

        ImageButton btnSettings = findViewById(R.id.btn_settings);
        btnSettings.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        // Chat button
        ImageButton btnChat = findViewById(R.id.btn_chat);
        btnChat.setOnClickListener(v -> {
            // Clear back stack and return to nearby chat
            FragmentManager fragmentManager = getSupportFragmentManager();
            fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
            loadNearbyFragment();
        });

        // Device counter button
        ImageButton btnDevices = findViewById(R.id.btn_devices);
        btnDevices.setOnClickListener(v -> {
            FragmentManager fragmentManager = getSupportFragmentManager();
            FragmentTransaction transaction = fragmentManager.beginTransaction();
            transaction.replace(R.id.fragment_container, new DevicesWithinReachFragment()).addToBackStack(null);
            transaction.commit();
        });

        // Messages button
        ImageButton btnMessages = findViewById(R.id.btn_messages);
        btnMessages.setOnClickListener(v -> {
            FragmentManager fragmentManager = getSupportFragmentManager();
            FragmentTransaction transaction = fragmentManager.beginTransaction();
            transaction.replace(R.id.fragment_container, new MessagesFragment()).addToBackStack(null);
            transaction.commit();
        });

        // Relay button
        ImageButton btnRelay = findViewById(R.id.btn_relay);
        btnRelay.setOnClickListener(v -> {
            FragmentManager fragmentManager = getSupportFragmentManager();
            FragmentTransaction transaction = fragmentManager.beginTransaction();
            transaction.replace(R.id.fragment_container, new RelayFragment()).addToBackStack(null);
            transaction.commit();
        });

        // Collections button
        ImageButton btnCollections = findViewById(R.id.btn_collections);
        btnCollections.setOnClickListener(v -> {
            FragmentManager fragmentManager = getSupportFragmentManager();
            FragmentTransaction transaction = fragmentManager.beginTransaction();
            transaction.replace(R.id.fragment_container, new CollectionsFragment()).addToBackStack(null);
            transaction.commit();
        });

        navigationView.setNavigationItemSelectedListener(item -> {
            FragmentManager fragmentManager = getSupportFragmentManager();
            FragmentTransaction transaction = fragmentManager.beginTransaction();

            if (item.getItemId() == R.id.nav_settings) {
                transaction.replace(R.id.fragment_container, SettingsFragment.getInstance()).addToBackStack(null);
            } else if (item.getItemId() == R.id.nav_connections) {
                transaction.replace(R.id.fragment_container, new ConnectionsFragment()).addToBackStack(null);
            } else if (item.getItemId() == R.id.nav_backup) {
                transaction.replace(R.id.fragment_container, new BackupFragment()).addToBackStack(null);
            // Relay menu removed - relay accessible via main action bar relay button
            // } else if (item.getItemId() == R.id.nav_relay) {
            //     transaction.replace(R.id.fragment_container, new RelayFragment()).addToBackStack(null);
            } else if (item.getItemId() == R.id.nav_debug) {
                transaction.replace(R.id.fragment_container, new DebugFragment()).addToBackStack(null);
            } else if (item.getItemId() == R.id.nav_about) {
                transaction.replace(R.id.fragment_container, new AboutFragment()).addToBackStack(null);
            }

            transaction.commit();
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });
    }

    private void setupBackPressedHandler() {
        this.getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                FragmentManager fragmentManager = getSupportFragmentManager();
                if (fragmentManager.getBackStackEntryCount() > 0) {
                    fragmentManager.popBackStack();
                } else {
                    finish();
                }
            }
        });
    }

    @SuppressLint("ObsoleteSdkInt")
    private void startBackgroundService() {
        Intent serviceIntent = new Intent(this, BackgroundService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            log(TAG, "Starting BackgroundService as a foreground service");
            startForegroundService(serviceIntent);
        } else {
            log(TAG, "Starting BackgroundService as a normal service");
            startService(serviceIntent);
        }
    }

    private void checkBluetoothStatus() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not supported on this device", Toast.LENGTH_LONG).show();
        } else if (!bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Bluetooth is disabled. Please enable it", Toast.LENGTH_LONG).show();
        }
    }

    private void loadNearbyFragment() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.fragment_container, Central.getInstance().broadcastChatFragment);
        transaction.commit();
    }

    /**
     * Update the device count badge.
     * Only counts devices heard in the last 5 minutes.
     * Call this method whenever devices are added/removed.
     */
    public void updateDeviceCount() {
        TextView deviceCountBadge = findViewById(R.id.tv_device_count);
        if (deviceCountBadge == null) return;

        // Count only devices heard in the last 5 minutes (300000 ms)
        long now = System.currentTimeMillis();
        long fiveMinutesAgo = now - 300000;

        int activeDeviceCount = 0;
        for (offgrid.geogram.devices.Device device : DeviceManager.getInstance().getDevicesSpotted()) {
            long lastSeen = device.latestTimestamp();
            if (lastSeen > fiveMinutesAgo) {
                activeDeviceCount++;
            }
        }

        deviceCountBadge.setText(String.valueOf(activeDeviceCount));

        // Update badge background color
        if (activeDeviceCount == 0) {
            // Grey when no active devices
            deviceCountBadge.setBackgroundResource(R.drawable.badge_background_grey);
        } else {
            // Green when active devices are nearby
            deviceCountBadge.setBackgroundResource(R.drawable.badge_background_green);
        }
    }

    /**
     * Update the relay message count badge.
     * Shows the total number of messages in the relay (inbox + outbox).
     * Call this method whenever relay messages are added/removed.
     */
    public void updateRelayCount() {
        TextView relayCountBadge = findViewById(R.id.tv_relay_count);
        if (relayCountBadge == null) return;

        try {
            offgrid.geogram.relay.RelayStorage storage = new offgrid.geogram.relay.RelayStorage(this);
            offgrid.geogram.relay.RelaySettings settings = new offgrid.geogram.relay.RelaySettings(this);

            // Count messages in inbox and outbox
            int inboxCount = storage.getMessageCount("inbox");
            int outboxCount = storage.getMessageCount("outbox");
            int totalCount = inboxCount + outboxCount;

            relayCountBadge.setText(String.valueOf(totalCount));

            // Update badge background color based on relay status
            if (!settings.isRelayEnabled()) {
                // Grey when relay is disabled
                relayCountBadge.setBackgroundResource(R.drawable.badge_background_grey);
            } else if (totalCount == 0) {
                // Grey when no messages
                relayCountBadge.setBackgroundResource(R.drawable.badge_background_grey);
            } else {
                // Green when relay is active with messages
                relayCountBadge.setBackgroundResource(R.drawable.badge_background_green);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating relay count: " + e.getMessage());
            relayCountBadge.setText("0");
        }
    }

    /**
     * Update the chat message count badge.
     * Shows the number of unread messages in geochat.
     * Call this method when new messages arrive or when user opens chat.
     */
    public void updateChatCount() {
        TextView chatCountBadge = findViewById(R.id.tv_chat_count);
        if (chatCountBadge == null) return;

        try {
            // Count unread messages that were NOT written by me
            int unreadCount = 0;
            for (offgrid.geogram.apps.chat.ChatMessage message : offgrid.geogram.database.DatabaseMessages.getInstance().getMessages()) {
                if (!message.isWrittenByMe() && !message.isRead()) {
                    unreadCount++;
                }
            }

            if (unreadCount > 0) {
                chatCountBadge.setText(String.valueOf(unreadCount));
                chatCountBadge.setVisibility(android.view.View.VISIBLE);
                // Red background for unread messages
                chatCountBadge.setBackgroundResource(R.drawable.badge_background_red);
            } else {
                // Hide badge when no unread messages
                chatCountBadge.setVisibility(android.view.View.GONE);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating chat count: " + e.getMessage());
            chatCountBadge.setVisibility(android.view.View.GONE);
        }
    }

    /**
     * Check if the activity is currently in the foreground
     * @return true if activity is visible to user, false if in background
     */
    public boolean isInForeground() {
        return isInForeground;
    }

    /**
     * Show or hide the top action bar.
     * Main screens (Chat, Messages, Devices list) should show it.
     * Detail screens (Device Profile, Settings, etc.) should hide it.
     */
    public void setTopActionBarVisible(boolean visible) {
        android.widget.LinearLayout topActionBar = findViewById(R.id.top_action_bar);
        if (topActionBar != null) {
            topActionBar.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }
}

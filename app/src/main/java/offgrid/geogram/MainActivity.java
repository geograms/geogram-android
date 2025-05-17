package offgrid.geogram;

import static offgrid.geogram.core.Messages.log;

import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;

import offgrid.geogram.bluetooth.broadcast.BroadcastChatFragment;
import offgrid.geogram.core.Art;
import offgrid.geogram.core.BackgroundService;
import offgrid.geogram.core.Log;
import offgrid.geogram.core.PermissionsHelper;
import offgrid.geogram.fragments.AboutFragment;
import offgrid.geogram.fragments.DebugFragment;
import offgrid.geogram.fragments.NetworksFragment;
import offgrid.geogram.settings.SettingsFragment;
import offgrid.geogram.util.BatteryOptimizationHelper;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    public static MainActivity activity = null;
    public static ListView beacons = null;
    //private FloatingActionButton btnAdd;
    private static boolean wasCreatedBefore = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        activity = this;

        // Request necessary permissions
        if (!PermissionsHelper.requestPermissionsIfNecessary(this)) {
            Log.e(TAG, "Permissions are not granted yet. Waiting for user response.");
            return; // Defer initialization until permissions are granted
        }

        // ask to use the dark theme as default
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);


        // load all the beacons we have seen before
        //loadBeaconsOnDatabase();

        initializeApp();
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (PermissionsHelper.handlePermissionResult(requestCode, permissions, grantResults)) {
            // Initialize the app now that permissions are granted
            initializeApp();
        } else {
            Toast.makeText(this, "Permissions are required for the app to function correctly.", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Initializes the app features and UI components after permissions are granted.
     */
    private void initializeApp() {
        Log.i(TAG, "Initializing the app...");

        // Initialize UI components
        //btnAdd = findViewById(R.id.btn_add);
        beacons = findViewById(R.id.lv_beacons);

        // Handle the floating action button click
//        btnAdd.setOnClickListener(v ->
//                Toast.makeText(this, "Feature not yet implemented", Toast.LENGTH_SHORT).show()
//        );

        // Handle window insets for modern devices
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // allow the permissions to run in the background
        BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(this);

        // Setup navigation drawer
        setupNavigationDrawer();

        // Handle back button press
        setupBackPressedHandler();

        // Hide the default ActionBar title
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        // Check Bluetooth status
        checkBluetoothStatus();

        if (wasCreatedBefore) {
            return;
        }

        // Output starter logo to log window
        log("Geogram", Art.logo1());

        // Launch background service
        startBackgroundService();

        wasCreatedBefore = true;
    }

    /**
     * Setup the navigation drawer and its actions.
     */
    private void setupNavigationDrawer() {
        DrawerLayout drawerLayout = findViewById(R.id.drawer_layout);
        NavigationView navigationView = findViewById(R.id.navigation_view);

        // Open the drawer when settings button is clicked
        ImageButton btnSettings = findViewById(R.id.btn_settings);
        btnSettings.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        // Handle navigation item clicks
        navigationView.setNavigationItemSelectedListener(item -> {
            FragmentManager fragmentManager = getSupportFragmentManager();
            FragmentTransaction transaction = fragmentManager.beginTransaction();

            if (item.getItemId() == R.id.nav_settings) {
                transaction.replace(R.id.main, SettingsFragment.getInstance())
                        .addToBackStack(null);
            } else if (item.getItemId() == R.id.nav_broadcast) {
                transaction.replace(R.id.main, new BroadcastChatFragment())
                        .addToBackStack(null);
            } else if (item.getItemId() == R.id.nav_debug) {
                transaction.replace(R.id.main, new DebugFragment())
                        .addToBackStack(null);
            } else if (item.getItemId() == R.id.nav_about) {
                transaction.replace(R.id.main, new AboutFragment())
                        .addToBackStack(null);
            } else if (item.getItemId() == R.id.nav_networks) {
                transaction.replace(R.id.main, new NetworksFragment())
                        .addToBackStack(null);
            }

            transaction.addToBackStack(null).commit();
            //btnAdd.hide(); // Hide FAB on navigating to any fragment

            // Close the drawer after an item is clicked
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });
    }

    /**
     * Setup the behavior for handling back button presses.
     */
    private void setupBackPressedHandler() {
        this.getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                FragmentManager fragmentManager = getSupportFragmentManager();
                if (fragmentManager.getBackStackEntryCount() > 0) {
                    fragmentManager.popBackStack(); // Navigate back to the previous fragment
                } else {
                    finish(); // Exit the app if no fragments are in the back stack
                }
                //btnAdd.show(); // Ensure the Floating Action Button is visible when returning
            }
        });
    }

    /**
     * Start the background service.
     */
    private void startBackgroundService() {
        Intent serviceIntent = new Intent(this, BackgroundService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            log(TAG, "Starting BackgroundService as a foreground service");
            startForegroundService(serviceIntent); // For Android 8.0 (Oreo) and above
        } else {
            log(TAG, "Starting BackgroundService as a normal service");
            startService(serviceIntent); // For Android versions below 8.0
        }

//        serviceIntent = new Intent(this, BackgroundService.class);
//
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            log(TAG, "Starting BackgroundService as a foreground service");
//            startForegroundService(serviceIntent);
//        } else {
//            log(TAG, "Starting BackgroundService as a normal service");
//            startService(serviceIntent);
//        }
    }

    /**
     * Check the status of Bluetooth and notify the user if necessary.
     */
    private void checkBluetoothStatus() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not supported on this device", Toast.LENGTH_LONG).show();
        } else if (!bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Bluetooth is disabled. Please enable it", Toast.LENGTH_LONG).show();
        }
    }
}

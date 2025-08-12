package offgrid.geogram;

import static offgrid.geogram.core.Messages.log;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
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

import com.google.android.material.navigation.NavigationView;

import offgrid.geogram.ble.events.BroadcastChatFragment;
import offgrid.geogram.core.Art;
import offgrid.geogram.core.BackgroundService;
import offgrid.geogram.core.Central;
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
    private static boolean wasCreatedBefore = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        activity = this;

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);

        if (PermissionsHelper.hasAllPermissions(this)) {
            initializeApp();
        } else {
            PermissionsHelper.requestPermissionsIfNecessary(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!PermissionsHelper.hasAllPermissions(this)) {
            PermissionsHelper.requestPermissionsIfNecessary(this);
        } else if (!wasCreatedBefore) {
            initializeApp();
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (PermissionsHelper.handlePermissionResult(requestCode, permissions, grantResults)) {
            initializeApp();
        } else {
            // Check if user denied with "Don't ask again"
            boolean permanentlyDenied = false;
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] == PackageManager.PERMISSION_DENIED &&
                        !shouldShowRequestPermissionRationale(permissions[i])) {
                    permanentlyDenied = true;
                    break;
                }
            }

            if (permanentlyDenied) {
                Toast.makeText(this,
                        "Some permissions are permanently denied. Please enable them in App Settings.",
                        Toast.LENGTH_LONG).show();

                // Open settings
                Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(android.net.Uri.fromParts("package", getPackageName(), null));
                startActivity(intent);
            } else {
                Toast.makeText(this,
                        "Permissions are required for the app to function correctly.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }



    private void initializeApp() {
        Log.i(TAG, "Initializing the app...");

        beacons = findViewById(R.id.lv_beacons);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(this);

        setupNavigationDrawer();
        setupBackPressedHandler();

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        checkBluetoothStatus();

        if (wasCreatedBefore) return;

        log("Geogram", Art.logo1());
        startBackgroundService();
        wasCreatedBefore = true;
    }

    private void setupNavigationDrawer() {
        DrawerLayout drawerLayout = findViewById(R.id.drawer_layout);
        NavigationView navigationView = findViewById(R.id.navigation_view);

        ImageButton btnSettings = findViewById(R.id.btn_settings);
        btnSettings.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        navigationView.setNavigationItemSelectedListener(item -> {
            FragmentManager fragmentManager = getSupportFragmentManager();
            FragmentTransaction transaction = fragmentManager.beginTransaction();

            if (item.getItemId() == R.id.nav_settings) {
                transaction.replace(R.id.main, SettingsFragment.getInstance()).addToBackStack(null);
            } else if (item.getItemId() == R.id.nav_broadcast) {
                transaction.replace(R.id.main, Central.getInstance().broadcastChatFragment).addToBackStack(null);
            } else if (item.getItemId() == R.id.nav_debug) {
                transaction.replace(R.id.main, new DebugFragment()).addToBackStack(null);
            } else if (item.getItemId() == R.id.nav_about) {
                transaction.replace(R.id.main, new AboutFragment()).addToBackStack(null);
            } else if (item.getItemId() == R.id.nav_networks) {
                transaction.replace(R.id.main, new NetworksFragment()).addToBackStack(null);
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
}

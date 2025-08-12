package offgrid.geogram.core;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.List;

public class PermissionsHelper {

    private static final String TAG = "PermissionsHelper";
    private static final int PERMISSION_REQUEST_CODE = 100;

    /**
     * Dynamically generates required permissions based on the device's Android version.
     */
    @SuppressLint({"InlinedApi", "ObsoleteSdkInt"})
    private static String[] getRequiredPermissions() {
        List<String> permissions = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN);
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE);
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            permissions.add(Manifest.permission.BLUETOOTH);
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }

//        permissions.add(Manifest.permission.ACCESS_WIFI_STATE);
//        permissions.add(Manifest.permission.CHANGE_WIFI_STATE);
//        permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        permissions.add(Manifest.permission.INTERNET);
        permissions.add(Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);

        return permissions.toArray(new String[0]);
    }

    /**
     * Requests all necessary permissions if they are not already granted.
     */
    @SuppressLint("ObsoleteSdkInt")
    public static boolean requestPermissionsIfNecessary(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;

        String[] requiredPermissions = getRequiredPermissions();
        List<String> missingPermissions = new ArrayList<>();

        for (String permission : requiredPermissions) {
            if (ActivityCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission);
            }
        }

        if (!missingPermissions.isEmpty()) {
            Log.i(TAG, "Requesting permissions: " + missingPermissions);
            ActivityCompat.requestPermissions(
                    activity,
                    missingPermissions.toArray(new String[0]),
                    PERMISSION_REQUEST_CODE
            );
            return false;
        }

        Log.i(TAG, "All permissions already granted.");
        return true;
    }

    /**
     * Check if all permissions are granted in an activity context.
     */
    public static boolean hasAllPermissions(Activity activity) {
        return hasAllPermissions((Context) activity);
    }

    /**
     * Check if all permissions are granted in a general context (e.g. Service).
     */
    public static boolean hasAllPermissions(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;

        for (String permission : getRequiredPermissions()) {
            if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Missing permission: " + permission);
                return false;
            }
        }
        return true;
    }

    /**
     * Handles permission request result.
     */
    public static boolean handlePermissionResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "Not all permissions were granted.");
                    return false;
                }
            }
            Log.i(TAG, "All permissions granted.");
            return true;
        }
        return false;
    }
}

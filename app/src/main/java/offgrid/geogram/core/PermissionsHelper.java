package offgrid.geogram.core;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.List;

public class PermissionsHelper {

    private static final String TAG = "PermissionsHelper";

    // List of required permissions
    private static final String[] REQUIRED_PERMISSIONS = {

            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.NEARBY_WIFI_DEVICES,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.INTERNET,
            Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
    };

    private static final int PERMISSION_REQUEST_CODE = 100;

    /**
     * Requests all necessary permissions if they are not already granted.
     *
     * @param activity The activity requesting the permissions.
     * @return True if all permissions are already granted, false otherwise.
     */
    public static boolean requestPermissionsIfNecessary(Activity activity) {
        List<String> missingPermissions = new ArrayList<>();
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ActivityCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission);
            }
        }

        if (!missingPermissions.isEmpty()) {
            Log.i(TAG, "Requesting necessary permissions...");
            ActivityCompat.requestPermissions(
                    activity,
                    missingPermissions.toArray(new String[0]),
                    PERMISSION_REQUEST_CODE
            );
            return false;
        }

        Log.i(TAG, "All necessary permissions already granted.");
        return true;
    }

    /**
     * Checks if all necessary permissions are granted.
     *
     * @param activity The activity to check permissions for.
     * @return True if all permissions are granted, false otherwise.
     */
    public static boolean hasAllPermissions(Activity activity) {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ActivityCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Missing permission: " + permission);
                return false;
            }
        }
        return true;
    }

    /**
     * Handle the result of the permission request.
     *
     * @param requestCode  The request code for the permission request.
     * @param permissions  The requested permissions.
     * @param grantResults The grant results for the permissions.
     * @return True if all permissions were granted, false otherwise.
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
            Log.i(TAG, "All permissions were granted.");
            return true;
        }
        return false;
    }
}

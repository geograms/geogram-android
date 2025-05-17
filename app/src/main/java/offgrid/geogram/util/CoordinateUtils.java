package offgrid.geogram.util;

import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;

import offgrid.geogram.core.Log;

public class CoordinateUtils {

    private static final String TAG = "Coordinates";

    /**
     * Static method to get the current coordinates of the device.
     *
     * @param context the application context
     * @return a String[] containing latitude, longitude, and altitude in {latitude, longitude, altitude} format,
     *         or null if location or altitude is unavailable
     */
    public static String[] getCurrentCoordinates(Context context) {
        Log.i(TAG, "Requesting current coordinates...");

        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

        if (locationManager == null) {
            Log.e(TAG, "LocationManager is null. Cannot retrieve location.");
            return null;
        }

        try {
            // Check for permissions (ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION)
            if (context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

                Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (location == null) {
                    Log.i(TAG, "GPS_PROVIDER location unavailable, falling back to NETWORK_PROVIDER...");
                    location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                }

                if (location != null && location.hasAltitude()) {
                    String latitude = String.valueOf(location.getLatitude());
                    String longitude = String.valueOf(location.getLongitude());
                    String altitude = String.valueOf(location.getAltitude());

                    Log.i(TAG, "Location retrieved: Latitude=" + latitude + ", Longitude=" + longitude + ", Altitude=" + altitude);
                    return new String[]{latitude, longitude, altitude};
                } else {
                    Log.e(TAG, "Location or altitude is not available.");
                }
            } else {
                Log.e(TAG, "Location permissions are not granted.");
            }
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException while accessing location: " + e.getMessage());
        }

        Log.i(TAG, "Returning null for current coordinates.");
        return null;
    }
}

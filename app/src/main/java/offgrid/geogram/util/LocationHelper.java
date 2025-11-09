package offgrid.geogram.util;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.CancellationSignal;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;
import androidx.core.content.ContextCompat;

import java.util.concurrent.Executor;

public final class LocationHelper {
    private LocationHelper() {}

    public static final class Fix {
        public final double lat, lon;
        public final float accuracyMeters;
        public final Double altitudeMeters; // may be null
        public final long timeMillis;

        public Fix(Location loc) {
            this.lat = loc.getLatitude();
            this.lon = loc.getLongitude();
            this.accuracyMeters = loc.hasAccuracy() ? loc.getAccuracy() : Float.NaN;
            this.altitudeMeters = loc.hasAltitude() ? loc.getAltitude() : null;
            this.timeMillis = loc.getTime();
        }
    }

    public interface Callback {
        void onSuccess(@NonNull Fix fix);
        void onError(@NonNull Exception e);
    }

    /** Gets one fresh/high-accuracy fix if possible; otherwise uses last known fix. */
    public static void getCurrentCoordinates(@NonNull Context ctx,
                                             @NonNull Callback cb) {
        Context app = ctx.getApplicationContext();
        if (!hasLocationPermission(app)) {
            cb.onError(new SecurityException("Missing FINE/COARSE location permission"));
            return;
        }
        // Lint: the following call is permission-guarded above.
        //noinspection MissingPermission
        getCurrentCoordinatesWithPermission(app, cb);
    }

    @RequiresPermission(anyOf = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    })
    @SuppressLint("MissingPermission") // all location calls inside are guarded by the caller
    private static void getCurrentCoordinatesWithPermission(@NonNull Context app,
                                                            @NonNull Callback cb) {
        LocationManager locationManager = (LocationManager) app.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            cb.onError(new IllegalStateException("LocationManager not available"));
            return;
        }

        // Try GPS first for high accuracy
        String provider = LocationManager.GPS_PROVIDER;
        if (!locationManager.isProviderEnabled(provider)) {
            // Fallback to network if GPS disabled
            provider = LocationManager.NETWORK_PROVIDER;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ has getCurrentLocation API
            CancellationSignal cancellationSignal = new CancellationSignal();
            Executor executor = app.getMainExecutor();

            final String finalProvider = provider;
            locationManager.getCurrentLocation(provider, cancellationSignal, executor, loc -> {
                if (loc != null) {
                    cb.onSuccess(new Fix(loc));
                } else {
                    // Fallback to last known location
                    getLastKnownLocation(locationManager, cb);
                }
            });
        } else {
            // For older Android versions, use last known location
            getLastKnownLocation(locationManager, cb);
        }
    }

    @RequiresPermission(anyOf = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    })
    @SuppressLint("MissingPermission")
    private static void getLastKnownLocation(@NonNull LocationManager locationManager,
                                             @NonNull Callback cb) {
        // Try GPS first
        Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);

        // If no GPS location, try network
        if (location == null) {
            location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        }

        // If still no location, try passive provider
        if (location == null) {
            location = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
        }

        if (location != null) {
            cb.onSuccess(new Fix(location));
        } else {
            cb.onError(new IllegalStateException("No location available"));
        }
    }

    public static boolean hasLocationPermission(@NonNull Context ctx) {
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
}

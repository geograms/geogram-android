package offgrid.geogram.util;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;

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
        FusedLocationProviderClient flp = LocationServices.getFusedLocationProviderClient(app);
        CancellationTokenSource cts = new CancellationTokenSource();

        flp.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.getToken())
                .addOnSuccessListener(loc -> {
                    if (loc != null) {
                        cb.onSuccess(new Fix(loc));
                    } else {
                        // Fallback: last known
                        flp.getLastLocation()
                                .addOnSuccessListener(last -> {
                                    if (last != null) cb.onSuccess(new Fix(last));
                                    else cb.onError(new IllegalStateException("No location available"));
                                })
                                .addOnFailureListener(cb::onError);
                    }
                })
                .addOnFailureListener(cb::onError);
    }

    public static boolean hasLocationPermission(@NonNull Context ctx) {
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
}

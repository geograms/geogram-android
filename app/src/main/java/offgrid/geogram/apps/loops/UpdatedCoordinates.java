package offgrid.geogram.apps.loops;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import androidx.core.content.ContextCompat;

import android.location.Location;
import android.location.LocationManager;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import offgrid.geogram.util.LocationHelper;

public final class UpdatedCoordinates {
    private static final String TAG = "UpdatedCoordinates";
    private static final long PERIOD_MINUTES = 2L;

    private UpdatedCoordinates() {}
    private static final class Holder { static final UpdatedCoordinates I = new UpdatedCoordinates(); }
    public static UpdatedCoordinates getInstance() { return Holder.I; }

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
                @Override public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "UpdatedCoordinatesScheduler");
                    t.setDaemon(true);
                    return t;
                }
            });

    private final Object lock = new Object();
    private @Nullable ScheduledFuture<?> task;
    private @Nullable Context appCtx;

    private volatile @Nullable LocationHelper.Fix lastFix;
    private volatile long lastAttemptMillis = 0L;
    private volatile @Nullable Exception lastError;

    private final CountDownLatch firstFixLatch = new CountDownLatch(1);
    private final AtomicBoolean firstFixSet = new AtomicBoolean(false);

    public void start(@NonNull Context ctx) {
        synchronized (lock) {
            this.appCtx = ctx.getApplicationContext();

            // immediate run so first callers have coords quickly
            fetchImmediateFastThenFresh();

            if (task != null && !task.isCancelled() && !task.isDone()) return;
            task = scheduler.scheduleAtFixedRate(this::safeUpdateOnce,
                    PERIOD_MINUTES, PERIOD_MINUTES, TimeUnit.MINUTES);
            Log.d(TAG, "Started coordinate updates");
        }
    }

    public void stop() {
        synchronized (lock) {
            if (task != null) { task.cancel(false); task = null; Log.d(TAG, "Stopped coordinate updates"); }
        }
    }

    public void shutdown() { stop(); scheduler.shutdownNow(); }

    public @Nullable LocationHelper.Fix getLastFix() { return lastFix; }
    public boolean awaitFirstFix(long timeoutMs) {
        try { return firstFixLatch.await(timeoutMs, TimeUnit.MILLISECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
    }
    public long getLastAttemptMillis() { return lastAttemptMillis; }
    public @Nullable Exception getLastError() { return lastError; }

    private void safeUpdateOnce() {
        try { updateOnce(); }
        catch (Throwable t) { lastError = (t instanceof Exception) ? (Exception) t : new RuntimeException(t); Log.e(TAG, "Periodic update failed", t); }
    }

    private void updateOnce() {
        final Context c = appCtx;
        lastAttemptMillis = System.currentTimeMillis();

        if (c == null) { lastError = new IllegalStateException("No context set; call start(context) first"); return; }
        if (!hasLocationPermission(c)) { lastError = new SecurityException("Missing location permission"); Log.w(TAG, "Location permission not granted"); return; }

        LocationHelper.getCurrentCoordinates(c, new LocationHelper.Callback() {
            @Override public void onSuccess(@NonNull LocationHelper.Fix fix) { publishFix(fix); }
            @Override public void onError(@NonNull Exception e) { lastError = e; Log.w(TAG, "getCurrentCoordinates failed", e); }
        });
    }

    private void fetchImmediateFastThenFresh() {
        final Context c = appCtx;
        if (c == null) return;
        if (!hasLocationPermission(c)) { lastError = new SecurityException("Missing location permission"); return; }

        // FAST path: last known (lint-safe: we checked permission, also catch SecurityException)
        try {
            //noinspection MissingPermission
            getLastKnownWithPermission(c, fix -> { if (fix != null) publishFix(fix); });
        } catch (SecurityException se) {
            lastError = se; Log.w(TAG, "Last known denied at runtime", se);
        }

        // FRESH path: single high-accuracy fix (LocationHelper is already lint/permission-guarded)
        LocationHelper.getCurrentCoordinates(c, new LocationHelper.Callback() {
            @Override public void onSuccess(@NonNull LocationHelper.Fix fix) { publishFix(fix); }
            @Override public void onError(@NonNull Exception e) { lastError = e; Log.w(TAG, "Initial fresh fetch failed", e); }
        });
    }

    private void publishFix(@NonNull LocationHelper.Fix fix) {
        lastFix = fix; lastError = null;
        if (firstFixSet.compareAndSet(false, true)) firstFixLatch.countDown();
        Log.d(TAG, "Fix updated: lat=" + fix.lat + " lon=" + fix.lon +
                " acc=" + fix.accuracyMeters + " t=" + fix.timeMillis);
    }

    private static boolean hasLocationPermission(@NonNull Context ctx) {
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private interface LastKnownCallback { void onResult(@Nullable LocationHelper.Fix fix); }

    @RequiresPermission(anyOf = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    })
    @SuppressLint("MissingPermission")
    private static void getLastKnownWithPermission(@NonNull Context ctx,
                                                   @NonNull LastKnownCallback cb) {
        LocationManager locationManager = (LocationManager) ctx.getApplicationContext()
                .getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            cb.onResult(null);
            return;
        }

        // Try GPS first
        Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);

        // If no GPS location, try network
        if (location == null) {
            location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        }

        // If still no location, try passive
        if (location == null) {
            location = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
        }

        cb.onResult(location != null ? new LocationHelper.Fix(location) : null);
    }
}

package offgrid.geogram.apps.loops;

import android.util.Log;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import offgrid.geogram.ble.BluetoothCentral;
import offgrid.geogram.ble.BluetoothSender;
import offgrid.geogram.core.Central;
import offgrid.geogram.util.GeoCode4;
import offgrid.geogram.util.LocationHelper;

/*
    Sends a broadcast to everywhere about this device.
    - Singleton (no Context held -> no leaks)
    - Repeats `broadcastPing()` every 60 seconds AFTER the previous run completes.
*/
public final class PingDevice {
    private static final String TAG = "PingDevice";
    private static final long REFRESH_INTERVAL_SECONDS = 60L;

    // --- Singleton (Initialization-on-demand holder) ---
    private PingDevice() {}
    private static final class Holder { static final PingDevice INSTANCE = new PingDevice(); }
    public static PingDevice getInstance() { return Holder.INSTANCE; }
    // ---------------------------------------------------

    // Single background scheduler; does NOT hold any Activity/Context.
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
        @Override public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "PingDeviceScheduler");
            t.setDaemon(true);
            return t;
        }
    });

    private final Object lock = new Object();
    private ScheduledFuture<?> repeatingTask; // null if not running

    /** Start repeating every 60s; no-op if already running. */
    public void start() {
        synchronized (lock) {
            if (repeatingTask != null && !repeatingTask.isCancelled() && !repeatingTask.isDone()) return;
            // With fixed DELAY: next run starts 60s AFTER previous run finished (avoids overlap).
            repeatingTask = scheduler.scheduleWithFixedDelay(this::safeBroadcastPing,
                    0, REFRESH_INTERVAL_SECONDS, TimeUnit.SECONDS);
            Log.d(TAG, "Ping started (every " + REFRESH_INTERVAL_SECONDS + "s)");
        }
    }

    /** Stop repeating; safe to call multiple times. */
    public void stop() {
        synchronized (lock) {
            if (repeatingTask != null) {
                repeatingTask.cancel(false);
                repeatingTask = null;
                Log.d(TAG, "Ping stopped");
            }
        }
    }

    /** True if the repeating task is active. */
    public boolean isRunning() {
        synchronized (lock) {
            return repeatingTask != null && !repeatingTask.isCancelled() && !repeatingTask.isDone();
        }
    }

    /** Manually trigger one ping now (does not affect the schedule). */
    public void pingNow() { safeBroadcastPing(); }

    /** Call once at app shutdown if you want to kill the scheduler thread. */
    public void shutdown() {
        stop();
        scheduler.shutdownNow();
    }

    // --- Your actual work lives here ---
    private void safeBroadcastPing() {
        try {
            broadcastPing();
        } catch (Throwable t) {
            Log.e(TAG, "broadcastPing failed", t);
        }
    }


    private void broadcastPing() {


        // get the basic info
        String callsign = Central.getInstance().getSettings().getIdDevice();
        String coordinates = null;

        LocationHelper.Fix f = UpdatedCoordinates.getInstance().getLastFix();
        if (f != null) {
            String latCode = GeoCode4.encodeLat(f.lat);   // e.g., "0A9F"
            String lonCode = GeoCode4.encodeLon(f.lon);   // e.g., "Z12K"
            coordinates = latCode + "-" + lonCode;
        }


        if(coordinates == null){
            coordinates = "";
        }else{
            coordinates = "@" + coordinates;
        }

        String message = "+" + callsign + coordinates;
        // ... send a broadcast ping through network / BLE / APRS, etc.
        Log.i(TAG, message);
        BluetoothSender.getInstance(null).sendMessage(message);
    }
}

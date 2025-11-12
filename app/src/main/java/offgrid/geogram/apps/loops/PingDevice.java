package offgrid.geogram.apps.loops;

import android.util.Log;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

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
    private static final long REFRESH_INTERVAL_SECONDS = 10L;

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

    /** Start: send one ping now, then repeat every 60s after that. */
    public void start() {
        synchronized (lock) {
            if (repeatingTask != null && !repeatingTask.isCancelled() && !repeatingTask.isDone()) return;

            // Immediate, async (doesn't block caller thread)
            scheduler.execute(this::safeBroadcastPing);

            // Next runs start 60s AFTER the previous one finishes
            repeatingTask = scheduler.scheduleWithFixedDelay(
                    this::safeBroadcastPing,
                    REFRESH_INTERVAL_SECONDS,            // initial delay (start after 60s)
                    REFRESH_INTERVAL_SECONDS,            // period
                    TimeUnit.SECONDS
            );
            Log.d(TAG, "Ping started (immediate + every " + REFRESH_INTERVAL_SECONDS + "s)");
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

        if(Central.getInstance() == null || Central.getInstance().getSettings() == null
            || Central.getInstance().getSettings().getIdDevice() == null){
            return;
        }

        // Add random delay (0-500ms) to avoid BLE collisions when multiple devices ping
        try {
            Thread.sleep(new Random().nextInt(500));
        } catch (InterruptedException e) {
            // Ignore interruption
        }

        // get the basic info
        String callsign = Central.getInstance().getSettings().getIdDevice();
        String coordinates = null;

        LocationHelper.Fix f = UpdatedCoordinates.getInstance().getLastFix();
        if (f != null) {
            String latCode = GeoCode4.encodeLat(f.lat);   // e.g., "0A9F"
            String lonCode = GeoCode4.encodeLon(f.lon);   // e.g., "Z12K"
            coordinates = latCode + "-" + lonCode;
            // don't use APRS coordinates because they use non-human friendly chars
            // coordinates = AprsCompressed.encodePairWithDash(f.lat, f.lon);
        }


        if(coordinates == null){
            coordinates = "";
        }else{
            coordinates = "@" + coordinates;
        }

        // Include device model code and version: +CALLSIGN@COORDS#APP-0.5.18
        String deviceModelCode = "APP-0.5.18";  // Device code: APP = Android Phone
        String message = "+" + callsign + coordinates + "#" + deviceModelCode;

        // BLE advertising has 31-byte limit. With overhead, we have ~20 bytes for data.
        // If message is too large, remove location to fit in advertising packet
        final int BLE_ADVERTISING_MAX_PAYLOAD = 20;
        if (message.length() > BLE_ADVERTISING_MAX_PAYLOAD && !coordinates.isEmpty()) {
            // Remove location info and try again
            String messageWithoutLocation = "+" + callsign + "#" + deviceModelCode;
            Log.i(TAG, "Message too large (" + message.length() + " bytes), removing location: " + message + " -> " + messageWithoutLocation);
            message = messageWithoutLocation;
        }

        // ... send a broadcast ping through network / BLE / APRS, etc.
        Log.i(TAG, "Broadcasting: " + message);
        BluetoothSender.getInstance(null).sendMessage(message);
    }
}

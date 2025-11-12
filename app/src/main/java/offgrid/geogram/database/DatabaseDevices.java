package offgrid.geogram.database;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Dao;
import androidx.room.Database;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.PrimaryKey;
import androidx.room.Query;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.Update;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Singleton database for storing device profiles and ping history.
 *
 * Stores:
 * - Device metadata: callsign, first/last seen, detection count, device type, npub, alias
 * - Ping history: each detection event with timestamp and optional geocode
 */
public final class DatabaseDevices {
    private static final String TAG = "DatabaseDevices";

    // Flush ping queue to disk every 30 seconds
    private static final long FLUSH_PERIOD_SECONDS = 30L;
    private static final int FLUSH_MAX_BATCH = 5000;

    // --- Singleton ---
    private DatabaseDevices() {}
    private static final class Holder {
        static final DatabaseDevices I = new DatabaseDevices();
    }
    public static DatabaseDevices get() {
        return Holder.I;
    }

    // --- State ---
    private volatile boolean initialized = false;
    private Context appCtx;
    private DevicesDb db;
    private DeviceDao deviceDao;
    private PingDao pingDao;

    // Write queue for pings (batched writes)
    private final ConcurrentLinkedQueue<DevicePingRow> pendingPings = new ConcurrentLinkedQueue<>();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "DbDevicesFlusher");
            t.setDaemon(true);
            return t;
        }
    });
    private @Nullable ScheduledFuture<?> flushTask;

    // ------------------- Public API -------------------

    /** Initialize once; safe to call multiple times. */
    public synchronized void init(@NonNull Context context) {
        if (initialized) return;
        this.appCtx = context.getApplicationContext();
        this.db = Room.databaseBuilder(appCtx, DevicesDb.class, "devices.db")
                .fallbackToDestructiveMigrationOnDowngrade()
                .build();
        this.deviceDao = db.deviceDao();
        this.pingDao = db.pingDao();

        // Schedule periodic batch flush for pings
        this.flushTask = scheduler.scheduleAtFixedRate(
            this::flushPings,
            FLUSH_PERIOD_SECONDS,
            FLUSH_PERIOD_SECONDS,
            TimeUnit.SECONDS
        );
        initialized = true;
        Log.i(TAG, "DatabaseDevices initialized");
    }

    /** Graceful shutdown (optional). */
    public synchronized void shutdown() {
        if (!initialized) return;
        try {
            flushPingsNow();
        } catch (Throwable ignored) {}
        if (flushTask != null) {
            flushTask.cancel(false);
            flushTask = null;
        }
        scheduler.shutdownNow();
        initialized = false;
    }

    // ------------------- Device Operations -------------------

    /**
     * Save or update a device profile.
     * Creates new if doesn't exist, updates if exists.
     * Pass null for optional fields (npub, alias, tags, notes) to leave unchanged on update.
     */
    public void saveDevice(@NonNull String callsign, @NonNull String deviceType,
                          long firstSeenTs, long lastSeenTs, int totalDetections,
                          @Nullable String npub, @Nullable String alias,
                          @Nullable String tags, @Nullable String notes) {
        ensureInit();
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                DeviceRow existing = deviceDao.findByCallsign(callsign);
                if (existing == null) {
                    // Create new device
                    DeviceRow newDevice = new DeviceRow();
                    newDevice.callsign = callsign;
                    newDevice.deviceType = deviceType;
                    newDevice.firstSeenTs = firstSeenTs;
                    newDevice.lastSeenTs = lastSeenTs;
                    newDevice.totalDetections = totalDetections;
                    newDevice.npub = npub;
                    newDevice.alias = alias;
                    newDevice.tags = tags;
                    newDevice.notes = notes;
                    newDevice.createdTs = System.currentTimeMillis();
                    deviceDao.insert(newDevice);
                    Log.d(TAG, "Created new device: " + callsign);
                } else {
                    // Update existing device
                    existing.lastSeenTs = lastSeenTs;
                    existing.totalDetections = totalDetections;
                    if (npub != null) existing.npub = npub;
                    if (alias != null) existing.alias = alias;
                    if (tags != null) existing.tags = tags;
                    if (notes != null) existing.notes = notes;
                    deviceDao.update(existing);
                    Log.d(TAG, "Updated device: " + callsign);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error saving device: " + callsign, e);
            }
        });
    }

    /** Get device by callsign (synchronous - avoid on UI thread). */
    public @Nullable DeviceRow getDevice(@NonNull String callsign) {
        ensureInit();
        return deviceDao.findByCallsign(callsign);
    }

    /** Get all devices ordered by last seen (newest first). */
    public @NonNull List<DeviceRow> getAllDevices() {
        ensureInit();
        return deviceDao.getAllOrderedByLastSeen();
    }

    /** Delete a device and all its pings. */
    public void deleteDevice(@NonNull String callsign) {
        ensureInit();
        Executors.newSingleThreadExecutor().execute(() -> {
            pingDao.deleteByCallsign(callsign);
            deviceDao.deleteByCallsign(callsign);
            Log.d(TAG, "Deleted device: " + callsign);
        });
    }

    /** Delete all devices and pings from the database. */
    public void deleteAllDevices() {
        ensureInit();
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                // Clear pending pings queue
                pendingPings.clear();
                // Delete all pings from database
                pingDao.deleteAllPings();
                // Delete all devices from database
                deviceDao.deleteAllDevices();
                Log.i(TAG, "Cleared all devices and pings from database");
            } catch (Exception e) {
                Log.e(TAG, "Error clearing all devices", e);
            }
        });
    }

    // ------------------- Ping Operations -------------------

    /**
     * Record a ping/detection event. Enqueued for batch write.
     * @param callsign Device callsign
     * @param timestamp When detected
     * @param geocode Optional location code (null for ping-only)
     */
    public void enqueuePing(@NonNull String callsign, long timestamp, @Nullable String geocode) {
        ensureInit();
        DevicePingRow ping = new DevicePingRow();
        ping.callsign = callsign;
        ping.timestamp = timestamp;
        ping.geocode = geocode;
        pendingPings.add(ping);
    }

    /** Force synchronous flush of pending pings (avoid on UI thread). */
    public void flushPingsNow() {
        ensureInit();
        flushPings();
    }

    /** Get all pings for a device, ordered by timestamp DESC. */
    public @NonNull List<DevicePingRow> getPingsForDevice(@NonNull String callsign, int limit) {
        ensureInit();
        return pingDao.findByCallsign(callsign, limit);
    }

    /** Get pings for a device in time range. */
    public @NonNull List<DevicePingRow> getPingsInRange(@NonNull String callsign,
                                                         long fromTs, long toTs, int limit) {
        ensureInit();
        return pingDao.findInRange(callsign, fromTs, toTs, limit);
    }

    /** Count total pings for a device. */
    public long getPingCount(@NonNull String callsign) {
        ensureInit();
        return pingDao.countByCallsign(callsign);
    }

    // ------------------- Internals -------------------

    private void ensureInit() {
        if (!initialized || db == null) {
            throw new IllegalStateException("DatabaseDevices not initialized. Call init(context) first.");
        }
    }

    private void flushPings() {
        if (pendingPings.isEmpty()) return;

        List<DevicePingRow> batch = new java.util.ArrayList<>(Math.min(FLUSH_MAX_BATCH, pendingPings.size()));
        DevicePingRow ping;
        while (batch.size() < FLUSH_MAX_BATCH && (ping = pendingPings.poll()) != null) {
            batch.add(ping);
        }
        if (batch.isEmpty()) return;

        try {
            db.runInTransaction(() -> {
                pingDao.insertAll(batch);
            });
            Log.d(TAG, "Flushed " + batch.size() + " pings to database");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to flush pings, re-queueing", t);
            // Put back on failure
            for (DevicePingRow p : batch) {
                pendingPings.add(p);
            }
        }
    }

    // ------------------- Room Schema -------------------

    /** Device profile entity. */
    @Entity(tableName = "devices")
    public static class DeviceRow {
        @PrimaryKey
        @NonNull
        public String callsign;

        @NonNull
        public String deviceType; // e.g., "HT_PORTABLE"

        public long firstSeenTs;  // Unix timestamp milliseconds
        public long lastSeenTs;   // Unix timestamp milliseconds
        public int totalDetections; // Count of pings

        @Nullable
        public String npub;       // NOSTR public key

        @Nullable
        public String alias;      // User-assigned nickname

        @Nullable
        public String tags;       // Comma-separated tags or JSON for organization (e.g., "friend,emergency,local")

        @Nullable
        public String notes;      // Free-form notes about the device

        public long createdTs;    // When record was created
    }

    /** Ping/detection event entity. */
    @Entity(tableName = "device_pings",
            indices = {
                @Index(value = {"callsign", "timestamp"}),
                @Index(value = {"timestamp"})
            })
    public static class DevicePingRow {
        @PrimaryKey(autoGenerate = true)
        public long id;

        @NonNull
        public String callsign;

        public long timestamp;    // Unix timestamp milliseconds

        @Nullable
        @ColumnInfo(collate = ColumnInfo.NOCASE)
        public String geocode;    // "LLLL-LLLL" or null for ping-only
    }

    // ------------------- DAOs -------------------

    @Dao
    public interface DeviceDao {
        @Insert(onConflict = OnConflictStrategy.REPLACE)
        void insert(DeviceRow device);

        @Update
        void update(DeviceRow device);

        @Query("SELECT * FROM devices WHERE callsign = :callsign LIMIT 1")
        DeviceRow findByCallsign(String callsign);

        @Query("SELECT * FROM devices ORDER BY lastSeenTs DESC")
        List<DeviceRow> getAllOrderedByLastSeen();

        @Query("DELETE FROM devices WHERE callsign = :callsign")
        void deleteByCallsign(String callsign);

        @Query("DELETE FROM devices")
        void deleteAllDevices();

        @Query("SELECT COUNT(*) FROM devices")
        long countAll();
    }

    @Dao
    public interface PingDao {
        @Insert(onConflict = OnConflictStrategy.IGNORE)
        void insertAll(List<DevicePingRow> pings);

        @Query("SELECT * FROM device_pings WHERE callsign = :callsign ORDER BY timestamp DESC LIMIT :limit")
        List<DevicePingRow> findByCallsign(String callsign, int limit);

        @Query("""
               SELECT * FROM device_pings
               WHERE callsign = :callsign AND timestamp BETWEEN :fromTs AND :toTs
               ORDER BY timestamp DESC LIMIT :limit
               """)
        List<DevicePingRow> findInRange(String callsign, long fromTs, long toTs, int limit);

        @Query("SELECT COUNT(*) FROM device_pings WHERE callsign = :callsign")
        long countByCallsign(String callsign);

        @Query("DELETE FROM device_pings WHERE callsign = :callsign")
        void deleteByCallsign(String callsign);

        @Query("DELETE FROM device_pings")
        void deleteAllPings();
    }

    // ------------------- Database -------------------

    @Database(entities = {DeviceRow.class, DevicePingRow.class}, version = 1, exportSchema = false)
    public abstract static class DevicesDb extends RoomDatabase {
        public abstract DeviceDao deviceDao();
        public abstract PingDao pingDao();
    }
}

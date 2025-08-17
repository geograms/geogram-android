package offgrid.geogram.database;

/*
Gradle (Java, no Kotlin):
--------------------------------------
dependencies {
    // Use the newest Room your minSdk supports:
    // - If minSdk >= 23 → 2.7.x is fine.
    // - If minSdk 21/22  → pin to 2.6.1.
    implementation "androidx.room:room-runtime:2.6.1"
    annotationProcessor "androidx.room:room-compiler:2.6.1"

    // Optional (logging/inspection)
    // debugImplementation "com.facebook.stetho:stetho:1.6.0"
}
--------------------------------------

Usage:
--------------------------------------
DatabaseLocations.get().init(appContext); // call once (e.g., in Application.onCreate())

// Enqueue writes (batched to DB by a background task)
DatabaseLocations.get().enqueue("DL1ABC-9", "0A9F-Z12K", System.currentTimeMillis(), /*alt null);

// Query recent or ranges
        Record last = DatabaseLocations.get().getMostRecent("DL1ABC-9");
        List<Record> hist = DatabaseLocations.get().getForCallsignInRange("DL1ABC-9", fromTs, toTs, 500);

// Nearby (radiusKm, lookbackHours; default lookback=48h if <=0)
        List<Nearby> near = DatabaseLocations.get().findNearby(49.0, 8.4, 10.0, 24);

// Maintenance
        DatabaseLocations.get().deleteByCallsign("DL1ABC-9");
        DatabaseLocations.get().deleteAll();
        DatabaseLocations.get().shutdown(); // optional
        --------------------------------------
        */
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
import androidx.sqlite.db.SupportSQLiteQuery;
import androidx.sqlite.db.SimpleSQLiteQuery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import offgrid.geogram.devices.ConnectionType;
import offgrid.geogram.devices.DeviceManager;
import offgrid.geogram.devices.DeviceType;
import offgrid.geogram.devices.EventConnected;
import offgrid.geogram.util.GeoCode4; // your 4+4 Base36 codec (encode/decode)

/** Singleton database facade for storing locations by callsign (with 4+4 cell code). */
public final class DatabaseLocations {
    private static final String TAG = "DatabaseLocations";

    // Tune batching/flush cadence here
    private static final long FLUSH_PERIOD_SECONDS = 30L;        // run flusher every 30s
    private static final int  FLUSH_MAX_BATCH = 20_000;          // max rows per flush tx
    private static final int  NEARBY_QUERY_LIMIT = 10_000;       // cap raw bbox rows

    // --- Singleton ---
    private DatabaseLocations() {}
    private static final class Holder { static final DatabaseLocations I = new DatabaseLocations(); }
    public static DatabaseLocations get() { return Holder.I; }

    // --- State ---
    private volatile boolean initialized = false;
    private Context appCtx;
    private LocationsDb db;
    private LocationDao dao;

    // Write queue (ingest thread(s) enqueue, single scheduler thread flushes in batches)
    private final ConcurrentLinkedQueue<LocationRow> pending = new ConcurrentLinkedQueue<>();

    // Reader activity tracking to avoid flushing while heavy reads are in progress
    private final AtomicInteger activeReads = new AtomicInteger(0);

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
        @Override public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "DbLocationsFlusher");
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
        this.db = Room.databaseBuilder(appCtx, LocationsDb.class, "locations.db")
                // addMigrations(...) when schema evolves
                .fallbackToDestructiveMigrationOnDowngrade()
                .build();
        this.dao = db.dao();

        // Schedule periodic batch flush
        this.flushTask = scheduler.scheduleAtFixedRate(this::flushIfIdle, FLUSH_PERIOD_SECONDS, FLUSH_PERIOD_SECONDS, TimeUnit.SECONDS);
        initialized = true;
    }

    /** Graceful shutdown (optional). */
    public synchronized void shutdown() {
        if (!initialized) return;
        try { flushNow(); } catch (Throwable ignored) {}
        if (flushTask != null) { flushTask.cancel(false); flushTask = null; }
        scheduler.shutdownNow();
        initialized = false;
    }

    /** Enqueue a record (callsign, "LLLL-LLLL" cell pair, timestamp, optional altitude meters). */
    public void enqueue(@NonNull String callsign, @NonNull String cellPair, long ts, @Nullable Double altitudeMeters) {
        ensureInit();
        // Accept "LLLL-LLLL" or "LLLLLLLL"
        double[] latlon = GeoCode4.decodePair(cellPair);
        String lat4 = GeoCode4.encodeLat(latlon[0]);
        String lon4 = GeoCode4.encodeLon(latlon[1]);

        LocationRow row = new LocationRow();
        row.callsign = callsign;
        row.ts = ts;
        row.lat = latlon[0];
        row.lon = latlon[1];
        row.alt = altitudeMeters;
        row.cellLat4 = lat4;
        row.cellLon4 = lon4;
        pending.add(row);
    }

    /** Force a synchronous flush of queued writes (avoid on UI thread). */
    public void flushNow() {
        ensureInit();
        flushBatch(Integer.MAX_VALUE);
    }

    /** Most recent record for a callsign, or null. */
    public @Nullable Record getMostRecent(@NonNull String callsign) {
        ensureInit();
        activeReads.incrementAndGet();
        try {
            LocationRow row = dao.findMostRecent(callsign);
            return row == null ? null : toRecord(row);
        } finally {
            activeReads.decrementAndGet();
        }
    }

    /** Records for a callsign in [fromTs, toTs], newest first, limited. */
    public @NonNull List<Record> getForCallsignInRange(@NonNull String callsign, long fromTs, long toTs, int limit) {
        ensureInit();
        if (toTs < fromTs) { long tmp = fromTs; fromTs = toTs; toTs = tmp; }
        if (limit <= 0) limit = 1000;

        activeReads.incrementAndGet();
        try {
            List<LocationRow> rows = dao.findForCallsignInRange(callsign, fromTs, toTs, limit);
            return toRecords(rows);
        } finally {
            activeReads.decrementAndGet();
        }
    }

    /**
     * Find callsigns near a point within radiusKm and lookbackHours (default 48h if <=0).
     * Returns at most one (most recent) hit per callsign, sorted by distance ASC.
     */
    public @NonNull List<Nearby> findNearby(double lat, double lon, double radiusKm, long lookbackHours) {
        ensureInit();
        if (radiusKm <= 0) return Collections.emptyList();
        if (lookbackHours <= 0) lookbackHours = 48;

        final long now = System.currentTimeMillis();
        final long minTs = now - TimeUnit.HOURS.toMillis(lookbackHours);

        // Fast bbox prefilter
        BBox bb = bboxFromRadiusKm(lat, lon, radiusKm);
        activeReads.incrementAndGet();
        final List<LocationRow> pre;
        try {
            pre = dao.byBoundingBox(bb.minLat, bb.maxLat, bb.minLon, bb.maxLon, minTs, now, NEARBY_QUERY_LIMIT);
        } finally {
            activeReads.decrementAndGet();
        }

        // Refine by Haversine and keep most recent per callsign
        final double rMeters = radiusKm * 1000.0;
        final long[] bestTs = new long[1]; // scratch
        final java.util.HashMap<String, Nearby> best = new java.util.HashMap<>(256);
        for (LocationRow r : pre) {
            double dMeters = haversineMeters(lat, lon, r.lat, r.lon);
            if (dMeters <= rMeters) {
                Nearby current = best.get(r.callsign);
                if (current == null || r.ts > current.ts) {
                    best.put(r.callsign, new Nearby(
                            r.callsign, r.lat, r.lon, r.alt, r.ts, dMeters / 1000.0
                    ));
                }
            }
        }
        ArrayList<Nearby> out = new ArrayList<>(best.values());
        out.sort(Comparator.comparingDouble(n -> n.distanceKm));
        return out;
    }

    /** Total number of stored location rows. */
    public long getTotalLocationCount() {
        ensureInit();
        activeReads.incrementAndGet();
        try {
            return dao.countAll();
        } finally {
            activeReads.decrementAndGet();
        }
    }

    /** Number of stored location rows for a specific callsign. */
    public long getLocationCountForCallsign(@NonNull String callsign) {
        ensureInit();
        activeReads.incrementAndGet();
        try {
            return dao.countByCallsign(callsign);
        } finally {
            activeReads.decrementAndGet();
        }
    }


    /** Delete all rows of a callsign. */
    public int deleteByCallsign(@NonNull String callsign) {
        ensureInit();
        return dao.deleteByCallsign(callsign);
    }

    /** Delete everything. */
    public int deleteAll() {
        ensureInit();
        return dao.deleteAll();
    }

    // ------------------- Internals -------------------

    private void ensureInit() {
        if (!initialized || db == null || dao == null) {
            throw new IllegalStateException("DatabaseLocations not initialized. Call init(context) first.");
        }
    }

    private void flushIfIdle() {
        // avoid flushing if a read is ongoing; this keeps read latency low under load
        if (activeReads.get() > 0) return;
        flushBatch(FLUSH_MAX_BATCH);
    }

    private void flushBatch(int max) {
        if (pending.isEmpty()) return;
        ArrayList<LocationRow> buf = new ArrayList<>(Math.min(max, pending.size()));
        LocationRow r;
        while (buf.size() < max && (r = pending.poll()) != null) {
            buf.add(r);
        }
        if (buf.isEmpty()) return;

        try {
            db.runInTransaction(() -> {
                // IGNORE on conflict (unique index) deduplicates
                dao.insertAll(buf);
            });
            if (pending.isEmpty()) return; // done
            // If still pending, schedule an immediate follow-up to drain quickly
            scheduler.schedule(() -> flushBatch(FLUSH_MAX_BATCH), 1, TimeUnit.SECONDS);
        } catch (Throwable t) {
            Log.e(TAG, "flush failed; re-queueing batch", t);
            // Put back to head in case of transient failure
            for (LocationRow x : buf) pending.add(x);
        }
    }

    private static Record toRecord(LocationRow r) {
        return new Record(r.callsign, r.lat, r.lon, r.alt, r.ts, r.cellLat4 + "-" + r.cellLon4);
    }

    private static List<Record> toRecords(List<LocationRow> rows) {
        ArrayList<Record> out = new ArrayList<>(rows.size());
        for (LocationRow r : rows) out.add(toRecord(r));
        return out;
    }

    // --- math ---

    /** Haversine distance in meters. */
    private static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371_000.0; // meters
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat/2) * Math.sin(dLat/2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon/2) * Math.sin(dLon/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return R * c;
    }

    private static final class BBox {
        final double minLat, maxLat, minLon, maxLon;
        BBox(double minLat, double maxLat, double minLon, double maxLon) {
            this.minLat = minLat; this.maxLat = maxLat; this.minLon = minLon; this.maxLon = maxLon;
        }
    }
    /** Bounding box for radius (km). */
    private static BBox bboxFromRadiusKm(double lat, double lon, double radiusKm) {
        double dLat = radiusKm / 111.32; // deg
        double cos = Math.cos(Math.toRadians(lat));
        if (cos < 1e-6) cos = 1e-6;
        double dLon = radiusKm / (111.32 * cos);
        double minLat = clampLat(lat - dLat), maxLat = clampLat(lat + dLat);
        double minLon = wrapLon(lon - dLon),  maxLon = wrapLon(lon + dLon);
        if (minLon <= maxLon) return new BBox(minLat, maxLat, minLon, maxLon);
        // dateline wrap: widen a bit to keep single range query; we’ll refine with Haversine anyway
        return new BBox(minLat, maxLat, -180.0, 180.0);
    }
    private static double wrapLon(double lon) {
        double x = lon % 360.0;
        if (x < -180.0) x += 360.0;
        if (x >= 180.0) x -= 360.0;
        return x;
    }
    private static double clampLat(double lat) {
        if (lat < -90.0) return -90.0;
        if (lat > 90.0) return 90.0;
        return lat;
    }

    // ------------------- Result DTOs -------------------

    /** Immutable record returned by queries. */
    public static final class Record {
        public final String callsign;
        public final double lat, lon;
        public final @Nullable Double alt;
        public final long ts;
        public final String cellPair; // "LLLL-LLLL"
        public Record(String callsign, double lat, double lon, @Nullable Double alt, long ts, String cellPair) {
            this.callsign = callsign;
            this.lat = lat; this.lon = lon; this.alt = alt; this.ts = ts; this.cellPair = cellPair;
        }
        @Override public String toString() {
            return String.format(Locale.US, "%s @ %.6f,%.6f alt=%s ts=%d cell=%s",
                    callsign, lat, lon, alt == null ? "n/a" : String.format(Locale.US, "%.1f", alt), ts, cellPair);
        }
    }

    /** Nearby result (one per callsign, most recent within constraints). */
    public static final class Nearby {
        public final String callsign;
        public final double lat, lon;
        public final @Nullable Double alt;
        public final long ts;
        public final double distanceKm;
        public Nearby(String callsign, double lat, double lon, @Nullable Double alt, long ts, double distanceKm) {
            this.callsign = callsign; this.lat = lat; this.lon = lon; this.alt = alt; this.ts = ts; this.distanceKm = distanceKm;
        }
        @Override public String toString() {
            return String.format(Locale.US, "%s: %.2f km @ %.6f,%.6f ts=%d", callsign, distanceKm, lat, lon, ts);
        }
    }

    // ------------------- Room schema -------------------

    /** Row for each observation. We store both numeric lat/lon and the 4+4 Base36 cells. */
    @Entity(tableName = "locations",
            indices = {
                    @Index(value = {"callsign", "ts"}),
                    @Index(value = {"ts"}),
                    @Index(value = {"lat"}),
                    @Index(value = {"lon"}),
                    @Index(value = {"cellLat4", "cellLon4"}),
                    // Prevent exact duplicates
                    @Index(value = {"callsign","ts","cellLat4","cellLon4"}, unique = true)
            })
    public static class LocationRow {
        @PrimaryKey(autoGenerate = true) public long id;

        @NonNull public String callsign;

        /** milliseconds since epoch (UTC). */
        public long ts;

        /** decimal degrees; center of the 4+4 cell. */
        public double lat;
        public double lon;

        /** optional altitude (meters). */
        @Nullable public Double alt;

        /** 4-char Base36 codes for each axis. */
        @NonNull @ColumnInfo(collate = ColumnInfo.NOCASE) public String cellLat4;
        @NonNull @ColumnInfo(collate = ColumnInfo.NOCASE) public String cellLon4;
    }

    @Dao
    public interface LocationDao {
        @Insert(onConflict = OnConflictStrategy.IGNORE)
        void insertAll(List<LocationRow> batch);

        @Query("SELECT * FROM locations WHERE callsign = :cs ORDER BY ts DESC LIMIT 1")
        LocationRow findMostRecent(String cs);

        @Query("""
               SELECT * FROM locations
               WHERE callsign = :cs AND ts BETWEEN :fromTs AND :toTs
               ORDER BY ts DESC LIMIT :limit
               """)
        List<LocationRow> findForCallsignInRange(String cs, long fromTs, long toTs, int limit);

        @Query("""
               SELECT * FROM locations
               WHERE ts BETWEEN :minTs AND :maxTs
                 AND lat BETWEEN :minLat AND :maxLat
                 AND lon BETWEEN :minLon AND :maxLon
               ORDER BY ts DESC
               LIMIT :limit
               """)
        List<LocationRow> byBoundingBox(double minLat, double maxLat, double minLon, double maxLon,
                                        long minTs, long maxTs, int limit);

        @Query("DELETE FROM locations WHERE callsign = :cs")
        int deleteByCallsign(String cs);

        @Query("DELETE FROM locations")
        int deleteAll();

        @Query("SELECT COUNT(*) FROM locations")
        long countAll();

        @Query("SELECT COUNT(*) FROM locations WHERE callsign = :cs")
        long countByCallsign(String cs);

    }

    @Database(entities = { LocationRow.class }, version = 1, exportSchema = false)
    public abstract static class LocationsDb extends RoomDatabase {
        public abstract LocationDao dao();
    }
}

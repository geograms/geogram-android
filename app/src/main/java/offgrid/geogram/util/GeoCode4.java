package offgrid.geogram.util;

/**
 * Base36 4-char geocode per axis (ASCII only).
 *
 * Latitude  [-90..+90]   -> "0000".."ZZZZ"
 * Longitude [-180..+180) -> "0000".."ZZZZ"
 * Pair format: "LLLL-LLLL" or "LLLLLLLL" (no dash).
 *
 * Capacity / resolution:
 *  STATES = 36^4 = 1,679,616 steps per axis
 *  Lat cell  ≈ 180 / 1,679,616 = 0.000107167°  (~11.93 m)
 *  Lon cell  ≈ 360 / 1,679,616 = 0.000214335°  (~23.86 m @ equator)
 *
 * Improvements vs prior:
 *  - Robust decode: case-insensitive; aliases O→0, I/L→1.
 *  - Longitude normalized to [-180, 180); latitude clamped to [-90, 90].
 *  - Helpers: encode/decode pair, bounds, neighbors, cell size in meters.
 */
public final class GeoCode4 {
    private GeoCode4() {}

    private static final int BASE = 36;
    private static final int LEN  = 4;
    private static final int STATES = 36 * 36 * 36 * 36; // 36^4 = 1,679,616

    private static final char[] DIGITS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
    private static final double METERS_PER_DEG_LAT = 111_320.0; // ~mean

    // ---------------- Encode ----------------

    /** Encode latitude in degrees [-90..+90] to 4 chars (clamped). */
    public static String encodeLat(double latDeg) {
        latDeg = clampLat(latDeg);
        double t = (latDeg + 90.0) / 180.0;  // 0..1
        return indexToCode(toIndex(t));
    }

    /** Encode longitude in degrees to 4 chars; input is normalized to [-180,180). */
    public static String encodeLon(double lonDeg) {
        lonDeg = wrapLon(lonDeg);
        double t = (lonDeg + 180.0) / 360.0; // 0..1
        return indexToCode(toIndex(t));
    }

    /** Encode pair as "LLLL-LLLL". */
    public static String encode(double latDeg, double lonDeg) {
        return encodeLat(latDeg) + "-" + encodeLon(lonDeg);
    }

    /** Encode pair as 8 chars without dash. */
    public static String encodeCompact(double latDeg, double lonDeg) {
        return encodeLat(latDeg) + encodeLon(lonDeg);
    }

    // ---------------- Decode (center of cell) ----------------

    /** Decode latitude code to degrees (center). */
    public static double decodeLat(String code) {
        int idx = codeToIndexStrict(code);
        double t = (idx + 0.5) / STATES;
        return t * 180.0 - 90.0;
    }

    /** Decode longitude code to degrees (center). */
    public static double decodeLon(String code) {
        int idx = codeToIndexStrict(code);
        double t = (idx + 0.5) / STATES;
        // Map back into [-180,180)
        return wrapLon(t * 360.0 - 180.0);
    }

    /** Decode "LLLL-LLLL" or "LLLLLLLL" (no dash). Returns {lat, lon} (cell centers). */
    public static double[] decodePair(String pair) {
        if (pair == null) throw new IllegalArgumentException("pair is null");
        String p = pair.trim().toUpperCase();
        String a, b;
        if (p.length() == 9 && p.charAt(4) == '-') {
            a = p.substring(0, 4);
            b = p.substring(5, 9);
        } else if (p.length() == 8) {
            a = p.substring(0, 4);
            b = p.substring(4, 8);
        } else {
            throw new IllegalArgumentException("Invalid pair format: " + pair);
        }
        return new double[] { decodeLat(a), decodeLon(b) };
    }

    // ---------------- Bounds & neighbors ----------------

    /** Latitude bounds (min,max) in degrees for a 4-char code. */
    public static double[] latBounds(String code) {
        int idx = codeToIndexStrict(code);
        double tMin = (double) idx / STATES;
        double tMax = (double) (idx + 1) / STATES;
        return new double[] { tMin * 180.0 - 90.0, tMax * 180.0 - 90.0 };
    }

    /** Longitude bounds (min,max) in degrees for a 4-char code (may wrap across -180/180). */
    public static double[] lonBounds(String code) {
        int idx = codeToIndexStrict(code);
        double tMin = (double) idx / STATES;
        double tMax = (double) (idx + 1) / STATES;
        double min = wrapLon(tMin * 360.0 - 180.0);
        double max = wrapLon(tMax * 360.0 - 180.0);
        return new double[] { min, max };
    }

    /** Neighbor latitude code by delta steps (negative=South, positive=North), clamped. */
    public static String latNeighbor(String latCode, int delta) {
        int idx = codeToIndexStrict(latCode);
        int n = Math.max(0, Math.min(STATES - 1, idx + delta));
        return indexToCode(n);
    }

    /** Neighbor longitude code by delta steps (negative=West, positive=East), wraps across dateline. */
    public static String lonNeighbor(String lonCode, int delta) {
        int idx = codeToIndexStrict(lonCode);
        int n = floorMod(idx + delta, STATES); // wrap
        return indexToCode(n);
    }

    // ---------------- Cell sizes ----------------

    /** Latitude cell size in degrees (constant). */
    public static double latCellSizeDeg() { return 180.0 / STATES; }

    /** Longitude cell size in degrees (constant). */
    public static double lonCellSizeDeg() { return 360.0 / STATES; }

    /** Latitude cell size in meters (~11.93 m). */
    public static double latCellSizeMeters() { return latCellSizeDeg() * METERS_PER_DEG_LAT; }

    /** Longitude cell size in meters at given latitude. */
    public static double lonCellSizeMeters(double latDeg) {
        double metersPerDegLon = METERS_PER_DEG_LAT * Math.cos(Math.toRadians(latDeg));
        return lonCellSizeDeg() * Math.abs(metersPerDegLon);
    }

    // ---------------- Helpers ----------------

    private static int toIndex(double t) {
        // clamp 0..1, map to 0..STATES-1 with floor
        if (t < 0.0) t = 0.0; else if (t > 1.0) t = 1.0;
        int idx = (int) Math.floor(t * STATES);
        if (idx == STATES) idx = STATES - 1; // handle t == 1.0
        return idx;
    }

    private static String indexToCode(int idx) {
        char[] out = new char[LEN];
        for (int i = LEN - 1; i >= 0; i--) {
            int d = idx % BASE;
            out[i] = DIGITS[d];
            idx /= BASE;
        }
        return new String(out);
    }

    private static int codeToIndexStrict(String code) {
        if (code == null || code.length() != LEN)
            throw new IllegalArgumentException("Code must be exactly 4 chars [0-9A-Z]");
        int v = 0;
        for (int i = 0; i < LEN; i++) {
            int d = charToDigit36(code.charAt(i));
            if (d < 0) throw new IllegalArgumentException("Invalid char '" + code.charAt(i) + "' at pos " + i);
            v = v * BASE + d;
        }
        return v; // 0..STATES-1
    }

    // Accepts 0-9, A-Z; aliases O/o -> 0, I/i/L/l -> 1
    private static int charToDigit36(char c) {
        char u = Character.toUpperCase(c);
        if (u == 'O') u = '0';
        if (u == 'I' || u == 'L') u = '1';
        if (u >= '0' && u <= '9') return u - '0';
        if (u >= 'A' && u <= 'Z') return u - 'A' + 10;
        return -1;
    }

    /** Normalize longitude to [-180, 180). */
    public static double wrapLon(double lonDeg) {
        double x = lonDeg % 360.0;
        if (x < -180.0) x += 360.0;
        if (x >= 180.0) x -= 360.0;
        return x;
    }

    /** Clamp latitude to [-90, 90]. */
    public static double clampLat(double latDeg) {
        if (latDeg < -90.0) return -90.0;
        if (latDeg > 90.0) return 90.0;
        return latDeg;
    }

    private static int floorMod(int x, int m) {
        int r = x % m;
        return (r < 0) ? r + m : r;
    }
}

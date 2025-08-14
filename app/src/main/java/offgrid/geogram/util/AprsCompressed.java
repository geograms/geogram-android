package offgrid.geogram.util;

/**
 * APRS "Compressed Position" (Base91) encoder/decoder, 4 chars per axis.
 * Output format (your request): "LLLL-LLLL" (lat-lon with a dash).
 *
 * Spec mapping (per APRS compressed position):
 *   y = floor( LAT_SCALE * (90  - lat) )          // lat in [-90,+90]
 *   x = floor( LON_SCALE * (180 + lonWrapped) )   // lonWrapped in [-180,+180)
 *   then encode y and x into 4 Base91 chars each, ASCII 33..123.
 *
 * Constants:
 *   BASE = 91, OFFSET = 33 ('!'), CODE_LEN = 4
 *   LAT_SCALE = 380_926.0  -> step ≈ 2.625e-6°  ≈ 0.292 m
 *   LON_SCALE = 190_463.0  -> step ≈ 5.250e-6°  ≈ 0.584 m at equator
 *
 * Notes:
 * - Input latitude is clamped to [-90, +90].
 * - Input longitude is normalized to [-180, +180).
 * - Decoder returns the cell center (inverse of the above mapping).
 */
public final class AprsCompressed {
    private AprsCompressed() {}

    // --- Base91 setup ---
    private static final int BASE = 91;
    private static final int OFFSET = 33;       // '!' .. '{' inclusive (33..123)
    private static final int CODE_LEN = 4;
    private static final long MAX_VAL = pow(BASE, CODE_LEN) - 1; // 91^4 - 1

    // --- APRS compressed position scales (spec) ---
    private static final double LAT_SCALE = 380_926.0;
    private static final double LON_SCALE = 190_463.0;

    // Public helpers for step sizes (useful for docs/tests)
    public static double latStepDeg() { return 1.0 / LAT_SCALE; }              // ~2.625e-6°
    public static double lonStepDeg() { return 1.0 / LON_SCALE; }              // ~5.250e-6°
    public static double latStepMeters() { return latStepDeg() * 111_320.0; }  // ~0.292 m
    public static double lonStepMetersAtEquator() { return lonStepDeg() * 111_320.0; } // ~0.584 m

    // ===== ENCODE =====

    /** Encode latitude (-90..+90) to 4 Base91 chars. */
    public static String encodeLat(double latDeg) {
        latDeg = clampLat(latDeg);
        long y = (long) Math.floor(LAT_SCALE * (90.0 - latDeg)); // 0..(≈68.5M)
        if (y < 0) y = 0;
        if (y > MAX_VAL) y = MAX_VAL;
        return toBase91(y, CODE_LEN);
        // Per spec, you’d follow with symbol table & code in a full APRS packet.
    }

    /** Encode longitude (-180..+180) to 4 Base91 chars (input normalized). */
    public static String encodeLon(double lonDeg) {
        lonDeg = wrapLon(lonDeg);
        long x = (long) Math.floor(LON_SCALE * (180.0 + lonDeg)); // 0..(≈68.5M)
        if (x < 0) x = 0;
        if (x > MAX_VAL) x = MAX_VAL;
        return toBase91(x, CODE_LEN);
    }

    /** Encode pair as "LLLL-LLLL" (lat-lon). */
    public static String encodePairWithDash(double latDeg, double lonDeg) {
        return encodeLat(latDeg) + "-" + encodeLon(lonDeg);
    }

    /** Encode pair as 8 chars without dash. */
    public static String encodePairCompact(double latDeg, double lonDeg) {
        return encodeLat(latDeg) + encodeLon(lonDeg);
    }

    // ===== DECODE (returns center of encoded cell) =====

    /** Decode 4-char Base91 latitude to degrees. */
    public static double decodeLat(String code) {
        long y = fromBase91Strict(code);
        return 90.0 - (y / LAT_SCALE);
    }

    /** Decode 4-char Base91 longitude to degrees (normalized to [-180,180)). */
    public static double decodeLon(String code) {
        long x = fromBase91Strict(code);
        return wrapLon((x / LON_SCALE) - 180.0);
    }

    /** Decode "LLLL-LLLL" or "LLLLLLLL" into {lat, lon}. */
    public static double[] decodePair(String s) {
        if (s == null) throw new IllegalArgumentException("pair is null");
        String p = s.trim();
        String a, b;
        if (p.length() == 9 && p.charAt(4) == '-') { a = p.substring(0,4); b = p.substring(5,9); }
        else if (p.length() == 8) { a = p.substring(0,4); b = p.substring(4,8); }
        else throw new IllegalArgumentException("Expected 8 chars or 4-4 with dash: " + s);
        return new double[] { decodeLat(a), decodeLon(b) };
    }

    // ===== Base91 codec =====

    /** Big-endian Base91 encoding (most-significant digit first). */
    private static String toBase91(long val, int len) {
        char[] out = new char[len];
        for (int i = len - 1; i >= 0; i--) {
            int d = (int) (val % BASE);
            out[i] = (char) (d + OFFSET);
            val /= BASE;
        }
        return new String(out);
    }

    private static long fromBase91Strict(String code) {
        if (code == null || code.length() != CODE_LEN)
            throw new IllegalArgumentException("Code must be exactly " + CODE_LEN + " Base91 chars");
        long v = 0;
        for (int i = 0; i < CODE_LEN; i++) {
            int d = code.charAt(i) - OFFSET;
            if (d < 0 || d >= BASE)
                throw new IllegalArgumentException("Invalid Base91 char at pos " + i + ": '" + code.charAt(i) + "'");
            v = v * BASE + d;
        }
        return v;
    }

    // ===== Math/geo helpers =====

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

    private static long pow(int a, int b) {
        long r = 1;
        for (int i = 0; i < b; i++) r *= a;
        return r;
    }
}

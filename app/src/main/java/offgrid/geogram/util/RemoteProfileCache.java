package offgrid.geogram.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayOutputStream;

/**
 * Cache for remote device profiles.
 * Stores nickname, description, profile picture, and preferred color for each device.
 */
public class RemoteProfileCache {
    private static final String TAG = "RemoteProfileCache";
    private static final String PREFS_NAME = "remote_profiles_cache";

    /**
     * Save a device profile to cache
     */
    public static void saveProfile(Context context, String deviceId,
                                   String nickname, String description,
                                   Bitmap profilePicture, String preferredColor, String npub) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        String prefix = "device_" + deviceId + "_";

        if (nickname != null) {
            editor.putString(prefix + "nickname", nickname);
        }

        if (description != null) {
            editor.putString(prefix + "description", description);
        }

        if (preferredColor != null) {
            editor.putString(prefix + "color", preferredColor);
        }

        if (npub != null) {
            editor.putString(prefix + "npub", npub);
        }

        // Save profile picture as Base64 encoded string
        if (profilePicture != null) {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                profilePicture.compress(Bitmap.CompressFormat.PNG, 100, baos);
                byte[] imageBytes = baos.toByteArray();
                String encodedImage = Base64.encodeToString(imageBytes, Base64.DEFAULT);
                editor.putString(prefix + "picture", encodedImage);
            } catch (Exception e) {
                Log.e(TAG, "Failed to save profile picture for " + deviceId, e);
            }
        }

        // Save timestamp
        editor.putLong(prefix + "timestamp", System.currentTimeMillis());

        editor.apply();
        Log.d(TAG, "Saved profile to cache for device: " + deviceId);
    }

    /**
     * Get cached nickname for a device
     */
    public static String getNickname(Context context, String deviceId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString("device_" + deviceId + "_nickname", null);
    }

    /**
     * Get cached description for a device
     */
    public static String getDescription(Context context, String deviceId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString("device_" + deviceId + "_description", null);
    }

    /**
     * Get cached preferred color for a device
     */
    public static String getPreferredColor(Context context, String deviceId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString("device_" + deviceId + "_color", null);
    }

    /**
     * Get cached npub for a device
     */
    public static String getNpub(Context context, String deviceId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString("device_" + deviceId + "_npub", null);
    }

    /**
     * Get cached profile picture for a device
     */
    public static Bitmap getProfilePicture(Context context, String deviceId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String encodedImage = prefs.getString("device_" + deviceId + "_picture", null);

        if (encodedImage != null) {
            try {
                byte[] imageBytes = Base64.decode(encodedImage, Base64.DEFAULT);
                return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
            } catch (Exception e) {
                Log.e(TAG, "Failed to decode profile picture for " + deviceId, e);
            }
        }

        return null;
    }

    /**
     * Get timestamp when profile was last cached
     */
    public static long getCacheTimestamp(Context context, String deviceId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getLong("device_" + deviceId + "_timestamp", 0);
    }

    /**
     * Check if profile cache exists and is still valid (not older than 6 hours)
     */
    public static boolean isCacheValid(Context context, String deviceId) {
        long timestamp = getCacheTimestamp(context, deviceId);
        if (timestamp == 0) {
            return false;
        }

        long currentTime = System.currentTimeMillis();
        long cacheAge = currentTime - timestamp;
        long sixHoursInMillis = 6 * 60 * 60 * 1000;

        return cacheAge < sixHoursInMillis;
    }

    /**
     * Clear cached profile for a device
     */
    public static void clearProfile(Context context, String deviceId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        String prefix = "device_" + deviceId + "_";
        editor.remove(prefix + "nickname");
        editor.remove(prefix + "description");
        editor.remove(prefix + "color");
        editor.remove(prefix + "npub");
        editor.remove(prefix + "picture");
        editor.remove(prefix + "timestamp");

        editor.apply();
        Log.d(TAG, "Cleared cached profile for device: " + deviceId);
    }

    /**
     * Clear all cached profiles
     */
    public static void clearAll(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().clear().apply();
        Log.d(TAG, "Cleared all cached profiles");
    }
}

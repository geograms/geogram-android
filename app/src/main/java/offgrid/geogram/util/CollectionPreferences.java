package offgrid.geogram.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

public class CollectionPreferences {
    private static final String PREFS_NAME = "collection_preferences";
    private static final String KEY_FAVORITES = "favorites";

    public static void setFavorite(Context context, String collectionId, boolean isFavorite) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> favorites = prefs.getStringSet(KEY_FAVORITES, new HashSet<>());

        // Create a new set to avoid modifying the original
        Set<String> newFavorites = new HashSet<>(favorites);

        if (isFavorite) {
            newFavorites.add(collectionId);
        } else {
            newFavorites.remove(collectionId);
        }

        prefs.edit().putStringSet(KEY_FAVORITES, newFavorites).apply();
    }

    public static boolean isFavorite(Context context, String collectionId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> favorites = prefs.getStringSet(KEY_FAVORITES, new HashSet<>());
        return favorites.contains(collectionId);
    }

    public static Set<String> getAllFavorites(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return new HashSet<>(prefs.getStringSet(KEY_FAVORITES, new HashSet<>()));
    }
}

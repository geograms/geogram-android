package offgrid.geogram.util;

import android.content.Context;
import android.content.SharedPreferences;

public class ProfilePreferences {
    private static final String PREFS_NAME = "profile_preferences";
    private static final String KEY_NICKNAME = "nickname";
    private static final String KEY_DESCRIPTION = "description";
    private static final String KEY_PROFILE_IMAGE_PATH = "profile_image_path";

    public static void setNickname(Context context, String nickname) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_NICKNAME, nickname).apply();
    }

    public static String getNickname(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_NICKNAME, "");
    }

    public static void setDescription(Context context, String description) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_DESCRIPTION, description).apply();
    }

    public static String getDescription(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_DESCRIPTION, "");
    }

    public static void setProfileImagePath(Context context, String imagePath) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_PROFILE_IMAGE_PATH, imagePath).apply();
    }

    public static String getProfileImagePath(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_PROFILE_IMAGE_PATH, "");
    }
}

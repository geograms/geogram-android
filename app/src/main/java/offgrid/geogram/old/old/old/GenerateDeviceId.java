package offgrid.geogram.old.old.old;

import android.content.Context;
import android.provider.Settings;

import java.util.Locale;

import offgrid.geogram.core.Central;

public class GenerateDeviceId {

    public static String getAndroidId(Context context){
        if(context == null){
            return "a1b2c3d4e5f67890";
        }else{
            return Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        }
    }

    public static String generate(Context context) {
        // Retrieve ANDROID_ID
        String androidId = getAndroidId(context);
        if (androidId == null || androidId.isEmpty()) {
            throw new IllegalStateException("ANDROID_ID is unavailable.");
        }

        // Hash ANDROID_ID and truncate to 12 characters
        int hash = 0;
        for (int i = 0; i < androidId.length(); i++) {
            hash = (hash * 31 + androidId.charAt(i)) % 0xFFFFFF; // Modulo 24 bits (6 bytes)
        }

        // Format as a 6-byte hexadecimal string
        return String.format("%06x", hash & 0xFFFFFF).toUpperCase(Locale.ROOT);
    }

}

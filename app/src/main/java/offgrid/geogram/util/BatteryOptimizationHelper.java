package offgrid.geogram.util;

import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.provider.Settings;
import android.net.Uri;

import offgrid.geogram.core.Log;

public class BatteryOptimizationHelper {

    private static final String TAG = "BatteryOptimizationHelper";

    public static void requestIgnoreBatteryOptimizations(Context context) {
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);

        // Check if the app is already exempt
        if (powerManager.isIgnoringBatteryOptimizations(context.getPackageName())) {
            Log.i(TAG, "Battery optimization already ignored for this app.");
            return; // Already exempt
        }

        // Request the user to disable battery optimizations
        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
        intent.setData(Uri.parse("package:" + context.getPackageName()));

        // Check if the intent can be resolved
        if (intent.resolveActivity(context.getPackageManager()) != null) {
            context.startActivity(intent);
        } else {
            Log.e(TAG, "Unable to resolve battery optimization settings intent.");
        }
    }
}

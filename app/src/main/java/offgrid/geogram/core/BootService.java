package offgrid.geogram.core;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import offgrid.geogram.MainActivity;

public class BootService extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.i("BootReceiver", "Device booted. Starting app...");
            Intent activityIntent = new Intent(context, MainActivity.class);
            activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(activityIntent);
        }
    }

}

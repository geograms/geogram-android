package offgrid.geogram.core.old;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import offgrid.geogram.core.Central;

public class ServiceTIO extends Service {

    private static final String TAG = "ServiceTIO";

    @Override
    public void onCreate() {
        super.onCreate();
        // Initialize resources needed for the service
        Log.d(TAG, "Service Created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Your background task logic here
        Log.d(TAG, "Service Started");

        // start the wifi
        Central.getInstance().initializeWiFiControl(this);

        // Example background task (replace with actual logic)
        new Thread(new Runnable() {
            @Override
            public void run() {
                // Perform your background operations here
                for (int i = 0; i < 10; i++) {
                    Log.d(TAG, "Service Running: " + i);
                    try {
                        Thread.sleep(1000); // Simulating some work
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                stopSelf(); // Stop service once done
            }
        }).start();

        return START_STICKY; // Service will be restarted if it gets terminated
    }

    @Override
    public IBinder onBind(Intent intent) {
        // We don't bind, return null
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Clean up any resources
        Log.d(TAG, "Service Destroyed");
    }
}
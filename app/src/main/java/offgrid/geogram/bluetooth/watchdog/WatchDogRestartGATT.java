package offgrid.geogram.bluetooth.watchdog;

import android.content.Context;
import android.os.Handler;

import offgrid.geogram.bluetooth.BlueQueueReceiving;
import offgrid.geogram.bluetooth.other.GattServer;
import offgrid.geogram.core.Log;

public class WatchDogRestartGATT {

    private final long timeBetweenRestarts = 10 * 60 * 1000;

    /*

    Try to keep the GATT server alive and working

     */

    private static final String TAG = "WatchDogGATT";
    private static WatchDogRestartGATT instance;

    private boolean isLoopRunning = false;
    private final Handler handler = new Handler();

    private WatchDogRestartGATT() {
        // Private constructor to prevent instantiation
    }

    /**
     * Returns the singleton instance of the WatchDogMissingParcels.
     *
     * @return The singleton instance.
     */
    public static synchronized WatchDogRestartGATT getInstance() {
        if (instance == null) {
            instance = new WatchDogRestartGATT();
        }
        return instance;
    }

    /**
     * Starts a looped method to handle missing parcels.
     *
     * @param context Context for operations.
     */
    public synchronized void startLoop(Context context) {
        if (isLoopRunning) {
            Log.i(TAG, "Looped method is already running.");
            return;
        }

        isLoopRunning = true;

        Runnable loopRunnable = new Runnable() {
            @Override
            public void run() {
//                if (isLoopRunning == false) return;
//
//                // Looped logic here
//                Log.i(TAG, "Restarting the GATT server due to inactivity");
//
//                if(BlueQueueReceiving.getInstance(context).stillReceivingMessages()){
//                    // don't send messages while we are receiving data
//                    Log.i(TAG, "Not restarting the GATT server because we are receiving data");
//                    return;
//                }
//
//                Log.i(TAG, "Restarting GATT server due to inactivity.");
//                GattServer.getInstance(context).restartGattServer();


                // Schedule the next execution
                handler.postDelayed(this, timeBetweenRestarts); // Run every few minutes
            }
        };

        handler.post(loopRunnable);
    }

    /**
     * Stops the looped method if running.
     */
    public synchronized void stopLoop() {
        if (isLoopRunning) {
            isLoopRunning = false;
            handler.removeCallbacksAndMessages(null);
            Log.i(TAG, "Looped method stopped.");
        } else {
            Log.i(TAG, "Looped method is not running.");
        }
    }
}

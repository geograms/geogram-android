package offgrid.geogram.bluetooth.watchdog;

import static offgrid.geogram.bluetooth.broadcast.LostAndFound.askToResendPackage;

import android.content.Context;
import android.os.Handler;

import java.util.ArrayList;
import java.util.HashMap;

import offgrid.geogram.bluetooth.BlueQueueReceiving;
import offgrid.geogram.bluetooth.eddystone.DeviceFinder;
import offgrid.geogram.bluetooth.other.comms.BluePackage;
import offgrid.geogram.core.Log;
import offgrid.geogram.devices.DeviceReachable;

public class WatchDogMissingParcels {

    private final long
            timeForMessageIsNotMoving = 15 * 1000,
            timeToQuitAskingForRepeat = 3 * 60 * 1000;


    /*

    The purpose of this class is to solve messages as seen below where
    you can see parcels being sent but then suddenly stopping

2025-01-07_20:33:30 [BlueReceiver] Received data from 57:C0:20:C7:0E:80: VY:007:HCNA:B:2A1A78
2025-01-07_20:33:32 [BlueReceiver] Received data from 57:C0:20:C7:0E:80: VY000:is this able t
2025-01-07_20:33:33 [BlueReceiver] Received data from 57:C0:20:C7:0E:80: VY001:o adjust for l
2025-01-07_20:33:35 [BlueReceiver] Received data from 57:C0:20:C7:0E:80: VY002:arger composit
2025-01-07_20:33:36 [BlueReceiver] Received data from 57:C0:20:C7:0E:80: VY003:ions of texts?
2025-01-07_20:33:38 [BlueReceiver] Received data from 57:C0:20:C7:0E:80: VY004: hmm.. it seem
2025-01-07_20:33:39 [BlueReceiver] Received data from 57:C0:20:C7:0E:80: VY005:s that it does

    Our purpose is to discover when these packages are incomplete and without activity
    for more than 15 seconds, try to diagnose why and then ask new packages to be sent.

     */



    private static final String TAG = "WatchDogMissingParcels";
    private static WatchDogMissingParcels instance;

    private boolean isLoopRunning = false;
    private final Handler handler = new Handler();

    private WatchDogMissingParcels() {
        // Private constructor to prevent instantiation
    }

    /**
     * Returns the singleton instance of the WatchDogMissingParcels.
     *
     * @return The singleton instance.
     */
    public static synchronized WatchDogMissingParcels getInstance() {
        if (instance == null) {
            instance = new WatchDogMissingParcels();
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
                if (isLoopRunning == false){
                    return;
                }

                // The looped logic here
                //Log.i(TAG, "Checking for missing parcels");
                try {
                    // keep running it
                    runTask(context);

                } catch (Exception e) {
                    Log.e(TAG, "Exception happened: " + e.getMessage());
                }


                // Schedule the next execution
                handler.postDelayed(this, 15000); // Run every 15 seconds
            }
        };
        handler.post(loopRunnable);
    }

    private void runTask(Context context){
        // list the current packages being received
        HashMap<String, BluePackage> items = BlueQueueReceiving.getInstance(context).packagesReceivedRecently;
        ArrayList<BluePackage> itemsToRemove = new ArrayList<>();

        for(BluePackage item : items.values()){
            // no need to include the ones that are already valid
            if (item.allParcelsReceivedAndValid()) {
                itemsToRemove.add(item);
                continue;
            }
            // is this package not moving?
            if (item.timeSinceLastPing() < timeForMessageIsNotMoving) {
                continue;
            }

            // is it time to quite asking for a repeated message?
            if (item.timeSinceLastPing() > timeToQuitAskingForRepeat) {
                itemsToRemove.add(item);
                continue;
            }

            // time to ask for resending the package
            String packageId = item.getId();
            String deviceId = item.getDeviceId();
            // get the most up-to-date MAC address for the device
            DeviceReachable device = DeviceFinder.getInstance(context).getDeviceMap().get(deviceId);
            if(device == null){
                Log.e(TAG, "Device not found for id: " + deviceId);
                continue;
            }
            // resend the package
            askToResendPackage(device.getMacAddress(), packageId, context);
        }

        // remove completed items from the queue
        if(itemsToRemove.isEmpty() == false){
            for(BluePackage item : itemsToRemove){
                items.remove(item.getId());
                Log.i(TAG, "Removed package from received queue: " + item.getId());
            }
        }
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

package offgrid.geogram.bluetooth;

import static offgrid.geogram.bluetooth.Bluecomm.timeBetweenMessages;

import android.content.Context;

import java.util.HashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import offgrid.geogram.bluetooth.other.comms.BluePackage;
import offgrid.geogram.bluetooth.other.comms.BlueQueueParcel;
import offgrid.geogram.core.Log;

/**
 * The queues for outgoing and incoming transmissions.
 * This class is implemented as a singleton to ensure only one instance of the queue exists.
 */
public class BlueQueueSending {

    // Queue for outgoing packages
    public final HashMap<String, BluePackage> packagesToSend = new HashMap<>();

    // Queue for outgoing parcels
    private final BlockingQueue<BlueQueueParcel> queueParcelToSend = new LinkedBlockingQueue<>();

    // Scheduler for message dispatching
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static final String TAG = "BlueQueueSending";
    private static BlueQueueSending instance = null;
    private final Context context;

    // Private constructor to prevent external instantiation
    private BlueQueueSending(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * Provides the singleton instance of the BlueQueues class.
     *
     * @return The singleton instance of BlueQueues.
     */
    public static BlueQueueSending getInstance(Context context) {
        if (instance == null) {
            instance = new BlueQueueSending(context);
        }
        return instance;
    }

    public void start() {
        scheduler.scheduleWithFixedDelay(this::processQueue, 0, timeBetweenMessages, TimeUnit.MILLISECONDS);
    }

    /**
     * Adds a package to be sent to other devices for future memory
     * in cases where it is needed to send again
     * @param packageToSend the data to be dispatched
     */
    public void addPackageToSend(BluePackage packageToSend) {
        String uid = packageToSend.getId();
        HashMap<String, BluePackage> packagesReceivedRecently
                = BlueQueueReceiving.getInstance(context).packagesReceivedRecently;
        // Avoid duplicates
        if (packagesReceivedRecently.containsKey(uid)) {
            return;
        }
        Log.i(TAG, "Adding package to send: " + uid);
        packagesToSend.put(uid, packageToSend);
    }

    /**
     * Adds an item to the queue.
     *
     * @param item The BlueQueueItem to be sent elsewhere
     */
    public void addQueueToSend(BlueQueueParcel item) {
        if (item == null || item.getData() == null) {
            Log.e(TAG, "Null item received for sending");
            return;
        }

        String dataCheck = item.getMacAddress() + item.getTimestamp() + item.getData();

        // Iterate over the queue to check for duplicates
        for (BlueQueueParcel existingItem : queueParcelToSend) {
            String existingDataCheck = existingItem.getMacAddress() + existingItem.getTimestamp() + existingItem.getData();
            if (existingDataCheck.equals(dataCheck)) {
                Log.i(TAG, "Duplicate item detected, not adding to queue");
                return; // Avoid adding a duplicate
            }
        }

        queueParcelToSend.add(item);
    }


    /**
     * Processes the message queue.
     */
    private void processQueue() {
        try {
            if (queueParcelToSend.isEmpty()) {
                return;
            }

            if (BlueQueueReceiving.getInstance(context).stillReceivingMessages()) {
                return;
            }

            BlueQueueParcel item = queueParcelToSend.poll();
            if (item != null && item.getData() != null) {
                Bluecomm.getInstance(context).writeData(item);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error sending data: " + e.getMessage());
        }
    }

    /**
     * Checks if a specific message is already on the queue to be dispatched
     * to another device and avoid repetition of messages.
     * @param message to be compared
     * @return true when it is a duplicate message
     */
    public boolean isAlreadyOnQueueToSend(String message, String macAddress) {
        for (BlueQueueParcel item : queueParcelToSend) {
            // Needs to match the mac address
            if (!item.getMacAddress().equals(macAddress)) {
                continue;
            }
            // Is the data same?
            if (item.getData().equals(message)) {
                return true;
            }
        }
        return false;
    }

    public void clear() {
        packagesToSend.clear();
        queueParcelToSend.clear();
    }
}

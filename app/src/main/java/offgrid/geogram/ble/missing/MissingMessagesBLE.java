package offgrid.geogram.ble.missing;

import java.util.ArrayList;
import java.util.HashMap;

import offgrid.geogram.ble.BluetoothSender;

/**
 * Ask to send back specific parcels from messages
 */
public class MissingMessagesBLE {
    private static final String TAG = "MissingMessagesBLE";

    private HashMap<String, Long> missingParcels = new HashMap<String, Long>();

    public static void addToQueue(String parcelId, String... parcelMissingNumbers){

    }
    /*
    Example to put a message on the queue to send
        String message = "R>" + messageId + parcelMissingNumber;
        BluetoothSender.getInstance(null).sendMessage(message);
     */

}

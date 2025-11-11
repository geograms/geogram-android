package offgrid.geogram.ble.missing;

import java.util.ArrayList;
import java.util.HashMap;

import offgrid.geogram.ble.BluetoothMessage;
import offgrid.geogram.ble.BluetoothSender;
import offgrid.geogram.core.Log;
import offgrid.geogram.devices.ConnectionType;

/**
 * Ask to send back specific parcels from messages
 */
public class MissingMessagesBLE {
    private static final String TAG = "MissingMessagesBLE";
    private static final int maxMessagesArchived = 1000;

    public static HashMap<String, ArchivedMessageBLE> messagesArchived = new HashMap<>();

    //private static HashMap<String, MissedParcel> missingParcels = new HashMap<String, MissedParcel>();


    /**
     * Add a message to the archive so that later we can send it back if needed
     * @param message
     */
    public static void addToArchive(BluetoothMessage message){
        String messageId = message.getId();
        // avoid duplicates
        if(messagesArchived.containsKey(messageId)){
            return;
        }

        // do we have room to add new messages?
        cleanupArchive();

        // can now be added
        messagesArchived.put(messageId, new ArchivedMessageBLE(message));
    }

    /**
     * We store the archive in memory, not on the disk.
     * Therefore we avoid filling it up too much with messages to save memory space
     */
    private static void cleanupArchive() {
        if(messagesArchived.size() < maxMessagesArchived){
            return;
        }

        // time limit is 1 hour
        long timePeriod = 60 * 60 * 1000;
        long timeNow = System.currentTimeMillis();

        // a clean up needs to be performed - remove expired messages
        // Use ArrayList to avoid ConcurrentModificationException
        ArrayList<String> toRemove = new ArrayList<>();
        for(String id : messagesArchived.keySet()){
            ArchivedMessageBLE archived = messagesArchived.get(id);
            if(archived == null){
                continue;
            }
            long timeFirstReceived = archived.timeFirstReceived;
            long timeToExpire = timeNow - timeFirstReceived;
            if(timeToExpire > timePeriod){
                toRemove.add(id);
            }
        }

        for(String id : toRemove){
            messagesArchived.remove(id);
        }

        // in case we are being spammed and cleanup didn't help, delete all as emergency measure
        if(messagesArchived.size() >= maxMessagesArchived){
            Log.i(TAG, "Emergency cleanup: clearing all archived messages due to spam");
            messagesArchived.clear();
        }
    }

    /**
     * Add the request to the queue and send it ocasionally until a reply is provided
     */
    public static void addToQueue(
            ConnectionType connectionType,
            String messageId,
            String parcelNumberText){

        // use the original id as source to know
        // if this message was here before or not
        //String id = messageId+parcelNumberText;
        int parcelNumber;

        try {
            // try to get the number
            parcelNumber = Integer.parseInt(parcelNumberText);
        }catch (Exception e){
            Log.e(TAG, "Unable to parse parcel number: " + parcelNumberText);
            return;
        }
        // don't care with duplicated requests
//        if(missingParcels.containsKey(id)){
//            return;
//        }

        // we can only react when the message is archived
        if(messagesArchived.containsKey(messageId) == false){
            Log.i(TAG, "Unable to find archived message with ID: " + messageId);
            return;
        }

        // get the message itself
        ArchivedMessageBLE messageArchived = messagesArchived.get(messageId);
        if(messageArchived == null){
            Log.i(TAG, "Unable to find archived message with ID: " + messageId);
            return;
        }
        String[] parcelData = messageArchived.message.getMessageParcels();

        // Check bounds before accessing array
        if(parcelNumber < 0 || parcelNumber >= parcelData.length){
            Log.i(TAG, "Parcel number " + parcelNumber + " out of bounds (0-" + (parcelData.length - 1) + ")");
            return;
        }

        String parcelText = parcelData[parcelNumber];
        if(parcelText == null){
            Log.i(TAG, "Unable to find parcel with number: " + parcelNumber);
            return;
        }
        BluetoothSender.getInstance(null).sendMessage(parcelText);

        // this is a new request, place it as request
//        MissedParcel parcel = new MissedParcel(System.currentTimeMillis(), parcelNumber, messageId);
//        missingParcels.put(id, parcel);
        Log.i(TAG, "Repeated parcel request for: " + messageId + parcelNumberText);
    }

}

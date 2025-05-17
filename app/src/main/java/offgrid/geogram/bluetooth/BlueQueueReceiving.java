package offgrid.geogram.bluetooth;

import android.content.Context;

import java.util.ArrayList;
import java.util.HashMap;

import offgrid.geogram.bluetooth.broadcast.BroadcastMessage;
import offgrid.geogram.bluetooth.other.comms.BluePackage;
import offgrid.geogram.core.Log;

/**
 * The queues for outgoing and incoming transmissions.
 * This class is implemented as a singleton to ensure only one instance of the queue exists.
 */
public class BlueQueueReceiving {

    private static BlueQueueReceiving instance = null;
    //private final Context context;

   // The queue for incoming transmissions
    //private final CopyOnWriteArrayList<BlueQueueItem> queueToReceive = new CopyOnWriteArrayList<>();
    // queue to store individual one-line messages to be received
    //private Thread queueThreadToReceive = null;

    // Queue for data packages being built with what we receive from other devices
    // <UID, BluePackage>
    public final HashMap<String, BluePackage> packagesReceivedRecently = new HashMap<>();


    // These are the messages visible from the UI
    // This gets updated even when the UI is not selected
    private final ArrayList<BroadcastMessage>
            messagesReceivedAsBroadcast = new ArrayList<>();



    private static final String TAG = "BlueQueueReceiving";

    // Private constructor to prevent external instantiation
    private BlueQueueReceiving(Context context) {
        //this.context = context.getApplicationContext();
    }

    /**
     * Provides the singleton instance of the BlueQueues class.
     *
     * @return The singleton instance of BlueQueues.
     */
    public static BlueQueueReceiving getInstance(Context context) {
        if (instance == null) {
            instance = new BlueQueueReceiving(context);
        }
        return instance;
    }



    public void start(){
//        if(queueThreadToReceive == null){
//            startThreadToReceiveMessagesInQueue();
//        }
    }

    /**
     * Checks if there are received messages that are
     * still with activity
     * @return true when we are receiving data
     */
    public boolean stillReceivingMessages() {
        for(BluePackage pkg : packagesReceivedRecently.values()){
            if(pkg.isStillActive()){
                return true;
            }
        }
        return false;
    }

    public ArrayList<BroadcastMessage> getMessagesReceivedAsBroadcast(){
        return messagesReceivedAsBroadcast;
    }

    /**
     * These are messages received for the broadcast chat
     */
    public void addBroadcastMessage(BroadcastMessage message){
        // avoid duplicate cases
        for(BroadcastMessage messageReceived : messagesReceivedAsBroadcast){
            if(messageReceived.getDeviceId().equals(message.getDeviceId()) == false){
                continue;
            }
            // same message? then avoid it
            if(messageReceived.getMessage().equals(message.getMessage())){
                return;
            }
        }
        Log.i(TAG, "Adding broadcast message: " + message.getMessage());
        messagesReceivedAsBroadcast.add(message);
    }

    public void clear() {
        packagesReceivedRecently.clear();
        messagesReceivedAsBroadcast.clear();
    }
}

package offgrid.geogram.wifi.comm;

import java.util.concurrent.CopyOnWriteArrayList;

import offgrid.geogram.core.Log;
import offgrid.geogram.wifi.messages.Message;
import offgrid.geogram.wifi.messages.MessageHello_v1;
import offgrid.geogram.wifi.messages.routine.MessageAddedToQueue_v1;
import offgrid.geogram.wifi.messages.routine.MessageDuplicated_v1;

/**
 * Dump all incoming Wi-Fi messages here
 * and they will magically be routed to
 * the right location.
 */
public class WiFiReceiver {

    private static final String TAG = "WiFiReceiver";;
    // messages that we receive over WiFi
    public static CopyOnWriteArrayList<Message> messagesReceived = new CopyOnWriteArrayList<>();

    // Handler to run periodic tasks
    private static final int CHECK_INTERVAL_MS = 1000; // 1 second

    private static final Runnable messageProcessor = new Runnable() {
        @Override
        public void run() {
            // Process messages
            processMessagesInQueue();

            try {
                Thread.sleep(CHECK_INTERVAL_MS);
            } catch (InterruptedException e) {
                Log.e(TAG, "Error processing messages: " + e.getMessage());
            }
        }
    };



    /**
     * Connect to the local Wi-Fi sender to pretend this
     * is a remote device sender. This is used only for
     * testing purposes and development of the message API.
     */
    public static void syncLocally() {
        // get the local messages
        CopyOnWriteArrayList<WiFiMessage> messagesToReceive
                = WiFiSender.getInstance(null).getMessages();
        // add them to the received queue
        for (WiFiMessage messageWiFi : messagesToReceive) {
            processReceivedMessage(messageWiFi.message);
        }
    }

    private static void processMessagesInQueue() {
        while (messagesReceived.isEmpty() == false) {
            Message message = messagesReceived.remove(0);
            processMessage(message);
            // remove the message from the queue
            messagesReceived.remove(message);
        }
    }

    /**
     * Process the message internally
     * @param message message that was in the queue,
     *                it will be removed after running
     *                this method.
     */
    private static void processMessage(Message message) {

        switch(message.getCid()){
            default:
                Log.e(TAG, "Unknown message: " + message.getCid());
        }

    }

    public static Message processReceivedMessage(Message message) {
        // do we need to reply right now?
        if (message.getCid() == CID.HELLO) {
            // say hello on both directions
            Log.d(TAG, "Received Hello message");
            return new MessageHello_v1();
        }

        // we don't handle this message, stop here
        return null;

//        // is it duplicated?
//        for (Message messageExisting : messagesReceived) {
//            // only accept one message with the same timestamp to reduce duplicates
//            if (messageExisting.getTimeStamp() == message.getTimeStamp()) {
//                return new MessageDuplicated_v1();
//            }
//        }
//        // Add the message to the queue
//        messagesReceived.add(message);
//        return new MessageAddedToQueue_v1();
    }



}

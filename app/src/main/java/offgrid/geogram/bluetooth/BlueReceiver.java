package offgrid.geogram.bluetooth;

import static offgrid.geogram.bluetooth.broadcast.BroadcastSender.sendPackageToDevice;
import static offgrid.geogram.bluetooth.other.comms.BlueCommands.oneLineAcknowledgement;
import static offgrid.geogram.bluetooth.other.comms.BlueCommands.oneLineCommandBio;
import static offgrid.geogram.bluetooth.other.comms.BlueCommands.oneLineCommandGapBroadcast;
import static offgrid.geogram.bluetooth.other.comms.BlueCommands.gapREPEAT;
import static offgrid.geogram.bluetooth.other.comms.BlueCommands.oneLineCommandPing;

import android.content.Context;

import java.util.HashMap;

import offgrid.geogram.bluetooth.broadcast.BroadcastMessage;
import offgrid.geogram.bluetooth.other.comms.BlueCommands;
import offgrid.geogram.bluetooth.other.comms.BluePackage;
import offgrid.geogram.core.Log;
import offgrid.geogram.bluetooth.broadcast.LostAndFound;
import offgrid.geogram.database.BioDatabase;
import offgrid.geogram.database.BioProfile;
import offgrid.geogram.devices.chat.ChatDatabaseWithDevice;
import offgrid.geogram.devices.chat.ChatMessage;
import offgrid.geogram.devices.chat.ChatMessages;
import offgrid.geogram.events.EventControl;
import offgrid.geogram.events.EventType;

/**
 * This class stores the requests that are made from outside
 * devices into this one. Since messages need to be shipped
 * in small little packages, this is where we keep track of
 * them.
 */
public class BlueReceiver {

    // the requests currently active
    // this is only used for read operations and will soon be phased out
    //private final HashMap<String, BluePackage> requests = new HashMap<>();

    // Static instance of the singleton
    private static BlueReceiver instance;
    private static final String TAG = "BlueReceiver";

    // Private constructor to prevent instantiation from outside
    private BlueReceiver() {
        //startCleanupThread(); // Start cleanup thread upon initialization
    }

    /**
     * Provides the global instance of the BlueCentral class.
     *
     * @return The singleton instance of BlueCentral.
     */
    public static synchronized BlueReceiver getInstance() {
        if (instance == null) {
            instance = new BlueReceiver();
        }
        return instance;
    }

    /**
     * A new request has arrived. First we run the request
     * and get the result. After that part we will ship the
     * results to the other device.
     * Any previous requests from the same MAC address will
     * be erased from the queue.
     *
     * @param macAddress MAC address of the bluetooth device
     * @param receivedData request that was received
     */
    public void receivingDataFromDevice(String macAddress, String receivedData, Context context) {
        // avoid null addresses
        if (macAddress == null) {
            Log.e(TAG, "Null MAC address received");
            return;
        }

        // output a log of what is received
        Log.i(TAG, "Received data from " + macAddress + ": " + receivedData);

        // needs to always contain an :
        if(receivedData == null || !receivedData.contains(":")){
            Log.e(TAG, "Invalid data received: " + receivedData);
            return;
        }


        // was this parcel received before?
        if(wasSameParcelReceivedRecently(macAddress, receivedData, context)){
            return;
        }

        // examples of messages:
        // HX:005:TCHA:B:2a1a78 -> data header
        // HX000:/bio:{"color": -> data parcel
        // >B:REPEAT:HZ         -> single command

        // is this a single command?
        if(receivedData.startsWith(">")){
            processSingleCommandReceived(macAddress, receivedData, context);
            return;
        }

        String[] data = receivedData.split(":");
        String UID = data[0].substring(0, 2);
        HashMap<String, BluePackage> packagesBeingReceived =
                BlueQueueReceiving.getInstance(context).packagesReceivedRecently;
        // with a valid device, is there already a write request?
        BluePackage packageBeingReceived;
        // does it already exist?
        if(packagesBeingReceived.containsKey(UID)){
            packageBeingReceived = packagesBeingReceived.get(UID);
        }else{
            // not yet, then let's create a new one
            packageBeingReceived = BluePackage.createReceiver(receivedData);
            // first message should be a header, is it valid?
            if(packageBeingReceived.isValidHeader()){
                packagesBeingReceived.put(UID, packageBeingReceived);
                // no need to continue, first message is the header
                return;
            }else{
                Log.e(TAG, "Invalid header received for write operation: " + receivedData);
                LostAndFound.decodeLostPackage(receivedData, macAddress, context);
                return;
            }
        }
        // at this point our package should NOT be null
        if(packageBeingReceived == null){
            Log.e(TAG, "Null package received");
            return;
        }

        // avoid replay actions, this parcel is complete
        if(packageBeingReceived.allParcelsReceivedAndValid()){
            return;
        }

        // next messages should be an increment
        packageBeingReceived.receiveParcel(receivedData);

        // when we detect a missing parcel, try to get it first
        // it will retry to ask for the package again
        if(LostAndFound.hasMissingParcels(packageBeingReceived, macAddress, context)){
            return;
        }

        // when the message is complete, process the command inside
        if(packageBeingReceived.allParcelsReceivedAndValid()){
            processReceivedRequest(macAddress, packageBeingReceived, context);
            Log.i(TAG, "Full data received from " + macAddress
                    + " -> " + packageBeingReceived.getData());
            // remove this message from the queue
            // don't remove yet to avoid replay actions
            //packagesBeingReceived.remove(UID);

            // trigger an event that a message was received
            EventControl.startEvent(EventType.BLUETOOTH_PACKAGE_RECEIVED, packageBeingReceived);
        }

    }

    /**
     * Reduce CPU effort when the same parcel was delivered recently before
     * from the same MAC address
     */
    private boolean wasSameParcelReceivedRecently(String macAddress, String receivedData, Context context) {
        // go through all the packages
        for(BluePackage packageReceivedEarlier : BlueQueueReceiving.getInstance(context).packagesReceivedRecently.values()){
            // check each of the received parcels
            for(String data : packageReceivedEarlier.getDataParcels()){
                // no data on the expected parcel? don't care
                if(data == null){
                    continue;
                }
                // there is a match, let's inform about this
                if(data.equalsIgnoreCase(receivedData)){
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * A single command starting with > was received
     * @param macAddress MAC address of the device which sent the message
     * @param receivedData message received
     * @param context context of the application
     */
    private void processSingleCommandReceived(String macAddress, String receivedData, Context context) {
        // example of what we expect to receive
        // >B:XY001
        // this means: broadcast message with identification XY requests parcel 1
        // >B:REPEAT:TZ
        // this means to repeat the TZ package again
        if(receivedData.startsWith(oneLineCommandGapBroadcast)){
            String[] data = receivedData.split(":");
            String action = data[1]; // e.g. XY001 or REPEAT
            // is this a repeat request?
            if(action.equals(gapREPEAT)){
                String packageId = data[2];
                BluePackage packageToResend =
                        BlueQueueSending.getInstance(context).packagesToSend.get(packageId);
                // there is a package that we can send again
                if(packageToResend != null){
                    Log.i(TAG, "Gap data: repeating package: " + packageId);
                    sendPackageToDevice(macAddress, packageToResend, context);
                    return;
                }
            }
            // otherwise assume that "action" is something like XY001
            String id = action.substring(0, 2);
            String parcelNumber = data[1].substring(2);
            Log.i(TAG, "Gap data: sending again id: "
                    + id + " and parcel " + parcelNumber);
            // send back the parcel to the other device
            HashMap<String, BluePackage> packagesSentBefore = BlueQueueSending.getInstance(context).packagesToSend;
            BluePackage packageToSendAgain = packagesSentBefore.get(id);
            if(packageToSendAgain == null){
                Log.e(TAG, "GapData: No write action found for id: " + id);
                return;
            }
            int value;
            try {
                value = Integer.parseInt(parcelNumber);
            } catch (NumberFormatException e){
                Log.e(TAG, "GapData: Invalid parcel number received: " + parcelNumber);
                return;
            }
            String textToSendAgain = packageToSendAgain.getSpecificParcel(value);
            Log.i(TAG, "GapData: Sending parcel: " + textToSendAgain);
            Bluecomm.getInstance(context).writeData(macAddress, textToSendAgain);
        }

        // received a message like >PING:713321
        // this is an update to the MAC address of that device Id
        if(receivedData.startsWith(oneLineCommandPing)){
            String data = receivedData.substring(oneLineCommandPing.length());
            BioDatabase.ping(data, macAddress, context);
            return;
        }

        // received a message like >PING:713321
        // this is an update to the MAC address of that device Id
        if(receivedData.startsWith(oneLineCommandBio)){
            //String data = receivedData.substring(oneLineCommandPing.length());
            BioDatabase.sendBio(macAddress, context);
            return;
        }

        // received a message like >ACK:QB
        // this is an update to the MAC address of that device Id
        if(receivedData.startsWith(oneLineAcknowledgement)){
            EventControl.startEvent(EventType.BLUETOOTH_ACKNOWLEDGE_RECEIVED, receivedData);
            return;
        }


        // single command is not yet supported
        Log.i(TAG, "Single command received and ignored: " + receivedData);

    }

    /**
     * Process all commands arriving to the
     */
    private void processReceivedRequest(
            String macAddress,
            BluePackage packageReceived,
            Context context) {
        //Log.i(TAG, "Received command: " + received);
        switch (packageReceived.getCommand()) {
            case C -> {
                saveDirectChatMessage(macAddress, packageReceived, context);
                return;
            }
            case B -> {
                saveBroadCastMessage(macAddress, packageReceived, context);
                return;
            }
            default -> {
            }
        }
    }

    /**
     * Received a direct chat message from the other device
     * @param macAddress MAC address of the device sending this message
     * @param packageReceived message received
     * @param context context of the application
     */
    private void saveDirectChatMessage(String macAddress, BluePackage packageReceived, Context context) {
        Log.i(TAG, "Direct message received: " + packageReceived.getData());
        // get the device Id
        String deviceId = packageReceived.getDeviceId();
        // get the respective database
        ChatMessages deviceMessages =
                ChatDatabaseWithDevice.getInstance(context).getMessages(deviceId);
        // create a message
        ChatMessage message = new ChatMessage(deviceId, packageReceived.getData());
        // add it to the database
        deviceMessages.add(message);
        // save it to disk
        ChatDatabaseWithDevice.getInstance(context).saveToDisk(deviceId, deviceMessages);
        // inform the UI (start the connected events)
        EventControl.startEvent(EventType.MESSAGE_DIRECT_RECEIVED, message, true);
    }

    /**
     * Handle the broadcast message when being received from the outside
     */
    private void saveBroadCastMessage(
            String macAddress,
            BluePackage packageReceived,
            Context context) {

        String messageText = packageReceived.getData();
        String deviceId = packageReceived.getDeviceId();

        // handle the case of bio messages
        // e.g.: /bio:{"color":"Pink","id":"2A1A78","nick":"Eva"} from 2A1A78
        String data = packageReceived.getData();
        if(data.startsWith(BlueCommands.tagBio)){
            // save this to disk
            BioDatabase.save(deviceId, BioProfile.fromJson(
                    data.substring(BlueCommands.tagBio.length())
            ), context);
            Log.i(TAG, "Bio data received and saved: " + data);
            //return;
        }

        // this message was written by someone else
        BroadcastMessage messageReceived = new BroadcastMessage(messageText, deviceId, false);
        // place the message on the list
        BlueQueueReceiving.getInstance(context).addBroadcastMessage(messageReceived);
    }


}

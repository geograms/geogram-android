/**
 * The {@code BluePackage} class facilitates the transmission of large data as smaller parcels
 * over a Bluetooth communication channel. It handles parcel splitting, parcel tracking, and provides
 * utility methods to manage and request specific parcels.
 *
 * <h3>Example Usage:</h3>
 * <pre>{@code
 *
 * This class is essential for managing data transfers where the size of a single transmission is limited.
 */
package offgrid.geogram.ble;


import static offgrid.geogram.ble.BluetoothCentral.maxSizeOfMessages;

import java.util.Random;
import java.util.TreeMap;

import offgrid.geogram.core.Log;

public class BluetoothMessage {

    private static final int TEXT_LENGTH_PER_PARCEL = maxSizeOfMessages;
    private boolean messageCompleted = false;
    private static final String TAG = "BluetoothMessage";
    private String
            id = null, // unique ID
            idFromSender = null, // who sent this message
            idDestination,
            message = null,
            checksum = null;
    private final TreeMap<String, String> messageBox = new TreeMap<>();

    private final long timeStamp = System.currentTimeMillis();

    public BluetoothMessage(String idFromSender, String idDestination, String messageToSend) {
        this.id = generateRandomId();
        this.idFromSender = idFromSender;
        this.idDestination = idDestination;
        this.message = messageToSend;
        this.checksum = calculateChecksum(message);
        splitDataIntoParcels();
    }

    public BluetoothMessage() {
    }

    /**
     * Calculates a 4-letter checksum for the given data.
     *
     * @param data The input data for which to calculate the checksum.
     * @return A 4-letter checksum.
     */
    public String calculateChecksum(String data) {
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("Data cannot be null or empty");
        }

        int sum = 0;
        for (char c : data.toCharArray()) {
            sum += c; // Add ASCII value of each character
        }

        // Reduce the sum to 4 letters
        char[] checksum = new char[4];
        for (int i = 0; i < 4; i++) {
            checksum[i] = (char) ('A' + (sum % 26));
            sum /= 26; // Shift to the next letter
        }
        return new String(checksum);
    }

    /**
     * Splits the data into smaller parcels based on TEXT_LENGTH_PER_PARCEL.
     * Each parcel will contain at most {@code TEXT_LENGTH_PER_PARCEL} characters.
     */
    private void splitDataIntoParcels() {
        int dataLength = message.length();
        int messageParcelsTotal = (int) Math.ceil((double) message.length() / TEXT_LENGTH_PER_PARCEL);

        // add the header
        String uidHeader = id + "0";
        String header =
                uidHeader
                + ":"
                + idFromSender
                + ":"
                + idDestination
                + ":"
                + checksum
                ;
        messageBox.put(uidHeader, header);

        for (int i = 0; i < messageParcelsTotal; i++) {
            int start = i * TEXT_LENGTH_PER_PARCEL;
            int end = Math.min(start + TEXT_LENGTH_PER_PARCEL, dataLength);
            String text = message.substring(start, end);
            int value = i + 1;
            String uid = id + value;
            messageBox.put(uid,
                    uid
                    + ":"
                    + text
            );
        }
    }
    /**
     * Generates a unique random ID using two bytes for each data transmission.
     *
     * @return A unique 2-byte random ID as a hexadecimal string.
     */
    public String generateRandomId() {
        Random random = new Random();
        char firstChar = (char) ('A' + random.nextInt(26)); // Random letter A-Z
        char secondChar = (char) ('A' + random.nextInt(26)); // Random letter A-Z
        return "" + firstChar + secondChar;
    }

    public int getMessageParcelsTotal() {
        return this.messageBox.size();
    }

    public String[] getMessageParcels() {
        //return messageParcels;
        return getMessageBox().values().toArray(new String[0]);
    }

    public String getChecksum() {
        return checksum;
    }

    public String getId() {
        return id;
    }

    public String getIdDestination() {
        return idDestination;
    }

    public String getIdFromSender() {
        return idFromSender;
    }

    public String getMessage() {
        return message;
    }

    public TreeMap<String, String> getMessageBox() {
        return messageBox;
    }

    public String getOutput() {
        String output = "";
        if(messageBox.isEmpty()){
            return "";
        }
        for (String key : messageBox.keySet()) {
            output += messageBox.get(key) + " | ";
        }
        return output.substring(0, output.length() - 3);
    }

    public String getAuthor() {
        return idFromSender;
    }

    public void addMessageParcel(String messageParcel) {
        // needs to be a parcel
        if(messageParcel.contains(":") == false){
            return;
        }
        // separate the header from the data
        String[] parcel = messageParcel.split(":");
        String parcelId = parcel[0];
        // is it already repeated?
        if(messageBox.containsKey(parcelId)){
            return;
        }
        // add this parcel on our collection
        messageBox.put(parcelId, messageParcel);

        // get the index value
        int index = -1;
        try{
            String value = parcelId.substring(2);
            index = Integer.parseInt(value);
            // update the id when this hasn't been done before
            if(id == null){
                this.id = parcelId.substring(0,2);
            }
        } catch (NumberFormatException e) {
            Log.i(TAG, "Invalid parcel ID: " + parcelId);
            return;
        }

        if(index < 0){
            Log.i(TAG, "Negative parcel ID: " + parcelId);
            return;
        }


        // when the index is 0, it is an header so process it accordingly;
        if(index == 0){
            this.idFromSender = parcel[1];
            this.idDestination = parcel[2];
            this.id = parcelId.substring(0,2);
            this.checksum = parcel[3];
            return;
        }

        // check if we have all the parcels
        if(messageBox.size() == 1){
            // too empty, we need at least two of them
            return;
        }

        // are we ready to compute the checksum?
        if(checksum == null){
            // not yet
            return;
        }

        String result = "";

        String[] lines = this.getMessageParcels();

        for(int i = 1; i < lines.length; i++){
            String line = lines[i];
            int anchor = line.indexOf(":");
            String text = line.substring(anchor + 1);
            result += text;
        }



        // compute the checksum
        String currentChecksum = calculateChecksum(result);
        // needs to match
        if(currentChecksum.equals(this.checksum) == false){
            return;
        }
        // this message is concluded
        this.message = result;
        this.messageCompleted = true;
    }

    public boolean isMessageCompleted() {
        return messageCompleted;
    }

    public void setMessageCompleted(boolean messageCompleted) {
        this.messageCompleted = messageCompleted;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setIdFromSender(String idFromSender) {
        this.idFromSender = idFromSender;
    }

    public void setIdDestination(String idDestination) {
        this.idDestination = idDestination;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public long getTimeStamp() {
        return timeStamp;
    }

    /**
     * Returns the first parcel that is noted as missing
     * @return the id and index of the missing parcel
     */
    public String getFirstMissingParcel() {
        // easiest situation: we missed the header
        if(checksum == null){
            return id + "0";
        }

        // second easy case, there isn't a followup messsage
        if(messageBox.size() == 1){
            return id + "1";
        }

        // it is a long message, so let's try to see which ones are missing
        for(int i = 0; i < messageBox.size(); i++){
            String key = id + i;
            if(messageBox.containsKey(key) == false){
                return key;
            }
        }

        // not the case, so we ask for a future value
        return id + messageBox.size();
    }

}

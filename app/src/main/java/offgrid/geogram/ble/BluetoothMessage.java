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

import java.util.ArrayList;
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

    public BluetoothMessage(String idFromSender, String idDestination,
                            String messageToSend, boolean singleMessage) {
        this.id = generateRandomId();
        this.idFromSender = idFromSender;
        this.idDestination = idDestination;
        this.message = messageToSend;
        this.checksum = calculateChecksum(message);
        if(singleMessage){
            messageBox.put("000", message);
        }else{
            splitDataIntoParcels();
        }
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
        // this is a long message, break into multiple parcels
        int dataLength = message.length();
        int messageParcelsTotal = (int) Math.ceil((double) message.length() / TEXT_LENGTH_PER_PARCEL);

        // add the header (use zero-padded 2-digit format for proper sorting)
        String uidHeader = id + "00";
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
            // Zero-pad parcel numbers to 2 digits for proper lexicographic sorting in TreeMap
            // This ensures XX01 < XX02 < ... < XX09 < XX10 < ... < XX99 (not XX1 < XX10 < XX2)
            String uid = id + String.format("%02d", i + 1);
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

    /**
     * A human readable format of the message, including parcel codes
     * @return
     */
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

        // don't add new parcels after completing a message
        if(this.messageCompleted){
            return;
        }

        // is this a single message?
        if(ValidCommands.isValidCommand(messageParcel)){
            // just mark as completed
            this.message = messageParcel;
            messageBox.put("000", messageParcel);
            this.messageCompleted = true;
            return;
        }

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
            // too empty, we need at least two of them (header + at least one data parcel)
            return;
        }

        // are we ready to compute the checksum?
        if(checksum == null){
            // not yet, we need the header first
            return;
        }

        // Optimization: Check if there are any missing parcels before attempting reconstruction
        // This avoids reconstructing incomplete messages on every parcel arrival
        ArrayList<String> missing = getMissingParcels();
        if(!missing.isEmpty()){
            // Still waiting for parcels, don't attempt reconstruction
            return;
        }

        // All parcels received, now reconstruct the message
        StringBuilder result = new StringBuilder();
        String[] lines = this.getMessageParcels();

        for(int i = 1; i < lines.length; i++){
            String line = lines[i];
            int anchor = line.indexOf(":");
            if(anchor == -1){
                // Malformed parcel, skip
                continue;
            }
            String text = line.substring(anchor + 1);
            result.append(text);
        }

        // compute the checksum
        String messageText = result.toString();
        String currentChecksum = calculateChecksum(messageText);
        // needs to match
        if(currentChecksum.equals(this.checksum) == false){
            Log.i(TAG, "Checksum mismatch: expected " + this.checksum + ", got " + currentChecksum);
            return;
        }
        // this message is concluded
        this.message = messageText;
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
            return id + "00";  // Zero-padded format
        }

        // second easy case, there isn't a followup messsage
        if(messageBox.size() == 1){
            return id + "01";  // Zero-padded format
        }

        // Find the highest index we've seen
        int maxSeen = -1;
        for (String key : messageBox.keySet()) {
            if (key.length() < 4) continue;  // Now expecting 4 chars (2-letter ID + 2-digit number)
            try {
                int idx = Integer.parseInt(key.substring(2));
                if (idx > maxSeen) maxSeen = idx;
            } catch (NumberFormatException ignore) { }
        }

        // Check all indices from 0 to maxSeen for gaps
        for(int i = 0; i <= maxSeen; i++){
            String key = id + String.format("%02d", i);  // Zero-padded format
            if(messageBox.containsKey(key) == false){
                return key;
            }
        }

        // No gaps found, ask for the next parcel after maxSeen
        return id + String.format("%02d", maxSeen + 1);  // Zero-padded format
    }

    /** List all missing parcel IDs up to the highest index we've seen (past gaps only). */
    public ArrayList<String> getMissingParcels() {
        ArrayList<String> missing = new ArrayList<>();
        if (messageBox.isEmpty()) return missing;

        // Determine the 2-char message ID prefix
        String baseId = this.id;
        if (baseId == null) {
            String firstKey = messageBox.firstKey();
            if (firstKey == null || firstKey.length() < 4) return missing;  // Now expecting 4 chars
            baseId = firstKey.substring(0, 2);
        }

        // Find the highest index we've seen (e.g., ...-5 means 0..4 are "past" indices)
        int maxSeen = -1;
        for (String key : messageBox.keySet()) {
            if (key.length() < 4) continue;  // Now expecting 4 chars (2-letter ID + 2-digit number)
            try {
                int idx = Integer.parseInt(key.substring(2));
                if (idx > maxSeen) maxSeen = idx;
            } catch (NumberFormatException ignore) { /* skip malformed */ }
        }

        // Only header (idx==0) or nothing parsable → no past gaps
        if (maxSeen <= 0) return missing;

        // Collect all gaps from 0..(maxSeen-1)
        for (int i = 0; i < maxSeen; i++) {
            String k = baseId + String.format("%02d", i);  // Zero-padded format
            if (!messageBox.containsKey(k)) {
                missing.add(k);
            }
        }
        return missing;
    }


}

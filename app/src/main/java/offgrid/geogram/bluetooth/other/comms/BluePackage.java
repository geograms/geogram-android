/**
 * The {@code BluePackage} class facilitates the transmission of large data as smaller parcels
 * over a Bluetooth communication channel. It handles parcel splitting, parcel tracking, and provides
 * utility methods to manage and request specific parcels.
 *
 * <h3>Example Usage:</h3>
 * <pre>{@code
 * BlueRequestData requestData = new BlueRequestData("HelloWorldExampleData");
 *
 * // Send parcels
 * while (!requestData.isComplete()) {
 *     String parcel = requestData.getNextParcel();
 *     sendParcel(parcel);  // Hypothetical method to send a parcel
 * }
 *
 * // Retrieve a specific parcel if needed
 * String specificParcel = requestData.getSpecificParcel(2);
 * System.out.println("Parcel 2: " + specificParcel);
 *
 * // Check the status of the data transfer
 * if (!requestData.isTransferring()) {
 *     System.out.println("Data transfer complete.");
 * }
 *
 * // Receiving side example:
 * BluePackage receiver = new BlueRequestData("AA:003"); // Header parcel received
 * receiver.receiveParcel("AA001:DataPart1");
 * receiver.receiveParcel("AA003:DataPart3");
 * receiver.receiveParcel("AA002:DataPart2");
 * System.out.println("Complete data: " + receiver.getData());
 * }</pre>
 *
 * This class is essential for managing data transfers where the size of a single transmission is limited.
 */
package offgrid.geogram.bluetooth.other.comms;

import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import java.util.Arrays;

import offgrid.geogram.bluetooth.Bluecomm;
import offgrid.geogram.core.Central;

public class BluePackage {

    // Random two bytes generated as ID
    private final String id;

    // the device id for this machine
    private String deviceId;

    private final DataType command;

    // The length of text per parcel
    private static final int TEXT_LENGTH_PER_PARCEL = Bluecomm.maxSizeOfMessages;

    // Total number of parcels in the message
    private final int messageParcelsTotal;

    // Current parcel index
    private int messageParcelCurrent;

    // The complete data message
    private final String data;

    // The individual data parcels
    private String[] dataParcels;

    // Timestamp when data transmission started
    private long timestamp;
    private long transmissionTimeLastActive;

    private final String checksum;

    // Indicates whether data is still being transferred
    private boolean isTransferring;
    private boolean validHeader = true;

    public static BluePackage createSender(String data) {
        DataType command = DataType.X;
        return new BluePackage(command, data, true);
    }

    public static BluePackage createSender(DataType command, String data, String deviceId) {
        return new BluePackage(command, data, true);
    }

    /**
     * Creates a new BluePackage instance for receiving data.
     * @param header is the initial message received from the other device
     * @return a new BluePackage instance for receiving data
     */
    public static BluePackage createReceiver(String header) {
        return new BluePackage(null, header, false);
    }

    private BluePackage(DataType command, String data, boolean isSender) {
        // we are sending this package to another device
        if (isSender) {
            if (data == null) {
                throw new IllegalArgumentException("Data cannot be null");
            }
            this.id = generateRandomId();
            this.deviceId = Central.getInstance().getSettings().getIdDevice();
            this.command = command;
            this.data = data;
            this.messageParcelCurrent = -1;
            this.timestamp = System.currentTimeMillis();
            this.ping();
            this.isTransferring = true;
            this.messageParcelsTotal = (int) Math.ceil((double) this.data.length() / TEXT_LENGTH_PER_PARCEL);
            this.checksum = calculateChecksum(data);
            splitDataIntoParcels();
        } else {
            // we are receiving this package from someone outside
            String[] parts = data.split(":");
            // expected format:
            // uid:parceltotal:datachecksum:commandId:deviceId
            // Example:
            // AA:003:A4GD:B:34343
            if (parts.length != 5) {
                // this header isn't valid, invalidate the whole package
                validHeader = false;
                id = null;
                messageParcelsTotal = 0;
                dataParcels = null;
                this.data = null;
                this.messageParcelCurrent = -1;
                this.command = DataType.NONE;
                this.timestamp = -1;
                this.checksum = null;
                return;
            }
            this.id = parts[0];
            this.messageParcelsTotal = Integer.parseInt(parts[1]);
            this.checksum = parts[2]; // don't calculate the checksum initially
            this.dataParcels = new String[messageParcelsTotal];
            this.data = null;
            this.messageParcelCurrent = -1;
            // get the command type
            this.command = DataType.valueOf(parts[3]);
            // get the device id
            this.deviceId = parts[4];
            // setup the transmission time
            this.timestamp = System.currentTimeMillis();
            this.ping();
            this.isTransferring = true;
        }
    }



    /**
     * Splits the data into smaller parcels based on TEXT_LENGTH_PER_PARCEL.
     * Each parcel will contain at most {@code TEXT_LENGTH_PER_PARCEL} characters.
     */
    private void splitDataIntoParcels() {
        int dataLength = data.length();
        dataParcels = new String[messageParcelsTotal];

        for (int i = 0; i < messageParcelsTotal; i++) {
            int start = i * TEXT_LENGTH_PER_PARCEL;
            int end = Math.min(start + TEXT_LENGTH_PER_PARCEL, dataLength);
            dataParcels[i] = data.substring(start, end);
        }
    }

    /**
     * Receives a parcel and stores it in the appropriate slot based on its ID.
     *
     * @param parcel The parcel received (format: "AA###:parcelText").
     * @throws IllegalArgumentException If the parcel format is invalid or the ID does not match.
     */
    public void receiveParcel(String parcel) {
        this.ping();
        String[] parts = parcel.split(":", 2);
        if (parts.length != 2 || !parts[0].startsWith(id)) {
            throw new IllegalArgumentException("Invalid parcel format or ID mismatch");
        }

        int parcelIndex = Integer.parseInt(parts[0].substring(id.length()));
        if (parcelIndex >= 0 && parcelIndex < messageParcelsTotal) {
            dataParcels[parcelIndex] = parts[1];
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

    public boolean isValidHeader() {
        return validHeader;
    }

    /**
     * Retrieves the next data parcel to send. Automatically increments the current parcel index.
     *
     * @return The next parcel as a string, or {@code null} if all parcels have been sent.
     */
    public String getNextParcel() {
        this.ping();
        // first message is the header
        if (messageParcelCurrent == -1) {
            messageParcelCurrent++;
            // First parcel is the header with ID and total parcel count
            return String.format(Locale.US, "%s:%03d:%s:%s:%s",
                    id, messageParcelsTotal, checksum, command, deviceId);
            // next parcels are normal
        } else if (messageParcelCurrent < messageParcelsTotal) {
            // Subsequent parcels contain just the id, parcel number and data
            String parcelId = String.format(Locale.US, "%s%03d", id, messageParcelCurrent);
            String parcelData = dataParcels[messageParcelCurrent];
            messageParcelCurrent++;
            return parcelId + ":" + parcelData  ;
        }
        return null;
    }

    /**
     * Permits to send again this package
     */
    public void resetParcelCounter(){
        this.ping();
        this.messageParcelCurrent = -1;
    }


    /**
     * Requests a specific parcel by its index (1-based).
     * This is useful for retransmitting lost parcels during communication.
     *
     * @param index The index of the parcel to retrieve (1-based).
     * @return The requested parcel as a string, or {@code null} if the index is invalid.
     */
    public String getSpecificParcel(int index) {
        if (index > 0 && index <= messageParcelsTotal) {
            return dataParcels[index - 1];
        }
        return null;
    }


    /**
     * Checks whether all parcels have been received.
     *
     * @return {@code true} if all parcels are received, {@code false} otherwise.
     */
    public boolean allParcelsReceivedAndValid() {
        if (dataParcels == null) {
            return false;
        }

        // it is false when there are still null sections on the array
        if(!Arrays.stream(dataParcels).allMatch(Objects::nonNull)){
            return false;
        }

        // fields should be complete now, but do they match the checksum?
        String data = String.join("", dataParcels);;
        String checksumReceived = calculateChecksum(data);
        return checksumReceived.equals(this.checksum);
    }

    /**
     * Checks if data is still being transferred.
     *
     * @return {@code true} if data is being transferred, {@code false} otherwise.
     */
    public boolean isTransferring() {
        return isTransferring;
    }

    /**
     * Retrieves the unique ID for this data transmission.
     *
     * @return The unique ID as a string.
     */
    public String getId() {
        return id;
    }

    /**
     * Retrieves the length of each parcel.
     *
     * @return The parcel length as an integer.
     */
    public int getTextLengthPerParcel() {
        return TEXT_LENGTH_PER_PARCEL;
    }

    /**
     * Retrieves the total number of parcels for the complete message.
     *
     * @return The total number of parcels as an integer.
     */
    public int getMessageParcelsTotal() {
        return messageParcelsTotal;
    }

    /**
     * Retrieves the current parcel index that is being processed.
     *
     * @return The current parcel index as an integer.
     */
    public int getMessageParcelCurrent() {
        return messageParcelCurrent;
    }

    /**
     * Retrieves the complete data message by combining all received parcels.
     *
     * @return The full data message as a string, or null if parcels are missing.
     */
    public String getData() {
        if (!allParcelsReceivedAndValid()) {
            return null;
        }
        return String.join("", dataParcels);
    }

    /**
     * Retrieves all data parcels as an array.
     *
     * @return An array of strings representing the data parcels.
     */
    public String[] getDataParcels() {
        return dataParcels;
    }

    /**
     * Retrieves the timestamp when data transmission started.
     *
     * @return The timestamp as a long value.
     */
    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
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

    public DataType getCommand() {
        return command;
    }


    /**
     * Gets the index of the latest parcel that is not null and closest to the end of the array.
     *
     * @return The index of the latest parcel (1-based), or -1 if no parcels are received.
     */
    private int getLatestParcel() {
        if (dataParcels == null) {
            return -1;
        }

        for (int i = dataParcels.length - 1; i >= 0; i--) {
            if (dataParcels[i] != null) {
                return i + 1; // Convert to 1-based index
            }
        }
        return -1; // No parcel found
    }


    /**
     * Looks at all the received parcels and checks if there are any gaps
     * @return true when there is at least one gap existing
     */
    public boolean hasGaps() {
        int latestParcel = getLatestParcel();
        for (int i = 0; i < latestParcel; i++) {
            if (dataParcels[i] == null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds the first gap (missing parcel) in the received data parcels.
     *
     * @return The ID of the first missing parcel in the format "AA###", or {@code null} if there are no gaps.
     */
    public String getFirstGapParcel() {
        if (dataParcels == null) {
            return null;
        }

        for (int i = 0; i < dataParcels.length; i++) {
            if (dataParcels[i] == null) {
                // Generate the parcel ID for the first gap
                return String.format(Locale.US, "%s%03d", id, i ); // Convert to 1-based index
            }
        }
        return null; // No gaps found
    }

    /**
     * Retrieves a parcel by its index, given as a string in the format "000", "001", etc.
     *
     * @param indexString The index of the parcel as a zero-padded string.
     * @return The parcel in the format "AA###:parcelText", or {@code null} if the index is invalid or the parcel is missing.
     */
    public String getParcelByIndex(String indexString) {
        try {
            int index = Integer.parseInt(indexString);
            if (index < 0 || index >= messageParcelsTotal || dataParcels[index] == null) {
                return null; // Index out of bounds or parcel missing
            }
            return String.format(Locale.US, "%s%03d:%s", id, index, dataParcels[index]);
        } catch (NumberFormatException e) {
            return null; // Invalid index format
        }
    }


    private void ping(){
        transmissionTimeLastActive = System.currentTimeMillis();
    }

    public long timeSinceLastPing(){
        return System.currentTimeMillis() - transmissionTimeLastActive;
    }
    /**
     * Checks if the data transmission is still active.
     * @return
     */
    public boolean isStillActive() {
        long timeInterval = System.currentTimeMillis() - transmissionTimeLastActive;
        return timeInterval < Bluecomm.packageTimeToBeActive;
    }

    public String getDeviceId() {
        return deviceId;
    }
}

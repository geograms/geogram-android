package offgrid.geogram.devices;

import android.content.Context;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import offgrid.geogram.core.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

/**
 * Defines information about a beacon that was
 * found by this device.
 */
public class DeviceReachable {
    private String macAddress;
    private String namespaceId = null;
    private String instanceId = null;
    private int rssi;
    private byte[] serviceData;
    private final long timeFirstFound;
    private long timeLastFound;
    private String profileName = null;
    private String npub = null;

    /**
     * Constructor initializes the time the beacon was first found.
     */
    public DeviceReachable() {
        this.timeFirstFound = System.currentTimeMillis();
        this.timeLastFound = this.timeFirstFound;
    }

    // Getters and setters

    /**
     * Gets the instance ID of the beacon.
     * The instance ID is part of the Eddystone protocol and
     * identifies the beacon uniquely.
     *
     * @return The instance ID as a String.
     */
    public String getDeviceId() {
        return instanceId;
    }

    /**
     * Sets the instance ID of the beacon.
     *
     * @param instanceId The instance ID to set, as defined by
     *                   the Eddystone protocol.
     */
    public void setDeviceId(String instanceId) {
        this.instanceId = instanceId;
    }

    /**
     * Gets the namespace ID of the beacon.
     * The namespace ID groups beacons under a common identifier.
     *
     * @return The namespace ID as a String.
     */
    public String getNamespaceId() {
        return namespaceId;
    }

    /**
     * Sets the namespace ID of the beacon.
     *
     * @param namespaceId The namespace ID to set, grouping this
     *                    beacon with others.
     */
    public void setNamespaceId(String namespaceId) {
        this.namespaceId = namespaceId;
    }

    /**
     * Gets the MAC address of the beacon.
     * This is the hardware address used for identification in
     * wireless communication. MAC (Media Access Control)
     * addresses are unique identifiers assigned to network
     * interfaces for communications within a network segment.
     *
     * @return The MAC address as a String.
     */
    public String getMacAddress() {
        return macAddress;
    }

    /**
     * Sets the MAC address of the beacon.
     *
     * @param macAddress The hardware address of the beacon.
     */
    public void setMacAddress(String macAddress) {
        this.macAddress = macAddress;
    }

    /**
     * Gets the RSSI (Received Signal Strength Indicator) of
     * the beacon. RSSI represents the signal strength and can
     * be used to estimate distance.
     *
     * @return The RSSI value as an integer.
     */
    public int getRssi() {
        return rssi;
    }

    /**
     * Sets the RSSI (Received Signal Strength Indicator) of
     * the beacon.
     *
     * @param rssi The signal strength value to set.
     */
    public void setRssi(int rssi) {
        this.rssi = rssi;
    }

    /**
     * Gets the service data broadcast by the beacon.
     * This data may include custom or protocol-specific
     * information.
     *
     * @return The service data as a byte array.
     */
    public byte[] getServiceData() {
        return serviceData;
    }

    /**
     * Sets the service data broadcast by the beacon.
     *
     * @param serviceData A byte array containing the service data.
     */
    public void setServiceData(byte[] serviceData) {
        this.serviceData = serviceData;
    }

    /**
     * Gets the timestamp of when the beacon was first
     * discovered.
     *
     * @return The timestamp as a long value in milliseconds.
     */
    public long getTimeFirstFound() {
        return timeFirstFound;
    }

    /**
     * Gets the timestamp of when the beacon was last
     * discovered.
     *
     * @return The timestamp as a long value in milliseconds.
     */
    public long getTimeLastFound() {
        return timeLastFound;
    }

    /**
     * Sets the timestamp of when the beacon was last
     * discovered.
     *
     * @param timeLastFound The timestamp as a long value in
     *                      milliseconds.
     */
    public void setTimeLastFound(long timeLastFound) {
        this.timeLastFound = timeLastFound;
    }

    /**
     * Converts a JSON file into a BeaconReachable object using
     * Gson.
     *
     * @param file The JSON file containing BeaconReachable data.
     * @return A BeaconReachable object populated with data from
     *         the JSON file, or null if an error occurs.
     */
    public static DeviceReachable fromJson(File file) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileReader reader = new FileReader(file)) {
            return gson.fromJson(reader, DeviceReachable.class);
        } catch (JsonSyntaxException e) {
            Log.e("BeaconReachable", "Error parsing JSON: " +
                    e.getMessage());
        } catch (IOException e) {
            Log.e("BeaconReachable", "Error reading file: " +
                    e.getMessage());
        }
        return null;
    }

    /**
     * Saves the current BeaconReachable object to a JSON file.
     *
     * @param file The file to save the JSON representation.
     * @param appContext The application context, if needed.
     */
    public void saveToFile(File file, Context appContext) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(this, writer);
        } catch (IOException e) {
            Log.e("BeaconReachable", "Error saving to file: " +
                    e.getMessage());
        }
    }

    /**
     * Merges data from another BeaconReachable object, preserving
     * existing values unless they are null.
     *
     * @param beaconFromFile The existing BeaconReachable object to merge.
     */
    public void merge(DeviceReachable beaconFromFile) {
        if (beaconFromFile == null) {
            return;
        }

        if (this.macAddress == null) {
            this.macAddress = beaconFromFile.getMacAddress();
        }
        if (this.namespaceId == null) {
            this.namespaceId = beaconFromFile.getNamespaceId();
        }
        if (this.instanceId == null) {
            this.instanceId = beaconFromFile.getDeviceId();
        }
        if (this.rssi == 0) {
            this.rssi = beaconFromFile.getRssi();
        }
        if (this.serviceData == null) {
            this.serviceData = beaconFromFile.getServiceData();
        }
//        if (this.timeLastFound < beaconFromFile.getTimeLastFound()) {
//            this.timeLastFound = beaconFromFile.getTimeLastFound();
//        }
    }

    public String getProfileName() {
        return profileName;
    }

    public void setProfileName(String profileName) {
        this.profileName = profileName;
    }
    public String getNpub() {
        return npub;
    }
    public void setNpub(String npub) {
        this.npub = npub;
    }
}

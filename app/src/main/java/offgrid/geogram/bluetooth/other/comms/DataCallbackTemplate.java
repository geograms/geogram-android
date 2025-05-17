package offgrid.geogram.bluetooth.other.comms;

/**
 * Abstract class for data read/write operations.
 */
public abstract class DataCallbackTemplate {

    protected String macAddress = null;
    protected String deviceId = null;

    /**
     * Abstract method to handle successful data operations.
     *
     * @param data The data received or processed.
     */
    public abstract void onDataSuccess(String data);

    /**
     * Abstract method to handle data errors.
     *
     * @param error The error message.
     */
    public abstract void onDataError(String error);

    /**
     * Retrieves the MAC address associated with this callback.
     *
     * @return The MAC address as a string.
     */
    public String getMacAddress() {
        return macAddress;// Use protected if subclasses need access
    }

    /**
     * Retrieves the device ID associated with this callback.
     *
     * @return The device ID as a string.
     */
    public String getDeviceId() {
        return deviceId;
    }

    /**
     * Sets the MAC address.
     *
     * @param macAddress The MAC address to set.
     */
    public void setMacAddress(String macAddress) {
        this.macAddress = macAddress;
    }

    /**
     * Sets the device ID.
     *
     * @param deviceId The device ID to set.
     */
    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }
}

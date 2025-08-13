package offgrid.geogram.old.bluetooth_old.broadcast;

import java.util.ArrayList;

import offgrid.geogram.old.bluetooth_old.other.comms.BluePackage;
import offgrid.geogram.devices.old.DeviceReachableOld;

/**
 * Stores a message that was broadcast to all devices within reach
 */
public class BroadcastMessage {
    private final String message;
    private final long timestamp;
    private final boolean writtenByMe;
    private String deviceId = null;
    private BluePackage packageSent;
    private final ArrayList<DeviceReachableOld> devicesThatReadMessage = new ArrayList<>();



    public BroadcastMessage(String message, String deviceId, boolean writtenByMe) {
        this.message = message;
        this.deviceId = deviceId;
        this.timestamp = System.currentTimeMillis();
        this.writtenByMe = writtenByMe;
    }

    private void addDeviceThatReadMessage(DeviceReachableOld device) {
        devicesThatReadMessage.add(device);
    }

    public ArrayList<DeviceReachableOld> getDevicesThatReadMessage() {
        return devicesThatReadMessage;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }
    public String getMessage() {
        return message;
    }
    public long getTimestamp() {
        return timestamp;
    }
    public boolean isWrittenByMe() {
        return writtenByMe;
    }
    public void setPackage(BluePackage packageSent) {
        this.packageSent = packageSent;
    }
    public BluePackage getPackage() {
        return packageSent;
    }
}

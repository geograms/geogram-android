package offgrid.geogram.bluetooth.watchdog;

import android.content.Context;

import offgrid.geogram.bluetooth.Bluecomm;
import offgrid.geogram.bluetooth.eddystone.DeviceFinder;
import offgrid.geogram.bluetooth.other.comms.BlueCommands;
import offgrid.geogram.bluetooth.other.comms.BluePackage;
import offgrid.geogram.core.Central;
import offgrid.geogram.core.Log;
import offgrid.geogram.devices.DeviceReachable;
import offgrid.geogram.events.EventAction;

public class EventBluetoothPackageReceived extends EventAction {

    private static final String TAG = "EventBluetoothPackageReceived";
    private final Context context;

    public EventBluetoothPackageReceived(String id, Context context) {
        super(id);
        this.context = context;
    }

    @Override
    public void action(Object... data) {
    // object data[0] is the package of type BluePackage
    BluePackage packageReceived = (BluePackage) data[0];
    Log.i(TAG, "Package received: " + packageReceived.getId());

    // send back an acknowledgement that the package was received
    String messageText = BlueCommands.oneLineAcknowledgement
            + Central.getInstance().getSettings().getIdDevice()
            + ":"
            + packageReceived.getId();

    // MAC address might have changes since this is dynamic
    String senderId = packageReceived.getDeviceId();
    DeviceReachable device = DeviceFinder.getInstance(context).getDeviceMap().get(senderId);
    // is the device still within our reach?
    if(device == null){
        Log.e(TAG, "Device not found: " + senderId);
        return;
    }
    // send to the device with the last-known MAC address
    String macAddress = device.getMacAddress();
    Bluecomm.getInstance(context).writeData(macAddress, messageText);
    Log.i(TAG, "Sent acknowledgement of received message: " + messageText);
    }
}

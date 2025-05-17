package offgrid.geogram.bluetooth.broadcast;

import static offgrid.geogram.bluetooth.other.comms.BlueCommands.oneLineCommandGapBroadcast;

import android.content.Context;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import offgrid.geogram.bluetooth.BlueQueueSending;
import offgrid.geogram.bluetooth.eddystone.DeviceFinder;
import offgrid.geogram.bluetooth.BluetoothCentral;
import offgrid.geogram.bluetooth.other.comms.BlueCommands;
import offgrid.geogram.bluetooth.other.comms.BluePackage;
import offgrid.geogram.bluetooth.Bluecomm;
import offgrid.geogram.bluetooth.other.comms.DataType;
import offgrid.geogram.core.Central;
import offgrid.geogram.core.Log;
import offgrid.geogram.database.BioProfile;
import offgrid.geogram.settings.SettingsUser;
import offgrid.geogram.devices.DeviceReachable;

public class BroadcastSender {
    private static final String TAG_ID = "BroadcastSender";

    // Listener for message updates
    private static WeakReference<MessageUpdateListener> messageUpdateListener;

    public static void addMessage(BroadcastMessage message) {
        notifyMessageUpdate();
    }

    public static boolean broadcast(BroadcastMessage messageToBroadcast, Context context) {
        BluetoothCentral bluetoothCentral = BluetoothCentral.getInstance(context);
        if (!bluetoothCentral.isBluetoothAvailable()) {
            Log.i(TAG_ID, "Bluetooth is not ready. Please enable it to continue.");
            return false;
        }

        broadcastMessageToAllEddystoneDevices(messageToBroadcast, context);
        Log.i(TAG_ID, "Message sent to all Eddystone devices: " + messageToBroadcast.getMessage());
        return true;
    }

    public static void broadcastMessageToAllEddystoneDevicesShort(String text, Context context) {
        new Thread(() -> {
            try {
                Collection<DeviceReachable> devices = DeviceFinder.getInstance(context).getDeviceMap().values();
                for (DeviceReachable device : devices) {
                    String macAddress = device.getMacAddress();
                    Log.i(TAG_ID, "Sending short message to " + macAddress + " with: " + text);
                    Bluecomm.getInstance(context).writeData(macAddress, text);
                }
            } catch (Exception e) {
                Log.e(TAG_ID, "Exception happened: " + e.getMessage());
            }
        }).start();
    }

    public static void broadcastMessageToAllEddystoneDevices(BroadcastMessage messageToBroadcast, Context context) {
        new Thread(() -> {
            try {
                Collection<DeviceReachable> devices = DeviceFinder.getInstance(context).getDeviceMap().values();
                if (devices.isEmpty()) {
                    Log.i(TAG_ID, "No Eddystone devices to broadcast the message.");
                    return;
                }

                String deviceId = Central.getInstance().getSettings().getIdDevice();
                BluePackage packageToSend = BluePackage.createSender(
                        DataType.B, messageToBroadcast.getMessage(), deviceId
                );
                messageToBroadcast.setPackage(packageToSend);

                for (DeviceReachable device : devices) {
                    sendPackageToDevice(device.getMacAddress(), packageToSend, context);
                }
            } catch (Exception e) {
                Log.e(TAG_ID, "Broadcasting failed: " + e.getMessage());
            }
        }).start();
    }

    public static void sendParcelToDevice(String macAddress, String gapData, Context context) {
        String text = oneLineCommandGapBroadcast + gapData;
        Log.i(TAG_ID, "GapData: Sending gap data request to " + macAddress + " with: " + text);
        Bluecomm.getInstance(context).writeData(macAddress, text);
    }

    public static void sendPackageToDevice(String macAddressProvided, BluePackage packageToSend, Context context) {
        BlueQueueSending.getInstance(context).addPackageToSend(packageToSend);
        packageToSend.resetParcelCounter();

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        for (int i = 0; i <= packageToSend.getMessageParcelsTotal(); i++) {
            final int index = i;
            scheduler.schedule(() -> {
                String text = packageToSend.getNextParcel();
                String deviceId = packageToSend.getDeviceId();
                Log.i(TAG_ID, "Sending message to " + deviceId + " with data: " + text);

                // get the specified MAC address as default or try to get a new one
                String macAddress = macAddressProvided;
                DeviceReachable device = DeviceFinder.getInstance(context).getDevice(deviceId);
                // is this device not reachable?
                if(device == null){
                    // don't send this message
                    return;
                }
                // update with the most recent address
                macAddress = device.getMacAddress();

                // send the text to the available device
                Bluecomm.getInstance(context).writeData(macAddress, text);

                if (index == packageToSend.getMessageParcelsTotal()) {
                    Log.i(TAG_ID, "Message sent to Eddystone device: " + deviceId);
                    scheduler.shutdown();
                }
            }, i * 500L, TimeUnit.MILLISECONDS);
        }
    }

    public static void setMessageUpdateListener(MessageUpdateListener listener) {
        messageUpdateListener = new WeakReference<>(listener);
    }

    public static void removeMessageUpdateListener() {
        if (messageUpdateListener != null) {
            messageUpdateListener.clear();
            messageUpdateListener = null;
        }
    }

    public static void notifyMessageUpdate() {
        if (messageUpdateListener != null) {
            MessageUpdateListener listener = messageUpdateListener.get();
            if (listener != null) {
                listener.onMessageUpdate();
            }
        }
    }

    public interface MessageUpdateListener {
        void onMessageUpdate();
    }

    public static void sendProfileToEveryone(Context context) {
        SettingsUser settings = Central.getInstance().getSettings();
        BioProfile profile = new BioProfile();
        profile.setNick(settings.getNickname());
        profile.setDeviceId(settings.getIdDevice());
        profile.setColor(settings.getPreferredColor());

        String message = BlueCommands.tagBio + profile.toJson();
        BroadcastMessage messageToBroadcast = new BroadcastMessage(message, settings.getIdDevice(), true);
        BroadcastSender.broadcastMessageToAllEddystoneDevices(messageToBroadcast, context);
    }
}

package offgrid.geogram.ble;

import android.content.Context;

import offgrid.geogram.ble.events.EventBleBroadcastMessageReceived;
import offgrid.geogram.ble.events.EventBleBroadcastMessageSent;
import offgrid.geogram.ble.events.EventBleMessageReceived;
import offgrid.geogram.core.Log;
import offgrid.geogram.events.EventControl;
import offgrid.geogram.events.EventType;
// Removed (legacy Google Play Services code) - import offgrid.geogram.old.EddystoneBeacon_unused;

public class BluetoothCentral {
    /*
    * Software developers tend to understand bluetooth as a digital thing.
    * Because it is used from a digital environment, like so many other things.
    *
    * But this is a radio.
    *
    * This means for example that our mentality when writing code should
    * be from a radio perspective and not from a computer perspective.
    * What does this change? Well, for example you need to remember that
    * a radio can either receive or send transmissions but cannot do both
    * at the same time. Also, remember that listening is preferable on a
    * radio than just transmitting without stopping.
    *
    * When you keep these advices in mind, then programming features on top
    * of bluetooth becomes far easier and actually works as expected.
    *
    * */

    // time duration to broadcast a message in loop
    public static final int advertiseDurationMillis = 1000;
    public static int
            selfIntervalSeconds = 60,
            maxSizeOfMessages = 40; // Increased to accommodate device model in ping messages

    // Time-sliced interleaving: send 3 parcels, then listen for 5 seconds
    public static final int parcelsBeforeListening = 3;
    public static final int listeningWindowMillis = 5000;
    public static final int selfAdvertiseThrottleThreshold = 5; // Skip self-advertise if queue >= 5 parcels

    private static final String TAG = "BluetoothCentral";

    private final Context context;
    private static BluetoothCentral instance;
    // Removed - EddystoneBeacon_unused was part of old code
    // public EddystoneBeacon_unused eddystoneBeacon;

    public static String EDDYSTONE_SERVICE_ID = "0000FEAA-0000-1000-8000-00805F9B34FB";

    // GATT Service UUIDs for bidirectional messaging
    public static final String GATT_SERVICE_UUID = "0000FEA0-0000-1000-8000-00805F9B34FB";
    public static final String GATT_CHARACTERISTIC_TX_UUID = "0000FEA1-0000-1000-8000-00805F9B34FB";  // Write, Notify - Send parcels
    public static final String GATT_CHARACTERISTIC_RX_UUID = "0000FEA2-0000-1000-8000-00805F9B34FB";  // Write, Notify - Receive parcels
    public static final String GATT_CHARACTERISTIC_CONTROL_UUID = "0000FEA3-0000-1000-8000-00805F9B34FB";  // Write, Read, Notify - ACK/NACK

    // GATT Connection settings
    public static final int MAX_GATT_CONNECTIONS = 7;  // Maximum simultaneous connections
    public static final int GATT_ACK_TIMEOUT_MS = 2000;  // Wait 2 seconds for ACK before retry
    public static final int GATT_MTU_SIZE = 512;  // Request 512-byte MTU for large parcels

    private BluetoothCentral(Context context) {
        this.context = context.getApplicationContext();
        initialize();
    }

    private void initialize() {
        setupEvents();
        //eddystoneBeacon = EddystoneBeacon.getInstance(context);
        //eddystoneBeacon.startBeaconing();

        BluetoothSender sender = BluetoothSender.getInstance(context);
        //sender.setSelfMessage("NODE1>ALL:>Beacon Active");
        sender.setSelfIntervalSeconds(10); // every 10 seconds
        //sender.setSelfMessage("Testing out");
        sender.start();

        BluetoothListener.getInstance(context).startListening();
        Log.i(TAG, "BluetoothCentral initialized with GATT server and beacon");
    }

    private void setupEvents() {
        // handle the case a new package being received as complete
        EventControl.addEvent(EventType.BLUETOOTH_MESSAGE_RECEIVED,
                new EventBleMessageReceived(TAG + "-packageReceived")
        );

        EventControl.addEvent(EventType.BLE_BROADCAST_RECEIVED,
                new EventBleBroadcastMessageReceived(TAG + "-BleBroadcastMessageReceived")
        );

        EventControl.addEvent(EventType.BLE_BROADCAST_SENT,
                new EventBleBroadcastMessageSent(TAG + "-BleBroadcastMessageSent")
        );

//        EventControl.addEvent(EventType.BLUETOOTH_ACKNOWLEDGE_RECEIVED,
//                new EventBluetoothAcknowledgementReceived(TAG + "+ ackReceived", context)
//        );
    }

    /**
     * Singleton access to the BluetoothCentral instance.
     */
    public static synchronized BluetoothCentral getInstance(Context context) {
        if (instance == null) {
            instance = new BluetoothCentral(context);
        }
        return instance;
    }

}

package offgrid.geogram.old.bluetooth_old.other.old;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import offgrid.geogram.old.bluetooth_old.BluetoothCentral;
import offgrid.geogram.core.Log;

public class BluetoothStateReceiver extends BroadcastReceiver {

    private static BluetoothStateReceiver instance;

    private BluetoothStateReceiver() {
        // Private constructor to enforce singleton pattern
    }

    /**
     * Singleton access to the BluetoothStateReceiver instance.
     */
    public static synchronized BluetoothStateReceiver getInstance() {
        if (instance == null) {
            instance = new BluetoothStateReceiver();
        }
        return instance;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
            BluetoothCentral central = BluetoothCentral.getInstance(context);

            switch (state) {
                case BluetoothAdapter.STATE_ON:
                    Log.i("BluetoothStateReceiver", "Bluetooth enabled.");
                    central.handleBluetoothStateChange(true);
                    break;
                case BluetoothAdapter.STATE_OFF:
                    Log.i("BluetoothStateReceiver", "Bluetooth disabled.");
                    central.handleBluetoothStateChange(false);
                    break;
                default:
                    Log.i("BluetoothStateReceiver", "Bluetooth state changed: " + state);
                    break;
            }
        }
    }
}

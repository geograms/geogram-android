package offgrid.geogram.devices;

import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.util.ArrayList;

import offgrid.geogram.MainActivity;
import offgrid.geogram.R;
import offgrid.geogram.core.Log;
import offgrid.geogram.events.EventAction;

/*

    This class is mainly responsible for updating the main screen
    and show which devices/Callsigns have been recently updated.

 */

public class EventDeviceUpdated extends EventAction {

    private static final String TAG = "EventDeviceUpdated";

    public EventDeviceUpdated(String id) {
        super(id);
    }

    @Override
    public void action(Object... data) {
        Device device = (Device) data[0];
        Log.d("DEVICE_UPDATED", "Device updated: " + device.ID);
        // start updating the UI when possible
        if (MainActivity.getInstance() == null) {
            Log.e("DEVICE_UPDATED", "MainActivity is null");
            return;
        }
        // get the beacon list
        ListView beaconWindow = MainActivity.getInstance().beacons;
        if (beaconWindow == null) {
            return;
        }

        // create a new list of things to show on the main screen
        ArrayList<Device> displayList = new ArrayList<>(DeviceManager.getInstance().getDevicesSpotted());


        // display on UI
        ArrayAdapter<Device> adapter = new ArrayAdapter<>(
                beaconWindow.getContext(),
                android.R.layout.simple_list_item_1,
                displayList
        );
        beaconWindow.setAdapter(adapter);

        // define what happens when clicking on the item
        beaconWindow.setOnItemClickListener((parent, view, position, id) -> {
            // get the object
            Device deviceClicked = displayList.get(position);
            // make the screen appear
            if(MainActivity.getInstance() == null){
                Log.e(TAG, "MainActivity is null");
                return;
            }

            MainActivity.getInstance().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.main, DeviceDetailsFragment.newInstance(deviceClicked))
                    .addToBackStack(null)
                    .commit();
        });

    }
}

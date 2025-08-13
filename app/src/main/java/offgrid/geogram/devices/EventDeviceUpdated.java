package offgrid.geogram.devices;

import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.util.ArrayList;

import offgrid.geogram.MainActivity;
import offgrid.geogram.core.Log;
import offgrid.geogram.database.old.BioProfile;
import offgrid.geogram.events.EventAction;

public class EventDeviceUpdated extends EventAction {
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

        // create a new list of things to show
        ArrayList<BioProfile> displayList = new ArrayList<>();

        // convert the profiles
        for(Device deviceSpotted: DeviceManager.getInstance().getDevicesSpotted()){
            BioProfile bio = new BioProfile();
            bio.setDeviceId(deviceSpotted.ID);
            bio.setNick(deviceSpotted.ID);
            // add it up
            displayList.add(bio);
        }


        // display on UI
        ArrayAdapter<BioProfile> adapter = new ArrayAdapter<>(
                beaconWindow.getContext(),
                android.R.layout.simple_list_item_1,
                displayList
        );
        beaconWindow.setAdapter(adapter);

    }
}

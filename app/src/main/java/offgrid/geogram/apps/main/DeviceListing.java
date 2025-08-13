package offgrid.geogram.apps.main;

import static offgrid.geogram.util.BluetoothUtils.calculateDistance;
import static offgrid.geogram.util.DateUtils.getHumanReadableTime;

import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;

import offgrid.geogram.MainActivity;
import offgrid.geogram.R;
import offgrid.geogram.old.bluetooth_old.broadcast.LostAndFound;
import offgrid.geogram.database.old.BeaconDatabase;
import offgrid.geogram.database.old.BioDatabase;
import offgrid.geogram.database.old.BioProfile;
import offgrid.geogram.devices.old.DeviceReachableOld;
import offgrid.geogram.core.Log;
import offgrid.geogram.devices.old.DeviceDetailsFragment;
import offgrid.geogram.old.bluetooth_old.eddystone.DeviceFinder;

/**
 * Manages the list of beacons that were found
 * and lists them on the UI of the android app
 */
public class DeviceListing {

    private static final String TAG = "DeviceListing";

    private static DeviceListing instance; // Singleton instance

    // list of beacons both reachable and past ones
    public final HashMap<String, DeviceReachableOld> beacons = new HashMap<>();

    // Private constructor for Singleton pattern
    private DeviceListing() {
        // Prevent instantiation
    }

    /**
     * Singleton access to the BeaconListing instance.
     */
    public static synchronized DeviceListing getInstance() {
        if (instance == null) {
            instance = new DeviceListing();
        }
        return instance;
    }

    /**
     * Updates the list of beacons on the UI.
     */
    public void updateList(Context context) {
        if(MainActivity.getInstance() == null){
            Log.e(TAG, "MainActivity is null");
            return;
        }

        ListView beaconWindow = MainActivity.getInstance().beacons;

        if (beaconWindow == null) {
            return;
        }

        // create a new list
        // add all beacons from the database
        HashMap<String, DeviceReachableOld> beaconsToList = new HashMap<>(BeaconDatabase.beacons);

        // now update with all the beacons within radio reach
        for(DeviceReachableOld beacon : DeviceFinder.getInstance(context).getDeviceMap().values()){
            // overwrite any existing items
            beaconsToList.put(beacon.getDeviceId(), beacon);
        }

        // transform into a simple arrayList
        ArrayList<DeviceReachableOld> deviceList = new ArrayList<>(beaconsToList.values());
        // Sort beacons by last seen time, most recent first
        deviceList.sort(Comparator.comparingLong(DeviceReachableOld::getTimeLastFound).reversed());

        ArrayList<BioProfile> displayList = new ArrayList<>();
        for (DeviceReachableOld deviceFound : deviceList) {
            // data displayed on main screen
            String distance = calculateDistance(deviceFound.getRssi());
            long lastSeen = System.currentTimeMillis() - deviceFound.getTimeLastFound();

            if (lastSeen > 3 * 60_000) {
                distance = "not reachable since " + getHumanReadableTime(deviceFound.getTimeLastFound());
            }


            // get the device id
            String deviceId = deviceFound.getDeviceId();
            if(deviceId.endsWith("000000")){
                deviceId = deviceId.substring(0, 6);
            }
            BioProfile bioData = BioDatabase.get(deviceId, context);
            if(bioData == null){
                Log.e(TAG, "No bio data found for " + deviceId);
                LostAndFound.askForBio(deviceFound.getMacAddress(), context);
                continue;
            }
            bioData.setDistance(distance);
            displayList.add(bioData);
        }

        // instead of strings, we place a whole object there
        ArrayAdapter<BioProfile> adapter = new ArrayAdapter<>(
                beaconWindow.getContext(),
                android.R.layout.simple_list_item_1,
                displayList
        );
        beaconWindow.setAdapter(adapter);

        // Add click listener to items
        beaconWindow.setOnItemClickListener((parent, view, position, id) -> {
            // get the object
            BioProfile profile = displayList.get(position);

            // make the screen appear
            if(MainActivity.getInstance() == null){
                Log.e(TAG, "MainActivity is null");
                return;
            }

            MainActivity.getInstance().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.main, DeviceDetailsFragment.newInstance(profile))
                    .addToBackStack(null)
                    .commit();
        });
    }


}

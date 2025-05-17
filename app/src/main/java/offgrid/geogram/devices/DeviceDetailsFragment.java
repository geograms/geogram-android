package offgrid.geogram.devices;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import offgrid.geogram.MainActivity;
import offgrid.geogram.R;
import offgrid.geogram.bluetooth.eddystone.DeviceFinder;
import offgrid.geogram.core.Log;
import offgrid.geogram.database.BioDatabase;
import offgrid.geogram.database.BioProfile;
import offgrid.geogram.devices.chat.DeviceChatFragment;

public class DeviceDetailsFragment extends Fragment {

    private static final String TAG = "DeviceDetailsFragment";

    private static final String
            ARG_BEACON_DETAILS = "beacon_details";

    public static DeviceDetailsFragment newInstance(BioProfile profile)  {
        DeviceDetailsFragment fragment = new DeviceDetailsFragment();
        Bundle args = new Bundle();
        args.putString(ARG_BEACON_DETAILS, profile.getDeviceId());
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_device_details, container, false);


        // Handle back button
        ImageButton btnBack = view.findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> requireActivity().onBackPressed());

        // Hide the floating action button
//        FloatingActionButton btnAdd = requireActivity().findViewById(R.id.btn_add);
//        if (btnAdd != null) {
//            btnAdd.hide();
//        }

        // Set beaconDiscovered details text
        TextView deviceDescription = view.findViewById(R.id.tv_device_description);
        TextView deviceDescriptionAdditional = view.findViewById(R.id.tv_device_additional_info);


        // we need to have valid arguments
        if (getArguments() == null) {
            Log.e(TAG, "Invalid arguments");
            return view;
        }

        // get the device Id
        String deviceId = getArguments().getString(ARG_BEACON_DETAILS);
        if (deviceId == null) {
            Log.e(TAG, "Invalid arguments: " + getArguments());
            return view;
        }

        // get the device from the list
        DeviceReachable deviceDiscovered =
                DeviceFinder.getInstance(this.getContext()).getDeviceMap().get(deviceId);
        if(deviceDiscovered == null){
            Log.e(TAG, "Device not found: " + deviceId);
            return view;
        }

        // try to say hello using Wi-Fi
//        new Thread(() -> {
//            // perform the request on its own thread to prevent blocking the UI
//            MessageHello_v1 helloReceived = WiFiUpdates.sayHello(deviceDiscovered, getContext());
//            if(helloReceived != null){
//                Log.i(TAG, "Hello reply received: " + helloReceived.getBioProfile().getNick());
//                getActivity().runOnUiThread(() -> {
//                Toast.makeText(getContext(),
//                        "Hello reply received: " + helloReceived.getBioProfile().getNick(),
//                        Toast.LENGTH_SHORT).show();
//                });
//            }else{
//                getActivity().runOnUiThread(() -> {
//                Log.e(TAG, "Hello reply was not received");
//                Toast.makeText(getContext(),
//                        "Hello reply was not received",
//                        Toast.LENGTH_SHORT).show();
//                });
//            }
//        }).start();

        BioProfile profile = BioDatabase.get(deviceId, this.getContext());
        if(profile == null){
            Log.i(TAG, "No bio data found for " + deviceId);
            return view;
        }

        String deviceName = profile.getNick() + " ("
                + deviceDiscovered.getMacAddress()
                + ")";

        deviceDescription.setText(deviceName);
        deviceDescriptionAdditional.setText(profile.getExtra());


        //disableIcon(view, R.id.section_chat);
        disableIcon(view, R.id.section_messages);
        disableIcon(view, R.id.section_collections);
        disableIcon(view, R.id.section_stats);
        disableIcon(view, R.id.section_settings);

        // Find the chat section
        View chatSection = view.findViewById(R.id.section_chat);

        // Set a click listener
        chatSection.setOnClickListener(v -> launchMessageWindow(deviceDiscovered));

        return view;
    }



    private void disableIcon(View view, int value) {
        LinearLayout icons = view.findViewById(value);
        icons.setAlpha(0.2f); // Set transparency to 50%
        icons.setClickable(false); // Disable click events
    }

    private void launchMessageWindow(DeviceReachable beaconDiscovered) {
        DeviceChatFragment fragment = DeviceChatFragment.newInstance(beaconDiscovered.getDeviceId());

        MainActivity.activity.getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.main, fragment)
                .addToBackStack(null)
                .commit();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        // Ensure the floating action button is shown again when leaving the fragment
//        FloatingActionButton btnAdd = requireActivity().findViewById(R.id.btn_add);
//        if (btnAdd != null) {
//            btnAdd.show();
//        }
    }
}

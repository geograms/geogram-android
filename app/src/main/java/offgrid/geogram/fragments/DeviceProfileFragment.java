package offgrid.geogram.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;

import offgrid.geogram.MainActivity;
import offgrid.geogram.R;
import offgrid.geogram.devices.Device;
import offgrid.geogram.devices.DeviceManager;

public class DeviceProfileFragment extends Fragment {

    private static final String ARG_DEVICE_ID = "device_id";

    public static DeviceProfileFragment newInstance(String deviceId) {
        DeviceProfileFragment fragment = new DeviceProfileFragment();
        Bundle args = new Bundle();
        args.putString(ARG_DEVICE_ID, deviceId);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_device_profile, container, false);

        // Get device ID from arguments
        String deviceId = getArguments() != null ? getArguments().getString(ARG_DEVICE_ID) : null;
        if (deviceId == null) {
            return view;
        }

        // Set device name as title
        TextView titleView = view.findViewById(R.id.tv_device_title);
        titleView.setText(deviceId);

        // Back button
        ImageButton btnBack = view.findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().getSupportFragmentManager().popBackStack();
            }
        });

        // Find device in DeviceManager
        Device device = null;
        for (Device d : DeviceManager.getInstance().getDevicesSpotted()) {
            if (d.ID.equals(deviceId)) {
                device = d;
                break;
            }
        }

        // Get view references
        TextView deviceTypeView = view.findViewById(R.id.tv_device_type);
        TextView firstSeenView = view.findViewById(R.id.tv_first_seen);
        TextView lastSeenView = view.findViewById(R.id.tv_last_seen);
        TextView connectionCountView = view.findViewById(R.id.tv_connection_count);

        if (device != null) {
            // Device found in DeviceManager - show full details
            deviceTypeView.setText("Type: " + device.deviceType.name());

            // First seen
            if (!device.connectedEvents.isEmpty()) {
                long firstSeen = Collections.min(device.connectedEvents).latestTimestamp();
                String firstSeenText = formatTimestamp(firstSeen);
                firstSeenView.setText("First seen: " + firstSeenText);
            } else {
                firstSeenView.setText("First seen: Unknown");
            }

            // Last seen
            long lastSeen = device.latestTimestamp();
            if (lastSeen != Long.MIN_VALUE) {
                String lastSeenText = formatTimestamp(lastSeen);
                lastSeenView.setText("Last seen: " + lastSeenText);
            } else {
                lastSeenView.setText("Last seen: Unknown");
            }

            // Connection count
            int totalConnections = 0;
            for (offgrid.geogram.devices.EventConnected event : device.connectedEvents) {
                totalConnections += event.timestamps.size();
            }
            connectionCountView.setText("Times detected: " + totalConnections);
        } else {
            // Device not found in DeviceManager (only seen via messages)
            deviceTypeView.setText("Type: Message contact");
            firstSeenView.setText("First seen: Not detected nearby");
            lastSeenView.setText("Last seen: Unknown");
            connectionCountView.setText("Times detected: 0 (message contact only)");
        }

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Hide top action bar for detail screens
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setTopActionBarVisible(false);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // Show top action bar when leaving
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setTopActionBarVisible(true);
        }
    }

    private String formatTimestamp(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.US);
        return sdf.format(new Date(timestamp));
    }
}

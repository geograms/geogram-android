package offgrid.geogram.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;

import offgrid.geogram.MainActivity;
import offgrid.geogram.R;
import offgrid.geogram.adapters.RelayDeviceAdapter;
import offgrid.geogram.p2p.DeviceRelayChecker;
import offgrid.geogram.p2p.DeviceRelayClient;

public class RelayDeviceFragment extends Fragment {

    private View view;
    private DeviceRelayClient relayClient;
    private DeviceRelayChecker relayChecker;
    private RelayDeviceAdapter deviceAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_relay_device, container, false);

        relayClient = DeviceRelayClient.getInstance(requireContext());
        relayChecker = DeviceRelayChecker.getInstance(requireContext());

        initializeUI(view);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Hide top action bar for detail screens
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setTopActionBarVisible(false);
        }
        // Refresh status when resuming
        updateStatusHeader();
    }

    @Override
    public void onPause() {
        super.onPause();
        // Show top action bar when leaving
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setTopActionBarVisible(true);
        }
    }

    private void initializeUI(View view) {
        // Back button
        view.findViewById(R.id.btn_back).setOnClickListener(v -> requireActivity().onBackPressed());

        // Status header
        updateStatusHeader();

        // Search section
        EditText searchBox = view.findViewById(R.id.search_box);
        // TODO: Implement search functionality

        // Directory section
        RecyclerView deviceList = view.findViewById(R.id.device_directory_list);
        deviceList.setLayoutManager(new LinearLayoutManager(requireContext()));
        deviceAdapter = new RelayDeviceAdapter(java.util.Arrays.asList()); // Empty initially
        deviceList.setAdapter(deviceAdapter);
    }

    private void updateStatusHeader() {
        if (view == null) return;

        TextView statusText = view.findViewById(R.id.relay_status);
        TextView connectedDevicesText = view.findViewById(R.id.connected_devices_count);
        TextView uptimeText = view.findViewById(R.id.relay_uptime);

        boolean isConnected = relayClient.isConnected();
        statusText.setText(isConnected ? "Connected" : "Disconnected");
        statusText.setTextColor(getResources().getColor(
            isConnected ? android.R.color.holo_green_light : android.R.color.holo_red_light, requireContext().getTheme()));

        // Get actual connected devices count
        java.util.Set<String> connectedDevices = relayChecker.getConnectedDevices();
        connectedDevicesText.setText(connectedDevices.size() + " devices connected");

        // Update device list
        deviceAdapter.updateDevices(new java.util.ArrayList<>(connectedDevices));

        // Show/hide no devices text
        TextView noDevicesText = view.findViewById(R.id.no_devices_text);
        if (connectedDevices.isEmpty()) {
            noDevicesText.setVisibility(View.VISIBLE);
        } else {
            noDevicesText.setVisibility(View.GONE);
        }

        // TODO: Calculate uptime
        uptimeText.setText("Uptime: checking...");
    }
}
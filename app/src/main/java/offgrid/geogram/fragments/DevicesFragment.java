package offgrid.geogram.fragments;

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

import java.util.TreeSet;

import offgrid.geogram.R;
import offgrid.geogram.devices.Device;
import offgrid.geogram.devices.DeviceManager;

public class DevicesFragment extends Fragment {

    private LinearLayout deviceListContainer;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_devices, container, false);

        // Back button functionality
        ImageButton btnBack = view.findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> requireActivity().onBackPressed());

        // Device list container
        deviceListContainer = view.findViewById(R.id.device_list_container);

        // Load devices
        loadDevices();

        return view;
    }

    private void loadDevices() {
        deviceListContainer.removeAllViews();

        TreeSet<Device> devices = DeviceManager.getInstance().getDevicesSpotted();

        if (devices.isEmpty()) {
            // Show empty state
            TextView emptyText = new TextView(getContext());
            emptyText.setText("No nearby devices found");
            emptyText.setTextColor(getResources().getColor(R.color.white));
            emptyText.setTextSize(16);
            emptyText.setPadding(16, 32, 16, 16);
            emptyText.setGravity(android.view.Gravity.CENTER);
            deviceListContainer.addView(emptyText);
        } else {
            // Show each device
            for (Device device : devices) {
                View deviceItem = LayoutInflater.from(getContext())
                        .inflate(R.layout.item_device, deviceListContainer, false);

                TextView deviceName = deviceItem.findViewById(R.id.device_name);
                TextView deviceType = deviceItem.findViewById(R.id.device_type);

                deviceName.setText(device.ID);
                deviceType.setText(device.deviceType != null ? device.deviceType.toString() : "Unknown");

                deviceListContainer.addView(deviceItem);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        loadDevices();
    }
}

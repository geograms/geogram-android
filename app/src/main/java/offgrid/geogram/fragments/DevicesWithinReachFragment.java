package offgrid.geogram.fragments;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TreeSet;

import offgrid.geogram.R;
import offgrid.geogram.devices.Device;
import offgrid.geogram.devices.DeviceManager;

public class DevicesWithinReachFragment extends Fragment {

    private RecyclerView recyclerView;
    private DeviceAdapter adapter;
    private TextView emptyMessage;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_devices_within_reach, container, false);

        // Initialize RecyclerView
        recyclerView = view.findViewById(R.id.devices_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        emptyMessage = view.findViewById(R.id.empty_message);

        // Load devices
        loadDevices();

        return view;
    }

    private void loadDevices() {
        TreeSet<Device> devices = DeviceManager.getInstance().getDevicesSpotted();

        if (devices.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            emptyMessage.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyMessage.setVisibility(View.GONE);
            adapter = new DeviceAdapter(devices);
            recyclerView.setAdapter(adapter);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        loadDevices();
    }

    // RecyclerView Adapter
    private class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder> {

        private final TreeSet<Device> devices;

        public DeviceAdapter(TreeSet<Device> devices) {
            this.devices = devices;
        }

        @NonNull
        @Override
        public DeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_device, parent, false);
            return new DeviceViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull DeviceViewHolder holder, int position) {
            Device device = (Device) devices.toArray()[position];
            holder.bind(device);

            // Set click listener to navigate to device profile
            holder.itemView.setOnClickListener(v -> {
                if (getActivity() != null) {
                    DeviceProfileFragment fragment = DeviceProfileFragment.newInstance(device.ID);
                    getActivity().getSupportFragmentManager()
                            .beginTransaction()
                            .replace(R.id.fragment_container, fragment)
                            .addToBackStack(null)
                            .commit();
                }
            });
        }

        @Override
        public int getItemCount() {
            return devices.size();
        }

        static class DeviceViewHolder extends RecyclerView.ViewHolder {
            private final TextView deviceName;
            private final TextView deviceType;
            private final TextView deviceLastSeen;

            public DeviceViewHolder(@NonNull View itemView) {
                super(itemView);
                deviceName = itemView.findViewById(R.id.device_name);
                deviceType = itemView.findViewById(R.id.device_type);
                deviceLastSeen = itemView.findViewById(R.id.device_last_seen);
            }

            public void bind(Device device) {
                deviceName.setText(device.ID);
                deviceType.setText(device.deviceType.name());

                // Format the last seen time
                long timestamp = device.latestTimestamp();
                String lastSeenText;
                boolean isInactive = false;

                if (timestamp == Long.MIN_VALUE) {
                    lastSeenText = "Last seen: Unknown";
                    isInactive = true;
                } else {
                    long now = System.currentTimeMillis();
                    long diff = now - timestamp;

                    // Mark as inactive if more than 5 minutes (300000 ms)
                    if (diff > 300000) {
                        isInactive = true;
                    }

                    if (diff < 60000) {
                        lastSeenText = "Last seen: Just now";
                    } else if (diff < 3600000) {
                        long minutes = diff / 60000;
                        lastSeenText = "Last seen: " + minutes + " min ago";
                    } else if (diff < 86400000) {
                        long hours = diff / 3600000;
                        lastSeenText = "Last seen: " + hours + " hr ago";
                    } else {
                        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, HH:mm", Locale.US);
                        lastSeenText = "Last seen: " + sdf.format(new Date(timestamp));
                    }
                }

                deviceLastSeen.setText(lastSeenText);

                // Apply grey color for inactive devices (not seen in 5+ minutes)
                if (isInactive) {
                    deviceName.setTextColor(Color.parseColor("#888888"));
                    deviceType.setTextColor(Color.parseColor("#666666"));
                    deviceLastSeen.setTextColor(Color.parseColor("#555555"));
                } else {
                    deviceName.setTextColor(Color.parseColor("#FFFFFF"));
                    deviceType.setTextColor(Color.parseColor("#AAAAAA"));
                    deviceLastSeen.setTextColor(Color.parseColor("#888888"));
                }
            }
        }
    }
}

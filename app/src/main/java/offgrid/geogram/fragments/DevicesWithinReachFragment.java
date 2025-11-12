package offgrid.geogram.fragments;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TreeSet;

import offgrid.geogram.MainActivity;
import offgrid.geogram.R;
import offgrid.geogram.devices.Device;
import offgrid.geogram.devices.DeviceManager;
import offgrid.geogram.devices.DeviceType;
import offgrid.geogram.events.EventAction;
import offgrid.geogram.events.EventControl;
import offgrid.geogram.events.EventType;

public class DevicesWithinReachFragment extends Fragment {

    private static final String EVENT_LISTENER_ID = "DevicesWithinReachFragment_Listener";

    private RecyclerView recyclerView;
    private DeviceAdapter adapter;
    private TextView emptyMessage;
    private EventAction deviceUpdateListener;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_devices_within_reach, container, false);

        // Initialize RecyclerView
        recyclerView = view.findViewById(R.id.devices_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        emptyMessage = view.findViewById(R.id.empty_message);

        // Initialize Clear List button
        Button btnClearList = view.findViewById(R.id.btn_clear_list);
        btnClearList.setOnClickListener(v -> {
            DeviceManager.getInstance().clear();
            loadDevices();
            // Update device count badge in MainActivity
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).updateDeviceCount();
            }
            Toast.makeText(getContext(), "Device list cleared", Toast.LENGTH_SHORT).show();
        });

        // Create event listener for device updates (only if not already created)
        if (deviceUpdateListener == null) {
            deviceUpdateListener = new EventAction(EVENT_LISTENER_ID) {
                @Override
                public void action(Object... data) {
                    // Update UI on main thread
                    if (getActivity() != null && isAdded()) {
                        getActivity().runOnUiThread(() -> {
                            if (isAdded() && getView() != null) {
                                loadDevices();
                                // Also update device count badge in MainActivity
                                if (getActivity() instanceof MainActivity) {
                                    ((MainActivity) getActivity()).updateDeviceCount();
                                }
                            }
                        });
                    }
                }
            };
        }

        return view;
    }

    private void loadDevices() {
        if (recyclerView == null || emptyMessage == null) {
            android.util.Log.d("DevicesFragment", "loadDevices: Views not ready yet");
            return; // Views not ready yet
        }

        TreeSet<Device> devices = DeviceManager.getInstance().getDevicesSpotted();
        android.util.Log.d("DevicesFragment", "loadDevices: Found " + devices.size() + " devices");

        if (devices.isEmpty()) {
            android.util.Log.d("DevicesFragment", "loadDevices: Showing empty message");
            recyclerView.setVisibility(View.GONE);
            emptyMessage.setVisibility(View.VISIBLE);
            adapter = null; // Clear adapter when no devices
        } else {
            android.util.Log.d("DevicesFragment", "loadDevices: Showing " + devices.size() + " devices in list");
            recyclerView.setVisibility(View.VISIBLE);
            emptyMessage.setVisibility(View.GONE);

            if (adapter == null) {
                // Create new adapter
                android.util.Log.d("DevicesFragment", "loadDevices: Creating new adapter");
                adapter = new DeviceAdapter(devices);
            } else {
                // Update existing adapter
                android.util.Log.d("DevicesFragment", "loadDevices: Updating existing adapter");
                adapter.updateDevices(devices);
            }

            // CRITICAL FIX: Always attach adapter to recyclerView
            // (views are recreated when navigating back, but adapter persists)
            if (recyclerView.getAdapter() != adapter) {
                android.util.Log.d("DevicesFragment", "loadDevices: Re-attaching adapter to new RecyclerView");
                recyclerView.setAdapter(adapter);
            }
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        android.util.Log.d("DevicesFragment", "onViewCreated called");
        // Load devices after view is created
        loadDevices();
    }

    @Override
    public void onResume() {
        super.onResume();
        android.util.Log.d("DevicesFragment", "onResume called");
        // Show top action bar for main screens
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setTopActionBarVisible(true);
        }
        // Register event listener for real-time updates
        if (deviceUpdateListener != null) {
            EventControl.addEvent(EventType.DEVICE_UPDATED, deviceUpdateListener);
        }
        // Refresh the device list when fragment becomes visible
        if (getView() != null) {
            loadDevices();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // Note: EventControl doesn't have removeEvent, listener stays registered
    }

    // RecyclerView Adapter
    private class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder> {

        private TreeSet<Device> devices;

        public DeviceAdapter(TreeSet<Device> devices) {
            this.devices = new TreeSet<>(devices);
        }

        public void updateDevices(TreeSet<Device> newDevices) {
            this.devices = new TreeSet<>(newDevices);
            // Notify on main thread to ensure UI updates properly
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> notifyDataSetChanged());
            } else {
                notifyDataSetChanged();
            }
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
            private final TextView relayBadge;
            private final android.widget.LinearLayout channelIndicators;

            public DeviceViewHolder(@NonNull View itemView) {
                super(itemView);
                deviceName = itemView.findViewById(R.id.device_name);
                deviceType = itemView.findViewById(R.id.device_type);
                deviceLastSeen = itemView.findViewById(R.id.device_last_seen);
                relayBadge = itemView.findViewById(R.id.relay_badge);
                channelIndicators = itemView.findViewById(R.id.channel_indicators);
            }

            public void bind(Device device) {
                deviceName.setText(device.ID);
                // Display custom device model if available, otherwise show device type
                deviceType.setText(device.getDisplayName());

                // Show relay badge for IGate devices
                if (device.deviceType == DeviceType.INTERNET_IGATE) {
                    relayBadge.setVisibility(View.VISIBLE);
                } else {
                    relayBadge.setVisibility(View.GONE);
                }

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

                // Add channel indicators (BLE, WIFI)
                channelIndicators.removeAllViews();

                // Check which connection types this device has
                boolean hasBLE = false;
                boolean hasWiFi = false;

                for (offgrid.geogram.devices.EventConnected event : device.connectedEvents) {
                    if (event.connectionType == offgrid.geogram.devices.ConnectionType.BLE) {
                        hasBLE = true;
                    } else if (event.connectionType == offgrid.geogram.devices.ConnectionType.WIFI) {
                        hasWiFi = true;
                    }
                }

                // Also check WiFi discovery service for current WiFi availability
                if (!hasWiFi) {
                    offgrid.geogram.wifi.WiFiDiscoveryService wifiService =
                        offgrid.geogram.wifi.WiFiDiscoveryService.getInstance(itemView.getContext());
                    // Check if device has an IP address in WiFi discovery
                    if (wifiService.getDeviceIp(device.ID) != null) {
                        hasWiFi = true;
                    }
                }

                // Add BLE badge
                if (hasBLE) {
                    TextView bleBadge = new TextView(itemView.getContext());
                    bleBadge.setText("BLE");
                    bleBadge.setTextSize(10);
                    bleBadge.setTextColor(Color.WHITE);
                    bleBadge.setBackgroundColor(0xFF666666); // Dark grey
                    bleBadge.setPadding(8, 4, 8, 4);
                    android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    );
                    params.setMargins(0, 0, 8, 0);
                    bleBadge.setLayoutParams(params);
                    channelIndicators.addView(bleBadge);
                }

                // Add WiFi badge
                if (hasWiFi) {
                    TextView wifiBadge = new TextView(itemView.getContext());
                    wifiBadge.setText("WIFI");
                    wifiBadge.setTextSize(10);
                    wifiBadge.setTextColor(Color.WHITE);
                    wifiBadge.setBackgroundColor(0xFF666666); // Dark grey
                    wifiBadge.setPadding(8, 4, 8, 4);
                    android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    );
                    wifiBadge.setLayoutParams(params);
                    channelIndicators.addView(wifiBadge);
                }

                // Show/hide channel indicators based on whether any badges were added
                if (hasBLE || hasWiFi) {
                    channelIndicators.setVisibility(View.VISIBLE);
                } else {
                    channelIndicators.setVisibility(View.GONE);
                }
            }
        }
    }
}

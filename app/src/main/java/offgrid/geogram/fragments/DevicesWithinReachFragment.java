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
import offgrid.geogram.p2p.DeviceRelayChecker;
import offgrid.geogram.p2p.P2PHttpClient;

public class DevicesWithinReachFragment extends Fragment {

    private static final String EVENT_LISTENER_ID = "DevicesWithinReachFragment_Listener";
    private static final long REACHABILITY_CHECK_INTERVAL = 60000; // Check every minute

    private RecyclerView recyclerView;
    private DeviceAdapter adapter;
    private TextView emptyMessage;
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipeRefresh;
    private EventAction deviceUpdateListener;
    private android.os.Handler reachabilityHandler;
    private Runnable reachabilityCheckRunnable;
    private android.content.BroadcastReceiver wifiDiscoveryReceiver;
    private android.content.BroadcastReceiver relayStatusReceiver;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_devices_within_reach, container, false);

        // Initialize RecyclerView
        recyclerView = view.findViewById(R.id.devices_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        emptyMessage = view.findViewById(R.id.empty_message);

        // Initialize SwipeRefreshLayout
        swipeRefresh = view.findViewById(R.id.swipe_refresh);
        swipeRefresh.setColorSchemeColors(Color.WHITE);
        swipeRefresh.setProgressBackgroundColorSchemeColor(Color.parseColor("#404040"));
        swipeRefresh.setOnRefreshListener(() -> {
            // Reload device list and update reachability status
            loadDevices();
            // Update device count badge in MainActivity
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).updateDeviceCount();
            }
            // Stop refreshing animation
            swipeRefresh.setRefreshing(false);
        });

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

        TreeSet<Device> devices = new TreeSet<>(DeviceManager.getInstance().getDevicesSpotted());

        // Add device relay server as a special device
        Device relayDevice = createRelayDevice();
        if (relayDevice != null) {
            devices.add(relayDevice);
        }

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

        // Trigger immediate device scans when user opens the nearby devices screen
        triggerDeviceScans();

        // Start periodic reachability checks
        startReachabilityChecks();

        // Register WiFi discovery broadcast receiver
        if (wifiDiscoveryReceiver == null) {
            wifiDiscoveryReceiver = new android.content.BroadcastReceiver() {
                @Override
                public void onReceive(android.content.Context context, android.content.Intent intent) {
                    // WiFi discovery has found devices - refresh the device list
                    android.util.Log.d("DevicesFragment", "WiFi discovery update received - refreshing device list");
                    loadDevices();
                }
            };
        }

        // Register receiver
        if (getContext() != null) {
            android.content.IntentFilter filter = new android.content.IntentFilter("offgrid.geogram.WIFI_DISCOVERY_UPDATE");
            androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(getContext())
                    .registerReceiver(wifiDiscoveryReceiver, filter);
        }

        // Register relay status broadcast receiver for immediate UI updates
        if (relayStatusReceiver == null) {
            relayStatusReceiver = new android.content.BroadcastReceiver() {
                @Override
                public void onReceive(android.content.Context context, android.content.Intent intent) {
                    boolean isConnected = intent.getBooleanExtra(
                            offgrid.geogram.p2p.DeviceRelayClient.EXTRA_IS_CONNECTED, false);
                    android.util.Log.d("DevicesFragment", "Relay status changed: " +
                            (isConnected ? "CONNECTED" : "DISCONNECTED") + " - refreshing UI");
                    if (adapter != null) {
                        adapter.notifyDataSetChanged();
                    }
                }
            };
        }

        // Register relay status receiver
        if (getContext() != null) {
            android.content.IntentFilter relayFilter = new android.content.IntentFilter(
                    offgrid.geogram.p2p.DeviceRelayClient.ACTION_RELAY_STATUS_CHANGED);
            // Use RECEIVER_NOT_EXPORTED since this is an internal app broadcast
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                getContext().registerReceiver(relayStatusReceiver, relayFilter,
                        android.content.Context.RECEIVER_NOT_EXPORTED);
            } else {
                getContext().registerReceiver(relayStatusReceiver, relayFilter);
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // Note: EventControl doesn't have removeEvent, listener stays registered

        // Stop periodic reachability checks
        stopReachabilityChecks();

        // Unregister WiFi discovery broadcast receiver
        if (wifiDiscoveryReceiver != null && getContext() != null) {
            androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(getContext())
                    .unregisterReceiver(wifiDiscoveryReceiver);
        }

        // Unregister relay status broadcast receiver
        if (relayStatusReceiver != null && getContext() != null) {
            getContext().unregisterReceiver(relayStatusReceiver);
        }
    }

    private void startReachabilityChecks() {
        if (reachabilityHandler == null) {
            reachabilityHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        }

        if (reachabilityCheckRunnable == null) {
            reachabilityCheckRunnable = new Runnable() {
                @Override
                public void run() {
                    checkDevicesReachability();
                    // Schedule next check
                    if (reachabilityHandler != null) {
                        reachabilityHandler.postDelayed(this, REACHABILITY_CHECK_INTERVAL);
                    }
                }
            };
        }

        // Start first check immediately
        reachabilityHandler.post(reachabilityCheckRunnable);
    }

    private void stopReachabilityChecks() {
        if (reachabilityHandler != null && reachabilityCheckRunnable != null) {
            reachabilityHandler.removeCallbacks(reachabilityCheckRunnable);
        }
    }

    /**
     * Trigger immediate device discovery scans (BLE and WiFi)
     * Called when user navigates to the nearby devices screen
     */
    private void triggerDeviceScans() {
        if (getContext() == null) {
            return;
        }

        android.util.Log.i("DevicesFragment", "Triggering immediate device scans (BLE + WiFi)");

        // Trigger BLE self-advertise/ping
        try {
            offgrid.geogram.ble.BluetoothSender sender =
                offgrid.geogram.ble.BluetoothSender.getInstance(getContext());
            sender.triggerImmediatePing();
        } catch (Exception e) {
            android.util.Log.e("DevicesFragment", "Error triggering BLE ping: " + e.getMessage());
        }

        // Trigger WiFi network scan
        try {
            offgrid.geogram.wifi.WiFiDiscoveryService wifiService =
                offgrid.geogram.wifi.WiFiDiscoveryService.getInstance(getContext());
            wifiService.triggerImmediateScan();
        } catch (Exception e) {
            android.util.Log.e("DevicesFragment", "Error triggering WiFi scan: " + e.getMessage());
        }

        // Also check relay connectivity
        try {
            DeviceRelayChecker relayChecker = DeviceRelayChecker.getInstance(getContext());
            // RelayChecker runs automatically every 30 seconds, no manual trigger needed
        } catch (Exception e) {
            android.util.Log.e("DevicesFragment", "Error accessing relay checker: " + e.getMessage());
        }
    }

    private void checkDevicesReachability() {
        TreeSet<Device> devices = DeviceManager.getInstance().getDevicesSpotted();
        offgrid.geogram.wifi.WiFiDiscoveryService wifiService =
            offgrid.geogram.wifi.WiFiDiscoveryService.getInstance(requireContext());

        // Remove devices that haven't been seen in 24 hours
        long currentTime = System.currentTimeMillis();
        long twentyFourHoursInMillis = 24 * 60 * 60 * 1000; // 24 hours
        java.util.List<Device> devicesToRemove = new java.util.ArrayList<>();

        for (Device device : devices) {
            // Skip the relay server device (special device)
            if (device.ID.equals("RELAY_SERVER")) {
                continue;
            }

            // Check if device is too old
            long deviceAge = currentTime - device.latestTimestamp();
            if (deviceAge > twentyFourHoursInMillis) {
                devicesToRemove.add(device);
                android.util.Log.d("DevicesFragment", "Marking device for removal: " + device.ID + " (age: " + (deviceAge / 3600000) + " hours)");
                continue;
            }

            // Check WiFi reachability for devices with IP addresses
            String deviceIp = wifiService.getDeviceIp(device.ID);
            if (deviceIp != null) {
                // Device has WiFi - check if it's reachable
                checkDeviceReachability(device, deviceIp);
            }
        }

        // Remove old devices from DeviceManager
        if (!devicesToRemove.isEmpty()) {
            for (Device device : devicesToRemove) {
                DeviceManager.getInstance().removeDevice(device);
                android.util.Log.d("DevicesFragment", "Removed old device: " + device.ID);
            }
            // Refresh the UI after cleanup
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    loadDevices();
                    // Update device count badge in MainActivity
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).updateDeviceCount();
                    }
                });
            }
        }

        // Always refresh UI to update relay connection status badge
        // (relay device is skipped in the loop above, so we need to refresh here)
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
            });
        }
    }

    private void checkDeviceReachability(Device device, String deviceIp) {
        // Run in background thread
        new Thread(() -> {
            try {
                String apiUrl = "http://" + deviceIp + ":45678/api/status";
                java.net.URL url = new java.net.URL(apiUrl);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);

                int responseCode = conn.getResponseCode();
                boolean isReachable = (responseCode == java.net.HttpURLConnection.HTTP_OK);

                // Update device reachability status
                device.setWiFiReachable(isReachable);

                conn.disconnect();

                // Update UI if reachability changed
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (adapter != null) {
                            adapter.notifyDataSetChanged();
                        }
                    });
                }
            } catch (Exception e) {
                // Device not reachable
                device.setWiFiReachable(false);

                // Update UI
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (adapter != null) {
                            adapter.notifyDataSetChanged();
                        }
                    });
                }
            }
        }).start();
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
                    androidx.fragment.app.Fragment fragment;
                    if (device.ID.equals("RELAY_SERVER")) {
                        // Relay server - open relay device panel
                        fragment = new RelayDeviceFragment();
                    } else {
                        // Regular device - open device profile
                        fragment = DeviceProfileFragment.newInstance(device.ID);
                    }
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
            private final TextView profileDescription;
            private final TextView deviceLastSeen;

            private final android.widget.LinearLayout channelIndicators;
            private final android.widget.ImageView profileImage;

            public DeviceViewHolder(@NonNull View itemView) {
                super(itemView);
                deviceName = itemView.findViewById(R.id.device_name);
                deviceType = itemView.findViewById(R.id.device_type);
                profileDescription = itemView.findViewById(R.id.profile_description);
                deviceLastSeen = itemView.findViewById(R.id.device_last_seen);

                channelIndicators = itemView.findViewById(R.id.channel_indicators);
                profileImage = itemView.findViewById(R.id.profile_image);
            }

            public void bind(Device device) {
                // Display nickname with callsign if available, otherwise just callsign
                if (device.getProfileNickname() != null && !device.getProfileNickname().isEmpty()) {
                    deviceName.setText(device.getProfileNickname() + " (" + device.ID + ")");
                } else {
                    // Special handling for relay server - show server URL instead of ID
                    if (device.ID.equals("RELAY_SERVER")) {
                        String serverUrl = offgrid.geogram.settings.ConfigManager.getInstance(itemView.getContext()).getConfig().getDeviceRelayServerUrl();
                        String displayUrl = extractServerHost(serverUrl);
                        deviceName.setText(displayUrl);
                    } else {
                        deviceName.setText(device.ID);
                    }
                }

                // Display profile picture if available
                if (device.getProfilePicture() != null) {
                    // Create circular bitmap for profile picture
                    android.graphics.Bitmap profileBitmap = device.getProfilePicture();
                    android.graphics.Bitmap circularBitmap = getCircularBitmap(profileBitmap);

                    profileImage.setImageBitmap(circularBitmap);
                    profileImage.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                    profileImage.setPadding(2, 2, 2, 2);
                    profileImage.setBackgroundColor(0); // Remove background
                 } else {
                    // Use server icon for relay device, person icon for others
                    if (device.ID.equals("RELAY_SERVER")) {
                        profileImage.setImageResource(R.drawable.ic_tethering);
                        profileImage.setImageTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE));
                    } else {
                        profileImage.setImageResource(R.drawable.ic_person);
                        profileImage.clearColorFilter(); // Ensure no tint for person icon
                    }
                    profileImage.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
                    profileImage.setPadding(8, 8, 8, 8);
                    profileImage.setBackgroundColor(itemView.getContext().getResources().getColor(R.color.dark_gray, null));
                }

                // Display device info:
                // - For relay server: show "Internet Relay"
                // - For devices with model: show model (e.g., "Geogram v0.4.0")
                // - For regular phones without model: show empty (don't show generic type)
                if (device.ID.equals("RELAY_SERVER")) {
                    // This is the actual relay server
                    deviceType.setText("Internet Relay");
                } else if (device.getDeviceModel() != null) {
                    // Device has a proper model string from the app
                    deviceType.setText(device.getDisplayName());
                } else {
                    // Regular device without model - don't show generic device type
                    deviceType.setText("");
                }

                // Display profile description if available
                if (device.getProfileDescription() != null && !device.getProfileDescription().isEmpty()) {
                    profileDescription.setText(device.getProfileDescription());
                    profileDescription.setVisibility(View.VISIBLE);
                } else {
                    profileDescription.setVisibility(View.GONE);
                }

                // Fetch profile data if WiFi is available and not yet fetched
                fetchProfileIfAvailable(device);

                // Relay badge removed - was showing "RELAY" in green for IGate devices

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

                // Add channel indicators (BLE, WIFI, P2P)
                channelIndicators.removeAllViews();

                // Check which connection types this device has (only show CURRENT connection methods)
                // Strategy: show BLE only if the most recent connection was BLE, not just if there are old BLE events
                boolean hasBLE = false;
                boolean hasWiFi = false;
                long now = System.currentTimeMillis();
                long fiveMinutesAgo = now - 300000; // 5 minutes in milliseconds

                // Find the most recent connection event
                offgrid.geogram.devices.EventConnected mostRecentEvent = null;
                long mostRecentTimestamp = 0;

                for (offgrid.geogram.devices.EventConnected event : device.connectedEvents) {
                    if (!event.timestamps.isEmpty()) {
                        long latestTimestamp = event.timestamps.get(event.timestamps.size() - 1);
                        // Only consider recent events (within last 5 minutes)
                        if (latestTimestamp >= fiveMinutesAgo && latestTimestamp > mostRecentTimestamp) {
                            mostRecentTimestamp = latestTimestamp;
                            mostRecentEvent = event;
                        }
                    }
                }

                // Set connection type based on the MOST RECENT connection
                if (mostRecentEvent != null) {
                    if (mostRecentEvent.connectionType == offgrid.geogram.devices.ConnectionType.BLE) {
                        hasBLE = true;
                    } else if (mostRecentEvent.connectionType == offgrid.geogram.devices.ConnectionType.WIFI) {
                        hasWiFi = true;
                    }
                }

                // Also check WiFi discovery service for current WiFi availability
                // Only show WIFI tag if device is currently reachable
                if (!hasWiFi) {
                    offgrid.geogram.wifi.WiFiDiscoveryService wifiService =
                        offgrid.geogram.wifi.WiFiDiscoveryService.getInstance(itemView.getContext());
                    // Check if device has an IP address AND is reachable
                    if (wifiService.getDeviceIp(device.ID) != null && device.isWiFiReachable()) {
                        hasWiFi = true;
                    }
                }

                // Add BLE badge (avoid duplicates)
                // CRITICAL: Only show BLE badge if Bluetooth is actually enabled on THIS device
                // Otherwise we show stale BLE events from before Bluetooth was disabled
                boolean isBluetoothEnabled = false;
                try {
                    android.bluetooth.BluetoothManager btManager =
                        (android.bluetooth.BluetoothManager) itemView.getContext().getSystemService(android.content.Context.BLUETOOTH_SERVICE);
                    if (btManager != null && btManager.getAdapter() != null) {
                        isBluetoothEnabled = btManager.getAdapter().isEnabled();
                    }
                } catch (Exception e) {
                    android.util.Log.e("DevicesFragment", "Error checking Bluetooth status: " + e.getMessage());
                }

                if (hasBLE && isBluetoothEnabled && !badgeExists(channelIndicators, "BLE")) {
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

                // Add WiFi badge (avoid duplicates)
                // Only show WIFI tag if device is currently reachable
                if (hasWiFi && device.isWiFiReachable() && !badgeExists(channelIndicators, "WIFI")) {
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
                    params.setMargins(0, 0, 8, 0);
                    wifiBadge.setLayoutParams(params);
                    channelIndicators.addView(wifiBadge);
                    // Note: Removed "NOT REACHABLE" tag - if not reachable, we simply don't show the WIFI tag
                 }

                 // Add relay connection status badge for relay server
                 if (device.ID.equals("RELAY_SERVER")) {
                     // Remove old status badges to ensure we show current status
                     removeBadge(channelIndicators, "Connected");
                     removeBadge(channelIndicators, "Disconnected");

                     offgrid.geogram.p2p.DeviceRelayClient relayClient = offgrid.geogram.p2p.DeviceRelayClient.getInstance(itemView.getContext());
                     boolean isConnected = relayClient.isConnected();

                     TextView statusBadge = new TextView(itemView.getContext());
                     statusBadge.setText(isConnected ? "Connected" : "Disconnected");
                     statusBadge.setTextSize(10);
                     statusBadge.setTextColor(Color.WHITE);
                     statusBadge.setBackgroundColor(isConnected ? 0xFF00AA00 : 0xFF666666); // Green for connected, grey for disconnected
                     statusBadge.setPadding(8, 4, 8, 4);
                     android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(
                         android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                         android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                     );
                     params.setMargins(0, 0, 8, 0);
                     statusBadge.setLayoutParams(params);
                     channelIndicators.addView(statusBadge);
                 }

                 // Ping device via relay to check if reachable over internet
                // Skip relay ping for RELAY_SERVER itself (doesn't make sense to ping the relay through itself)
                if (device.ID.equals("RELAY_SERVER")) {
                    // RELAY_SERVER connection status already shown via WebSocket status badge
                    // Don't try to ping it through the relay proxy
                } else {
                    // Use callsign if available, otherwise use ID
                    String callsignToCheck = (device.callsign != null && !device.callsign.isEmpty())
                        ? device.callsign
                        : device.ID;

                    // Ping via relay in background thread
                    if (callsignToCheck != null && !callsignToCheck.isEmpty()) {
                    android.util.Log.d("Relay/DevicesFragment", "Starting relay ping check for device: " + callsignToCheck);
                    final android.widget.LinearLayout channelIndicatorsFinal = channelIndicators;
                    final String callsignFinal = callsignToCheck;
                    new Thread(() -> {
                        try {
                            android.util.Log.d("Relay/DevicesFragment", "Creating P2PHttpClient for relay ping: " + callsignFinal);
                            P2PHttpClient httpClient = new P2PHttpClient(itemView.getContext());
                            android.util.Log.d("Relay/DevicesFragment", "Calling pingViaRelay for: " + callsignFinal);
                            boolean hasRelay = httpClient.pingViaRelay(callsignFinal);
                            android.util.Log.d("Relay/DevicesFragment", "Relay ping result for " + callsignFinal + ": " + hasRelay);

                             // Update UI on main thread
                             if (itemView.getContext() instanceof android.app.Activity) {
                                 ((android.app.Activity) itemView.getContext()).runOnUiThread(() -> {
                                     if (hasRelay) {
                                         // Check if NET badge already exists to avoid duplicates
                                         boolean netBadgeExists = false;
                                         for (int i = 0; i < channelIndicatorsFinal.getChildCount(); i++) {
                                             android.view.View child = channelIndicatorsFinal.getChildAt(i);
                                             if (child instanceof TextView && "NET".equals(((TextView) child).getText().toString())) {
                                                 netBadgeExists = true;
                                                 break;
                                             }
                                         }

                                         if (!netBadgeExists) {
                                             android.util.Log.d("Relay/DevicesFragment", "Adding NET badge for: " + callsignFinal);
                                             TextView netBadge = new TextView(itemView.getContext());
                                             netBadge.setText("NET");
                                             netBadge.setTextSize(10);
                                             netBadge.setTextColor(Color.WHITE);
                                             netBadge.setBackgroundColor(0xFF0066CC); // Blue background
                                             netBadge.setPadding(8, 4, 8, 4);
                                             android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(
                                                 android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                                                 android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                                             );
                                             params.setMargins(0, 0, 8, 0);
                                             netBadge.setLayoutParams(params);
                                             channelIndicatorsFinal.addView(netBadge);

                                             // Show channel indicators if any badges present
                                             if (channelIndicatorsFinal.getChildCount() > 0) {
                                                 channelIndicatorsFinal.setVisibility(View.VISIBLE);
                                             }
                                         } else {
                                             android.util.Log.d("Relay/DevicesFragment", "NET badge already exists for: " + callsignFinal);
                                         }
                                     } else {
                                         android.util.Log.d("Relay/DevicesFragment", "No NET badge (relay ping failed) for: " + callsignFinal);
                                     }
                                 });
                             }
                        } catch (Exception e) {
                            android.util.Log.e("Relay/DevicesFragment", "Error during relay ping for " + callsignFinal + ": " + e.getMessage(), e);
                        }
                    }).start();
                    } else {
                        android.util.Log.d("Relay/DevicesFragment", "Skipping relay ping - no callsign/ID for device");
                    }
                }

                // Show/hide channel indicators based on whether any badges were added
                // (NET badge is added asynchronously, so it will update visibility when added)
                if (hasBLE || hasWiFi || device.ID.equals("RELAY_SERVER")) {
                    channelIndicators.setVisibility(View.VISIBLE);
                } else {
                    channelIndicators.setVisibility(View.GONE);
                }
            }

            private void fetchProfileIfAvailable(Device device) {
                // First, try to load from cache to show something immediately
                if (offgrid.geogram.util.RemoteProfileCache.isCacheValid(itemView.getContext(), device.ID)) {
                    // Load cached profile immediately for faster display
                    String cachedNickname = offgrid.geogram.util.RemoteProfileCache.getNickname(itemView.getContext(), device.ID);
                    String cachedDescription = offgrid.geogram.util.RemoteProfileCache.getDescription(itemView.getContext(), device.ID);
                    String cachedColor = offgrid.geogram.util.RemoteProfileCache.getPreferredColor(itemView.getContext(), device.ID);
                    String cachedNpub = offgrid.geogram.util.RemoteProfileCache.getNpub(itemView.getContext(), device.ID);
                    android.graphics.Bitmap cachedPicture = offgrid.geogram.util.RemoteProfileCache.getProfilePicture(itemView.getContext(), device.ID);

                    if (cachedNickname != null) {
                        device.setProfileNickname(cachedNickname);
                    }
                    if (cachedDescription != null) {
                        device.setProfileDescription(cachedDescription);
                    }
                    if (cachedNpub != null) {
                        device.setProfileNpub(cachedNpub);
                    }
                    if (cachedColor != null) {
                        device.setProfilePreferredColor(cachedColor);
                    }
                    if (cachedPicture != null) {
                        device.setProfilePicture(cachedPicture);
                    }

                    android.util.Log.d("DevicesFragment", "Loaded profile from cache for " + device.ID);
                }

                // Check if device has WiFi available
                offgrid.geogram.wifi.WiFiDiscoveryService wifiService =
                    offgrid.geogram.wifi.WiFiDiscoveryService.getInstance(itemView.getContext());
                String deviceIp = wifiService.getDeviceIp(device.ID);

                if (deviceIp == null) {
                    return; // No WiFi available, use cached data only
                }

                // Skip if already fetched recently (within the last minute to avoid spam)
                if (device.isProfileFetched()) {
                    long timeSinceLastFetch = System.currentTimeMillis() - device.getLastReachabilityCheck();
                    if (timeSinceLastFetch < 60000) { // Less than 1 minute ago
                        return;
                    }
                }

                // Mark as fetched to prevent duplicate requests
                device.setProfileFetched(true);

                // Fetch profile in background thread
                new Thread(() -> {
                    try {
                        // Fetch profile metadata
                        String apiUrl = "http://" + deviceIp + ":45678/api/profile";
                        java.net.URL url = new java.net.URL(apiUrl);
                        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("GET");
                        conn.setConnectTimeout(3000);
                        conn.setReadTimeout(3000);

                        int responseCode = conn.getResponseCode();
                        if (responseCode == java.net.HttpURLConnection.HTTP_OK) {
                            java.io.BufferedReader reader = new java.io.BufferedReader(
                                new java.io.InputStreamReader(conn.getInputStream()));
                            StringBuilder response = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) {
                                response.append(line);
                            }
                            reader.close();

                            // Parse JSON response
                            com.google.gson.JsonObject jsonResponse = com.google.gson.JsonParser
                                .parseString(response.toString()).getAsJsonObject();

                            if (jsonResponse.get("success").getAsBoolean()) {
                                String nickname = jsonResponse.has("nickname") && !jsonResponse.get("nickname").isJsonNull()
                                    ? jsonResponse.get("nickname").getAsString() : "";
                                String description = jsonResponse.has("description") && !jsonResponse.get("description").isJsonNull()
                                    ? jsonResponse.get("description").getAsString() : "";
                                String preferredColor = jsonResponse.has("preferredColor") && !jsonResponse.get("preferredColor").isJsonNull()
                                    ? jsonResponse.get("preferredColor").getAsString() : "";
                                String npub = jsonResponse.has("npub") && !jsonResponse.get("npub").isJsonNull()
                                    ? jsonResponse.get("npub").getAsString() : "";
                                boolean hasProfilePicture = jsonResponse.has("hasProfilePicture")
                                    && jsonResponse.get("hasProfilePicture").getAsBoolean();

                                // Update device with profile data
                                if (nickname != null && !nickname.isEmpty()) {
                                    device.setProfileNickname(nickname);
                                }
                                if (description != null && !description.isEmpty()) {
                                    device.setProfileDescription(description);
                                }
                                if (preferredColor != null && !preferredColor.isEmpty()) {
                                    device.setProfilePreferredColor(preferredColor);
                                }
                                if (npub != null && !npub.isEmpty()) {
                                    device.setProfileNpub(npub);
                                }

                                // Fetch profile picture if available
                                if (hasProfilePicture) {
                                    fetchProfilePicture(device, deviceIp);
                                } else {
                                    // Update UI on main thread
                                    updateUIAfterProfileFetch(device);
                                }
                            }
                        }
                    } catch (Exception e) {
                        android.util.Log.e("DevicesFragment", "Error fetching profile for " + device.ID + ": " + e.getMessage());
                    }
                }).start();
            }

            private void fetchProfilePicture(Device device, String deviceIp) {
                try {
                    String pictureUrl = "http://" + deviceIp + ":45678/api/profile/picture";
                    java.net.URL url = new java.net.URL(pictureUrl);
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(3000);
                    conn.setReadTimeout(3000);

                    int responseCode = conn.getResponseCode();
                    if (responseCode == java.net.HttpURLConnection.HTTP_OK) {
                        java.io.InputStream inputStream = conn.getInputStream();
                        android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(inputStream);
                        inputStream.close();

                        if (bitmap != null) {
                            device.setProfilePicture(bitmap);
                        }
                    }

                    // Update UI on main thread
                    updateUIAfterProfileFetch(device);
                } catch (Exception e) {
                    android.util.Log.e("DevicesFragment", "Error fetching profile picture for " + device.ID + ": " + e.getMessage());
                    // Still update UI to show nickname even if picture failed
                    updateUIAfterProfileFetch(device);
                }
            }

            private void updateUIAfterProfileFetch(Device device) {
                // Save profile to cache
                offgrid.geogram.util.RemoteProfileCache.saveProfile(
                    itemView.getContext(),
                    device.ID,
                    device.getProfileNickname(),
                    device.getProfileDescription(),
                    device.getProfilePicture(),
                    device.getProfilePreferredColor(),
                    device.getProfileNpub()
                );

                // Update UI on main thread
                if (itemView.getContext() instanceof android.app.Activity) {
                    ((android.app.Activity) itemView.getContext()).runOnUiThread(() -> {
                        // Rebind to update display
                        bind(device);
                    });
                }
            }

            /**
             * Create a circular bitmap from a square bitmap
             */
            private android.graphics.Bitmap getCircularBitmap(android.graphics.Bitmap bitmap) {
                int size = Math.min(bitmap.getWidth(), bitmap.getHeight());

                android.graphics.Bitmap output = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888);
                android.graphics.Canvas canvas = new android.graphics.Canvas(output);

                final android.graphics.Paint paint = new android.graphics.Paint();
                final android.graphics.Rect rect = new android.graphics.Rect(0, 0, size, size);

                paint.setAntiAlias(true);
                canvas.drawARGB(0, 0, 0, 0);
                canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);

                paint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN));
                canvas.drawBitmap(bitmap, null, rect, paint);

                return output;
            }
        }
    }

    /**
     * Create a special device entry for the device relay server
     */
    private Device createRelayDevice() {
        try {
            // Create relay device
            Device relayDevice = new Device("RELAY_SERVER", DeviceType.INTERNET_IGATE);
            relayDevice.callsign = "RELAY";

            // Check relay connection status
            offgrid.geogram.p2p.DeviceRelayClient relayClient = offgrid.geogram.p2p.DeviceRelayClient.getInstance(requireContext());
            boolean isConnected = relayClient.isConnected();

            if (isConnected) {
                // Add current connection event
                offgrid.geogram.devices.EventConnected event = new offgrid.geogram.devices.EventConnected(
                    offgrid.geogram.devices.ConnectionType.INTERNET, null // No location for relay
                );
                relayDevice.connectedEvents.add(event);
            }

            // TODO: Add historical connection events when available

            return relayDevice;
        } catch (Exception e) {
            android.util.Log.e("DevicesFragment", "Error creating relay device", e);
            return null;
        }
    }

    /**
     * Extract server host from WebSocket URL, omitting protocol and port
     */
    private static String extractServerHost(String wsUrl) {
        if (wsUrl == null || wsUrl.isEmpty()) {
            return "unknown";
        }
        try {
            // Remove protocol prefix
            String withoutProtocol = wsUrl.replaceFirst("^(ws://|wss://)", "");
            // Remove port if present
            int colonIndex = withoutProtocol.lastIndexOf(':');
            if (colonIndex > 0) {
                return withoutProtocol.substring(0, colonIndex);
            }
            return withoutProtocol;
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * Check if a badge with the given text already exists in the channel indicators
     */
    private static boolean badgeExists(android.widget.LinearLayout channelIndicators, String badgeText) {
        for (int i = 0; i < channelIndicators.getChildCount(); i++) {
            android.view.View child = channelIndicators.getChildAt(i);
            if (child instanceof TextView && badgeText.equals(((TextView) child).getText().toString())) {
                return true;
            }
        }
        return false;
    }

    private static void removeBadge(android.widget.LinearLayout channelIndicators, String badgeText) {
        for (int i = channelIndicators.getChildCount() - 1; i >= 0; i--) {
            android.view.View child = channelIndicators.getChildAt(i);
            if (child instanceof TextView && badgeText.equals(((TextView) child).getText().toString())) {
                channelIndicators.removeViewAt(i);
            }
        }
    }
}

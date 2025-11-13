package offgrid.geogram.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;

import offgrid.geogram.MainActivity;
import offgrid.geogram.R;
import offgrid.geogram.apps.chat.ChatFragmentDevice;
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

        // Initialize messaging buttons
        Button btnSendMessage = view.findViewById(R.id.btn_send_message);
        Button btnOpenChat = view.findViewById(R.id.btn_open_chat);

        // Both buttons open the chat interface
        View.OnClickListener openChatListener = v -> {
            if (getActivity() != null) {
                ChatFragmentDevice chatFragment = ChatFragmentDevice.newInstance(deviceId);
                getActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, chatFragment)
                        .addToBackStack(null)
                        .commit();
            }
        };

        btnSendMessage.setOnClickListener(openChatListener);
        btnOpenChat.setOnClickListener(openChatListener);

        // Setup Collections card
        setupCollectionsCard(view, deviceId, device);

        return view;
    }

    private void setupCollectionsCard(View view, String deviceId, Device device) {
        LinearLayout collectionsCard = view.findViewById(R.id.collections_card);
        TextView collectionsCount = view.findViewById(R.id.tv_collections_count);
        TextView collectionsInfo = view.findViewById(R.id.tv_collections_info);

        // Check if device has WiFi connection
        offgrid.geogram.wifi.WiFiDiscoveryService wifiService =
                offgrid.geogram.wifi.WiFiDiscoveryService.getInstance(getContext());
        String deviceIp = wifiService.getDeviceIp(deviceId);

        if (deviceIp != null && !deviceIp.isEmpty()) {
            // Device has WiFi - show collections card and fetch count
            collectionsCard.setVisibility(View.VISIBLE);
            collectionsCount.setText("Loading...");

            // Fetch collections count in background thread
            new Thread(() -> {
                try {
                    String apiUrl = "http://" + deviceIp + ":45678/api/collections/count";
                    URL url = new URL(apiUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(5000); // 5 second timeout
                    conn.setReadTimeout(5000);

                    int responseCode = conn.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                        reader.close();

                        // Parse JSON response
                        JsonObject jsonResponse = JsonParser.parseString(response.toString()).getAsJsonObject();
                        int count = jsonResponse.get("count").getAsInt();

                        // Update UI on main thread
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                if (count > 0) {
                                    collectionsCount.setText(count + " collection" + (count != 1 ? "s" : ""));
                                    collectionsInfo.setText("Tap to browse collections");

                                    // Set click listener to open collections browser
                                    collectionsCard.setOnClickListener(v -> {
                                        if (getActivity() != null) {
                                            RemoteCollectionsFragment fragment =
                                                    RemoteCollectionsFragment.newInstance(deviceId, deviceIp);
                                            getActivity().getSupportFragmentManager()
                                                    .beginTransaction()
                                                    .replace(R.id.fragment_container, fragment)
                                                    .addToBackStack(null)
                                                    .commit();
                                        }
                                    });
                                } else {
                                    collectionsCount.setText("No public collections");
                                    collectionsInfo.setText("This device has no public collections");
                                    collectionsCard.setClickable(false);
                                }
                            });
                        }
                    } else {
                        // API call failed
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                collectionsCount.setText("Unable to fetch collections");
                                collectionsInfo.setText("Device may not support collections");
                                collectionsCard.setClickable(false);
                            });
                        }
                    }

                    conn.disconnect();
                } catch (Exception e) {
                    android.util.Log.e("DeviceProfile", "Error fetching collections count: " + e.getMessage());
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            collectionsCount.setText("Connection error");
                            collectionsInfo.setText("Could not connect to device");
                            collectionsCard.setClickable(false);
                        });
                    }
                }
            }).start();
        } else {
            // No WiFi connection - hide collections card
            collectionsCard.setVisibility(View.GONE);
        }
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

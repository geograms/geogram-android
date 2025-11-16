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
import offgrid.geogram.network.ConnectionManager;

public class DeviceProfileFragment extends Fragment {

    private static final String ARG_DEVICE_ID = "device_id";

    private View rootView;
    private String deviceId;
    private android.content.BroadcastReceiver wifiDiscoveryReceiver;

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
        this.rootView = view;

        // Get device ID from arguments
        deviceId = getArguments() != null ? getArguments().getString(ARG_DEVICE_ID) : null;
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

        // Load profile from cache if available
        if (device != null && offgrid.geogram.util.RemoteProfileCache.isCacheValid(getContext(), deviceId)) {
            String cachedNickname = offgrid.geogram.util.RemoteProfileCache.getNickname(getContext(), deviceId);
            String cachedDescription = offgrid.geogram.util.RemoteProfileCache.getDescription(getContext(), deviceId);
            String cachedNpub = offgrid.geogram.util.RemoteProfileCache.getNpub(getContext(), deviceId);
            android.graphics.Bitmap cachedPicture = offgrid.geogram.util.RemoteProfileCache.getProfilePicture(getContext(), deviceId);

            if (cachedNickname != null) {
                device.setProfileNickname(cachedNickname);
            }
            if (cachedDescription != null) {
                device.setProfileDescription(cachedDescription);
            }
            if (cachedNpub != null) {
                device.setProfileNpub(cachedNpub);
            }
            if (cachedPicture != null) {
                device.setProfilePicture(cachedPicture);
            }
        }

        // Get view references for profile section
        android.widget.ImageView profileImage = view.findViewById(R.id.profile_image);
        TextView nicknameView = view.findViewById(R.id.tv_nickname);
        TextView callsignView = view.findViewById(R.id.tv_callsign);
        TextView descriptionView = view.findViewById(R.id.tv_description);
        LinearLayout npubContainer = view.findViewById(R.id.npub_container);
        TextView npubView = view.findViewById(R.id.tv_npub);
        ImageButton btnCopyNpub = view.findViewById(R.id.btn_copy_npub);
        TextView deviceTypeView = view.findViewById(R.id.tv_device_type);
        TextView firstSeenView = view.findViewById(R.id.tv_first_seen);
        TextView lastSeenView = view.findViewById(R.id.tv_last_seen);
        TextView connectionCountView = view.findViewById(R.id.tv_connection_count);
        TextView connectionMethodView = view.findViewById(R.id.tv_connection_method);

        if (device != null) {
            // Display profile information
            String nickname = device.getProfileNickname();
            String description = device.getProfileDescription();
            String npub = device.getProfileNpub();
            android.graphics.Bitmap profilePicture = device.getProfilePicture();

            // Show nickname if available, otherwise hide both (callsign already in title)
            if (nickname != null && !nickname.isEmpty()) {
                nicknameView.setText(nickname);
                nicknameView.setVisibility(View.VISIBLE);
                // Show callsign in parenthesis when nickname is available
                callsignView.setText("(" + deviceId + ")");
                callsignView.setVisibility(View.VISIBLE);
            } else {
                // Hide both nickname and callsign when no nickname (callsign shown in title bar)
                nicknameView.setVisibility(View.GONE);
                callsignView.setVisibility(View.GONE);
            }

            // Show description in the device type field (instead of device type)
            if (description != null && !description.isEmpty()) {
                deviceTypeView.setText(description);
                deviceTypeView.setVisibility(View.VISIBLE);
            } else {
                // Keep field empty if no description
                deviceTypeView.setText("");
                deviceTypeView.setVisibility(View.GONE);
            }

            // Hide the old description field (we're using device type field instead)
            descriptionView.setVisibility(View.GONE);

            // Show npub if available
            if (npub != null && !npub.isEmpty()) {
                npubView.setText(npub);
                npubContainer.setVisibility(View.VISIBLE);

                // Setup copy button
                btnCopyNpub.setOnClickListener(v -> {
                    android.content.ClipboardManager clipboard =
                        (android.content.ClipboardManager) getContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                    android.content.ClipData clip = android.content.ClipData.newPlainText("npub", npub);
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(getContext(), "Npub copied to clipboard", Toast.LENGTH_SHORT).show();
                });
            } else {
                npubContainer.setVisibility(View.GONE);
            }

            // Show profile picture if available (larger size)
            if (profilePicture != null) {
                profileImage.setVisibility(View.VISIBLE);
                // Scale bitmap to fit ImageView (96dp = ~288px on high-density screens)
                int targetSize = (int) (96 * getResources().getDisplayMetrics().density);
                android.graphics.Bitmap scaledBitmap = scaleBitmapToFit(profilePicture, targetSize, targetSize);
                profileImage.setImageBitmap(scaledBitmap);
                profileImage.setPadding(0, 0, 0, 0);
                profileImage.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                // Remove tint to show the actual image colors
                profileImage.setImageTintList(null);

                // Make profile image clickable to show full screen
                final android.graphics.Bitmap finalProfilePicture = profilePicture;
                profileImage.setClickable(true);
                profileImage.setOnClickListener(v -> showFullScreenImage(finalProfilePicture));
            } else {
                // Hide profile image when no picture is available
                profileImage.setVisibility(View.GONE);
            }

            // First seen
            if (!device.connectedEvents.isEmpty()) {
                long firstSeen = Collections.min(device.connectedEvents).latestTimestamp();
                String firstSeenText = formatTimestampShort(firstSeen);
                firstSeenView.setText(firstSeenText);
            } else {
                firstSeenView.setText("Unknown");
            }

            // Last seen
            long lastSeen = device.latestTimestamp();
            if (lastSeen != Long.MIN_VALUE) {
                String lastSeenText = formatTimestampShort(lastSeen);
                lastSeenView.setText(lastSeenText);
            } else {
                lastSeenView.setText("Unknown");
            }

            // Connection count
            int totalConnections = 0;
            for (offgrid.geogram.devices.EventConnected event : device.connectedEvents) {
                totalConnections += event.timestamps.size();
            }
            connectionCountView.setText(String.valueOf(totalConnections));

            // Connection method
            ConnectionManager connectionManager = ConnectionManager.getInstance(getContext());
            ConnectionManager.ConnectionMethod method = connectionManager.selectConnectionMethod(device);
            String methodDescription = ConnectionManager.getConnectionDescription(method);
            String speedDescription = ConnectionManager.getConnectionSpeed(method);
            connectionMethodView.setText(methodDescription + " (" + speedDescription + ")");
        } else {
            // Device not found in DeviceManager (only seen via messages)
            nicknameView.setVisibility(View.GONE);
            callsignView.setText(deviceId);
            deviceTypeView.setText("Message contact");
            deviceTypeView.setVisibility(View.VISIBLE);
            firstSeenView.setText("Not detected");
            lastSeenView.setText("Unknown");
            connectionCountView.setText("0");
            connectionMethodView.setText("Offline (N/A)");
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

    /**
     * Refresh profile data in background (called on resume and when nearby panel opens)
     * This ONLY refreshes profile, NOT collections
     */
    private void refreshProfileDataInBackground() {
        if (deviceId == null || getContext() == null) {
            return;
        }

        // Get device from DeviceManager
        Device device = null;
        for (Device d : DeviceManager.getInstance().getDevicesSpotted()) {
            if (d.ID.equals(deviceId)) {
                device = d;
                break;
            }
        }

        if (device == null) {
            return;
        }

        // Check if device has WiFi available
        offgrid.geogram.wifi.WiFiDiscoveryService wifiService =
            offgrid.geogram.wifi.WiFiDiscoveryService.getInstance(getContext());
        String deviceIp = wifiService.getDeviceIp(deviceId);

        if (deviceIp == null) {
            return; // No WiFi available
        }

        final Device finalDevice = device;
        final String finalDeviceIp = deviceIp;

        // Fetch fresh profile in background thread
        new Thread(() -> {
            try {
                // Fetch profile metadata
                String apiUrl = "http://" + finalDeviceIp + ":45678/api/profile";
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

                        // Update device with profile data
                        if (nickname != null && !nickname.isEmpty()) {
                            finalDevice.setProfileNickname(nickname);
                        }
                        if (description != null && !description.isEmpty()) {
                            finalDevice.setProfileDescription(description);
                        }
                        if (preferredColor != null && !preferredColor.isEmpty()) {
                            finalDevice.setProfilePreferredColor(preferredColor);
                        }
                        if (npub != null && !npub.isEmpty()) {
                            finalDevice.setProfileNpub(npub);
                        }

                        // Save to cache
                        offgrid.geogram.util.RemoteProfileCache.saveProfile(
                            getContext(),
                            finalDevice.ID,
                            finalDevice.getProfileNickname(),
                            finalDevice.getProfileDescription(),
                            finalDevice.getProfilePicture(),
                            finalDevice.getProfilePreferredColor(),
                            finalDevice.getProfileNpub()
                        );

                        android.util.Log.d("DeviceProfile", "Refreshed profile for " + deviceId);

                        // Update UI on main thread
                        if (getActivity() != null && rootView != null) {
                            getActivity().runOnUiThread(() -> updateProfileUI(finalDevice));
                        }
                    }
                }
            } catch (Exception e) {
                android.util.Log.e("DeviceProfile", "Error refreshing profile for " + deviceId + ": " + e.getMessage(), e);
            }
        }).start();
    }

    /**
     * Update profile UI with fresh data
     */
    private void updateProfileUI(Device device) {
        if (rootView == null || device == null) {
            return;
        }

        TextView nicknameView = rootView.findViewById(R.id.tv_nickname);
        TextView callsignView = rootView.findViewById(R.id.tv_callsign);
        TextView deviceTypeView = rootView.findViewById(R.id.tv_device_type);
        LinearLayout npubContainer = rootView.findViewById(R.id.npub_container);
        TextView npubView = rootView.findViewById(R.id.tv_npub);
        ImageButton btnCopyNpub = rootView.findViewById(R.id.btn_copy_npub);

        String nickname = device.getProfileNickname();
        String description = device.getProfileDescription();
        String npub = device.getProfileNpub();

        // Update nickname and callsign
        if (nickname != null && !nickname.isEmpty()) {
            nicknameView.setText(nickname);
            nicknameView.setVisibility(View.VISIBLE);
            callsignView.setText("(" + deviceId + ")");
            callsignView.setVisibility(View.VISIBLE);
        } else {
            // Hide both nickname and callsign when no nickname (callsign shown in title bar)
            nicknameView.setVisibility(View.GONE);
            callsignView.setVisibility(View.GONE);
        }

        // Update description
        if (description != null && !description.isEmpty()) {
            deviceTypeView.setText(description);
            deviceTypeView.setVisibility(View.VISIBLE);
        } else {
            deviceTypeView.setText("");
            deviceTypeView.setVisibility(View.GONE);
        }

        // Update npub
        if (npub != null && !npub.isEmpty()) {
            npubView.setText(npub);
            npubContainer.setVisibility(View.VISIBLE);

            btnCopyNpub.setOnClickListener(v -> {
                android.content.ClipboardManager clipboard =
                    (android.content.ClipboardManager) getContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("npub", npub);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(getContext(), "Npub copied to clipboard", Toast.LENGTH_SHORT).show();
            });
        } else {
            npubContainer.setVisibility(View.GONE);
        }
    }

    private void setupCollectionsCard(View view, String deviceId, Device device) {
        LinearLayout collectionsCard = view.findViewById(R.id.collections_card);
        TextView collectionsCount = view.findViewById(R.id.tv_collections_count);
        TextView collectionsInfo = view.findViewById(R.id.tv_collections_info);

        // Check if device has WiFi connection
        offgrid.geogram.wifi.WiFiDiscoveryService wifiService =
                offgrid.geogram.wifi.WiFiDiscoveryService.getInstance(getContext());
        String deviceIp = wifiService.getDeviceIp(deviceId);

        android.util.Log.d("DeviceProfile", "setupCollectionsCard for " + deviceId + ", WiFi IP: " + deviceIp);

        if (deviceIp != null && !deviceIp.isEmpty()) {
            // Device has WiFi - show collections card and fetch count
            android.util.Log.d("DeviceProfile", "Showing collections card for " + deviceId);
            collectionsCard.setVisibility(View.VISIBLE);
            collectionsCount.setText("Loading...");

            // Fetch collections list in background thread (we need the full list to filter owned ones)
            new Thread(() -> {
                try {
                    String apiUrl = "http://" + deviceIp + ":45678/api/collections";
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

                        // Parse JSON response and filter out owned collections
                        JsonObject jsonResponse = JsonParser.parseString(response.toString()).getAsJsonObject();
                        com.google.gson.JsonArray collectionsArray = jsonResponse.getAsJsonArray("collections");

                        int filteredCount = 0;
                        for (int i = 0; i < collectionsArray.size(); i++) {
                            JsonObject collectionJson = collectionsArray.get(i).getAsJsonObject();
                            String collectionId = collectionJson.get("id").getAsString();

                            // Skip collections that we own (are admin of)
                            if (!offgrid.geogram.util.CollectionKeysManager.isOwnedCollection(getContext(), collectionId)) {
                                filteredCount++;
                            }
                        }

                        final int count = filteredCount;

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

        // Refresh profile data in background
        refreshProfileDataInBackground();

        // Refresh collections card immediately (don't wait for profile)
        if (rootView != null && deviceId != null) {
            Device device = null;
            for (Device d : DeviceManager.getInstance().getDevicesSpotted()) {
                if (d.ID.equals(deviceId)) {
                    device = d;
                    break;
                }
            }
            if (device != null) {
                setupCollectionsCard(rootView, deviceId, device);
            }
        }

        // Register WiFi discovery broadcast receiver
        if (wifiDiscoveryReceiver == null) {
            wifiDiscoveryReceiver = new android.content.BroadcastReceiver() {
                @Override
                public void onReceive(android.content.Context context, android.content.Intent intent) {
                    // WiFi discovery has found devices - refresh profile and collections
                    if (rootView != null && deviceId != null) {
                        android.util.Log.d("DeviceProfile", "WiFi discovery update received - refreshing profile display");
                        Device device = null;
                        for (Device d : DeviceManager.getInstance().getDevicesSpotted()) {
                            if (d.ID.equals(deviceId)) {
                                device = d;
                                break;
                            }
                        }

                        // Refresh profile display
                        if (device != null) {
                            android.widget.ImageView profileImage = rootView.findViewById(R.id.profile_image);
                            TextView nicknameView = rootView.findViewById(R.id.tv_nickname);
                            TextView callsignView = rootView.findViewById(R.id.tv_callsign);
                            TextView deviceTypeView = rootView.findViewById(R.id.tv_device_type);
                            LinearLayout npubContainer = rootView.findViewById(R.id.npub_container);
                            TextView npubView = rootView.findViewById(R.id.tv_npub);
                            ImageButton btnCopyNpub = rootView.findViewById(R.id.btn_copy_npub);

                            String nickname = device.getProfileNickname();
                            String description = device.getProfileDescription();
                            String npub = device.getProfileNpub();
                            android.graphics.Bitmap profilePicture = device.getProfilePicture();

                            if (nickname != null && !nickname.isEmpty()) {
                                nicknameView.setText(nickname);
                                nicknameView.setVisibility(View.VISIBLE);
                                callsignView.setText("(" + deviceId + ")");
                                callsignView.setVisibility(View.VISIBLE);
                            } else {
                                // Hide both nickname and callsign when no nickname (callsign shown in title bar)
                                nicknameView.setVisibility(View.GONE);
                                callsignView.setVisibility(View.GONE);
                            }

                            // Show description in the device type field (instead of device type)
                            if (description != null && !description.isEmpty()) {
                                deviceTypeView.setText(description);
                                deviceTypeView.setVisibility(View.VISIBLE);
                            } else {
                                deviceTypeView.setText("");
                                deviceTypeView.setVisibility(View.GONE);
                            }

                            if (npub != null && !npub.isEmpty()) {
                                npubView.setText(npub);
                                npubContainer.setVisibility(View.VISIBLE);

                                // Setup copy button
                                btnCopyNpub.setOnClickListener(v -> {
                                    android.content.ClipboardManager clipboard =
                                        (android.content.ClipboardManager) getContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                                    android.content.ClipData clip = android.content.ClipData.newPlainText("npub", npub);
                                    clipboard.setPrimaryClip(clip);
                                    Toast.makeText(getContext(), "Npub copied to clipboard", Toast.LENGTH_SHORT).show();
                                });
                            } else {
                                npubContainer.setVisibility(View.GONE);
                            }

                            if (profilePicture != null) {
                                profileImage.setVisibility(View.VISIBLE);
                                // Scale bitmap to fit ImageView
                                int targetSize = (int) (96 * getResources().getDisplayMetrics().density);
                                android.graphics.Bitmap scaledBitmap = scaleBitmapToFit(profilePicture, targetSize, targetSize);
                                profileImage.setImageBitmap(scaledBitmap);
                                profileImage.setPadding(0, 0, 0, 0);
                                profileImage.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                                // Remove tint to show the actual image colors
                                profileImage.setImageTintList(null);
                            } else {
                                // Hide profile image when no picture is available
                                profileImage.setVisibility(View.GONE);
                            }
                        }

                        setupCollectionsCard(rootView, deviceId, device);
                    }
                }
            };
        }

        // Register receiver
        if (getContext() != null) {
            android.content.IntentFilter filter = new android.content.IntentFilter("offgrid.geogram.WIFI_DISCOVERY_UPDATE");
            androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(getContext())
                    .registerReceiver(wifiDiscoveryReceiver, filter);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // Show top action bar when leaving
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setTopActionBarVisible(true);
        }

        // Unregister WiFi discovery broadcast receiver
        if (wifiDiscoveryReceiver != null && getContext() != null) {
            androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(getContext())
                    .unregisterReceiver(wifiDiscoveryReceiver);
        }
    }

    private String formatTimestamp(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.US);
        return sdf.format(new Date(timestamp));
    }

    private String formatTimestampShort(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, HH:mm", Locale.US);
        return sdf.format(new Date(timestamp));
    }

    /**
     * Scale a bitmap to fit within the target dimensions while maintaining aspect ratio
     */
    private android.graphics.Bitmap scaleBitmapToFit(android.graphics.Bitmap bitmap, int maxWidth, int maxHeight) {
        if (bitmap == null) {
            return null;
        }

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        // Calculate scaling factor
        float scaleWidth = ((float) maxWidth) / width;
        float scaleHeight = ((float) maxHeight) / height;
        float scaleFactor = Math.min(scaleWidth, scaleHeight);

        // Calculate new dimensions
        int newWidth = Math.round(width * scaleFactor);
        int newHeight = Math.round(height * scaleFactor);

        // Scale bitmap
        return android.graphics.Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
    }

    /**
     * Show profile picture in full screen dialog
     */
    private void showFullScreenImage(android.graphics.Bitmap bitmap) {
        if (bitmap == null || getContext() == null) {
            return;
        }

        // Create dialog
        android.app.Dialog dialog = new android.app.Dialog(getContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(R.layout.dialog_fullscreen_image);

        // Find ImageView
        android.widget.ImageView fullscreenImage = dialog.findViewById(R.id.fullscreen_image);
        if (fullscreenImage != null) {
            fullscreenImage.setImageBitmap(bitmap);
            fullscreenImage.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);

            // Close dialog on click
            fullscreenImage.setOnClickListener(v -> dialog.dismiss());
        }

        // Close button
        android.widget.ImageButton btnClose = dialog.findViewById(R.id.btn_close);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.show();
    }
}

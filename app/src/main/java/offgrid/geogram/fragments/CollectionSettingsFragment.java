package offgrid.geogram.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import offgrid.geogram.MainActivity;
import offgrid.geogram.R;
import offgrid.geogram.devices.Device;
import offgrid.geogram.devices.DeviceManager;
import offgrid.geogram.models.Collection;
import offgrid.geogram.util.CollectionKeysManager;
import offgrid.geogram.util.RemoteProfileCache;

public class CollectionSettingsFragment extends Fragment {

    private static final String ARG_COLLECTION = "collection";

    private Collection collection;
    private Spinner spinnerVisibility;
    private LinearLayout groupMembersCard;
    private RecyclerView recyclerGroupMembers;
    private TextView emptyGroupMembers;
    private GroupMemberAdapter groupMemberAdapter;
    private Set<String> selectedNpubs;

    public static CollectionSettingsFragment newInstance(Collection collection) {
        CollectionSettingsFragment fragment = new CollectionSettingsFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_COLLECTION, collection);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_collection_settings, container, false);

        // Get collection from arguments
        if (getArguments() != null) {
            collection = (Collection) getArguments().getSerializable(ARG_COLLECTION);
        }

        if (collection == null) {
            Toast.makeText(getContext(), "Error: Collection not found", Toast.LENGTH_SHORT).show();
            if (getActivity() != null) {
                getActivity().getSupportFragmentManager().popBackStack();
            }
            return view;
        }

        // Initialize views
        ImageButton btnBack = view.findViewById(R.id.btn_back);
        spinnerVisibility = view.findViewById(R.id.spinner_visibility);
        groupMembersCard = view.findViewById(R.id.group_members_card);
        recyclerGroupMembers = view.findViewById(R.id.recycler_group_members);
        emptyGroupMembers = view.findViewById(R.id.empty_group_members);

        // Initialize selected npubs set
        selectedNpubs = new HashSet<>();

        // Setup RecyclerView
        recyclerGroupMembers.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerGroupMembers.setNestedScrollingEnabled(false);

        // Back button
        btnBack.setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().getSupportFragmentManager().popBackStack();
            }
        });

        // Load current visibility setting and group members
        loadCurrentVisibility();

        // Setup spinner listener to show/hide group members card and auto-save
        spinnerVisibility.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 2) { // Group selected
                    groupMembersCard.setVisibility(View.VISIBLE);
                    loadNearbyDevices();
                } else {
                    groupMembersCard.setVisibility(View.GONE);
                }
                // Auto-save when visibility changes
                autoSaveSettings();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                groupMembersCard.setVisibility(View.GONE);
            }
        });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Hide top action bar
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

    /**
     * Load current visibility from security.json
     */
    private void loadCurrentVisibility() {
        try {
            File securityFile = new File(collection.getStoragePath(), "extra/security.json");
            if (securityFile.exists()) {
                // Read security.json content
                String content = readFile(securityFile);

                // Parse JSON
                com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(content).getAsJsonObject();

                // Get visibility
                String visibility = json.has("visibility") ? json.get("visibility").getAsString() : "public";

                // Parse existing whitelisted users (npubs for group visibility)
                if (json.has("permissions")) {
                    com.google.gson.JsonObject permissions = json.getAsJsonObject("permissions");
                    if (permissions.has("whitelisted_users")) {
                        com.google.gson.JsonArray whitelisted = permissions.getAsJsonArray("whitelisted_users");
                        for (int i = 0; i < whitelisted.size(); i++) {
                            selectedNpubs.add(whitelisted.get(i).getAsString());
                        }
                        android.util.Log.d("CollectionSettings", "Loaded " + whitelisted.size() + " whitelisted npubs");
                    }
                }

                // Set spinner selection
                int position = 0; // Default to Public
                if ("private".equalsIgnoreCase(visibility)) {
                    position = 1;
                } else if ("group".equalsIgnoreCase(visibility)) {
                    position = 2;
                }
                spinnerVisibility.setSelection(position);

                // If group visibility, load devices immediately
                if (position == 2) {
                    groupMembersCard.setVisibility(View.VISIBLE);
                    loadNearbyDevices();
                }
            }
        } catch (Exception e) {
            android.util.Log.e("CollectionSettings", "Error loading visibility: " + e.getMessage(), e);
        }
    }


    /**
     * Auto-save settings to security.json whenever they change
     */
    private void autoSaveSettings() {
        if (collection == null) {
            return;
        }

        // Get selected visibility
        int position = spinnerVisibility.getSelectedItemPosition();
        String visibility;
        switch (position) {
            case 1:
                visibility = "private";
                break;
            case 2:
                visibility = "group";
                break;
            default:
                visibility = "public";
                break;
        }

        // Get selected whitelisted users (npubs, if group visibility)
        List<String> whitelistedUsers = null;
        if (position == 2) {
            whitelistedUsers = new ArrayList<>(selectedNpubs);
            android.util.Log.d("CollectionSettings", "Auto-saving " + whitelistedUsers.size() + " whitelisted npubs");
        }

        // Update security.json
        boolean success = updateSecurityJson(visibility, whitelistedUsers);

        if (!success) {
            Toast.makeText(getContext(), "Error saving settings", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Update security.json with new visibility setting and whitelisted users
     */
    private boolean updateSecurityJson(String visibility, List<String> whitelistedUsers) {
        try {
            // Ensure extra directory exists
            File extraDir = new File(collection.getStoragePath(), "extra");
            if (!extraDir.exists()) {
                extraDir.mkdirs();
            }

            File securityFile = new File(extraDir, "security.json");

            com.google.gson.JsonObject json;

            // Read existing security.json or create new one
            if (securityFile.exists()) {
                String content = readFile(securityFile);
                json = com.google.gson.JsonParser.parseString(content).getAsJsonObject();
            } else {
                json = createDefaultSecurityJson();
            }

            // Update visibility
            json.addProperty("visibility", visibility);

            // Update whitelisted_users in permissions
            if (!json.has("permissions")) {
                json.add("permissions", new com.google.gson.JsonObject());
            }
            com.google.gson.JsonObject permissions = json.getAsJsonObject("permissions");

            // Set whitelisted users array
            com.google.gson.JsonArray whitelistedArray = new com.google.gson.JsonArray();
            if (whitelistedUsers != null && !whitelistedUsers.isEmpty()) {
                for (String npub : whitelistedUsers) {
                    whitelistedArray.add(npub);
                }
            }
            permissions.add("whitelisted_users", whitelistedArray);

            // Write formatted JSON
            com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
            String jsonString = gson.toJson(json);

            FileWriter writer = new FileWriter(securityFile);
            writer.write(jsonString);
            writer.close();

            android.util.Log.d("CollectionSettings", "Updated security.json with visibility: " + visibility);
            if (whitelistedUsers != null) {
                android.util.Log.d("CollectionSettings", "Updated with " + whitelistedUsers.size() + " whitelisted npubs");
            }
            return true;

        } catch (Exception e) {
            android.util.Log.e("CollectionSettings", "Error updating security.json: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Create default security.json structure
     */
    private com.google.gson.JsonObject createDefaultSecurityJson() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();

        json.addProperty("version", "1.0");
        json.addProperty("visibility", "public");

        // Permissions
        com.google.gson.JsonObject permissions = new com.google.gson.JsonObject();
        permissions.addProperty("can_users_read", true);
        permissions.addProperty("can_users_submit", true);
        permissions.addProperty("submit_requires_approval", true);
        permissions.addProperty("can_users_comment", true);
        permissions.addProperty("can_users_like", true);
        permissions.addProperty("can_users_dislike", true);
        permissions.addProperty("can_users_rate", true);
        permissions.add("whitelisted_users", new com.google.gson.JsonArray());
        permissions.add("blocked_users", new com.google.gson.JsonArray());
        json.add("permissions", permissions);

        // Admin (get from collection keys manager)
        com.google.gson.JsonObject admin = new com.google.gson.JsonObject();
        try {
            offgrid.geogram.core.Central central = offgrid.geogram.core.Central.getInstance();
            if (central != null && central.getSettings() != null) {
                String npub = central.getSettings().getNpub();
                if (npub != null) {
                    admin.addProperty("npub", npub);
                }
            }
        } catch (Exception e) {
            android.util.Log.e("CollectionSettings", "Error getting admin npub", e);
        }
        json.add("admin", admin);

        // Other fields
        json.add("subscribers", new com.google.gson.JsonArray());
        json.add("permitted_contributors", new com.google.gson.JsonArray());
        json.add("content_warnings", new com.google.gson.JsonArray());

        com.google.gson.JsonObject ageRestriction = new com.google.gson.JsonObject();
        ageRestriction.addProperty("enabled", false);
        ageRestriction.addProperty("minimum_age", 0);
        ageRestriction.addProperty("verification_required", false);
        json.add("age_restriction", ageRestriction);

        com.google.gson.JsonObject encryption = new com.google.gson.JsonObject();
        encryption.addProperty("enabled", false);
        encryption.add("method", com.google.gson.JsonNull.INSTANCE);
        encryption.add("encrypted_files", new com.google.gson.JsonArray());
        json.add("encryption", encryption);

        return json;
    }

    /**
     * Read file content
     */
    private String readFile(File file) throws java.io.IOException {
        StringBuilder content = new StringBuilder();
        java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(file));
        String line;
        while ((line = reader.readLine()) != null) {
            content.append(line).append("\n");
        }
        reader.close();
        return content.toString();
    }


    /**
     * Load nearby devices that have been spotted and have npubs
     */
    private void loadNearbyDevices() {
        try {
            // Get all devices spotted
            TreeSet<Device> allDevices = DeviceManager.getInstance().getDevicesSpotted();
            List<Device> deviceList = new ArrayList<>();

            android.util.Log.d("CollectionSettings", "Loading nearby devices, total spotted: " + allDevices.size());

            // Load cached profiles and fetch missing profiles via WiFi
            for (Device device : allDevices) {
                // Always try to load from cache first
                if (RemoteProfileCache.isCacheValid(getContext(), device.ID)) {
                    String cachedNickname = RemoteProfileCache.getNickname(getContext(), device.ID);
                    String cachedNpub = RemoteProfileCache.getNpub(getContext(), device.ID);
                    android.graphics.Bitmap cachedPicture = RemoteProfileCache.getProfilePicture(getContext(), device.ID);

                    // Always set from cache to ensure we have the latest data
                    if (cachedNickname != null) {
                        device.setProfileNickname(cachedNickname);
                    }
                    if (cachedNpub != null) {
                        device.setProfileNpub(cachedNpub);
                    }
                    if (cachedPicture != null) {
                        device.setProfilePicture(cachedPicture);
                    }
                }

                // If no npub in cache, try to fetch via WiFi
                if (device.getProfileNpub() == null || device.getProfileNpub().isEmpty()) {
                    fetchProfileFromWiFi(device);
                }

                // Only include devices that have npubs (required for group permissions)
                String npub = device.getProfileNpub();
                if (npub != null && !npub.isEmpty()) {
                    deviceList.add(device);
                }
            }

            android.util.Log.d("CollectionSettings", "Found " + deviceList.size() + " devices with npubs out of " + allDevices.size() + " total");

            // Show/hide empty state
            if (deviceList.isEmpty()) {
                recyclerGroupMembers.setVisibility(View.GONE);
                emptyGroupMembers.setVisibility(View.VISIBLE);
            } else {
                recyclerGroupMembers.setVisibility(View.VISIBLE);
                emptyGroupMembers.setVisibility(View.GONE);

                // Create and set adapter
                groupMemberAdapter = new GroupMemberAdapter(deviceList, selectedNpubs, this);
                recyclerGroupMembers.setAdapter(groupMemberAdapter);
            }
        } catch (Exception e) {
            android.util.Log.e("CollectionSettings", "Error loading nearby devices: " + e.getMessage(), e);
            recyclerGroupMembers.setVisibility(View.GONE);
            emptyGroupMembers.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Fetch profile from WiFi for a device that doesn't have cached profile
     */
    private void fetchProfileFromWiFi(Device device) {
        // Skip if already fetched recently
        if (device.isProfileFetched()) {
            return;
        }

        // Check if device has WiFi available
        offgrid.geogram.wifi.WiFiDiscoveryService wifiService =
            offgrid.geogram.wifi.WiFiDiscoveryService.getInstance(getContext());
        String deviceIp = wifiService.getDeviceIp(device.ID);

        if (deviceIp == null) {
            return; // No WiFi available, only BLE
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

                        // Save to cache
                        RemoteProfileCache.saveProfile(
                            getContext(),
                            device.ID,
                            device.getProfileNickname(),
                            device.getProfileDescription(),
                            device.getProfilePicture(),
                            device.getProfilePreferredColor(),
                            device.getProfileNpub()
                        );

                        android.util.Log.d("CollectionSettings", "Fetched profile via WiFi for " + device.ID + " with npub: " + (npub != null && !npub.isEmpty()));

                        // Reload the list on main thread to include this device
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> loadNearbyDevices());
                        }
                    }
                }
            } catch (Exception e) {
                android.util.Log.e("CollectionSettings", "Error fetching profile for " + device.ID + ": " + e.getMessage());
            }
        }).start();
    }

    /**
     * Adapter for group member selection (by npub)
     */
    private class GroupMemberAdapter extends RecyclerView.Adapter<GroupMemberAdapter.ViewHolder> {
        private final List<Device> devices;
        private final Set<String> selectedNpubs;
        private final CollectionSettingsFragment fragment;

        GroupMemberAdapter(List<Device> devices, Set<String> selectedNpubs, CollectionSettingsFragment fragment) {
            this.devices = devices;
            this.selectedNpubs = selectedNpubs;
            this.fragment = fragment;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_multiple_choice, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Device device = devices.get(position);
            String npub = device.getProfileNpub();
            String deviceId = device.ID;
            String nickname = device.getProfileNickname();

            // Display user-friendly name: "Nickname (X1ABCD)" or just "X1ABCD"
            // The npub is used internally for security.json but not shown to user
            String displayName;
            if (nickname != null && !nickname.isEmpty()) {
                displayName = nickname + " (" + deviceId + ")";
            } else {
                displayName = deviceId;
            }

            holder.textView.setText(displayName);
            holder.textView.setChecked(selectedNpubs.contains(npub));

            holder.textView.setOnClickListener(v -> {
                if (selectedNpubs.contains(npub)) {
                    selectedNpubs.remove(npub);
                    holder.textView.setChecked(false);
                } else {
                    selectedNpubs.add(npub);
                    holder.textView.setChecked(true);
                }
                // Auto-save when selection changes
                fragment.autoSaveSettings();
            });
        }

        @Override
        public int getItemCount() {
            return devices.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            android.widget.CheckedTextView textView;

            ViewHolder(View itemView) {
                super(itemView);
                textView = (android.widget.CheckedTextView) itemView;
            }
        }
    }
}

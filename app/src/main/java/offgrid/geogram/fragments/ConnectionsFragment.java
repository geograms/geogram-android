package offgrid.geogram.fragments;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;

import offgrid.geogram.MainActivity;
import offgrid.geogram.R;
import offgrid.geogram.core.Central;
import offgrid.geogram.settings.ConfigManager;
import offgrid.geogram.settings.AppConfig;
import offgrid.geogram.util.NetworkUtils;

public class ConnectionsFragment extends Fragment {

    private View view = null;
    private offgrid.geogram.settings.SettingsUser settings = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_connections, container, false);

        // Back button functionality
        ImageButton btnBack = view.findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> requireActivity().onBackPressed());

        // Load settings
        loadSettings();

        // Initialize UI components and bind settings
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
    }

    @Override
    public void onPause() {
        super.onPause();
        // Show top action bar when leaving
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setTopActionBarVisible(true);
        }
    }

    private void loadSettings() {
        settings = Central.getInstance().getSettings();
        if (settings == null) {
            try {
                settings = offgrid.geogram.settings.SettingsLoader.loadSettings(requireContext());
            } catch (Exception e) {
                settings = new offgrid.geogram.settings.SettingsUser(); // Default settings if loading fails
                saveSettings(settings);
                Toast.makeText(getContext(),
                        "Failed to load settings. Using defaults.",
                        Toast.LENGTH_LONG).show();
            }
            // Ensure settings are saved to Central
            Central.getInstance().setSettings(settings);
        }
    }

    private void initializeUI(View view) {
        // HTTP API Settings
        SwitchCompat httpApiSwitch = view.findViewById(R.id.switch_http_api);
        TextView httpApiUrlText = view.findViewById(R.id.text_http_api_url);
        ImageButton btnCopyHttpUrl = view.findViewById(R.id.btn_copy_http_url);
        ImageButton btnShareHttpUrl = view.findViewById(R.id.btn_share_http_url);

        // Set initial state
        httpApiSwitch.setChecked(settings.isHttpApiEnabled());
        updateHttpApiUrl(httpApiUrlText, settings.isHttpApiEnabled());

        // Handle switch changes
        httpApiSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settings.setHttpApiEnabled(isChecked);
            saveSettings(settings);
            updateHttpApiUrl(httpApiUrlText, isChecked);

            // Inform user about restart requirement
            if (isChecked) {
                Toast.makeText(getContext(), "HTTP API enabled. Restart app to apply.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), "HTTP API disabled. Restart app to apply.", Toast.LENGTH_SHORT).show();
            }
        });

        // Copy HTTP URL button
        btnCopyHttpUrl.setOnClickListener(v -> {
            String url = NetworkUtils.getServerUrl(45678);
            if (url.startsWith("http://")) {
                ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("HTTP API URL", url);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(getContext(), "URL copied to clipboard", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), "Server URL not available", Toast.LENGTH_SHORT).show();
            }
        });

        // Share HTTP URL button
        btnShareHttpUrl.setOnClickListener(v -> {
            String url = NetworkUtils.getServerUrl(45678);
            if (url.startsWith("http://")) {
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Geogram HTTP API");
                shareIntent.putExtra(Intent.EXTRA_TEXT, "Geogram HTTP API Server:\n" + url + "\n\nEndpoints:\n" +
                        "- POST /api/ble/send - Send BLE message\n" +
                        "- GET /api/logs - Get recent logs\n" +
                        "- GET /api/status - Get server status");
                startActivity(Intent.createChooser(shareIntent, "Share HTTP API URL"));
            } else {
                Toast.makeText(getContext(), "Server URL not available", Toast.LENGTH_SHORT).show();
            }
        });

        // Device Relay Settings
        androidx.appcompat.widget.SwitchCompat switchRelayEnabled = view.findViewById(R.id.switch_device_relay_enabled);
        EditText editRelayServerUrl = view.findViewById(R.id.edit_relay_server_url);

        ConfigManager configManager = ConfigManager.getInstance(requireContext());
        AppConfig config = configManager.getConfig();

        switchRelayEnabled.setChecked(config.isDeviceRelayEnabled());
        editRelayServerUrl.setText(config.getDeviceRelayServerUrl());

        switchRelayEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            config.setDeviceRelayEnabled(isChecked);
            configManager.saveConfig();
            Toast.makeText(getContext(), "Device relay " + (isChecked ? "enabled" : "disabled"), Toast.LENGTH_SHORT).show();
        });

        editRelayServerUrl.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                String url = editRelayServerUrl.getText().toString().trim();
                if (!url.isEmpty()) {
                    try {
                        config.setDeviceRelayServerUrl(url);
                        configManager.saveConfig();
                        Toast.makeText(getContext(), "Relay server URL updated", Toast.LENGTH_SHORT).show();
                    } catch (IllegalArgumentException e) {
                        Toast.makeText(getContext(), "Invalid URL: Must start with ws:// or wss://", Toast.LENGTH_LONG).show();
                        editRelayServerUrl.setText(config.getDeviceRelayServerUrl());
                    }
                }
            }
        });
    }

    private void saveSettings(offgrid.geogram.settings.SettingsUser settings) {
        try {
            offgrid.geogram.settings.SettingsLoader.saveSettings(requireContext(), settings);
            // No success toast - settings save silently
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void updateHttpApiUrl(TextView textView, boolean enabled) {
        if (!enabled) {
            textView.setText("Disabled");
            textView.setTextColor(getResources().getColor(R.color.gray, null));
        } else {
            String url = NetworkUtils.getServerUrl(45678);
            textView.setText(url);
            textView.setTextColor(getResources().getColor(R.color.white, null));
        }
    }
}
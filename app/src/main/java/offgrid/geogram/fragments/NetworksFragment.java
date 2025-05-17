package offgrid.geogram.fragments;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import offgrid.geogram.R;

public class NetworksFragment extends Fragment {

    private ImageButton btnBack;
    private Switch bluetoothSwitch;
    private EditText deviceIdField, macAddressField;
    private Switch wifiSwitch;
    private EditText ssidField, wifiPasswordField, hostIpField;
    private Switch humanMessengerSwitch;
    private EditText storageGbField;
    private Spinner expirySpinner;

    public NetworksFragment() {
        // Required empty public constructor
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_networks, container, false);

        // Back button functionality
        btnBack = view.findViewById(R.id.btn_back);


        // Initialize Bluetooth Section
        bluetoothSwitch = view.findViewById(R.id.switch_bluetooth);
        deviceIdField = view.findViewById(R.id.edit_device_id);
        macAddressField = view.findViewById(R.id.edit_mac_address);

        // Initialize Wi-Fi Section
        wifiSwitch = view.findViewById(R.id.switch_wifi);
        ssidField = view.findViewById(R.id.edit_ssid);
        wifiPasswordField = view.findViewById(R.id.edit_wifi_password);
        hostIpField = view.findViewById(R.id.edit_host_ip);

        // Initialize Human Messenger Section
        humanMessengerSwitch = view.findViewById(R.id.switch_human_messenger);
        storageGbField = view.findViewById(R.id.edit_storage_gb);
        expirySpinner = view.findViewById(R.id.spinner_expiry);

        // Add listeners
        setupListeners();

        return view;
    }

    private void setupListeners() {
        // Back button functionality
        btnBack.setOnClickListener(v -> requireActivity().onBackPressed());

        // Bluetooth Switch Listener
        bluetoothSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            String message = isChecked ? "Bluetooth Enabled" : "Bluetooth Disabled";
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        });

        // Wi-Fi Switch Listener
        wifiSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            String message = isChecked ? "Local Wi-Fi Enabled" : "Local Wi-Fi Disabled";
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        });

        // Human Messenger Switch Listener
        humanMessengerSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            String message = isChecked ? "Human Messenger Enabled" : "Human Messenger Disabled";
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        });
    }

    public void saveSettings() {
        // Bluetooth Section
        String deviceId = deviceIdField.getText().toString();
        String macAddress = macAddressField.getText().toString();

        if (bluetoothSwitch.isChecked() && (TextUtils.isEmpty(deviceId) || TextUtils.isEmpty(macAddress))) {
            Toast.makeText(requireContext(), "Please fill out Device ID and MAC Address for Bluetooth", Toast.LENGTH_SHORT).show();
            return;
        }

        // Wi-Fi Section
        String ssid = ssidField.getText().toString();
        String password = wifiPasswordField.getText().toString();
        String hostIp = hostIpField.getText().toString();

        if (wifiSwitch.isChecked() && (TextUtils.isEmpty(ssid) || TextUtils.isEmpty(password) || TextUtils.isEmpty(hostIp))) {
            Toast.makeText(requireContext(), "Please fill out all fields for Local Wi-Fi", Toast.LENGTH_SHORT).show();
            return;
        }

        // Human Messenger Section
        String storageGb = storageGbField.getText().toString();
        String expiry = expirySpinner.getSelectedItem().toString();

        if (humanMessengerSwitch.isChecked() && TextUtils.isEmpty(storageGb)) {
            Toast.makeText(requireContext(), "Please specify storage for Human Messenger", Toast.LENGTH_SHORT).show();
            return;
        }

        // Save logic here (e.g., saving to preferences or a database)
        Toast.makeText(requireContext(), "Settings saved successfully", Toast.LENGTH_SHORT).show();
    }
}

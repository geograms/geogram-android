package offgrid.geogram.fragments;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;

import offgrid.geogram.R;
import offgrid.geogram.i2p.I2PService;

/**
 * Fragment for I2P settings and configuration
 */
public class I2PSettingsFragment extends Fragment {

    private I2PService i2pService;

    private SwitchCompat switchEnableI2P;
    private TextView tvI2PStatus;
    private TextView tvI2PDestination;
    private TextView tvBatteryThreshold;
    private SeekBar seekBarBatteryThreshold;
    private ImageButton btnCopyDestination;
    private View cardI2PDestination;

    public static I2PSettingsFragment newInstance() {
        return new I2PSettingsFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_i2p_settings, container, false);

        // Initialize I2P service
        i2pService = I2PService.getInstance(getContext());

        // Initialize views
        switchEnableI2P = view.findViewById(R.id.switch_enable_i2p);
        tvI2PStatus = view.findViewById(R.id.tv_i2p_status);
        tvI2PDestination = view.findViewById(R.id.tv_i2p_destination);
        tvBatteryThreshold = view.findViewById(R.id.tv_battery_threshold);
        seekBarBatteryThreshold = view.findViewById(R.id.seekbar_battery_threshold);
        btnCopyDestination = view.findViewById(R.id.btn_copy_destination);
        cardI2PDestination = view.findViewById(R.id.card_i2p_destination);

        // Back button
        ImageButton btnBack = view.findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().getSupportFragmentManager().popBackStack();
            }
        });

        // Load current settings
        loadSettings();

        // Setup listeners
        setupListeners();

        return view;
    }

    private void loadSettings() {
        // Load I2P enabled state
        boolean isEnabled = i2pService.isEnabled();
        switchEnableI2P.setChecked(isEnabled);

        // Load I2P status
        updateI2PStatus();

        // Load I2P destination
        updateI2PDestination();

        // Load battery threshold
        int batteryThreshold = i2pService.getBatteryThreshold();
        seekBarBatteryThreshold.setProgress(batteryThreshold);
        tvBatteryThreshold.setText(batteryThreshold + "%");
    }

    private void updateI2PStatus() {
        boolean isReady = i2pService.isI2PReady();
        boolean isRunning = i2pService.isRunning();

        if (isReady) {
            tvI2PStatus.setText("Ready");
            tvI2PStatus.setTextColor(0xFF4CAF50); // Green
        } else if (isRunning) {
            tvI2PStatus.setText("Starting...");
            tvI2PStatus.setTextColor(0xFFFFC107); // Amber
        } else {
            tvI2PStatus.setText("Not Running");
            tvI2PStatus.setTextColor(0xFF757575); // Grey
        }
    }

    private void updateI2PDestination() {
        String destination = i2pService.getI2PDestination();

        if (destination != null && !destination.isEmpty()) {
            tvI2PDestination.setText(destination);
            tvI2PDestination.setTextColor(0xFFFFFFFF); // White
            cardI2PDestination.setVisibility(View.VISIBLE);
        } else {
            tvI2PDestination.setText("Not generated");
            tvI2PDestination.setTextColor(0xFF888888); // Grey
            cardI2PDestination.setVisibility(View.VISIBLE);
        }
    }

    private void setupListeners() {
        // Enable/disable I2P
        switchEnableI2P.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // Enable I2P
                i2pService.setEnabled(true);
                i2pService.startI2P();
                Toast.makeText(getContext(), "I2P enabled - starting...", Toast.LENGTH_SHORT).show();

                // Update UI after a short delay to show status change
                buttonView.postDelayed(this::updateI2PStatus, 1000);
                buttonView.postDelayed(this::updateI2PDestination, 1000);
            } else {
                // Disable I2P
                i2pService.setEnabled(false);
                i2pService.stopI2P();
                Toast.makeText(getContext(), "I2P disabled", Toast.LENGTH_SHORT).show();
                updateI2PStatus();
            }
        });

        // Battery threshold
        seekBarBatteryThreshold.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // Minimum 5%
                if (progress < 5) {
                    progress = 5;
                    seekBar.setProgress(progress);
                }
                tvBatteryThreshold.setText(progress + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Not needed
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Save threshold when user releases
                int threshold = seekBar.getProgress();
                if (threshold < 5) {
                    threshold = 5;
                }
                i2pService.setBatteryThreshold(threshold);
                Toast.makeText(getContext(), "Battery threshold set to " + threshold + "%", Toast.LENGTH_SHORT).show();
            }
        });

        // Copy destination button
        btnCopyDestination.setOnClickListener(v -> {
            String destination = i2pService.getI2PDestination();
            if (destination != null && !destination.isEmpty()) {
                ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("I2P Destination", destination);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(getContext(), "I2P address copied to clipboard", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), "No I2P address available", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        // Refresh status when fragment becomes visible
        updateI2PStatus();
        updateI2PDestination();
    }
}

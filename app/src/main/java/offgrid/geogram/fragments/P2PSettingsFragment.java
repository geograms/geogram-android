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
import offgrid.geogram.p2p.P2PService;

/**
 * Fragment for P2P settings and configuration
 */
public class P2PSettingsFragment extends Fragment {

    private P2PService p2pService;

    private SwitchCompat switchEnableI2P;
    private TextView tvI2PStatus;
    private TextView tvI2PDestination;
    private TextView tvBatteryThreshold;
    private SeekBar seekBarBatteryThreshold;
    private ImageButton btnCopyDestination;
    private View cardI2PDestination;

    public static P2PSettingsFragment newInstance() {
        return new P2PSettingsFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_p2p_settings, container, false);

        // Initialize P2P service
        p2pService = P2PService.getInstance(getContext());

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
        // Load P2P enabled state
        boolean isEnabled = p2pService.isEnabled();
        switchEnableI2P.setChecked(isEnabled);

        // Load P2P status
        updateI2PStatus();

        // Load P2P peer ID
        updateI2PDestination();

        // Load battery threshold
        int batteryThreshold = p2pService.getBatteryThreshold();
        seekBarBatteryThreshold.setProgress(batteryThreshold);
        tvBatteryThreshold.setText(batteryThreshold + "%");
    }

    private void updateI2PStatus() {
        boolean isReady = p2pService.isP2PReady();
        boolean isRunning = p2pService.isRunning();

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
        String destination = p2pService.getPeerId();

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
        // Enable/disable P2P
        switchEnableI2P.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // Enable P2P
                p2pService.setEnabled(true);
                p2pService.startP2P();
                Toast.makeText(getContext(), "P2P enabled - starting...", Toast.LENGTH_SHORT).show();

                // Update UI after a short delay to show status change
                buttonView.postDelayed(this::updateI2PStatus, 1000);
                buttonView.postDelayed(this::updateI2PDestination, 1000);
            } else {
                // Disable P2P
                p2pService.setEnabled(false);
                p2pService.stopP2P();
                Toast.makeText(getContext(), "P2P disabled", Toast.LENGTH_SHORT).show();
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
                p2pService.setBatteryThreshold(threshold);
                Toast.makeText(getContext(), "Battery threshold set to " + threshold + "%", Toast.LENGTH_SHORT).show();
            }
        });

        // Copy peer ID button
        btnCopyDestination.setOnClickListener(v -> {
            String destination = p2pService.getPeerId();
            if (destination != null && !destination.isEmpty()) {
                ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("P2P Peer ID", destination);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(getContext(), "P2P peer ID copied to clipboard", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), "No P2P peer ID available", Toast.LENGTH_SHORT).show();
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

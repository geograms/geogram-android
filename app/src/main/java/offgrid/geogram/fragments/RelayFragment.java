package offgrid.geogram.fragments;

import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import offgrid.geogram.MainActivity;
import offgrid.geogram.R;
import offgrid.geogram.relay.RelaySettings;
import offgrid.geogram.relay.RelayStorage;

public class RelayFragment extends Fragment {

    private Switch switchEnable;
    private SeekBar seekBarDiskSpace;
    private TextView textDiskSpaceValue;
    private Switch switchAutoAccept;
    private Spinner spinnerMessageTypes;

    // Status displays
    private TextView textInboxCount;
    private TextView textOutboxCount;
    private TextView textSentCount;
    private TextView textStorageUsed;

    private RelaySettings settings;
    private RelayStorage storage;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable updateStatusRunnable = this::updateStatus;

    // Logarithmic steps for disk space: 100MB to 10GB
    private static final int[] DISK_SPACE_VALUES_MB = {
        100, 150, 200, 300, 500, 750, 1024,  // 100MB to 1GB
        1536, 2048, 3072, 4096, 5120, 6144, 7168, 8192, 9216, 10240  // 1.5GB to 10GB
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_relay, container, false);

        // Initialize storage and settings
        settings = new RelaySettings(requireContext());
        storage = new RelayStorage(requireContext());

        // Back button functionality
        ImageButton btnBack = view.findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().getSupportFragmentManager().popBackStack();
            }
        });

        // Initialize status displays
        textInboxCount = view.findViewById(R.id.text_inbox_count);
        textOutboxCount = view.findViewById(R.id.text_outbox_count);
        textSentCount = view.findViewById(R.id.text_sent_count);
        textStorageUsed = view.findViewById(R.id.text_storage_used);

        // Initialize UI components
        switchEnable = view.findViewById(R.id.switch_enable_relay);
        seekBarDiskSpace = view.findViewById(R.id.seekbar_disk_space);
        textDiskSpaceValue = view.findViewById(R.id.text_disk_space_value);
        switchAutoAccept = view.findViewById(R.id.switch_auto_accept);
        spinnerMessageTypes = view.findViewById(R.id.spinner_message_types);

        // Action buttons
        Button btnViewMessages = view.findViewById(R.id.btn_view_messages);
        Button btnClearSent = view.findViewById(R.id.btn_clear_sent);

        btnViewMessages.setOnClickListener(v -> showMessagesDialog());
        btnClearSent.setOnClickListener(v -> clearSentMessages());

        // Load current settings
        loadSettings();

        // Setup listeners
        setupListeners();

        // Update status display
        updateStatus();

        return view;
    }

    private void loadSettings() {
        // Load enabled state
        switchEnable.setChecked(settings.isRelayEnabled());

        // Load disk space limit
        int currentLimitMB = settings.getDiskSpaceLimitMB();
        int closestIndex = findClosestDiskSpaceIndex(currentLimitMB);
        seekBarDiskSpace.setProgress(closestIndex);
        updateDiskSpaceText(closestIndex);

        // Load auto-accept
        switchAutoAccept.setChecked(settings.isAutoAcceptEnabled());

        // Load message types
        String currentType = settings.getAcceptedMessageTypes();
        int spinnerPosition = 0; // default to "Everything"
        if ("text_only".equals(currentType)) {
            spinnerPosition = 1;
        } else if ("text_and_images".equals(currentType)) {
            spinnerPosition = 2;
        }
        spinnerMessageTypes.setSelection(spinnerPosition);
    }

    private int findClosestDiskSpaceIndex(int targetMB) {
        int closestIndex = 0;
        int minDiff = Math.abs(DISK_SPACE_VALUES_MB[0] - targetMB);

        for (int i = 1; i < DISK_SPACE_VALUES_MB.length; i++) {
            int diff = Math.abs(DISK_SPACE_VALUES_MB[i] - targetMB);
            if (diff < minDiff) {
                minDiff = diff;
                closestIndex = i;
            }
        }

        return closestIndex;
    }

    private void setupListeners() {
        // Enable/disable relay
        switchEnable.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settings.setRelayEnabled(isChecked);
            updateMainActivityBadge();
            Toast.makeText(requireContext(),
                "Relay " + (isChecked ? "enabled" : "disabled"),
                Toast.LENGTH_SHORT).show();
        });

        // Disk space slider
        seekBarDiskSpace.setMax(DISK_SPACE_VALUES_MB.length - 1);
        seekBarDiskSpace.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateDiskSpaceText(progress);
                if (fromUser) {
                    int valueMB = DISK_SPACE_VALUES_MB[progress];
                    settings.setDiskSpaceLimitMB(valueMB);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Auto-accept toggle
        switchAutoAccept.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settings.setAutoAcceptEnabled(isChecked);
        });

        // Message types spinner
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                requireContext(),
                R.array.relay_message_types,
                android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMessageTypes.setAdapter(adapter);

        spinnerMessageTypes.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String messageType;
                switch (position) {
                    case 1:
                        messageType = "text_only";
                        break;
                    case 2:
                        messageType = "text_and_images";
                        break;
                    default:
                        messageType = "everything";
                        break;
                }
                settings.setAcceptedMessageTypes(messageType);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void updateDiskSpaceText(int progress) {
        int valueMB = DISK_SPACE_VALUES_MB[progress];
        String displayText;

        if (valueMB >= 1024) {
            float valueGB = valueMB / 1024f;
            displayText = String.format("%.1f GB", valueGB);
        } else {
            displayText = valueMB + " MB";
        }

        textDiskSpaceValue.setText(displayText);
    }

    private void updateStatus() {
        try {
            // Get message counts
            int inboxCount = storage.getMessageCount("inbox");
            int outboxCount = storage.getMessageCount("outbox");
            int sentCount = storage.getMessageCount("sent");

            textInboxCount.setText(String.valueOf(inboxCount));
            textOutboxCount.setText(String.valueOf(outboxCount));
            textSentCount.setText(String.valueOf(sentCount));

            // Get storage used
            long storageBytes = storage.getTotalStorageUsed();
            String storageText = formatStorageSize(storageBytes);
            textStorageUsed.setText(storageText);

            // Update main activity badge
            updateMainActivityBadge();

        } catch (Exception e) {
            textInboxCount.setText("?");
            textOutboxCount.setText("?");
            textSentCount.setText("?");
            textStorageUsed.setText("? MB");
        }
    }

    private String formatStorageSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }

    private void updateMainActivityBadge() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).updateRelayCount();
        }
    }

    private void showMessagesDialog() {
        try {
            int inboxCount = storage.getMessageCount("inbox");
            int outboxCount = storage.getMessageCount("outbox");
            int sentCount = storage.getMessageCount("sent");

            String message = "Relay Messages:\n\n" +
                    "Inbox: " + inboxCount + " messages\n" +
                    "Outbox: " + outboxCount + " messages\n" +
                    "Sent: " + sentCount + " messages\n\n" +
                    "Total Storage: " + formatStorageSize(storage.getTotalStorageUsed());

            new AlertDialog.Builder(requireContext())
                    .setTitle("Relay Messages")
                    .setMessage(message)
                    .setPositiveButton("OK", null)
                    .show();
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Error loading messages", Toast.LENGTH_SHORT).show();
        }
    }

    private void clearSentMessages() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Clear Sent Messages")
                .setMessage("Are you sure you want to clear all sent messages? This cannot be undone.")
                .setPositiveButton("Clear", (dialog, which) -> {
                    try {
                        int cleared = storage.clearFolder("sent");
                        Toast.makeText(requireContext(),
                            "Cleared " + cleared + " sent messages",
                            Toast.LENGTH_SHORT).show();
                        updateStatus();
                    } catch (Exception e) {
                        Toast.makeText(requireContext(),
                            "Error clearing messages",
                            Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Hide top action bar for detail screens
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setTopActionBarVisible(false);
        }

        // Start periodic status updates (every 5 seconds)
        handler.postDelayed(updateStatusRunnable, 5000);
        updateStatus();
    }

    @Override
    public void onPause() {
        super.onPause();
        // Show top action bar when leaving
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setTopActionBarVisible(true);
        }

        // Stop periodic updates
        handler.removeCallbacks(updateStatusRunnable);
    }
}

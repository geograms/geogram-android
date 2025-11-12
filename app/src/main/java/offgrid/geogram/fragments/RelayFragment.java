package offgrid.geogram.fragments;

import androidx.appcompat.app.AlertDialog;
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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import offgrid.geogram.MainActivity;
import offgrid.geogram.R;
import offgrid.geogram.contacts.ContactFolderManager;
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
    private ContactFolderManager folderManager;

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
        folderManager = new ContactFolderManager(requireContext());

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
            // Get message counts from both global relay folder and contact-specific folders
            int inboxCount = storage.getMessageCount("inbox") + getContactRelayMessageCount("inbox");
            int outboxCount = storage.getMessageCount("outbox") + getContactRelayMessageCount("outbox");
            int sentCount = storage.getMessageCount("sent") + getContactRelayMessageCount("sent");

            textInboxCount.setText(String.valueOf(inboxCount));
            textOutboxCount.setText(String.valueOf(outboxCount));
            textSentCount.setText(String.valueOf(sentCount));

            // Get storage used (both global and contact-specific)
            long storageBytes = storage.getTotalStorageUsed() + getContactRelayStorageUsed();
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

    /**
     * Count relay messages in all contact folders for a specific folder type.
     */
    private int getContactRelayMessageCount(String folderType) {
        int count = 0;
        try {
            File contactsDir = folderManager.getContactsDir();
            if (!contactsDir.exists() || !contactsDir.isDirectory()) {
                return 0;
            }

            File[] contactFolders = contactsDir.listFiles(File::isDirectory);
            if (contactFolders == null) {
                return 0;
            }

            for (File contactFolder : contactFolders) {
                String callsign = contactFolder.getName();
                File relayFolder;

                if ("inbox".equalsIgnoreCase(folderType)) {
                    relayFolder = folderManager.getRelayInboxDir(callsign);
                } else if ("outbox".equalsIgnoreCase(folderType)) {
                    relayFolder = folderManager.getRelayOutboxDir(callsign);
                } else if ("sent".equalsIgnoreCase(folderType)) {
                    relayFolder = folderManager.getRelaySentDir(callsign);
                } else {
                    continue;
                }

                if (relayFolder.exists() && relayFolder.isDirectory()) {
                    File[] messages = relayFolder.listFiles((dir, name) -> name.endsWith(".md"));
                    if (messages != null) {
                        count += messages.length;
                    }
                }
            }
        } catch (Exception e) {
            // Ignore errors, return what we have
        }
        return count;
    }

    /**
     * Calculate total storage used by all contact relay messages.
     */
    private long getContactRelayStorageUsed() {
        long totalBytes = 0;
        try {
            File contactsDir = folderManager.getContactsDir();
            if (!contactsDir.exists() || !contactsDir.isDirectory()) {
                return 0;
            }

            File[] contactFolders = contactsDir.listFiles(File::isDirectory);
            if (contactFolders == null) {
                return 0;
            }

            for (File contactFolder : contactFolders) {
                String callsign = contactFolder.getName();
                File relayDir = folderManager.getRelayDir(callsign);

                if (relayDir.exists() && relayDir.isDirectory()) {
                    totalBytes += calculateDirectorySize(relayDir);
                }
            }
        } catch (Exception e) {
            // Ignore errors, return what we have
        }
        return totalBytes;
    }

    /**
     * Calculate total size of all files in a directory recursively.
     */
    private long calculateDirectorySize(File directory) {
        long size = 0;
        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        size += file.length();
                    } else if (file.isDirectory()) {
                        size += calculateDirectorySize(file);
                    }
                }
            }
        }
        return size;
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
            // Show selection dialog for which folder to view
            String[] folders = {"Inbox", "Outbox", "Sent"};

            AlertDialog dialog = new AlertDialog.Builder(requireContext())
                    .setTitle("View Relay Messages")
                    .setItems(folders, (d, which) -> {
                        String folderType = folders[which].toLowerCase();
                        openMessagesGrid(folderType);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();

            // Set button text color explicitly
            android.widget.Button negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (negativeButton != null) {
                negativeButton.setTextColor(getResources().getColor(R.color.white, null));
            }
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Error loading messages", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Open the messages grid view for a specific folder type.
     */
    private void openMessagesGrid(String folderType) {
        RelayMessagesGridFragment fragment = RelayMessagesGridFragment.newInstance(folderType);

        if (getActivity() != null) {
            androidx.fragment.app.FragmentTransaction transaction =
                    getActivity().getSupportFragmentManager().beginTransaction();
            transaction.replace(R.id.fragment_container, fragment);
            transaction.addToBackStack(null);
            transaction.commit();
        }
    }

    /**
     * Show list of messages from a specific folder (inbox, outbox, or sent).
     * Includes messages from both global relay folder and all contact-specific folders.
     */
    private void showMessagesList(String folderType) {
        try {
            // Collect all messages from both global and contact folders
            List<RelayMessageItem> messages = new ArrayList<>();

            // Load from global relay folder
            File globalFolder = getGlobalRelayFolder(folderType);
            loadMessagesFromFolder(globalFolder, null, messages);

            // Load from all contact folders
            File contactsDir = folderManager.getContactsDir();
            if (contactsDir.exists() && contactsDir.isDirectory()) {
                File[] contactFolders = contactsDir.listFiles(File::isDirectory);
                if (contactFolders != null) {
                    for (File contactFolder : contactFolders) {
                        String callsign = contactFolder.getName();
                        File relayFolder;

                        if ("inbox".equalsIgnoreCase(folderType)) {
                            relayFolder = folderManager.getRelayInboxDir(callsign);
                        } else if ("outbox".equalsIgnoreCase(folderType)) {
                            relayFolder = folderManager.getRelayOutboxDir(callsign);
                        } else {
                            relayFolder = folderManager.getRelaySentDir(callsign);
                        }

                        loadMessagesFromFolder(relayFolder, callsign, messages);
                    }
                }
            }

            // Sort by timestamp (newest first)
            messages.sort((m1, m2) -> Long.compare(m2.timestamp, m1.timestamp));

            if (messages.isEmpty()) {
                Toast.makeText(requireContext(),
                    "No messages in " + folderType,
                    Toast.LENGTH_SHORT).show();
                return;
            }

            // Create display strings for the list
            String[] displayItems = new String[messages.size()];
            for (int i = 0; i < messages.size(); i++) {
                RelayMessageItem item = messages.get(i);
                String timeStr = new java.text.SimpleDateFormat("MMM dd HH:mm", java.util.Locale.US)
                    .format(new java.util.Date(item.timestamp * 1000));

                String preview = item.subject != null && !item.subject.isEmpty()
                    ? item.subject
                    : item.content;

                if (preview.length() > 40) {
                    preview = preview.substring(0, 40) + "...";
                }

                String location = item.contactCallsign != null
                    ? " [" + item.contactCallsign + "]"
                    : " [Global]";

                displayItems[i] = timeStr + " - " + preview + location;
            }

            // Show list dialog
            AlertDialog dialog = new AlertDialog.Builder(requireContext())
                    .setTitle(capitalize(folderType) + " Messages (" + messages.size() + ")")
                    .setItems(displayItems, (d, which) -> {
                        RelayMessageItem selected = messages.get(which);
                        showMessageDetails(selected);
                    })
                    .setNegativeButton("Close", null)
                    .show();

            // Set button text color explicitly
            android.widget.Button negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (negativeButton != null) {
                negativeButton.setTextColor(getResources().getColor(R.color.white, null));
            }

        } catch (Exception e) {
            Toast.makeText(requireContext(),
                "Error loading messages: " + e.getMessage(),
                Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Load messages from a specific folder into the list.
     */
    private void loadMessagesFromFolder(File folder, String contactCallsign, List<RelayMessageItem> messages) {
        if (!folder.exists() || !folder.isDirectory()) {
            return;
        }

        File[] messageFiles = folder.listFiles((dir, name) -> name.endsWith(".md"));
        if (messageFiles == null) {
            return;
        }

        for (File file : messageFiles) {
            try {
                String markdown = new String(java.nio.file.Files.readAllBytes(file.toPath()),
                    java.nio.charset.StandardCharsets.UTF_8);
                offgrid.geogram.relay.RelayMessage message =
                    offgrid.geogram.relay.RelayMessage.parseMarkdown(markdown);

                if (message != null) {
                    RelayMessageItem item = new RelayMessageItem();
                    item.message = message;
                    item.file = file;
                    item.contactCallsign = contactCallsign;
                    item.timestamp = message.getTimestamp();
                    item.subject = message.getSubject();
                    item.content = message.getContent();
                    messages.add(item);
                }
            } catch (Exception e) {
                // Skip files that can't be parsed
            }
        }
    }

    /**
     * Get the global relay folder for a specific type.
     */
    private File getGlobalRelayFolder(String folderType) {
        File relayDir = new File(requireContext().getFilesDir(), "relay");
        return new File(relayDir, folderType);
    }

    /**
     * Show detailed view of a relay message.
     */
    private void showMessageDetails(RelayMessageItem item) {
        try {
            offgrid.geogram.relay.RelayMessage message = item.message;

            StringBuilder details = new StringBuilder();

            // Subject
            if (message.getSubject() != null && !message.getSubject().isEmpty()) {
                details.append("Subject: ").append(message.getSubject()).append("\n\n");
            }

            // From/To
            details.append("From: ").append(message.getFromCallsign()).append("\n");
            if (message.getToCallsign() != null) {
                details.append("To: ").append(message.getToCallsign()).append("\n");
            }

            // Timestamp
            String timeStr = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                .format(new java.util.Date(message.getTimestamp() * 1000));
            details.append("Time: ").append(timeStr).append("\n");

            // Priority
            if (message.getPriority() != null) {
                details.append("Priority: ").append(message.getPriority()).append("\n");
            }

            // Location
            String location = item.contactCallsign != null
                ? "Contact folder: " + item.contactCallsign
                : "Global relay folder";
            details.append("Location: ").append(location).append("\n\n");

            // Content
            details.append("Message:\n").append(message.getContent());

            AlertDialog dialog = new AlertDialog.Builder(requireContext())
                    .setTitle("Message Details")
                    .setMessage(details.toString())
                    .setPositiveButton("OK", null)
                    .show();

            // Set button text color explicitly
            android.widget.Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (positiveButton != null) {
                positiveButton.setTextColor(getResources().getColor(R.color.white, null));
            }

        } catch (Exception e) {
            Toast.makeText(requireContext(),
                "Error showing message: " + e.getMessage(),
                Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Capitalize first letter of a string.
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    /**
     * Helper class to hold message info for the list.
     */
    private static class RelayMessageItem {
        offgrid.geogram.relay.RelayMessage message;
        File file;
        String contactCallsign;  // null if from global folder
        long timestamp;
        String subject;
        String content;
    }

    /**
     * Clear all sent messages with confirmation dialog.
     */
    private void clearSentMessages() {
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Clear Sent Messages")
                .setMessage("Are you sure you want to clear all sent messages? This cannot be undone.")
                .setPositiveButton("Clear", (d, which) -> {
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

        // Set button text colors explicitly
        android.widget.Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        android.widget.Button negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (positiveButton != null) {
            positiveButton.setTextColor(getResources().getColor(R.color.white, null));
        }
        if (negativeButton != null) {
            negativeButton.setTextColor(getResources().getColor(R.color.white, null));
        }
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

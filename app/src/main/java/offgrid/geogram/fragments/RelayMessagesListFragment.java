package offgrid.geogram.fragments;

import androidx.appcompat.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import offgrid.geogram.R;
import offgrid.geogram.contacts.ContactFolderManager;
import offgrid.geogram.core.Central;
import offgrid.geogram.core.Log;
import offgrid.geogram.relay.RelayMessage;
import offgrid.geogram.relay.RelayStorage;
import offgrid.geogram.settings.SettingsUser;

/**
 * Displays relay messages for a specific contact in an email-style list.
 *
 * Shows messages from inbox, outbox, and sent folders with:
 * - Subject/content preview
 * - From/To information
 * - Timestamp
 * - Priority indicator
 * - Delivery status
 * - Attachment count
 */
public class RelayMessagesListFragment extends Fragment {

    private static final String TAG = "RelayMessagesListFragment";
    private static final String ARG_CALLSIGN = "callsign";

    private String callsign;
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView messagesRecyclerView;
    private TextView emptyState;
    private FloatingActionButton fabCompose;
    private RelayMessageAdapter adapter;

    private ContactFolderManager folderManager;
    private RelayStorage relayStorage;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public static RelayMessagesListFragment newInstance(String callsign) {
        RelayMessagesListFragment fragment = new RelayMessagesListFragment();
        Bundle args = new Bundle();
        args.putString(ARG_CALLSIGN, callsign);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            callsign = getArguments().getString(ARG_CALLSIGN);
        }

        folderManager = new ContactFolderManager(requireContext());
        relayStorage = new RelayStorage(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_relay_messages_list, container, false);

        // Initialize views
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout);
        messagesRecyclerView = view.findViewById(R.id.messages_recycler_view);
        emptyState = view.findViewById(R.id.empty_state);
        fabCompose = view.findViewById(R.id.fab_compose);

        // Setup RecyclerView
        messagesRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new RelayMessageAdapter(new ArrayList<>(), this::onMessageClick);
        messagesRecyclerView.setAdapter(adapter);

        // Setup swipe refresh
        swipeRefreshLayout.setOnRefreshListener(this::loadMessages);

        // Setup FAB
        fabCompose.setOnClickListener(v -> showComposeDialog());

        // Load messages
        loadMessages();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Reload messages when returning to this fragment (e.g., after replying)
        if (swipeRefreshLayout != null) {
            loadMessages();
        }
    }

    private void loadMessages() {
        swipeRefreshLayout.setRefreshing(true);

        new Thread(() -> {
            List<RelayMessageItem> messageItems = new ArrayList<>();

            try {
                // Load messages from contact's relay folders
                File inboxDir = folderManager.getRelayInboxDir(callsign);
                File outboxDir = folderManager.getRelayOutboxDir(callsign);
                File sentDir = folderManager.getRelaySentDir(callsign);

                // Add inbox messages
                addMessagesFromFolder(messageItems, inboxDir, "Inbox");

                // Add outbox messages
                addMessagesFromFolder(messageItems, outboxDir, "Outbox");

                // Add sent messages
                addMessagesFromFolder(messageItems, sentDir, "Sent");

                // Sort by timestamp (newest first)
                Collections.sort(messageItems, (m1, m2) ->
                    Long.compare(m2.message.getTimestamp(), m1.message.getTimestamp()));

            } catch (Exception e) {
                Log.e(TAG, "Error loading relay messages: " + e.getMessage());
            }

            // Update UI
            handler.post(() -> {
                swipeRefreshLayout.setRefreshing(false);

                if (messageItems.isEmpty()) {
                    emptyState.setVisibility(View.VISIBLE);
                    messagesRecyclerView.setVisibility(View.GONE);
                } else {
                    emptyState.setVisibility(View.GONE);
                    messagesRecyclerView.setVisibility(View.VISIBLE);
                    adapter.updateMessages(messageItems);
                }

                Log.d(TAG, "Loaded " + messageItems.size() + " relay messages for " + callsign);
            });
        }).start();
    }

    private void addMessagesFromFolder(List<RelayMessageItem> messageItems, File folder, String folderName) {
        if (!folder.exists() || !folder.isDirectory()) {
            return;
        }

        File[] files = folder.listFiles((dir, name) -> name.endsWith(".md"));
        if (files == null) {
            return;
        }

        for (File file : files) {
            try {
                // Read markdown file directly from contact folder
                String markdown = new String(
                    java.nio.file.Files.readAllBytes(file.toPath()),
                    java.nio.charset.StandardCharsets.UTF_8
                );

                // Parse as thread (handles both single messages and threaded conversations)
                List<RelayMessage> threadMessages = RelayMessage.parseThread(markdown);

                if (threadMessages != null && !threadMessages.isEmpty()) {
                    // Use the first message (represents the thread)
                    RelayMessage message = threadMessages.get(0);

                    // Only include messages to/from this contact
                    if (message.getFromCallsign().equalsIgnoreCase(callsign) ||
                        message.getToCallsign().equalsIgnoreCase(callsign)) {
                        RelayMessageItem item = new RelayMessageItem(message, folderName);
                        item.messageCount = threadMessages.size(); // Track thread size
                        messageItems.add(item);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading message from " + file.getName() + ": " + e.getMessage());
            }
        }
    }

    private void onMessageClick(RelayMessageItem item) {
        // Navigate to message thread view
        if (getActivity() != null) {
            RelayMessageThreadFragment fragment = RelayMessageThreadFragment.newInstance(
                item.message.getId(),
                item.folder.toLowerCase(),
                callsign
            );

            getActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit();
        }
    }

    /**
     * Show dialog to compose a new relay message.
     */
    private void showComposeDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Compose Relay Message");

        // Create layout for dialog
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        // To field (pre-filled with contact callsign)
        TextView toLabel = new TextView(requireContext());
        toLabel.setText("To:");
        toLabel.setTextSize(14);
        layout.addView(toLabel);

        TextView toField = new TextView(requireContext());
        toField.setText(callsign);
        toField.setTextSize(16);
        toField.setTextColor(0xFFFFFFFF);
        toField.setPadding(0, 0, 0, 20);
        layout.addView(toField);

        // Subject field (optional)
        TextView subjectLabel = new TextView(requireContext());
        subjectLabel.setText("Subject (optional):");
        subjectLabel.setTextSize(14);
        layout.addView(subjectLabel);

        EditText subjectInput = new EditText(requireContext());
        subjectInput.setHint("e.g., Meeting reminder");
        subjectInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        subjectInput.setSingleLine(true);
        subjectInput.setPadding(0, 0, 0, 20);
        layout.addView(subjectInput);

        // Priority spinner
        TextView priorityLabel = new TextView(requireContext());
        priorityLabel.setText("Priority:");
        priorityLabel.setTextSize(14);
        layout.addView(priorityLabel);

        Spinner prioritySpinner = new Spinner(requireContext());
        ArrayAdapter<CharSequence> priorityAdapter = ArrayAdapter.createFromResource(
            requireContext(),
            R.array.relay_priority_levels,
            android.R.layout.simple_spinner_item
        );
        priorityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        prioritySpinner.setAdapter(priorityAdapter);
        prioritySpinner.setSelection(1); // Default to "normal"
        prioritySpinner.setPadding(0, 0, 0, 20);
        layout.addView(prioritySpinner);

        // Message content
        TextView contentLabel = new TextView(requireContext());
        contentLabel.setText("Message:");
        contentLabel.setTextSize(14);
        layout.addView(contentLabel);

        EditText contentInput = new EditText(requireContext());
        contentInput.setHint("Enter your message...");
        contentInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        contentInput.setMinLines(4);
        contentInput.setMaxLines(8);
        layout.addView(contentInput);

        builder.setView(layout);

        // Send button
        builder.setPositiveButton("Send", (dialog, which) -> {
            String subject = subjectInput.getText().toString().trim();
            String content = contentInput.getText().toString().trim();

            if (content.isEmpty()) {
                Toast.makeText(requireContext(), "Message cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }

            String priority;
            switch (prioritySpinner.getSelectedItemPosition()) {
                case 0:
                    priority = "urgent";
                    break;
                case 2:
                    priority = "low";
                    break;
                default:
                    priority = "normal";
                    break;
            }

            // Create and send relay message (subject is optional, can be empty)
            composeRelayMessage(callsign, subject, content, priority);
        });

        // Cancel button
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        AlertDialog dialog = builder.show();

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

    /**
     * Compose and save a new relay message to outbox.
     */
    private void composeRelayMessage(String toCallsign, String subject, String content, String priority) {
        new Thread(() -> {
            try {
                // Get user's callsign
                SettingsUser settings = Central.getInstance().getSettings();
                String fromCallsign = settings.getCallsign();

                if (fromCallsign == null || fromCallsign.isEmpty()) {
                    handler.post(() -> {
                        Toast.makeText(requireContext(),
                            "Please configure your callsign in Settings",
                            Toast.LENGTH_LONG).show();
                    });
                    return;
                }

                // Generate unique message ID
                long timestamp = System.currentTimeMillis();
                String messageId = fromCallsign + "-" + timestamp;

                // Create relay message
                RelayMessage message = new RelayMessage();
                message.setId(messageId);
                message.setFromCallsign(fromCallsign);
                message.setToCallsign(toCallsign);

                // Set subject if provided (optional)
                if (subject != null && !subject.isEmpty()) {
                    message.setSubject(subject);
                }

                message.setContent(content);
                message.setType("private");
                message.setPriority(priority);
                message.setTtl(604800); // 7 days
                message.setTimestamp(timestamp / 1000);

                // Save to contact's relay outbox
                File outboxDir = folderManager.getRelayOutboxDir(toCallsign);
                File messageFile = new File(outboxDir, messageId + ".md");

                // Ensure directory exists
                if (!outboxDir.exists()) {
                    outboxDir.mkdirs();
                }

                // Write message
                java.nio.file.Files.write(
                    messageFile.toPath(),
                    message.toMarkdown().getBytes(java.nio.charset.StandardCharsets.UTF_8)
                );

                handler.post(() -> {
                    Toast.makeText(requireContext(),
                        "Message queued for relay",
                        Toast.LENGTH_SHORT).show();

                    // Reload messages to show the new one
                    loadMessages();

                    Log.d(TAG, "Created relay message: " + messageId + " to " + toCallsign);
                });

            } catch (Exception e) {
                Log.e(TAG, "Error composing relay message: " + e.getMessage());
                handler.post(() -> {
                    Toast.makeText(requireContext(),
                        "Error creating message: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    // --- Data Classes ---

    /**
     * Wrapper for a relay message with metadata.
     */
    private static class RelayMessageItem {
        final RelayMessage message;
        final String folder; // "Inbox", "Outbox", or "Sent"
        int messageCount = 1; // Number of messages in thread

        RelayMessageItem(RelayMessage message, String folder) {
            this.message = message;
            this.folder = folder;
        }
    }

    // --- Adapter ---

    private static class RelayMessageAdapter extends RecyclerView.Adapter<RelayMessageAdapter.ViewHolder> {

        private List<RelayMessageItem> messages;
        private final OnMessageClickListener clickListener;

        interface OnMessageClickListener {
            void onMessageClick(RelayMessageItem item);
        }

        RelayMessageAdapter(List<RelayMessageItem> messages, OnMessageClickListener clickListener) {
            this.messages = messages;
            this.clickListener = clickListener;
        }

        void updateMessages(List<RelayMessageItem> newMessages) {
            this.messages = newMessages;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_relay_message, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            RelayMessageItem item = messages.get(position);
            holder.bind(item, clickListener);
        }

        @Override
        public int getItemCount() {
            return messages.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            private final View priorityIndicator;
            private final TextView textFromTo;
            private final TextView textTimestamp;
            private final TextView textContent;
            private final TextView textFolder;
            private final TextView textAttachmentCount;
            private final TextView textStatus;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                priorityIndicator = itemView.findViewById(R.id.priority_indicator);
                textFromTo = itemView.findViewById(R.id.text_from_to);
                textTimestamp = itemView.findViewById(R.id.text_timestamp);
                textContent = itemView.findViewById(R.id.text_content);
                textFolder = itemView.findViewById(R.id.text_folder);
                textAttachmentCount = itemView.findViewById(R.id.text_attachment_count);
                textStatus = itemView.findViewById(R.id.text_status);
            }

            void bind(RelayMessageItem item, OnMessageClickListener listener) {
                RelayMessage message = item.message;

                // Priority indicator color
                int priorityColor;
                if ("urgent".equalsIgnoreCase(message.getPriority())) {
                    priorityColor = 0xFFF44336; // Red
                } else if ("high".equalsIgnoreCase(message.getPriority())) {
                    priorityColor = 0xFFFF9800; // Orange
                } else {
                    priorityColor = 0xFFCCCCCC; // Gray
                }
                priorityIndicator.setBackgroundColor(priorityColor);

                // From/To
                String fromTo;
                if ("Sent".equals(item.folder) || "Outbox".equals(item.folder)) {
                    fromTo = "To: " + message.getToCallsign();
                } else {
                    fromTo = "From: " + message.getFromCallsign();
                }
                textFromTo.setText(fromTo);

                // Timestamp
                SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, HH:mm", Locale.US);
                String timestamp = dateFormat.format(new Date(message.getTimestamp() * 1000));
                textTimestamp.setText(timestamp);

                // Content preview - show subject if present, otherwise show content
                String displayText;
                String subject = message.getSubject();
                String content = message.getContent();

                if (subject != null && !subject.isEmpty()) {
                    // Show subject in bold/prominent style
                    displayText = subject;
                    // If there's content too, show a preview after the subject
                    if (content != null && !content.isEmpty()) {
                        // Limit content preview to first 50 chars
                        String contentPreview = content.length() > 50
                            ? content.substring(0, 50) + "..."
                            : content;
                        displayText = subject + "\n" + contentPreview;
                    }
                } else if (content != null && !content.isEmpty()) {
                    // No subject, just show content
                    displayText = content;
                } else {
                    displayText = "(No content)";
                }

                // Add thread indicator if there are multiple messages
                if (item.messageCount > 1) {
                    displayText = "(" + item.messageCount + ") " + displayText;
                }

                textContent.setText(displayText);

                // Folder badge
                textFolder.setText(item.folder);

                // Attachment count
                if (message.getAttachments() != null && !message.getAttachments().isEmpty()) {
                    textAttachmentCount.setText("📎 " + message.getAttachments().size());
                    textAttachmentCount.setVisibility(View.VISIBLE);
                } else {
                    textAttachmentCount.setVisibility(View.GONE);
                }

                // Status indicator
                String statusIcon;
                int statusColor;

                if ("Sent".equals(item.folder)) {
                    statusIcon = "✓✓"; // Checkmark (sent)
                    statusColor = 0xFF4CAF50; // Green
                } else if ("Outbox".equals(item.folder)) {
                    statusIcon = "⏱"; // Clock (queued)
                    statusColor = 0xFFFF9800; // Orange
                } else {
                    statusIcon = "⬇"; // Down arrow (received)
                    statusColor = 0xFF2196F3; // Blue
                }

                textStatus.setText(statusIcon);
                textStatus.setTextColor(statusColor);

                // Click listener
                itemView.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onMessageClick(item);
                    }
                });
            }
        }
    }
}

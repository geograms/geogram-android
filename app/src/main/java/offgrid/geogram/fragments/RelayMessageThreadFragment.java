package offgrid.geogram.fragments;

import androidx.appcompat.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import offgrid.geogram.MainActivity;
import offgrid.geogram.R;
import offgrid.geogram.contacts.ContactFolderManager;
import offgrid.geogram.core.Log;
import offgrid.geogram.relay.RelayAttachment;
import offgrid.geogram.relay.RelayMessage;
import offgrid.geogram.relay.RelayStorage;

/**
 * Displays a single relay message with full details and actions (reply, delete).
 *
 * Shows:
 * - From/To/Timestamp
 * - Priority
 * - Full message content
 * - Attachments (if any)
 * - Actions: Reply, Delete
 */
public class RelayMessageThreadFragment extends Fragment {

    private static final String TAG = "RelayMessageThreadFragment";
    private static final String ARG_MESSAGE_ID = "message_id";
    private static final String ARG_FOLDER = "folder";
    private static final String ARG_CALLSIGN = "callsign";
    private static final String ARG_MESSAGE_FILE = "message_file";

    private String messageId;
    private String folder;
    private String callsign;
    private String messageFilePath;

    private TextView textFrom;
    private TextView textTo;
    private TextView textTimestamp;
    private TextView textPriority;
    private TextView textSubject;
    private TextView textContent;
    private LinearLayout attachmentsSection;
    private TextView textAttachments;
    private Button btnReply;
    private Button btnDelete;

    private RelayStorage relayStorage;
    private ContactFolderManager folderManager;
    private RelayMessage message;
    private java.util.List<RelayMessage> threadMessages;  // All messages in the conversation
    private java.io.File messageFile;  // Track the file for appending replies

    public static RelayMessageThreadFragment newInstance(String messageId, String folder, String callsign) {
        RelayMessageThreadFragment fragment = new RelayMessageThreadFragment();
        Bundle args = new Bundle();
        args.putString(ARG_MESSAGE_ID, messageId);
        args.putString(ARG_FOLDER, folder);
        args.putString(ARG_CALLSIGN, callsign);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            messageId = getArguments().getString(ARG_MESSAGE_ID);
            folder = getArguments().getString(ARG_FOLDER);
            callsign = getArguments().getString(ARG_CALLSIGN);
            messageFilePath = getArguments().getString(ARG_MESSAGE_FILE);
        }

        relayStorage = new RelayStorage(requireContext());
        folderManager = new ContactFolderManager(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_relay_message_thread, container, false);

        // Initialize views
        textFrom = view.findViewById(R.id.text_from);
        textTo = view.findViewById(R.id.text_to);
        textTimestamp = view.findViewById(R.id.text_timestamp);
        textPriority = view.findViewById(R.id.text_priority);
        textSubject = view.findViewById(R.id.text_subject);
        textContent = view.findViewById(R.id.text_content);
        attachmentsSection = view.findViewById(R.id.attachments_section);
        textAttachments = view.findViewById(R.id.text_attachments);
        btnReply = view.findViewById(R.id.btn_reply);
        btnDelete = view.findViewById(R.id.btn_delete);

        // Back button
        ImageButton btnBack = view.findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().getSupportFragmentManager().popBackStack();
            }
        });

        // Action buttons
        btnReply.setOnClickListener(v -> replyToMessage());
        btnDelete.setOnClickListener(v -> deleteMessage());

        // Load message
        loadMessage();

        return view;
    }

    private void loadMessage() {
        new Thread(() -> {
            try {
                // Check if we have a direct file path (from grid view)
                if (messageFilePath != null && !messageFilePath.isEmpty()) {
                    messageFile = new java.io.File(messageFilePath);
                } else {
                    // Construct path from messageId, folder, and callsign (legacy approach)
                    java.io.File messageDir;
                    if ("inbox".equalsIgnoreCase(folder)) {
                        messageDir = folderManager.getRelayInboxDir(callsign);
                    } else if ("outbox".equalsIgnoreCase(folder)) {
                        messageDir = folderManager.getRelayOutboxDir(callsign);
                    } else if ("sent".equalsIgnoreCase(folder)) {
                        messageDir = folderManager.getRelaySentDir(callsign);
                    } else {
                        Log.e(TAG, "Unknown folder: " + folder);
                        return;
                    }

                    messageFile = new java.io.File(messageDir, messageId + ".md");
                }

                if (!messageFile.exists()) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            Toast.makeText(requireContext(), "Message not found", Toast.LENGTH_SHORT).show();
                            if (getActivity() != null) {
                                getActivity().getSupportFragmentManager().popBackStack();
                            }
                        });
                    }
                    return;
                }

                // Read and parse markdown file as a thread
                String markdown = new String(
                    java.nio.file.Files.readAllBytes(messageFile.toPath()),
                    java.nio.charset.StandardCharsets.UTF_8
                );

                // Parse all messages in the thread
                threadMessages = RelayMessage.parseThread(markdown);

                if (threadMessages == null || threadMessages.isEmpty()) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            Toast.makeText(requireContext(), "Failed to parse message", Toast.LENGTH_SHORT).show();
                            if (getActivity() != null) {
                                getActivity().getSupportFragmentManager().popBackStack();
                            }
                        });
                    }
                    return;
                }

                // Set the first message as the primary message (for compatibility)
                message = threadMessages.get(0);

                // Update UI on main thread
                if (getActivity() != null) {
                    getActivity().runOnUiThread(this::displayMessage);
                }

            } catch (Exception e) {
                Log.e(TAG, "Error loading message: " + e.getMessage());
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), "Error loading message: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }
            }
        }).start();
    }

    private void displayMessage() {
        if (message == null || threadMessages == null || threadMessages.isEmpty()) {
            return;
        }

        // Display the first message's metadata at the top
        textFrom.setText("From: " + message.getFromCallsign());
        textTo.setText("To: " + message.getToCallsign());

        // Timestamp
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US);
        String timestamp = dateFormat.format(new Date(message.getTimestamp() * 1000));
        textTimestamp.setText("Date: " + timestamp);

        // Priority
        String priority = message.getPriority();
        if (priority == null) {
            priority = "normal";
        }
        textPriority.setText("Priority: " + priority.substring(0, 1).toUpperCase() + priority.substring(1));

        // Set priority background color
        int priorityColor;
        if ("urgent".equalsIgnoreCase(priority)) {
            priorityColor = 0xFFF44336; // Red
        } else if ("high".equalsIgnoreCase(priority)) {
            priorityColor = 0xFFFF9800; // Orange
        } else {
            priorityColor = 0xFF454545; // Gray
        }
        textPriority.setBackgroundColor(priorityColor);

        // Subject (optional)
        String subject = message.getSubject();
        if (subject != null && !subject.isEmpty()) {
            textSubject.setText("Subject: " + subject);
            textSubject.setVisibility(View.VISIBLE);
        } else {
            textSubject.setVisibility(View.GONE);
        }

        // Build conversation thread - show all messages
        StringBuilder conversationContent = new StringBuilder();
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.US);

        for (int i = 0; i < threadMessages.size(); i++) {
            RelayMessage msg = threadMessages.get(i);

            // Add visual separator between messages
            if (i > 0) {
                conversationContent.append("\n\n─────────────────────────\n\n");
            }

            // Message header
            String msgTime = timeFormat.format(new Date(msg.getTimestamp() * 1000));
            conversationContent.append("▸ ").append(msg.getFromCallsign())
                              .append(" (").append(msgTime).append(")\n\n");

            // Message content
            String content = msg.getContent();
            if (content != null && !content.isEmpty()) {
                conversationContent.append(content);
            } else {
                conversationContent.append("(No content)");
            }

            // Show if message is a reply
            if (msg.getInReplyTo() != null && !msg.getInReplyTo().isEmpty()) {
                conversationContent.append("\n\n[↩ Reply to: ").append(msg.getInReplyTo().substring(0, Math.min(8, msg.getInReplyTo().length()))).append("...]");
            }
        }

        textContent.setText(conversationContent.toString());

        // Show attachments from the first message only (for now)
        if (message.getAttachments() != null && !message.getAttachments().isEmpty()) {
            attachmentsSection.setVisibility(View.VISIBLE);

            StringBuilder attachmentsList = new StringBuilder();
            for (RelayAttachment attachment : message.getAttachments()) {
                attachmentsList.append("📎 ")
                              .append(attachment.getFilename())
                              .append(" (")
                              .append(attachment.getMimeType())
                              .append(")\n");
            }

            textAttachments.setText(attachmentsList.toString().trim());
        } else {
            attachmentsSection.setVisibility(View.GONE);
        }

        // Update reply button text if there are multiple messages
        if (threadMessages.size() > 1) {
            btnReply.setText("Reply (" + threadMessages.size() + ")");
        }

        Log.d(TAG, "Displayed message thread with " + threadMessages.size() + " messages");
    }

    private void replyToMessage() {
        if (message == null) {
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Reply to " + message.getFromCallsign());

        // Create layout for dialog
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        // Show original subject if present
        if (message.getSubject() != null && !message.getSubject().isEmpty()) {
            TextView originalSubjectLabel = new TextView(requireContext());
            originalSubjectLabel.setText("Original Subject:");
            originalSubjectLabel.setTextSize(12);
            originalSubjectLabel.setTextColor(0xFF888888);
            layout.addView(originalSubjectLabel);

            TextView originalSubjectText = new TextView(requireContext());
            originalSubjectText.setText(message.getSubject());
            originalSubjectText.setTextSize(14);
            originalSubjectText.setTextColor(0xFFCCCCCC);
            originalSubjectText.setPadding(0, 0, 0, 20);
            layout.addView(originalSubjectText);
        }

        // Subject field (pre-filled with "Re: ")
        TextView subjectLabel = new TextView(requireContext());
        subjectLabel.setText("Subject (optional):");
        subjectLabel.setTextSize(14);
        layout.addView(subjectLabel);

        EditText subjectInput = new EditText(requireContext());
        // Pre-fill with "Re: [original subject]" if there was one
        if (message.getSubject() != null && !message.getSubject().isEmpty()) {
            String replySubject = message.getSubject().startsWith("Re: ")
                ? message.getSubject()  // Don't add "Re: " multiple times
                : "Re: " + message.getSubject();
            subjectInput.setText(replySubject);
        }
        subjectInput.setHint("e.g., Re: Meeting reminder");
        subjectInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        subjectInput.setSingleLine(true);
        subjectInput.setPadding(0, 0, 0, 20);
        layout.addView(subjectInput);

        // Message content
        TextView contentLabel = new TextView(requireContext());
        contentLabel.setText("Message:");
        contentLabel.setTextSize(14);
        layout.addView(contentLabel);

        EditText contentInput = new EditText(requireContext());
        contentInput.setHint("Enter your reply...");
        contentInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        contentInput.setMinLines(4);
        contentInput.setMaxLines(8);
        layout.addView(contentInput);

        builder.setView(layout);

        // Send button
        builder.setPositiveButton("Send", (dialog, which) -> {
            String subject = subjectInput.getText().toString().trim();
            String content = contentInput.getText().toString().trim();

            if (content.isEmpty()) {
                Toast.makeText(requireContext(), "Reply cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }

            // Send reply
            sendReply(message.getFromCallsign(), subject, content);
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

    private void sendReply(String toCallsign, String subject, String content) {
        new Thread(() -> {
            try {
                // Get user's callsign
                offgrid.geogram.settings.SettingsUser settings = offgrid.geogram.core.Central.getInstance().getSettings();
                String fromCallsign = settings.getCallsign();

                if (fromCallsign == null || fromCallsign.isEmpty()) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            Toast.makeText(requireContext(),
                                "Please configure your callsign in Settings",
                                Toast.LENGTH_LONG).show();
                        });
                    }
                    return;
                }

                // Generate unique message ID
                long timestamp = System.currentTimeMillis();
                String replyMessageId = fromCallsign + "-" + timestamp;

                // Create relay message
                RelayMessage replyMessage = new RelayMessage();
                replyMessage.setId(replyMessageId);
                replyMessage.setFromCallsign(fromCallsign);
                replyMessage.setToCallsign(toCallsign);

                // Set subject if provided
                if (subject != null && !subject.isEmpty()) {
                    replyMessage.setSubject(subject);
                }

                replyMessage.setContent(content);
                replyMessage.setType("private");
                replyMessage.setPriority("normal");
                replyMessage.setTtl(604800); // 7 days
                replyMessage.setTimestamp(timestamp / 1000);

                // Set threading fields
                // Thread ID is the ID of the first message in the conversation
                String threadId = (message.getThreadId() != null && !message.getThreadId().isEmpty())
                    ? message.getThreadId()
                    : message.getId();
                replyMessage.setThreadId(threadId);

                // In-reply-to is the ID of the last message in the thread
                RelayMessage lastMessage = threadMessages.get(threadMessages.size() - 1);
                replyMessage.setInReplyTo(lastMessage.getId());

                // Append reply to the existing message file (keeping conversation together)
                boolean appended = RelayMessage.appendReplyToFile(messageFile, replyMessage);

                if (appended) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            Toast.makeText(requireContext(),
                                "Reply added to conversation",
                                Toast.LENGTH_SHORT).show();

                            // Reload the message to show the updated thread
                            loadMessage();
                        });
                    }

                    Log.d(TAG, "Appended reply to thread: " + replyMessageId);
                } else {
                    // Fallback: Create new message file if append failed
                    java.io.File outboxDir = folderManager.getRelayOutboxDir(toCallsign);
                    java.io.File newMessageFile = new java.io.File(outboxDir, replyMessageId + ".md");

                    if (!outboxDir.exists()) {
                        outboxDir.mkdirs();
                    }

                    java.nio.file.Files.write(
                        newMessageFile.toPath(),
                        replyMessage.toMarkdown().getBytes(java.nio.charset.StandardCharsets.UTF_8)
                    );

                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            Toast.makeText(requireContext(),
                                "Reply sent to outbox (new message)",
                                Toast.LENGTH_SHORT).show();

                            if (getActivity() != null) {
                                getActivity().getSupportFragmentManager().popBackStack();
                            }
                        });
                    }

                    Log.d(TAG, "Created new reply message: " + replyMessageId);
                }

            } catch (Exception e) {
                Log.e(TAG, "Error sending reply: " + e.getMessage());
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(),
                            "Error sending reply: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                    });
                }
            }
        }).start();
    }

    private void deleteMessage() {
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
            .setTitle("Delete Message")
            .setMessage("Are you sure you want to delete this message? This cannot be undone.")
            .setPositiveButton("Delete", (d, which) -> {
                new Thread(() -> {
                    try {
                        java.io.File messageFile;

                        // Check if we have a direct file path (from grid view)
                        if (messageFilePath != null && !messageFilePath.isEmpty()) {
                            messageFile = new java.io.File(messageFilePath);
                        } else {
                            // Construct path from messageId, folder, and callsign (legacy approach)
                            java.io.File messageDir;
                            if ("inbox".equalsIgnoreCase(folder)) {
                                messageDir = folderManager.getRelayInboxDir(callsign);
                            } else if ("outbox".equalsIgnoreCase(folder)) {
                                messageDir = folderManager.getRelayOutboxDir(callsign);
                            } else if ("sent".equalsIgnoreCase(folder)) {
                                messageDir = folderManager.getRelaySentDir(callsign);
                            } else {
                                Log.e(TAG, "Unknown folder: " + folder);
                                return;
                            }

                            messageFile = new java.io.File(messageDir, messageId + ".md");
                        }

                        boolean deleted = messageFile.delete();

                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                if (deleted) {
                                    Toast.makeText(requireContext(),
                                        "Message deleted",
                                        Toast.LENGTH_SHORT).show();

                                    // Return to previous screen
                                    if (getActivity() != null) {
                                        getActivity().getSupportFragmentManager().popBackStack();
                                    }
                                } else {
                                    Toast.makeText(requireContext(),
                                        "Failed to delete message",
                                        Toast.LENGTH_SHORT).show();
                                }
                            });
                        }

                    } catch (Exception e) {
                        Log.e(TAG, "Error deleting message: " + e.getMessage());
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                Toast.makeText(requireContext(),
                                    "Error deleting message: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                            });
                        }
                    }
                }).start();
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
    }

    @Override
    public void onPause() {
        super.onPause();

        // Show top action bar when leaving
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setTopActionBarVisible(true);
        }
    }
}

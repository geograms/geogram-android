package offgrid.geogram.fragments;

import androidx.appcompat.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import offgrid.geogram.MainActivity;
import offgrid.geogram.R;
import offgrid.geogram.contacts.ContactFolderManager;
import offgrid.geogram.core.Log;
import offgrid.geogram.relay.RelayMessage;

/**
 * Fragment displaying relay messages in a sortable table/grid view.
 * Handles thousands of messages efficiently using RecyclerView.
 */
public class RelayMessagesGridFragment extends Fragment {

    private static final String TAG = "RelayMessagesGridFragment";
    private static final String ARG_FOLDER_TYPE = "folder_type";

    private String folderType; // "inbox", "outbox", or "sent"
    private List<MessageItem> messages;
    private MessageAdapter adapter;
    private ContactFolderManager folderManager;

    private TextView textTitle;
    private TextView textCount;
    private TextView headerPriority;
    private TextView headerDate;
    private TextView headerSender;
    private TextView headerDestination;
    private TextView headerSubject;
    private RecyclerView recyclerView;
    private TextView emptyState;

    private SortColumn currentSortColumn = SortColumn.PRIORITY;
    private boolean sortAscending = false;

    public static RelayMessagesGridFragment newInstance(String folderType) {
        RelayMessagesGridFragment fragment = new RelayMessagesGridFragment();
        Bundle args = new Bundle();
        args.putString(ARG_FOLDER_TYPE, folderType);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            folderType = getArguments().getString(ARG_FOLDER_TYPE, "inbox");
        }
        folderManager = new ContactFolderManager(requireContext());
        messages = new ArrayList<>();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_relay_messages_grid, container, false);

        // Initialize views
        textTitle = view.findViewById(R.id.text_title);
        textCount = view.findViewById(R.id.text_count);
        headerPriority = view.findViewById(R.id.header_priority);
        headerDate = view.findViewById(R.id.header_date);
        headerSender = view.findViewById(R.id.header_sender);
        headerDestination = view.findViewById(R.id.header_destination);
        headerSubject = view.findViewById(R.id.header_subject);
        recyclerView = view.findViewById(R.id.recycler_messages);
        emptyState = view.findViewById(R.id.empty_state);

        // Set title
        textTitle.setText(capitalize(folderType));

        // Back button
        ImageButton btnBack = view.findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().getSupportFragmentManager().popBackStack();
            }
        });

        // Setup RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new MessageAdapter();
        recyclerView.setAdapter(adapter);

        // Setup column header click listeners for sorting
        headerPriority.setOnClickListener(v -> sortBy(SortColumn.PRIORITY));
        headerDate.setOnClickListener(v -> sortBy(SortColumn.DATE));
        headerSender.setOnClickListener(v -> sortBy(SortColumn.SENDER));
        headerDestination.setOnClickListener(v -> sortBy(SortColumn.DESTINATION));
        headerSubject.setOnClickListener(v -> sortBy(SortColumn.SUBJECT));

        // Load messages
        loadMessages();

        return view;
    }

    private void loadMessages() {
        messages.clear();

        try {
            // Load from global relay folder
            File globalFolder = getGlobalRelayFolder(folderType);
            loadMessagesFromFolder(globalFolder, null);

            // Load from all contact folders
            File contactsDir = folderManager.getContactsDir();
            if (contactsDir.exists() && contactsDir.isDirectory()) {
                File[] contactFolders = contactsDir.listFiles(File::isDirectory);
                if (contactFolders != null) {
                    for (File contactFolder : contactFolders) {
                        String callsign = contactFolder.getName();
                        File relayFolder = getContactRelayFolder(callsign);
                        loadMessagesFromFolder(relayFolder, callsign);
                    }
                }
            }

            // Sort by default column (priority)
            sortMessages();

            // Update UI
            textCount.setText(messages.size() + " messages");
            if (messages.isEmpty()) {
                emptyState.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            } else {
                emptyState.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
                adapter.notifyDataSetChanged();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error loading messages: " + e.getMessage());
            Toast.makeText(requireContext(), "Error loading messages", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadMessagesFromFolder(File folder, String contactCallsign) {
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

                // Parse as thread (handles both single messages and threaded conversations)
                List<RelayMessage> threadMessages = RelayMessage.parseThread(markdown);

                if (threadMessages != null && !threadMessages.isEmpty()) {
                    // Use the first message for the grid display
                    // (this represents the conversation thread)
                    MessageItem item = new MessageItem();
                    item.message = threadMessages.get(0);
                    item.file = file;
                    item.contactCallsign = contactCallsign;
                    item.messageCount = threadMessages.size(); // Track thread size
                    messages.add(item);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading message file: " + file.getName());
            }
        }
    }

    private File getGlobalRelayFolder(String type) {
        File relayDir = new File(requireContext().getFilesDir(), "relay");
        return new File(relayDir, type);
    }

    private File getContactRelayFolder(String callsign) {
        if ("inbox".equalsIgnoreCase(folderType)) {
            return folderManager.getRelayInboxDir(callsign);
        } else if ("outbox".equalsIgnoreCase(folderType)) {
            return folderManager.getRelayOutboxDir(callsign);
        } else {
            return folderManager.getRelaySentDir(callsign);
        }
    }

    private void sortBy(SortColumn column) {
        if (currentSortColumn == column) {
            // Toggle sort direction
            sortAscending = !sortAscending;
        } else {
            // New column, default to descending
            currentSortColumn = column;
            sortAscending = false;
        }

        sortMessages();
        updateSortIndicators();
        adapter.notifyDataSetChanged();
    }

    private void sortMessages() {
        Comparator<MessageItem> comparator = null;

        switch (currentSortColumn) {
            case PRIORITY:
                comparator = (m1, m2) -> {
                    int p1 = getPriorityValue(m1.message.getPriority());
                    int p2 = getPriorityValue(m2.message.getPriority());
                    return Integer.compare(p1, p2);
                };
                break;
            case DATE:
                comparator = (m1, m2) -> Long.compare(m1.message.getTimestamp(), m2.message.getTimestamp());
                break;
            case SENDER:
                comparator = (m1, m2) -> {
                    String s1 = m1.message.getFromCallsign() != null ? m1.message.getFromCallsign() : "";
                    String s2 = m2.message.getFromCallsign() != null ? m2.message.getFromCallsign() : "";
                    return s1.compareToIgnoreCase(s2);
                };
                break;
            case DESTINATION:
                comparator = (m1, m2) -> {
                    String d1 = m1.message.getToCallsign() != null ? m1.message.getToCallsign() : "";
                    String d2 = m2.message.getToCallsign() != null ? m2.message.getToCallsign() : "";
                    return d1.compareToIgnoreCase(d2);
                };
                break;
            case SUBJECT:
                comparator = (m1, m2) -> {
                    String sub1 = m1.message.getSubject() != null ? m1.message.getSubject() :
                            (m1.message.getContent() != null ? m1.message.getContent() : "");
                    String sub2 = m2.message.getSubject() != null ? m2.message.getSubject() :
                            (m2.message.getContent() != null ? m2.message.getContent() : "");
                    return sub1.compareToIgnoreCase(sub2);
                };
                break;
        }

        if (comparator != null) {
            if (sortAscending) {
                messages.sort(comparator);
            } else {
                messages.sort(comparator.reversed());
            }
        }
    }

    private int getPriorityValue(String priority) {
        if (priority == null) return 2;
        switch (priority.toLowerCase()) {
            case "urgent":
            case "emergency":
                return 0;
            case "high":
                return 1;
            case "normal":
                return 2;
            case "low":
                return 3;
            default:
                return 2;
        }
    }

    private void updateSortIndicators() {
        String arrow = sortAscending ? " ▲" : " ▼";

        headerPriority.setText(currentSortColumn == SortColumn.PRIORITY ? "Priority" + arrow : "Priority");
        headerDate.setText(currentSortColumn == SortColumn.DATE ? "Date" + arrow : "Date");
        headerSender.setText(currentSortColumn == SortColumn.SENDER ? "Sender" + arrow : "Sender");
        headerDestination.setText(currentSortColumn == SortColumn.DESTINATION ? "Dest" + arrow : "Dest");
        headerSubject.setText(currentSortColumn == SortColumn.SUBJECT ? "Subject" + arrow : "Subject");
    }

    private void showMessageDetails(MessageItem item) {
        // Navigate to message detail fragment
        RelayMessageThreadFragment fragment = new RelayMessageThreadFragment();
        Bundle args = new Bundle();
        args.putString("message_file", item.file.getAbsolutePath());
        args.putString("contact_callsign", item.contactCallsign);
        args.putString("folder_type", folderType);
        fragment.setArguments(args);

        if (getActivity() != null) {
            FragmentTransaction transaction = getActivity().getSupportFragmentManager().beginTransaction();
            transaction.replace(R.id.fragment_container, fragment);
            transaction.addToBackStack(null);
            transaction.commit();
        }
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setTopActionBarVisible(false);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setTopActionBarVisible(true);
        }
    }

    // RecyclerView Adapter
    private class MessageAdapter extends RecyclerView.Adapter<MessageViewHolder> {

        @NonNull
        @Override
        public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_relay_message_row, parent, false);
            return new MessageViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
            MessageItem item = messages.get(position);
            holder.bind(item);
        }

        @Override
        public int getItemCount() {
            return messages.size();
        }
    }

    // ViewHolder for message rows
    private class MessageViewHolder extends RecyclerView.ViewHolder {
        TextView textPriority;
        TextView textDate;
        TextView textSender;
        TextView textDestination;
        TextView textSubject;

        public MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            textPriority = itemView.findViewById(R.id.text_priority);
            textDate = itemView.findViewById(R.id.text_date);
            textSender = itemView.findViewById(R.id.text_sender);
            textDestination = itemView.findViewById(R.id.text_destination);
            textSubject = itemView.findViewById(R.id.text_subject);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    showMessageDetails(messages.get(position));
                }
            });
        }

        public void bind(MessageItem item) {
            RelayMessage message = item.message;

            // Priority
            String priority = message.getPriority() != null ? message.getPriority().toUpperCase() : "NORMAL";
            textPriority.setText(priority);
            switch (priority) {
                case "URGENT":
                case "EMERGENCY":
                    textPriority.setTextColor(0xFFFF5252);
                    break;
                case "HIGH":
                    textPriority.setTextColor(0xFFFFAA00);
                    break;
                case "LOW":
                    textPriority.setTextColor(0xFF888888);
                    break;
                default:
                    textPriority.setTextColor(0xFFCCCCCC);
                    break;
            }

            // Date
            SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd HH:mm", Locale.US);
            String dateStr = dateFormat.format(new Date(message.getTimestamp() * 1000));
            textDate.setText(dateStr);

            // Sender
            String sender = message.getFromCallsign() != null ? message.getFromCallsign() : "Unknown";
            textSender.setText(sender);

            // Destination
            String destination = message.getToCallsign() != null ? message.getToCallsign() : "-";
            textDestination.setText(destination);

            // Subject (or content preview if no subject)
            String subject = message.getSubject();
            if (subject == null || subject.isEmpty()) {
                subject = message.getContent();
                if (subject != null && subject.length() > 50) {
                    subject = subject.substring(0, 50) + "...";
                }
            }

            // Add thread indicator if there are multiple messages
            if (item.messageCount > 1) {
                subject = "(" + item.messageCount + ") " + (subject != null ? subject : "(no content)");
            } else {
                subject = subject != null ? subject : "(no content)";
            }

            textSubject.setText(subject);
        }
    }

    // Helper classes
    private static class MessageItem {
        RelayMessage message;
        File file;
        String contactCallsign;  // null if from global folder
        int messageCount = 1;    // Number of messages in thread
    }

    private enum SortColumn {
        PRIORITY, DATE, SENDER, DESTINATION, SUBJECT
    }
}

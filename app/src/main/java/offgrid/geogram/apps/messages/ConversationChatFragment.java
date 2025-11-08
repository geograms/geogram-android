package offgrid.geogram.apps.messages;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.json.JSONException;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import offgrid.geogram.MainActivity;
import offgrid.geogram.R;
import offgrid.geogram.api.GeogramMessagesAPI;
import offgrid.geogram.core.Central;
import offgrid.geogram.core.Log;
import offgrid.geogram.database.DatabaseConversations;
import offgrid.geogram.settings.SettingsUser;
import offgrid.geogram.util.DateUtils;

/**
 * Fragment for displaying and sending messages in a conversation
 */
public class ConversationChatFragment extends Fragment {

    private static final String TAG = "ConversationChatFragment";
    private static final String ARG_PEER_ID = "peer_id";
    private static final long POLL_INTERVAL_MS = 30000; // 30 seconds

    private String peerId;
    private LinearLayout chatMessageContainer;
    private ScrollView chatScrollView;
    private TextView conversationHeader;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable messagePoller;
    private Map<String, Integer> userColorMap = new HashMap<>();
    private int[] userColors = {
            0xFF4A4A4A, // Dark gray
            0xFF2E7D32, // Green
            0xFF1565C0, // Blue
            0xFF6A1B9A, // Purple
            0xFFD84315  // Orange
    };
    private int nextColorIndex = 0;

    public static ConversationChatFragment newInstance(String peerId) {
        ConversationChatFragment fragment = new ConversationChatFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PEER_ID, peerId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            peerId = getArguments().getString(ARG_PEER_ID);
        }
        Log.d(TAG, "onCreate: Created ConversationChatFragment for peer: " + peerId);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_conversation_chat, container, false);

        // Initialize views
        conversationHeader = view.findViewById(R.id.conversation_header);
        chatMessageContainer = view.findViewById(R.id.chat_message_container);
        chatScrollView = view.findViewById(R.id.chat_scroll_view);
        EditText messageInput = view.findViewById(R.id.message_input);
        ImageButton btnSend = view.findViewById(R.id.btn_send);

        // Set conversation header
        conversationHeader.setText(peerId);

        // Setup send button
        btnSend.setOnClickListener(v -> {
            String message = messageInput.getText().toString().trim();
            if (!message.isEmpty()) {
                sendMessage(message);
                messageInput.setText("");
            }
        });

        // Load initial messages
        loadMessages();

        // Setup polling
        messagePoller = new Runnable() {
            @Override
            public void run() {
                if (isAdded() && getContext() != null) {
                    loadMessages();
                    handler.postDelayed(this, POLL_INTERVAL_MS);
                }
            }
        };

        return view;
    }

    private void sendMessage(String message) {
        SettingsUser settings = Central.getInstance().getSettings();
        String callsign = settings.getCallsign();
        String nsec = settings.getNsec();
        String npub = settings.getNpub();

        if (callsign == null || nsec == null || npub == null) {
            Toast.makeText(getContext(), "Please configure your NOSTR identity", Toast.LENGTH_SHORT).show();
            return;
        }

        // Add message optimistically to UI
        ConversationMessage tempMessage = new ConversationMessage("-- " + callsign, message, callsign);
        addUserMessage(tempMessage);

        // Send in background (send raw message, server will format it)
        new Thread(() -> {
            try {
                boolean success = GeogramMessagesAPI.sendMessage(callsign, peerId, message, nsec, npub);
                if (success) {
                    Log.d(TAG, "Message sent successfully");
                    // Refresh messages after a short delay
                    handler.postDelayed(this::loadMessages, 1000);
                } else {
                    handler.post(() -> Toast.makeText(getContext(), "Failed to send message", Toast.LENGTH_SHORT).show());
                }
            } catch (IOException | JSONException e) {
                Log.e(TAG, "Error sending message: " + e.getMessage());
                handler.post(() -> Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void loadMessages() {
        SettingsUser settings = Central.getInstance().getSettings();
        String callsign = settings.getCallsign();
        String nsec = settings.getNsec();
        String npub = settings.getNpub();

        if (callsign == null || nsec == null || npub == null) {
            Log.e(TAG, "Cannot load messages - missing credentials");
            return;
        }

        Log.d(TAG, "Loading messages for conversation: " + peerId);

        new Thread(() -> {
            // First, load cached messages immediately
            String cachedMarkdown = DatabaseConversations.getInstance().loadConversationMessages(peerId);
            if (cachedMarkdown != null && !cachedMarkdown.isEmpty()) {
                List<ConversationMessage> cachedMessages = MarkdownParser.parseConversation(cachedMarkdown, callsign);
                handler.post(() -> {
                    if (isAdded() && getContext() != null) {
                        Log.d(TAG, "Displaying " + cachedMessages.size() + " cached messages");
                        displayMessages(cachedMessages);
                    }
                });
            }

            // Then try to fetch fresh data from API
            try {
                // Fetch messages from API
                Log.d(TAG, "Fetching messages from API for peer: " + peerId);
                String markdown = GeogramMessagesAPI.getConversationMessages(callsign, peerId, nsec, npub);

                Log.d(TAG, "Received markdown, length: " + markdown.length());

                // Save to cache
                DatabaseConversations.getInstance().saveConversationMessages(peerId, markdown);

                // Parse messages
                List<ConversationMessage> messages = MarkdownParser.parseConversation(markdown, callsign);

                Log.d(TAG, "Parsed " + messages.size() + " messages from markdown");

                // Log each message
                for (int i = 0; i < messages.size(); i++) {
                    ConversationMessage msg = messages.get(i);
                    Log.d(TAG, "Message " + i + ": author=" + msg.getAuthor() + ", content=" + msg.getContent());
                }

                // Update UI on main thread
                handler.post(() -> {
                    if (isAdded() && getContext() != null) {
                        Log.d(TAG, "Displaying " + messages.size() + " messages in UI");
                        displayMessages(messages);
                    } else {
                        Log.w(TAG, "Cannot display messages - fragment not added or context null");
                    }
                });

            } catch (IOException e) {
                Log.e(TAG, "IOException loading messages: " + e.getMessage());
                e.printStackTrace();
                handler.post(() -> {
                    if (getContext() != null) {
                        // Only show error if we don't have cached data
                        if (cachedMarkdown == null || cachedMarkdown.isEmpty()) {
                            Toast.makeText(getContext(), "Offline - No cached messages", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(getContext(), "Offline - Showing cached messages", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            } catch (JSONException e) {
                Log.e(TAG, "JSONException loading messages: " + e.getMessage());
                e.printStackTrace();
                // Only show error if we have cached data or if it's a real parsing error
                // Empty conversations will just show no messages, which is fine
                handler.post(() -> {
                    if (getContext() != null) {
                        // Don't show error toast for empty conversations
                        Log.d(TAG, "Conversation may be empty - no error shown to user");
                    }
                });
            }
        }).start();
    }

    private void displayMessages(List<ConversationMessage> messages) {
        if (chatMessageContainer == null) {
            Log.w(TAG, "displayMessages: chatMessageContainer is null");
            return;
        }

        Log.d(TAG, "displayMessages: Clearing existing messages and adding " + messages.size() + " new messages");

        // Clear existing messages
        chatMessageContainer.removeAllViews();

        // Add each message
        for (ConversationMessage message : messages) {
            Log.d(TAG, "displayMessages: Adding message from " + message.getAuthor());
            addMessageView(message);
        }

        Log.d(TAG, "displayMessages: All messages added, scrolling to bottom");

        // Scroll to bottom
        chatScrollView.post(() -> chatScrollView.fullScroll(View.FOCUS_DOWN));
    }

    private void addUserMessage(ConversationMessage message) {
        if (chatMessageContainer == null) {
            return;
        }

        addMessageView(message);
        chatScrollView.post(() -> chatScrollView.fullScroll(View.FOCUS_DOWN));
    }

    private void addMessageView(ConversationMessage message) {
        View messageView;
        if (message.isFromSelf()) {
            messageView = createUserMessageView(message);
        } else {
            messageView = createReceivedMessageView(message);
        }
        chatMessageContainer.addView(messageView);
    }

    private View createUserMessageView(ConversationMessage message) {
        LinearLayout messageLayout = new LinearLayout(getContext());
        messageLayout.setOrientation(LinearLayout.VERTICAL);
        messageLayout.setPadding(0, 0, 0, 16);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        messageLayout.setLayoutParams(layoutParams);

        // Message bubble
        LinearLayout bubbleLayout = new LinearLayout(getContext());
        bubbleLayout.setOrientation(LinearLayout.VERTICAL);
        bubbleLayout.setPadding(16, 12, 16, 12);

        GradientDrawable bubble = new GradientDrawable();
        bubble.setColor(0xFF1E88E5); // Blue for user messages
        bubble.setCornerRadius(24);
        bubbleLayout.setBackground(bubble);

        LinearLayout.LayoutParams bubbleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        bubbleParams.setMargins(100, 0, 0, 0);
        bubbleParams.gravity = android.view.Gravity.END;
        bubbleLayout.setLayoutParams(bubbleParams);

        // Message text
        TextView messageText = new TextView(getContext());
        messageText.setText(message.getContent());
        messageText.setTextColor(0xFFFFFFFF);
        messageText.setTextSize(16);
        bubbleLayout.addView(messageText);

        // Timestamp
        TextView timestamp = new TextView(getContext());
        timestamp.setText(DateUtils.formatTimestamp(message.getTimestamp()));
        timestamp.setTextColor(0xFFBBBBBB);
        timestamp.setTextSize(12);
        timestamp.setPadding(0, 4, 0, 0);
        bubbleLayout.addView(timestamp);

        messageLayout.addView(bubbleLayout);
        return messageLayout;
    }

    private View createReceivedMessageView(ConversationMessage message) {
        LinearLayout messageLayout = new LinearLayout(getContext());
        messageLayout.setOrientation(LinearLayout.VERTICAL);
        messageLayout.setPadding(0, 0, 0, 16);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        messageLayout.setLayoutParams(layoutParams);

        // Author name
        TextView authorText = new TextView(getContext());
        authorText.setText(message.getAuthor());
        authorText.setTextColor(0xFFAAAAAA);
        authorText.setTextSize(12);
        authorText.setPadding(16, 0, 0, 4);
        messageLayout.addView(authorText);

        // Message bubble
        LinearLayout bubbleLayout = new LinearLayout(getContext());
        bubbleLayout.setOrientation(LinearLayout.VERTICAL);
        bubbleLayout.setPadding(16, 12, 16, 12);

        // Get consistent color for this user
        int color = getUserColor(message.getAuthor());
        GradientDrawable bubble = new GradientDrawable();
        bubble.setColor(color);
        bubble.setCornerRadius(24);
        bubbleLayout.setBackground(bubble);

        LinearLayout.LayoutParams bubbleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        bubbleParams.setMargins(0, 0, 100, 0);
        bubbleParams.gravity = android.view.Gravity.START;
        bubbleLayout.setLayoutParams(bubbleParams);

        // Message text
        TextView messageText = new TextView(getContext());
        messageText.setText(message.getContent());
        messageText.setTextColor(0xFFFFFFFF);
        messageText.setTextSize(16);
        bubbleLayout.addView(messageText);

        // Timestamp
        TextView timestamp = new TextView(getContext());
        timestamp.setText(DateUtils.formatTimestamp(message.getTimestamp()));
        timestamp.setTextColor(0xFFBBBBBB);
        timestamp.setTextSize(12);
        timestamp.setPadding(0, 4, 0, 0);
        bubbleLayout.addView(timestamp);

        messageLayout.addView(bubbleLayout);
        return messageLayout;
    }

    private int getUserColor(String author) {
        if (!userColorMap.containsKey(author)) {
            userColorMap.put(author, userColors[nextColorIndex % userColors.length]);
            nextColorIndex++;
        }
        return userColorMap.get(author);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Hide top action bar for chat screens
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setTopActionBarVisible(false);
        }
        // Start polling for new messages
        if (messagePoller != null) {
            handler.post(messagePoller);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // Stop polling when fragment is not visible
        if (messagePoller != null) {
            handler.removeCallbacks(messagePoller);
        }
    }
}

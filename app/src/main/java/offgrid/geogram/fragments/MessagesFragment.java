package offgrid.geogram.fragments;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import offgrid.geogram.MainActivity;
import offgrid.geogram.R;
import offgrid.geogram.api.GeogramMessagesAPI;
import offgrid.geogram.apps.messages.Conversation;
import offgrid.geogram.apps.messages.ConversationAdapter;
import offgrid.geogram.apps.messages.ConversationChatFragment;
import offgrid.geogram.core.Central;
import offgrid.geogram.core.Log;
import offgrid.geogram.database.DatabaseConversations;
import offgrid.geogram.database.DatabaseMessages;
import offgrid.geogram.settings.SettingsUser;

public class MessagesFragment extends Fragment {

    public static String TAG = "MessagesFragment";

    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView conversationsRecyclerView;
    private TextView emptyState;
    private ConversationAdapter adapter;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private static final long AUTO_REFRESH_INTERVAL_MS = 30000; // 30 seconds
    private Runnable autoRefreshRunnable;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_messages, container, false);

        // Initialize views
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout);
        conversationsRecyclerView = view.findViewById(R.id.conversations_recycler_view);
        emptyState = view.findViewById(R.id.empty_state);

        // Setup RecyclerView
        conversationsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ConversationAdapter(new ArrayList<>(), this::onConversationClick);
        conversationsRecyclerView.setAdapter(adapter);

        // Setup swipe refresh
        swipeRefreshLayout.setOnRefreshListener(this::loadConversations);

        // Load conversations initially
        loadConversations();

        return view;
    }

    private void onConversationClick(Conversation conversation) {
        // Navigate to conversation chat
        if (getActivity() != null) {
            ConversationChatFragment fragment = ConversationChatFragment.newInstance(conversation.getPeerId());
            getActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .addToBackStack(null)
                    .commit();
        }
    }

    private void loadConversations() {
        // Show loading indicator
        swipeRefreshLayout.setRefreshing(true);

        // Get user credentials
        SettingsUser settings = Central.getInstance().getSettings();
        String callsign = settings.getCallsign();
        String nsec = settings.getNsec();
        String npub = settings.getNpub();

        // Check if user has credentials
        if (callsign == null || nsec == null || npub == null) {
            handler.post(() -> {
                swipeRefreshLayout.setRefreshing(false);
                emptyState.setText("Please configure your NOSTR identity in Settings");
                emptyState.setVisibility(View.VISIBLE);
                conversationsRecyclerView.setVisibility(View.GONE);
            });
            return;
        }

        // Load conversations in background thread
        new Thread(() -> {
            // First, load cached conversations immediately and enrich with current stats
            List<Conversation> cachedConversations = DatabaseConversations.getInstance().loadConversationList();
            if (!cachedConversations.isEmpty()) {
                // Enrich cached conversations with latest message stats
                for (Conversation conv : cachedConversations) {
                    enrichConversationWithStats(conv);
                }
                // Sort by most recent
                cachedConversations.sort((c1, c2) -> Long.compare(c2.getLastMessageTime(), c1.getLastMessageTime()));

                handler.post(() -> {
                    emptyState.setVisibility(View.GONE);
                    conversationsRecyclerView.setVisibility(View.VISIBLE);
                    adapter.updateConversations(cachedConversations);
                    Log.d(TAG, "Displaying " + cachedConversations.size() + " cached conversations");
                });
            }

            // Then try to fetch fresh data from API
            try {
                // Fetch conversation list from API
                List<String> peerIds = GeogramMessagesAPI.getConversationList(callsign, nsec, npub);

                // Convert to Conversation objects and populate stats from local database
                List<Conversation> conversations = new ArrayList<>();
                for (String peerId : peerIds) {
                    Conversation conv = new Conversation(peerId);
                    // Populate with message statistics from local database
                    enrichConversationWithStats(conv);
                    conversations.add(conv);
                }

                // Sort by most recent message time (newest first)
                conversations.sort((c1, c2) -> Long.compare(c2.getLastMessageTime(), c1.getLastMessageTime()));

                // Save to cache
                DatabaseConversations.getInstance().saveConversationList(conversations);

                // Update UI on main thread
                handler.post(() -> {
                    swipeRefreshLayout.setRefreshing(false);

                    if (conversations.isEmpty()) {
                        emptyState.setVisibility(View.VISIBLE);
                        conversationsRecyclerView.setVisibility(View.GONE);
                    } else {
                        emptyState.setVisibility(View.GONE);
                        conversationsRecyclerView.setVisibility(View.VISIBLE);
                        adapter.updateConversations(conversations);
                    }
                });

                Log.d(TAG, "Loaded " + conversations.size() + " conversations from API");

            } catch (IOException e) {
                Log.e(TAG, "Network error loading conversations: " + e.getMessage());
                handler.post(() -> {
                    swipeRefreshLayout.setRefreshing(false);
                    // Only show error if we don't have cached data
                    if (cachedConversations.isEmpty()) {
                        Toast.makeText(getContext(), "Offline - No cached conversations", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getContext(), "Offline - Showing cached conversations", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (JSONException e) {
                Log.e(TAG, "JSON error loading conversations: " + e.getMessage());
                handler.post(() -> {
                    swipeRefreshLayout.setRefreshing(false);
                    if (cachedConversations.isEmpty()) {
                        Toast.makeText(getContext(), "Error parsing response", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Show top action bar for main screens
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setTopActionBarVisible(true);
        }
        // Refresh conversations when returning to this screen
        if (swipeRefreshLayout != null) {
            loadConversations();
        }
        // Start auto-refresh
        startAutoRefresh();
    }

    @Override
    public void onPause() {
        super.onPause();
        // Stop auto-refresh to save resources
        stopAutoRefresh();
    }

    private void startAutoRefresh() {
        if (autoRefreshRunnable == null) {
            autoRefreshRunnable = new Runnable() {
                @Override
                public void run() {
                    if (isAdded() && swipeRefreshLayout != null) {
                        Log.d(TAG, "Auto-refreshing conversations");
                        loadConversations();
                        // Schedule next refresh
                        handler.postDelayed(this, AUTO_REFRESH_INTERVAL_MS);
                    }
                }
            };
        }
        // Start the auto-refresh cycle
        handler.postDelayed(autoRefreshRunnable, AUTO_REFRESH_INTERVAL_MS);
    }

    private void stopAutoRefresh() {
        if (autoRefreshRunnable != null) {
            handler.removeCallbacks(autoRefreshRunnable);
        }
    }

    /**
     * Enrich a conversation with message statistics from the local database
     */
    private void enrichConversationWithStats(Conversation conversation) {
        DatabaseMessages.ConversationStats stats =
            DatabaseMessages.getInstance().getConversationStats(conversation.getPeerId());

        conversation.setMessageCount(stats.totalMessages);
        conversation.setUnreadCount(stats.unreadCount);

        if (stats.lastMessage != null) {
            conversation.setLastMessage(stats.lastMessage.getMessage());
            conversation.setLastMessageTime(stats.lastMessage.getTimestamp());
        } else {
            conversation.setLastMessage("");
            conversation.setLastMessageTime(0);
        }
    }
}

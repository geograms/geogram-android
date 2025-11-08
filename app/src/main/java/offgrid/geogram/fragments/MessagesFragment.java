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
import offgrid.geogram.settings.SettingsUser;

public class MessagesFragment extends Fragment {

    public static String TAG = "MessagesFragment";

    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView conversationsRecyclerView;
    private TextView emptyState;
    private ConversationAdapter adapter;
    private final Handler handler = new Handler(Looper.getMainLooper());

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
            // First, load cached conversations immediately
            List<Conversation> cachedConversations = DatabaseConversations.getInstance().loadConversationList();
            if (!cachedConversations.isEmpty()) {
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

                // Convert to Conversation objects
                List<Conversation> conversations = new ArrayList<>();
                for (String peerId : peerIds) {
                    Conversation conv = new Conversation(peerId);
                    conversations.add(conv);
                }

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
    }
}

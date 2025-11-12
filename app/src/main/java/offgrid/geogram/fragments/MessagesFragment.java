package offgrid.geogram.fragments;

import androidx.appcompat.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import offgrid.geogram.MainActivity;
import offgrid.geogram.R;
import offgrid.geogram.api.GeogramMessagesAPI;
import offgrid.geogram.api.ProfileAPI;
import offgrid.geogram.apps.messages.Conversation;
import offgrid.geogram.apps.messages.ConversationAdapter;
import offgrid.geogram.contacts.ContactFolderManager;
import offgrid.geogram.contacts.ContactProfile;
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
    private FloatingActionButton fabAddContact;
    private ConversationAdapter adapter;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private static final long AUTO_REFRESH_INTERVAL_MS = 30000; // 30 seconds
    private Runnable autoRefreshRunnable;
    private ContactFolderManager folderManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_messages, container, false);

        // Initialize folder manager
        folderManager = new ContactFolderManager(requireContext());

        // Initialize views
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout);
        conversationsRecyclerView = view.findViewById(R.id.conversations_recycler_view);
        emptyState = view.findViewById(R.id.empty_state);
        fabAddContact = view.findViewById(R.id.fab_add_contact);

        // Setup RecyclerView
        conversationsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ConversationAdapter(new ArrayList<>(), this::onConversationClick);
        conversationsRecyclerView.setAdapter(adapter);

        // Setup swipe refresh
        swipeRefreshLayout.setOnRefreshListener(this::loadConversations);

        // Setup FAB
        fabAddContact.setOnClickListener(v -> showAddContactDialog());

        // Load conversations initially
        loadConversations();

        return view;
    }

    private void onConversationClick(Conversation conversation) {
        // Navigate to contact detail (folder view)
        if (getActivity() != null) {
            ContactDetailFragment fragment = ContactDetailFragment.newInstance(conversation.getPeerId());
            getActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .addToBackStack(null)
                    .commit();
        }
    }

    /**
     * Show dialog to add a new contact by callsign.
     */
    private void showAddContactDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Add Contact");
        builder.setMessage("Enter the callsign of the contact you want to add:");

        // Input field
        final EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        input.setHint("e.g., CR7BBQ");

        // Force uppercase and only allow valid callsign characters (A-Z, 0-9, -)
        input.setFilters(new android.text.InputFilter[] {
            new android.text.InputFilter.AllCaps(),
            new android.text.InputFilter.LengthFilter(9),
            (source, start, end, dest, dstart, dend) -> {
                // Only allow A-Z, 0-9, and dash
                for (int i = start; i < end; i++) {
                    char c = source.charAt(i);
                    if (!Character.isLetterOrDigit(c) && c != '-') {
                        return "";
                    }
                }
                return null;
            }
        });

        builder.setView(input);

        // Add button
        builder.setPositiveButton("Add", (dialog, which) -> {
            String callsign = input.getText().toString().trim().toUpperCase();

            if (callsign.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter a callsign", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!ContactFolderManager.isValidCallsign(callsign)) {
                Toast.makeText(requireContext(), "Invalid callsign format", Toast.LENGTH_SHORT).show();
                return;
            }

            // Fetch profile from server
            fetchAndAddContact(callsign);
        });

        // Cancel button
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        AlertDialog dialog = builder.show();

        // Set button text colors explicitly to white for better readability
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
     * Fetch profile from server and add as contact.
     * If profile is not found on server, creates a basic profile locally.
     */
    private void fetchAndAddContact(String callsign) {
        // Show progress
        Toast.makeText(requireContext(), "Fetching profile for " + callsign + "...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                // Try to fetch profile from server
                ContactProfile profile = ProfileAPI.fetchProfile(callsign);
                final boolean foundOnServer = (profile != null);

                // If not found on server, create a basic profile
                if (profile == null) {
                    Log.d(TAG, "Profile not found on server for " + callsign + ", creating basic profile");
                    profile = new ContactProfile();
                    profile.setCallsign(callsign);
                    profile.setFirstTimeSeen(System.currentTimeMillis());
                    profile.setLastUpdated(System.currentTimeMillis());
                }

                final ContactProfile finalProfile = profile;

                handler.post(() -> {
                    // Create contact folder structure
                    boolean created = folderManager.ensureContactStructure(callsign);

                    if (!created) {
                        Toast.makeText(requireContext(),
                            "Failed to create contact folder",
                            Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Save profile
                    boolean saved = folderManager.saveProfile(callsign, finalProfile);

                    if (!saved) {
                        Toast.makeText(requireContext(),
                            "Failed to save profile",
                            Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Show appropriate message
                    String message;
                    if (foundOnServer) {
                        message = "Added contact: " + callsign + " (from server)";
                    } else {
                        message = "Added contact: " + callsign + " (not on server)";
                    }

                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                    Log.d(TAG, message);

                    // Navigate to contact detail
                    if (getActivity() != null) {
                        ContactDetailFragment fragment = ContactDetailFragment.newInstance(callsign);
                        getActivity().getSupportFragmentManager()
                            .beginTransaction()
                            .replace(R.id.fragment_container, fragment)
                            .addToBackStack(null)
                            .commit();
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Error adding contact: " + e.getMessage());
                handler.post(() -> {
                    Toast.makeText(requireContext(),
                        "Error adding contact: " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
                });
            }
        }).start();
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

package offgrid.geogram.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import offgrid.geogram.MainActivity;
import offgrid.geogram.R;
import offgrid.geogram.apps.messages.ConversationChatFragment;
import offgrid.geogram.contacts.ContactFolderManager;
import offgrid.geogram.contacts.ContactProfile;
import offgrid.geogram.core.Log;

/**
 * Shows detailed view of a contact with tabs for Chat and Relay Messages.
 *
 * This fragment acts as a "folder view" for a contact, providing access to:
 * - Chat messages (existing ConversationChatFragment)
 * - Relay messages (new RelayMessagesListFragment)
 *
 * The fragment intelligently hides the tab layout if only one type of content exists.
 */
public class ContactDetailFragment extends Fragment {

    private static final String TAG = "ContactDetailFragment";
    private static final String ARG_CALLSIGN = "callsign";
    private static final String ARG_DEFAULT_TAB = "default_tab";

    private String callsign;
    private int defaultTab = 0;

    private TextView textContactName;
    private TextView textContactCallsign;
    private TabLayout tabLayout;
    private ViewPager2 viewPager;

    private ContactFolderManager folderManager;
    private ContactProfile profile;

    public static ContactDetailFragment newInstance(String callsign) {
        return newInstance(callsign, 0);
    }

    public static ContactDetailFragment newInstance(String callsign, int defaultTab) {
        ContactDetailFragment fragment = new ContactDetailFragment();
        Bundle args = new Bundle();
        args.putString(ARG_CALLSIGN, callsign);
        args.putInt(ARG_DEFAULT_TAB, defaultTab);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            callsign = getArguments().getString(ARG_CALLSIGN);
            defaultTab = getArguments().getInt(ARG_DEFAULT_TAB, 0);
        }

        folderManager = new ContactFolderManager(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_contact_detail, container, false);

        // Initialize views
        textContactName = view.findViewById(R.id.text_contact_name);
        textContactCallsign = view.findViewById(R.id.text_contact_callsign);
        tabLayout = view.findViewById(R.id.tab_layout);
        viewPager = view.findViewById(R.id.view_pager);

        // Back button
        ImageButton btnBack = view.findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().getSupportFragmentManager().popBackStack();
            }
        });

        // Load profile
        loadProfile();

        // Setup tabs and ViewPager
        setupTabsAndPager();

        return view;
    }

    private void loadProfile() {
        profile = folderManager.getOrCreateProfile(callsign);

        // Update header
        if (profile.getName() != null && !profile.getName().isEmpty()) {
            textContactName.setText(profile.getName());
        } else {
            textContactName.setText(callsign);
        }

        textContactCallsign.setText(callsign);
    }

    private void setupTabsAndPager() {
        // Create adapter
        ContactTabAdapter adapter = new ContactTabAdapter(this);
        viewPager.setAdapter(adapter);

        // Enable user input for swiping between tabs
        viewPager.setUserInputEnabled(true);

        // Configure TabLayout with ViewPager2
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            if (position == 0) {
                tab.setText("Chat");
            } else {
                tab.setText("Relay Messages");
            }
        }).attach();

        // Set default tab
        if (defaultTab >= 0 && defaultTab < 2) {
            viewPager.setCurrentItem(defaultTab, false);
        }

        // Check content type to potentially hide tabs
        checkContentTypeAndAdjustUI();
    }

    /**
     * Check what type of content exists and adjust UI accordingly.
     * Always show tabs so users know they can switch between chat and relay messages.
     */
    private void checkContentTypeAndAdjustUI() {
        new Thread(() -> {
            ContactFolderManager.ContactContentType contentType = folderManager.getContentType(callsign);

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    // Always show tabs so users can see both options
                    tabLayout.setVisibility(View.VISIBLE);

                    // Set default tab based on content
                    switch (contentType) {
                        case RELAY_ONLY:
                            // Start on relay messages tab if only relay exists
                            viewPager.setCurrentItem(1, false);
                            Log.d(TAG, "Contact has only relay messages - showing relay tab");
                            break;

                        case CHAT_ONLY:
                        case BOTH:
                        case NONE:
                        default:
                            // Default to chat tab
                            Log.d(TAG, "Contact content type: " + contentType);
                            break;
                    }
                });
            }
        }).start();
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

    /**
     * ViewPager2 adapter for contact tabs.
     */
    private class ContactTabAdapter extends FragmentStateAdapter {

        public ContactTabAdapter(@NonNull Fragment fragment) {
            super(fragment);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            if (position == 0) {
                // Chat tab - use existing ConversationChatFragment in embedded mode
                return ConversationChatFragment.newInstance(callsign, true);
            } else {
                // Relay Messages tab - use new RelayMessagesListFragment
                return RelayMessagesListFragment.newInstance(callsign);
            }
        }

        @Override
        public int getItemCount() {
            return 2; // Chat and Relay Messages
        }
    }
}

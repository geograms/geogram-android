package offgrid.geogram.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import offgrid.geogram.R;
import offgrid.geogram.core.Log;

public class DebugFragment extends Fragment {

    public DebugFragment() {
        // Required empty public constructor
    }

    /**
     * Update log messages in the LogTabFragment.
     */
    public void updateLogMessages() {
        Fragment fragment = getChildFragmentManager().findFragmentByTag("f0"); // "f0" for LogTabFragment
        if (fragment instanceof LogTabFragment) {
            ((LogTabFragment) fragment).logUpdateAllMessages();
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_debug, container, false);

        TabLayout tabLayout = view.findViewById(R.id.tab_layout);
        ViewPager2 viewPager = view.findViewById(R.id.view_pager);
        ImageButton btnBack = view.findViewById(R.id.btn_back);

        viewPager.setAdapter(new DebugFragmentAdapter(this));
        viewPager.setOffscreenPageLimit(2); // Prevent fragment destruction on tab switch

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            if (position == 0) {
                tab.setText(R.string.log);
            } else {
                tab.setText(R.string.options);
            }
        }).attach();

        btnBack.setOnClickListener(v -> requireActivity().onBackPressed());

        // Display existing log messages when the fragment is opened
        updateLogMessages();

        // Register the log listener to receive real-time updates
        Log.setLogListener(message -> {
            if (isAdded()) {
                updateLogMessages();
            }
        });

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Unregister the log listener when the fragment is destroyed
        Log.setLogListener(null);
    }

    /**
     * Adapter for managing fragments in ViewPager2.
     */
    private static class DebugFragmentAdapter extends FragmentStateAdapter {

        public DebugFragmentAdapter(@NonNull Fragment fragment) {
            super(fragment);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            if (position == 0) {
                return new LogTabFragment(); // Log tab fragment
            } else {
                return new OptionsTabFragment(); // Options tab fragment
            }
        }

        @Override
        public long getItemId(int position) {
            return position; // Return stable ID for each tab
        }

        @Override
        public boolean containsItem(long itemId) {
            return itemId >= 0 && itemId < getItemCount(); // Validate tab existence
        }

        @Override
        public int getItemCount() {
            return 2; // Number of tabs
        }
    }
}

package offgrid.geogram.fragments;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

import offgrid.geogram.MainActivity;
import offgrid.geogram.R;
import offgrid.geogram.adapters.CollectionAdapter;
import offgrid.geogram.models.Collection;
import offgrid.geogram.models.CollectionFile;
import offgrid.geogram.util.CollectionLoader;

public class CollectionsFragment extends Fragment {

    private RecyclerView recyclerView;
    private CollectionAdapter adapter;
    private SwipeRefreshLayout swipeRefresh;
    private LinearLayout emptyState;
    private EditText searchInput;
    private FloatingActionButton fabAddCollection;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private List<Collection> allCollections = new ArrayList<>();
    private List<Collection> filteredCollections = new ArrayList<>();

    private BroadcastReceiver collectionAddedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            android.util.Log.i("CollectionsFragment", "Received COLLECTION_ADDED broadcast");
            loadCollections();
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_collections, container, false);

        recyclerView = view.findViewById(R.id.collections_recycler);
        emptyState = view.findViewById(R.id.empty_state);
        swipeRefresh = view.findViewById(R.id.swipe_refresh_layout);
        searchInput = view.findViewById(R.id.search_input);
        fabAddCollection = view.findViewById(R.id.fab_add_collection);

        setupRecyclerView();
        setupSearch();
        setupSwipeRefresh();
        setupFAB();

        return view;
    }

    private void setupFAB() {
        fabAddCollection.setOnClickListener(v -> {
            FragmentManager fragmentManager = getParentFragmentManager();
            FragmentTransaction transaction = fragmentManager.beginTransaction();
            transaction.replace(R.id.fragment_container, new CreateCollectionFragment());
            transaction.addToBackStack(null);
            transaction.commit();
        });
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        loadCollections();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Show top action bar for main screens
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setTopActionBarVisible(true);
        }

        // Register broadcast receiver for collection updates
        IntentFilter filter = new IntentFilter("offgrid.geogram.COLLECTION_ADDED");
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(collectionAddedReceiver, filter);

        // Reload collections when returning to this screen
        loadCollections();
    }

    @Override
    public void onPause() {
        super.onPause();
        // Unregister broadcast receiver
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(requireContext())
            .unregisterReceiver(collectionAddedReceiver);
    }

    private void setupRecyclerView() {
        // Use grid layout with 1 column (can be changed to 2 for tablets)
        GridLayoutManager layoutManager = new GridLayoutManager(getContext(), 1);
        recyclerView.setLayoutManager(layoutManager);

        adapter = new CollectionAdapter(this::onCollectionClick);
        recyclerView.setAdapter(adapter);
    }

    private void setupSearch() {
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                filterCollections(s.toString());
            }
        });
    }

    private void setupSwipeRefresh() {
        swipeRefresh.setOnRefreshListener(this::loadCollections);
    }

    private void loadCollections() {
        // Load on background thread
        new Thread(() -> {
            List<Collection> collections = CollectionLoader.loadCollectionsFromAppStorage(requireContext());

            handler.post(() -> {
                allCollections = collections;
                filteredCollections = new ArrayList<>(allCollections);
                updateUI();
                swipeRefresh.setRefreshing(false);
            });
        }).start();
    }

    private List<Collection> createDummyCollections_REMOVED() {
        List<Collection> collections = new ArrayList<>();

        // Collection 1: Classic Sci-Fi Books
        Collection sciFi = new Collection(
                "npub1scifi123",
                "Classic Science Fiction Books",
                "A curated collection of timeless science fiction literature"
        );
        sciFi.setFilesCount(4);
        sciFi.setTotalSize(4 * 1024 * 1024); // 4 MB
        sciFi.setUpdated("2025-11-13");

        // Add some files to the collection
        CollectionFile booksDir = new CollectionFile("books", "books", CollectionFile.FileType.DIRECTORY);
        sciFi.addFile(booksDir);

        CollectionFile foundation = new CollectionFile("books/foundation.md", "Foundation", CollectionFile.FileType.FILE);
        foundation.setSize(1456);
        foundation.setDescription("The first book in Asimov's Foundation series");
        foundation.setMimeType("text/markdown");
        sciFi.addFile(foundation);

        CollectionFile dune = new CollectionFile("books/dune.md", "Dune", CollectionFile.FileType.FILE);
        dune.setSize(1523);
        dune.setDescription("Frank Herbert's epic tale of desert planet Arrakis");
        dune.setMimeType("text/markdown");
        sciFi.addFile(dune);

        CollectionFile neuromancer = new CollectionFile("books/neuromancer.md", "Neuromancer", CollectionFile.FileType.FILE);
        neuromancer.setSize(1398);
        neuromancer.setDescription("William Gibson's groundbreaking cyberpunk novel");
        neuromancer.setMimeType("text/markdown");
        sciFi.addFile(neuromancer);

        collections.add(sciFi);

        // Collection 2: Open Source Manuals
        Collection manuals = new Collection(
                "npub1manuals456",
                "Open Source Software Manuals",
                "Documentation and guides for popular open source projects"
        );
        manuals.setFilesCount(12);
        manuals.setTotalSize(25 * 1024 * 1024); // 25 MB
        manuals.setUpdated("2025-11-12");

        CollectionFile docsDir = new CollectionFile("docs", "docs", CollectionFile.FileType.DIRECTORY);
        manuals.addFile(docsDir);

        CollectionFile linuxGuide = new CollectionFile("docs/linux-basics.pdf", "Linux Basics", CollectionFile.FileType.FILE);
        linuxGuide.setSize(2 * 1024 * 1024);
        linuxGuide.setDescription("Complete guide to Linux command line");
        linuxGuide.setMimeType("application/pdf");
        manuals.addFile(linuxGuide);

        collections.add(manuals);

        // Collection 3: Photography Collection
        Collection photos = new Collection(
                "npub1photos789",
                "Nature Photography",
                "High-resolution nature and landscape photographs"
        );
        photos.setFilesCount(156);
        photos.setTotalSize(523 * 1024 * 1024); // 523 MB
        photos.setUpdated("2025-11-10");

        CollectionFile imagesDir = new CollectionFile("images", "images", CollectionFile.FileType.DIRECTORY);
        photos.addFile(imagesDir);

        collections.add(photos);

        return collections;
    }

    private void filterCollections(String query) {
        if (query == null || query.trim().isEmpty()) {
            filteredCollections = new ArrayList<>(allCollections);
        } else {
            String lowerQuery = query.toLowerCase().trim();
            filteredCollections = new ArrayList<>();
            for (Collection collection : allCollections) {
                // Search in title and description
                if (collection.getTitle().toLowerCase().contains(lowerQuery) ||
                    collection.getDescription().toLowerCase().contains(lowerQuery)) {
                    filteredCollections.add(collection);
                }
            }
        }
        updateUI();
    }

    private void updateUI() {
        if (filteredCollections.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
            adapter.updateCollections(filteredCollections);
        }
    }

    private void onCollectionClick(Collection collection) {
        // Navigate to collection browser
        if (getActivity() != null) {
            CollectionBrowserFragment browserFragment = CollectionBrowserFragment.newInstance(collection);
            getActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, browserFragment)
                    .addToBackStack(null)
                    .commit();
        }
    }
}

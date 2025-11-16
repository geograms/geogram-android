package offgrid.geogram.fragments;

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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

import offgrid.geogram.MainActivity;
import offgrid.geogram.R;
import offgrid.geogram.adapters.CollectionAdapter;
import offgrid.geogram.models.Collection;
import offgrid.geogram.p2p.P2PHttpClient;

public class RemoteCollectionsFragment extends Fragment {

    private static final String ARG_DEVICE_ID = "device_id";
    private static final String ARG_REMOTE_IP = "remote_ip";

    private String deviceId;
    private String remoteIp;
    private RecyclerView recyclerView;
    private CollectionAdapter adapter;
    private TextView emptyMessage;
    private TextView loadingMessage;

    public static RemoteCollectionsFragment newInstance(String deviceId, String remoteIp) {
        RemoteCollectionsFragment fragment = new RemoteCollectionsFragment();
        Bundle args = new Bundle();
        args.putString(ARG_DEVICE_ID, deviceId);
        args.putString(ARG_REMOTE_IP, remoteIp);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            deviceId = getArguments().getString(ARG_DEVICE_ID);
            remoteIp = getArguments().getString(ARG_REMOTE_IP);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_remote_collections, container, false);

        // Setup toolbar
        TextView titleView = view.findViewById(R.id.tv_title);
        titleView.setText("Collections from " + deviceId);

        ImageButton btnBack = view.findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().getSupportFragmentManager().popBackStack();
            }
        });

        // Setup RecyclerView
        recyclerView = view.findViewById(R.id.collections_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        emptyMessage = view.findViewById(R.id.empty_message);
        loadingMessage = view.findViewById(R.id.loading_message);

        // Load collections from remote device
        loadRemoteCollections();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Hide top action bar
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setTopActionBarVisible(false);
        }
    }

    private void loadRemoteCollections() {
        loadingMessage.setVisibility(View.VISIBLE);
        emptyMessage.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);

        new Thread(() -> {
            try {
                // Use P2P-aware HTTP client (automatically routes through P2P if available)
                // Pass both deviceId and remoteIp - if remoteIp is local network, it will be used directly
                P2PHttpClient httpClient = new P2PHttpClient(getContext());
                P2PHttpClient.HttpResponse response = httpClient.get(deviceId, remoteIp, "/api/collections", 10000);

                if (response.isSuccess()) {
                    // Parse JSON response
                    JsonObject jsonResponse = JsonParser.parseString(response.body).getAsJsonObject();
                    JsonArray collectionsArray = jsonResponse.getAsJsonArray("collections");

                    List<Collection> collections = new ArrayList<>();
                    for (int i = 0; i < collectionsArray.size(); i++) {
                        JsonObject collectionJson = collectionsArray.get(i).getAsJsonObject();

                        String id = collectionJson.get("id").getAsString();

                        // Skip collections that we own (are admin of)
                        if (offgrid.geogram.util.CollectionKeysManager.isOwnedCollection(getContext(), id)) {
                            android.util.Log.i("RemoteCollections", "Skipping owned collection: " + id);
                            continue;
                        }

                        String title = collectionJson.get("title").getAsString();
                        String description = collectionJson.has("description") ?
                                collectionJson.get("description").getAsString() : "";

                        Collection collection = new Collection(id, title, description);
                        collection.setFilesCount(collectionJson.get("filesCount").getAsInt());
                        collection.setTotalSize(collectionJson.get("totalSize").getAsLong());

                        collections.add(collection);
                    }

                    // Update UI on main thread
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            loadingMessage.setVisibility(View.GONE);

                            if (collections.isEmpty()) {
                                emptyMessage.setVisibility(View.VISIBLE);
                                recyclerView.setVisibility(View.GONE);
                            } else {
                                emptyMessage.setVisibility(View.GONE);
                                recyclerView.setVisibility(View.VISIBLE);

                                adapter = new CollectionAdapter(collection -> {
                                    // Navigate to remote collection browser
                                    if (getActivity() != null) {
                                        CollectionBrowserFragment browserFragment =
                                                CollectionBrowserFragment.newRemoteInstance(collection, remoteIp, deviceId);
                                        getActivity().getSupportFragmentManager()
                                                .beginTransaction()
                                                .replace(R.id.fragment_container, browserFragment)
                                                .addToBackStack(null)
                                                .commit();
                                    }
                                });
                                adapter.updateCollections(collections);
                                recyclerView.setAdapter(adapter);
                            }
                        });
                    }
                } else {
                    showError("Failed to load collections (HTTP " + response.statusCode + ")");
                }
            } catch (Exception e) {
                android.util.Log.e("RemoteCollections", "Error: " + e.getMessage());
                showError("Connection error: " + e.getMessage());
            }
        }).start();
    }

    private void showError(String message) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                loadingMessage.setVisibility(View.GONE);
                emptyMessage.setText(message);
                emptyMessage.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
                Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
            });
        }
    }
}

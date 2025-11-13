package offgrid.geogram.fragments;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import java.io.File;

import offgrid.geogram.MainActivity;
import offgrid.geogram.R;
import offgrid.geogram.models.Collection;
import offgrid.geogram.util.TorrentGenerator;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CollectionInfoFragment extends Fragment {

    private static final String ARG_COLLECTION = "collection";

    private Collection collection;

    private TextView collectionId;
    private TextView storagePath;
    private Button btnCopyNpub;
    private Button btnOpenFileBrowser;
    private Button btnOpenTorrent;
    private Button btnShareTorrent;

    public static CollectionInfoFragment newInstance(Collection collection) {
        CollectionInfoFragment fragment = new CollectionInfoFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_COLLECTION, collection);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            collection = (Collection) getArguments().getSerializable(ARG_COLLECTION);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_collection_info, container, false);

        // Find views
        collectionId = view.findViewById(R.id.collection_id);
        storagePath = view.findViewById(R.id.storage_path);
        btnCopyNpub = view.findViewById(R.id.btn_copy_npub);
        btnOpenFileBrowser = view.findViewById(R.id.btn_open_file_browser);
        btnOpenTorrent = view.findViewById(R.id.btn_open_torrent);
        btnShareTorrent = view.findViewById(R.id.btn_share_torrent);

        setupButtons();
        populateInfo();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Show top action bar
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setTopActionBarVisible(true);
        }
    }

    private void populateInfo() {
        if (collection == null) return;

        // Collection ID
        collectionId.setText(collection.getId());

        // Storage Path
        if (collection.getStoragePath() != null && !collection.getStoragePath().isEmpty()) {
            storagePath.setText(collection.getStoragePath());
        } else {
            storagePath.setText("Not available");
        }
    }

    private void setupButtons() {
        btnCopyNpub.setOnClickListener(v -> copyNpubToClipboard());
        btnOpenFileBrowser.setOnClickListener(v -> openCollectionFolder());
        btnOpenTorrent.setOnClickListener(v -> openTorrentFile());
        btnShareTorrent.setOnClickListener(v -> shareTorrentFile());
    }

    private void copyNpubToClipboard() {
        if (collection == null || collection.getId() == null) {
            Toast.makeText(getContext(), "No npub to copy", Toast.LENGTH_SHORT).show();
            return;
        }

        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Collection npub", collection.getId());
        clipboard.setPrimaryClip(clip);
        Toast.makeText(getContext(), "Npub copied to clipboard", Toast.LENGTH_SHORT).show();
    }

    private void openCollectionFolder() {
        if (collection == null || collection.getStoragePath() == null) {
            Toast.makeText(getContext(), "Collection path not available", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            File collectionFolder = new File(collection.getStoragePath());
            if (!collectionFolder.exists()) {
                Toast.makeText(getContext(), "Collection folder not found", Toast.LENGTH_SHORT).show();
                return;
            }

            Uri folderUri = FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().getPackageName() + ".fileprovider",
                    collectionFolder
            );

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(folderUri, "resource/folder");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            try {
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                // Try alternative method - open with DocumentsUI
                Intent documentsIntent = new Intent(Intent.ACTION_GET_CONTENT);
                documentsIntent.setType("*/*");
                documentsIntent.addCategory(Intent.CATEGORY_OPENABLE);
                try {
                    startActivity(documentsIntent);
                    Toast.makeText(getContext(), "Navigate to: " + collection.getStoragePath(), Toast.LENGTH_LONG).show();
                } catch (ActivityNotFoundException e2) {
                    Toast.makeText(getContext(), "No file browser app found", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String getTorrentFilename() {
        if (collection == null) return "collection.torrent";

        String collectionName = collection.getTitle() != null ? collection.getTitle() : "collection";
        collectionName = collectionName.replaceAll("[^a-zA-Z0-9-_]", "_");

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        String date = sdf.format(new Date());

        return collectionName + "_" + date + ".torrent";
    }

    private File getTorrentFile() {
        if (collection == null || collection.getStoragePath() == null) {
            return null;
        }

        File extraDir = new File(collection.getStoragePath(), "extra");
        String filename = getTorrentFilename();
        File torrentFile = new File(extraDir, filename);

        // If torrent doesn't exist, try to generate it
        if (!torrentFile.exists()) {
            try {
                if (!extraDir.exists()) {
                    extraDir.mkdirs();
                }

                File collectionRoot = new File(collection.getStoragePath());
                List<String> trackers = new ArrayList<>();
                TorrentGenerator.generateTorrent(collectionRoot, torrentFile, trackers);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        return torrentFile.exists() ? torrentFile : null;
    }

    private void openTorrentFile() {
        if (collection == null || collection.getStoragePath() == null) {
            Toast.makeText(getContext(), "Collection path not available", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            File torrentFile = getTorrentFile();
            if (torrentFile == null || !torrentFile.exists()) {
                Toast.makeText(getContext(), "Failed to generate torrent file", Toast.LENGTH_SHORT).show();
                return;
            }

            Uri torrentUri = FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().getPackageName() + ".fileprovider",
                    torrentFile
            );

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(torrentUri, "application/x-bittorrent");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            try {
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(getContext(), "No torrent app found. Please install a BitTorrent client.", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void shareTorrentFile() {
        if (collection == null || collection.getStoragePath() == null) {
            Toast.makeText(getContext(), "Collection path not available", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            File torrentFile = getTorrentFile();
            if (torrentFile == null || !torrentFile.exists()) {
                Toast.makeText(getContext(), "Failed to generate torrent file", Toast.LENGTH_SHORT).show();
                return;
            }

            Uri torrentUri = FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().getPackageName() + ".fileprovider",
                    torrentFile
            );

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/x-bittorrent");
            shareIntent.putExtra(Intent.EXTRA_STREAM, torrentUri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, collection.getTitle() + " - Torrent");
            shareIntent.putExtra(Intent.EXTRA_TEXT, "Share this collection via BitTorrent");
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(shareIntent, "Share Torrent File"));
        } catch (Exception e) {
            Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}

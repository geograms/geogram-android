package offgrid.geogram.fragments;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.documentfile.provider.DocumentFile;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import offgrid.geogram.MainActivity;
import offgrid.geogram.R;
import offgrid.geogram.adapters.FileAdapter;
import offgrid.geogram.models.Collection;
import offgrid.geogram.models.CollectionFile;
import offgrid.geogram.util.CollectionLoader;
import offgrid.geogram.util.DownloadProgress;
import offgrid.geogram.util.TorrentGenerator;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CollectionBrowserFragment extends Fragment {

    private static final String ARG_COLLECTION = "collection";
    private static final String ARG_REMOTE_MODE = "remote_mode";
    private static final String ARG_REMOTE_IP = "remote_ip";
    private static final String ARG_DEVICE_ID = "device_id";

    private Collection collection;
    private boolean isRemoteMode = false;
    private String remoteIp = null;
    private String deviceId = null;
    private String currentPath = "";
    private List<CollectionFile> currentFiles = new ArrayList<>();
    private List<CollectionFile> allFilesFlat = new ArrayList<>();
    private String searchQuery = "";
    private boolean isSettingSearchText = false;

    private RecyclerView recyclerView;
    private FileAdapter adapter;
    private LinearLayout emptyState;
    private TextView emptyStateText;
    private TextView collectionTitle;
    private TextView breadcrumbPath;
    private LinearLayout breadcrumbContainer;
    private ImageButton btnBack;
    private ImageButton btnSettings;
    private ImageButton btnInfo;
    private FloatingActionButton fabAdd;

    // Header views
    private View collectionHeaderContainer;
    private ImageView collectionThumbnailLarge;
    private TextView collectionTitleHeader;
    private TextView collectionDescriptionHeader;
    private TextView collectionFilesCountHeader;
    private TextView collectionSizeHeader;
    private TextView collectionUpdatedHeader;
    private TextView collectionIdHeader;

    // Search views
    private LinearLayout searchContainer;
    private EditText searchFiles;

    // File/folder pickers
    private ActivityResultLauncher<Intent> pickFileLauncher;
    private ActivityResultLauncher<Uri> pickFolderLauncher;

    public static CollectionBrowserFragment newInstance(Collection collection) {
        CollectionBrowserFragment fragment = new CollectionBrowserFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_COLLECTION, collection);
        args.putBoolean(ARG_REMOTE_MODE, false);
        fragment.setArguments(args);
        return fragment;
    }

    public static CollectionBrowserFragment newRemoteInstance(Collection collection, String remoteIp, String deviceId) {
        CollectionBrowserFragment fragment = new CollectionBrowserFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_COLLECTION, collection);
        args.putBoolean(ARG_REMOTE_MODE, true);
        args.putString(ARG_REMOTE_IP, remoteIp);
        args.putString(ARG_DEVICE_ID, deviceId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            collection = (Collection) getArguments().getSerializable(ARG_COLLECTION);
            isRemoteMode = getArguments().getBoolean(ARG_REMOTE_MODE, false);
            remoteIp = getArguments().getString(ARG_REMOTE_IP);
            deviceId = getArguments().getString(ARG_DEVICE_ID);
        }

        // Initialize file picker
        pickFileLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null) {
                        Uri fileUri = result.getData().getData();
                        if (fileUri != null) {
                            copyFileToCollection(fileUri);
                        }
                    }
                });

        // Initialize folder picker
        pickFolderLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocumentTree(),
                uri -> {
                    if (uri != null) {
                        copyFolderToCollection(uri);
                    }
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_collection_browser, container, false);

        recyclerView = view.findViewById(R.id.files_recycler);
        emptyState = view.findViewById(R.id.empty_state);
        emptyStateText = view.findViewById(R.id.empty_state_text);
        collectionTitle = view.findViewById(R.id.collection_title);
        breadcrumbPath = view.findViewById(R.id.breadcrumb_path);
        breadcrumbContainer = view.findViewById(R.id.breadcrumb_container);
        btnBack = view.findViewById(R.id.btn_back);
        btnSettings = view.findViewById(R.id.btn_settings);
        btnInfo = view.findViewById(R.id.btn_info);
        fabAdd = view.findViewById(R.id.fab_add);

        // Header views
        collectionHeaderContainer = view.findViewById(R.id.collection_header_container);
        collectionThumbnailLarge = view.findViewById(R.id.collection_thumbnail_large);
        collectionTitleHeader = view.findViewById(R.id.collection_title_header);
        collectionDescriptionHeader = view.findViewById(R.id.collection_description_header);
        collectionFilesCountHeader = view.findViewById(R.id.collection_files_count_header);
        collectionSizeHeader = view.findViewById(R.id.collection_size_header);
        collectionUpdatedHeader = view.findViewById(R.id.collection_updated_header);
        collectionIdHeader = view.findViewById(R.id.collection_id_header);

        // Search views
        searchContainer = view.findViewById(R.id.search_container);
        searchFiles = view.findViewById(R.id.search_files);

        setupRecyclerView();
        setupNavigation();
        setupSearch();
        setupSettingsButton();
        setupInfoButton();
        setupAddButton();

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Get collection from parent fragment
        // For now, use dummy data
        if (collection == null) {
            collection = getDummyCollection();
        }

        collectionTitle.setText(collection.getTitle());
        setupCollectionHeader();

        // Show FAB add button only if user owns this collection AND not in remote mode
        if (collection.isOwned() && !isRemoteMode) {
            fabAdd.setVisibility(View.VISIBLE);
        } else {
            fabAdd.setVisibility(View.GONE);
        }

        // Re-scan collection to ensure we have the latest files
        // In remote mode, we don't rescan - files are loaded from remote API
        if (collection.getStoragePath() != null && !isRemoteMode) {
            rescanCollection();
        } else if (isRemoteMode && (remoteIp != null || deviceId != null) && collection.getId() != null) {
            // Load tree-data.js from remote device via WiFi or relay
            loadRemoteTreeData();
        }

        // Set storage path in adapter for thumbnail loading
        updateAdapterStoragePath();

        // Handle physical back button
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (currentPath.isEmpty()) {
                    // At root - go back to collections list
                    setEnabled(false);
                    requireActivity().getOnBackPressedDispatcher().onBackPressed();
                } else {
                    // In subdirectory - navigate up
                    navigateUp();
                }
            }
        });

        loadDirectory("");
    }

    private void setupCollectionHeader() {
        if (collection == null) return;

        // Populate header fields
        collectionTitleHeader.setText(collection.getTitle());

        if (collection.getDescription() != null && !collection.getDescription().isEmpty()) {
            collectionDescriptionHeader.setText(collection.getDescription());
            collectionDescriptionHeader.setVisibility(View.VISIBLE);
        } else {
            collectionDescriptionHeader.setVisibility(View.GONE);
        }

        collectionFilesCountHeader.setText(collection.getFilesCount() + " files");
        collectionSizeHeader.setText(collection.getFormattedSize());

        if (collection.getUpdated() != null && !collection.getUpdated().isEmpty()) {
            collectionUpdatedHeader.setText("Updated: " + collection.getUpdated());
            collectionUpdatedHeader.setVisibility(View.VISIBLE);
        } else {
            collectionUpdatedHeader.setVisibility(View.GONE);
        }

        if (collection.getId() != null && !collection.getId().isEmpty()) {
            collectionIdHeader.setText(collection.getId());
            collectionIdHeader.setVisibility(View.VISIBLE);
        } else {
            collectionIdHeader.setVisibility(View.GONE);
        }

        // TODO: Load actual thumbnail if available
        // For now, use default icon
        collectionThumbnailLarge.setImageResource(R.drawable.ic_collections);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Hide top action bar for detail screens
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setTopActionBarVisible(false);
        }
    }

    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new FileAdapter(this::onFileClick);
        adapter.setOnFileLongClickListener(this::onFileLongClick);
        adapter.setOnFileDownloadListener(this::onFileDownload);
        adapter.setOnFolderDownloadListener(this::onFolderDownload);
        recyclerView.setAdapter(adapter);
    }

    private void updateAdapterStoragePath() {
        if (adapter != null && collection != null) {
            if (isRemoteMode) {
                // Set remote mode in adapter - pass both remoteIp and deviceId for WiFi/relay routing
                adapter.setRemoteMode(true, remoteIp, deviceId, collection.getId(), getContext());
            } else {
                // Set local storage path
                adapter.setCollectionStoragePath(collection.getStoragePath());
            }
        }
    }

    private void setupNavigation() {
        btnBack.setOnClickListener(v -> {
            if (currentPath.isEmpty()) {
                // At root, go back to collections list
                if (getActivity() != null) {
                    getActivity().getSupportFragmentManager().popBackStack();
                }
            } else {
                // Go up one directory
                navigateUp();
            }
        });
    }

    private void setupSettingsButton() {
        // Show settings button only if user is administrator of the collection
        boolean isAdmin = offgrid.geogram.util.CollectionKeysManager.isOwnedCollection(getContext(), collection.getId());

        if (isAdmin && !isRemoteMode) {
            btnSettings.setVisibility(View.VISIBLE);
            btnSettings.setOnClickListener(v -> {
                if (collection == null) return;

                // Navigate to CollectionSettingsFragment
                CollectionSettingsFragment settingsFragment = CollectionSettingsFragment.newInstance(collection);
                getParentFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, settingsFragment)
                        .addToBackStack(null)
                        .commit();
            });
        } else {
            btnSettings.setVisibility(View.GONE);
        }
    }

    private void setupInfoButton() {
        btnInfo.setOnClickListener(v -> {
            if (collection == null) return;

            // Navigate to CollectionInfoFragment
            CollectionInfoFragment infoFragment = CollectionInfoFragment.newInstance(collection);
            getParentFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, infoFragment)
                    .addToBackStack(null)
                    .commit();
        });
    }

    private void setupAddButton() {
        fabAdd.setOnClickListener(v -> {
            // Show menu with options: Create Folder, Add File, Add Folder
            String[] options = {"Create Folder", "Add file (copy)", "Add folder (copy)"};

            AlertDialog dialog = new AlertDialog.Builder(requireContext())
                    .setTitle("Add to Collection")
                    .setItems(options, (dialogInterface, which) -> {
                        switch (which) {
                            case 0: // Create Folder
                                showCreateFolderDialog();
                                break;
                            case 1: // Add File
                                pickFile();
                                break;
                            case 2: // Add Folder
                                pickFolder();
                                break;
                        }
                    })
                    .create();
            dialog.show();
        });
    }

    private void showCreateFolderDialog() {
        EditText input = new EditText(requireContext());
        input.setHint("Folder name");

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Create New Folder")
                .setView(input)
                .setPositiveButton("Create", (dialogInterface, i) -> {
                    String folderName = input.getText().toString().trim();
                    if (!folderName.isEmpty()) {
                        createFolder(folderName);
                    } else {
                        Toast.makeText(requireContext(), "Folder name cannot be empty", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .create();
        dialog.show();

        // Force button text to white
        if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(0xFFFFFFFF);
        }
        if (dialog.getButton(AlertDialog.BUTTON_NEGATIVE) != null) {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(0xFFFFFFFF);
        }
    }

    private void pickFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        pickFileLauncher.launch(intent);
    }

    private void pickFolder() {
        pickFolderLauncher.launch(null);
    }

    private void createFolder(String folderName) {
        if (collection == null || collection.getStoragePath() == null) {
            Toast.makeText(requireContext(), "Collection path not available", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            File collectionRoot = new File(collection.getStoragePath());
            File newFolder = new File(collectionRoot, currentPath.isEmpty() ? folderName : currentPath + "/" + folderName);

            if (newFolder.exists()) {
                Toast.makeText(requireContext(), "Folder already exists", Toast.LENGTH_SHORT).show();
                return;
            }

            if (newFolder.mkdirs()) {
                Toast.makeText(requireContext(), "Folder created", Toast.LENGTH_SHORT).show();

                // Re-scan the collection folder to update file list
                rescanCollection();
                loadDirectory(currentPath); // Reload current directory
            } else {
                Toast.makeText(requireContext(), "Failed to create folder", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void copyFileToCollection(Uri fileUri) {
        if (collection == null || collection.getStoragePath() == null) {
            Toast.makeText(requireContext(), "Collection path not available", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // Get file name
            String fileName = getFileName(fileUri);
            if (fileName == null) {
                fileName = "file_" + System.currentTimeMillis();
            }

            File collectionRoot = new File(collection.getStoragePath());
            File destFile = new File(collectionRoot, currentPath.isEmpty() ? fileName : currentPath + "/" + fileName);

            // Copy file
            copyFile(fileUri, destFile);

            Toast.makeText(requireContext(), "File added", Toast.LENGTH_SHORT).show();

            // Re-scan the collection folder to update file list
            rescanCollection();
            loadDirectory(currentPath); // Reload current directory
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void copyFolderToCollection(Uri folderUri) {
        if (collection == null || collection.getStoragePath() == null) {
            Toast.makeText(requireContext(), "Collection path not available", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            DocumentFile folder = DocumentFile.fromTreeUri(requireContext(), folderUri);
            if (folder == null || !folder.isDirectory()) {
                Toast.makeText(requireContext(), "Invalid folder", Toast.LENGTH_SHORT).show();
                return;
            }

            File collectionRoot = new File(collection.getStoragePath());
            String folderName = folder.getName();
            if (folderName == null) {
                folderName = "folder_" + System.currentTimeMillis();
            }

            File destFolder = new File(collectionRoot, currentPath.isEmpty() ? folderName : currentPath + "/" + folderName);
            destFolder.mkdirs();

            // Recursively copy folder contents
            copyFolderContents(folder, destFolder);

            Toast.makeText(requireContext(), "Folder added", Toast.LENGTH_SHORT).show();

            // Re-scan the collection folder to update file list
            rescanCollection();
            loadDirectory(currentPath); // Reload current directory
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void copyFolderContents(DocumentFile sourceFolder, File destFolder) throws Exception {
        for (DocumentFile file : sourceFolder.listFiles()) {
            if (file.isDirectory()) {
                File newSubFolder = new File(destFolder, file.getName());
                newSubFolder.mkdirs();
                copyFolderContents(file, newSubFolder);
            } else {
                File destFile = new File(destFolder, file.getName());
                copyFile(file.getUri(), destFile);
            }
        }
    }

    private void copyFile(Uri sourceUri, File destFile) throws Exception {
        try (java.io.InputStream in = requireContext().getContentResolver().openInputStream(sourceUri);
             java.io.OutputStream out = new java.io.FileOutputStream(destFile)) {

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }
    }

    private String getFileName(Uri uri) {
        String fileName = null;
        android.database.Cursor cursor = requireContext().getContentResolver().query(
                uri, null, null, null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        fileName = cursor.getString(nameIndex);
                    }
                }
            } finally {
                cursor.close();
            }
        }
        return fileName;
    }

    private void rescanCollection() {
        if (collection == null || collection.getStoragePath() == null) {
            return;
        }

        // Clear current files
        collection.getFiles().clear();

        // Re-scan the collection directory
        File collectionRoot = new File(collection.getStoragePath());
        scanFolderRecursive(collectionRoot, collectionRoot, "");

        // Recalculate statistics
        long totalSize = 0;
        int fileCount = 0;
        for (CollectionFile file : collection.getFiles()) {
            if (!file.isDirectory()) {
                totalSize += file.getSize();
                fileCount++;
            }
        }
        collection.setTotalSize(totalSize);
        collection.setFilesCount(fileCount);

        // Rebuild flat file list
        buildFlatFileList();

        // Update tree-data.js with current file structure
        updateTreeDataJs();

        // Generate torrent file for sharing
        generateTorrentFile();

        // Refresh header with new statistics
        setupCollectionHeader();
    }

    private void loadRemoteTreeData() {
        android.util.Log.i("CollectionBrowser", "═══════════════════════════════════════");
        android.util.Log.i("CollectionBrowser", "loadRemoteTreeData STARTED");
        android.util.Log.i("CollectionBrowser", "  Collection ID: " + collection.getId());
        android.util.Log.i("CollectionBrowser", "  Device ID: " + deviceId);
        android.util.Log.i("CollectionBrowser", "  Remote IP: " + remoteIp);
        android.util.Log.i("CollectionBrowser", "═══════════════════════════════════════");

        new Thread(() -> {
            try {
                // Check if we have a cached tree-data.js
                File collectionsDir = new File(requireContext().getFilesDir(), "collections");
                File collectionFolder = new File(collectionsDir, collection.getId());
                File cachedTreeData = new File(new File(collectionFolder, "extra"), "tree-data.js");

                String cachedSha1 = null;
                if (cachedTreeData.exists()) {
                    // Calculate SHA1 of cached file
                    cachedSha1 = calculateSHA1(cachedTreeData);
                    android.util.Log.i("CollectionBrowser", "Found cached tree-data.js (SHA1: " + cachedSha1 + ")");
                }

                // Get tree-data SHA1 from config.json (if available)
                String remoteSha1 = getTreeDataSha1FromConfig(deviceId, remoteIp);

                // Check if cache is valid
                boolean useCached = cachedTreeData.exists() &&
                                   cachedSha1 != null &&
                                   remoteSha1 != null &&
                                   cachedSha1.equals(remoteSha1);

                if (useCached) {
                    android.util.Log.i("CollectionBrowser", "✓ Using cached tree-data.js (SHA1 matches)");
                    String cachedContent = readFileContent(cachedTreeData);
                    parseTreeData(cachedContent);

                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> loadDirectory(currentPath));
                    }
                    return;
                }

                // Cache miss or outdated - download fresh copy
                android.util.Log.i("CollectionBrowser", "Downloading tree-data.js (cache miss or outdated)");
                offgrid.geogram.p2p.P2PHttpClient httpClient = new offgrid.geogram.p2p.P2PHttpClient(getContext());
                String path = "/api/collections/" + collection.getId() + "/file/extra/tree-data.js";

                offgrid.geogram.p2p.P2PHttpClient.HttpResponse response =
                    httpClient.get(deviceId, remoteIp, path, 10000);

                if (response.isSuccess()) {
                    android.util.Log.d("CollectionBrowser", "✓ Received tree-data.js: " + response.body.length() + " bytes");

                    // Cache the downloaded tree-data.js
                    cacheTreeData(collectionFolder, response.body);

                    // Parse tree-data.js content
                    parseTreeData(response.body);

                    // Update UI on main thread
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> loadDirectory(currentPath));
                    }
                } else {
                    android.util.Log.e("CollectionBrowser", "✗ HTTP request failed with status: " + response.statusCode);
                    showRemoteError("Failed to load file list (HTTP " + response.statusCode + ")");
                }
            } catch (Exception e) {
                android.util.Log.e("CollectionBrowser", "Error loading remote tree-data", e);
                showRemoteError("Failed to load file list: " + e.getMessage());
            }
        }).start();
    }

    private void parseTreeData(String treeDataContent) {
        android.util.Log.i("CollectionBrowser", "═══════════════════════════════════════");
        android.util.Log.i("CollectionBrowser", "parseTreeData STARTED");
        android.util.Log.i("CollectionBrowser", "  Content length: " + treeDataContent.length() + " bytes");
        android.util.Log.i("CollectionBrowser", "  Content preview (first 150 chars): " + treeDataContent.substring(0, Math.min(150, treeDataContent.length())));
        android.util.Log.i("CollectionBrowser", "═══════════════════════════════════════");

        try {
            // Extract JSON array from "window.TREE_DATA = [...];"
            android.util.Log.d("CollectionBrowser", "Searching for '[' and ']' in content...");
            int startIdx = treeDataContent.indexOf('[');
            int endIdx = treeDataContent.lastIndexOf(']');
            android.util.Log.d("CollectionBrowser", "Found '[' at index: " + startIdx + ", ']' at index: " + endIdx);

            if (startIdx == -1 || endIdx == -1) {
                android.util.Log.e("CollectionBrowser", "✗ Invalid tree-data.js format - missing '[' or ']'");
                return;
            }

            String jsonArrayStr = treeDataContent.substring(startIdx, endIdx + 1);
            android.util.Log.d("CollectionBrowser", "Extracted JSON array: " + jsonArrayStr.length() + " chars");
            android.util.Log.d("CollectionBrowser", "JSON preview: " + jsonArrayStr.substring(0, Math.min(200, jsonArrayStr.length())));

            // Parse JSON array
            android.util.Log.d("CollectionBrowser", "Parsing JSON array...");
            JsonArray filesArray = JsonParser.parseString(jsonArrayStr).getAsJsonArray();
            android.util.Log.i("CollectionBrowser", "✓ JSON parsed successfully: " + filesArray.size() + " entries");

            // Clear existing files and repopulate
            int previousFileCount = collection.getFiles().size();
            collection.getFiles().clear();
            android.util.Log.d("CollectionBrowser", "Cleared " + previousFileCount + " existing files from collection");

            int fileCount = 0;
            int dirCount = 0;

            for (int i = 0; i < filesArray.size(); i++) {
                JsonObject fileObj = filesArray.get(i).getAsJsonObject();
                String path = fileObj.get("path").getAsString();
                String type = fileObj.get("type").getAsString();

                // Extract file name from path
                String fileName = path;
                if (path.contains("/")) {
                    fileName = path.substring(path.lastIndexOf('/') + 1);
                }

                CollectionFile.FileType fileType = "directory".equals(type) ?
                        CollectionFile.FileType.DIRECTORY : CollectionFile.FileType.FILE;

                CollectionFile file = new CollectionFile(path, fileName, fileType);

                // Set file size if available
                if (fileObj.has("size")) {
                    file.setSize(fileObj.get("size").getAsLong());
                }

                // Set MIME type if available
                if (fileObj.has("metadata") && fileObj.getAsJsonObject("metadata").has("mime_type")) {
                    file.setMimeType(fileObj.getAsJsonObject("metadata").get("mime_type").getAsString());
                }

                // Set SHA1 if available
                if (fileObj.has("sha1")) {
                    file.setSha1(fileObj.get("sha1").getAsString());
                }

                collection.addFile(file);

                // Track counts
                if (fileType == CollectionFile.FileType.DIRECTORY) {
                    dirCount++;
                } else {
                    fileCount++;
                }

                // Log first few entries for debugging
                if (i < 5) {
                    android.util.Log.d("CollectionBrowser", "Parsed entry " + i + ": path=" + path + ", type=" + type + ", name=" + fileName);
                }
            }

            android.util.Log.i("CollectionBrowser", "Loaded " + collection.getFiles().size() + " total entries from remote tree-data.js: " + fileCount + " files, " + dirCount + " directories");

            // Show error if collection is empty (no files or folders found)
            if (collection.getFiles().isEmpty()) {
                android.util.Log.w("CollectionBrowser", "Collection has no files or folders - this shouldn't happen");
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        showRemoteError("Collection is empty - no files or folders found");
                    });
                }
            }

        } catch (Exception e) {
            android.util.Log.e("CollectionBrowser", "Error parsing tree-data: " + e.getMessage());
            e.printStackTrace();
            // Show error to user
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    showRemoteError("Failed to parse collection data: " + e.getMessage());
                });
            }
        }
    }

    private void showRemoteError(String message) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
            });
        }
    }

    private void scanFolderRecursive(File collectionRoot, File folder, String currentPath) {
        File[] files = folder.listFiles();
        if (files == null) return;

        for (File file : files) {
            String fileName = file.getName();

            // Skip collection metadata files and extra/ folder
            if ("collection.js".equals(fileName) || "extra".equals(fileName) ||
                "manifest".equals(fileName) || "data.js".equals(fileName)) {
                continue;
            }

            String filePath = currentPath.isEmpty() ? fileName : currentPath + "/" + fileName;

            if (file.isDirectory()) {
                CollectionFile dirEntry = new CollectionFile(filePath, fileName,
                        CollectionFile.FileType.DIRECTORY);
                collection.addFile(dirEntry);
                scanFolderRecursive(collectionRoot, file, filePath);
            } else {
                CollectionFile fileEntry = new CollectionFile(filePath, fileName,
                        CollectionFile.FileType.FILE);
                fileEntry.setSize(file.length());
                collection.addFile(fileEntry);
            }
        }
    }

    private void updateTreeDataJs() {
        if (collection == null || collection.getStoragePath() == null) {
            return;
        }

        try {
            File collectionRoot = new File(collection.getStoragePath());

            // Ensure extra/ folder exists
            File extraDir = new File(collectionRoot, "extra");
            if (!extraDir.exists()) {
                extraDir.mkdirs();
            }

            // Generate tree-data.js content
            StringBuilder sb = new StringBuilder();
            sb.append("window.TREE_DATA = [\n");

            List<CollectionFile> files = collection.getFiles();
            for (int i = 0; i < files.size(); i++) {
                CollectionFile file = files.get(i);
                sb.append("  {\n");
                sb.append("    \"path\": \"").append(escapeJson(file.getPath())).append("\",\n");
                sb.append("    \"type\": \"").append(file.isDirectory() ? "directory" : "file").append("\"");

                if (!file.isDirectory()) {
                    sb.append(",\n");
                    sb.append("    \"size\": ").append(file.getSize()).append(",\n");
                    sb.append("    \"hashes\": {\n");
                    sb.append("      \"sha1\": \"").append(calculateSHA1(new File(collectionRoot, file.getPath()))).append("\"\n");
                    sb.append("    }");

                    if (file.getMimeType() != null) {
                        sb.append(",\n");
                        sb.append("    \"metadata\": {\n");
                        sb.append("      \"mime_type\": \"").append(escapeJson(file.getMimeType())).append("\"\n");
                        sb.append("    }");
                    }
                }

                sb.append("\n  }");
                if (i < files.size() - 1) {
                    sb.append(",");
                }
                sb.append("\n");
            }

            sb.append("];\n");

            // Write to extra/tree-data.js
            File treeDataJs = new File(extraDir, "tree-data.js");
            try (java.io.FileWriter writer = new java.io.FileWriter(treeDataJs)) {
                writer.write(sb.toString());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String calculateSHA1(File file) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-1");
            try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            byte[] hashBytes = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private void generateTorrentFile() {
        if (collection == null || collection.getStoragePath() == null) {
            return;
        }

        try {
            File collectionRoot = new File(collection.getStoragePath());

            // Ensure extra/ folder exists
            File extraDir = new File(collectionRoot, "extra");
            if (!extraDir.exists()) {
                extraDir.mkdirs();
            }

            // Generate torrent filename with collection name and date
            String collectionName = collection.getTitle() != null ? collection.getTitle() : "collection";
            collectionName = collectionName.replaceAll("[^a-zA-Z0-9-_]", "_");

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            String date = sdf.format(new Date());

            String torrentFilename = collectionName + "_" + date + ".torrent";
            File torrentFile = new File(extraDir, torrentFilename);

            // Optional: Add tracker URLs (can be customized)
            List<String> trackers = new ArrayList<>();
            // Add public trackers if desired
            // trackers.add("udp://tracker.opentrackr.org:1337/announce");
            // trackers.add("udp://open.stealth.si:80/announce");

            TorrentGenerator.TorrentInfo info = TorrentGenerator.generateTorrent(
                    collectionRoot,
                    torrentFile,
                    trackers
            );

            // Log success (optional)
            // Toast.makeText(requireContext(), "Torrent generated: " + info.pieceCount + " pieces", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            e.printStackTrace();
            // Silently fail - torrent generation is optional
        }
    }

    private void setupSearch() {
        // Build flat list of all files for searching
        buildFlatFileList();

        searchFiles.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Ignore text changes when we're setting it programmatically
                if (isSettingSearchText) {
                    return;
                }
                searchQuery = s.toString();
                applySearch();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void buildFlatFileList() {
        allFilesFlat.clear();
        if (collection != null) {
            for (CollectionFile file : collection.getFiles()) {
                if (!file.isDirectory()) {
                    allFilesFlat.add(file);
                }
            }
        }
    }

    private void applySearch() {
        if (searchQuery == null || searchQuery.trim().isEmpty()) {
            // No search, show normal directory view
            loadDirectory(currentPath);
        } else {
            // Filter all files by search query
            String lowerQuery = searchQuery.toLowerCase().trim();
            List<CollectionFile> searchResults = new ArrayList<>();

            for (CollectionFile file : allFilesFlat) {
                if (file.getName().toLowerCase().contains(lowerQuery) ||
                    (file.getDescription() != null && file.getDescription().toLowerCase().contains(lowerQuery))) {
                    searchResults.add(file);
                }
            }

            // Sort search results alphabetically
            Collections.sort(searchResults, (f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName()));

            currentFiles = searchResults;
            updateUI();
        }
    }

    private void loadDirectory(String path) {
        currentPath = path;

        // Show/hide header and search based on path
        if (path.isEmpty()) {
            // At root - show header and search
            collectionHeaderContainer.setVisibility(View.VISIBLE);
            searchContainer.setVisibility(View.VISIBLE);
            breadcrumbContainer.setVisibility(View.GONE);
        } else {
            // In subdirectory - hide header and search, show breadcrumb
            collectionHeaderContainer.setVisibility(View.GONE);
            searchContainer.setVisibility(View.GONE);
            breadcrumbContainer.setVisibility(View.VISIBLE);
            breadcrumbPath.setText("/" + path);

            // Clear search when navigating away from root
            searchQuery = "";
            isSettingSearchText = true;
            searchFiles.setText("");
            isSettingSearchText = false;
        }

        // Filter files for current directory
        currentFiles = getFilesInPath(path);

        // Sort: directories first, then files, both alphabetically
        Collections.sort(currentFiles, (f1, f2) -> {
            if (f1.isDirectory() && !f2.isDirectory()) {
                return -1;
            } else if (!f1.isDirectory() && f2.isDirectory()) {
                return 1;
            } else {
                return f1.getName().compareToIgnoreCase(f2.getName());
            }
        });

        updateUI();
    }

    private List<CollectionFile> getFilesInPath(String path) {
        List<CollectionFile> filesInPath = new ArrayList<>();

        android.util.Log.d("CollectionBrowser", "getFilesInPath called with path='" + path + "', total files in collection=" + collection.getFiles().size());

        int processedCount = 0;
        for (CollectionFile file : collection.getFiles()) {
            String filePath = file.getPath();

            if (path.isEmpty()) {
                // Root level - show items without '/' or with single '/'
                int slashCount = countSlashes(filePath);
                if (slashCount == 0 || slashCount == 1 && filePath.endsWith("/")) {
                    filesInPath.add(file);
                    android.util.Log.d("CollectionBrowser", "  Added (root direct): " + filePath);
                } else if (slashCount == 1 && !filePath.endsWith("/")) {
                    // It's a file in a directory, add the directory if not already added
                    String dirName = filePath.substring(0, filePath.indexOf('/'));
                    if (!containsDirectory(filesInPath, dirName)) {
                        CollectionFile dir = new CollectionFile(dirName, dirName, CollectionFile.FileType.DIRECTORY);
                        filesInPath.add(dir);
                        android.util.Log.d("CollectionBrowser", "  Created directory for: " + dirName + " (from file: " + filePath + ")");
                    }
                } else {
                    if (processedCount < 3) {
                        android.util.Log.d("CollectionBrowser", "  Skipped (root): " + filePath + " (slashCount=" + slashCount + ")");
                    }
                }
            } else {
                // Inside a directory
                if (filePath.startsWith(path + "/")) {
                    String remainder = filePath.substring((path + "/").length());
                    if (!remainder.contains("/")) {
                        // Direct child
                        filesInPath.add(file);
                        android.util.Log.d("CollectionBrowser", "  Added (subdir direct): " + filePath);
                    } else {
                        // File in subdirectory, add subdirectory if not exists
                        String subDirName = remainder.substring(0, remainder.indexOf('/'));
                        String fullSubDirPath = path + "/" + subDirName;
                        if (!containsDirectory(filesInPath, subDirName)) {
                            CollectionFile dir = new CollectionFile(fullSubDirPath, subDirName, CollectionFile.FileType.DIRECTORY);
                            filesInPath.add(dir);
                            android.util.Log.d("CollectionBrowser", "  Created subdirectory: " + fullSubDirPath + " (from file: " + filePath + ")");
                        }
                    }
                } else {
                    if (processedCount < 3) {
                        android.util.Log.d("CollectionBrowser", "  Skipped (subdir): " + filePath + " (doesn't start with '" + path + "/')");
                    }
                }
            }
            processedCount++;
        }

        android.util.Log.i("CollectionBrowser", "getFilesInPath result: " + filesInPath.size() + " files/dirs for path='" + path + "'");
        return filesInPath;
    }

    private boolean containsDirectory(List<CollectionFile> files, String dirName) {
        for (CollectionFile file : files) {
            if (file.isDirectory() && file.getName().equals(dirName)) {
                return true;
            }
        }
        return false;
    }

    private int countSlashes(String path) {
        int count = 0;
        for (char c : path.toCharArray()) {
            if (c == '/') count++;
        }
        return count;
    }

    private void navigateUp() {
        if (currentPath.contains("/")) {
            currentPath = currentPath.substring(0, currentPath.lastIndexOf('/'));
        } else {
            currentPath = "";
        }
        loadDirectory(currentPath);
    }

    private void updateUI() {
        if (currentFiles.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);

            // Check if we're still loading data from remote device
            if (isRemoteMode && collection != null && collection.getFiles().isEmpty()) {
                // Still loading remote data over BLE - show loading message
                emptyStateText.setText("Loading data..");
            } else {
                // Actually empty folder
                emptyStateText.setText("Empty folder");
            }
        } else {
            emptyState.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
            adapter.updateFiles(currentFiles);
        }
    }

    private void onFileClick(CollectionFile file) {
        if (file.isDirectory()) {
            // Navigate into directory
            loadDirectory(file.getPath());
        } else {
            // Open file with associated app
            openFile(file);
        }
    }

    private void onFileLongClick(CollectionFile file) {
        // Only show context menu if user owns the collection
        if (collection == null || !collection.isOwned()) {
            return;
        }

        // Show context menu with rename and delete options
        String[] options = {"Rename", "Delete"};

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(file.getName())
                .setItems(options, (dialogInterface, which) -> {
                    switch (which) {
                        case 0: // Rename
                            showRenameDialog(file);
                            break;
                        case 1: // Delete
                            showDeleteConfirmDialog(file);
                            break;
                    }
                })
                .create();
        dialog.show();
    }

    /**
     * Download all files in a folder recursively
     */
    private void onFolderDownload(CollectionFile folder) {
        if (!isRemoteMode || collection == null) {
            Toast.makeText(requireContext(), "Download only available in remote mode", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!folder.isDirectory()) {
            Toast.makeText(requireContext(), "Not a folder", Toast.LENGTH_SHORT).show();
            return;
        }

        // Get all files in this folder recursively
        List<CollectionFile> filesToDownload = new ArrayList<>();
        String folderPath = folder.getPath();

        // Add trailing slash if not present
        if (!folderPath.endsWith("/")) {
            folderPath += "/";
        }

        // Filter allFilesFlat to get all files under this folder path
        for (CollectionFile file : allFilesFlat) {
            if (!file.isDirectory() && file.getPath().startsWith(folderPath)) {
                filesToDownload.add(file);
            }
        }

        if (filesToDownload.isEmpty()) {
            Toast.makeText(requireContext(), "No files found in folder", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show confirmation
        int fileCount = filesToDownload.size();
        String message = "Download " + fileCount + " file" + (fileCount > 1 ? "s" : "") + " from '" + folder.getName() + "'?";

        new android.app.AlertDialog.Builder(requireContext())
            .setTitle("Download Folder")
            .setMessage(message)
            .setPositiveButton("Download", (dialog, which) -> {
                // Queue all files for download
                android.util.Log.i("CollectionBrowser", "Queueing " + fileCount + " files for download from folder: " + folder.getName());

                for (CollectionFile file : filesToDownload) {
                    onFileDownload(file);
                }

                Toast.makeText(requireContext(),
                    fileCount + " file" + (fileCount > 1 ? "s" : "") + " queued for download",
                    Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void onFileDownload(CollectionFile file) {
        if (!isRemoteMode || collection == null) {
            Toast.makeText(requireContext(), "Download only available in remote mode", Toast.LENGTH_SHORT).show();
            return;
        }

        // Start chunked download in background thread
        new Thread(() -> {
            try {
                // Call chunked download API endpoint
                String serverUrl = "http://localhost:45678";
                String endpoint = serverUrl + "/api/remote/download-file-chunked";

                // Build JSON request
                JsonObject request = new JsonObject();
                if (deviceId != null) {
                    request.addProperty("deviceId", deviceId);
                } else {
                    request.addProperty("deviceId", "");
                }
                if (remoteIp != null) {
                    request.addProperty("remoteIp", remoteIp);
                }
                request.addProperty("collectionId", collection.getId());
                request.addProperty("filePath", file.getPath());
                request.addProperty("fileSize", file.getSize());

                // Send POST request
                URL url = new URL(endpoint);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.getOutputStream().write(request.toString().getBytes("UTF-8"));

                int responseCode = conn.getResponseCode();
                android.util.Log.d("CollectionBrowser", "Download API response code: " + responseCode);

                BufferedReader reader = new BufferedReader(
                    new InputStreamReader((responseCode == 200 || responseCode == 202) ? conn.getInputStream() : conn.getErrorStream())
                );
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                android.util.Log.d("CollectionBrowser", "Download API response: " + response.toString());

                if (responseCode == 200 || responseCode == 202) {
                    // Download started (202 Accepted) - begin polling for progress
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), "Download started", Toast.LENGTH_SHORT).show();
                        startDownloadProgressPolling(collection.getId() + "/" + file.getPath());
                    });
                } else {
                    final String errorMsg = response.toString();
                    android.util.Log.e("CollectionBrowser", "Download failed with code " + responseCode + ": " + errorMsg);
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), "Download failed (HTTP " + responseCode + "): " + errorMsg, Toast.LENGTH_LONG).show();
                    });
                }
            } catch (Exception e) {
                android.util.Log.e("CollectionBrowser", "Error starting download", e);
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void startDownloadProgressPolling(String fileId) {
        // Poll download progress every 2 seconds
        android.os.Handler handler = new android.os.Handler();
        Runnable pollRunnable = new Runnable() {
            @Override
            public void run() {
                // Check if fragment is still attached before proceeding
                if (!isAdded() || getActivity() == null) {
                    // Fragment detached, stop polling
                    return;
                }

                offgrid.geogram.util.DownloadProgress.DownloadStatus status =
                    offgrid.geogram.util.DownloadProgress.getInstance().getDownloadStatus(fileId);

                if (status != null) {
                    // Update adapter with new progress
                    if (adapter != null) {
                        adapter.updateDownloadProgress(fileId, status.percentComplete);
                    }

                    // Check if download is complete or failed
                    if (status.completed) {
                        if (isAdded() && getContext() != null) {
                            Toast.makeText(getContext(), "Download complete", Toast.LENGTH_SHORT).show();
                        }
                        return; // Stop polling
                    } else if (status.failed) {
                        if (isAdded() && getContext() != null) {
                            Toast.makeText(getContext(), "Download failed: " + status.errorMessage, Toast.LENGTH_LONG).show();
                        }
                        return; // Stop polling
                    }

                    // Continue polling
                    handler.postDelayed(this, 2000);
                } else {
                    // Download not found, stop polling
                    return;
                }
            }
        };

        // Start polling after 1 second delay
        handler.postDelayed(pollRunnable, 1000);
    }

    private void showRenameDialog(CollectionFile file) {
        EditText input = new EditText(requireContext());
        input.setText(file.getName());
        input.setHint("New name");
        input.selectAll();

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Rename " + (file.isDirectory() ? "Folder" : "File"))
                .setView(input)
                .setPositiveButton("Rename", (dialogInterface, i) -> {
                    String newName = input.getText().toString().trim();
                    if (!newName.isEmpty() && !newName.equals(file.getName())) {
                        renameFileOrFolder(file, newName);
                    } else if (newName.isEmpty()) {
                        Toast.makeText(requireContext(), "Name cannot be empty", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .create();
        dialog.show();

        // Force button text to white
        if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(0xFFFFFFFF);
        }
        if (dialog.getButton(AlertDialog.BUTTON_NEGATIVE) != null) {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(0xFFFFFFFF);
        }
    }

    private void showDeleteConfirmDialog(CollectionFile file) {
        String message = file.isDirectory() ?
                "Delete folder \"" + file.getName() + "\" and all its contents?" :
                "Delete file \"" + file.getName() + "\"?";

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Confirm Delete")
                .setMessage(message)
                .setPositiveButton("Delete", (dialogInterface, i) -> {
                    deleteFileOrFolder(file);
                })
                .setNegativeButton("Cancel", null)
                .create();
        dialog.show();

        // Force button text to white
        if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(0xFFFFFFFF);
        }
        if (dialog.getButton(AlertDialog.BUTTON_NEGATIVE) != null) {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(0xFFFFFFFF);
        }
    }

    private void renameFileOrFolder(CollectionFile file, String newName) {
        if (collection == null || collection.getStoragePath() == null) {
            Toast.makeText(requireContext(), "Collection path not available", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            File collectionRoot = new File(collection.getStoragePath());
            File oldFile = new File(collectionRoot, file.getPath());

            // Calculate new path
            String parentPath = "";
            if (file.getPath().contains("/")) {
                parentPath = file.getPath().substring(0, file.getPath().lastIndexOf('/') + 1);
            }
            String newPath = parentPath + newName;
            File newFile = new File(collectionRoot, newPath);

            if (newFile.exists()) {
                Toast.makeText(requireContext(), "A file or folder with this name already exists", Toast.LENGTH_SHORT).show();
                return;
            }

            if (oldFile.renameTo(newFile)) {
                Toast.makeText(requireContext(), "Renamed successfully", Toast.LENGTH_SHORT).show();

                // Re-scan the collection folder to update file list
                rescanCollection();
                loadDirectory(currentPath);
            } else {
                Toast.makeText(requireContext(), "Failed to rename", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteFileOrFolder(CollectionFile file) {
        if (collection == null || collection.getStoragePath() == null) {
            Toast.makeText(requireContext(), "Collection path not available", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            File collectionRoot = new File(collection.getStoragePath());
            File fileToDelete = new File(collectionRoot, file.getPath());

            if (deleteRecursive(fileToDelete)) {
                Toast.makeText(requireContext(), "Deleted successfully", Toast.LENGTH_SHORT).show();

                // Re-scan the collection folder to update file list
                rescanCollection();
                loadDirectory(currentPath);
            } else {
                Toast.makeText(requireContext(), "Failed to delete", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private boolean deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        return fileOrDirectory.delete();
    }

    private void openFile(CollectionFile file) {
        android.util.Log.i("CollectionBrowser", "openFile called: " + file.getName() + ", isRemoteMode=" + isRemoteMode);
        if (isRemoteMode) {
            // Download remote file first, then open
            android.util.Log.i("CollectionBrowser", "Downloading remote file: " + file.getPath());
            downloadAndOpenRemoteFile(file);
        } else {
            android.util.Log.i("CollectionBrowser", "Opening local file: " + file.getPath());
            openLocalFile(file);
        }
    }

    private void openLocalFile(CollectionFile file) {
        if (collection == null || collection.getStoragePath() == null) {
            Toast.makeText(getContext(), "Collection path not available", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            File collectionRoot = new File(collection.getStoragePath());
            File actualFile = new File(collectionRoot, file.getPath());

            if (!actualFile.exists()) {
                Toast.makeText(getContext(), "File not found", Toast.LENGTH_SHORT).show();
                return;
            }

            Uri fileUri = FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().getPackageName() + ".fileprovider",
                    actualFile
            );

            String mimeType = getMimeType(file.getPath());
            if (mimeType == null || mimeType.isEmpty()) {
                mimeType = "*/*";
            }

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(fileUri, mimeType);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            try {
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(getContext(), "No app found to open this file", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(getContext(), "Error opening file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void downloadAndOpenRemoteFile(CollectionFile file) {
        new Thread(() -> {
            try {
                // Ensure we have the collection folder structure
                File collectionsDir = new File(requireContext().getFilesDir(), "collections");
                File collectionFolder = new File(collectionsDir, collection.getId());

                // Create subdirectories if needed
                String filePath = file.getPath();
                File targetFile = new File(collectionFolder, filePath);

                // Check if file already exists locally
                if (targetFile.exists()) {
                    // File already downloaded, just open it
                    collection.setStoragePath(collectionFolder.getAbsolutePath());
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            openLocalFile(file);
                        });
                    }
                    return;
                }

                // File doesn't exist, need to download
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "Downloading file...", Toast.LENGTH_SHORT).show();
                    });
                }

                // Check if collection is already loadable by CollectionLoader
                boolean isCollectionLoadable = false;
                if (collectionFolder.exists()) {
                    // Try to load the collection to see if it's valid
                    List<Collection> loadedCollections = CollectionLoader.loadCollectionsFromAppStorage(requireContext());
                    for (Collection c : loadedCollections) {
                        if (c.getId().equals(collection.getId())) {
                            isCollectionLoadable = true;
                            android.util.Log.i("CollectionBrowser", "Collection already exists and is loadable");
                            break;
                        }
                    }
                }

                boolean isNewCollection = !isCollectionLoadable;

                // If collection is not loadable, ensure all metadata files exist
                if (isNewCollection) {
                    android.util.Log.i("CollectionBrowser", "Collection not loadable, ensuring all metadata files exist");

                    // Create collection folder if it doesn't exist
                    if (!collectionFolder.exists()) {
                        collectionFolder.mkdirs();
                    }

                    // Generate collection.js and security.json
                    downloadCollectionMetadata(collectionFolder);

                    // Ensure extra directory exists
                    File extraDir = new File(collectionFolder, "extra");
                    if (!extraDir.exists()) {
                        extraDir.mkdirs();
                    }
                }

                File parentDir = targetFile.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs();
                }

                // Download the file using P2PHttpClient
                String path = "/api/collections/" + collection.getId() + "/file/" + filePath;

                // Start or resume progress tracking
                String fileId = collection.getId() + "/" + filePath;
                offgrid.geogram.util.DownloadProgress downloadProgress =
                    offgrid.geogram.util.DownloadProgress.getInstance();

                // Use getOrCreateDownload to resume if download already exists
                offgrid.geogram.util.DownloadProgress.DownloadStatus downloadStatus =
                    downloadProgress.getOrCreateDownload(fileId, file.getName(), file.getSize());

                // If download is already completed, don't restart it
                if (downloadStatus.completed) {
                    android.util.Log.i("CollectionBrowser", "Download already completed, skipping: " + file.getName());
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            Toast.makeText(getContext(), "File already downloaded", Toast.LENGTH_SHORT).show();
                        });
                    }
                    return;
                }

                // Resume if paused
                if (downloadStatus.paused) {
                    downloadStatus.resume();
                    android.util.Log.i("CollectionBrowser", "Resuming paused download: " + file.getName());
                }

                offgrid.geogram.p2p.P2PHttpClient httpClient = new offgrid.geogram.p2p.P2PHttpClient(getContext());
                offgrid.geogram.p2p.P2PHttpClient.InputStreamResponse streamResponse =
                    httpClient.getInputStream(deviceId, remoteIp, path, 30000);

                if (streamResponse.isSuccess()) {
                    try {
                        // Check if resuming a partial download
                        boolean appendMode = targetFile.exists() && downloadStatus.downloadedBytes > 0;
                        long existingBytes = appendMode ? targetFile.length() : 0;

                        if (appendMode) {
                            android.util.Log.i("CollectionBrowser", "Resuming download from " + existingBytes + " bytes");
                        }

                        // Download file with progress tracking
                        try (InputStream in = streamResponse.stream;
                             java.io.FileOutputStream out = new java.io.FileOutputStream(targetFile, appendMode)) {

                            byte[] buffer = new byte[8192];
                            int bytesRead;
                            long totalBytesRead = existingBytes; // Start from existing progress
                            int saveCounter = 0;

                            while ((bytesRead = in.read(buffer)) != -1) {
                                out.write(buffer, 0, bytesRead);
                                totalBytesRead += bytesRead;

                                // Update progress every chunk
                                downloadStatus.updateProgress(totalBytesRead);

                                // Save progress to disk every 10 chunks (~80KB)
                                saveCounter++;
                                if (saveCounter >= 10) {
                                    DownloadProgress.getInstance().save();
                                    saveCounter = 0;
                                }

                                // Log progress every 100KB for debugging
                                if (totalBytesRead % 102400 < 8192) {
                                    android.util.Log.d("CollectionBrowser",
                                        "Download progress: " + downloadStatus.percentComplete + "% (" +
                                        downloadStatus.getFormattedProgress() + ") @ " +
                                        downloadStatus.getFormattedSpeed());
                                }
                            }

                            // Mark as completed and save
                            downloadStatus.markCompleted();
                            DownloadProgress.getInstance().save();
                            android.util.Log.i("CollectionBrowser", "Download completed: " + filePath);
                        } finally {
                            // Close the connection
                            streamResponse.close();
                        }

                        // Update collection storage path
                        collection.setStoragePath(collectionFolder.getAbsolutePath());

                        // Update local tree-data.js with downloaded file
                        updateLocalTreeData(collectionFolder, file);

                        // Log collection structure for debugging
                        android.util.Log.i("CollectionBrowser", "Collection folder: " + collectionFolder.getAbsolutePath());
                        android.util.Log.i("CollectionBrowser", "Files in collection folder:");
                        logDirectoryContents(collectionFolder, "  ");

                        // Open the downloaded file on main thread
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                Toast.makeText(getContext(), "Download complete", Toast.LENGTH_SHORT).show();
                                openLocalFile(file);

                                // If this was a new collection, notify that it's now available locally
                                if (isNewCollection) {
                                    Toast.makeText(getContext(), "Collection added to your device", Toast.LENGTH_SHORT).show();

                                    // Broadcast that a new collection was added
                                    Intent intent = new Intent("offgrid.geogram.COLLECTION_ADDED");
                                    intent.putExtra("collection_id", collection.getId());
                                    androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(requireContext())
                                        .sendBroadcast(intent);
                                }
                            });
                        }
                    } catch (Exception e) {
                        downloadStatus.markFailed(e.getMessage());
                        DownloadProgress.getInstance().save();
                        streamResponse.close();
                        throw e;
                    }
                } else {
                    String errorMsg = streamResponse.errorMessage != null ? streamResponse.errorMessage : "Unknown error";
                    downloadStatus.markFailed("HTTP " + streamResponse.statusCode + ": " + errorMsg);
                    DownloadProgress.getInstance().save();
                    showDownloadError("Failed to download file (HTTP " + streamResponse.statusCode + "): " + errorMsg);
                }
            } catch (Exception e) {
                android.util.Log.e("CollectionBrowser", "Error downloading file: " + e.getMessage());
                showDownloadError("Download failed: " + e.getMessage());
            }
        }).start();
    }

    private void updateLocalTreeData(File collectionFolder, CollectionFile downloadedFile) {
        try {
            // Ensure extra/ folder exists
            File extraDir = new File(collectionFolder, "extra");
            if (!extraDir.exists()) {
                extraDir.mkdirs();
            }

            File treeDataJs = new File(extraDir, "tree-data.js");

            // Load existing entries if file exists
            List<CollectionFile> existingFiles = new ArrayList<>();
            if (treeDataJs.exists()) {
                try {
                    String content = readFileContent(treeDataJs);
                    int startIdx = content.indexOf('[');
                    int endIdx = content.lastIndexOf(']');
                    if (startIdx != -1 && endIdx != -1) {
                        String jsonArrayStr = content.substring(startIdx, endIdx + 1);
                        JsonArray filesArray = JsonParser.parseString(jsonArrayStr).getAsJsonArray();

                        for (int i = 0; i < filesArray.size(); i++) {
                            JsonObject fileObj = filesArray.get(i).getAsJsonObject();
                            String path = fileObj.get("path").getAsString();
                            String type = fileObj.get("type").getAsString();
                            String fileName = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;

                            CollectionFile.FileType fileType = "directory".equals(type) ?
                                    CollectionFile.FileType.DIRECTORY : CollectionFile.FileType.FILE;
                            CollectionFile file = new CollectionFile(path, fileName, fileType);

                            if (fileObj.has("size")) {
                                file.setSize(fileObj.get("size").getAsLong());
                            }
                            if (fileObj.has("metadata") && fileObj.getAsJsonObject("metadata").has("mime_type")) {
                                file.setMimeType(fileObj.getAsJsonObject("metadata").get("mime_type").getAsString());
                            }

                            existingFiles.add(file);
                        }
                    }
                } catch (Exception e) {
                    android.util.Log.e("CollectionBrowser", "Error reading existing tree-data: " + e.getMessage());
                }
            }

            // Check if file already in list
            boolean fileExists = false;
            for (CollectionFile existing : existingFiles) {
                if (existing.getPath().equals(downloadedFile.getPath())) {
                    fileExists = true;
                    break;
                }
            }

            // Add downloaded file if not already present
            if (!fileExists) {
                existingFiles.add(downloadedFile);

                // Add parent directories if needed
                String filePath = downloadedFile.getPath();
                if (filePath.contains("/")) {
                    String[] parts = filePath.split("/");
                    StringBuilder currentPath = new StringBuilder();

                    for (int i = 0; i < parts.length - 1; i++) {
                        if (currentPath.length() > 0) {
                            currentPath.append("/");
                        }
                        currentPath.append(parts[i]);

                        String dirPath = currentPath.toString();
                        boolean dirExists = false;

                        for (CollectionFile existing : existingFiles) {
                            if (existing.getPath().equals(dirPath)) {
                                dirExists = true;
                                break;
                            }
                        }

                        if (!dirExists) {
                            CollectionFile dir = new CollectionFile(dirPath, parts[i], CollectionFile.FileType.DIRECTORY);
                            existingFiles.add(dir);
                        }
                    }
                }
            }

            // Write updated tree-data.js
            StringBuilder sb = new StringBuilder();
            sb.append("window.TREE_DATA = [\n");

            for (int i = 0; i < existingFiles.size(); i++) {
                CollectionFile file = existingFiles.get(i);
                sb.append("  {\n");
                sb.append("    \"path\": \"").append(escapeJson(file.getPath())).append("\",\n");
                sb.append("    \"type\": \"").append(file.isDirectory() ? "directory" : "file").append("\"");

                if (!file.isDirectory()) {
                    sb.append(",\n");
                    sb.append("    \"size\": ").append(file.getSize());

                    if (file.getMimeType() != null) {
                        sb.append(",\n");
                        sb.append("    \"metadata\": {\n");
                        sb.append("      \"mime_type\": \"").append(escapeJson(file.getMimeType())).append("\"\n");
                        sb.append("    }");
                    }
                }

                sb.append("\n  }");
                if (i < existingFiles.size() - 1) {
                    sb.append(",");
                }
                sb.append("\n");
            }

            sb.append("];\n");

            // Write to file
            try (java.io.FileWriter writer = new java.io.FileWriter(treeDataJs)) {
                writer.write(sb.toString());
            }

            android.util.Log.i("CollectionBrowser", "Updated tree-data.js with " + existingFiles.size() + " entries");

        } catch (Exception e) {
            android.util.Log.e("CollectionBrowser", "Error updating tree-data.js: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String readFileContent(File file) throws Exception {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new java.io.FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        return content.toString();
    }

    private String getTreeDataSha1FromConfig(String deviceId, String remoteIp) {
        try {
            offgrid.geogram.p2p.P2PHttpClient httpClient = new offgrid.geogram.p2p.P2PHttpClient(getContext());
            String path = "/api/collections/" + collection.getId() + "/file/config.json";

            offgrid.geogram.p2p.P2PHttpClient.HttpResponse response =
                httpClient.get(deviceId, remoteIp, path, 5000);

            if (response.isSuccess()) {
                JsonObject config = JsonParser.parseString(response.body).getAsJsonObject();
                if (config.has("treeDataSha1")) {
                    String sha1 = config.get("treeDataSha1").getAsString();
                    android.util.Log.i("CollectionBrowser", "Remote tree-data.js SHA1: " + sha1);
                    return sha1;
                }
            }
        } catch (Exception e) {
            android.util.Log.w("CollectionBrowser", "Could not get tree-data SHA1 from config: " + e.getMessage());
        }
        return null;
    }

    private void cacheTreeData(File collectionFolder, String content) {
        try {
            File extraDir = new File(collectionFolder, "extra");
            if (!extraDir.exists()) {
                extraDir.mkdirs();
            }

            File treeDataFile = new File(extraDir, "tree-data.js");
            try (java.io.FileWriter writer = new java.io.FileWriter(treeDataFile)) {
                writer.write(content);
            }
            android.util.Log.i("CollectionBrowser", "Cached tree-data.js to: " + treeDataFile.getAbsolutePath());
        } catch (Exception e) {
            android.util.Log.e("CollectionBrowser", "Error caching tree-data.js: " + e.getMessage());
        }
    }

    private void downloadCollectionMetadata(File collectionFolder) {
        try {
            // Generate collection.js locally based on collection data
            String collectionJsContent = generateCollectionJs(collection);

            // Save collection.js
            File collectionJs = new File(collectionFolder, "collection.js");
            try (java.io.FileWriter writer = new java.io.FileWriter(collectionJs)) {
                writer.write(collectionJsContent);
            }

            // Also create security.json with default public security
            File extraDir = new File(collectionFolder, "extra");
            if (!extraDir.exists()) {
                extraDir.mkdirs();
            }

            File securityJson = new File(extraDir, "security.json");
            String securityContent = "{\n" +
                    "  \"visibility\": \"public\",\n" +
                    "  \"public_read\": true,\n" +
                    "  \"public_write\": false,\n" +
                    "  \"allowed_readers\": [],\n" +
                    "  \"allowed_writers\": []\n" +
                    "}";
            try (java.io.FileWriter writer = new java.io.FileWriter(securityJson)) {
                writer.write(securityContent);
            }

            android.util.Log.i("CollectionBrowser", "Generated collection metadata for: " + collection.getTitle());

        } catch (Exception e) {
            android.util.Log.e("CollectionBrowser", "Error generating metadata: " + e.getMessage());
        }
    }

    private String generateCollectionJs(Collection collection) {
        String timestamp = collection.getUpdated() != null && !collection.getUpdated().isEmpty() ?
                collection.getUpdated() : new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(new Date());

        return "window.COLLECTION_DATA = {\n" +
                "  \"version\": \"1.0\",\n" +
                "  \"collection\": {\n" +
                "    \"id\": \"" + collection.getId() + "\",\n" +
                "    \"title\": \"" + escapeJson(collection.getTitle()) + "\",\n" +
                "    \"description\": \"" + escapeJson(collection.getDescription()) + "\",\n" +
                "    \"created\": \"" + timestamp + "\",\n" +
                "    \"updated\": \"" + timestamp + "\",\n" +
                "    \"signature\": \"\"\n" +
                "  },\n" +
                "  \"statistics\": {\n" +
                "    \"files_count\": " + collection.getFilesCount() + ",\n" +
                "    \"folders_count\": 0,\n" +
                "    \"total_size\": " + collection.getTotalSize() + ",\n" +
                "    \"likes\": 0,\n" +
                "    \"dislikes\": 0,\n" +
                "    \"comments\": 0,\n" +
                "    \"ratings\": {\n" +
                "      \"average\": 0,\n" +
                "      \"count\": 0\n" +
                "    },\n" +
                "    \"downloads\": 0,\n" +
                "    \"last_computed\": \"" + timestamp + "\"\n" +
                "  },\n" +
                "  \"views\": {\n" +
                "    \"total\": 0,\n" +
                "    \"unique\": 0,\n" +
                "    \"tags\": []\n" +
                "  },\n" +
                "  \"tags\": [],\n" +
                "  \"metadata\": {\n" +
                "    \"language\": \"en\",\n" +
                "    \"license\": \"\",\n" +
                "    \"source\": \"remote\"\n" +
                "  }\n" +
                "};\n";
    }

    private void logDirectoryContents(File dir, String indent) {
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    android.util.Log.i("CollectionBrowser", indent + "[DIR] " + file.getName());
                    logDirectoryContents(file, indent + "  ");
                } else {
                    android.util.Log.i("CollectionBrowser", indent + file.getName() + " (" + file.length() + " bytes)");
                }
            }
        }
    }

    private void showDownloadError(String message) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
            });
        }
    }

    private String getMimeType(String path) {
        String extension = "";
        int dotIndex = path.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < path.length() - 1) {
            extension = path.substring(dotIndex + 1).toLowerCase();
        }

        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        return mimeType != null ? mimeType : "*/*";
    }

    private Collection getDummyCollection() {
        // Return dummy collection matching what we created in CollectionsFragment
        Collection c = new Collection(
                "npub1scifi123",
                "Classic Science Fiction Books",
                "A curated collection of timeless science fiction literature"
        );
        c.setFilesCount(4);
        c.setTotalSize(4 * 1024 * 1024);

        CollectionFile booksDir = new CollectionFile("books", "books", CollectionFile.FileType.DIRECTORY);
        c.addFile(booksDir);

        CollectionFile foundation = new CollectionFile("books/foundation.md", "foundation.md", CollectionFile.FileType.FILE);
        foundation.setSize(1456);
        foundation.setDescription("The first book in Asimov's Foundation series");
        foundation.setMimeType("text/markdown");
        foundation.setViews(342);
        c.addFile(foundation);

        CollectionFile dune = new CollectionFile("books/dune.md", "dune.md", CollectionFile.FileType.FILE);
        dune.setSize(1523);
        dune.setDescription("Frank Herbert's epic tale of desert planet Arrakis");
        dune.setMimeType("text/markdown");
        dune.setViews(567);
        c.addFile(dune);

        CollectionFile neuromancer = new CollectionFile("books/neuromancer.md", "neuromancer.md", CollectionFile.FileType.FILE);
        neuromancer.setSize(1398);
        neuromancer.setDescription("William Gibson's groundbreaking cyberpunk novel");
        neuromancer.setMimeType("text/markdown");
        neuromancer.setViews(423);
        c.addFile(neuromancer);

        return c;
    }
}

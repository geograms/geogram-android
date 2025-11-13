package offgrid.geogram.fragments;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;

import java.io.File;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

import offgrid.geogram.MainActivity;
import offgrid.geogram.R;
import offgrid.geogram.models.Collection;
import offgrid.geogram.models.CollectionFile;
import offgrid.geogram.util.CollectionKeysManager;
import offgrid.geogram.util.NostrKeyGenerator;

public class CreateCollectionFragment extends Fragment {

    private EditText inputTitle;
    private EditText inputDescription;
    private CheckBox checkboxAutoFolder;
    private LinearLayout folderChooserContainer;
    private TextView textFolderPath;
    private Button btnChooseFolder;
    private ImageButton btnBack;
    private Button btnSave;

    private Uri selectedFolderUri;
    private String selectedFolderPath;
    private boolean useAutoFolder = true;

    private ActivityResultLauncher<Intent> folderPickerLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Register folder picker launcher
        folderPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        selectedFolderUri = result.getData().getData();
                        if (selectedFolderUri != null) {
                            handleFolderSelection(selectedFolderUri);
                        }
                    }
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_create_collection, container, false);

        inputTitle = view.findViewById(R.id.input_title);
        inputDescription = view.findViewById(R.id.input_description);
        checkboxAutoFolder = view.findViewById(R.id.checkbox_auto_folder);
        folderChooserContainer = view.findViewById(R.id.folder_chooser_container);
        textFolderPath = view.findViewById(R.id.text_folder_path);
        btnChooseFolder = view.findViewById(R.id.btn_choose_folder);
        btnBack = view.findViewById(R.id.btn_back);
        btnSave = view.findViewById(R.id.btn_save);

        setupListeners();

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Hide top action bar for detail screens
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setTopActionBarVisible(false);
        }
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().getSupportFragmentManager().popBackStack();
            }
        });

        checkboxAutoFolder.setOnCheckedChangeListener((buttonView, isChecked) -> {
            useAutoFolder = isChecked;
            folderChooserContainer.setVisibility(isChecked ? View.GONE : View.VISIBLE);
            if (isChecked) {
                selectedFolderUri = null;
                selectedFolderPath = null;
            }
        });

        btnChooseFolder.setOnClickListener(v -> openFolderPicker());

        btnSave.setOnClickListener(v -> saveCollection());
    }

    private void openFolderPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        folderPickerLauncher.launch(intent);
    }

    private void handleFolderSelection(Uri uri) {
        try {
            // Take persistable permission for both read and write
            if (getActivity() != null) {
                getActivity().getContentResolver().takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                );
            }

            DocumentFile folder = DocumentFile.fromTreeUri(requireContext(), uri);
            if (folder != null && folder.isDirectory()) {
                selectedFolderPath = folder.getName();
                if (selectedFolderPath == null) {
                    selectedFolderPath = uri.getLastPathSegment();
                }
                textFolderPath.setText(selectedFolderPath);
                textFolderPath.setTextColor(getResources().getColor(android.R.color.white, null));
            }
        } catch (Exception e) {
            Toast.makeText(getContext(), "Error selecting folder: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void saveCollection() {
        String title = inputTitle.getText().toString().trim();
        String description = inputDescription.getText().toString().trim();

        // Validation
        if (title.isEmpty()) {
            Toast.makeText(getContext(), "Please enter a title", Toast.LENGTH_SHORT).show();
            inputTitle.requestFocus();
            return;
        }

        if (!useAutoFolder && selectedFolderUri == null) {
            Toast.makeText(getContext(), "Please select a folder", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Uri folderUri;

            if (useAutoFolder) {
                // Create folder automatically in app's files directory
                folderUri = createAutoFolder(title);
            } else {
                // Use selected folder
                folderUri = selectedFolderUri;
            }

            // Create collection metadata
            Collection collection = createCollectionFromFolder(title, description, folderUri);

            // Write collection files to disk
            writeCollectionFiles(collection, folderUri);

            Toast.makeText(getContext(),
                    "Collection created successfully!\nFiles: " + collection.getFilesCount(),
                    Toast.LENGTH_LONG).show();

            // Go back to collections list
            if (getActivity() != null) {
                getActivity().getSupportFragmentManager().popBackStack();
            }

        } catch (Exception e) {
            Toast.makeText(getContext(), "Error creating collection: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private Uri createAutoFolder(String collectionTitle) throws Exception {
        // Create collections directory in app's files directory
        java.io.File collectionsDir = new java.io.File(requireContext().getFilesDir(), "collections");
        if (!collectionsDir.exists()) {
            if (!collectionsDir.mkdirs()) {
                throw new Exception("Failed to create collections directory");
            }
        }

        // Create folder for this collection (sanitize title for folder name)
        // 1. Replace spaces with underscores
        // 2. Convert to lowercase
        // 3. Remove special characters except underscore and hyphen
        // 4. Truncate to 50 characters max
        String folderName = collectionTitle
                .replace(' ', '_')
                .toLowerCase()
                .replaceAll("[^a-z0-9-_]", "_");

        // Truncate to 50 characters
        if (folderName.length() > 50) {
            folderName = folderName.substring(0, 50);
        }

        // Remove trailing underscores if any
        folderName = folderName.replaceAll("_+$", "");

        // Ensure folder name is not empty
        if (folderName.isEmpty()) {
            folderName = "collection";
        }

        java.io.File collectionFolder = new java.io.File(collectionsDir, folderName);

        // If folder exists, add number suffix
        int counter = 1;
        while (collectionFolder.exists()) {
            String suffix = "_" + counter;
            // Make sure the full name with suffix doesn't exceed 50 chars
            String baseName = folderName;
            if (baseName.length() + suffix.length() > 50) {
                baseName = baseName.substring(0, 50 - suffix.length());
            }
            collectionFolder = new java.io.File(collectionsDir, baseName + suffix);
            counter++;
        }

        if (!collectionFolder.mkdirs()) {
            throw new Exception("Failed to create collection folder");
        }

        return Uri.fromFile(collectionFolder);
    }

    private void writeCollectionFiles(Collection collection, Uri folderUri) throws Exception {
        if ("file".equals(folderUri.getScheme())) {
            // Auto-created folder - use regular file I/O
            java.io.File folder = new java.io.File(folderUri.getPath());

            // Create extra/ subdirectory
            java.io.File extraDir = new java.io.File(folder, "extra");
            if (!extraDir.exists() && !extraDir.mkdirs()) {
                throw new Exception("Failed to create extra/ directory");
            }

            // Create collection.js file (main metadata)
            java.io.File collectionJs = new java.io.File(folder, "collection.js");
            writeToFile(collectionJs, generateCollectionJs(collection));

            // Create extra/security.json
            java.io.File securityJson = new java.io.File(extraDir, "security.json");
            writeToFile(securityJson, generateSecurityJson(collection));

            // Create extra/tree-data.js (empty for now, will be populated when files are added)
            java.io.File treeDataJs = new java.io.File(extraDir, "tree-data.js");
            writeToFile(treeDataJs, generateTreeDataJs(collection));

        } else {
            // User-selected folder - use Storage Access Framework
            DocumentFile folder = DocumentFile.fromTreeUri(requireContext(), folderUri);
            if (folder == null || !folder.isDirectory()) {
                throw new Exception("Invalid folder");
            }

            // Create extra/ subdirectory
            DocumentFile extraDir = folder.findFile("extra");
            if (extraDir == null) {
                extraDir = folder.createDirectory("extra");
            }
            if (extraDir == null || !extraDir.isDirectory()) {
                throw new Exception("Failed to create extra/ directory");
            }

            // Create collection.js file (main metadata)
            DocumentFile collectionJs = folder.createFile("application/javascript", "collection.js");
            if (collectionJs != null) {
                writeToDocumentFile(collectionJs, generateCollectionJs(collection));
            }

            // Create extra/security.json
            DocumentFile securityJson = extraDir.createFile("application/json", "security.json");
            if (securityJson != null) {
                writeToDocumentFile(securityJson, generateSecurityJson(collection));
            }

            // Create extra/tree-data.js
            DocumentFile treeDataJs = extraDir.createFile("application/javascript", "tree-data.js");
            if (treeDataJs != null) {
                writeToDocumentFile(treeDataJs, generateTreeDataJs(collection));
            }
        }
    }

    private void writeToFile(java.io.File file, String content) throws Exception {
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    private void writeToDocumentFile(DocumentFile file, String content) throws Exception {
        try (OutputStream out = requireContext().getContentResolver().openOutputStream(file.getUri())) {
            if (out != null) {
                out.write(content.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    private String generateCollectionJs(Collection collection) {
        return "window.COLLECTION_DATA = {\n" +
                "  \"version\": \"1.0\",\n" +
                "  \"collection\": {\n" +
                "    \"id\": \"" + collection.getId() + "\",\n" +
                "    \"title\": \"" + escapeJson(collection.getTitle()) + "\",\n" +
                "    \"description\": \"" + escapeJson(collection.getDescription()) + "\",\n" +
                "    \"created\": \"" + collection.getUpdated() + "\",\n" +
                "    \"updated\": \"" + collection.getUpdated() + "\",\n" +
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
                "    \"last_computed\": \"" + collection.getUpdated() + "\"\n" +
                "  },\n" +
                "  \"views\": {\n" +
                "    \"total\": 0,\n" +
                "    \"unique\": 0,\n" +
                "    \"tags\": []\n" +
                "  },\n" +
                "  \"tags\": [],\n" +
                "  \"metadata\": {\n" +
                "    \"category\": \"general\",\n" +
                "    \"language\": \"en\",\n" +
                "    \"license\": \"\",\n" +
                "    \"copyright\": \"\",\n" +
                "    \"website\": \"\",\n" +
                "    \"contact\": {\n" +
                "      \"email\": \"\",\n" +
                "      \"nostr\": \"" + collection.getId() + "\"\n" +
                "    },\n" +
                "    \"donation_address\": \"\",\n" +
                "    \"attribution\": \"\",\n" +
                "    \"content_rating\": \"G\",\n" +
                "    \"mature_content\": false\n" +
                "  }\n" +
                "};\n";
    }

    private String generateSecurityJson(Collection collection) {
        return "{\n" +
                "  \"version\": \"1.0\",\n" +
                "  \"visibility\": \"public\",\n" +
                "  \"permissions\": {\n" +
                "    \"can_users_read\": true,\n" +
                "    \"can_users_submit\": false,\n" +
                "    \"submit_requires_approval\": true,\n" +
                "    \"can_users_comment\": true,\n" +
                "    \"can_users_like\": true,\n" +
                "    \"can_users_dislike\": true,\n" +
                "    \"can_users_rate\": true,\n" +
                "    \"whitelisted_users\": [],\n" +
                "    \"blocked_users\": []\n" +
                "  },\n" +
                "  \"admin\": {\n" +
                "    \"npub\": \"" + collection.getId() + "\",\n" +
                "    \"name\": \"Collection Owner\",\n" +
                "    \"contact\": \"\"\n" +
                "  },\n" +
                "  \"subscribers\": [],\n" +
                "  \"permitted_contributors\": [],\n" +
                "  \"content_warnings\": [],\n" +
                "  \"age_restriction\": {\n" +
                "    \"enabled\": false,\n" +
                "    \"minimum_age\": 0,\n" +
                "    \"verification_required\": false\n" +
                "  },\n" +
                "  \"encryption\": {\n" +
                "    \"enabled\": false,\n" +
                "    \"method\": null,\n" +
                "    \"encrypted_files\": []\n" +
                "  }\n" +
                "}\n";
    }

    private String generateTreeDataJs(Collection collection) {
        StringBuilder sb = new StringBuilder();
        sb.append("window.TREE_DATA = [\n");

        for (int i = 0; i < collection.getFiles().size(); i++) {
            CollectionFile file = collection.getFiles().get(i);
            sb.append("  {\n");
            sb.append("    \"path\": \"").append(escapeJson(file.getPath())).append("\",\n");
            sb.append("    \"name\": \"").append(escapeJson(file.getName())).append("\",\n");
            sb.append("    \"type\": \"").append(file.isDirectory() ? "directory" : "file").append("\",\n");
            if (!file.isDirectory()) {
                sb.append("    \"size\": ").append(file.getSize()).append(",\n");
                if (file.getMimeType() != null) {
                    sb.append("    \"mime_type\": \"").append(escapeJson(file.getMimeType())).append("\",\n");
                }
                sb.append("    \"sha1\": \"\",\n");
                sb.append("    \"tlsh\": \"\"\n");
            } else {
                sb.append("    \"children\": []\n");
            }
            sb.append("  }");
            if (i < collection.getFiles().size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }

        sb.append("];\n");
        return sb.toString();
    }

    private String generateDataJs_OLD(Collection collection) {
        StringBuilder sb = new StringBuilder();
        sb.append("const collectionData = {\n");
        sb.append("  id: \"").append(collection.getId()).append("\",\n");
        sb.append("  title: \"").append(escapeJson(collection.getTitle())).append("\",\n");
        sb.append("  description: \"").append(escapeJson(collection.getDescription())).append("\",\n");
        sb.append("  filesCount: ").append(collection.getFilesCount()).append(",\n");
        sb.append("  totalSize: ").append(collection.getTotalSize()).append(",\n");
        sb.append("  updated: \"").append(collection.getUpdated()).append("\",\n");
        sb.append("  files: [\n");

        for (int i = 0; i < collection.getFiles().size(); i++) {
            CollectionFile file = collection.getFiles().get(i);
            sb.append("    {\n");
            sb.append("      path: \"").append(escapeJson(file.getPath())).append("\",\n");
            sb.append("      name: \"").append(escapeJson(file.getName())).append("\",\n");
            sb.append("      type: \"").append(file.isDirectory() ? "directory" : "file").append("\",\n");
            if (!file.isDirectory()) {
                sb.append("      size: ").append(file.getSize()).append(",\n");
                if (file.getMimeType() != null) {
                    sb.append("      mimeType: \"").append(escapeJson(file.getMimeType())).append("\",\n");
                }
            }
            sb.append("    }");
            if (i < collection.getFiles().size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }

        sb.append("  ]\n");
        sb.append("};\n\n");
        sb.append("if (typeof module !== 'undefined' && module.exports) {\n");
        sb.append("  module.exports = collectionData;\n");
        sb.append("}\n");

        return sb.toString();
    }

    private String generateManifest(Collection collection) {
        return "{\n" +
                "  \"id\": \"" + collection.getId() + "\",\n" +
                "  \"title\": \"" + escapeJson(collection.getTitle()) + "\",\n" +
                "  \"description\": \"" + escapeJson(collection.getDescription()) + "\",\n" +
                "  \"version\": \"1.0\",\n" +
                "  \"updated\": \"" + collection.getUpdated() + "\"\n" +
                "}\n";
    }

    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private Collection createCollectionFromFolder(String title, String description, Uri folderUri) {
        // Generate NOSTR key pair for this collection
        NostrKeyGenerator.NostrKeys keys = NostrKeyGenerator.generateKeyPair();
        String collectionId = keys.npub;

        // Store the npub/nsec pair in config
        CollectionKeysManager.storeKeys(requireContext(), keys.npub, keys.nsec);

        Collection collection = new Collection(collectionId, title, description);

        // Set timestamp
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        collection.setUpdated(sdf.format(new Date()));

        // Index files from folder
        if ("file".equals(folderUri.getScheme())) {
            // Auto-created folder - use regular file I/O
            java.io.File folder = new java.io.File(folderUri.getPath());
            if (folder.exists() && folder.isDirectory()) {
                scanFileFolder(collection, folder, "");
            }
        } else {
            // User-selected folder - use Storage Access Framework
            DocumentFile folder = DocumentFile.fromTreeUri(requireContext(), folderUri);
            if (folder != null && folder.isDirectory()) {
                scanDocumentFolder(collection, folder, "", null);
            }
        }

        // Calculate totals
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

        return collection;
    }

    private void scanFileFolder(Collection collection, java.io.File folder, String currentPath) {
        java.io.File[] files = folder.listFiles();
        if (files == null) return;

        for (java.io.File file : files) {
            String fileName = file.getName();
            String filePath = currentPath.isEmpty() ? fileName : currentPath + "/" + fileName;

            if (file.isDirectory()) {
                CollectionFile dirEntry = new CollectionFile(filePath, fileName,
                        CollectionFile.FileType.DIRECTORY);
                collection.addFile(dirEntry);
                scanFileFolder(collection, file, filePath);
            } else {
                CollectionFile fileEntry = new CollectionFile(filePath, fileName,
                        CollectionFile.FileType.FILE);
                fileEntry.setSize(file.length());
                collection.addFile(fileEntry);
            }
        }
    }

    private void scanDocumentFolder(Collection collection, DocumentFile folder, String currentPath, String parentPath) {
        if (folder == null || !folder.isDirectory()) {
            return;
        }

        DocumentFile[] files = folder.listFiles();
        if (files == null) return;

        for (DocumentFile file : files) {
            String fileName = file.getName();
            if (fileName == null) continue;

            String filePath = currentPath.isEmpty() ? fileName : currentPath + "/" + fileName;

            if (file.isDirectory()) {
                // Add directory entry
                CollectionFile dirEntry = new CollectionFile(filePath, fileName,
                        CollectionFile.FileType.DIRECTORY);
                collection.addFile(dirEntry);

                // Recursively scan subdirectory
                scanDocumentFolder(collection, file, filePath, currentPath);
            } else {
                // Add file entry
                CollectionFile fileEntry = new CollectionFile(filePath, fileName,
                        CollectionFile.FileType.FILE);
                fileEntry.setSize(file.length());

                // Determine mime type from extension
                String mimeType = file.getType();
                if (mimeType != null) {
                    fileEntry.setMimeType(mimeType);
                }

                collection.addFile(fileEntry);
            }
        }
    }
}

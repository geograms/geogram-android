package offgrid.geogram.fragments;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;

import offgrid.geogram.MainActivity;
import offgrid.geogram.R;
import offgrid.geogram.util.BackupManager;

public class BackupFragment extends Fragment {

    private View view = null;
    private BackupManager backupManager;

    // File picker launchers
    private ActivityResultLauncher<Intent> exportFileLauncher;
    private ActivityResultLauncher<Intent> importFileLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        backupManager = new BackupManager(requireContext());

        // Initialize file pickers
        exportFileLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        performExport(uri);
                    }
                }
            }
        );

        importFileLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        performImport(uri);
                    }
                }
            }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_backup, container, false);

        // Back button functionality
        android.widget.ImageButton btnBack = view.findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> requireActivity().onBackPressed());

        // Initialize UI components
        initializeUI(view);

        return view;
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

    private void initializeUI(View view) {
        // Import and Export buttons
        Button btnImport = view.findViewById(R.id.btn_import);
        Button btnExport = view.findViewById(R.id.btn_export);

        // Backup options switches
        SwitchCompat switchCollections = view.findViewById(R.id.switch_backup_collections);
        SwitchCompat switchSettings = view.findViewById(R.id.switch_backup_settings);
        SwitchCompat switchIdentity = view.findViewById(R.id.switch_backup_identity);

        // Identity is automatically checked by default
        switchIdentity.setChecked(true);

        // Set up button click listeners
        btnImport.setOnClickListener(v -> startImport());
        btnExport.setOnClickListener(v -> startExport());
    }

    private void startExport() {
        SwitchCompat switchCollections = view.findViewById(R.id.switch_backup_collections);
        SwitchCompat switchSettings = view.findViewById(R.id.switch_backup_settings);
        SwitchCompat switchIdentity = view.findViewById(R.id.switch_backup_identity);

        boolean includeCollections = switchCollections.isChecked();
        boolean includeSettings = switchSettings.isChecked();
        boolean includeIdentity = switchIdentity.isChecked();

        // Generate filename
        String callsign = includeIdentity ? backupManager.getCallsign() : null;
        String filename = BackupManager.generateBackupFilename(includeIdentity, callsign);

        // Create file picker intent
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_TITLE, filename);

        exportFileLauncher.launch(intent);
    }

    private void startImport() {
        // Create file picker intent for ZIP files
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");

        importFileLauncher.launch(intent);
    }

    private void performExport(Uri destinationUri) {
        SwitchCompat switchCollections = view.findViewById(R.id.switch_backup_collections);
        SwitchCompat switchSettings = view.findViewById(R.id.switch_backup_settings);
        SwitchCompat switchIdentity = view.findViewById(R.id.switch_backup_identity);

        boolean includeCollections = switchCollections.isChecked();
        boolean includeSettings = switchSettings.isChecked();
        boolean includeIdentity = switchIdentity.isChecked();

        boolean success = backupManager.exportData(includeCollections, includeSettings, includeIdentity, destinationUri);

        if (success) {
            Toast.makeText(getContext(), "Backup exported successfully", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(getContext(), "Failed to export backup", Toast.LENGTH_LONG).show();
        }
    }

    private void performImport(Uri sourceUri) {
        SwitchCompat switchCollections = view.findViewById(R.id.switch_backup_collections);
        SwitchCompat switchSettings = view.findViewById(R.id.switch_backup_settings);
        SwitchCompat switchIdentity = view.findViewById(R.id.switch_backup_identity);

        boolean includeCollections = switchCollections.isChecked();
        boolean includeSettings = switchSettings.isChecked();
        boolean includeIdentity = switchIdentity.isChecked();

        boolean success = backupManager.importData(includeCollections, includeSettings, includeIdentity, sourceUri);

        if (success) {
            // Reload settings to apply changes immediately
            offgrid.geogram.core.Central.getInstance().loadSettings(requireContext());
            Toast.makeText(getContext(), "Backup imported successfully", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(getContext(), "Failed to import backup", Toast.LENGTH_LONG).show();
        }
    }
}
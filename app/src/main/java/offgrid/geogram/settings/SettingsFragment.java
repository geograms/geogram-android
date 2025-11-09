package offgrid.geogram.settings;

// Removed (legacy Google Play Services code) - import static offgrid.geogram.old.bluetooth_old.broadcast.BroadcastSender.sendProfileToEveryone;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import offgrid.geogram.MainActivity;
import offgrid.geogram.R;
import offgrid.geogram.core.Central;

public class SettingsFragment extends Fragment {

    private static SettingsFragment instance;
    private SettingsUser settings = null;
    private View view = null;

    // Private constructor to enforce singleton pattern
    private SettingsFragment() {
        // Required empty constructor
    }

    /**
     * Get the singleton instance of SettingsFragment.
     */
    public static synchronized SettingsFragment getInstance() {
        if (instance == null) {
            instance = new SettingsFragment();
        }
        return instance;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_settings, container, false);

        // Back button functionality
        ImageButton btnBack = view.findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> requireActivity().onBackPressed());

        // Load settings
        loadSettings();

        // Initialize UI components and bind settings
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

    private void loadSettings() {
        settings = Central.getInstance().getSettings();
        if (settings != null) {
            return;
        }

        try {
            settings = SettingsLoader.loadSettings(requireContext());
        } catch (Exception e) {
            settings = new SettingsUser(); // Default settings if loading fails
            saveSettings(settings);
            Toast.makeText(getContext(),
                    "Failed to load settings. Using defaults.",
                    Toast.LENGTH_LONG).show();
        }

        // Ensure settings are saved to Central
        Central.getInstance().setSettings(settings);
    }

    private void initializeUI(View view) {
        // Auto-generate identity if not present
        if (settings.getNpub() == null || settings.getNpub().isEmpty() ||
                settings.getNsec() == null || settings.getNsec().isEmpty()) {
            generateNewIdentity();
        } else if (settings.getCallsign() == null || settings.getCallsign().isEmpty()) {
            // Derive callsign from existing npub if missing
            try {
                String callsign = IdentityHelper.deriveCallsignFromNpub(settings.getNpub());
                settings.setCallsign(callsign);
                saveSettings(settings);
            } catch (Exception e) {
                // If derivation fails, generate new identity
                generateNewIdentity();
            }
        }

        // Callsign (read-only)
        EditText callsignField = view.findViewById(R.id.edit_callsign);
        callsignField.setText(settings.getCallsign());

        Spinner preferredColorSpinner = view.findViewById(R.id.spinner_preferred_color);
        String[] colorOptions = getResources().getStringArray(R.array.color_options);
        for (int i = 0; i < colorOptions.length; i++) {
            if (colorOptions[i].equals(settings.getPreferredColor())) {
                preferredColorSpinner.setSelection(i);
                break;
            }
        }

        // NOSTR Identity
        EditText npub = view.findViewById(R.id.edit_npub);
        EditText nsec = view.findViewById(R.id.edit_nsec);
        npub.setText(settings.getNpub());
        nsec.setText(settings.getNsec());

        // Save Button
        View saveButton = view.findViewById(R.id.btn_save_settings);
        saveButton.setOnClickListener(v -> {
            saveSettings(npub, nsec, preferredColorSpinner);
            // Removed (legacy) - sendProfileToEveryone used BroadcastSender (Google Play Services)
            requireActivity().onBackPressed(); // Navigate back
        });

        // Copy to clipboard button functionality
        ImageButton btnCopyNSEC = view.findViewById(R.id.btn_copy_nsec);
        btnCopyNSEC.setOnClickListener(v -> copyToClipboard(nsec, "NSEC"));

        ImageButton btnCopyNPUB = view.findViewById(R.id.btn_copy_npub);
        btnCopyNPUB.setOnClickListener(v -> copyToClipboard(npub, "NPUB"));

        // Reset Identity Button
        view.findViewById(R.id.btn_reset_identity).setOnClickListener(v -> {
            new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Reset Identity")
                    .setMessage("This will generate new Nostr keys and callsign. Your old identity will be lost. Continue?")
                    .setPositiveButton("Yes, Reset", (dialog, which) -> {
                        generateNewIdentity();
                        reloadSettings();
                        Toast.makeText(getContext(), "New identity generated", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void generateNewIdentity() {
        try {
            IdentityHelper.NostrIdentity identity = IdentityHelper.generateNewIdentity();
            settings.setNpub(identity.npub);
            settings.setNsec(identity.nsec);
            settings.setCallsign(identity.callsign);
            saveSettings(settings);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Error generating identity: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void copyToClipboard(EditText editText, String label) {
        String textToCopy = editText.getText().toString();
        if (!textToCopy.isEmpty()) {
            ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText(label, textToCopy);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(getContext(), "Copied to clipboard", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getContext(), "Field is empty", Toast.LENGTH_SHORT).show();
        }
    }

    public void reloadSettings() {
        // Callsign
        EditText callsignField = view.findViewById(R.id.edit_callsign);
        callsignField.setText(settings.getCallsign());

        Spinner preferredColorSpinner = view.findViewById(R.id.spinner_preferred_color);
        String[] colorOptions = getResources().getStringArray(R.array.color_options);
        for (int i = 0; i < colorOptions.length; i++) {
            if (colorOptions[i].equals(settings.getPreferredColor())) {
                preferredColorSpinner.setSelection(i);
                break;
            }
        }

        // NOSTR Identity
        EditText npub = view.findViewById(R.id.edit_npub);
        EditText nsec = view.findViewById(R.id.edit_nsec);
        npub.setText(settings.getNpub());
        nsec.setText(settings.getNsec());
    }

    private void saveSettings(SettingsUser settings) {
        try {
            SettingsLoader.saveSettings(requireContext(), settings);
            Toast.makeText(requireContext(), "Settings saved successfully", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void saveSettings(EditText npub, EditText nsec, Spinner preferredColorSpinner) {
        try {
            settings.setNpub(npub.getText().toString());
            settings.setNsec(nsec.getText().toString());
            settings.setPreferredColor(preferredColorSpinner.getSelectedItem().toString());

            // Update callsign from npub if npub was manually changed
            try {
                String callsign = IdentityHelper.deriveCallsignFromNpub(npub.getText().toString());
                settings.setCallsign(callsign);
            } catch (Exception e) {
                // Keep existing callsign if derivation fails
            }

            saveSettings(settings);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Error saving settings" + ": " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}

package offgrid.geogram.settings;

// Removed (legacy Google Play Services code) - import static offgrid.geogram.old.bluetooth_old.broadcast.BroadcastSender.sendProfileToEveryone;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;

import offgrid.geogram.MainActivity;
import offgrid.geogram.R;
import offgrid.geogram.core.Central;
import offgrid.geogram.util.NetworkUtils;

public class SettingsFragment extends Fragment {

    private static SettingsFragment instance;
    private SettingsUser settings = null;
    private View view = null;
    private android.widget.ImageView profileImageView = null;
    private androidx.activity.result.ActivityResultLauncher<Intent> profileImageLauncher;

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

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize profile image launcher
        profileImageLauncher = registerForActivityResult(
                new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null) {
                        android.net.Uri imageUri = result.getData().getData();
                        if (imageUri != null) {
                            try {
                                // Copy image to internal storage
                                java.io.InputStream inputStream = requireContext().getContentResolver().openInputStream(imageUri);
                                if (inputStream != null) {
                                    java.io.File profileDir = new java.io.File(requireContext().getFilesDir(), "profile");
                                    if (!profileDir.exists()) {
                                        profileDir.mkdirs();
                                    }

                                    java.io.File imageFile = new java.io.File(profileDir, "profile_image.jpg");

                                    // Decode and rescale the image to max 200x200
                                    android.graphics.Bitmap originalBitmap = android.graphics.BitmapFactory.decodeStream(inputStream);
                                    inputStream.close();

                                    if (originalBitmap != null) {
                                        // Calculate scaling to fit within 200x200 while maintaining aspect ratio
                                        int width = originalBitmap.getWidth();
                                        int height = originalBitmap.getHeight();
                                        int maxSize = 200;

                                        float scale = Math.min((float) maxSize / width, (float) maxSize / height);
                                        int newWidth = Math.round(width * scale);
                                        int newHeight = Math.round(height * scale);

                                        android.graphics.Bitmap scaledBitmap = android.graphics.Bitmap.createScaledBitmap(
                                            originalBitmap, newWidth, newHeight, true);

                                        // Save the scaled bitmap as JPEG
                                        java.io.FileOutputStream outputStream = new java.io.FileOutputStream(imageFile);
                                        scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, outputStream);
                                        outputStream.close();

                                        // Clean up
                                        if (scaledBitmap != originalBitmap) {
                                            originalBitmap.recycle();
                                        }
                                    } else {
                                        Toast.makeText(getContext(), "Failed to decode image", Toast.LENGTH_SHORT).show();
                                        return;
                                    }

                                    // Save path to preferences
                                    offgrid.geogram.util.ProfilePreferences.setProfileImagePath(requireContext(), imageFile.getAbsolutePath());

                                    // Update UI
                                    if (profileImageView != null) {
                                        android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(imageFile.getAbsolutePath());
                                        if (bitmap != null) {
                                            // Clear the white tint and update display
                                            profileImageView.setImageTintList(null);

                                            // Apply rounded corners
                                            android.graphics.Bitmap roundedBitmap = getRoundedCornerBitmap(bitmap, 12);
                                            profileImageView.setImageBitmap(roundedBitmap);
                                            profileImageView.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                                            profileImageView.setPadding(0, 0, 0, 0);
                                        }
                                    }

                                    Toast.makeText(getContext(), "Profile picture updated", Toast.LENGTH_SHORT).show();
                                }
                            } catch (Exception e) {
                                android.util.Log.e("SettingsFragment", "Error saving profile image: " + e.getMessage());
                                Toast.makeText(getContext(), "Error saving profile image", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                }
        );
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

        // Auto-save all settings when leaving the fragment
        if (view != null) {
            EditText nicknameField = view.findViewById(R.id.edit_nickname);
            EditText descriptionField = view.findViewById(R.id.edit_description);

            if (nicknameField != null && descriptionField != null) {
                String nickname = nicknameField.getText().toString().trim();
                String description = descriptionField.getText().toString().trim();
                offgrid.geogram.util.ProfilePreferences.setNickname(requireContext(), nickname);
                offgrid.geogram.util.ProfilePreferences.setDescription(requireContext(), description);
            }
        }

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

        // Profile fields
        EditText nicknameField = view.findViewById(R.id.edit_nickname);
        EditText descriptionField = view.findViewById(R.id.edit_description);
        profileImageView = view.findViewById(R.id.img_profile_preview);
        android.widget.Button chooseProfileImageButton = view.findViewById(R.id.btn_choose_profile_image);

        // Load saved profile data
        String savedNickname = offgrid.geogram.util.ProfilePreferences.getNickname(requireContext());
        String savedDescription = offgrid.geogram.util.ProfilePreferences.getDescription(requireContext());
        String savedImagePath = offgrid.geogram.util.ProfilePreferences.getProfileImagePath(requireContext());

        nicknameField.setText(savedNickname);
        descriptionField.setText(savedDescription);

        // Load profile image if exists
        if (savedImagePath != null && !savedImagePath.isEmpty()) {
            try {
                android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(savedImagePath);
                if (bitmap != null) {
                    // Clear the white tint and update display
                    profileImageView.setImageTintList(null);

                    // Apply rounded corners
                    android.graphics.Bitmap roundedBitmap = getRoundedCornerBitmap(bitmap, 12);
                    profileImageView.setImageBitmap(roundedBitmap);
                    profileImageView.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                    profileImageView.setPadding(0, 0, 0, 0);
                }
            } catch (Exception e) {
                android.util.Log.e("SettingsFragment", "Error loading profile image: " + e.getMessage());
            }
        }

        // Choose profile image button
        chooseProfileImageButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            profileImageLauncher.launch(intent);
        });

        Spinner preferredColorSpinner = view.findViewById(R.id.spinner_preferred_color);
        String[] colorOptions = getResources().getStringArray(R.array.color_options);

        // Track whether this is the initial setup to avoid triggering listener
        final boolean[] isInitialSetup = {true};

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

        // HTTP API Settings
        SwitchCompat httpApiSwitch = view.findViewById(R.id.switch_http_api);
        TextView httpApiUrlText = view.findViewById(R.id.text_http_api_url);
        ImageButton btnCopyHttpUrl = view.findViewById(R.id.btn_copy_http_url);
        ImageButton btnShareHttpUrl = view.findViewById(R.id.btn_share_http_url);

        // Set initial state
        httpApiSwitch.setChecked(settings.isHttpApiEnabled());
        updateHttpApiUrl(httpApiUrlText, settings.isHttpApiEnabled());

        // Handle switch changes
        httpApiSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settings.setHttpApiEnabled(isChecked);
            saveSettings(settings);
            updateHttpApiUrl(httpApiUrlText, isChecked);

            // Inform user about restart requirement
            if (isChecked) {
                Toast.makeText(getContext(), "HTTP API enabled. Restart app to apply.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), "HTTP API disabled. Restart app to apply.", Toast.LENGTH_SHORT).show();
            }
        });

        // Copy HTTP URL button
        btnCopyHttpUrl.setOnClickListener(v -> {
            String url = NetworkUtils.getServerUrl(45678);
            if (url.startsWith("http://")) {
                ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("HTTP API URL", url);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(getContext(), "URL copied to clipboard", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), "Server URL not available", Toast.LENGTH_SHORT).show();
            }
        });

        // Share HTTP URL button
        btnShareHttpUrl.setOnClickListener(v -> {
            String url = NetworkUtils.getServerUrl(45678);
            if (url.startsWith("http://")) {
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Geogram HTTP API");
                shareIntent.putExtra(Intent.EXTRA_TEXT, "Geogram HTTP API Server:\n" + url + "\n\nEndpoints:\n" +
                        "- POST /api/ble/send - Send BLE message\n" +
                        "- GET /api/logs - Get recent logs\n" +
                        "- GET /api/status - Get server status");
                startActivity(Intent.createChooser(shareIntent, "Share HTTP API URL"));
            } else {
                Toast.makeText(getContext(), "Server URL not available", Toast.LENGTH_SHORT).show();
            }
        });

        // Auto-save nickname when focus is lost
        nicknameField.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                String nickname = nicknameField.getText().toString().trim();
                offgrid.geogram.util.ProfilePreferences.setNickname(requireContext(), nickname);
            }
        });

        // Auto-save description when focus is lost
        descriptionField.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                String description = descriptionField.getText().toString().trim();
                offgrid.geogram.util.ProfilePreferences.setDescription(requireContext(), description);
            }
        });

        // Auto-save preferred color when changed
        preferredColorSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                // Skip the initial selection trigger
                if (isInitialSetup[0]) {
                    isInitialSetup[0] = false;
                    return;
                }

                String selectedColor = colorOptions[position];
                settings.setPreferredColor(selectedColor);
                saveSettings(settings);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                // No action needed
            }
        });

        // Copy to clipboard button functionality
        ImageButton btnCopyNSEC = view.findViewById(R.id.btn_copy_nsec);
        btnCopyNSEC.setOnClickListener(v -> copyToClipboard(nsec, "NSEC"));

        ImageButton btnCopyNPUB = view.findViewById(R.id.btn_copy_npub);
        btnCopyNPUB.setOnClickListener(v -> copyToClipboard(npub, "NPUB"));

        // P2P Settings Navigation
        view.findViewById(R.id.btn_i2p_settings).setOnClickListener(v -> {
            if (getActivity() != null) {
                androidx.fragment.app.FragmentTransaction transaction = getActivity().getSupportFragmentManager().beginTransaction();
                transaction.replace(R.id.fragment_container, offgrid.geogram.fragments.P2PSettingsFragment.newInstance());
                transaction.addToBackStack(null);
                transaction.commit();
            }
        });

        // Reset Identity Button
        view.findViewById(R.id.btn_reset_identity).setOnClickListener(v -> {
            androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Reset Identity")
                    .setMessage("This will generate new Nostr keys and callsign. Your old identity will be lost. Continue?")
                    .setPositiveButton("Yes, Reset", (d, which) -> {
                        generateNewIdentity();
                        reloadSettings();
                        Toast.makeText(getContext(), "New identity generated", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();

            // Set button text colors to white for better readability
            android.widget.Button positiveButton = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE);
            android.widget.Button negativeButton = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE);
            if (positiveButton != null) {
                positiveButton.setTextColor(getResources().getColor(R.color.white, null));
            }
            if (negativeButton != null) {
                negativeButton.setTextColor(getResources().getColor(R.color.white, null));
            }
        });
    }

    private void generateNewIdentity() {
        try {
            IdentityHelper.NostrIdentity identity = IdentityHelper.generateNewIdentity();
            settings.setNpub(identity.npub);
            settings.setNsec(identity.nsec);
            settings.setCallsign(identity.callsign);

            // Set a random preferred color if not already set
            if (settings.getPreferredColor() == null || settings.getPreferredColor().isEmpty()) {
                String[] colorOptions = getResources().getStringArray(R.array.color_options);
                int randomIndex = new java.util.Random().nextInt(colorOptions.length);
                settings.setPreferredColor(colorOptions[randomIndex]);
            }

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
            // No success toast - settings save silently
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

    private void updateHttpApiUrl(TextView textView, boolean enabled) {
        if (!enabled) {
            textView.setText("Server: Disabled");
            textView.setTextColor(getResources().getColor(R.color.gray, null));
        } else {
            String url = NetworkUtils.getServerUrl(45678);
            textView.setText("Server: " + url);
            if (url.startsWith("http://")) {
                textView.setTextColor(getResources().getColor(R.color.green, null));
            } else {
                textView.setTextColor(getResources().getColor(R.color.gray, null));
            }
        }
    }

    /**
     * Create a bitmap with rounded corners
     * @param bitmap The original bitmap
     * @param cornerRadiusDp Corner radius in dp
     * @return Bitmap with rounded corners
     */
    private android.graphics.Bitmap getRoundedCornerBitmap(android.graphics.Bitmap bitmap, int cornerRadiusDp) {
        // Convert dp to pixels
        float density = getResources().getDisplayMetrics().density;
        float cornerRadiusPx = cornerRadiusDp * density;

        android.graphics.Bitmap output = android.graphics.Bitmap.createBitmap(
            bitmap.getWidth(), bitmap.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(output);

        final android.graphics.Paint paint = new android.graphics.Paint();
        final android.graphics.Rect rect = new android.graphics.Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        final android.graphics.RectF rectF = new android.graphics.RectF(rect);

        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);
        canvas.drawRoundRect(rectF, cornerRadiusPx, cornerRadiusPx, paint);

        paint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, rect, rect, paint);

        return output;
    }
}

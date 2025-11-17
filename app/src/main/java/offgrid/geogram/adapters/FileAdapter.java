package offgrid.geogram.adapters;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import offgrid.geogram.R;
import offgrid.geogram.models.CollectionFile;

public class FileAdapter extends RecyclerView.Adapter<FileAdapter.ViewHolder> {

    public interface OnFileClickListener {
        void onFileClick(CollectionFile file);
    }

    public interface OnFileLongClickListener {
        void onFileLongClick(CollectionFile file);
    }

    public interface OnFileDownloadListener {
        void onFileDownload(CollectionFile file);
    }

    public interface OnFolderDownloadListener {
        void onFolderDownload(CollectionFile folder);
    }

    private List<CollectionFile> files;
    private final OnFileClickListener listener;
    private OnFileLongClickListener longClickListener;
    private OnFileDownloadListener downloadListener;
    private OnFolderDownloadListener folderDownloadListener;
    private String collectionStoragePath;
    private boolean isRemoteMode = false;
    private String remoteIp;
    private String deviceId;
    private String collectionNpub;
    private android.content.Context context;
    private java.util.Map<String, Integer> downloadProgress = new java.util.HashMap<>();

    public FileAdapter(OnFileClickListener listener) {
        this.files = new ArrayList<>();
        this.listener = listener;
    }

    public void setCollectionStoragePath(String path) {
        this.collectionStoragePath = path;
    }

    public void setRemoteMode(boolean isRemote, String ip, String deviceId, String npub, android.content.Context context) {
        this.isRemoteMode = isRemote;
        this.remoteIp = ip;
        this.deviceId = deviceId;
        this.collectionNpub = npub;
        this.context = context;
    }

    public void setOnFileLongClickListener(OnFileLongClickListener listener) {
        this.longClickListener = listener;
    }

    public void setOnFileDownloadListener(OnFileDownloadListener listener) {
        this.downloadListener = listener;
    }

    public void setOnFolderDownloadListener(OnFolderDownloadListener listener) {
        this.folderDownloadListener = listener;
    }

    public void updateDownloadProgress(String fileId, int percentComplete) {
        downloadProgress.put(fileId, percentComplete);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_collection_file, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CollectionFile file = files.get(position);
        holder.bind(file, listener, longClickListener, downloadListener, folderDownloadListener, collectionStoragePath, isRemoteMode, remoteIp, deviceId, collectionNpub, context);
    }

    @Override
    public int getItemCount() {
        return files.size();
    }

    public void updateFiles(List<CollectionFile> newFiles) {
        this.files = new ArrayList<>(newFiles);
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView fileIcon;
        private final TextView fileName;
        private final TextView fileDetails;
        private final TextView fileDescription;
        private final TextView downloadStatus;
        private final View downloadContainer;
        private final ImageView downloadIcon;
        private final TextView downloadProgress;
        private final ImageView downloadFolderButton;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            fileIcon = itemView.findViewById(R.id.file_icon);
            fileName = itemView.findViewById(R.id.file_name);
            fileDetails = itemView.findViewById(R.id.file_details);
            fileDescription = itemView.findViewById(R.id.file_description);
            downloadStatus = itemView.findViewById(R.id.download_status);
            downloadContainer = itemView.findViewById(R.id.download_container);
            downloadIcon = itemView.findViewById(R.id.download_icon);
            downloadProgress = itemView.findViewById(R.id.download_progress);
            downloadFolderButton = itemView.findViewById(R.id.download_folder_button);
        }

        public void bind(CollectionFile file, OnFileClickListener listener, OnFileLongClickListener longClickListener,
                         OnFileDownloadListener downloadListener, OnFolderDownloadListener folderDownloadListener, String storagePath, boolean isRemote, String remoteIp, String deviceId, String collectionNpub, android.content.Context context) {
            android.util.Log.d("FileAdapter", "═══════════════════════════════════════");
            android.util.Log.d("FileAdapter", "bind() called for: " + file.getName());
            android.util.Log.d("FileAdapter", "  filePath=" + file.getPath());
            android.util.Log.d("FileAdapter", "  isRemote=" + isRemote);
            android.util.Log.d("FileAdapter", "  storagePath=" + storagePath);
            android.util.Log.d("FileAdapter", "  remoteIp=" + remoteIp);
            android.util.Log.d("FileAdapter", "  deviceId=" + deviceId);
            android.util.Log.d("FileAdapter", "  collectionNpub=" + collectionNpub);
            fileName.setText(file.getName());

            // Set icon based on type
            if (file.isDirectory()) {
                fileIcon.setImageResource(R.drawable.ic_folder);
                fileIcon.setImageTintList(android.content.res.ColorStateList.valueOf(0xFFFFFFFF)); // White tint for folder icon
                fileIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                fileDetails.setText("Folder");
            } else {
                // Check if it's an image file and load thumbnail
                boolean isImage = isImageFile(file.getName());
                android.util.Log.d("FileAdapter", "  isImage=" + isImage);
                if (isImage) {
                    // Only load remote thumbnails over WiFi or Relay, not over BLE GATT (too slow)
                    if (isRemote && remoteIp != null && collectionNpub != null && context != null) {
                        // Load thumbnail from remote device (via WiFi or relay only)
                        android.util.Log.d("FileAdapter", "  → Loading REMOTE thumbnail (WiFi/Relay)");
                        loadRemoteThumbnail(file.getPath(), remoteIp, deviceId, collectionNpub, context);
                    } else if (!isRemote && storagePath != null) {
                        // Load local thumbnail
                        android.util.Log.d("FileAdapter", "  → Loading LOCAL thumbnail");
                        android.util.Log.d("FileAdapter", "     storagePath=" + storagePath);
                        android.util.Log.d("FileAdapter", "     filePath=" + file.getPath());
                        loadThumbnail(file.getPath(), storagePath);
                    } else {
                        android.util.Log.w("FileAdapter", "  ✗ No storage path available - cannot load thumbnail");
                        android.util.Log.w("FileAdapter", "     isRemote=" + isRemote);
                        android.util.Log.w("FileAdapter", "     storagePath=" + storagePath);
                        fileIcon.setImageResource(R.drawable.ic_file);
                        fileIcon.setImageTintList(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
                        fileIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    }
                } else {
                    android.util.Log.d("FileAdapter", "  → File is not an image, using generic icon");
                    fileIcon.setImageResource(R.drawable.ic_file);
                    fileIcon.setImageTintList(android.content.res.ColorStateList.valueOf(0xFFFFFFFF)); // White tint for file icon
                    fileIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                }

                // Build details string
                StringBuilder details = new StringBuilder();
                String size = file.getFormattedSize();
                if (!size.isEmpty()) {
                    details.append(size);
                }
                if (file.getViews() > 0) {
                    if (details.length() > 0) {
                        details.append(" • ");
                    }
                    details.append(file.getViews()).append(" views");
                }
                fileDetails.setText(details.toString());
            }

            // Show description if available
            if (file.getDescription() != null && !file.getDescription().isEmpty()) {
                fileDescription.setVisibility(View.VISIBLE);
                fileDescription.setText(file.getDescription());
            } else {
                fileDescription.setVisibility(View.GONE);
            }

            // Validate SHA1 for local files (not remote, not directories)
            if (!isRemote && !file.isDirectory() && context != null && storagePath != null) {
                String validationError = validateFileSha1(context, storagePath, file);
                if (validationError != null) {
                    // File is corrupted - show warning
                    fileDescription.setVisibility(View.VISIBLE);
                    fileDescription.setText("⚠️ " + validationError);
                    fileDescription.setTextColor(0xFFF44336); // Red color

                    // Add click listener to offer deletion
                    fileDescription.setOnClickListener(v -> {
                        showCorruptedFileDialog(context, storagePath, file);
                    });
                } else {
                    // Reset text color if validation passed
                    fileDescription.setTextColor(0xFFCCCCCC); // Original gray color
                    fileDescription.setOnClickListener(null);
                }
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    // In remote mode for files, trigger download instead of opening
                    if (isRemote && !file.isDirectory() && downloadListener != null) {
                        // Check if using BLE GATT and file is >10KB
                        if (remoteIp == null && file.getSize() > 10240) {
                            // BLE GATT mode with file >10KB - show warning
                            showBleDownloadWarning(context, file, () -> downloadListener.onFileDownload(file));
                        } else {
                            // WiFi or small file - proceed directly
                            downloadListener.onFileDownload(file);
                        }
                    } else {
                        // Local mode or directory - use normal click behavior
                        listener.onFileClick(file);
                    }
                }
            });

            itemView.setOnLongClickListener(v -> {
                if (longClickListener != null) {
                    longClickListener.onFileLongClick(file);
                    return true;
                }
                return false;
            });

            // Show download button only in remote mode for files (not folders)
            if (isRemote && !file.isDirectory()) {
                downloadContainer.setVisibility(View.VISIBLE);

                // Check if file exists locally
                boolean fileExistsLocally = checkFileExistsLocally(context, collectionNpub, file.getPath());

                // Check if download is in progress and show percentage
                String fileId = collectionNpub + "/" + file.getPath();
                offgrid.geogram.util.DownloadProgress.DownloadStatus dlStatus =
                    offgrid.geogram.util.DownloadProgress.getInstance().getDownloadStatus(fileId);

                if (fileExistsLocally) {
                    // File already downloaded - show open icon
                    downloadIcon.setVisibility(View.VISIBLE);
                    downloadIcon.setImageResource(R.drawable.ic_check);
                    downloadProgress.setVisibility(View.GONE);

                    // Show "available locally" status
                    downloadStatus.setText("available locally");
                    downloadStatus.setTextColor(0xFF4CAF50); // Green
                    downloadStatus.setVisibility(View.VISIBLE);

                    // Click opens the file instead of downloading
                    downloadIcon.setOnClickListener(v -> {
                        if (listener != null) {
                            listener.onFileClick(file);
                        }
                    });
                } else if (dlStatus != null && !dlStatus.completed && !dlStatus.failed) {
                    // Download in progress - show percentage, hide icon
                    downloadIcon.setVisibility(View.GONE);
                    downloadProgress.setVisibility(View.VISIBLE);
                    downloadProgress.setText(dlStatus.percentComplete + "%");

                    // Show download status text
                    showDownloadStatusText(dlStatus);

                    // No click action during download
                    downloadIcon.setOnClickListener(null);
                } else {
                    // No active download - show download icon
                    downloadIcon.setVisibility(View.VISIBLE);
                    downloadIcon.setImageResource(R.drawable.ic_download);
                    downloadProgress.setVisibility(View.GONE);
                    downloadStatus.setVisibility(View.GONE);

                    // Set click listener for download icon (no BLE warning if file exists)
                    downloadIcon.setOnClickListener(v -> {
                        if (downloadListener != null) {
                            // Check if using BLE GATT and file is >10KB
                            if (remoteIp == null && file.getSize() > 10240) {
                                // BLE GATT mode with file >10KB - show warning
                                showBleDownloadWarning(context, file, () -> downloadListener.onFileDownload(file));
                            } else {
                                // WiFi or small file - proceed directly
                                downloadListener.onFileDownload(file);
                            }
                        }
                    });
                }
            } else {
                downloadContainer.setVisibility(View.GONE);
                downloadStatus.setVisibility(View.GONE);
            }

            // Show folder download button for folders in remote mode
            if (isRemote && file.isDirectory() && folderDownloadListener != null) {
                downloadFolderButton.setVisibility(View.VISIBLE);
                downloadFolderButton.setOnClickListener(v -> {
                    if (folderDownloadListener != null) {
                        folderDownloadListener.onFolderDownload(file);
                    }
                });
            } else {
                downloadFolderButton.setVisibility(View.GONE);
            }
        }

        /**
         * Check if file exists locally in the collection folder
         */
        private boolean checkFileExistsLocally(android.content.Context context, String collectionNpub, String filePath) {
            try {
                if (collectionNpub == null || filePath == null || context == null) {
                    return false;
                }

                File collectionsDir = new File(context.getFilesDir(), "collections");
                File collectionFolder = new File(collectionsDir, collectionNpub);
                File targetFile = new File(collectionFolder, filePath);

                boolean exists = targetFile.exists() && targetFile.isFile();
                if (exists) {
                    android.util.Log.d("FileAdapter", "File exists locally: " + filePath);
                }
                return exists;
            } catch (Exception e) {
                android.util.Log.e("FileAdapter", "Error checking if file exists locally: " + e.getMessage());
                return false;
            }
        }

        /**
         * Display download status text with appropriate color
         */
        private void showDownloadStatusText(offgrid.geogram.util.DownloadProgress.DownloadStatus dlStatus) {
            long now = System.currentTimeMillis();
            long timeSinceUpdate = now - dlStatus.lastUpdateTime;
            boolean isStalled = timeSinceUpdate > 30000 && dlStatus.bytesPerSecond == 0;

            String statusText;
            int statusColor;

            if (dlStatus.paused) {
                statusText = dlStatus.percentComplete + "% • paused";
                statusColor = 0xFFFF9800; // Orange
            } else if (isStalled) {
                statusText = dlStatus.percentComplete + "% • stalled";
                statusColor = 0xFFF44336; // Red
            } else if (dlStatus.percentComplete == 0) {
                statusText = "queued";
                statusColor = 0xFF9E9E9E; // Gray
            } else {
                statusText = dlStatus.percentComplete + "% • downloading";
                statusColor = 0xFF4CAF50; // Green
            }

            downloadStatus.setText(statusText);
            downloadStatus.setTextColor(statusColor);
            downloadStatus.setVisibility(View.VISIBLE);
        }

        private void showBleDownloadWarning(android.content.Context context, CollectionFile file, Runnable onConfirm) {
            // Calculate estimated time based on measured throughput: ~75 bytes/second over BLE GATT
            long fileSize = file.getSize();
            long estimatedSeconds = fileSize / 75;
            String timeEstimate;

            if (estimatedSeconds < 60) {
                timeEstimate = estimatedSeconds + " seconds";
            } else if (estimatedSeconds < 3600) {
                long minutes = estimatedSeconds / 60;
                timeEstimate = minutes + " minute" + (minutes > 1 ? "s" : "");
            } else {
                long hours = estimatedSeconds / 3600;
                long minutes = (estimatedSeconds % 3600) / 60;
                timeEstimate = hours + " hour" + (hours > 1 ? "s" : "") +
                             (minutes > 0 ? " " + minutes + " minute" + (minutes > 1 ? "s" : "") : "");
            }

            String message = "This file is " + file.getFormattedSize() + " and will take approximately " +
                           timeEstimate + " to download over Bluetooth.\n\n" +
                           "For faster downloads, connect both devices to the same WiFi network or enable Internet Relay.";

            android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(context)
                .setTitle("Slow Bluetooth Download")
                .setMessage(message)
                .setPositiveButton("Download Anyway", (d, which) -> {
                    if (onConfirm != null) {
                        onConfirm.run();
                    }
                })
                .setNegativeButton("Cancel", null)
                .create();
            dialog.show();

            // Force button text to white
            if (dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE) != null) {
                dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setTextColor(0xFFFFFFFF);
            }
            if (dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE) != null) {
                dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).setTextColor(0xFFFFFFFF);
            }
        }

        private boolean isImageFile(String fileName) {
            String lowerName = fileName.toLowerCase();
            return lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") ||
                   lowerName.endsWith(".png") || lowerName.endsWith(".gif") ||
                   lowerName.endsWith(".webp") || lowerName.endsWith(".bmp");
        }

        private void loadThumbnail(String filePath, String storagePath) {
            android.util.Log.d("FileAdapter", "loadThumbnail() START");
            android.util.Log.d("FileAdapter", "  storagePath=" + storagePath);
            android.util.Log.d("FileAdapter", "  filePath=" + filePath);

            File imageFile = new File(storagePath, filePath);
            String fullPath = imageFile.getAbsolutePath();
            android.util.Log.d("FileAdapter", "  fullPath=" + fullPath);
            android.util.Log.d("FileAdapter", "  file.exists()=" + imageFile.exists());

            if (!imageFile.exists()) {
                android.util.Log.w("FileAdapter", "  ✗ Image file does not exist!");
                fileIcon.setImageResource(R.drawable.ic_file);
                fileIcon.setImageTintList(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
                fileIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                return;
            }

            android.util.Log.d("FileAdapter", "  → File exists, decoding...");
            try {
                // Load image with downsampling to save memory
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(imageFile.getAbsolutePath(), options);

                // Calculate inSampleSize to create thumbnail
                int targetSize = 200; // Target thumbnail size in pixels
                options.inSampleSize = calculateInSampleSize(options, targetSize, targetSize);
                options.inJustDecodeBounds = false;

                Bitmap thumbnail = BitmapFactory.decodeFile(imageFile.getAbsolutePath(), options);
                android.util.Log.d("FileAdapter", "  thumbnail=" + (thumbnail != null ? (thumbnail.getWidth() + "x" + thumbnail.getHeight()) : "null"));

                if (thumbnail != null) {
                    android.util.Log.d("FileAdapter", "  ✓ Thumbnail decoded successfully, displaying...");
                    fileIcon.setImageTintList(null); // Remove tint for actual images
                    fileIcon.setImageBitmap(thumbnail);
                    fileIcon.setScaleType(ImageView.ScaleType.CENTER_CROP);
                } else {
                    android.util.Log.w("FileAdapter", "  ✗ Thumbnail decode returned null");
                    fileIcon.setImageResource(R.drawable.ic_file);
                    fileIcon.setImageTintList(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
                    fileIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                }
            } catch (Exception e) {
                android.util.Log.e("FileAdapter", "  ✗ Exception loading thumbnail: " + e.getMessage(), e);
                fileIcon.setImageResource(R.drawable.ic_file);
                fileIcon.setImageTintList(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
                fileIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            }
            android.util.Log.d("FileAdapter", "loadThumbnail() END");
        }

        private void loadRemoteThumbnail(String filePath, String remoteIp, String deviceId, String collectionNpub, android.content.Context context) {
            // Set placeholder while loading
            fileIcon.setImageResource(R.drawable.ic_file);
            fileIcon.setImageTintList(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
            fileIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);

            // Load thumbnail in background thread
            new Thread(() -> {
                try {
                    String path = "/api/collections/" + collectionNpub + "/thumbnail/" + filePath;
                    android.util.Log.d("FileAdapter", "Loading remote thumbnail: " + path + " (deviceId=" + deviceId + ", remoteIp=" + remoteIp + ")");

                    // Use P2PHttpClient to route through WiFi or relay
                    // Increased timeout to 15s for relay routing + image processing
                    offgrid.geogram.p2p.P2PHttpClient httpClient = new offgrid.geogram.p2p.P2PHttpClient(context);
                    offgrid.geogram.p2p.P2PHttpClient.InputStreamResponse streamResponse =
                        httpClient.getInputStream(deviceId, remoteIp, path, 15000);

                    if (streamResponse.isSuccess()) {
                        android.util.Log.d("FileAdapter", "Thumbnail request successful, decoding image...");
                        try {
                            InputStream inputStream = streamResponse.stream;

                            // Decode image directly from stream
                            Bitmap thumbnail = BitmapFactory.decodeStream(inputStream);

                            if (thumbnail != null) {
                                android.util.Log.d("FileAdapter", "Thumbnail decoded successfully: " + thumbnail.getWidth() + "x" + thumbnail.getHeight());
                                // Update UI on main thread
                                fileIcon.post(() -> {
                                    fileIcon.setImageTintList(null); // Remove tint for actual images
                                    fileIcon.setImageBitmap(thumbnail);
                                    fileIcon.setScaleType(ImageView.ScaleType.CENTER_CROP);
                                });
                            } else {
                                android.util.Log.w("FileAdapter", "Failed to decode thumbnail bitmap - stream might be empty or invalid format");
                            }
                        } finally {
                            streamResponse.close();
                        }
                    } else {
                        android.util.Log.e("FileAdapter", "Thumbnail request failed: HTTP " + streamResponse.statusCode +
                            (streamResponse.errorMessage != null ? " - " + streamResponse.errorMessage : ""));
                    }
                } catch (Exception e) {
                    android.util.Log.e("FileAdapter", "Error loading remote thumbnail: " + e.getMessage(), e);
                    // Keep default file icon on error
                }
            }).start();
        }

        private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
            final int height = options.outHeight;
            final int width = options.outWidth;
            int inSampleSize = 1;

            if (height > reqHeight || width > reqWidth) {
                final int halfHeight = height / 2;
                final int halfWidth = width / 2;

                while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                    inSampleSize *= 2;
                }
            }

            return inSampleSize;
        }

        /**
         * Calculate SHA1 hash of a file
         * @param file The file to hash
         * @return SHA1 hash as lowercase hex string, or null if error
         */
        private String calculateFileSha1(File file) {
            if (file == null || !file.exists() || !file.isFile()) {
                return null;
            }

            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-1");
                FileInputStream fis = new FileInputStream(file);
                byte[] buffer = new byte[8192];
                int read;

                while ((read = fis.read(buffer)) > 0) {
                    digest.update(buffer, 0, read);
                }
                fis.close();

                byte[] hash = digest.digest();
                StringBuilder hexString = new StringBuilder();
                for (byte b : hash) {
                    String hex = Integer.toHexString(0xff & b);
                    if (hex.length() == 1) {
                        hexString.append('0');
                    }
                    hexString.append(hex);
                }

                return hexString.toString().toLowerCase();
            } catch (Exception e) {
                android.util.Log.e("FileAdapter", "Error calculating SHA1: " + e.getMessage(), e);
                return null;
            }
        }

        /**
         * Validate file SHA1 against expected hash
         * @return null if valid or no SHA1 to check, error message if corrupted
         */
        private String validateFileSha1(android.content.Context context, String storagePath, CollectionFile file) {
            // Only validate if file has expected SHA1 and we're in local mode
            if (file.getSha1() == null || file.getSha1().isEmpty() || storagePath == null) {
                return null;
            }

            try {
                File targetFile = new File(storagePath, file.getPath());
                if (!targetFile.exists() || !targetFile.isFile()) {
                    return null; // File doesn't exist, no need to validate
                }

                String expectedSha1 = file.getSha1().toLowerCase();
                String actualSha1 = calculateFileSha1(targetFile);

                if (actualSha1 == null) {
                    return "Failed to calculate SHA1";
                }

                if (!expectedSha1.equals(actualSha1)) {
                    android.util.Log.w("FileAdapter", "SHA1 MISMATCH for " + file.getName() +
                        "\n  Expected: " + expectedSha1 +
                        "\n  Actual:   " + actualSha1);
                    return "File corrupted (SHA1 mismatch)";
                }

                android.util.Log.d("FileAdapter", "SHA1 validated OK for " + file.getName());
                return null; // Valid
            } catch (Exception e) {
                android.util.Log.e("FileAdapter", "Error validating SHA1: " + e.getMessage(), e);
                return "SHA1 validation error";
            }
        }

        /**
         * Show dialog offering to delete a corrupted file
         */
        private void showCorruptedFileDialog(android.content.Context context, String storagePath, CollectionFile file) {
            String message = "The file '" + file.getName() + "' is corrupted and does not match the expected SHA1 hash.\n\n" +
                           "Would you like to delete it? You can re-download it later to get a clean copy.";

            android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(context)
                .setTitle("Corrupted File Detected")
                .setMessage(message)
                .setPositiveButton("Delete", (d, which) -> {
                    deleteCorruptedFile(context, storagePath, file);
                })
                .setNegativeButton("Keep", null)
                .create();
            dialog.show();

            // Force button text to white
            if (dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE) != null) {
                dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setTextColor(0xFFF44336); // Red for delete
            }
            if (dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE) != null) {
                dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).setTextColor(0xFFFFFFFF); // White for keep
            }
        }

        /**
         * Delete a corrupted file from disk
         */
        private void deleteCorruptedFile(android.content.Context context, String storagePath, CollectionFile file) {
            try {
                File targetFile = new File(storagePath, file.getPath());
                if (targetFile.exists() && targetFile.isFile()) {
                    if (targetFile.delete()) {
                        android.util.Log.i("FileAdapter", "Deleted corrupted file: " + targetFile.getAbsolutePath());
                        android.widget.Toast.makeText(context, "Corrupted file deleted. Pull to refresh to update the list.", android.widget.Toast.LENGTH_LONG).show();

                        // Hide the corruption warning immediately
                        fileDescription.setVisibility(View.GONE);
                    } else {
                        android.util.Log.e("FileAdapter", "Failed to delete corrupted file: " + targetFile.getAbsolutePath());
                        android.widget.Toast.makeText(context, "Failed to delete file", android.widget.Toast.LENGTH_SHORT).show();
                    }
                } else {
                    android.util.Log.w("FileAdapter", "Corrupted file not found or not a file: " + targetFile.getAbsolutePath());
                    android.widget.Toast.makeText(context, "File not found", android.widget.Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                android.util.Log.e("FileAdapter", "Error deleting corrupted file: " + e.getMessage(), e);
                android.widget.Toast.makeText(context, "Error deleting file: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
            }
        }
    }
}

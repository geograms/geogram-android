package offgrid.geogram.fragments;

import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import offgrid.geogram.R;
import offgrid.geogram.devices.Device;
import offgrid.geogram.devices.DeviceManager;
import offgrid.geogram.util.DownloadProgress;

public class DownloadQueueFragment extends Fragment {

    private RecyclerView recyclerView;
    private DownloadAdapter adapter;
    private TextView emptyMessage;
    private TextView summaryText;
    private View summaryContainer;
    private ImageView btnPauseAll;
    private Handler updateHandler;
    private Runnable updateRunnable;
    private boolean allPaused = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_download_queue, container, false);

        recyclerView = view.findViewById(R.id.downloads_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        emptyMessage = view.findViewById(R.id.empty_message);
        summaryText = view.findViewById(R.id.summary_text);
        summaryContainer = view.findViewById(R.id.summary_container);

        ImageView btnClearCompleted = view.findViewById(R.id.btn_clear_completed);
        btnClearCompleted.setOnClickListener(v -> clearCompletedDownloads());

        btnPauseAll = view.findViewById(R.id.btn_pause_all);
        btnPauseAll.setOnClickListener(v -> togglePauseAll());

        adapter = new DownloadAdapter();
        recyclerView.setAdapter(adapter);

        // Set up periodic updates
        updateHandler = new Handler();
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                updateDownloadList();
                updateHandler.postDelayed(this, 1000); // Update every second
            }
        };

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateDownloadList();
        updateHandler.postDelayed(updateRunnable, 1000);
    }

    @Override
    public void onPause() {
        super.onPause();
        updateHandler.removeCallbacks(updateRunnable);
    }

    private void updateDownloadList() {
        Map<String, DownloadProgress.DownloadStatus> downloads =
            DownloadProgress.getInstance().getAllDownloads();

        List<DownloadProgress.DownloadStatus> downloadList = new ArrayList<>(downloads.values());

        if (downloadList.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            emptyMessage.setVisibility(View.VISIBLE);
            summaryContainer.setVisibility(View.GONE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyMessage.setVisibility(View.GONE);
            adapter.updateDownloads(downloadList);

            // Calculate and show summary
            updateSummary(downloadList);

            // Check for stalled downloads
            checkStalledDownloads(downloadList);
        }
    }

    private void updateSummary(List<DownloadProgress.DownloadStatus> downloadList) {
        int activeCount = 0;
        int pausedCount = 0;
        long totalRemaining = 0;
        long totalTimeRemaining = 0;
        int downloadsWithSpeed = 0;

        for (DownloadProgress.DownloadStatus download : downloadList) {
            if (!download.completed && !download.failed) {
                if (download.paused) {
                    pausedCount++;
                } else {
                    activeCount++;
                }
                totalRemaining += (download.totalBytes - download.downloadedBytes);

                long timeRemaining = download.getEstimatedTimeRemainingMs();
                if (timeRemaining > 0) {
                    totalTimeRemaining += timeRemaining;
                    downloadsWithSpeed++;
                }
            }
        }

        if (activeCount > 0 || pausedCount > 0) {
            StringBuilder summary = new StringBuilder();
            summary.append(activeCount).append(" active");
            if (pausedCount > 0) {
                summary.append(", ").append(pausedCount).append(" paused");
            }
            summary.append(" • ").append(formatBytes(totalRemaining)).append(" remaining");

            if (downloadsWithSpeed > 0) {
                summary.append(" • ~").append(formatTimeRemaining(totalTimeRemaining / downloadsWithSpeed));
            }

            summaryText.setText(summary.toString());
            summaryContainer.setVisibility(View.VISIBLE);
        } else {
            summaryContainer.setVisibility(View.GONE);
        }
    }

    private void checkStalledDownloads(List<DownloadProgress.DownloadStatus> downloadList) {
        long now = System.currentTimeMillis();
        for (DownloadProgress.DownloadStatus download : downloadList) {
            if (!download.completed && !download.failed && !download.paused) {
                // Check if download has been stalled for more than 60 seconds
                long timeSinceUpdate = now - download.lastUpdateTime;
                if (timeSinceUpdate > 60000 && download.bytesPerSecond == 0) {
                    // Mark as stalled and retry
                    offgrid.geogram.core.Log.w("DownloadQueue",
                        "Download stalled: " + download.fileName + " (no progress for 60s) - auto-retrying");

                    // Trigger a retry by pausing and immediately resuming
                    download.pause();
                    download.resume();

                    // Show notification to user
                    if (getContext() != null) {
                        android.widget.Toast.makeText(getContext(),
                            "Retrying stalled download: " + download.fileName,
                            android.widget.Toast.LENGTH_SHORT).show();
                    }

                    // Reset last update time to prevent immediate re-trigger
                    download.lastUpdateTime = now;
                }
            }
        }
    }

    private void openDownloadedFile(DownloadProgress.DownloadStatus download) {
        try {
            // Extract collection ID and file path from fileId
            if (!download.fileId.contains("/")) {
                android.widget.Toast.makeText(getContext(), "Invalid file ID", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }

            String collectionId = download.fileId.substring(0, download.fileId.indexOf("/"));
            String filePath = download.fileId.substring(download.fileId.indexOf("/") + 1);

            File collectionsDir = new File(requireContext().getFilesDir(), "collections");
            File targetFile = new File(new File(collectionsDir, collectionId), filePath);

            if (!targetFile.exists()) {
                android.widget.Toast.makeText(getContext(), "File not found", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }

            // Open file with appropriate app
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
            android.net.Uri fileUri = androidx.core.content.FileProvider.getUriForFile(
                requireContext(),
                requireContext().getPackageName() + ".provider",
                targetFile
            );

            String mimeType = getMimeType(targetFile.getName());
            intent.setDataAndType(fileUri, mimeType);
            intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(intent);
        } catch (Exception e) {
            offgrid.geogram.core.Log.e("DownloadQueue", "Error opening file: " + e.getMessage());
            android.widget.Toast.makeText(getContext(), "Error opening file: " + e.getMessage(),
                android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void openCollectionFolder(DownloadProgress.DownloadStatus download) {
        try {
            // Extract collection ID from fileId
            if (!download.fileId.contains("/")) {
                android.widget.Toast.makeText(getContext(), "Invalid file ID", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }

            String collectionId = download.fileId.substring(0, download.fileId.indexOf("/"));

            // Open CollectionBrowserFragment for this collection
            if (getActivity() instanceof offgrid.geogram.MainActivity) {
                // Load collection and navigate to it
                File collectionsDir = new File(requireContext().getFilesDir(), "collections");
                File collectionFolder = new File(collectionsDir, collectionId);

                if (collectionFolder.exists()) {
                    offgrid.geogram.models.Collection collection = new offgrid.geogram.models.Collection(
                        collectionId,
                        "Downloaded Collection",
                        ""
                    );
                    collection.setStoragePath(collectionFolder.getAbsolutePath());

                    offgrid.geogram.fragments.CollectionBrowserFragment fragment =
                        offgrid.geogram.fragments.CollectionBrowserFragment.newInstance(collection);

                    getParentFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, fragment)
                        .addToBackStack(null)
                        .commit();
                } else {
                    android.widget.Toast.makeText(getContext(), "Collection folder not found", android.widget.Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            offgrid.geogram.core.Log.e("DownloadQueue", "Error opening folder: " + e.getMessage());
            android.widget.Toast.makeText(getContext(), "Error opening folder: " + e.getMessage(),
                android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private String getMimeType(String fileName) {
        String extension = "";
        int i = fileName.lastIndexOf('.');
        if (i > 0) {
            extension = fileName.substring(i + 1).toLowerCase();
        }

        switch (extension) {
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "gif":
                return "image/gif";
            case "mp4":
                return "video/mp4";
            case "mp3":
                return "audio/mpeg";
            case "pdf":
                return "application/pdf";
            case "txt":
                return "text/plain";
            default:
                return "*/*";
        }
    }

    private void clearCompletedDownloads() {
        DownloadProgress.getInstance().clearCompleted();
        updateDownloadList();
    }

    private void togglePauseAll() {
        if (allPaused) {
            DownloadProgress.getInstance().resumeAll();
            btnPauseAll.setImageResource(R.drawable.ic_pause);
            allPaused = false;
        } else {
            DownloadProgress.getInstance().pauseAll();
            btnPauseAll.setImageResource(R.drawable.ic_play);
            allPaused = true;
        }
        updateDownloadList();
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private String formatTimeRemaining(long milliseconds) {
        long seconds = milliseconds / 1000;

        if (seconds < 60) {
            return seconds + " sec";
        } else if (seconds < 3600) {
            long minutes = seconds / 60;
            return minutes + " min";
        } else if (seconds < 86400) {
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            if (minutes > 0) {
                return hours + " hr " + minutes + " min";
            }
            return hours + " hr";
        } else {
            long days = seconds / 86400;
            long hours = (seconds % 86400) / 3600;
            if (hours > 0) {
                return days + " days " + hours + " hr";
            }
            return days + " days";
        }
    }

    /**
     * Get a display-friendly name for a device.
     * Priority: alias (nickname) > callsign > truncated NPUB
     * Checks both currently spotted devices and database
     */
    private String getDeviceDisplayName(String deviceId) {
        if (deviceId == null || deviceId.isEmpty()) {
            return "Unknown";
        }

        // First, search for device in DeviceManager (currently spotted devices)
        for (Device device : DeviceManager.getInstance().getDevicesSpotted()) {
            if (device.ID.equals(deviceId)) {
                // Try nickname first
                String nickname = device.getProfileNickname();
                if (nickname != null && !nickname.isEmpty()) {
                    return nickname;
                }
                // Try callsign
                if (device.callsign != null && !device.callsign.isEmpty()) {
                    return device.callsign;
                }
                break;
            }
        }

        // If not found in spotted devices, check database
        // The deviceId might be a callsign or npub, try both
        try {
            // Try as callsign first
            offgrid.geogram.database.DatabaseDevices.DeviceRow deviceRow =
                offgrid.geogram.database.DatabaseDevices.get().getDevice(deviceId);

            if (deviceRow != null) {
                // Priority: alias > callsign > npub
                if (deviceRow.alias != null && !deviceRow.alias.trim().isEmpty()) {
                    return deviceRow.alias;
                }
                if (deviceRow.callsign != null && !deviceRow.callsign.isEmpty()) {
                    return deviceRow.callsign;
                }
            }

            // If deviceId looks like an npub, search all devices for matching npub
            if (deviceId.startsWith("npub")) {
                java.util.List<offgrid.geogram.database.DatabaseDevices.DeviceRow> allDevices =
                    offgrid.geogram.database.DatabaseDevices.get().getAllDevices();
                for (offgrid.geogram.database.DatabaseDevices.DeviceRow row : allDevices) {
                    if (deviceId.equals(row.npub)) {
                        // Priority: alias > callsign
                        if (row.alias != null && !row.alias.trim().isEmpty()) {
                            return row.alias;
                        }
                        if (row.callsign != null && !row.callsign.isEmpty()) {
                            return row.callsign;
                        }
                        break;
                    }
                }
            }
        } catch (Exception e) {
            offgrid.geogram.core.Log.w("DownloadQueue", "Error looking up device in database: " + e.getMessage());
        }

        // Fallback: show truncated NPUB
        if (deviceId.length() > 16) {
            return deviceId.substring(0, 8) + "..." + deviceId.substring(deviceId.length() - 8);
        }
        return deviceId;
    }

    // Adapter for download items
    private class DownloadAdapter extends RecyclerView.Adapter<DownloadAdapter.DownloadViewHolder> {

        private List<DownloadProgress.DownloadStatus> downloads = new ArrayList<>();

        public void updateDownloads(List<DownloadProgress.DownloadStatus> newDownloads) {
            this.downloads = new ArrayList<>(newDownloads);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public DownloadViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_download_queue, parent, false);
            return new DownloadViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull DownloadViewHolder holder, int position) {
            DownloadProgress.DownloadStatus download = downloads.get(position);
            holder.bind(download);
        }

        @Override
        public int getItemCount() {
            return downloads.size();
        }

        class DownloadViewHolder extends RecyclerView.ViewHolder {
            private final ImageView statusIcon;
            private final TextView filename;
            private final TextView source;
            private final ProgressBar progressBar;
            private final TextView progressText;
            private final TextView statusText;
            private final ImageView cancelButton;
            private final ImageView deleteButton;
            private final View actionButtonsContainer;
            private final ImageView btnPlay;
            private final ImageView btnFolder;
            private final ImageView btnClean;

            public DownloadViewHolder(@NonNull View itemView) {
                super(itemView);
                statusIcon = itemView.findViewById(R.id.download_status_icon);
                filename = itemView.findViewById(R.id.download_filename);
                source = itemView.findViewById(R.id.download_source);
                progressBar = itemView.findViewById(R.id.download_progress_bar);
                progressText = itemView.findViewById(R.id.download_progress_text);
                statusText = itemView.findViewById(R.id.download_status_text);
                cancelButton = itemView.findViewById(R.id.download_cancel_button);
                deleteButton = itemView.findViewById(R.id.download_delete_button);
                actionButtonsContainer = itemView.findViewById(R.id.download_action_buttons);
                btnPlay = itemView.findViewById(R.id.btn_play);
                btnFolder = itemView.findViewById(R.id.btn_folder);
                btnClean = itemView.findViewById(R.id.btn_clean);
            }

            public void bind(DownloadProgress.DownloadStatus download) {
                // Extract device ID from fileId (format: "deviceId/path")
                String deviceId = "Unknown";
                String filePath = download.fileName;
                if (download.fileId.contains("/")) {
                    int firstSlash = download.fileId.indexOf("/");
                    deviceId = download.fileId.substring(0, firstSlash);
                    filePath = download.fileId.substring(firstSlash + 1);
                }

                // Get just the filename from the path
                String displayFilename = filePath;
                if (filePath.contains("/")) {
                    displayFilename = filePath.substring(filePath.lastIndexOf("/") + 1);
                }

                filename.setText(displayFilename);

                // Get display name from device (callsign or nickname)
                String displaySource = DownloadQueueFragment.this.getDeviceDisplayName(deviceId);
                source.setText("From: " + displaySource);

                // Update progress
                progressBar.setProgress(download.percentComplete);
                progressText.setText(download.percentComplete + "%");

                // Update status
                if (download.completed) {
                    statusIcon.setImageResource(R.drawable.ic_check);
                    statusText.setText("Completed - " + formatBytes(download.totalBytes));
                    cancelButton.setVisibility(View.GONE);
                    deleteButton.setVisibility(View.GONE);

                    // Show action buttons for completed downloads
                    actionButtonsContainer.setVisibility(View.VISIBLE);

                    // Play button - open the file
                    btnPlay.setOnClickListener(v -> openDownloadedFile(download));

                    // Folder button - open collection folder
                    btnFolder.setOnClickListener(v -> openCollectionFolder(download));

                    // Clean button - remove from list only (don't delete file)
                    btnClean.setOnClickListener(v -> {
                        DownloadProgress.getInstance().removeDownload(download.fileId);
                        updateDownloadList();
                    });
                } else {
                    actionButtonsContainer.setVisibility(View.GONE);
                }

                if (download.failed) {
                    statusIcon.setImageResource(R.drawable.ic_close);
                    statusText.setText("Failed: " + download.errorMessage);
                    cancelButton.setVisibility(View.GONE);

                    // Show delete button for failed downloads
                    deleteButton.setVisibility(View.VISIBLE);
                    deleteButton.setOnClickListener(v -> {
                        DownloadProgress.getInstance().deleteDownloadAndFiles(download.fileId);
                        updateDownloadList();
                        android.widget.Toast.makeText(getContext(),
                            "Download cancelled and files deleted",
                            android.widget.Toast.LENGTH_SHORT).show();
                    });
                } else if (download.paused) {
                    // Paused state
                    statusIcon.setImageResource(R.drawable.ic_pause);
                    statusText.setText("Paused - " +
                        formatBytes(download.downloadedBytes) + " / " +
                        formatBytes(download.totalBytes));

                    // Show play button to resume
                    cancelButton.setVisibility(View.VISIBLE);
                    cancelButton.setImageResource(R.drawable.ic_play);
                    cancelButton.setOnClickListener(v -> {
                        download.resume();
                        adapter.notifyDataSetChanged();
                    });

                    // Show delete button to cancel and delete files
                    deleteButton.setVisibility(View.VISIBLE);
                    deleteButton.setOnClickListener(v -> {
                        DownloadProgress.getInstance().deleteDownloadAndFiles(download.fileId);
                        updateDownloadList();
                        android.widget.Toast.makeText(getContext(),
                            "Download cancelled and partial files deleted",
                            android.widget.Toast.LENGTH_SHORT).show();
                    });
                } else if (!download.completed) {
                    // Active download
                    statusIcon.setImageResource(R.drawable.ic_download);

                    // Build status text with size and time estimation
                    String statusMessage = "Downloading... " +
                        formatBytes(download.downloadedBytes) + " / " +
                        formatBytes(download.totalBytes);

                    // Add time estimation if available
                    long timeRemainingMs = download.getEstimatedTimeRemainingMs();
                    if (timeRemainingMs > 0) {
                        statusMessage += " - " + formatTimeRemaining(timeRemainingMs);
                    }

                    statusText.setText(statusMessage);

                    // Show pause button
                    cancelButton.setVisibility(View.VISIBLE);
                    cancelButton.setImageResource(R.drawable.ic_pause);
                    cancelButton.setOnClickListener(v -> {
                        download.pause();
                        adapter.notifyDataSetChanged();
                    });

                    // Show delete button to cancel and delete files
                    deleteButton.setVisibility(View.VISIBLE);
                    deleteButton.setOnClickListener(v -> {
                        DownloadProgress.getInstance().deleteDownloadAndFiles(download.fileId);
                        updateDownloadList();
                        android.widget.Toast.makeText(getContext(),
                            "Download cancelled and partial files deleted",
                            android.widget.Toast.LENGTH_SHORT).show();
                    });
                }
            }

            private String formatBytes(long bytes) {
                if (bytes < 1024) return bytes + " B";
                if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
                if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
                return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
            }

            private String formatSpeed(long bytesPerSecond) {
                return formatBytes(bytesPerSecond) + "/s";
            }

            private String formatTimeRemaining(long milliseconds) {
                long seconds = milliseconds / 1000;

                if (seconds < 60) {
                    return seconds + " sec";
                } else if (seconds < 3600) {
                    long minutes = seconds / 60;
                    long remainingSeconds = seconds % 60;
                    if (remainingSeconds > 0) {
                        return minutes + " min " + remainingSeconds + " sec";
                    }
                    return minutes + " min";
                } else {
                    long hours = seconds / 3600;
                    long minutes = (seconds % 3600) / 60;
                    if (minutes > 0) {
                        return hours + " hr " + minutes + " min";
                    }
                    return hours + " hr";
                }
            }
        }
    }
}

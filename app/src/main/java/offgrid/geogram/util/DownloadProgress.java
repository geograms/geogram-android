package offgrid.geogram.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

import offgrid.geogram.core.Log;

/**
 * Tracks download progress for remote files with persistence
 */
public class DownloadProgress {
    private static final String TAG = "DownloadProgress";
    private static final String PREFS_NAME = "download_queue";
    private static final String KEY_DOWNLOADS = "downloads";

    private static DownloadProgress instance;
    private final Map<String, DownloadStatus> downloads = new ConcurrentHashMap<>();
    private Context context;
    private final Gson gson = new Gson();

    public static class DownloadStatus {
        public final String fileId;
        public final String fileName;
        public final long totalBytes;
        public long downloadedBytes;
        public int percentComplete;
        public long startTime;
        public long lastUpdateTime;
        public boolean completed;
        public boolean failed;
        public boolean paused;
        public String errorMessage;
        public long bytesPerSecond;

        public DownloadStatus(String fileId, String fileName, long totalBytes) {
            this.fileId = fileId;
            this.fileName = fileName;
            this.totalBytes = totalBytes;
            this.downloadedBytes = 0;
            this.percentComplete = 0;
            this.startTime = System.currentTimeMillis();
            this.lastUpdateTime = this.startTime;
            this.completed = false;
            this.failed = false;
            this.paused = false;
            this.errorMessage = null;
            this.bytesPerSecond = 0;
        }

        public void updateProgress(long newDownloadedBytes) {
            long now = System.currentTimeMillis();
            long timeDelta = now - lastUpdateTime;

            if (timeDelta > 0) {
                long bytesDelta = newDownloadedBytes - downloadedBytes;
                bytesPerSecond = (bytesDelta * 1000) / timeDelta;
            }

            this.downloadedBytes = newDownloadedBytes;
            this.lastUpdateTime = now;

            if (totalBytes > 0) {
                this.percentComplete = (int) ((downloadedBytes * 100) / totalBytes);
            } else {
                this.percentComplete = 0;
            }
        }

        public void markCompleted() {
            this.completed = true;
            this.percentComplete = 100;
        }

        public void markFailed(String error) {
            this.failed = true;
            this.errorMessage = error;
        }

        public void pause() {
            this.paused = true;
        }

        public void resume() {
            this.paused = false;
        }

        public long getElapsedTimeMs() {
            return System.currentTimeMillis() - startTime;
        }

        public String getFormattedSpeed() {
            if (bytesPerSecond < 1024) {
                return bytesPerSecond + " B/s";
            } else if (bytesPerSecond < 1024 * 1024) {
                return String.format("%.1f KB/s", bytesPerSecond / 1024.0);
            } else {
                return String.format("%.2f MB/s", bytesPerSecond / (1024.0 * 1024.0));
            }
        }

        public String getFormattedProgress() {
            return String.format("%d KB / %d KB",
                downloadedBytes / 1024,
                totalBytes / 1024);
        }

        public long getEstimatedTimeRemainingMs() {
            if (bytesPerSecond > 0 && downloadedBytes < totalBytes) {
                long remainingBytes = totalBytes - downloadedBytes;
                return (remainingBytes * 1000) / bytesPerSecond;
            }
            return -1;
        }
    }

    private DownloadProgress() {}

    public static synchronized DownloadProgress getInstance() {
        if (instance == null) {
            instance = new DownloadProgress();
        }
        return instance;
    }

    /**
     * Initialize with context to enable persistence.
     * Call once from Application or MainActivity.
     */
    public synchronized void initialize(Context context) {
        if (this.context == null) {
            this.context = context.getApplicationContext();
            loadFromDisk();
        }
    }

    public DownloadStatus startDownload(String fileId, String fileName, long totalBytes) {
        DownloadStatus status = new DownloadStatus(fileId, fileName, totalBytes);
        downloads.put(fileId, status);
        saveToDisk();
        return status;
    }

    /**
     * Get or create a download status.
     * Used when resuming downloads from persistence.
     */
    public DownloadStatus getOrCreateDownload(String fileId, String fileName, long totalBytes) {
        DownloadStatus status = downloads.get(fileId);
        if (status == null) {
            status = startDownload(fileId, fileName, totalBytes);
        }
        return status;
    }

    public DownloadStatus getDownloadStatus(String fileId) {
        return downloads.get(fileId);
    }

    public Map<String, DownloadStatus> getAllDownloads() {
        return new ConcurrentHashMap<>(downloads);
    }

    public void removeDownload(String fileId) {
        downloads.remove(fileId);
        saveToDisk();
    }

    /**
     * Delete a download and remove all partial files from disk.
     * This is used when user wants to cancel/delete an in-progress download.
     */
    public void deleteDownloadAndFiles(String fileId) {
        if (context == null) {
            Log.w(TAG, "Cannot delete files - context not initialized");
            removeDownload(fileId);
            return;
        }

        try {
            // Extract collection ID and file path from fileId (format: "collectionId/path/to/file")
            if (fileId.contains("/")) {
                String collectionId = fileId.substring(0, fileId.indexOf("/"));
                String filePath = fileId.substring(fileId.indexOf("/") + 1);

                // Get the file location
                java.io.File collectionsDir = new java.io.File(context.getFilesDir(), "collections");
                java.io.File collectionFolder = new java.io.File(collectionsDir, collectionId);
                java.io.File targetFile = new java.io.File(collectionFolder, filePath);

                // Delete the file if it exists (partial or complete)
                if (targetFile.exists()) {
                    if (targetFile.delete()) {
                        Log.i(TAG, "Deleted file: " + targetFile.getAbsolutePath());
                    } else {
                        Log.w(TAG, "Failed to delete file: " + targetFile.getAbsolutePath());
                    }
                }

                // Also check for .part file (some download implementations use this)
                java.io.File partFile = new java.io.File(collectionFolder, filePath + ".part");
                if (partFile.exists()) {
                    if (partFile.delete()) {
                        Log.i(TAG, "Deleted partial file: " + partFile.getAbsolutePath());
                    } else {
                        Log.w(TAG, "Failed to delete partial file: " + partFile.getAbsolutePath());
                    }
                }

                // Delete parent directories if empty
                deleteEmptyParentDirs(targetFile.getParentFile(), collectionFolder);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error deleting download files: " + e.getMessage(), e);
        }

        // Remove from download queue
        removeDownload(fileId);
    }

    /**
     * Delete empty parent directories up to the collection folder
     */
    private void deleteEmptyParentDirs(java.io.File dir, java.io.File stopAt) {
        if (dir == null || !dir.exists() || dir.equals(stopAt)) {
            return;
        }

        // Only delete if directory is empty
        String[] children = dir.list();
        if (children != null && children.length == 0) {
            if (dir.delete()) {
                Log.d(TAG, "Deleted empty directory: " + dir.getAbsolutePath());
                // Recursively delete parent if also empty
                deleteEmptyParentDirs(dir.getParentFile(), stopAt);
            }
        }
    }

    public void clearCompleted() {
        downloads.entrySet().removeIf(entry ->
            entry.getValue().completed || entry.getValue().failed);
        saveToDisk();
    }

    public void pauseAll() {
        for (DownloadStatus status : downloads.values()) {
            if (!status.completed && !status.failed) {
                status.pause();
            }
        }
        saveToDisk();
    }

    public void resumeAll() {
        for (DownloadStatus status : downloads.values()) {
            if (!status.completed && !status.failed) {
                status.resume();
            }
        }
        saveToDisk();
    }

    /**
     * Save download queue to disk for persistence across app restarts
     */
    private synchronized void saveToDisk() {
        if (context == null) {
            Log.w(TAG, "Cannot save downloads - context not initialized");
            return;
        }

        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            List<DownloadStatus> downloadList = new ArrayList<>(downloads.values());
            String json = gson.toJson(downloadList);
            prefs.edit().putString(KEY_DOWNLOADS, json).apply();
            Log.d(TAG, "Saved " + downloadList.size() + " downloads to disk");
        } catch (Exception e) {
            Log.e(TAG, "Error saving downloads to disk", e);
        }
    }

    /**
     * Load download queue from disk on app start
     */
    private synchronized void loadFromDisk() {
        if (context == null) {
            Log.w(TAG, "Cannot load downloads - context not initialized");
            return;
        }

        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String json = prefs.getString(KEY_DOWNLOADS, null);

            Log.d(TAG, "Loading downloads from disk...");
            if (json != null) {
                Log.d(TAG, "Found saved downloads JSON: " + json.substring(0, Math.min(200, json.length())) + "...");
                Type listType = new TypeToken<ArrayList<DownloadStatus>>(){}.getType();
                List<DownloadStatus> downloadList = gson.fromJson(json, listType);

                if (downloadList != null && !downloadList.isEmpty()) {
                    int loaded = 0;
                    int skipped = 0;
                    int autoCompleted = 0;
                    for (DownloadStatus status : downloadList) {
                        // Skip completed/failed downloads older than 24 hours
                        long age = System.currentTimeMillis() - status.lastUpdateTime;
                        if ((status.completed || status.failed) && age > 86400000) {
                            Log.d(TAG, "Skipping old download: " + status.fileName + " (age: " + (age/3600000) + "h)");
                            skipped++;
                            continue;
                        }

                        // Validate file existence for non-completed downloads
                        if (!status.completed && !status.failed) {
                            boolean fileExistsAndComplete = checkFileExistsAndComplete(status);
                            if (fileExistsAndComplete) {
                                Log.i(TAG, "Download marked as active but file exists on disk, marking as completed: " + status.fileName);
                                status.markCompleted();
                                autoCompleted++;
                            }
                        }

                        downloads.put(status.fileId, status);
                        loaded++;

                        // Log status of each loaded download
                        String state = status.completed ? "completed" :
                                      status.failed ? "failed" :
                                      status.paused ? "paused" : "active";
                        Log.i(TAG, "Loaded download: " + status.fileName +
                              " (" + status.percentComplete + "%, " + state + ")");

                        // Mark incomplete downloads for resume (unpause them)
                        if (!status.completed && !status.failed && !status.paused) {
                            Log.i(TAG, "  → Will need to be resumed manually or automatically");
                        }
                    }
                    Log.i(TAG, "Successfully loaded " + loaded + " downloads from disk (skipped " + skipped + " old ones, auto-completed " + autoCompleted + ")");
                } else {
                    Log.d(TAG, "No downloads found in saved data");
                }
            } else {
                Log.d(TAG, "No saved downloads found");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading downloads from disk", e);
        }
    }

    /**
     * Manually trigger save (call after updating download progress)
     */
    public void save() {
        saveToDisk();
    }

    /**
     * Get list of incomplete downloads that should be resumed
     */
    public List<DownloadStatus> getIncompleteDownloads() {
        List<DownloadStatus> incomplete = new ArrayList<>();
        for (DownloadStatus status : downloads.values()) {
            if (!status.completed && !status.failed) {
                incomplete.add(status);
            }
        }
        return incomplete;
    }

    /**
     * Check if a download's file exists on disk and is complete
     * @param status The download status to check
     * @return true if file exists and matches expected size
     */
    private boolean checkFileExistsAndComplete(DownloadStatus status) {
        if (context == null || status == null || status.fileId == null) {
            return false;
        }

        try {
            // Extract collection ID and file path from fileId (format: "collectionId/path/to/file")
            if (!status.fileId.contains("/")) {
                return false;
            }

            String collectionId = status.fileId.substring(0, status.fileId.indexOf("/"));
            String filePath = status.fileId.substring(status.fileId.indexOf("/") + 1);

            // Get the file location
            java.io.File collectionsDir = new java.io.File(context.getFilesDir(), "collections");
            java.io.File collectionFolder = new java.io.File(collectionsDir, collectionId);
            java.io.File targetFile = new java.io.File(collectionFolder, filePath);

            // Check if file exists
            if (!targetFile.exists() || !targetFile.isFile()) {
                return false;
            }

            // Check if file size matches expected size (allowing for small differences due to rounding)
            long fileSize = targetFile.length();
            long expectedSize = status.totalBytes;

            // File is complete if it exists and size matches (within 1% tolerance)
            boolean sizeMatches = Math.abs(fileSize - expectedSize) <= (expectedSize * 0.01);

            if (sizeMatches) {
                Log.d(TAG, "File exists and size matches: " + targetFile.getAbsolutePath() +
                          " (size: " + fileSize + ", expected: " + expectedSize + ")");
                return true;
            } else {
                Log.d(TAG, "File exists but size mismatch: " + targetFile.getAbsolutePath() +
                          " (size: " + fileSize + ", expected: " + expectedSize + ")");
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking file existence: " + e.getMessage());
            return false;
        }
    }

    /**
     * Check if a download exists and return its status
     */
    public boolean hasDownload(String fileId) {
        return downloads.containsKey(fileId);
    }
}

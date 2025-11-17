package offgrid.geogram.util;

import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages chunked file downloads with support for out-of-order chunk arrivals.
 *
 * Features:
 * - Saves partial downloads as .partial files
 * - Tracks chunk status in text manifest files
 * - Handles out-of-order chunk arrivals
 * - Reassembles chunks into final file when complete
 *
 * File structure:
 * - Downloads/acme.zip.partial - Binary file with chunks written at correct offsets
 * - Downloads/acme.zip.manifest - Text file tracking chunk status
 */
public class ChunkDownloadManager {
    private static final String TAG = "ChunkDownloadManager";
    private static final int DEFAULT_CHUNK_SIZE = 4096; // 4KB chunks for BLE

    // Track active downloads: fileId -> ChunkDownload
    private final Map<String, ChunkDownload> activeDownloads = new ConcurrentHashMap<>();

    private static ChunkDownloadManager instance;

    public static synchronized ChunkDownloadManager getInstance() {
        if (instance == null) {
            instance = new ChunkDownloadManager();
        }
        return instance;
    }

    private ChunkDownloadManager() {}

    /**
     * Start a new chunked download or resume an existing one.
     *
     * @param fileId Unique identifier for this download
     * @param fileName Name of the file being downloaded
     * @param totalSize Total size of the file in bytes
     * @param downloadDir Directory where .partial and .manifest files are saved
     * @param chunkSize Size of each chunk (default 8KB)
     * @return ChunkDownload object to track progress
     */
    public ChunkDownload startDownload(String fileId, String fileName, long totalSize,
                                       File downloadDir, int chunkSize) {
        ChunkDownload download = activeDownloads.get(fileId);

        if (download == null) {
            download = new ChunkDownload(fileId, fileName, totalSize, downloadDir, chunkSize);
            activeDownloads.put(fileId, download);
            Log.i(TAG, "Started new chunked download: " + fileName +
                      " (" + download.getTotalChunks() + " chunks of " + chunkSize + " bytes)");
        } else {
            Log.i(TAG, "Resuming chunked download: " + fileName +
                      " (" + download.getCompletedChunks() + "/" + download.getTotalChunks() + " chunks complete)");
        }

        return download;
    }

    /**
     * Get an active download by fileId.
     */
    public ChunkDownload getDownload(String fileId) {
        return activeDownloads.get(fileId);
    }

    /**
     * Remove a download from active tracking (call after completion or cancellation).
     */
    public void removeDownload(String fileId) {
        activeDownloads.remove(fileId);
    }

    /**
     * Get all active downloads.
     */
    public Map<String, ChunkDownload> getActiveDownloads() {
        return new HashMap<>(activeDownloads);
    }

    /**
     * Represents a single chunked download with manifest tracking.
     */
    public static class ChunkDownload {
        private final String fileId;
        private final String fileName;
        private final long totalSize;
        private final File downloadDir;
        private final int chunkSize;
        private final int totalChunks;

        private final File partialFile;
        private final File manifestFile;
        private final File finalFile;

        // Track which chunks are complete: chunkIndex -> true
        private final Map<Integer, Boolean> completedChunks = new ConcurrentHashMap<>();

        private long startTimeMs;
        private boolean completed = false;
        private boolean failed = false;
        private String errorMessage = null;

        public ChunkDownload(String fileId, String fileName, long totalSize,
                           File downloadDir, int chunkSize) {
            this.fileId = fileId;
            this.fileName = fileName;
            this.totalSize = totalSize;
            this.downloadDir = downloadDir;
            this.chunkSize = chunkSize;
            this.totalChunks = (int) Math.ceil((double) totalSize / chunkSize);

            this.partialFile = new File(downloadDir, fileName + ".partial");
            this.manifestFile = new File(downloadDir, fileName + ".manifest");
            this.finalFile = new File(downloadDir, fileName);

            this.startTimeMs = System.currentTimeMillis();

            // Create download directory if needed
            if (!downloadDir.exists()) {
                downloadDir.mkdirs();
            }

            // Load existing manifest if resuming
            loadManifest();

            // Create empty partial file if starting fresh
            if (!partialFile.exists()) {
                try {
                    partialFile.createNewFile();
                    // Pre-allocate file to total size
                    RandomAccessFile raf = new RandomAccessFile(partialFile, "rw");
                    raf.setLength(totalSize);
                    raf.close();
                    Log.i(TAG, "Created partial file: " + partialFile.getAbsolutePath() +
                              " (" + totalSize + " bytes)");
                } catch (IOException e) {
                    Log.e(TAG, "Failed to create partial file", e);
                }
            }
        }

        /**
         * Write a chunk to the partial file at the correct offset.
         * Handles out-of-order chunks by using RandomAccessFile.
         *
         * @param chunkIndex Index of this chunk (0-based)
         * @param data Chunk data to write
         * @return true if successful, false on error
         */
        public synchronized boolean writeChunk(int chunkIndex, byte[] data) {
            if (chunkIndex < 0 || chunkIndex >= totalChunks) {
                Log.e(TAG, "Invalid chunk index: " + chunkIndex + " (max: " + (totalChunks - 1) + ")");
                return false;
            }

            if (completedChunks.containsKey(chunkIndex)) {
                Log.w(TAG, "Chunk " + chunkIndex + " already completed, skipping");
                return true;
            }

            try {
                long offset = (long) chunkIndex * chunkSize;
                RandomAccessFile raf = new RandomAccessFile(partialFile, "rw");
                raf.seek(offset);
                raf.write(data);
                raf.close();

                // Mark chunk as complete
                completedChunks.put(chunkIndex, true);

                // Update manifest
                saveManifest();

                Log.i(TAG, "Wrote chunk " + chunkIndex + "/" + (totalChunks - 1) +
                          " (" + data.length + " bytes at offset " + offset + ") - " +
                          getCompletedChunks() + "/" + totalChunks + " complete");

                // Check if download is complete
                if (isComplete()) {
                    finalizeDownload();
                }

                return true;
            } catch (IOException e) {
                Log.e(TAG, "Failed to write chunk " + chunkIndex, e);
                return false;
            }
        }

        /**
         * Check if a specific chunk is already completed.
         */
        public boolean isChunkCompleted(int chunkIndex) {
            return completedChunks.containsKey(chunkIndex);
        }

        /**
         * Get the next chunk that needs to be downloaded.
         * @return chunk index, or -1 if all chunks complete
         */
        public int getNextChunkToDownload() {
            for (int i = 0; i < totalChunks; i++) {
                if (!completedChunks.containsKey(i)) {
                    return i;
                }
            }
            return -1; // All chunks complete
        }

        /**
         * Check if all chunks are complete.
         */
        public boolean isComplete() {
            return completedChunks.size() == totalChunks;
        }

        /**
         * Get number of completed chunks.
         */
        public int getCompletedChunks() {
            return completedChunks.size();
        }

        /**
         * Get completion percentage (0-100).
         */
        public int getPercentComplete() {
            return (int) ((completedChunks.size() * 100L) / totalChunks);
        }

        /**
         * Get downloaded bytes.
         */
        public long getDownloadedBytes() {
            return (long) completedChunks.size() * chunkSize;
        }

        /**
         * Finalize the download by renaming .partial to final filename.
         */
        private void finalizeDownload() {
            Log.i(TAG, "All chunks complete! Finalizing download: " + fileName);

            try {
                // Rename partial file to final file
                if (finalFile.exists()) {
                    finalFile.delete();
                }

                if (partialFile.renameTo(finalFile)) {
                    Log.i(TAG, "Download complete: " + finalFile.getAbsolutePath());
                    completed = true;

                    // Delete manifest file
                    if (manifestFile.exists()) {
                        manifestFile.delete();
                    }
                } else {
                    Log.e(TAG, "Failed to rename partial file to final file");
                    markFailed("Failed to finalize download");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error finalizing download", e);
                markFailed("Error finalizing: " + e.getMessage());
            }
        }

        /**
         * Load manifest from disk to resume download.
         */
        private void loadManifest() {
            if (!manifestFile.exists()) {
                return;
            }

            try (BufferedReader reader = new BufferedReader(new FileReader(manifestFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("chunk")) {
                        // Format: chunk5=complete,40960-49151
                        String[] parts = line.split("=");
                        if (parts.length == 2 && parts[1].startsWith("complete,")) {
                            String chunkNumStr = parts[0].substring(5); // Remove "chunk" prefix
                            int chunkIndex = Integer.parseInt(chunkNumStr);
                            completedChunks.put(chunkIndex, true);
                        }
                    }
                }
                Log.i(TAG, "Loaded manifest: " + completedChunks.size() + " chunks already complete");
            } catch (IOException e) {
                Log.e(TAG, "Failed to load manifest", e);
            }
        }

        /**
         * Save manifest to disk.
         *
         * Format:
         * filename=acme.zip
         * totalSize=102400
         * chunkSize=10240
         * totalChunks=10
         * chunk0=complete,0-10239
         * chunk1=complete,10240-20479
         * chunk2=pending,20480-30719
         * ...
         */
        private void saveManifest() {
            try (FileWriter writer = new FileWriter(manifestFile)) {
                writer.write("filename=" + fileName + "\n");
                writer.write("totalSize=" + totalSize + "\n");
                writer.write("chunkSize=" + chunkSize + "\n");
                writer.write("totalChunks=" + totalChunks + "\n");

                for (int i = 0; i < totalChunks; i++) {
                    long startOffset = (long) i * chunkSize;
                    long endOffset = Math.min(startOffset + chunkSize - 1, totalSize - 1);
                    String status = completedChunks.containsKey(i) ? "complete" : "pending";
                    writer.write("chunk" + i + "=" + status + "," + startOffset + "-" + endOffset + "\n");
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed to save manifest", e);
            }
        }

        /**
         * Mark download as failed.
         */
        public void markFailed(String errorMessage) {
            this.failed = true;
            this.errorMessage = errorMessage;
            Log.e(TAG, "Download failed: " + fileName + " - " + errorMessage);
        }

        // Getters
        public String getFileId() { return fileId; }
        public String getFileName() { return fileName; }
        public long getTotalSize() { return totalSize; }
        public int getChunkSize() { return chunkSize; }
        public int getTotalChunks() { return totalChunks; }
        public boolean isCompleted() { return completed; }
        public boolean isFailed() { return failed; }
        public String getErrorMessage() { return errorMessage; }
        public File getPartialFile() { return partialFile; }
        public File getManifestFile() { return manifestFile; }
        public File getFinalFile() { return finalFile; }
        public long getElapsedTimeMs() { return System.currentTimeMillis() - startTimeMs; }
    }
}

package offgrid.geogram.util;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;

import com.google.gson.Gson;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import offgrid.geogram.models.Collection;
import offgrid.geogram.settings.AppConfig;
import offgrid.geogram.settings.ConfigManager;

public class BackupManager {

    private static final String TAG = "BackupManager";
    private final Context context;
    private final ConfigManager configManager;

    public BackupManager(Context context) {
        this.context = context;
        this.configManager = ConfigManager.getInstance(context);
    }

    /**
     * Export data to a ZIP file
     */
    public boolean exportData(boolean includeCollections, boolean includeSettings, boolean includeIdentity, Uri destinationUri) {
        try {
            // Create temporary ZIP file
            File tempZipFile = new File(context.getCacheDir(), "backup_temp.zip");

            try (FileOutputStream fos = new FileOutputStream(tempZipFile);
                 ZipOutputStream zos = new ZipOutputStream(fos)) {

                // Add settings if selected
                if (includeSettings) {
                    addSettingsToZip(zos);
                }

                // Add collections if selected
                if (includeCollections) {
                    addCollectionsToZip(zos);
                }

                // Add identity if selected
                if (includeIdentity) {
                    addIdentityToZip(zos);
                }

                zos.finish();
            }

            // Copy to destination
            try (FileInputStream fis = new FileInputStream(tempZipFile);
                  OutputStream os = context.getContentResolver().openOutputStream(destinationUri)) {

                byte[] buffer = new byte[8192];
                int length;
                while ((length = fis.read(buffer)) > 0) {
                    os.write(buffer, 0, length);
                }
            }

            // Clean up temp file
            tempZipFile.delete();

            Log.i(TAG, "Backup export completed successfully");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Error during backup export", e);
            return false;
        }
    }

    /**
     * Validate ZIP archive contents before import
     */
    private boolean validateZipContents(boolean includeCollections, boolean includeSettings, boolean includeIdentity, Uri sourceUri) {
        try (InputStream is = context.getContentResolver().openInputStream(sourceUri);
             ZipInputStream zis = new ZipInputStream(is)) {

            List<String> foundFiles = new ArrayList<>();
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    foundFiles.add(entry.getName());
                }
                zis.closeEntry();
            }

            // Check if selected items are present
            boolean hasValidContent = false;

            if (includeSettings && foundFiles.contains("config.json")) {
                hasValidContent = true;
            }

            if (includeCollections && foundFiles.stream().anyMatch(name -> name.startsWith("collections/"))) {
                hasValidContent = true;
            }

            if (includeIdentity && foundFiles.contains("identity.json")) {
                hasValidContent = true;
            }

            return hasValidContent;

        } catch (Exception e) {
            Log.e(TAG, "Error validating ZIP contents", e);
            return false;
        }
    }

    /**
     * Import data from a ZIP file
     */
    public boolean importData(boolean includeCollections, boolean includeSettings, boolean includeIdentity, Uri sourceUri) {
        try {
            // Validate ZIP contents first
            if (!validateZipContents(includeCollections, includeSettings, includeIdentity, sourceUri)) {
                Log.e(TAG, "ZIP archive does not contain selected backup data");
                return false;
            }

            // Create temporary directory for extraction
            File tempDir = new File(context.getCacheDir(), "backup_extract");
            if (tempDir.exists()) {
                deleteDirectory(tempDir);
            }
            tempDir.mkdirs();

            // Extract ZIP to temp directory
            try (InputStream is = context.getContentResolver().openInputStream(sourceUri);
                  ZipInputStream zis = new ZipInputStream(is)) {

                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    File entryFile = new File(tempDir, entry.getName());

                    if (entry.isDirectory()) {
                        entryFile.mkdirs();
                    } else {
                        // Ensure parent directories exist
                        entryFile.getParentFile().mkdirs();

                        try (FileOutputStream fos = new FileOutputStream(entryFile)) {
                            byte[] buffer = new byte[8192];
                            int length;
                            while ((length = zis.read(buffer)) > 0) {
                                fos.write(buffer, 0, length);
                            }
                        }
                    }
                    zis.closeEntry();
                }
            }

            // Import selected data
            boolean success = true;

            if (includeSettings && !importSettings(tempDir)) {
                Log.e(TAG, "Failed to import settings");
                success = false;
            }

            if (includeCollections && !importCollections(tempDir)) {
                Log.e(TAG, "Failed to import collections");
                success = false;
            }

            if (includeIdentity && !importIdentity(tempDir)) {
                Log.e(TAG, "Failed to import identity");
                success = false;
            }

            // Clean up temp directory
            deleteDirectory(tempDir);

            if (success) {
                Log.i(TAG, "Backup import completed successfully");
            }
            return success;

        } catch (Exception e) {
            Log.e(TAG, "Error during backup import", e);
            return false;
        }
    }

    /**
     * Generate backup filename based on current date and identity inclusion
     */
    public static String generateBackupFilename(boolean includeIdentity, String callsign) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String date = sdf.format(new Date());

        if (includeIdentity && callsign != null && !callsign.isEmpty()) {
            return "geogram-backup-" + callsign + "-" + date + ".zip";
        } else {
            return "geogram-backup_" + date + ".zip";
        }
    }

    /**
     * Get the current user's callsign
     */
    public String getCallsign() {
        return configManager.getCallsign();
    }

    private void addSettingsToZip(ZipOutputStream zos) throws IOException {
        File configFile = new File(context.getFilesDir(), "config.json");
        if (configFile.exists()) {
            zos.putNextEntry(new ZipEntry("config.json"));
            try (FileInputStream fis = new FileInputStream(configFile)) {
                byte[] buffer = new byte[8192];
                int length;
                while ((length = fis.read(buffer)) > 0) {
                    zos.write(buffer, 0, length);
                }
            }
            zos.closeEntry();
            Log.i(TAG, "Added config.json to backup");
        }
    }

    private void addCollectionsToZip(ZipOutputStream zos) throws IOException {
        File collectionsDir = new File(context.getFilesDir(), "collections");
        if (collectionsDir.exists() && collectionsDir.isDirectory()) {
            addDirectoryToZip(zos, collectionsDir, "collections");
            Log.i(TAG, "Added collections directory to backup");
        }
    }

    private void addIdentityToZip(ZipOutputStream zos) throws IOException {
        AppConfig config = configManager.getConfig();

        // Create identity JSON
        String identityJson = String.format(
            "{\"nsec\":\"%s\",\"npub\":\"%s\",\"callsign\":\"%s\"}",
            config.getNsec(),
            config.getNpub(),
            config.getCallsign()
        );

        zos.putNextEntry(new ZipEntry("identity.json"));
        zos.write(identityJson.getBytes());
        zos.closeEntry();

        Log.i(TAG, "Added identity data to backup");
    }

    private void addDirectoryToZip(ZipOutputStream zos, File dir, String basePath) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                addDirectoryToZip(zos, file, basePath + "/" + file.getName());
            } else {
                String entryName = basePath + "/" + file.getName();
                zos.putNextEntry(new ZipEntry(entryName));
                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[8192];
                    int length;
                    while ((length = fis.read(buffer)) > 0) {
                        zos.write(buffer, 0, length);
                    }
                }
                zos.closeEntry();
            }
        }
    }

    private boolean importSettings(File tempDir) {
        try {
            File configFile = new File(tempDir, "config.json");
            if (configFile.exists()) {
                File destFile = new File(context.getFilesDir(), "config.json");
                copyFile(configFile, destFile);

                // Reload config
                configManager.initialize();

                Log.i(TAG, "Imported settings from backup");
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error importing settings", e);
        }
        return false;
    }

    private boolean importCollections(File tempDir) {
        try {
            File collectionsDir = new File(tempDir, "collections");
            if (collectionsDir.exists() && collectionsDir.isDirectory()) {
                File destDir = new File(context.getFilesDir(), "collections");

                // Remove existing collections directory if it exists
                if (destDir.exists()) {
                    deleteDirectory(destDir);
                }

                // Copy collections
                copyDirectory(collectionsDir, destDir);

                Log.i(TAG, "Imported collections from backup");
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error importing collections", e);
        }
        return false;
    }

    private boolean importIdentity(File tempDir) {
        try {
            File identityFile = new File(tempDir, "identity.json");
            if (identityFile.exists()) {
                Gson gson = new Gson();
                String content = new String(Files.readAllBytes(identityFile.toPath()));

                // Parse identity data
                IdentityData identityData = gson.fromJson(content, IdentityData.class);

                // Update config
                configManager.updateConfig(config -> {
                    config.setNsec(identityData.nsec);
                    config.setNpub(identityData.npub);
                    config.setCallsign(identityData.callsign);
                });

                Log.i(TAG, "Imported identity from backup");
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error importing identity", e);
        }
        return false;
    }

    private void copyFile(File source, File dest) throws IOException {
        try (FileInputStream fis = new FileInputStream(source);
             FileOutputStream fos = new FileOutputStream(dest)) {

            byte[] buffer = new byte[8192];
            int length;
            while ((length = fis.read(buffer)) > 0) {
                fos.write(buffer, 0, length);
            }
        }
    }

    private void copyDirectory(File source, File dest) throws IOException {
        if (!dest.exists()) {
            dest.mkdirs();
        }

        File[] files = source.listFiles();
        if (files != null) {
            for (File file : files) {
                File destFile = new File(dest, file.getName());
                if (file.isDirectory()) {
                    copyDirectory(file, destFile);
                } else {
                    copyFile(file, destFile);
                }
            }
        }
    }

    private boolean deleteDirectory(File dir) {
        if (dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            return dir.delete();
        }
        return false;
    }

    private static class IdentityData {
        String nsec;
        String npub;
        String callsign;
    }
}
package offgrid.geogram.util;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

import offgrid.geogram.models.Collection;
import offgrid.geogram.models.CollectionFile;
import offgrid.geogram.models.CollectionSecurity;
import offgrid.geogram.util.CollectionKeysManager;

public class CollectionLoader {

    public static List<Collection> loadCollectionsFromAppStorage(Context context) {
        List<Collection> collections = new ArrayList<>();

        File collectionsDir = new File(context.getFilesDir(), "collections");
        android.util.Log.i("CollectionLoader", "Looking for collections in: " + collectionsDir.getAbsolutePath());

        if (!collectionsDir.exists() || !collectionsDir.isDirectory()) {
            android.util.Log.w("CollectionLoader", "Collections directory does not exist");
            return collections;
        }

        File[] collectionFolders = collectionsDir.listFiles();
        if (collectionFolders == null) {
            android.util.Log.w("CollectionLoader", "Collections directory is empty");
            return collections;
        }

        android.util.Log.i("CollectionLoader", "Found " + collectionFolders.length + " potential collection folders");
        for (File collectionFolder : collectionFolders) {
            if (collectionFolder.isDirectory()) {
                android.util.Log.i("CollectionLoader", "Attempting to load collection from: " + collectionFolder.getName());
                Collection collection = loadCollectionFromFolder(context, collectionFolder);
                if (collection != null) {
                    android.util.Log.i("CollectionLoader", "Successfully loaded collection: " + collection.getTitle());
                    collections.add(collection);
                } else {
                    android.util.Log.w("CollectionLoader", "Failed to load collection from: " + collectionFolder.getName());
                }
            }
        }

        android.util.Log.i("CollectionLoader", "Total collections loaded: " + collections.size());
        return collections;
    }

    private static Collection loadCollectionFromFolder(Context context, File folder) {
        File collectionJs = new File(folder, "collection.js");

        if (!collectionJs.exists()) {
            android.util.Log.w("CollectionLoader", "collection.js not found in: " + folder.getName());
            return null;
        }

        android.util.Log.i("CollectionLoader", "Found collection.js, reading content...");
        try {
            // Read collection.js file
            String content = readFile(collectionJs);
            android.util.Log.i("CollectionLoader", "collection.js content length: " + content.length());

            // Extract JSON from JavaScript file
            int startIndex = content.indexOf("window.COLLECTION_DATA = {");
            if (startIndex == -1) {
                android.util.Log.e("CollectionLoader", "Could not find 'window.COLLECTION_DATA' in collection.js");
                return null;
            }

            startIndex = content.indexOf("{", startIndex);
            int endIndex = content.lastIndexOf("};");
            if (endIndex == -1) {
                // Try without semicolon
                endIndex = content.lastIndexOf("}");
                if (endIndex == -1) {
                    android.util.Log.e("CollectionLoader", "Could not find closing brace in collection.js");
                    return null;
                }
            }

            String jsonContent = content.substring(startIndex, endIndex + 1);
            android.util.Log.i("CollectionLoader", "Extracted JSON, length: " + jsonContent.length());

            JSONObject data = new JSONObject(jsonContent);

            JSONObject collectionObj = data.optJSONObject("collection");
            if (collectionObj == null) {
                android.util.Log.e("CollectionLoader", "No 'collection' object found in JSON");
                return null;
            }

            String id = collectionObj.optString("id", "");
            String title = collectionObj.optString("title", "");
            String description = collectionObj.optString("description", "");
            String updated = collectionObj.optString("updated", "");

            android.util.Log.i("CollectionLoader", "Parsed collection: id=" + id + ", title=" + title);

            Collection collection = new Collection(id, title, description);
            collection.setUpdated(updated);
            collection.setStoragePath(folder.getAbsolutePath());

            // Check if we own this collection (have the nsec)
            collection.setOwned(CollectionKeysManager.isOwnedCollection(context, id));

            // Load favorite status
            collection.setFavorite(CollectionPreferences.isFavorite(context, id));

            // Load security.json if it exists
            File securityJson = new File(folder, "extra/security.json");
            if (securityJson.exists()) {
                try {
                    String securityContent = readFile(securityJson);
                    JSONObject securityData = new JSONObject(securityContent);
                    CollectionSecurity security = CollectionSecurity.fromJSON(securityData);
                    collection.setSecurity(security);
                } catch (Exception e) {
                    e.printStackTrace();
                    // If security.json fails to parse, create default public security
                    collection.setSecurity(new CollectionSecurity());
                }
            } else {
                // No security.json found, create default public security
                collection.setSecurity(new CollectionSecurity());
            }

            // Try to load file data from tree-data.js if it exists
            File treeDataJs = new File(folder, "extra/tree-data.js");
            boolean treeDataExists = treeDataJs.exists();

            if (treeDataExists) {
                loadFilesFromTreeDataJs(collection, treeDataJs);
            } else {
                // Scan folder for files
                scanFolder(collection, folder, "");
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

            // Generate tree-data.js if it doesn't exist or if we had to scan manually
            if (!treeDataExists && collection.isOwned()) {
                android.util.Log.i("CollectionLoader", "Generating missing tree-data.js for collection: " + title);
                generateTreeDataJs(collection, folder);
            }

            return collection;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static void loadFilesFromTreeDataJs(Collection collection, File treeDataFile) {
        try {
            String content = readFile(treeDataFile);

            // Extract JSON from JavaScript file
            // Look for window.TREE_DATA = [...]
            int startIndex = content.indexOf("window.TREE_DATA = [");
            if (startIndex == -1) return;

            startIndex = content.indexOf("[", startIndex);
            int endIndex = content.lastIndexOf("];");
            if (endIndex == -1) {
                // Try without semicolon
                endIndex = content.lastIndexOf("]");
                if (endIndex == -1) return;
            }

            String jsonContent = content.substring(startIndex, endIndex + 1);
            JSONArray filesArray = new JSONArray(jsonContent);

            if (filesArray != null) {
                for (int i = 0; i < filesArray.length(); i++) {
                    JSONObject fileObj = filesArray.getJSONObject(i);

                    String path = fileObj.optString("path", "");
                    String name = fileObj.optString("name", "");
                    String type = fileObj.optString("type", "file");

                    CollectionFile.FileType fileType = "directory".equals(type) ?
                        CollectionFile.FileType.DIRECTORY : CollectionFile.FileType.FILE;

                    CollectionFile file = new CollectionFile(path, name, fileType);

                    if (!file.isDirectory()) {
                        file.setSize(fileObj.optLong("size", 0));
                        file.setMimeType(fileObj.optString("mimeType", null));
                    }

                    collection.addFile(file);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void scanFolder(Collection collection, File folder, String currentPath) {
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
                scanFolder(collection, file, filePath);
            } else {
                CollectionFile fileEntry = new CollectionFile(filePath, fileName,
                        CollectionFile.FileType.FILE);
                fileEntry.setSize(file.length());
                collection.addFile(fileEntry);
            }
        }
    }

    private static String readFile(File file) throws Exception {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        return content.toString();
    }

    private static void generateTreeDataJs(Collection collection, File collectionFolder) {
        try {
            // Ensure extra/ folder exists
            File extraDir = new File(collectionFolder, "extra");
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
                    sb.append("      \"sha1\": \"").append(calculateSHA1(new File(collectionFolder, file.getPath()))).append("\"\n");
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

            android.util.Log.i("CollectionLoader", "Successfully generated tree-data.js with " + files.size() + " entries");

        } catch (Exception e) {
            android.util.Log.e("CollectionLoader", "Error generating tree-data.js: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String calculateSHA1(File file) {
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

    private static String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}

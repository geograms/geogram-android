package offgrid.geogram.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Generates BitTorrent metadata files for collections
 */
public class TorrentGenerator {

    private static final int PIECE_LENGTH = 256 * 1024; // 256 KB pieces

    public static class TorrentInfo {
        public String infoHash;
        public long totalSize;
        public int pieceCount;
    }

    /**
     * Generates a .torrent file for a collection
     * @param collectionRoot The root directory of the collection
     * @param outputFile The output .torrent file
     * @param trackers List of tracker URLs (optional)
     * @return TorrentInfo with metadata about the generated torrent
     */
    public static TorrentInfo generateTorrent(File collectionRoot, File outputFile, List<String> trackers) throws Exception {
        if (!collectionRoot.exists() || !collectionRoot.isDirectory()) {
            throw new IllegalArgumentException("Collection root must be a valid directory");
        }

        // Collect all files (excluding extra/ folder and collection.js)
        List<File> files = new ArrayList<>();
        collectFiles(collectionRoot, collectionRoot, files);

        if (files.isEmpty()) {
            throw new IllegalArgumentException("No files found in collection");
        }

        // Calculate total size
        long totalSize = 0;
        for (File file : files) {
            totalSize += file.length();
        }

        // Generate pieces
        List<byte[]> pieces = generatePieces(files);

        // Build torrent dictionary
        Map<String, Object> torrent = new TreeMap<>();

        // Add announce (tracker) if provided
        if (trackers != null && !trackers.isEmpty()) {
            torrent.put("announce", trackers.get(0));
            if (trackers.size() > 1) {
                List<List<String>> announceList = new ArrayList<>();
                for (String tracker : trackers) {
                    announceList.add(Arrays.asList(tracker));
                }
                torrent.put("announce-list", announceList);
            }
        }

        torrent.put("created by", "Geogram");
        torrent.put("creation date", System.currentTimeMillis() / 1000);

        // Build info dictionary
        Map<String, Object> info = new TreeMap<>();
        info.put("name", collectionRoot.getName());
        info.put("piece length", PIECE_LENGTH);

        // Concatenate all piece hashes
        byte[] piecesData = new byte[pieces.size() * 20];
        for (int i = 0; i < pieces.size(); i++) {
            System.arraycopy(pieces.get(i), 0, piecesData, i * 20, 20);
        }
        info.put("pieces", piecesData);

        // Add files list
        List<Map<String, Object>> filesList = new ArrayList<>();
        for (File file : files) {
            Map<String, Object> fileInfo = new TreeMap<>();
            fileInfo.put("length", file.length());

            // Get path relative to collection root
            String relativePath = collectionRoot.toPath().relativize(file.toPath()).toString();
            String[] pathParts = relativePath.split(File.separator.equals("\\") ? "\\\\" : File.separator);
            fileInfo.put("path", Arrays.asList(pathParts));

            filesList.add(fileInfo);
        }
        info.put("files", filesList);

        torrent.put("info", info);

        // Encode and write torrent file
        byte[] torrentData = bencode(torrent);
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            fos.write(torrentData);
        }

        // Calculate info hash
        byte[] infoEncoded = bencode(info);
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] infoHashBytes = sha1.digest(infoEncoded);
        String infoHash = bytesToHex(infoHashBytes);

        TorrentInfo result = new TorrentInfo();
        result.infoHash = infoHash;
        result.totalSize = totalSize;
        result.pieceCount = pieces.size();

        return result;
    }

    private static void collectFiles(File root, File current, List<File> files) {
        File[] children = current.listFiles();
        if (children == null) return;

        for (File child : children) {
            // Skip extra/ folder and collection.js
            if (child.getName().equals("extra") || child.getName().equals("collection.js")) {
                continue;
            }

            if (child.isDirectory()) {
                collectFiles(root, child, files);
            } else {
                files.add(child);
            }
        }
    }

    private static List<byte[]> generatePieces(List<File> files) throws Exception {
        List<byte[]> pieces = new ArrayList<>();
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");

        byte[] buffer = new byte[PIECE_LENGTH];
        int bufferPos = 0;

        for (File file : files) {
            try (FileInputStream fis = new FileInputStream(file)) {
                int bytesRead;
                byte[] readBuffer = new byte[8192];

                while ((bytesRead = fis.read(readBuffer)) != -1) {
                    int offset = 0;

                    while (offset < bytesRead) {
                        int toCopy = Math.min(bytesRead - offset, PIECE_LENGTH - bufferPos);
                        System.arraycopy(readBuffer, offset, buffer, bufferPos, toCopy);
                        bufferPos += toCopy;
                        offset += toCopy;

                        if (bufferPos == PIECE_LENGTH) {
                            // Complete piece
                            sha1.update(buffer);
                            pieces.add(sha1.digest());
                            sha1.reset();
                            bufferPos = 0;
                        }
                    }
                }
            }
        }

        // Hash remaining partial piece
        if (bufferPos > 0) {
            sha1.update(buffer, 0, bufferPos);
            pieces.add(sha1.digest());
        }

        return pieces;
    }

    /**
     * Bencode encoding implementation
     */
    private static byte[] bencode(Object obj) throws Exception {
        StringBuilder sb = new StringBuilder();
        bencodeObject(obj, sb);
        return sb.toString().getBytes("ISO-8859-1");
    }

    private static void bencodeObject(Object obj, StringBuilder sb) throws Exception {
        if (obj instanceof String) {
            String s = (String) obj;
            sb.append(s.length()).append(':').append(s);
        } else if (obj instanceof byte[]) {
            byte[] bytes = (byte[]) obj;
            sb.append(bytes.length).append(':');
            // For byte arrays, we need to preserve the raw bytes
            // This is a simplified version - in production use a proper library
            for (byte b : bytes) {
                sb.append((char) (b & 0xFF));
            }
        } else if (obj instanceof Integer || obj instanceof Long) {
            sb.append('i').append(obj).append('e');
        } else if (obj instanceof List) {
            sb.append('l');
            for (Object item : (List<?>) obj) {
                bencodeObject(item, sb);
            }
            sb.append('e');
        } else if (obj instanceof Map) {
            sb.append('d');
            Map<?, ?> map = (Map<?, ?>) obj;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                bencodeObject(entry.getKey(), sb);
                bencodeObject(entry.getValue(), sb);
            }
            sb.append('e');
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}

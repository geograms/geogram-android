package offgrid.geogram.models;

import java.io.Serializable;

public class CollectionFile implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum FileType {
        DIRECTORY,
        FILE
    }

    private String path;
    private String name;
    private FileType type;
    private long size;
    private String mimeType;
    private String description;
    private int views;

    public CollectionFile() {
        this.type = FileType.FILE;
    }

    public CollectionFile(String path, String name, FileType type) {
        this.path = path;
        this.name = name;
        this.type = type;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public FileType getType() {
        return type;
    }

    public void setType(FileType type) {
        this.type = type;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getViews() {
        return views;
    }

    public void setViews(int views) {
        this.views = views;
    }

    public boolean isDirectory() {
        return type == FileType.DIRECTORY;
    }

    public String getFormattedSize() {
        if (type == FileType.DIRECTORY) {
            return "";
        }
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", size / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", size / (1024.0 * 1024.0 * 1024.0));
        }
    }

    public String getFileExtension() {
        if (path == null || type == FileType.DIRECTORY) {
            return "";
        }
        int dotIndex = path.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < path.length() - 1) {
            return path.substring(dotIndex + 1).toLowerCase();
        }
        return "";
    }
}

package offgrid.geogram.adapters;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

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

    private List<CollectionFile> files;
    private final OnFileClickListener listener;
    private OnFileLongClickListener longClickListener;
    private String collectionStoragePath;
    private boolean isRemoteMode = false;
    private String remoteIp;
    private String deviceId;
    private String collectionNpub;
    private android.content.Context context;

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
        holder.bind(file, listener, longClickListener, collectionStoragePath, isRemoteMode, remoteIp, deviceId, collectionNpub, context);
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

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            fileIcon = itemView.findViewById(R.id.file_icon);
            fileName = itemView.findViewById(R.id.file_name);
            fileDetails = itemView.findViewById(R.id.file_details);
            fileDescription = itemView.findViewById(R.id.file_description);
        }

        public void bind(CollectionFile file, OnFileClickListener listener, OnFileLongClickListener longClickListener,
                         String storagePath, boolean isRemote, String remoteIp, String deviceId, String collectionNpub, android.content.Context context) {
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
                    if (isRemote && (remoteIp != null || deviceId != null) && collectionNpub != null && context != null) {
                        // Load thumbnail from remote device (via WiFi or relay)
                        android.util.Log.d("FileAdapter", "  → Loading REMOTE thumbnail");
                        loadRemoteThumbnail(file.getPath(), remoteIp, deviceId, collectionNpub, context);
                    } else if (storagePath != null) {
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

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onFileClick(file);
                }
            });

            itemView.setOnLongClickListener(v -> {
                if (longClickListener != null) {
                    longClickListener.onFileLongClick(file);
                    return true;
                }
                return false;
            });
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
    }
}

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
    private String collectionNpub;

    public FileAdapter(OnFileClickListener listener) {
        this.files = new ArrayList<>();
        this.listener = listener;
    }

    public void setCollectionStoragePath(String path) {
        this.collectionStoragePath = path;
    }

    public void setRemoteMode(boolean isRemote, String ip, String npub) {
        this.isRemoteMode = isRemote;
        this.remoteIp = ip;
        this.collectionNpub = npub;
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
        holder.bind(file, listener, longClickListener, collectionStoragePath, isRemoteMode, remoteIp, collectionNpub);
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
                         String storagePath, boolean isRemote, String remoteIp, String collectionNpub) {
            fileName.setText(file.getName());

            // Set icon based on type
            if (file.isDirectory()) {
                fileIcon.setImageResource(R.drawable.ic_folder);
                fileIcon.setImageTintList(android.content.res.ColorStateList.valueOf(0xFFFFFFFF)); // White tint for folder icon
                fileIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                fileDetails.setText("Folder");
            } else {
                // Check if it's an image file and load thumbnail
                if (isImageFile(file.getName())) {
                    if (isRemote && remoteIp != null && collectionNpub != null) {
                        // Load thumbnail from remote device
                        loadRemoteThumbnail(file.getPath(), remoteIp, collectionNpub);
                    } else if (storagePath != null) {
                        // Load local thumbnail
                        loadThumbnail(file.getPath(), storagePath);
                    } else {
                        fileIcon.setImageResource(R.drawable.ic_file);
                        fileIcon.setImageTintList(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
                        fileIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    }
                } else {
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
            File imageFile = new File(storagePath, filePath);
            if (!imageFile.exists()) {
                fileIcon.setImageResource(R.drawable.ic_file);
                fileIcon.setImageTintList(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
                fileIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                return;
            }

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
                if (thumbnail != null) {
                    fileIcon.setImageTintList(null); // Remove tint for actual images
                    fileIcon.setImageBitmap(thumbnail);
                    fileIcon.setScaleType(ImageView.ScaleType.CENTER_CROP);
                } else {
                    fileIcon.setImageResource(R.drawable.ic_file);
                    fileIcon.setImageTintList(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
                    fileIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                }
            } catch (Exception e) {
                fileIcon.setImageResource(R.drawable.ic_file);
                fileIcon.setImageTintList(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
                fileIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            }
        }

        private void loadRemoteThumbnail(String filePath, String remoteIp, String collectionNpub) {
            // Set placeholder while loading
            fileIcon.setImageResource(R.drawable.ic_file);
            fileIcon.setImageTintList(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
            fileIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);

            // Load thumbnail in background thread
            new Thread(() -> {
                try {
                    String thumbnailUrl = "http://" + remoteIp + ":45678/api/collections/" +
                            collectionNpub + "/thumbnail/" + filePath;

                    URL url = new URL(thumbnailUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);

                    int responseCode = conn.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        InputStream inputStream = conn.getInputStream();
                        Bitmap thumbnail = BitmapFactory.decodeStream(inputStream);
                        inputStream.close();

                        if (thumbnail != null) {
                            // Update UI on main thread
                            fileIcon.post(() -> {
                                fileIcon.setImageTintList(null); // Remove tint for actual images
                                fileIcon.setImageBitmap(thumbnail);
                                fileIcon.setScaleType(ImageView.ScaleType.CENTER_CROP);
                            });
                        }
                    }

                    conn.disconnect();
                } catch (Exception e) {
                    android.util.Log.e("FileAdapter", "Error loading remote thumbnail: " + e.getMessage());
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

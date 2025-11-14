package offgrid.geogram.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import offgrid.geogram.R;
import offgrid.geogram.models.Collection;

public class CollectionAdapter extends RecyclerView.Adapter<CollectionAdapter.ViewHolder> {

    public interface OnCollectionClickListener {
        void onCollectionClick(Collection collection);
    }

    public interface OnCollectionLongClickListener {
        void onCollectionLongClick(Collection collection, View view);
    }

    private List<Collection> collections;
    private final OnCollectionClickListener listener;
    private final OnCollectionLongClickListener longClickListener;

    public CollectionAdapter(OnCollectionClickListener listener) {
        this(listener, null);
    }

    public CollectionAdapter(OnCollectionClickListener listener, OnCollectionLongClickListener longClickListener) {
        this.collections = new ArrayList<>();
        this.listener = listener;
        this.longClickListener = longClickListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_collection, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Collection collection = collections.get(position);
        holder.bind(collection, listener, longClickListener);
    }

    @Override
    public int getItemCount() {
        return collections.size();
    }

    public void updateCollections(List<Collection> newCollections) {
        this.collections = new ArrayList<>(newCollections);
        notifyDataSetChanged();
    }

    public void filter(String query) {
        // This would be called from the fragment with the full list
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView thumbnail;
        private final TextView title;
        private final TextView description;
        private final TextView filesCount;
        private final TextView size;
        private final TextView adminTag;
        private final ImageView favoriteStar;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.collection_thumbnail);
            title = itemView.findViewById(R.id.collection_title);
            description = itemView.findViewById(R.id.collection_description);
            filesCount = itemView.findViewById(R.id.collection_files_count);
            size = itemView.findViewById(R.id.collection_size);
            adminTag = itemView.findViewById(R.id.collection_admin_tag);
            favoriteStar = itemView.findViewById(R.id.collection_favorite_star);
        }

        public void bind(Collection collection, OnCollectionClickListener listener, OnCollectionLongClickListener longClickListener) {
            title.setText(collection.getTitle());
            description.setText(collection.getDescription());
            filesCount.setText(collection.getFilesCount() + " files");
            size.setText(collection.getFormattedSize());

            // Show favorite star if collection is favorited
            if (collection.isFavorite()) {
                favoriteStar.setVisibility(View.VISIBLE);
            } else {
                favoriteStar.setVisibility(View.GONE);
            }

            // Show ADMIN tag if user owns this collection
            if (collection.isOwned()) {
                adminTag.setVisibility(View.VISIBLE);
            } else {
                adminTag.setVisibility(View.GONE);
            }

            // TODO: Load actual thumbnail if available
            // For now, use default icon
            thumbnail.setImageResource(R.drawable.ic_collections);

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCollectionClick(collection);
                }
            });

            itemView.setOnLongClickListener(v -> {
                if (longClickListener != null) {
                    longClickListener.onCollectionLongClick(collection, v);
                    return true;
                }
                return false;
            });
        }
    }
}

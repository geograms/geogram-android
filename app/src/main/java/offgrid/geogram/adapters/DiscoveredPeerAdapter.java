package offgrid.geogram.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import offgrid.geogram.R;
import offgrid.geogram.p2p.AprsPeerDiscovery;

/**
 * Adapter for displaying discovered P2P peers
 */
public class DiscoveredPeerAdapter extends RecyclerView.Adapter<DiscoveredPeerAdapter.ViewHolder> {

    private List<AprsPeerDiscovery.PeerInfo> peers;
    private List<String> connectedPeerIds = new ArrayList<>();

    public DiscoveredPeerAdapter() {
        this.peers = new ArrayList<>();
    }

    public void setPeers(List<AprsPeerDiscovery.PeerInfo> peers) {
        this.peers = peers != null ? peers : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setConnectedPeers(List<String> connectedPeerIds) {
        this.connectedPeerIds = connectedPeerIds != null ? connectedPeerIds : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_discovered_peer, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AprsPeerDiscovery.PeerInfo peer = peers.get(position);
        boolean isConnected = connectedPeerIds.contains(peer.peerId);
        holder.bind(peer, isConnected);
    }

    @Override
    public int getItemCount() {
        return peers.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvCallsign;
        private final TextView tvPeerId;
        private final TextView tvLastSeen;
        private final View viewConnectionStatus;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCallsign = itemView.findViewById(R.id.tv_peer_callsign);
            tvPeerId = itemView.findViewById(R.id.tv_peer_id);
            tvLastSeen = itemView.findViewById(R.id.tv_peer_last_seen);
            viewConnectionStatus = itemView.findViewById(R.id.view_connection_status);
        }

        void bind(AprsPeerDiscovery.PeerInfo peer, boolean isConnected) {
            // Display callsign (or "Unknown" if not available)
            if (peer.callsign != null && !peer.callsign.isEmpty()) {
                tvCallsign.setText(peer.callsign);
            } else {
                tvCallsign.setText("Unknown");
            }

            // Display peer ID
            tvPeerId.setText(peer.peerId);

            // Display last seen time in human-readable format
            tvLastSeen.setText(formatLastSeen(peer.lastSeen));

            // Update connection status indicator
            if (isConnected) {
                viewConnectionStatus.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0xFF4CAF50)); // Green for connected
            } else {
                viewConnectionStatus.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0xFF666666)); // Gray for disconnected
            }
        }

        /**
         * Format last seen time as relative time (e.g., "2 hours ago")
         */
        private String formatLastSeen(long lastSeenMillis) {
            long now = System.currentTimeMillis();
            long diff = now - lastSeenMillis;

            if (diff < 0) {
                return "just now";
            }

            long minutes = TimeUnit.MILLISECONDS.toMinutes(diff);
            long hours = TimeUnit.MILLISECONDS.toHours(diff);
            long days = TimeUnit.MILLISECONDS.toDays(diff);

            if (minutes < 1) {
                return "just now";
            } else if (minutes < 60) {
                return minutes + (minutes == 1 ? " min ago" : " mins ago");
            } else if (hours < 24) {
                return hours + (hours == 1 ? " hour ago" : " hours ago");
            } else if (days < 7) {
                return days + (days == 1 ? " day ago" : " days ago");
            } else {
                // For older entries, show the actual date
                SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
                return sdf.format(new Date(lastSeenMillis));
            }
        }
    }
}

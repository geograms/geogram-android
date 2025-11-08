package offgrid.geogram.apps.messages;

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

import offgrid.geogram.R;

/**
 * Adapter for displaying conversation list in RecyclerView
 */
public class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.ConversationViewHolder> {

    private List<Conversation> conversations;
    private OnConversationClickListener clickListener;

    public interface OnConversationClickListener {
        void onConversationClick(Conversation conversation);
    }

    public ConversationAdapter(List<Conversation> conversations, OnConversationClickListener clickListener) {
        this.conversations = new ArrayList<>(conversations);
        this.clickListener = clickListener;
    }

    public void updateConversations(List<Conversation> newConversations) {
        this.conversations = new ArrayList<>(newConversations);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ConversationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_conversation, parent, false);
        return new ConversationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ConversationViewHolder holder, int position) {
        Conversation conversation = conversations.get(position);
        holder.bind(conversation);

        // Set click listener
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onConversationClick(conversation);
            }
        });
    }

    @Override
    public int getItemCount() {
        return conversations.size();
    }

    static class ConversationViewHolder extends RecyclerView.ViewHolder {
        private final TextView conversationName;
        private final TextView conversationTime;
        private final TextView conversationLastMessage;
        private final TextView conversationUnreadBadge;

        public ConversationViewHolder(@NonNull View itemView) {
            super(itemView);
            conversationName = itemView.findViewById(R.id.conversation_name);
            conversationTime = itemView.findViewById(R.id.conversation_time);
            conversationLastMessage = itemView.findViewById(R.id.conversation_last_message);
            conversationUnreadBadge = itemView.findViewById(R.id.conversation_unread_badge);
        }

        public void bind(Conversation conversation) {
            // Set conversation name
            conversationName.setText(conversation.getDisplayName());

            // Set last message
            String lastMsg = conversation.getLastMessage();
            if (lastMsg == null || lastMsg.isEmpty()) {
                conversationLastMessage.setText("No messages yet");
            } else {
                conversationLastMessage.setText(lastMsg);
            }

            // Format and set timestamp
            long timestamp = conversation.getLastMessageTime();
            if (timestamp > 0) {
                conversationTime.setText(formatTimestamp(timestamp));
                conversationTime.setVisibility(View.VISIBLE);
            } else {
                conversationTime.setVisibility(View.GONE);
            }

            // Show/hide unread badge
            int unreadCount = conversation.getUnreadCount();
            if (unreadCount > 0) {
                conversationUnreadBadge.setText(String.valueOf(unreadCount));
                conversationUnreadBadge.setVisibility(View.VISIBLE);
            } else {
                conversationUnreadBadge.setVisibility(View.GONE);
            }
        }

        private String formatTimestamp(long timestamp) {
            long now = System.currentTimeMillis();
            long diff = now - timestamp;

            if (diff < 60000) {
                return "Now";
            } else if (diff < 3600000) {
                long minutes = diff / 60000;
                return minutes + "m";
            } else if (diff < 86400000) {
                long hours = diff / 3600000;
                return hours + "h";
            } else if (diff < 604800000) {
                long days = diff / 86400000;
                return days + "d";
            } else {
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd", Locale.US);
                return sdf.format(new Date(timestamp));
            }
        }
    }
}

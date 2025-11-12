package offgrid.geogram.apps.chat;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import offgrid.geogram.MainActivity;
import offgrid.geogram.R;
import offgrid.geogram.database.DatabaseMessages;

/**
 * Manages Android push notifications for geochat messages.
 * Shows up to 5 most recent unread messages in the notification.
 */
public class ChatNotificationManager {
    private static final String TAG = "ChatNotifications";
    private static final String CHANNEL_ID = "GeochatMessages";
    private static final int NOTIFICATION_ID = 100;
    private static final int MAX_MESSAGES_IN_NOTIFICATION = 5;

    private static ChatNotificationManager instance;
    private final Context context;
    private final NotificationManager notificationManager;

    private ChatNotificationManager(Context context) {
        this.context = context.getApplicationContext();
        this.notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
    }

    public static synchronized ChatNotificationManager getInstance(Context context) {
        if (instance == null) {
            instance = new ChatNotificationManager(context);
        }
        return instance;
    }

    /**
     * Create notification channel for geochat messages (Android 8.0+)
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Geochat Messages",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications for new geochat messages");
            channel.enableVibration(true);
            channel.enableLights(true);

            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
                Log.d(TAG, "Geochat notification channel created");
            }
        }
    }

    /**
     * Show notification for unread geochat messages.
     * Displays up to 5 most recent unread messages.
     */
    public void showUnreadMessagesNotification() {
        try {
            Log.i(TAG, "showUnreadMessagesNotification called");

            // Get unread messages
            List<ChatMessage> unreadMessages = getUnreadMessages();
            Log.i(TAG, "Found " + unreadMessages.size() + " unread messages");

            if (unreadMessages.isEmpty()) {
                // No unread messages, clear notification
                Log.d(TAG, "No unread messages, clearing notification");
                clearNotification();
                return;
            }

            // Build notification
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("Geochat")
                    .setAutoCancel(true) // Remove notification when tapped
                    .setPriority(NotificationCompat.PRIORITY_HIGH);

            // Create intent to open chat when notification is tapped
            Intent intent = new Intent(context, MainActivity.class);
            intent.putExtra("open_chat", true); // Signal to open chat fragment
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            builder.setContentIntent(pendingIntent);

            // Set notification text based on number of unread messages
            int unreadCount = unreadMessages.size();
            if (unreadCount == 1) {
                // Single message - show author and content
                ChatMessage msg = unreadMessages.get(0);
                builder.setContentTitle(msg.authorId);
                builder.setContentText(msg.message);
            } else {
                // Multiple messages - show count and use inbox style
                builder.setContentText(unreadCount + " new messages");

                // Use InboxStyle to show multiple messages
                NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
                inboxStyle.setBigContentTitle("Geochat (" + unreadCount + " new)");

                // Show up to 5 most recent messages
                int messagesToShow = Math.min(unreadCount, MAX_MESSAGES_IN_NOTIFICATION);
                for (int i = 0; i < messagesToShow; i++) {
                    ChatMessage msg = unreadMessages.get(i);
                    String line = msg.authorId + ": " + msg.message;
                    // Truncate long messages
                    if (line.length() > 50) {
                        line = line.substring(0, 47) + "...";
                    }
                    inboxStyle.addLine(line);
                }

                // If there are more messages, show summary
                if (unreadCount > MAX_MESSAGES_IN_NOTIFICATION) {
                    inboxStyle.setSummaryText("+" + (unreadCount - MAX_MESSAGES_IN_NOTIFICATION) + " more");
                }

                builder.setStyle(inboxStyle);
            }

            // Show notification
            if (notificationManager != null) {
                Log.i(TAG, "Attempting to show notification for " + unreadCount + " unread messages");
                notificationManager.notify(NOTIFICATION_ID, builder.build());
                Log.i(TAG, "✓ Successfully showed notification");
            } else {
                Log.e(TAG, "NotificationManager is null, cannot show notification");
            }

        } catch (Exception e) {
            Log.e(TAG, "Error showing notification: " + e.getMessage(), e);
        }
    }

    /**
     * Clear the geochat notification
     */
    public void clearNotification() {
        if (notificationManager != null) {
            notificationManager.cancel(NOTIFICATION_ID);
            Log.d(TAG, "Cleared geochat notification");
        }
    }

    /**
     * Get list of unread messages (most recent first)
     */
    private List<ChatMessage> getUnreadMessages() {
        List<ChatMessage> unreadMessages = new ArrayList<>();

        TreeSet<ChatMessage> allMessages = DatabaseMessages.getInstance().getMessages();
        for (ChatMessage message : allMessages) {
            if (!message.isWrittenByMe() && !message.isRead()) {
                unreadMessages.add(message);
            }
        }

        // Sort by timestamp descending (most recent first)
        unreadMessages.sort((m1, m2) -> Long.compare(m2.timestamp, m1.timestamp));

        return unreadMessages;
    }

    /**
     * Check if we should show notification (app in background or chat not open)
     */
    public static boolean shouldShowNotification(Context context) {
        // Check if chat fragment is currently visible
        if (context instanceof MainActivity) {
            MainActivity mainActivity = (MainActivity) context;
            // Check if activity is in foreground
            if (!mainActivity.isFinishing() && !mainActivity.isDestroyed()) {
                // Check if chat fragment is visible
                if (offgrid.geogram.core.Central.getInstance() != null &&
                    offgrid.geogram.core.Central.getInstance().broadcastChatFragment != null &&
                    offgrid.geogram.core.Central.getInstance().broadcastChatFragment.isVisible()) {
                    // Chat is currently open, don't show notification
                    Log.d(TAG, "shouldShowNotification: false (chat is currently visible)");
                    return false;
                }
            }
        }
        // App in background or chat not open, show notification
        Log.d(TAG, "shouldShowNotification: true (app in background or chat not visible)");
        return true;
    }
}

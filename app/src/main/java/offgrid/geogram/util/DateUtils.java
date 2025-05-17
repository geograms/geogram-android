package offgrid.geogram.util;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;

public class DateUtils {

        /**
         * Converts a timestamp from System.currentTimeMillis() to a formatted date string.
         *
         * @param timestampMillis The timestamp in milliseconds.
         * @return The formatted date string in "YYYY-MM-DD HH:MM" format.
         */
        public static String formatTimestamp(long timestampMillis) {
            // Create a Date object from the timestamp
            Date date = new Date(timestampMillis);

            // Define the desired date format
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

            // Format the date and return it as a string
            return formatter.format(date);
        }


    public static String convertTimestampForChatMessage(long timestamp) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .withZone(ZoneId.systemDefault());
        return formatter.format(Instant.ofEpochMilli(timestamp));
    }

    /**
     * Convert the last seen timestamp into a human-readable format.
     */
    public static String getHumanReadableTime(long lastSeenTimestamp) {
        long currentTime = System.currentTimeMillis();
        long elapsedMillis = currentTime - lastSeenTimestamp;

        long seconds = elapsedMillis / 1000;
        if (seconds == 0) {
            return "Reachable now"; // Do not display 0 seconds ago or "Last Seen:" text
        }
        if (seconds < 60) {
            return seconds + (seconds == 1 ? " second ago" : " seconds ago");
        }

        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + (minutes == 1 ? " minute ago" : " minutes ago");
        }

        long hours = minutes / 60;
        if (hours < 24) {
            return hours + (hours == 1 ? " hour ago" : " hours ago");
        }

        long days = hours / 24;
        if (days < 7) {
            return days + (days == 1 ? " day ago" : " days ago");
        }

        long weeks = days / 7;
        if (weeks < 4) {
            return weeks + (weeks == 1 ? " week ago" : " weeks ago");
        }

        long months = days / 30; // Approximation: 30 days per month
        if (months < 6) {
            return months + (months == 1 ? " month ago" : " months ago");
        }

        // More than 6 months: Display full date
        SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return dateFormatter.format(new Date(lastSeenTimestamp));
    }

    }
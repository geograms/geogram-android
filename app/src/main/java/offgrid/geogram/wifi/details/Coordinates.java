package offgrid.geogram.wifi.details;

import android.content.Context;

import androidx.annotation.NonNull;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import offgrid.geogram.util.CoordinateUtils;
import offgrid.geogram.core.Log;

public class Coordinates {

    private static final String TAG = "Coordinates";

    private final String latitude;
    private final String longitude;
    private final String altitude;
    private final String timestamp;

    /**
     * Constructor for manually creating EventCoordinates with given values.
     *
     * @param latitude  the latitude value
     * @param longitude the longitude value
     * @param altitude  the altitude value
     */
    public Coordinates(String latitude, String longitude, String altitude) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
        this.timestamp = getCurrentTimestamp();
        Log.i(TAG, "EventCoordinates manually created: " + this);
    }

    /**
     * Factory method for creating EventCoordinates automatically using device location.
     *
     * @param context the application context
     * @return an EventCoordinates object with location data, or null if location is unavailable
     */
    public static Coordinates createFromContext(Context context) {
        String[] coordinates = CoordinateUtils.getCurrentCoordinates(context);
        if (coordinates != null) {
            Log.i(TAG, "EventCoordinates created from device location.");
            return new Coordinates(coordinates[0], coordinates[1], coordinates[2]);
        } else {
            Log.e(TAG, "Failed to create EventCoordinates: Location data unavailable.");
            return null;
        }
    }

    /**
     * Returns the current timestamp in a human-readable format.
     *
     * @return the current timestamp as a string
     */
    private String getCurrentTimestamp() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return dateFormat.format(new Date());
    }

    public String getLatitude() {
        return latitude;
    }

    public String getLongitude() {
        return longitude;
    }

    public String getAltitude() {
        return altitude;
    }

    public String getTimestamp() {
        return timestamp;
    }

    @NonNull
    @Override
    public String toString() {
        return "EventCoordinates{" +
                "latitude='" + latitude + '\'' +
                ", longitude='" + longitude + '\'' +
                ", altitude='" + altitude + '\'' +
                ", timestamp='" + timestamp + '\'' +
                '}';
    }
}


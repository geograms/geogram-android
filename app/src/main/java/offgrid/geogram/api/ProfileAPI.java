package offgrid.geogram.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import offgrid.geogram.contacts.ContactProfile;
import offgrid.geogram.core.Log;

/**
 * API client for querying profile information from the Geogram server.
 *
 * Endpoints:
 * - GET /profile/{CALLSIGN} -> returns profile.json
 */
public class ProfileAPI {

    private static final String TAG = "ProfileAPI";
    private static final String BASE_URL = "https://api.geogram.radio";
    private static final int TIMEOUT_MS = 10000; // 10 seconds

    /**
     * Fetch profile for a callsign from the server.
     *
     * @param callsign The callsign to query
     * @return ContactProfile if found, null otherwise
     */
    public static ContactProfile fetchProfile(String callsign) {
        if (callsign == null || callsign.trim().isEmpty()) {
            Log.e(TAG, "Invalid callsign");
            return null;
        }

        String normalizedCallsign = callsign.trim().toUpperCase();
        String url = BASE_URL + "/profile/" + normalizedCallsign;

        Log.d(TAG, "Fetching profile for " + normalizedCallsign + " from " + url);

        HttpURLConnection connection = null;
        try {
            URL requestUrl = new URL(url);
            connection = (HttpURLConnection) requestUrl.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setRequestProperty("Accept", "application/json");

            int responseCode = connection.getResponseCode();
            Log.d(TAG, "Response code: " + responseCode);

            if (responseCode == HttpURLConnection.HTTP_OK) {
                // Read response
                StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }

                String jsonResponse = response.toString();
                Log.d(TAG, "Received profile JSON for " + normalizedCallsign);

                // Parse JSON to ContactProfile
                ContactProfile profile = ContactProfile.fromJson(jsonResponse);

                if (profile != null) {
                    Log.d(TAG, "Successfully parsed profile for " + normalizedCallsign);
                    return profile;
                } else {
                    Log.e(TAG, "Failed to parse profile JSON");
                    return null;
                }

            } else if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                Log.d(TAG, "Profile not found for " + normalizedCallsign);
                return null;
            } else {
                Log.e(TAG, "HTTP error " + responseCode + " fetching profile");
                return null;
            }

        } catch (Exception e) {
            Log.e(TAG, "Error fetching profile for " + normalizedCallsign + ": " + e.getMessage());
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Check if a profile exists on the server.
     *
     * @param callsign The callsign to check
     * @return true if profile exists, false otherwise
     */
    public static boolean profileExists(String callsign) {
        return fetchProfile(callsign) != null;
    }
}

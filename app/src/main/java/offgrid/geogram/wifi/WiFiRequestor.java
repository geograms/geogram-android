package offgrid.geogram.wifi;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import offgrid.geogram.core.Log;

/**
 * Singleton class for handling web requests over Wi-Fi.
 */
public class WiFiRequestor {

    private static final String TAG = "WiFiRequestor";

//    private static WiFiRequestor instance;
//
//    private final Context context;
//
//    private WiFiRequestor(Context context) {
//        if(context == null){
//            this.context = null;
//        }else{
//            this.context = context.getApplicationContext();
//        }
//    }
//
//    /**
//     * Returns the singleton instance of WiFiRequestor.
//     *
//     * @param context the application context
//     * @return the singleton instance
//     */
//    public static synchronized WiFiRequestor getInstance(Context context) {
//        if (instance == null) {
//            instance = new WiFiRequestor(context);
//        }
//        return instance;
//    }

    /**
     * Performs a GET request to the given URL and returns the response as a string.
     *
     * @param urlString the URL to connect to
     * @return the response body as a string, or null if the request fails
     */
//    public String getPage(String urlString) {
//        HttpURLConnection connection = null;
//        BufferedReader reader = null;
//
//        try {
//            // Create a URL object from the string
//            URL url = new URL(urlString);
//
//            // Open the connection
//            connection = (HttpURLConnection) url.openConnection();
//            connection.setRequestMethod("GET");
//            connection.setConnectTimeout(5000); // Set timeout for connection
//            connection.setReadTimeout(5000);    // Set timeout for reading data
//
//            // Log the request
//            Log.i(TAG, "Connecting to URL: " + urlString);
//
//            // Check the response code
//            int responseCode = connection.getResponseCode();
//            if (responseCode == HttpURLConnection.HTTP_OK) { // HTTP 200
//                // Read the response
//                reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
//                StringBuilder response = new StringBuilder();
//                String line;
//                while ((line = reader.readLine()) != null) {
//                    response.append(line);
//                }
//                String text = response.toString();
//                Log.i(TAG, "Received response: " + text);
//                // Return the response as a string
//                return response.toString();
//            } else {
//                Log.e(TAG, "Failed to fetch URL. Response Code: " + responseCode);
//                return null;
//            }
//
//        } catch (Exception e) {
//            Log.e(TAG, "Error while fetching URL: " + e.getMessage());
//            return null;
//        } finally {
//            // Close resources
//            if (reader != null) {
//                try {
//                    reader.close();
//                } catch (Exception ignored) {
//                }
//            }
//            if (connection != null) {
//                connection.disconnect();
//            }
//        }
//    }

    /**
     * Sends a POST request with a JSON body to the specified URL.
     *
     * @param urlString the URL to send the request to
     * @param jsonBody  the JSON string to send as the request body
     * @return the response body as a string, or null if the request fails
     */
    public static String postJson(String urlString, String jsonBody) {
        HttpURLConnection connection = null;
        BufferedReader reader = null;

        try {
            // Create a URL object from the string
            URL url = new URL(urlString);

            // Open the connection
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            connection.setConnectTimeout(5000); // Set timeout for connection
            connection.setReadTimeout(5000);    // Set timeout for reading data

            // Write the JSON body to the request
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonBody.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            // Log the request
            Log.i(TAG, "POSTing to URL: " + urlString + ", Body: " + jsonBody);

            // Check the response code
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) { // HTTP 200
                // Read the response
                reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                String text = response.toString();
                Log.i(TAG, "Received response: " + text);
                // Return the response as a string
                return text;
            } else {
                Log.e(TAG, "Failed to send POST request. Response Code: " + responseCode);
                return null;
            }

        } catch (Exception e) {
            Log.e(TAG, "Error while sending POST request: " + e.getMessage());
            return null;
        } finally {
            // Close resources
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {
                }
            }
            if (connection != null) {
                try {
                    connection.disconnect();
                }catch (Exception e){
                    Log.e(TAG, "Error while disconnecting: " + e.getMessage());
                }
            }
        }
    }
}

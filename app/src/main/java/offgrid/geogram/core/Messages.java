package offgrid.geogram.core;

import android.content.Context;
import android.widget.Toast;

public class Messages {

    private static final String TAG = "offgrid";

    public static void log(String message) {
        Log.d(TAG, message);
    }

    public static void log(String TAG, String message) {
        Log.d(TAG, message);
    }

    public static void message(Context context, String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        Log.d(TAG, message);
    }

}

package offgrid.geogram;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

/**
 * First-run permissions introduction screen.
 *
 * Displays a user-friendly explanation of required permissions before
 * requesting them from the Android system. Gives users the option to
 * accept and continue or decline and exit the app.
 *
 * This activity is shown only once on the first app launch. Preference
 * tracking is managed via SharedPreferences.
 *
 * @see MainActivity MainActivity.java:90-94 - First-run check and launch logic
 * @see offgrid.geogram.core.PermissionsHelper PermissionsHelper.java - Actual permission requests
 */
public class PermissionsIntroActivity extends AppCompatActivity {

    private static final String TAG = "PermissionsIntroActivity";
    private static final String PREFS_NAME = "GeogramPrefs";
    private static final String KEY_FIRST_RUN = "isFirstRun";

    // Documentation URL in the central repository
    private static final String PERMISSIONS_DOCS_URL =
        "https://github.com/geograms/central/blob/main/docs/privacy/permissions-explained.md";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Apply dark mode theme
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);

        // If not first run, skip intro and go directly to MainActivity
        if (!isFirstRun(this)) {
            proceedToMainActivity();
            return;
        }

        setContentView(R.layout.activity_permissions_intro);

        Button acceptButton = findViewById(R.id.acceptButton);
        Button declineButton = findViewById(R.id.declineButton);
        TextView learnMoreLink = findViewById(R.id.learnMoreLink);

        // Accept button - mark first run complete and proceed to main activity
        acceptButton.setOnClickListener(v -> {
            markFirstRunComplete();
            proceedToMainActivity();
        });

        // Decline button - exit the app
        declineButton.setOnClickListener(v -> {
            Toast.makeText(this,
                "Permissions are required for Geogram to function. Exiting app.",
                Toast.LENGTH_LONG).show();
            finish();
        });

        // Learn more link - open documentation in browser
        learnMoreLink.setOnClickListener(v -> {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(PERMISSIONS_DOCS_URL));
            startActivity(browserIntent);
        });
    }

    /**
     * Mark that the first run intro has been completed.
     * Subsequent app launches will skip this activity.
     */
    private void markFirstRunComplete() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(KEY_FIRST_RUN, false);
        editor.apply();
    }

    /**
     * Check if this is the first run of the app.
     * @param context Activity context
     * @return true if first run, false otherwise
     */
    public static boolean isFirstRun(android.content.Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return prefs.getBoolean(KEY_FIRST_RUN, true);
    }

    /**
     * Proceed to MainActivity where actual permission requests occur.
     */
    private void proceedToMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}

package offgrid.geogram.fragments;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import offgrid.geogram.R;
import offgrid.geogram.core.Log;

public class LogTabFragment extends Fragment {

    private TextView logWindow;
    private ScrollView logScrollView;
    private EditText logFilter;
    private boolean isPaused = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.tab_log, container, false);

        logWindow = view.findViewById(R.id.lv_log);
        logScrollView = view.findViewById(R.id.log_scroll_view);
        logFilter = view.findViewById(R.id.log_filter);

        ImageButton btnPauseLog = view.findViewById(R.id.btn_pause_log);
        ImageButton btnClearLog = view.findViewById(R.id.btn_clear_log);
        ImageButton btnCopyLog = view.findViewById(R.id.btn_copy_log);

        // Pause/Resume Log Button
        btnPauseLog.setOnClickListener(v -> {
            isPaused = !isPaused;
            btnPauseLog.setImageResource(isPaused ? R.drawable.ic_autorenew : R.drawable.ic_pause);
            Toast.makeText(getContext(), isPaused ? "Log paused" : "Log resumed", Toast.LENGTH_SHORT).show();
        });

        // Clear Log Button
        btnClearLog.setOnClickListener(v -> {
            Log.clear(); // Clear the log messages from Log class
            logWindow.setText(""); // Clear the log window
            Toast.makeText(getContext(), "Log cleared", Toast.LENGTH_SHORT).show();
        });

        // Copy Log Button
        btnCopyLog.setOnClickListener(v -> {
            String logContent = logWindow.getText().toString();
            if (!logContent.isEmpty()) {
                ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Log Messages", logContent);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(getContext(), "Log copied to clipboard", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), "Log is empty", Toast.LENGTH_SHORT).show();
            }
        });

        // Log Filter
        logFilter.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // No-op
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                logUpdateAllMessages(); // Update the log based on the filter
                logFilter.requestFocus();
            }

            @Override
            public void afterTextChanged(Editable s) {
                // No-op
            }
        });

        // Show all the messages
        logUpdateAllMessages();

        return view;
    }

    /**
     * Updates all log messages in the log window.
     */
    public void logUpdateAllMessages() {
        if (isPaused || logWindow == null) {
            return;
        }

        logScrollView.post(() -> {
            StringBuilder text = new StringBuilder();
            String filter = logFilter.getText().toString().toLowerCase();

            // Filter and append log messages
            for (String message : Log.logMessages) {
                if (filter.isEmpty() || message.toLowerCase().contains(filter)) {
                    text.append(message).append("\n");
                }
            }

            // Prevent auto-scrolling issues by resetting movement method
            logWindow.setMovementMethod(null);
            logWindow.setText(text.toString());

            // Always force scrolling to the bottom
            logScrollView.postDelayed(() -> logScrollView.fullScroll(View.FOCUS_DOWN), 50);
        });
    }



}
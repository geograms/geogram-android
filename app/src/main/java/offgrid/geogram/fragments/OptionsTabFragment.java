package offgrid.geogram.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import offgrid.geogram.R;
import offgrid.geogram.settings.SettingsLoader;

public class OptionsTabFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.tab_log_options, container, false);

        // Reset settings button
        Button resetButton = view.findViewById(R.id.btn_reset_settings);
        resetButton.setOnClickListener(v -> {
            SettingsLoader.deleteSettings(requireContext());
            Toast.makeText(getContext(), "Settings reset to defaults.", Toast.LENGTH_SHORT).show();
        });

        // Shutdown app button
        Button shutdownButton = view.findViewById(R.id.btn_shutdown_app);
        shutdownButton.setOnClickListener(v -> {
            requireActivity().finish();
            System.exit(0);
        });

        return view;
    }
}

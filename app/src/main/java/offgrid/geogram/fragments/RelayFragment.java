package offgrid.geogram.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import offgrid.geogram.MainActivity;
import offgrid.geogram.R;

public class RelayFragment extends Fragment {

    private Switch switchEnable;
    private SeekBar seekBarDiskSpace;
    private TextView textDiskSpaceValue;
    private Switch switchAutoAccept;
    private Spinner spinnerMessageTypes;

    // Logarithmic steps for disk space: 100MB to 10GB
    private static final int[] DISK_SPACE_VALUES_MB = {
        100, 150, 200, 300, 500, 750, 1024,  // 100MB to 1GB
        1536, 2048, 3072, 4096, 5120, 6144, 7168, 8192, 9216, 10240  // 1.5GB to 10GB
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_relay, container, false);

        // Back button functionality
        ImageButton btnBack = view.findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().getSupportFragmentManager().popBackStack();
            }
        });

        // Initialize UI components
        switchEnable = view.findViewById(R.id.switch_enable_relay);
        seekBarDiskSpace = view.findViewById(R.id.seekbar_disk_space);
        textDiskSpaceValue = view.findViewById(R.id.text_disk_space_value);
        switchAutoAccept = view.findViewById(R.id.switch_auto_accept);
        spinnerMessageTypes = view.findViewById(R.id.spinner_message_types);

        // Setup disk space slider
        seekBarDiskSpace.setMax(DISK_SPACE_VALUES_MB.length - 1);
        seekBarDiskSpace.setProgress(6); // Default to 1GB (index 6)
        updateDiskSpaceText(6);

        seekBarDiskSpace.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateDiskSpaceText(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Setup message types spinner
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                requireContext(),
                R.array.relay_message_types,
                android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMessageTypes.setAdapter(adapter);

        // TODO: Add logic to load/save settings

        return view;
    }

    private void updateDiskSpaceText(int progress) {
        int valueMB = DISK_SPACE_VALUES_MB[progress];
        String displayText;

        if (valueMB >= 1024) {
            float valueGB = valueMB / 1024f;
            displayText = String.format("%.1f GB", valueGB);
        } else {
            displayText = valueMB + " MB";
        }

        textDiskSpaceValue.setText(displayText);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Hide top action bar for detail screens
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setTopActionBarVisible(false);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // Show top action bar when leaving
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setTopActionBarVisible(true);
        }
    }
}

package offgrid.geogram.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class RelayDeviceAdapter extends RecyclerView.Adapter<RelayDeviceAdapter.DeviceViewHolder> {

    private List<String> connectedDevices;

    public RelayDeviceAdapter(List<String> connectedDevices) {
        this.connectedDevices = connectedDevices;
    }

    @NonNull
    @Override
    public DeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(android.R.layout.simple_list_item_1, parent, false);
        return new DeviceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DeviceViewHolder holder, int position) {
        String deviceId = connectedDevices.get(position);
        holder.deviceName.setText(deviceId);
        holder.deviceName.setTextColor(0xFFFFFFFF); // White text
    }

    @Override
    public int getItemCount() {
        return connectedDevices.size();
    }

    public void updateDevices(List<String> newDevices) {
        this.connectedDevices = newDevices;
        notifyDataSetChanged();
    }

    static class DeviceViewHolder extends RecyclerView.ViewHolder {
        TextView deviceName;

        DeviceViewHolder(View itemView) {
            super(itemView);
            deviceName = itemView.findViewById(android.R.id.text1);
        }
    }
}
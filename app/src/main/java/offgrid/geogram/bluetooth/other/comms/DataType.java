package offgrid.geogram.bluetooth.other.comms;

import androidx.annotation.NonNull;

public enum DataType {
            NONE,
            X, // generic usage
            G, // get user from device
            C,  // chat with device
            B;  // broadcast to device

    public static DataType fromString(String value) {
        for (DataType type : values()) {
            if (type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        // it wasn't found, return generic
        return X;
    }

    @NonNull
    @Override
    public String toString() {
        return name();
    }
}

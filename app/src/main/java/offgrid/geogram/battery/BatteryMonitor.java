package offgrid.geogram.battery;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import offgrid.geogram.core.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Battery monitor that tracks battery level over time and estimates remaining battery life.
 *
 * Takes measurements every 2 minutes and keeps track of the last 1000 measurements.
 * Uses historical data to calculate battery drain rate and estimate time remaining.
 */
public class BatteryMonitor {
    private static final String TAG = "BatteryMonitor";
    private static final int MAX_MEASUREMENTS = 1000;
    private static final long MEASUREMENT_INTERVAL_MS = 2 * 60 * 1000; // 2 minutes

    private static BatteryMonitor instance;

    private final Context context;
    private final List<BatteryMeasurement> measurements;
    private Thread monitorThread;
    private volatile boolean isRunning = false;
    private BatteryUpdateListener listener;

    /**
     * Battery measurement data point
     */
    public static class BatteryMeasurement implements Serializable {
        private static final long serialVersionUID = 1L;

        public final long timestamp;
        public final int level;          // Battery level percentage (0-100)
        public final int temperature;    // Temperature in tenths of degrees Celsius
        public final int voltage;        // Voltage in millivolts
        public final boolean isCharging; // Whether device is charging

        public BatteryMeasurement(long timestamp, int level, int temperature, int voltage, boolean isCharging) {
            this.timestamp = timestamp;
            this.level = level;
            this.temperature = temperature;
            this.voltage = voltage;
            this.isCharging = isCharging;
        }
    }

    /**
     * Listener for battery updates
     */
    public interface BatteryUpdateListener {
        void onBatteryUpdate(int currentLevel, String estimatedTimeRemaining);
    }

    private BatteryMonitor(Context context) {
        this.context = context.getApplicationContext();
        this.measurements = new ArrayList<>();
        loadMeasurements();
    }

    public static synchronized BatteryMonitor getInstance(Context context) {
        if (instance == null) {
            instance = new BatteryMonitor(context);
        }
        return instance;
    }

    /**
     * Set listener for battery updates
     */
    public void setListener(BatteryUpdateListener listener) {
        this.listener = listener;
    }

    /**
     * Start monitoring battery
     */
    public void start() {
        if (isRunning) {
            Log.w(TAG, "Battery monitor already running");
            return;
        }

        Log.i(TAG, "Starting battery monitor");
        isRunning = true;

        // Take initial measurement
        takeMeasurement();

        // Start monitoring thread
        monitorThread = new Thread(() -> {
            while (isRunning) {
                try {
                    Thread.sleep(MEASUREMENT_INTERVAL_MS);
                    if (isRunning) {
                        takeMeasurement();
                        notifyListener();
                    }
                } catch (InterruptedException e) {
                    Log.d(TAG, "Battery monitor thread interrupted");
                    break;
                }
            }
        });
        monitorThread.setName("BatteryMonitor");
        monitorThread.start();

        // Notify listener immediately with initial data
        notifyListener();
    }

    /**
     * Stop monitoring battery
     */
    public void stop() {
        Log.i(TAG, "Stopping battery monitor");
        isRunning = false;
        if (monitorThread != null) {
            monitorThread.interrupt();
            monitorThread = null;
        }
    }

    /**
     * Take a battery measurement
     */
    private void takeMeasurement() {
        try {
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = context.registerReceiver(null, ifilter);

            if (batteryStatus != null) {
                // Get battery level
                int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                int batteryPct = (int) ((level / (float) scale) * 100);

                // Get temperature (in tenths of degrees Celsius)
                int temperature = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);

                // Get voltage (in millivolts)
                int voltage = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);

                // Check if charging
                int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                                   status == BatteryManager.BATTERY_STATUS_FULL;

                // Create measurement
                BatteryMeasurement measurement = new BatteryMeasurement(
                    System.currentTimeMillis(),
                    batteryPct,
                    temperature,
                    voltage,
                    isCharging
                );

                // Add to list
                synchronized (measurements) {
                    measurements.add(measurement);

                    // Keep only last MAX_MEASUREMENTS
                    while (measurements.size() > MAX_MEASUREMENTS) {
                        measurements.remove(0);
                    }
                }

                // Save to disk
                saveMeasurements();

                Log.d(TAG, "Battery measurement: " + batteryPct + "%, " +
                    (temperature / 10.0) + "°C, " + voltage + "mV, charging=" + isCharging);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error taking battery measurement: " + e.getMessage());
        }
    }

    /**
     * Get current battery level
     */
    public int getCurrentBatteryLevel() {
        synchronized (measurements) {
            if (measurements.isEmpty()) {
                return -1;
            }
            return measurements.get(measurements.size() - 1).level;
        }
    }

    /**
     * Check if device is currently charging
     */
    public boolean isCharging() {
        synchronized (measurements) {
            if (measurements.isEmpty()) {
                return false;
            }
            return measurements.get(measurements.size() - 1).isCharging;
        }
    }

    /**
     * Calculate battery drain rate in percentage per hour
     * Returns negative value if battery is charging/increasing
     */
    private double calculateDrainRate() {
        synchronized (measurements) {
            if (measurements.size() < 2) {
                return 0.0;
            }

            // Filter out charging measurements for drain calculation
            List<BatteryMeasurement> dischargingMeasurements = new ArrayList<>();
            for (BatteryMeasurement m : measurements) {
                if (!m.isCharging) {
                    dischargingMeasurements.add(m);
                }
            }

            if (dischargingMeasurements.size() < 2) {
                // Not enough discharging data
                return 0.0;
            }

            // Use linear regression on recent discharging measurements
            // For simplicity, use first and last discharging measurement
            BatteryMeasurement first = dischargingMeasurements.get(0);
            BatteryMeasurement last = dischargingMeasurements.get(dischargingMeasurements.size() - 1);

            long timeDiffMs = last.timestamp - first.timestamp;
            if (timeDiffMs <= 0) {
                return 0.0;
            }

            int levelDiff = first.level - last.level; // Positive means draining

            // Convert to percentage per hour
            double hoursElapsed = timeDiffMs / (1000.0 * 60.0 * 60.0);
            return levelDiff / hoursElapsed;
        }
    }

    /**
     * Estimate remaining battery life in human readable format
     */
    public String getEstimatedTimeRemaining() {
        int currentLevel = getCurrentBatteryLevel();

        if (currentLevel < 0) {
            return "Unknown";
        }

        // If charging, show charging status
        if (isCharging()) {
            return "Charging";
        }

        double drainRate = calculateDrainRate();

        if (drainRate <= 0) {
            // Battery not draining or insufficient data
            synchronized (measurements) {
                if (measurements.size() < 10) {
                    return "Calculating...";
                }
            }
            return ">24h";
        }

        // Calculate hours remaining
        double hoursRemaining = currentLevel / drainRate;

        // Convert to human readable format
        return formatDuration(hoursRemaining);
    }

    /**
     * Format duration in hours to human readable string
     */
    private String formatDuration(double hours) {
        if (hours < 0) {
            return "Unknown";
        }

        if (hours > 24) {
            int days = (int) (hours / 24);
            int remainingHours = (int) (hours % 24);
            if (remainingHours > 0) {
                return String.format("%dd %dh", days, remainingHours);
            }
            return String.format("%dd", days);
        }

        if (hours >= 1) {
            int h = (int) hours;
            int m = (int) ((hours - h) * 60);
            if (m > 0) {
                return String.format("%dh %dm", h, m);
            }
            return String.format("%dh", h);
        }

        int minutes = (int) (hours * 60);
        if (minutes < 1) {
            return "<1m";
        }
        return String.format("%dm", minutes);
    }

    /**
     * Notify listener of battery update
     */
    private void notifyListener() {
        if (listener != null) {
            int level = getCurrentBatteryLevel();
            String timeRemaining = getEstimatedTimeRemaining();
            listener.onBatteryUpdate(level, timeRemaining);
        }
    }

    /**
     * Get number of measurements collected
     */
    public int getMeasurementCount() {
        synchronized (measurements) {
            return measurements.size();
        }
    }

    /**
     * Save measurements to disk
     */
    private void saveMeasurements() {
        try {
            File file = new File(context.getFilesDir(), "battery_measurements.dat");
            synchronized (measurements) {
                try (FileOutputStream fos = new FileOutputStream(file);
                     ObjectOutputStream oos = new ObjectOutputStream(fos)) {
                    oos.writeObject(new ArrayList<>(measurements));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error saving battery measurements: " + e.getMessage());
        }
    }

    /**
     * Load measurements from disk
     */
    @SuppressWarnings("unchecked")
    private void loadMeasurements() {
        try {
            File file = new File(context.getFilesDir(), "battery_measurements.dat");
            if (file.exists()) {
                try (FileInputStream fis = new FileInputStream(file);
                     ObjectInputStream ois = new ObjectInputStream(fis)) {
                    List<BatteryMeasurement> loaded = (List<BatteryMeasurement>) ois.readObject();
                    synchronized (measurements) {
                        measurements.clear();
                        measurements.addAll(loaded);
                    }
                    Log.i(TAG, "Loaded " + measurements.size() + " battery measurements from disk");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading battery measurements: " + e.getMessage());
        }
    }
}

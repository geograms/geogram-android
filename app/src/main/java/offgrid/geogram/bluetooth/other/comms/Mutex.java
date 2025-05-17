package offgrid.geogram.bluetooth.other.comms;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Rudimentary semaphore to reduce collisions
 * between read and write operations.
 */
public class Mutex {
    private boolean isLocked = false;
    private final Object lock = new Object();
    private Timer timer = null;

    // Timeout value in milliseconds (default is 1 minute)
    private static final int TIMEOUT_MS = 20_000;

    // Singleton instance
    private static final Mutex INSTANCE = new Mutex();

    /**
     * Private constructor to prevent instantiation.
     */
    private Mutex() {
    }

    /**
     * Returns the singleton instance of the Mutex.
     *
     * @return The singleton Mutex instance.
     */
    public static Mutex getInstance() {
        return INSTANCE;
    }

    /**
     * Acquires the lock. Blocks until the lock is available.
     */
    public void lock() {
        synchronized (lock) {
            while (isLocked) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // Restore interrupted status
                }
            }
            isLocked = true;

            // Start the timeout timer
            startTimeout();
        }
    }

    /**
     * Releases the lock and notifies waiting threads.
     */
    public void unlock() {
        synchronized (lock) {
            if (isLocked) {
                isLocked = false;

                // Cancel the timeout timer
                if (timer != null) {
                    timer.cancel();
                    timer = null;
                }

                lock.notify();
            }
        }
    }

    /**
     * Checks if the lock is currently held.
     *
     * @return True if the lock is held, false otherwise.
     */
    public boolean isLocked() {
        synchronized (lock) {
            return isLocked;
        }
    }

    /**
     * Starts a timeout to automatically release the lock after the specified timeout.
     */
    private void startTimeout() {
        if (timer != null) {
            timer.cancel(); // Cancel any existing timer
        }

        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                synchronized (lock) {
                    if (isLocked) {
                        isLocked = false;
                        lock.notify();
                    }
                }
            }
        }, TIMEOUT_MS); // Use the configurable timeout value
    }

    /**
     * Makes the current thread wait until the lock is unlocked.
     */
    public void waitUntilUnlocked() {
        //return;
        synchronized (lock) {
            while (isLocked) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // Restore interrupted status
                }
            }
        }
    }
}

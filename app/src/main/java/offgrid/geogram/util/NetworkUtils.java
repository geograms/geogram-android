package offgrid.geogram.util;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

public class NetworkUtils {

    /**
     * Get the device's IP address (IPv4).
     * Prioritizes Wi-Fi connection over mobile data.
     *
     * @return IP address as a string, or "Not available" if not found
     */
    public static String getIPAddress() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());

            // First pass: Look for Wi-Fi interface (wlan0)
            for (NetworkInterface intf : interfaces) {
                String name = intf.getName();
                if (name.startsWith("wlan")) {
                    String ip = getIPFromInterface(intf);
                    if (ip != null) {
                        return ip;
                    }
                }
            }

            // Second pass: Look for any non-loopback interface
            for (NetworkInterface intf : interfaces) {
                String name = intf.getName();
                // Skip loopback and virtual interfaces
                if (!name.equals("lo") && !name.startsWith("dummy")) {
                    String ip = getIPFromInterface(intf);
                    if (ip != null) {
                        return ip;
                    }
                }
            }

        } catch (Exception e) {
            // Return error message
            return "Error: " + e.getMessage();
        }

        return "Not available";
    }

    /**
     * Extract IPv4 address from a network interface.
     *
     * @param intf Network interface to check
     * @return IPv4 address string, or null if not found
     */
    private static String getIPFromInterface(NetworkInterface intf) {
        try {
            List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
            for (InetAddress addr : addrs) {
                if (!addr.isLoopbackAddress()) {
                    String sAddr = addr.getHostAddress();

                    // Check if it's IPv4 (no colon)
                    boolean isIPv4 = sAddr.indexOf(':') < 0;

                    if (isIPv4) {
                        return sAddr;
                    }
                }
            }
        } catch (Exception e) {
            // Ignore and try next interface
        }
        return null;
    }

    /**
     * Get the HTTP API server URL.
     *
     * @param port The server port
     * @return Full URL as a string
     */
    public static String getServerUrl(int port) {
        String ip = getIPAddress();
        if (ip.equals("Not available") || ip.startsWith("Error")) {
            return ip;
        }
        return "http://" + ip + ":" + port;
    }
}

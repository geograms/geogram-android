package offgrid.geogram.wifi;

import static offgrid.geogram.core.Central.server;

import android.content.Context;

import java.io.IOException;
import java.net.Socket;

import offgrid.geogram.bluetooth.eddystone.EddystoneNamespaceGenerator;
import offgrid.geogram.core.Log;
import offgrid.geogram.database.HelloDatabase;
import offgrid.geogram.devices.DeviceReachable;
import offgrid.geogram.util.JsonUtils;
import offgrid.geogram.wifi.details.WiFiNetwork;
import offgrid.geogram.wifi.messages.MessageHello_v1;

public class WiFiUpdates {

    private static final String TAG = "WiFiUpdates";


    /**
     * Display the information we have about this device.
     * Always try first to show cached information and then
     * show information updated from the other device over Wi-Fi
     */
    public static MessageHello_v1 sayHello(DeviceReachable deviceDiscovered, Context context) {
        // get the namespace info
        String[] data = EddystoneNamespaceGenerator.extractNamespaceDetails(
                deviceDiscovered.getNamespaceId()
        );
        // needs to have valid data inside
        if(data[0] == null && data[1] == null){
            return null;
        }

        try {

            // basic info
        String ssidHash = data[0];
        String ssidPassword = data[1];
        String deviceId = deviceDiscovered.getDeviceId();

        // get a network matching the name hash
        WiFiNetwork networkReachable =
                WiFiDatabase.getInstance(context).getReachableNetwork(ssidHash);

        if(networkReachable == null){
            Log.e(TAG, "SSID not found for hash: " + ssidHash);
            return null;
        }

            Log.i(TAG, "Target device SSID: " + networkReachable.getSSID());
            Log.i(TAG, "Target device password: " + ssidPassword);

            // Save the current Wi-Fi connection
            //WiFiDirectConnector.getInstance(context).saveCurrentConnection();


            // Connect to the Wi-Fi Direct hotspot
            boolean connected = false;

            try {
                connected = WiFiDirectConnector.getInstance(context)
                        .connectToNetwork(networkReachable.getSSID(), ssidPassword);


            }catch (Exception e){
                Log.e(TAG, "Error connecting to Wi-Fi Direct network: " + networkReachable.getSSID());
            }
            Thread.sleep(2000); // Wait for the network to be fully connected


            if (connected) {
                Log.i(TAG, "Connected to Wi-Fi Direct network: " + networkReachable.getSSID());
                //Toast.makeText(context, "Connected to Wi-Fi Direct network: " + networkReachable.getSSID(), Toast.LENGTH_LONG).show();
            } else {
                Log.e(TAG, "Failed to connect to Wi-Fi Direct network: " + networkReachable.getSSID());
                //Toast.makeText(context, "Failed to connect to Wi-Fi Direct network: " + networkReachable.getSSID(), Toast.LENGTH_LONG).show();
                return null;
            }

            // Get the IP address of this device
            String addressIP = WiFiDirectConnector.getInstance(context).getCurrentIpAddress();
            Log.i(TAG, "IP address of this device: " + addressIP);

            // Get the DHCP server IP address
            String targetIP = WiFiDirectConnector.getInstance(context).getDhcpServerIpAddress();
            Log.i(TAG, "DHCP address: " + targetIP);

            // perform a connectivity test
            Socket socket = null;
            try {
                socket = new Socket("192.168.49.1", 5050);
                Log.i(TAG, "Socket connection successful");
            } catch (IOException e) {
                Log.e(TAG, "Socket connection failed");
            } finally {
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        Log.e(TAG, "Error closing socket");
                    }
                }
            }


            // Perform necessary actions (e.g., web requests)
            Log.i(TAG, "Performing actions with the Wi-Fi Direct network...");

            // send the hello
            MessageHello_v1 hello = new MessageHello_v1();
            String text = JsonUtils.convertToJsonText(hello);
            // send the json to the local server
            String reply = null;
            try {

                // disconnect the local server
                if(server.stopServer() == false){
                    Log.e(TAG, "Error stopping local server");
                    return null;
                }

                String URL = "http://" +
                        targetIP +
                        ":5050";
                Log.i(TAG, "Sending hello to: " + URL);
                // make the actual request
                reply = WiFiRequestor
                        .postJson(URL,
                                text
                        );

                // restart the local server
                server.startServer();

            }catch (Exception e){
                Log.e(TAG, "Error sending hello: " + e.getMessage());
                return null;
            }
            // Disconnect from the Wi-Fi Direct hotspot
            Log.i(TAG, "Disconnecting from Wi-Fi Direct hotspot...");
            //Thread.sleep(2000); // Wait for the group to be fully cleaned up
            WiFiDirectConnector.getInstance(context).disconnect();
            //Thread.sleep(2000); // Wait for the group to be fully cleaned up

            // provide the received hello
            return receivedHello(reply, context);

        } catch (Exception e) {
            Log.e(TAG, "Error in network operations: " + e.getMessage());
            return null;
        }
    }

    private static MessageHello_v1 receivedHello(String reply, Context context) {
        try {
            // we expect a reply that cannot be null
            if (reply == null) {
                Log.e(TAG, "Reply to hello was null");
                return null;
            }

            // convert to the other hello
            MessageHello_v1 replyMessage = null;

            try {
                replyMessage = JsonUtils.parseJson(reply, MessageHello_v1.class);
            }catch (Exception e){
                Log.e(TAG, "Error parsing hello reply: " + e.getMessage());
                return null;
            }
            Log.i(TAG, "Received hello reply: " + replyMessage.getBioProfile().getNpub());
            // write the hello to the database
            HelloDatabase.save(replyMessage, context);
            return replyMessage;
        } catch (Exception e) {
            Log.e(TAG, "Error parsing hello reply: " + e.getMessage());
            return null;
        }
    }
}

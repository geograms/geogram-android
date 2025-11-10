package offgrid.grid.geogram.wifi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

import offgrid.geogram.core.Central;
import offgrid.geogram.server.SimpleSparkServer;
import offgrid.geogram.settings.SettingsLoader;
import offgrid.geogram.settings.SettingsUser;
import offgrid.geogram.util.JsonUtils;
import offgrid.geogram.old.wifi.WiFiRequestor;
import offgrid.geogram.old.wifi.comm.WiFiReceiver;
import offgrid.geogram.old.wifi.comm.WiFiSender;
import offgrid.geogram.old.wifi.messages.Message;
import offgrid.geogram.old.wifi.messages.MessageHello_v1;
/*
    Focus on the communication between two devices
    using Wi-Fi communication
 */

public class WiFiProtocolTest {

    @Test
    public void testHello() throws InterruptedException {
        /*
            The first command on the protocol is "Hello".
            Android A will connect to the hotspot on the Android B
            and request information about Android B.

            This information includes the public key, the device ID
            and an index of the collections inside the device.
            It also includes number of messages and other statistics
            that might be relevant for Android A to consider
         */

        SettingsUser settings = SettingsLoader.createDefaultSettings(null);
        Central.getInstance().setSettings(settings);

        WiFiReceiver receiver = new WiFiReceiver();
        WiFiSender sender = WiFiSender.getInstance(null);

        // create basic info
        String targetSSID = "TestNetwork";
        String targetIP = "192.168.49.1";

        // create a basic Hello message
        MessageHello_v1 hello = new MessageHello_v1();
        // send that message (place in the sending queue)
        sender.send(targetSSID, targetIP, hello);

        // make sure our sender receives the messages
        assertNotNull(hello);
        assertEquals(1, sender.getMessages().size());

        // wait for a bit
        Thread.sleep(1000);

        // run the local version of the receiver
        //receiver.syncLocally();
        Message reply = WiFiReceiver.processReceivedMessage(hello);
        // for this to work, timestamps are different
        assertNotEquals(hello.getTimeStamp(), reply.getTimeStamp());

        // make sure we have messages received locally
        assertEquals(0, WiFiReceiver.messagesReceived.size());
    }

    @Test
    public void testServerCommunication() throws InterruptedException {
        /*
        Run a local server on this machine and make requests
        to test the output.
         */
        SettingsUser settings = SettingsLoader.createDefaultSettings(null);
        Central.getInstance().setSettings(settings);
        Central.debugForLocalTests = true;

        // Start the web server
        Thread serverThread = new Thread(new SimpleSparkServer());
        serverThread.start();
        // wait for initialization to complete
        Thread.sleep(1000);

        // create a basic Hello message
        MessageHello_v1 hello = new MessageHello_v1();
        String targetSSID = "TestNetwork";
        String targetIP = "192.168.49.1";
        String text = JsonUtils.convertToJsonText(hello);
        assertNotNull(hello);
        System.out.println("Sending: " + text);

        // send the json to the local server
        String reply = WiFiRequestor
                //.getInstance(null)
                .postJson("http://localhost:5050", text);
        assertNotNull(reply);
        Message replyMessage = JsonUtils.parseJson(reply, Message.class);
        assertNotEquals(hello.getTimeStamp(), replyMessage.getTimeStamp());
    }



}

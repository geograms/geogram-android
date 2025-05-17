package offgrid.geogram.wifi.comm;

import android.content.Context;

import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;

import offgrid.geogram.wifi.messages.Message;

public class WiFiSender {

    CopyOnWriteArrayList<WiFiMessage> messagesToSend = new CopyOnWriteArrayList<>();


    // Singleton instance
    private static WiFiSender instance;

    // Application context
    private final Context appContext;

    // Private constructor
    private WiFiSender(Context context) {
        // Use application context to avoid leaking activity context
        if(context == null){
            this.appContext = null;
            return;
        }
        this.appContext = context.getApplicationContext();
    }

    // Public method to get the singleton instance
    public static synchronized WiFiSender getInstance(Context context) {
        if (instance == null) {
            instance = new WiFiSender(context);
        }
        return instance;
    }


    public void send(String targetSSID, String targetIP, Message message) {
        // avoid sending the same message twice
        if(messageIsAlreadyOnQueue(message)){
            return;
        }

        // create the Wi-Fi message
        WiFiMessage wifiMessage = new WiFiMessage(targetSSID, targetIP, message);
        messagesToSend.add(wifiMessage);
    }

    private boolean messageIsAlreadyOnQueue(Message message) {
        for(WiFiMessage messageExisting : messagesToSend){
            // for the moment just timestamp check
            //TODO improve duplicate checks by comparing the message content
            if(messageExisting.message.getTimeStamp() == message.getTimeStamp()){
                return true;
            }
        }
        return false;
    }

    public CopyOnWriteArrayList<WiFiMessage> getMessages() {
        return this.messagesToSend;
    }
}

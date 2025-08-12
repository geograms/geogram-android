package offgrid.geogram.ble.chat;

import offgrid.geogram.ble.BluetoothMessage;
import offgrid.geogram.old.bluetooth_old.broadcast.BroadcastMessage;

/*
    Defines a message displayed inside a chat window
 */
public class ChatMessage {

    // has the message been read by this user?
    private boolean wasRead;
    // was the message sent by the device?
    private boolean wasSent;
    // when was it received or sent?
    // was this message displayed to the end user?
    private boolean wasDisplayed;
    private boolean isWrittenByMe = false;
    private long timestamp;

    // the message text itself
    private String message;
    private String from, to;


    public boolean isWasRead() {
        return wasRead;
    }

    public void setWasRead(boolean wasRead) {
        this.wasRead = wasRead;
    }

    public boolean isWasSent() {
        return wasSent;
    }

    public void setWasSent(boolean wasSent) {
        this.wasSent = wasSent;
    }

    public boolean wasDisplayedBefore() {
        return wasDisplayed;
    }

    public void setWasDisplayed(boolean wasDisplayed) {
        this.wasDisplayed = wasDisplayed;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public boolean isWrittenByMe() {
        return isWrittenByMe;
    }

    public void setWrittenByMe(boolean writtenByMe) {
        isWrittenByMe = writtenByMe;
    }

    public static ChatMessage convert(BluetoothMessage message){
        ChatMessage output = new ChatMessage();
        output.setFrom(message.getIdFromSender());
        output.setTo(message.getIdDestination());
        output.setMessage(message.getMessage());
        output.setTimestamp(message.getTimeStamp());
        return output;
    }

    public static ChatMessage convert(BroadcastMessage message) {
        ChatMessage output = new ChatMessage();
        output.setFrom(message.getDeviceId());
        output.setMessage(message.getMessage());
        output.setTimestamp(message.getTimestamp());
        return output;
    }



}

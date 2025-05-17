package offgrid.geogram.wifi.messages.routine;

import offgrid.geogram.wifi.comm.CID;
import offgrid.geogram.wifi.messages.Message;

public class MessageAddedToQueue_v1 extends Message {
    public MessageAddedToQueue_v1() {
        super(CID.ADDED_QUEUE);
    }
}

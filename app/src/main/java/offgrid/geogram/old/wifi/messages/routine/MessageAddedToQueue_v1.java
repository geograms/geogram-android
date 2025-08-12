package offgrid.geogram.old.wifi.messages.routine;

import offgrid.geogram.old.wifi.comm.CID;
import offgrid.geogram.old.wifi.messages.Message;

public class MessageAddedToQueue_v1 extends Message {
    public MessageAddedToQueue_v1() {
        super(CID.ADDED_QUEUE);
    }
}

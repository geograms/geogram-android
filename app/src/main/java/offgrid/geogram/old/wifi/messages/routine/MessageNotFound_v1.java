package offgrid.geogram.old.wifi.messages.routine;

import offgrid.geogram.old.wifi.comm.CID;
import offgrid.geogram.old.wifi.messages.Message;

public class MessageNotFound_v1 extends Message {
    public MessageNotFound_v1() {
        super(CID.NOT_FOUND);
    }
}

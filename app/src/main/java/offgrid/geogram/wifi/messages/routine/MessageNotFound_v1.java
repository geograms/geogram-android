package offgrid.geogram.wifi.messages.routine;

import offgrid.geogram.wifi.comm.CID;
import offgrid.geogram.wifi.messages.Message;

public class MessageNotFound_v1 extends Message {
    public MessageNotFound_v1() {
        super(CID.NOT_FOUND);
    }
}

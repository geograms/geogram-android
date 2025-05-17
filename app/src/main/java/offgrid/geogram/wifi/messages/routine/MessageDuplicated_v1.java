package offgrid.geogram.wifi.messages.routine;

import offgrid.geogram.wifi.comm.CID;
import offgrid.geogram.wifi.messages.Message;

public class MessageDuplicated_v1 extends Message {
    public MessageDuplicated_v1() {
        super(CID.DUPLICATED);
    }
}

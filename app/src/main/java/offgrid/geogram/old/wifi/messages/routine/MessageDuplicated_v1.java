package offgrid.geogram.old.wifi.messages.routine;

import offgrid.geogram.old.wifi.comm.CID;
import offgrid.geogram.old.wifi.messages.Message;

public class MessageDuplicated_v1 extends Message {
    public MessageDuplicated_v1() {
        super(CID.DUPLICATED);
    }
}

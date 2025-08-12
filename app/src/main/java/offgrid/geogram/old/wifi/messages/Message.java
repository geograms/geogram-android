package offgrid.geogram.old.wifi.messages;

import offgrid.geogram.old.wifi.comm.CID;

public class Message {
    final CID cid;
    final long timeStamp;

    public CID getCid() {
        return cid;
    }

    public long getTimeStamp() {
        return timeStamp;
    }

    public Message(CID cid) {
        this.cid = cid;
        this.timeStamp = System.currentTimeMillis();
    }
}

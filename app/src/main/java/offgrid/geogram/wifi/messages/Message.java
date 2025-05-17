package offgrid.geogram.wifi.messages;

import offgrid.geogram.wifi.comm.CID;

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

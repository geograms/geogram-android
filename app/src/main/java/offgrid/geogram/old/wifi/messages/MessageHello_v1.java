package offgrid.geogram.old.wifi.messages;

import offgrid.geogram.protocol.data.BioProfile_v1;
import offgrid.geogram.old.wifi.comm.CID;

public class MessageHello_v1 extends Message{

    final BioProfile_v1 bioProfile;

    // container values
    private String version = "1";

    public BioProfile_v1 getBioProfile() {
        return bioProfile;
    }

    public String getVersion() {
        return version;
    }

    public MessageHello_v1() {
        super(CID.HELLO);
        // the the local hello from this device
        this.bioProfile = BioProfile_v1.getLocalUser();
    }

    /**
     * Gets the unique identifier for this message.
     * It requires NPUB and device ID since the same
     * user can own different devices with different
     * storage files inside.
     * @return null when either NPUB or device ID are null
     */
    public String getUID() {
        // check if we have the needed values
        if(bioProfile == null
                || bioProfile.getNpub() == null
        || bioProfile.getDeviceId() == null){
            return null;
        }
        return bioProfile.getNpub() + "_" + bioProfile.getDeviceId();
    }
}

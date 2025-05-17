package offgrid.geogram.bluetooth.other.comms;

/**
 * A list of the commands and specific keywords
 * used between our bluetooth communications
 */
public class BlueCommands {

    public final static String
            tagBio = "/bio:",                   // send the bio profile details
            oneLineCommandBio = ">BIO:",        // request to send back the biographical details
            oneLineCommandGapBroadcast = ">B:", // means a one line statement
            gapREPEAT = "REPEAT",               // please send the whole package again
            oneLineCommandPing = ">PING:",      // send the Mac Address and Device Id
            oneLineAcknowledgement = ">ACK:";   // confirm that a package was received

}

package offgrid.grid.geogram.ble;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import offgrid.geogram.ble.BluetoothMessage;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class BluetoothPackageTest {

    @Test
    public void parcelSizes() {
        // AV0:053156:ANY:KPBA | AV1:+053156@RY1A-IUZS
        // send a location package
        String messageShort = "+053156@RY1A-ISZS";
        String messageLong = "An example of a long message to break into multiple parcels.";

        BluetoothMessage msgLong = new BluetoothMessage("CR7BBQ", "ANY", messageLong, false);
        assertEquals(5, msgLong.getMessageParcels().length);

        BluetoothMessage msgShort = new BluetoothMessage("CR7BBQ", "ANY", messageShort, true);
        assertEquals(1, msgShort.getMessageParcels().length);

        System.out.println(msgShort.getMessage());

        // now let's decode the long message again
        BluetoothMessage msgLongDecoded = new BluetoothMessage();
        for(String parcel : msgLong.getMessageParcels()){
            msgLongDecoded.addMessageParcel(parcel);
        }
        String resultLong = msgLongDecoded.getMessage();
        assertEquals(messageLong, resultLong);

        // now let's decode the short message again
        BluetoothMessage msgShortDecoded = new BluetoothMessage();
        for(String parcel : msgShort.getMessageParcels()){
            msgShortDecoded.addMessageParcel(parcel);
        }
        String resultShort = msgShortDecoded.getMessage();
        assertEquals(messageShort, resultShort);

    }
}
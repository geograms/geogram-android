package offgrid.grid.geogram;

import org.junit.Test;
import static org.junit.Assert.*;

import offgrid.geogram.old.bluetooth_old.other.comms.BluePackage;
import offgrid.geogram.old.bluetooth_old.other.comms.DataType;

public class BluePackageTestOld {

    @Test
    public void testRandomId() {
        BluePackage sender = BluePackage.createSender("HelloWorldThisIsATest");
        String randomId = sender.generateRandomId();
        assertNotNull(randomId);
        assertEquals(2, randomId.length());
    }


    @Test
    public void testCreateSender() {
        BluePackage sender = BluePackage.createSender("HelloWorldThisIsATest");

        assertNotNull(sender);
        assertEquals("HelloWorldThisIsATest", sender.getData());
        assertEquals(10, sender.getTextLengthPerParcel());
        assertEquals(3, sender.getMessageParcelsTotal()); // Total parcels = ceil(20 / 15)
        assertTrue(sender.isTransferring());
    }

    @Test
    public void testReceiverReconstruction1() {

        BluePackage sender = BluePackage.createSender("HelloWorldThisIsATestThatGoesAroundAndShouldBreakToMultipleMessagesOK?");
        // get the first parcel, which should be a header
        String parcel = sender.getNextParcel();
        assertNotNull(parcel);
        String[] header = parcel.split(":");
        // unique and random id
        String id = header[0];
        // total number of parcels inside the package
        String parcelNumber = header[1];
        int parcelTotal = Integer.parseInt(parcelNumber);
        assertEquals(7, parcelTotal);
        // checksum of the data inside
        String checksum = header[2];
        assertEquals(4, checksum.length());
        // what kind of data is being shipped?
        String dataType = header[3];
        DataType dataTypeEnum = DataType.valueOf(dataType);
        assertEquals(DataType.X, dataTypeEnum);

        // second part
        String parcel1 = sender.getNextParcel();
        String parcel2 = sender.getNextParcel();

        String headerToReceive = "ab:003:JSDA:B";
        BluePackage receiver = BluePackage.createReceiver(headerToReceive);

        receiver.receiveParcel("ab000:DataPart1");
        receiver.receiveParcel("ab002:DataPart3");
        receiver.receiveParcel("ab001:DataPart2");

        assertTrue(receiver.allParcelsReceivedAndValid());
        String dataReceived = receiver.getData();
        assertEquals("DataPart1DataPart2DataPart3", dataReceived);
    }



    @Test
    public void testReceiverReconstruction2() {

        BluePackage sender = BluePackage.createSender("HelloWorldThisIsATestThatGoesAroundAndShouldBreakToMultipleMessagesOK?");

        // we assume the first parcel as the header
        BluePackage receiver = null;
        String initialHeader = sender.getNextParcel();
        try {
            receiver = BluePackage.createReceiver(initialHeader);
        } catch (Exception e) {
            fail("Invalid header format");
        }

        for(int i = 0; i < receiver.getMessageParcelsTotal(); i++){
            String text = sender.getNextParcel();
            System.out.println(text);
            receiver.receiveParcel(text);
        }

        assertTrue(receiver.allParcelsReceivedAndValid());
        String dataReceived = receiver.getData();
        assertEquals("HelloWorldThisIsATestThatGoesAroundAndShouldBreakToMultipleMessagesOK?", dataReceived);
    }


    @Test
    public void testGaps() {
        String headerToReceive = "ab:003:JSDA:B";
        BluePackage receiver = BluePackage.createReceiver(headerToReceive);

        receiver.receiveParcel("ab000:DataPart1");
        assertFalse(receiver.hasGaps());

        receiver.receiveParcel("ab002:DataPart3");
        assertTrue(receiver.hasGaps());

        String gapIndex = receiver.getFirstGapParcel();
        assertEquals(receiver.getId() + "001", gapIndex);

        receiver.receiveParcel("ab001:DataPart2");
        assertFalse(receiver.hasGaps());
    }

}

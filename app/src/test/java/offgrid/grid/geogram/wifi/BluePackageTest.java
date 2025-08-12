package offgrid.grid.geogram.wifi;

import static org.junit.Assert.*;

import org.junit.Test;

import offgrid.geogram.ble.BluetoothMessage;

public class BluePackageTest {

    @Test
    public void testGaps() {

        String messageToSend = "Hello there, this is a long message just to test that we can send them";

        BluetoothMessage msg = new BluetoothMessage("CR7BBQ-15", "KO6ZJI-10", messageToSend);
        assertEquals(5, msg.getMessageParcelsTotal());

        // e.g. WO0:CR7BBQ-15:KO6ZJI-10:GMJA | WO1:Hello there, this  | WO2:is a long message  | WO3:just to test that  | WO4:we can send them
        String output = msg.getOutput();


        // test the reconstruction of messages
        BluetoothMessage msg2 = new BluetoothMessage();
        msg2.addMessageParcel(msg.getMessageParcels()[1]);

        // get the missing results
        String missingParcel = msg2.getFirstMissingParcel();
        String expectedId = msg.getId() + "0";
        assertEquals(expectedId, missingParcel);
        msg2.addMessageParcel(msg.getMessageParcels()[0]);

        assertFalse(msg2.isMessageCompleted());

        // get the missing results
        missingParcel = msg2.getFirstMissingParcel();
        expectedId = msg.getId() + "2";
        assertEquals(expectedId, missingParcel);

        msg2.addMessageParcel(msg.getMessageParcels()[2]);
        msg2.addMessageParcel(msg.getMessageParcels()[4]);

        // get the missing results
        missingParcel = msg2.getFirstMissingParcel();
        expectedId = msg.getId() + "3";
        assertEquals(expectedId, missingParcel);

        msg2.addMessageParcel(msg.getMessageParcels()[3]);

        assertTrue(msg2.isMessageCompleted());

        System.gc();

//        String headerToReceive = "ab:003:JSDA:B";
//        BluePackage receiver = BluePackage.createReceiver(headerToReceive);
//
//        receiver.receiveParcel("ab000:DataPart1");
//        assertFalse(receiver.hasGaps());
//
//        receiver.receiveParcel("ab002:DataPart3");
//        assertTrue(receiver.hasGaps());
//
//        String gapIndex = receiver.getFirstGapParcel();
//        assertEquals(receiver.getId() + "001", gapIndex);
//
//        receiver.receiveParcel("ab001:DataPart2");
//        assertFalse(receiver.hasGaps());
    }
    /*
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
*/

}

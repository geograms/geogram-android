package offgrid.grid.geogram;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

import offgrid.geogram.core.Log;
import offgrid.geogram.database.BeaconDatabase;
import offgrid.geogram.devices.DeviceReachable;

/**
 * Test beacons inside an Android device.
 */
@RunWith(AndroidJUnit4.class)
public class BeaconDatabaseTest {
    @Test
    public void testWritingToFolders() {
        // Context of the app under test.
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        //assertEquals("off.grid.geogram", appContext.getPackageName());

        File folder = BeaconDatabase.getFolder(appContext);
        assertTrue(folder.exists());
        Log.i("BeaconDatabaseTest", "Folder exists: " + folder.getAbsolutePath());

        // create a discovered beacon
        DeviceReachable beacon = new DeviceReachable();
        beacon.setMacAddress("00:11:22:33:44:55");
        // always 10 digits, user-defined
        beacon.setNamespaceId("0123456789");
        // identifier based on Android
        beacon.setDeviceId("000000");
        beacon.setRssi(74);

//        File file = BeaconDatabase.saveBeaconToDisk(beacon, appContext);
//        assertNotNull(file);
//        Log.i("BeaconDatabaseTest", "File exists: " + file.getAbsolutePath());


    }
}
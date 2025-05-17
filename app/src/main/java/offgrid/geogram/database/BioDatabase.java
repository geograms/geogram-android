package offgrid.geogram.database;

import android.content.Context;

import java.io.File;
import java.util.HashMap;

import offgrid.geogram.bluetooth.eddystone.DeviceFinder;
import offgrid.geogram.bluetooth.broadcast.BroadcastSender;
import offgrid.geogram.core.Log;
import offgrid.geogram.devices.DeviceReachable;

/**
 * Handles all data related to bio profiles that were sent
 * Each bio is mapped to a Device ID
 */
public class BioDatabase {

    public static final String TAG = "BioDatabase";
    public static final String FOLDER_NAME = "devices";
    public static final String FILE_NAME = "bio.json";

    // profiles that we have chatted so far. The string is the device Id being used
    private static final HashMap<String, BioProfile> profiles = new HashMap<>();


    /**
     * Gets the base folder for storage.
     *
     * @param context The application context.
     * @return The folder for storing beacon data.
     */
    private static File getFolder(Context context) {
        File folder = new File(context.getFilesDir(), FOLDER_NAME);
        if (!folder.exists()) {
            if (!folder.mkdirs()) {
                Log.e(TAG, "Failed to create folder: " + FOLDER_NAME);
                return null;
            }
        }
        return folder;
    }

    /**
     * Gets the folder associated with a specific device ID.
     *
     * @param deviceId The device ID
     * @param context  The application context.
     * @return The folder for the specified device ID.
     */
    public static File getFolderDeviceId(String deviceId, Context context) {
        if (deviceId == null) {
            Log.e(TAG, "Device ID is null");
            return null;
        }

        if (deviceId.length() < 3) {
            Log.e(TAG, "Device ID is too short: " + deviceId);
            return null;
        }
        String id = deviceId.substring(0, 3); // First three characters
        File folderBase = getFolder(context);
        if (folderBase == null) {
            Log.e(TAG, "Base folder could not be created");
            return null;
        }
        File folderSection = new File(folderBase, id);
        File folderDevice = new File(folderSection, deviceId);

        if (!folderDevice.exists()) {
            if (!folderDevice.mkdirs()) {
                Log.e(TAG, "Failed to create folder for device ID: " + folderDevice.getAbsolutePath());
                return null;
            }
        }
        return folderDevice;
    }


    private static void mergeRelevantSettings(BioProfile profile, Context appContext){
        String deviceId = profile.getDeviceId();
        if (deviceId == null) {
            Log.e(TAG, "Device Id is null");
            return;
        }
        // merge relevant settings
        BioProfile existingProfile = get(deviceId, appContext);
        if (existingProfile != null) {
            if(profile.getExtra() == null &&
                    existingProfile.getExtra() != null){
                profile.setExtra(existingProfile.getExtra());
                Log.i(TAG, "Merged extra: " + profile.getExtra());
            }
        }
    }

    /**
     * Saves a discovered thing to the database.
     *
     * @param profile     The profile to save or merge.
     * @param appContext The application context.
     */
    private static void saveToDisk(BioProfile profile, Context appContext) {
        String deviceId = profile.getDeviceId();
        if (deviceId == null) {
            Log.e(TAG, "Device Id is null");
            return;
        }
        File folderDevice = getFolderDeviceId(deviceId, appContext);
        if (folderDevice == null) {
            return;
        }
        File file = new File(folderDevice, FILE_NAME);

        // merge relevant settings
        mergeRelevantSettings(profile, appContext);


        try {
            profile.saveToFile(file, appContext);
        } catch (Exception e) {
            Log.e(TAG, "Failed to save data to file: " + file.getAbsolutePath() + " " + e.getMessage());
        }

        Log.i(TAG, "Saved data to file: " + file.getAbsolutePath());
    }

    /**
     * Retrieves a beacon by its device ID.
     *
     * @param deviceId   The device ID of the beacon.
     * @param appContext The application context.
     * @return The beacon object if found, or null if not.
     */
    public static BioProfile get(String deviceId, Context appContext) {
        if (deviceId == null) {
            Log.e(TAG, "Device Id is null");
            return null;
        }

        // if the device is in memory, add it from there
        if(profiles.containsKey(deviceId)){
            return profiles.get(deviceId);
        }

        // seems we really have to read it from disk
        File folderDevice = getFolderDeviceId(deviceId, appContext);
        if (folderDevice == null || !folderDevice.exists()) {
            Log.e(TAG, "Folder does not exist for " + deviceId);
            return null;
        }

        File file = new File(folderDevice, FILE_NAME);
        if (!file.exists()) {
            Log.e(TAG, "File does not exist: " + file.getAbsolutePath());
            return null;
        }
        Log.i(TAG, "Providing bio profile: " + deviceId);
        BioProfile profile = BioProfile.fromJson(file);
        if (profile == null) {
            Log.e(TAG, "Failed to load profile from file: " + file.getAbsolutePath());
            return null;
        }
        // save it in memory for future runs
        profiles.put(deviceId, profile);
        return profile;
    }

    public static void save(String deviceId, BioProfile profile, Context context) {
        if(profile == null){
            Log.e(TAG, "Profile is null");
            return;
        }
        if (deviceId == null) {
            Log.e(TAG, "Device Id is null");
        }

        // merge any relevant settings
        mergeRelevantSettings(profile, context);

        profiles.put(deviceId, profile);
        saveToDisk(profile, context);
    }

    public static BioProfile get(String deviceId) {
        if (deviceId == null) {
            Log.e(TAG, "Device Id is null");
            return null;
        }
        return profiles.getOrDefault(deviceId, null);
    }

    /**
     * Received a bluetooth ping for a specific deviceId
     *
     * @param data      presumed to be the deviceID
     * @param context   used for looking inside the archive on the disk
     */
    public static void ping(String data, String macAddress, Context context) {
        Log.i(TAG, "Ping received from " + macAddress + " with data: " + data);
        // data on this case is just the device ID. In the future will have more fields
        BioProfile profile = get(data, context);
        if(profile == null){
            Log.e(TAG, "Pinged profile not found for " + data);
            return;
        }
        DeviceReachable beacon = DeviceFinder.getInstance(context)
                .getDeviceMap().get(profile.getDeviceId());
        if(beacon == null){
            Log.e(TAG, "Pinged beacon not found for " + data);
            return;
        }
        // update the beacon
        beacon.setMacAddress(macAddress);
        beacon.setTimeLastFound(System.currentTimeMillis());
        DeviceFinder.getInstance(context).update(beacon);
        Log.i(TAG, "Ping updated beacon: " + beacon.getDeviceId() + " from " + beacon.getMacAddress());

    }

    /**
     * Send a bio profile to a device
     * @param macAddress MAC address of the device
     * @param context context to be used by the method
     */
    public static void sendBio(String macAddress, Context context) {
//        SettingsUser settings = Central.getInstance().getSettings();
//        BioProfile profile = new BioProfile();
//        profile.setNick(settings.getNickname());
//        String deviceId = settings.getIdDevice();
//        profile.setDeviceId(deviceId);
//        profile.setColor(settings.getPreferredColor());
//        //profile.setNpub(settings.getNpub());
//        String message = BlueCommands.tagBio + profile.toJson();
//        BluePackage packageToSend = BluePackage.createSender(DataType.B, message, deviceId);
//        BroadcastSendMessage.sendPackageToDevice(macAddress, packageToSend, context);
        BroadcastSender.sendProfileToEveryone(context);
    }
}

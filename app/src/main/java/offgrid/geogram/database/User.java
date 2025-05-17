package offgrid.geogram.database;

import java.util.ArrayList;

import offgrid.geogram.devices.NPUB;

/**
 * Defines a user which is normally a single person.
 * This single person can have several devices
 */
public class User {

    // the NPUBs that are associated to this user
    private ArrayList<NPUB> npubs = new ArrayList<>();

    // the devices that are associated to this user
    private ArrayList<String> devicesIds = new ArrayList<>();


}

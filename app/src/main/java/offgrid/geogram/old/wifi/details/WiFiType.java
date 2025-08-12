package offgrid.geogram.old.wifi.details;

/**
 * Common types of Wi-Fi devices.
 */
public enum WiFiType {

    // Movable Devices
    PHONE("Smartphone"),              // Common smartphone, assumed as movable location
    LAPTOP("Laptop"),                 // Laptop, assumed as movable location
    TABLET("Tablet"),                 // Tablet computer, assumed as movable location
    WATCH("Smartwatch"),              // Smart watch, assumed as movable location
    MICROCONTROLLER("IoT Device"),   // IoT boards like ESP32 or similar
    WEARABLE("Wearable"),             // Wearable devices with Wi-Fi built-in (e.g., glasses, fitness bands)
    DRONE("Drone"),                   // Wi-Fi-enabled flying devices
    GAME_CONSOLE("Game Console"),     // Handheld or home gaming consoles (e.g., Nintendo Switch)

    // Fixed Location Devices
    COMPUTER("Desktop"),              // Desktop computer, assumed as fixed location
    ROUTER("Router"),                 // Network router
    MESH_NODE("Mesh Node"),           // Node in a Wi-Fi mesh network
    GROUP_WIFI("Public Wi-Fi"),       // Group Wi-Fi access point with several locations
    STATION("Station"),               // Devices like weather stations
    CAMERA("Camera"),                 // Wi-Fi-connected cameras
    DOORBELL("Doorbell"),             // Smart video doorbells
    PRINTER("Printer"),               // Wi-Fi-enabled printers
    LIGHT("Smart Light"),             // Smart bulbs or fixtures
    APPLIANCE("Appliance"),           // Household appliances (e.g., fridge, washer)
    AIR_PURIFIER("Air Purifier"),     // Smart air purifiers
    THERMOSTAT("Thermostat"),         // Wi-Fi-connected thermostats (e.g., Nest)
    VACUUM("Vacuum Cleaner"),         // Smart robotic vacuums (e.g., Roomba)

    // Industrial and Commercial Devices
    POS_TERMINAL("POS Terminal"),     // Point-of-sale systems
    SENSOR("Sensor"),                 // Industrial IoT sensors (e.g., temperature, humidity)
    VENDING_MACHINE("Vending Machine"), // Smart vending machines
    ROBOT("Robot"),                   // Wi-Fi-enabled robots
    WAREHOUSE_ROBOT("Warehouse Robot"), // Automated warehouse devices

    // Automotive and Transportation Devices
    VEHICLE("Vehicle"),               // Cars or transport with Wi-Fi
    CHARGER("EV Charger"),            // Wi-Fi-enabled electric vehicle chargers
    DASH_CAMERA("Dash Camera"),       // Car dashboard cameras with Wi-Fi
    BIKE_LOCK("Smart Bike Lock"),     // Wi-Fi-enabled bike locks

    // Smart Entertainment Devices
    SPEAKER("Smart Speaker"),         // Smart speakers (e.g., Alexa, Google Home)
    TV("Smart TV"),                   // Wi-Fi-enabled TVs
    STREAMING_DEVICE("Streaming Device"), // Devices like Chromecast or Fire Stick
    PROJECTOR("Smart Projector"),     // Wi-Fi-enabled projectors

    // Education and Research
    MICROSCOPE("Wi-Fi Microscope"),   // Smart digital microscopes
    WHITEBOARD("Smart Whiteboard"),   // Interactive whiteboards

    // Emerging and Speculative Devices
    PERSONAL_ROBOT("Personal Robot"), // Wi-Fi-connected personal robots
    PET_FEEDER("Pet Feeder"),         // Automatic pet feeders
    CLOTHING("Smart Clothing"),       // Wearable technology in garments
    SOLAR_DRONE("Solar Drone"),       // Solar-powered drones with Wi-Fi
    NEURAL_INTERFACE("Neural Interface"), // Brain-computer interfaces with Wi-Fi

    // Other Types
    OTHER("Other"),                   // Unspecified device types
    UNKNOWN("Unknown");               // Undefined or unknown device type

    private final String description;

    /**
     * Constructor for WiFiType with a description.
     *
     * @param description short description of the device type
     */
    WiFiType(String description) {
        this.description = description;
    }

    /**
     * Gets the description of the Wi-Fi type.
     *
     * @return a short description of the Wi-Fi type
     */
    public String getDescription() {
        return description;
    }
}

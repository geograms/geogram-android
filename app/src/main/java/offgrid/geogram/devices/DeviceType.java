package offgrid.geogram.devices;

public enum DeviceType {
    PRIMARY_STATION,        // SSID 0 — your primary fixed station, usually message capable
    ADDITIONAL_STATION_1,   // SSID 1 — generic additional: digi, mobile, weather, etc.
    ADDITIONAL_STATION_2,   // SSID 2 — generic additional
    ADDITIONAL_STATION_3,   // SSID 3 — generic additional
    ADDITIONAL_STATION_4,   // SSID 4 — generic additional
    OTHER_NETWORKS,         // SSID 5 — other networks (D-Star, iPhones, Androids, etc.)
    SPECIAL_ACTIVITY,       // SSID 6 — special activities: satellites, camping, 6 m, etc.
    HT_PORTABLE,            // SSID 7 — walkie-talkies, HTs, human-portable
    BOATS_RVS,              // SSID 8 — boats, sailboats, RVs, second main mobile
    PRIMARY_MOBILE,         // SSID 9 — primary mobile, usually message capable
    INTERNET_IGATE,         // SSID 10 — internet, IGates, EchoLink, Winlink, AVRS etc.
    BALLOONS_AIRCRAFT,      // SSID 11 — balloons, aircraft, spacecraft
    ONE_WAY_TRACKERS,       // SSID 12 — APRStt, DTMF, RFID devices, one-way trackers
    WEATHER_STATION,        // SSID 13 — weather stations
    TRUCKERS,               // SSID 14 — truckers or full-time drivers
    ADDITIONAL_STATION_15   // SSID 15 — generic additional
    ;

    /** Convert SSID number (0-15) to DeviceType enum. */
    public static DeviceType fromSsid(int ssid) {
        switch (ssid) {
            case 0:  return PRIMARY_STATION;
            case 1:  return ADDITIONAL_STATION_1;
            case 2:  return ADDITIONAL_STATION_2;
            case 3:  return ADDITIONAL_STATION_3;
            case 4:  return ADDITIONAL_STATION_4;
            case 5:  return OTHER_NETWORKS;
            case 6:  return SPECIAL_ACTIVITY;
            case 7:  return HT_PORTABLE;
            case 8:  return BOATS_RVS;
            case 9:  return PRIMARY_MOBILE;
            case 10: return INTERNET_IGATE;
            case 11: return BALLOONS_AIRCRAFT;
            case 12: return ONE_WAY_TRACKERS;
            case 13: return WEATHER_STATION;
            case 14: return TRUCKERS;
            case 15: return ADDITIONAL_STATION_15;
            default: throw new IllegalArgumentException("Invalid SSID: " + ssid);
        }
    }

    /** Returns the recommended SSID value (0-15) for this DeviceType. */
    public int toSsid() {
        return this.ordinal();
    }

    @Override
    public String toString() {
        switch (this) {
            case PRIMARY_STATION: return "Primary Station (-0)";
            case ADDITIONAL_STATION_1: return "Additional Station 1 (-1)";
            case ADDITIONAL_STATION_2: return "Additional Station 2 (-2)";
            case ADDITIONAL_STATION_3: return "Additional Station 3 (-3)";
            case ADDITIONAL_STATION_4: return "Additional Station 4 (-4)";
            case OTHER_NETWORKS: return "Other Networks (-5)";
            case SPECIAL_ACTIVITY: return "Special Activity (-6)";
            case HT_PORTABLE: return "HT / Portable (-7)";
            case BOATS_RVS: return "Boats / RVs (-8)";
            case PRIMARY_MOBILE: return "Primary Mobile (-9)";
            case INTERNET_IGATE: return "Internet / IGate (-10)";
            case BALLOONS_AIRCRAFT: return "Balloons / Aircraft (-11)";
            case ONE_WAY_TRACKERS: return "One-Way Trackers (-12)";
            case WEATHER_STATION: return "Weather Station (-13)";
            case TRUCKERS: return "Truckers (-14)";
            case ADDITIONAL_STATION_15: return "Additional Station 15 (-15)";
            default: return super.toString();
        }
    }
}

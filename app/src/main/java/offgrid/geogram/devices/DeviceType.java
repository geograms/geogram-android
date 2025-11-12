package offgrid.geogram.devices;

import java.util.Locale;

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
    ADDITIONAL_STATION_15,  // SSID 15 — generic additional
    UNSPECIFIED;            // no SSID, invalid SSID, or unknown type

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
            default: return UNSPECIFIED;
        }
    }

    /** Returns the recommended SSID value (0-15) for this DeviceType; -1 for UNSPECIFIED. */
    public int toSsid() {
        return this == UNSPECIFIED ? -1 : this.ordinal();
    }

    /**
     * Determine device type from a callsign string like "DL1ABC-9".
     * Rules:
     *  - If callsign is null/blank → UNSPECIFIED.
     *  - If it has "-SSID" with 0..15 → mapped type.
     *  - If no "-SSID" or invalid/out-of-range → UNSPECIFIED.
     */
    public static DeviceType fromCallsign(String callsign) {
        if (callsign == null || callsign.trim().isEmpty()) return UNSPECIFIED;
        String cs = callsign.trim().toUpperCase(Locale.ROOT);

        int ssid = parseSsid(cs);
        if (ssid < 0) return UNSPECIFIED;
        return fromSsid(ssid);
    }

    /** Extract SSID (0..15) from a callsign like "K1ABC-7"; returns -1 if absent/invalid. */
    public static int parseSsid(String callsign) {
        if (callsign == null) return -1;
        int dash = callsign.lastIndexOf('-');
        if (dash < 0 || dash == callsign.length() - 1) return -1;
        try {
            int val = Integer.parseInt(callsign.substring(dash + 1));
            return (val >= 0 && val <= 15) ? val : -1;
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    @Override
    public String toString() {
        switch (this) {
            case PRIMARY_STATION: return "Primary Station";
            case ADDITIONAL_STATION_1: return "Additional Station";
            case ADDITIONAL_STATION_2: return "Additional Station";
            case ADDITIONAL_STATION_3: return "Additional Station";
            case ADDITIONAL_STATION_4: return "Additional Station";
            case OTHER_NETWORKS: return "Other Network";
            case SPECIAL_ACTIVITY: return "Special Activity";
            case HT_PORTABLE: return "Handheld Radio";
            case BOATS_RVS: return "Boat / RV";
            case PRIMARY_MOBILE: return "Mobile Station";
            case INTERNET_IGATE: return "Internet Relay";
            case BALLOONS_AIRCRAFT: return "Balloon / Aircraft";
            case ONE_WAY_TRACKERS: return "Tracker";
            case WEATHER_STATION: return "Weather Station";
            case TRUCKERS: return "Trucker";
            case ADDITIONAL_STATION_15: return "Additional Station";
            case UNSPECIFIED: return "Unknown Device";
            default: return super.toString();
        }
    }
}

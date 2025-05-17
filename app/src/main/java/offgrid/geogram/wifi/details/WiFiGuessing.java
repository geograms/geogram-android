package offgrid.geogram.wifi.details;

import offgrid.geogram.core.Log;

public class WiFiGuessing {

    private static final String TAG = "WiFiGuessing";

    private static final String[] listStartRouters = new String[]{
            "TP-Link",
            "Vodafone",
            "Vodafone Hotspot",
            "FRITZ!Box"
    };

    private static final String[] listStartSpeakers = new String[]{
            "LG_Speaker",
            "JBL Bar"
    };

    private static final String[] listSmartphones = new String[]{
            "DIRECT-",
    };



    private static final String[] listTwoKeywordsPrinters = new String[]{
            "HP Laser",
            "EPSON"
    };



    /**
     * Attempt to discover what kind of network this is in front of us
     * @param network the object to be improved
     */
    public static void detectNetworkType(WiFiNetwork network) {
        String SSID = network.getSSID();

        // bunch of rules to help us identify the device type

        // starting of names for routers and other devices
        if(matchStartWith(network, listStartRouters,
                WiFiType.ROUTER, WiFiMobility.FIXED_LOCATION)){
            return;
        }
        if(matchStartWith(network, listStartSpeakers,
                WiFiType.SPEAKER, WiFiMobility.FIXED_LOCATION)){
            return;
        }

        // match with two known words inside the SSID
        if(matchHasTwoKeywords(network, "DIRECT-", listTwoKeywordsPrinters,
                WiFiType.PRINTER, WiFiMobility.FIXED_LOCATION)){
            return;
        }
        if(matchStartWith(network, listSmartphones,
                WiFiType.PHONE, WiFiMobility.MOVING_EXPECTED)){
            return;
        }

    }

    private static boolean matchHasTwoKeywords(WiFiNetwork network, String keyword, String[] list,
                                               WiFiType wiFiType, WiFiMobility mobility) {
        String SSID = network.getSSID();
        for(String name : list){
            if(SSID.startsWith(keyword) && SSID.contains(name)){
                network.setType(wiFiType);
                network.setMobility(mobility);
                Log.i(TAG, "Device with " + SSID + " is: " + wiFiType.toString());
                return true;
            }
        }
        return false;
    }

    private static boolean matchStartWith(WiFiNetwork network, String[] list,
                                          WiFiType typeWiFi, WiFiMobility mobility) {
        String SSID = network.getSSID();
        for(String router : list){
            if(SSID.startsWith(router)){
                network.setType(typeWiFi);
                network.setMobility(mobility);
                Log.i(TAG, "Device with " + SSID + " is: " + typeWiFi.toString());
                return true;
            }
        }
        return false;
    }

}

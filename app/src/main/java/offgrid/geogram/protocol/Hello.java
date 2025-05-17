package offgrid.geogram.protocol;

import com.google.gson.annotations.Expose;

public class Hello {
         /*
            The first command on the protocol is "Hello".
            Android A will connect to the hotspot on the Android B
            and request information about Android B.

            This information includes the public key, the device ID
            and an index of the collections inside the device.
            It also includes number of messages and other statistics
            that might be relevant for Android A to consider
         */

    private String
            deviceId,
            npub,
            nick, // Limit: 15 characters
            color;


}

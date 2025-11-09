#!/bin/bash

# Comprehensive script to stub out all old Google Play Services dependencies

echo "Fixing ChatDatabaseWithDevice.java..."
cat > app/src/main/java/offgrid/geogram/apps/chat/ChatDatabaseWithDevice.java << 'EOF'
package offgrid.geogram.apps.chat;

import android.content.Context;
// Removed legacy imports - BioDatabase was part of old code

import java.io.File;

/**
 * DEPRECATED: This class used legacy BioDatabase which depended on Google Play Services
 * Kept for compatibility but functionality is stubbed out
 */
@Deprecated
public class ChatDatabaseWithDevice {

    public static File getFolder(String deviceId, Context context) {
        // Stubbed - BioDatabase removed (Google Play Services dependency)
        return null;
    }

    public static void save(String deviceId, String message, Context context) {
        // Stubbed - BioDatabase removed (Google Play Services dependency)
    }

    public static String load(String deviceId, Context context) {
        // Stubbed - BioDatabase removed (Google Play Services dependency)
        return "";
    }
}
EOF

echo "Fixing SimpleSparkServer.java..."
sed -i 's/^\(.*WiFiReceiver\)/\/\/ Removed (legacy) - \1/' app/src/main/java/offgrid/geogram/server/SimpleSparkServer.java
sed -i 's/^\(.*Message\.class\)/\/\/ Removed (legacy) - \1/' app/src/main/java/offgrid/geogram/server/SimpleSparkServer.java
sed -i 's/^\(.*message\.getContent\)/\/\/ Removed (legacy) - \1/' app/src/main/java/offgrid/geogram/server/SimpleSparkServer.java

echo "Fixing SettingsFragment.java..."
sed -i 's/sendProfileToEveryone/\/\/ sendProfileToEveryone/' app/src/main/java/offgrid/geogram/settings/SettingsFragment.java

echo "Fixing SettingsLoader.java..."
sed -i 's/GenerateDeviceId\./\/\/ GenerateDeviceId./' app/src/main/java/offgrid/geogram/settings/SettingsLoader.java

echo "Fixing ChatFragmentBroadcast.java..."
sed -i 's/BioProfile/\/\/ BioProfile/g' app/src/main/java/offgrid/geogram/apps/chat/ChatFragmentBroadcast.java
sed -i 's/BroadcastSender\./\/\/ BroadcastSender./g' app/src/main/java/offgrid/geogram/apps/chat/ChatFragmentBroadcast.java

echo "Fixing ChatFragmentDevice.java remaining issues..."
sed -i 's/^\(\s*\)\(.*tagBio.*\)/\1\/\/ Removed (legacy) - \2/' app/src/main/java/offgrid/geogram/apps/chat/ChatFragmentDevice.java
sed -i 's/^\(\s*\)\(.*BioDatabase.*\)/\1\/\/ Removed (legacy) - \2/' app/src/main/java/offgrid/geogram/apps/chat/ChatFragmentDevice.java

echo "All old code references stubbed out"
EOF

chmod +x fix-all-old-code.sh
./fix-all-old-code.sh

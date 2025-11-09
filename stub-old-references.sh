#!/bin/bash

# Fix all BioDatabase references in ChatDatabaseWithDevice
sed -i 's/^\(\s*File folder = \)BioDatabase\./\/\/ Removed (legacy code) - \1File /' \
  app/src/main/java/offgrid/geogram/apps/chat/ChatDatabaseWithDevice.java

# Fix DeviceFinder and BioProfile references in DeviceDetailsFragment  
sed -i 's/^\(\s*\)DeviceFinder\./\/\/ Removed (legacy code) - \1\/\/ DeviceFinder./' \
  app/src/main/java/offgrid/geogram/devices/DeviceDetailsFragment.java

sed -i 's/^\(\s*BioProfile.*=.*BioDatabase\.\)/\/\/ Removed (legacy code) - \1/' \
  app/src/main/java/offgrid/geogram/devices/DeviceDetailsFragment.java
  
echo "Stubbed out old references"

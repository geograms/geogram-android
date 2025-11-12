#!/bin/bash

# Multi-Device BLE Stress Test Runner
# Tests BLE communication between two Android devices
#
# Device 1 (Samsung): Sender
# Device 2 (TANK2): Receiver

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Device serial numbers
DEVICE1_SERIAL="R58M91ETKFE"
DEVICE2_SERIAL="adb-TANK200000007933-4IW9F8._adb-tls-connect._tcp"

# Test package and runner
PACKAGE="off.grid.geogram"
TEST_RUNNER="androidx.test.runner.AndroidJUnitRunner"

echo -e "${BLUE}╔═══════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║    Multi-Device BLE Stress Test                      ║${NC}"
echo -e "${BLUE}╚═══════════════════════════════════════════════════════╝${NC}"
echo ""

# Check if both devices are connected
echo -e "${YELLOW}Checking devices...${NC}"
if ! adb -s "$DEVICE1_SERIAL" get-state &>/dev/null; then
    echo -e "${RED}Error: Device 1 (Samsung) not found${NC}"
    exit 1
fi

if ! adb -s "$DEVICE2_SERIAL" get-state &>/dev/null; then
    echo -e "${RED}Error: Device 2 (TANK2) not found${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Device 1 (Samsung SM-G398FN): ${DEVICE1_SERIAL}${NC}"
echo -e "${GREEN}✓ Device 2 (TANK2): ${DEVICE2_SERIAL}${NC}"
echo ""

# Build and install APK
echo -e "${YELLOW}Building APK...${NC}"
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
./gradlew assembleDebugAndroidTest assembleDebug --console=plain | tail -20

if [ $? -ne 0 ]; then
    echo -e "${RED}Build failed!${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Build complete${NC}"
echo ""

# Install on both devices
echo -e "${YELLOW}Installing on Device 1 (Sender)...${NC}"
adb -s "$DEVICE1_SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk 2>&1 | grep -E "Success|INSTALL"
adb -s "$DEVICE1_SERIAL" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk 2>&1 | grep -E "Success|INSTALL"

echo -e "${YELLOW}Installing on Device 2 (Receiver)...${NC}"
adb -s "$DEVICE2_SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk 2>&1 | grep -E "Success|INSTALL"
adb -s "$DEVICE2_SERIAL" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk 2>&1 | grep -E "Success|INSTALL"

echo -e "${GREEN}✓ Installation complete${NC}"
echo ""

# Grant Bluetooth permissions
echo -e "${YELLOW}Granting permissions...${NC}"
adb -s "$DEVICE1_SERIAL" shell pm grant $PACKAGE android.permission.ACCESS_FINE_LOCATION
adb -s "$DEVICE1_SERIAL" shell pm grant $PACKAGE android.permission.BLUETOOTH_SCAN
adb -s "$DEVICE1_SERIAL" shell pm grant $PACKAGE android.permission.BLUETOOTH_ADVERTISE

adb -s "$DEVICE2_SERIAL" shell pm grant $PACKAGE android.permission.ACCESS_FINE_LOCATION
adb -s "$DEVICE2_SERIAL" shell pm grant $PACKAGE android.permission.BLUETOOTH_SCAN
adb -s "$DEVICE2_SERIAL" shell pm grant $PACKAGE android.permission.BLUETOOTH_ADVERTISE

echo -e "${GREEN}✓ Permissions granted${NC}"
echo ""

# Create log files
LOG_DIR="./ble-test-logs"
mkdir -p "$LOG_DIR"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
RECEIVER_LOG="$LOG_DIR/receiver_${TIMESTAMP}.log"
SENDER_LOG="$LOG_DIR/sender_${TIMESTAMP}.log"

echo -e "${BLUE}Logs will be saved to:${NC}"
echo -e "  Receiver: ${RECEIVER_LOG}"
echo -e "  Sender: ${SENDER_LOG}"
echo ""

# Clear logcat on both devices
adb -s "$DEVICE1_SERIAL" logcat -c
adb -s "$DEVICE2_SERIAL" logcat -c

echo -e "${BLUE}╔═══════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║    Starting Tests                                     ║${NC}"
echo -e "${BLUE}╚═══════════════════════════════════════════════════════╝${NC}"
echo ""

# Start receiver first (it needs to be listening)
echo -e "${YELLOW}[RECEIVER] Starting on Device 2...${NC}"
adb -s "$DEVICE2_SERIAL" shell am instrument -w \
    -e class offgrid.grid.geogram.BleReceiverStressTest \
    ${PACKAGE}.test/${TEST_RUNNER} > "$RECEIVER_LOG" 2>&1 &
RECEIVER_PID=$!

# Give receiver time to initialize
sleep 3

# Start sender
echo -e "${YELLOW}[SENDER] Starting on Device 1...${NC}"
adb -s "$DEVICE1_SERIAL" shell am instrument -w \
    -e class offgrid.grid.geogram.BleSenderStressTest \
    ${PACKAGE}.test/${TEST_RUNNER} > "$SENDER_LOG" 2>&1 &
SENDER_PID=$!

# Monitor both tests with logcat
echo ""
echo -e "${BLUE}Monitoring test execution (press Ctrl+C to stop monitoring)...${NC}"
echo -e "${BLUE}Tests will continue running in background${NC}"
echo ""

# Stream logcat from both devices in parallel
(adb -s "$DEVICE2_SERIAL" logcat -s BleReceiverStressTest:I | sed "s/^/[RECEIVER] /") &
LOGCAT2_PID=$!

(adb -s "$DEVICE1_SERIAL" logcat -s BleSenderStressTest:I | sed "s/^/[SENDER] /") &
LOGCAT1_PID=$!

# Wait for both tests to complete
wait $SENDER_PID
SENDER_EXIT=$?

wait $RECEIVER_PID
RECEIVER_EXIT=$?

# Stop logcat monitoring
kill $LOGCAT1_PID 2>/dev/null || true
kill $LOGCAT2_PID 2>/dev/null || true

echo ""
echo -e "${BLUE}╔═══════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║    Test Results                                       ║${NC}"
echo -e "${BLUE}╚═══════════════════════════════════════════════════════╝${NC}"
echo ""

if [ $SENDER_EXIT -eq 0 ]; then
    echo -e "${GREEN}✓ Sender test completed successfully${NC}"
else
    echo -e "${RED}✗ Sender test failed (exit code: $SENDER_EXIT)${NC}"
fi

if [ $RECEIVER_EXIT -eq 0 ]; then
    echo -e "${GREEN}✓ Receiver test completed successfully${NC}"
else
    echo -e "${RED}✗ Receiver test failed (exit code: $RECEIVER_EXIT)${NC}"
fi

echo ""
echo -e "${BLUE}Detailed logs saved to:${NC}"
echo -e "  ${RECEIVER_LOG}"
echo -e "  ${SENDER_LOG}"
echo ""

# Extract key statistics from receiver log
if [ -f "$RECEIVER_LOG" ]; then
    echo -e "${BLUE}Receiver Statistics:${NC}"
    grep -E "Total unique messages|Completed|Incomplete|Success rate" "$RECEIVER_LOG" | sed 's/^/  /'
fi

echo ""
echo -e "${BLUE}╔═══════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║    Test Complete                                      ║${NC}"
echo -e "${BLUE}╚═══════════════════════════════════════════════════════╝${NC}"

# Exit with error if either test failed
if [ $SENDER_EXIT -ne 0 ] || [ $RECEIVER_EXIT -ne 0 ]; then
    exit 1
fi

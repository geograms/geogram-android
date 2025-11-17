#!/bin/bash

# Test script for chunked file downloads over BLE/WiFi/Relay
# Usage: ./test-chunked-download.sh

set -e

TANK2_IP="192.168.178.28"
TANK2_PORT="45678"
DEVICE_ID="X15RJ0"
COLLECTION_ID="npub13059301306072a8648ce3d020106082a8648ce3d03010703420004ba798"

echo "========================================="
echo "Chunked Download Test Script"
echo "========================================="
echo "Tank2:      http://$TANK2_IP:$TANK2_PORT"
echo "Device:     $DEVICE_ID"
echo "Collection: $COLLECTION_ID"
echo ""

# Step 1: Check API health
echo "1. Checking API health..."
curl -s "http://$TANK2_IP:$TANK2_PORT/api/ping" | jq -r '.pong'
echo ""

# Step 2: List files in remote collection
echo "2. Listing files in remote collection..."
FILES=$(curl -s -X POST "http://$TANK2_IP:$TANK2_PORT/api/remote/list-files" \
  -H "Content-Type: application/json" \
  -d "{\"deviceId\":\"$DEVICE_ID\",\"collectionId\":\"$COLLECTION_ID\"}")

echo "$FILES" | jq -r '.files[] | "\(.name) (\(.size) bytes)"'
echo ""

# Step 3: Select a file to download
echo "3. Select file to download (default: images/FH1EQSTG5LEK6PW.jpg):"
read -p "File path: " FILE_PATH
FILE_PATH=${FILE_PATH:-images/FH1EQSTG5LEK6PW.jpg}

# Get file size
FILE_SIZE=$(echo "$FILES" | jq -r ".files[] | select(.name == \"$FILE_PATH\") | .size")
if [ "$FILE_SIZE" == "" ]; then
    echo "Error: File not found"
    exit 1
fi

echo "Selected: $FILE_PATH ($FILE_SIZE bytes)"
echo ""

# Step 4: Choose chunk size
echo "4. Choose chunk size:"
echo "   1) 4KB  (very conservative for BLE)"
echo "   2) 8KB  (default for BLE)"
echo "   3) 16KB (aggressive for BLE)"
echo "   4) 32KB (for WiFi/Relay)"
echo "   5) 64KB (for WiFi only)"
read -p "Choice [2]: " CHUNK_CHOICE
CHUNK_CHOICE=${CHUNK_CHOICE:-2}

case $CHUNK_CHOICE in
    1) CHUNK_SIZE=4096 ;;
    2) CHUNK_SIZE=8192 ;;
    3) CHUNK_SIZE=16384 ;;
    4) CHUNK_SIZE=32768 ;;
    5) CHUNK_SIZE=65536 ;;
    *) CHUNK_SIZE=8192 ;;
esac

TOTAL_CHUNKS=$(( ($FILE_SIZE + $CHUNK_SIZE - 1) / $CHUNK_SIZE ))
echo "Chunk size: $CHUNK_SIZE bytes ($TOTAL_CHUNKS chunks total)"
echo ""

# Step 5: Start chunked download
echo "5. Starting chunked download..."
RESPONSE=$(curl -s -X POST "http://$TANK2_IP:$TANK2_PORT/api/remote/download-file-chunked" \
  -H "Content-Type: application/json" \
  -d "{
    \"deviceId\":\"$DEVICE_ID\",
    \"collectionId\":\"$COLLECTION_ID\",
    \"filePath\":\"$FILE_PATH\",
    \"fileSize\":$FILE_SIZE,
    \"chunkSize\":$CHUNK_SIZE
  }")

echo "$RESPONSE" | jq '.'

FILE_ID=$(echo "$RESPONSE" | jq -r '.fileId')
if [ "$FILE_ID" == "null" ] || [ "$FILE_ID" == "" ]; then
    echo "Error: Failed to start download"
    exit 1
fi

echo ""
echo "Download started! fileId: $FILE_ID"
echo ""

# Step 6: Monitor progress
echo "6. Monitoring download progress..."
echo "   (Press Ctrl+C to stop monitoring)"
echo ""

LAST_PERCENT=-1
START_TIME=$(date +%s)

while true; do
    PROGRESS=$(curl -s "http://$TANK2_IP:$TANK2_PORT/api/downloads/$(echo -n "$FILE_ID" | jq -sRr @uri)")

    PERCENT=$(echo "$PROGRESS" | jq -r '.download.percentComplete // 0')
    DOWNLOADED=$(echo "$PROGRESS" | jq -r '.download.downloadedBytes // 0')
    TOTAL=$(echo "$PROGRESS" | jq -r '.download.totalBytes // 0')
    SPEED=$(echo "$PROGRESS" | jq -r '.download.speed // "0 B/s"')
    COMPLETED=$(echo "$PROGRESS" | jq -r '.download.completed // false')
    FAILED=$(echo "$PROGRESS" | jq -r '.download.failed // false')
    ERROR=$(echo "$PROGRESS" | jq -r '.download.errorMessage // ""')

    # Only print if percentage changed
    if [ "$PERCENT" != "$LAST_PERCENT" ]; then
        NOW=$(date +%s)
        ELAPSED=$((NOW - START_TIME))

        printf "[%3ds] %3d%% - %s / %s (%s)\n" \
            "$ELAPSED" "$PERCENT" \
            "$(numfmt --to=iec $DOWNLOADED 2>/dev/null || echo ${DOWNLOADED})" \
            "$(numfmt --to=iec $TOTAL 2>/dev/null || echo ${TOTAL})" \
            "$SPEED"

        LAST_PERCENT=$PERCENT
    fi

    # Check if completed
    if [ "$COMPLETED" == "true" ]; then
        echo ""
        echo "✓ Download completed successfully!"
        ELAPSED=$(($(date +%s) - START_TIME))
        AVG_SPEED=$(echo "scale=2; $FILE_SIZE / $ELAPSED" | bc 2>/dev/null || echo "0")
        echo "  Total time: ${ELAPSED}s"
        echo "  Average speed: $(numfmt --to=iec $AVG_SPEED 2>/dev/null || echo ${AVG_SPEED}) B/s"
        break
    fi

    # Check if failed
    if [ "$FAILED" == "true" ]; then
        echo ""
        echo "✗ Download failed: $ERROR"
        exit 1
    fi

    sleep 2
done

echo ""
echo "========================================="
echo "Test Complete!"
echo "========================================="
echo ""
echo "Check Tank2 logs for chunk details:"
echo "  curl -s \"http://$TANK2_IP:$TANK2_PORT/api/logs?limit=100\" | jq -r '.logs[]' | grep -E 'chunk|Chunk'"
echo ""
echo "Check for .partial and .manifest files on Tank2"

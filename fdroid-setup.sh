#!/bin/bash
set -e

# Geogram F-Droid Repository Setup Script
# Creates a self-hosted F-Droid repository for Geogram Android

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${GREEN}Geogram F-Droid Repository Setup${NC}"
echo "=================================="

# Check if fdroidserver is installed
if ! command -v fdroid &> /dev/null; then
    echo -e "${RED}Error: fdroidserver is not installed${NC}"
    echo "Install it with: sudo apt-get install fdroidserver"
    exit 1
fi

# Extract version
VERSION=$(grep 'versionName = ' app/build.gradle.kts | sed 's/.*versionName = "\(.*\)".*/\1/')
echo -e "${YELLOW}Current version: ${VERSION}${NC}"

# Create fdroid directory structure
mkdir -p fdroid/repo
cd fdroid

# Initialize if not already initialized
if [ ! -f "config.yml" ]; then
    echo -e "${YELLOW}Initializing F-Droid repository...${NC}"
    fdroid init

    # Update config.yml
    cat > config.yml <<EOF
repo_url: "https://geograms.github.io/fdroid/repo"
repo_name: "Geogram F-Droid Repository"
repo_description: "Official F-Droid repository for Geogram Android - Offline-first messaging"
repo_icon: "icon.png"
archive_older: 3
EOF

    echo -e "${GREEN}✓ Repository initialized${NC}"
fi

# Copy latest APK
echo -e "${YELLOW}Copying APK...${NC}"
if [ -f "../app/build/outputs/apk/debug/app-debug.apk" ]; then
    cp ../app/build/outputs/apk/debug/app-debug.apk repo/geogram-${VERSION}.apk
    echo -e "${GREEN}✓ APK copied: geogram-${VERSION}.apk${NC}"
else
    echo -e "${RED}Error: APK not found. Build it first with: ./gradlew assembleDebug${NC}"
    exit 1
fi

# Update repository index
echo -e "${YELLOW}Updating repository index...${NC}"
fdroid update --create-metadata

# Sign repository
echo -e "${YELLOW}Signing repository...${NC}"
fdroid publish

echo -e "${GREEN}✓ F-Droid repository ready!${NC}"
echo ""
echo "Next steps:"
echo "1. Create a new GitHub repository: geograms/fdroid"
echo "2. Push this fdroid/ directory:"
echo "   cd fdroid"
echo "   git init"
echo "   git add ."
echo "   git commit -m 'Initialize Geogram F-Droid repository'"
echo "   git branch -M main"
echo "   git remote add origin https://github.com/geograms/fdroid.git"
echo "   git push -u origin main"
echo ""
echo "3. Enable GitHub Pages on the repository"
echo "4. Users can add: https://geograms.github.io/fdroid/repo"

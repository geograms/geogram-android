#!/bin/bash
set -e

# Geogram Android Release Script
# Automates APK building and GitHub release creation

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if gh CLI is authenticated
if ! gh auth status &>/dev/null; then
    echo -e "${RED}Error: GitHub CLI is not authenticated${NC}"
    echo "Please run: gh auth login"
    exit 1
fi

# Extract version from build.gradle.kts
VERSION=$(grep 'versionName = ' app/build.gradle.kts | sed 's/.*versionName = "\(.*\)".*/\1/')
VERSION_CODE=$(grep 'versionCode = ' app/build.gradle.kts | sed 's/.*versionCode = \(.*\)/\1/')

if [ -z "$VERSION" ]; then
    echo -e "${RED}Error: Could not extract version from build.gradle.kts${NC}"
    exit 1
fi

echo -e "${GREEN}Building Geogram Android v${VERSION} (versionCode: ${VERSION_CODE})${NC}"

# Check if tag already exists
if git rev-parse "v${VERSION}" >/dev/null 2>&1; then
    echo -e "${RED}Error: Tag v${VERSION} already exists${NC}"
    echo "Please update the version in app/build.gradle.kts"
    exit 1
fi

# Clean and build APK
echo -e "${YELLOW}Cleaning previous builds...${NC}"
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew clean

echo -e "${YELLOW}Building debug APK...${NC}"
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew assembleDebug

# Check if build succeeded
if [ ! -f "app/build/outputs/apk/debug/app-debug.apk" ]; then
    echo -e "${RED}Error: APK build failed${NC}"
    exit 1
fi

# Copy APK to clean filename
echo -e "${YELLOW}Copying APK to geogram.apk...${NC}"
cp app/build/outputs/apk/debug/app-debug.apk geogram.apk

# Get APK size
APK_SIZE=$(du -h geogram.apk | cut -f1)
echo -e "${GREEN}APK built successfully: ${APK_SIZE}${NC}"

# Get commit range for changelog
PREVIOUS_TAG=$(git describe --tags --abbrev=0 2>/dev/null || echo "")
if [ -z "$PREVIOUS_TAG" ]; then
    CHANGELOG_RANGE="Initial release"
    COMPARE_URL=""
else
    CHANGELOG_RANGE="${PREVIOUS_TAG}...HEAD"
    COMPARE_URL="**Full Changelog:** https://github.com/geograms/geogram-android/compare/${PREVIOUS_TAG}...v${VERSION}"
fi

# Generate release notes
RELEASE_NOTES=$(cat <<EOF
# Geogram Android v${VERSION}

## 📦 Installation

1. Download \`geogram.apk\` (${APK_SIZE})
2. Enable "Install from Unknown Sources" on your Android device
3. Install the APK
4. Configure your NOSTR identity in Settings (nsec/npub keys)

**Requirements:** Android 10 (API 29) or higher

**Note:** This is a debug build signed with debug keystore for testing purposes.

## 📝 Changes

$(git log ${CHANGELOG_RANGE} --pretty=format:"- %s" 2>/dev/null || echo "- Initial release")

${COMPARE_URL}
EOF
)

# Create GitHub release
echo -e "${YELLOW}Creating GitHub release v${VERSION}...${NC}"
GIT_CONFIG_NOSYSTEM=1 gh release create "v${VERSION}" \
    geogram.apk \
    --title "v${VERSION}" \
    --notes "${RELEASE_NOTES}"

# Check if release was created successfully
if [ $? -eq 0 ]; then
    RELEASE_URL="https://github.com/geograms/geogram-android/releases/tag/v${VERSION}"
    echo -e "${GREEN}✓ Release created successfully!${NC}"
    echo -e "${GREEN}✓ Release URL: ${RELEASE_URL}${NC}"

    # Clean up temporary APK
    rm geogram.apk
    echo -e "${GREEN}✓ Cleaned up temporary files${NC}"
else
    echo -e "${RED}Error: Failed to create GitHub release${NC}"
    rm -f geogram.apk
    exit 1
fi

echo -e "${GREEN}Release process completed successfully!${NC}"

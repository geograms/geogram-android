# Zapstore Submission Guide

Zapstore is a NOSTR-based permissionless app store - perfect for Geogram since it already uses NOSTR protocol!

## Prerequisites

1. **Install NIP-07 Browser Extension** (NOSTR signer)
   - Chrome/Brave: [Alby](https://chrome.google.com/webstore/detail/alby/iokeahhehimjnekafflcihljlcjccdbe) or [nos2x](https://chrome.google.com/webstore/detail/nos2x/kpgefcfmnafjgpblomihpgmejjdanjjp)
   - Firefox: [Alby](https://addons.mozilla.org/en-US/firefox/addon/alby/)

2. **Have NOSTR Identity**
   - If you don't have one, create it in your extension
   - Geogram app can generate one for you too

## Submission Information (Ready to Use)

Visit: **https://publisher.zapstore.dev/**

### Required Fields

**APK URL:**
```
https://github.com/geograms/geogram-android/releases/download/v0.2.0/geogram.apk
```

**App Icon URL:**
```
https://raw.githubusercontent.com/geograms/geogram-android/main/assets/app-icon.png
```

**Source Code Repository URL:**
```
https://github.com/geograms/geogram-android
```

**License (SPDX Identifier):**
```
Apache-2.0
```

**Description (Optional - Markdown Supported):**
```markdown
# Geogram - Offline-First Mesh Communication

Resilient, decentralized communication platform using NOSTR protocol for end-to-end encrypted messaging.

## Features

- **NOSTR Protocol**: End-to-end encrypted messaging with automatic identity generation
- **Offline-First**: Messages cached locally, sync when connectivity available
- **BLE Proximity**: Discover nearby devices using Bluetooth Low Energy beacons
- **No Google Dependencies**: Pure native Android APIs, F-Droid compatible
- **APRS Integration**: Radio communications support for emergency response
- **Location Tracking**: GPS-based location sharing for coordination

## Privacy & Security

- All messages end-to-end encrypted using NOSTR protocol
- Keys stored locally only
- No central server required
- Permissionless and censorship-resistant

## Perfect For

- Emergency response teams
- Outdoor activities and hiking
- Privacy-conscious users
- Off-grid communication
- Amateur radio operators

Built with native Android APIs. No proprietary dependencies.
```

## Submission Steps

1. **Open Publisher Page**
   - Go to https://publisher.zapstore.dev/

2. **Connect NIP-07 Extension**
   - Click "Connect" button
   - Approve connection in your extension popup

3. **Fill in Form**
   - Copy/paste the information above into the corresponding fields

4. **Review Generated Events**
   - Zapstore will show you the NOSTR events that will be published
   - These include app metadata and release information

5. **Sign & Publish**
   - Click "Publish" button
   - Approve the signature in your NIP-07 extension
   - Events will be broadcast to NOSTR relays

6. **Verify Publication**
   - Your app should appear on https://zapstore.dev/ within minutes
   - Users with Zapstore can now discover and install Geogram

## Updating Your App

When you release a new version:

1. Update the APK URL to the new release tag
2. Return to https://publisher.zapstore.dev/
3. Publish the updated information
4. Both versions will be available (users get latest by default)

## Why Zapstore?

- **Permissionless**: No approval process, publish instantly
- **Decentralized**: Uses NOSTR relays, no single point of failure
- **Censorship-Resistant**: Can't be taken down or blocked
- **Perfect for Geogram**: Both use NOSTR protocol
- **Zaps Support**: Users can tip/zap developers directly via Lightning

## Additional Notes

- Zapstore uses "web-of-trust" metrics to surface quality apps
- Your NOSTR identity becomes your developer identity
- Users can follow you on NOSTR to get app updates
- Lightning integration allows users to zap you directly for support

---

## Alternative: Zapstore CLI

For automated publishing or CI/CD integration:

```bash
# Install Zapstore CLI (available for macOS and Linux)
# Visit: https://zapstore.dev/download

# Publish via CLI
zapstore publish \
  --apk https://github.com/geograms/geogram-android/releases/download/v0.2.0/geogram.apk \
  --icon https://raw.githubusercontent.com/geograms/geogram-android/main/assets/app-icon.png \
  --repo https://github.com/geograms/geogram-android \
  --license Apache-2.0
```

---

**Status**: Ready to submit! All URLs and information prepared.
**Time to publish**: ~5 minutes
**Approval needed**: None (permissionless!)

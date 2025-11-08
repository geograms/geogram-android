# Geogram for Android

**Geogram for Android** is a hybrid messaging and presence app designed to work both **offline** and **online**, enabling proximity-based communication via **BLE** and internet messaging when available. It integrates seamlessly with the Geogram ecosystem, including ESP32 beacons, HTML app and UV-K5 firmware nodes.

---

## 📱 Features

### 💬 **NOSTR Messaging**
- End-to-end encrypted messaging using NOSTR protocol
- Group conversations and 1:1 chats
- Default groups: X2DEVS (development), X2HELP (support), X2TALK (general)
- Offline message caching - read messages without internet
- Automatic sync when online
- Compatible with Geogram HTML client

### 📶 **Offline Discovery & BLE Communication**
- Uses **Bluetooth (BLE)** for peer-to-peer device communication
- Detects **geogram beacons** (e.g. T-Dongle S3) for passive proximity logging
- Store-and-forward messaging through beacons

### 🌍 **Hybrid Online/Offline Architecture**
- Works fully offline for core functionality
- Syncs with NOSTR relays when internet available
- Cryptographic event signing with Schnorr signatures
- All message signing happens on-device

### 🔔 **Location-Based Interactions**
- Detects Geogram beacons installed in physical locations (e.g. train stations, buildings)
- Receives context-specific messages (alerts, instructions, events)
- Logs beacon encounters with timestamps

### 🔐 **Privacy by Design**
- No centralized server or tracking
- Full offline-first support
- NOSTR identity (nsec/npub) managed locally
- BouncyCastle cryptography for secure operations

---

## 🧱 Requirements

- **Android 10+** (API 29 or higher)
- BLE and Wi-Fi hardware
- No internet required for core functionality (offline messaging via BLE)
- Internet optional for NOSTR relay synchronization

---

## 🛠️ Installation

### Option 1: Download Release APK (Recommended)

1. Go to [Releases](https://github.com/geograms/geogram-android/releases/latest)
2. Download `geogram.apk`
3. Enable "Install from Unknown Sources" on your Android device
4. Install the APK
5. Configure your NOSTR identity in Settings (nsec/npub keys)

### Option 2: Build from Source

1. Clone the repository:
   ```bash
   git clone https://github.com/geograms/geogram-android.git
   cd geogram-android
   ```

2. Open in Android Studio or build via command line:
   ```bash
   JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew assembleDebug
   ```

3. Install on device:
   ```bash
   JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew installDebug
   ```

---

## 🔧 Permissions

The app requires:
- **Location**: Required for BLE scanning (Android system requirement)
- **Bluetooth**: For BLE beacon detection and peer-to-peer messaging
- **Internet**: Optional, only for NOSTR relay synchronization

---

## 📡 Use Cases

### 1. NOSTR Group Messaging
- Join default groups or create custom conversations
- Send messages that sync across Geogram clients (Android + HTML)
- Read cached messages offline

### 2. Beacon-Based Communication
Geogram beacons (based on ESP32 or T-Dongle) installed in real-world locations can:
- Broadcast announcements to nearby users
- Serve as drop-off/pick-up relays for messages (store-and-forward)
- Provide time-sensitive alerts even when offline

### 3. Proximity Detection
- Automatically log nearby devices and beacons
- Track device encounters with timestamps
- View "Devices Within Reach" history

---

## 🧪 Development Info

- **Minimum SDK**: 29 (Android 10)
- **Target SDK**: 34 (Android 14)
- **Build System**: Gradle 8.x
- **Language**: Java 17
- **Architecture**: Fragment-based UI with offline-first database caching
- **Key Libraries**:
  - BouncyCastle for cryptography
  - Google Nearby API for BLE Direct
  - RecyclerView + SwipeRefreshLayout
  - Custom NOSTR implementation with Schnorr signatures

### Building Releases

See [RELEASING.md](RELEASING.md) for the automated release process.

---

## 📄 License

Licensed under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)

---

## 📣 Support

- **Telegram Group**: https://t.me/geogramx (main support channel)
- **GitHub Discussions**: https://github.com/orgs/geograms/discussions
- **Issues**: https://github.com/geograms/geogram-android/issues

---

## 🤝 Contributors

**Primary Contributor**
👤 Max Brito (Portugal/Germany) — 2025–present
- BLE integration & beacon logic
- BLE Direct peer messaging
- NOSTR protocol implementation
- Messages feature with offline caching
- UI/UX design

For full contributor list, see [`CONTRIBUTORS.md`](https://github.com/geograms/geogram-html/blob/main/CONTRIBUTORS.md)

---

## 🚀 Roadmap

- [ ] Release builds with keystore signing
- [ ] F-Droid distribution
- [ ] Direct BLE messaging between Android devices
- [ ] Encrypted file attachments
- [ ] Voice messages
- [ ] Map integration for beacon locations

# Geogram for Android

**Geogram for Android** is a hybrid messaging and presence app designed to work both **offline** and **online**, enabling proximity-based communication via **BLE** and internet when available. It integrates seamlessly with the Geogram ecosystem, including ESP32 beacons, HTML app and UV-K5 firmware nodes.

---

## 📱 Features

- 📶 **Offline Messaging & Discovery**
  - Uses **Bluetooth (BLE)** for peer-to-peer device communication.
  - Detects **geogram beacons** (e.g. T-Dongle S3) for passive proximity logging.

- 🌍 **Online Synchronization (Optional)**
  - Supports fallback to internet messaging when available.
  - Uses NOSTR-compatible keys and relays for decentralized communication.

- 🔔 **Location-Based Interactions**
  - Detects Geogram beacons installed in physical locations (e.g. train stations, buildings).
  - Receives context-specific messages (alerts, instructions, events).

- 🔐 **Privacy by Design**
  - All message signing happens on-device.
  - No centralized server or tracking; full offline-first support.

---

## 🧱 Requirements

- Android 5.0+ (API 21)
- BLE and Wi-Fi hardware
- No internet required for core functionality

---

## 🛠️ Installation

1. Clone the repository:
   ```bash
   git clone https://github.com/geograms/geogram-android.git
   ```
2. Open in Android Studio.
3. Build and deploy to your Android device:
   ```bash
   ./gradlew installDebug
   ```

> A prebuilt APK will be provided in future releases.

---

## 🔧 Permissions

The app will request:
- **Location**: Required for BLE and Wi-Fi Direct scanning
- **Eddystone beacons**: For BLE beacon detection
- **BLE**: For peer-to-peer local messaging
- **Internet**: Optional for sync and relay access

---

## 📡 Use Case: Beacon Messaging

Geogram beacons (based on ESP32 or T-Dongle) installed in real-world locations can:

- Broadcast announcements to nearby users
- Serve as drop-off/pick-up relays for messages (store-and-forward)
- Provide time-sensitive alerts even when offline

The app automatically interacts with beacons when in range and logs local context messages for the user.

---

## 🧪 Development Info

- Minimum SDK: 21
- Target SDK: 34
- Build system: Gradle
- Language: Kotlin + Jetpack libraries
- NOSTR integration via `nostr-tools`

---

## 📄 License

Licensed under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)

---

## 📣 Support

- Discussions: https://github.com/orgs/geograms/discussions  
- Issues: https://github.com/geograms/geogram-html/issues

---

## 🤝 Contributors

See [`CONTRIBUTORS.md`](https://github.com/geograms/geogram-html/blob/main/CONTRIBUTORS.md)

**Primary Contributor**  
👤 Max Brito (Portugal/Germany) — 2025–present  
- BLE integration  
- BLE Direct peer messaging  
- NOSTR relay support  
- Beacon logic and UI/UX

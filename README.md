# Geogram

Geogram is an Android app designed to enable seamless communication in both **off-grid** and **internet-enabled** environments. The app leverages **Wi-Fi Direct** and **Bluetooth Low Energy (BLE)** for off-grid messaging and traditional internet connections for online messaging. Geogram ensures connectivity even in remote areas.

*ATTENTION*: App is not yet available for download (early development)

---

## Features

- **Off-Grid Messaging**:
  - Send and receive messages without an internet connection.
  - Uses Wi-Fi Direct for peer-to-peer communication.
  - Utilizes Bluetooth LE for low-energy proximity-based beacons.

- **Internet Messaging**:
  - Switches to internet communication when available.
  - Provides fallback to internet-based protocols for reliability.

- **Hybrid Network Detection**:
  - Automatically detects available communication methods.
  - Prioritizes off-grid communication when internet is unavailable.

- **User-Friendly Interface** (in progress):
  - Clean and intuitive UI for seamless communication.
  - Message history for both off-grid and internet messages.

- **Security**:
  - Messages transmitted off-grid are encrypted for privacy.

---

## How does it work

Geograms are telegrams based on _geography_. In other words, specific
to a location. Users with the geogram app running on their devices are
interacting automatically with the geogram beacons installed on a
specific location.

Beacon are simple devices, often based on ESP32-arduino machines that
run from USB power and have both Wi-Fi and bluetooth available.
They are installed on locations such as houses, train stations or 
relevant places. Users passing through the beacons receive a
notification (most of them only time messages).

Beacons are both cheap and smart enough to interact with users,
providing local messages that are relevant such as welcome messages,
warning messages or even specific information about events and other
things happening on that place.

Last but not least, geogram beacons can store messages from users to
other users. They are meeting points to exchange messages in cases when
Internet is not available. These messages can either be written in
plain text or made private just for a specific user to read.

---

## Installation

1. Clone the repository or download the APK:
   ```bash
   git clone https://github.com/username/geogram.git
   ```
2. Open the project in Android Studio.
3. Sync Gradle files and build the project.
4. Install the APK on your Android device:
   ```bash
   adb install path/to/geogram.apk
   ```

---

## Usage

### Off-Grid Messaging
1. Enable **Wi-Fi Direct** or **Bluetooth** on your device.
2. Open the app and select "Off-Grid Mode".
3. Discover nearby devices and start sending messages.

### Internet Messaging
1. Connect your device to an active internet connection.
2. Open the app and select "Online Mode".
3. Start chatting with contacts using standard internet protocols.

---

## Permissions

Geogram requires the following permissions for full functionality:
- **Location**: Required for Wi-Fi Direct and Bluetooth functionality.
- **Bluetooth**: To connect and exchange messages with nearby devices.
- **Wi-Fi**: For peer-to-peer Wi-Fi Direct communication.
- **Internet**: For internet-based messaging.

---

## Development

### Requirements
- Android Studio (latest version)
- Gradle 7.0+
- Minimum SDK: 21 (Android 5.0)
- Target SDK: 34 (Android 14)

---

## Contributing

We welcome contributions! Please follow these steps:
1. Fork the repository.
2. Create a feature branch:
   ```bash
   git checkout -b feature/your-feature
   ```
3. Commit your changes:
   ```bash
   git commit -m "Add your feature"
   ```
4. Push to the branch:
   ```bash
   git push origin feature/your-feature
   ```
5. Open a pull request.

---

## Contact

You are welcome to contact directly.

Telegram: @brito_X1

---

## License

Geogram is licensed under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).


# UUID in BLE and Wi-Fi Direct: Implementation and Usage in the Project

## Overview

In this project, UUID plays a significant role in both Bluetooth Low Energy (BLE)
and Wi-Fi Direct operations. This document details how UUIDs are generated and
used in the context of device discovery, peer-to-peer communication, and service
advertisement between users.

---

## BLE UUID Implementation

The `BluetoothBeacon` class is responsible for generating and using UUIDs for BLE
advertising. The UUID serves as a unique identifier for devices and services
during the BLE advertising process.


## UUID Format

The UUID is constructed using several predefined parts, each with a fixed length:
- idApp: 2 characters: AA
- idProduct: 2 characters: BB
- idPub: 4 characters: CCCC
- idLocation: 4 to 5 characters: DDDD(D)
- idMisc: 16 characters: (M)MMMMMMMMMMMMMMM
- idSSID: 2 characters: EE
- idPassword: 8 characters: FFFFFFFF

The final UUID follows the standard format: 8-4-4-4-12.

Example UUID:
AA-BBCCCC-DDDDMMMM-MMMMMMMM-EEFFFFFFFF


## idLocation



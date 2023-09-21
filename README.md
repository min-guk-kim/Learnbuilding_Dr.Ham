# Thermal App
A research project for the National Science Foundation led by Dr. Ham at Texas A&M University.
We will be using thermal imaging to discover potential problems with buildings.

## Update History

### 2023-09-16 (by Minguk Kim)
#### Version Code: 14
#### Version Name: 2.4
Versions from 1.6 to 2.3 has been skipped.
The latest version containing all the recent updates is now available on the [Google Play Store]

#### **Update 1: App Stability Enhancement**
- **Fix**: Addressed an issue where the app would crash following the removal and reconnection of any sensor through the SensorPush App. This was caused due to a change in the corresponding `SensorName`.
- **Finding**: Found that if any single sensor is removed and then reconnected using the SensorPush App, the corresponding `SensorName` would change, leading to app crashes.
- **Future Work**: To ensure reliable data collection, `SENSORNAME1` and `SENSORNAME1` in `passwords.java` should be generated automatically.

#### **Update 2: Sensor Selection Feature**
- **New Feature**: Introduced a button that allows users to select their preferred sensor for data retrieval. While the necessary functions were already coded in the previous version, they were not uploaded to the Play Store; I have tested and applied these updates, making them now available to all users.

#### **Update 3: Privacy Policy for Release
- https://app.termly.io/dashboard/website/da269f1a-5163-4f43-8c7a-267f9f10f92e/privacy-policy

### 2023-09-17 (by Minguk Kim)
#### Version Code: 16
#### Version Name: 2.6
The latest version containing all the recent updates is now available on the [Google Play Store]

#### **Update 1: App Stability Enhancement**
- **Fix**: Resolved a problem causing the app to crash when toggling the thermal camera on and off. This issue was specific to smartphones running API 31 or higher.

### 2023-09-20 (by Minguk Kim)
#### Version Code: 17
#### Version Name: 2.7
The latest version containing all the recent updates is now available on the [Google Play Store]

#### **Update 1: App Stability Enhancement**
- **Fix**: Resolved a problem causing the app to crash when toggling the thermal camera on and off. This issue was specific to minSdkVersion. The minimum version should be 24 at least.
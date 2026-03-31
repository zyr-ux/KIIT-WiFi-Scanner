# KIIT-WiFi-Scanner  
_Data-collection counterpart of the KIIT Indoor Localisation project_

## Overview  
KIIT-WiFi-Scanner is an Android application written in Kotlin, designed to scan WiFi access points indoors (within the Kalinga Institute of Industrial Technology campus) and collect necessary data for the related indoor localisation research project.  
It records WiFi scan results (SSIDs, BSSIDs, signal strengths, timestamps, and locations) so that the dataset can be used to train and evaluate indoor localisation algorithms.

## Features  
- Runs on Android devices 
- Scans available WiFi networks periodically (configurable interval)  
- Captures: SSID, BSSID (MAC), signal strength (RSSI), timestamp, device location (if enabled)  
- Saves scanned results into a local file / database / exported CSV or similar (as implemented)  
- Simple UI for starting/stopping scans, selecting scan interval, exporting data  

## Getting Started  

### Prerequisites  
- Android Studio (Arctic Fox or newer, or as per `build.gradle.kts`)  
- Android SDK with necessary WiFi & location permissions  
- A test Android device or emulator with WiFi scanning capabilities  

### Build & Run  
```bash
git clone https://github.com/zyr-ux/KIIT-WiFi-Scanner.git  
cd KIIT-WiFi-Scanner/app  
# Open in Android Studio and run on device/emulator  
```

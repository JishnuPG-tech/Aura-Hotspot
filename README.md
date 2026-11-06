# Aura Hotspot • FastShare 5GHz (Android)

> **High-Performance, Low-Thermal Captive Hotspot Engine for Android (Android 11 - 15)**  
> Broadcasts high-speed 5 GHz Wi-Fi, redirects connecting guest devices to your 3D captive web portal, and unlocks line-rate internet routing upon passcode verification.

---

## ⚡ Key Highlights & Architecture

- **Zero Third-Party Apps Required:** Embedded native ADB client (`dadb`) and embedded Device Owner receiver eliminate the need for external Shizuku or Dhizuku manager apps.
- **Low-Heat & Battery Guard ("Race-to-Sleep"):**
  - Locked to **5 GHz 80MHz channel width**, transmitting at 500–800 Mbps burst rates so the Wi-Fi chip sleeps between packet bursts.
  - Recommended pairing with **4G+/LTE** to cut cellular modem power from 3.5W to under 0.9W, keeping the phone completely room-temperature cold.
- **Native Captive Network Assistant (CNA) Trigger:** Intercepts port 53 (DNS) and port 80 (HTTP probes) to automatically pop up your web portal on connecting iPhones, Android devices, Macs, and Windows PCs.
- **Passcode & Telemetry Sync:** Integrates with the cloud telemetry dashboard and 3D web card. Validates user-submitted passcodes (e.g. `Jishnupg`) and dynamically whitelists the client IP in `iptables`.

---

## 📱 Screenshots & UI

Built with **Jetpack Compose & Material 3** in a sleek dark executive palette (`#09090b` zinc-950, emerald-400):
- **Master Switch [Start Captive Hotspot / Stop]**
- **Live Connected Devices Telemetry**
- **Active SSID & Passcode Display**
- **One-Tap Link to 3D Web Portal & Cloud Analytics**

---

## 🚀 One-Time Setup (Post-Install)

Because Android's security sandbox prevents apps from granting themselves system firewall permissions, run this single ADB command once after installation:

```bash
adb shell dpm set-device-owner com.jishnu.aurahotspot/.admin.HotspotDeviceAdminReceiver
adb tcpip 5555
```

*After this single step, the app operates 100% autonomously forever with zero cables, zero PC dependencies, and zero external apps.*

---

## 🛠️ Building From Source

```bash
git clone https://github.com/JishnuPG-tech/Aura-Hotspot.git
cd Aura-Hotspot
./gradlew assembleDebug
```

Compiled APK will be located at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 📄 License
MIT License. Developed for ultra-fast, autonomous Android hotspot management.

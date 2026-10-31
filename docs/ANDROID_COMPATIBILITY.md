# Android compatibility report

**No Android device or emulator was used.** The table below is derived from the Android documentation and from what the code does per API level; "Tested" is **No** for every row. The in-app Performance screen shows the same matrix for the phone it runs on.

Targets: `minSdk 26`, `compileSdk/targetSdk 35` (Android 16 / API 36 not yet targeted — AGP 8.7 supports 35; bump when upgrading AGP). Primary device: Infinix X6856 (Dimensity 8300, Android 15 reported).

| Feature | 8–9 (26–28) | 10–11 (29–30) | 12–13 (31–33) | 14 (34) | 15–16 (35–36) | Tested |
|---|---|---|---|---|---|---|
| Default assistant | Settings → Default apps → Assist & voice input | `RoleManager` ROLE_ASSISTANT dialog | same | same | same | No |
| Mic service while screen off | foreground service | FGS type `microphone` | + while-in-use limits; started from visible UI | must declare `FOREGROUND_SERVICE_MICROPHONE`; cannot start from background without exemption | same; **not allowed from BOOT_COMPLETED** → tap notification | No |
| Notification permission | install-time | install-time | `POST_NOTIFICATIONS` from 13 | runtime | runtime | No |
| Media access | `READ_EXTERNAL_STORAGE` | same | `READ_MEDIA_IMAGES/VIDEO` from 13 | same | same | No |
| Exact reminders | exact | exact | `SCHEDULE_EXACT_ALARM` user-granted from 12 | same | same | No |
| Background activity start | allowed | blocked | blocked | blocked | blocked → assistant session / foreground / tap-notification | No |
| Bluetooth/Wi-Fi switching | settings screens | panels (`Settings.Panel`) | settings/panels | same | same | No |
| Package visibility | open | `<queries>` needed from 11 (declared) | same | same | same | No |
| 16 KB pages | n/a | n/a | n/a | n/a | native libs linked with 16 KB alignment (`libsmartllama`); sherpa-onnx libs come from upstream | No |
| Local LLM | arm64 + dotprod only | same | same | same | same | No |
| Thermal API | n/a | `currentThermalStatus` | yes | yes | yes | No |

## Required physical test matrix (all pending)
Android 8, 10, 12, 13, 14, 15 (Infinix X6856), 16. For each: install, grant permissions, set default assistant, standby 1 h with screen off, wake word ×50 incl. TV/noise, a call, WhatsApp, camera, reminder, reboot, battery drain per hour.

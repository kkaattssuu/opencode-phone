# OpenCode Phone

![OpenCode Phone icon](docs/app-icon.png)

Access OpenCode and Codex on your computer from an Android phone, with QR pairing and terminal views.

## Downloads and project status

| Component | Availability | Testing status |
| --- | --- | --- |
| Android app | [Download APK](https://github.com/kkaattssuu/opencode-phone/releases/latest) | Version 1.0.1 builds; the latest Codex disconnect changes have not been tested on a physical phone. |
| Windows pairing app | [Download Windows x64 preview](https://github.com/kkaattssuu/opencode-phone/releases/tag/desktop-v1.0.0) | **Untested with a real Windows installation and phone.** Automated Windows startup and synthetic QR tests passed. |
| Linux pairing app | [Source and build instructions](desktop/README.md) | **Untested end to end.** A Linux prototype compiled; no Linux desktop binary is provided yet. |
| Omarchy pairing dropdown | [Installation and removal instructions](omarchy-plugin/README.md) | QR generation and display tested locally; phone scanning and reboot reconnection have not been verified end to end. |

The Android APK is debug-signed. Android may require uninstalling a copy signed with a different key before installing it.

## Connect your phone

Install OpenCode on the computer, start its background service, and run `opencode pair --remote` or generate a code with a pairing app. In the Android app select **Connect laptop** and scan the QR code. Install and sign in to Codex separately to use Codex.

The computer must be awake, online and signed in. Pairing through OpenTunnel exposes the password-protected OpenCode service to the internet. A pairing code is single-use and expires after five minutes; keep it private. Paired sessions expire after 30 days.

The Android app includes OpenCode, Codex, an Android sandbox shell, and a laptop shell. The laptop shell can execute commands as the computer user. Task notifications require an active connection and Android permission; Android may restrict background operation. In Codex simple view, completion notifications can include an answer and session name. OpenCode notifications may not open the exact session, and Codex terminal notifications have limited details.

## Repository guide

- `app/` — Android source and phone terminal library.
- `desktop/` — Windows and Linux pairing app source.
- `omarchy-plugin/` and root `manifest.json` — Omarchy bar dropdown.
- `laptop/` — example Linux service files and pairing script.
- `docs/` — app icon.

## Build Android

Install JDK 21 and Android SDK 35, set `ANDROID_HOME` or the ignored `local.properties`, then run `./gradlew assembleDebug`. The APK is generated in `app/build/outputs/apk/debug/`. For native phone terminal libraries, use Android NDK 27.2.12479018 and run `PHONE_NDK_PATH=/path/to/ndk app/src/main/jni/build-native.sh`.

## Licensing

The Omarchy plugin is offered under the [MIT license](LICENSE). Third-party components retain their own licenses, including the QR encoder and xterm.js. The Android and desktop application source has not been assigned a separate project license.

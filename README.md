# OpenCode Phone

![OpenCode Phone icon](docs/app-icon.png)

Access OpenCode and Codex on your computer from an Android phone.

## Connect your phone

1. Install the [Android app](https://github.com/kkaattssuu/opencode-phone/releases/latest).
2. Install OpenCode on your computer:

   **Linux:**
   ```bash
   curl -fsSL https://opencode.ai/v2/install | bash
   ```

   **macOS:**
   ```bash
   brew install anomalyco/tap/opencode-v2
   ```

   **Windows:** Download the Windows CLI from [OpenCode's installation page](https://opencode.ai/docs/) and add it to your PATH.

3. On the computer, open a terminal (PowerShell on Windows) and run:
   ```sh
   opencode service start
   opencode pair --remote
   ```
   Accept the remote access prompt if shown.
4. In the Android app, choose **Connect laptop** and scan the QR code shown in the terminal.

Keep the computer awake, online and signed in. Remote pairing exposes the password-protected OpenCode service to the internet; keep the QR code private. Codes expire after five minutes and paired sessions after 30 days. To use Codex, install it and sign in on the computer separately.

## Omarchy dropdown

Omarchy users can install the [pairing dropdown](omarchy-plugin/README.md) to generate QR codes from the bar and optionally start OpenCode after login.

## Android build

Install JDK 21 and Android SDK 35, set `ANDROID_HOME`, and run `./gradlew assembleDebug`. The APK appears in `app/build/outputs/apk/debug/`. The downloadable APK is debug-signed; Android may require uninstalling a copy signed with a different key.

The Android disconnect changes and reboot reconnection have not been tested end to end on a physical phone. Notifications depend on Android background restrictions and an active connection.

The Omarchy plugin is offered under the [MIT license](LICENSE). Third-party components retain their own licenses. The Android application source has not been assigned a separate project license.

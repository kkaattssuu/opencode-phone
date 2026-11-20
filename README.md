# OpenCode Phone

![OpenCode Phone icon](docs/app-icon.png)

**Version 1.0.0** · [Download the Android APK](https://github.com/kkaattssuu/opencode-phone/releases/latest)

The downloadable APK is a debug-signed build. Android may require uninstalling an existing copy signed with a different key before installation.

Android 8+ app for connecting to OpenCode and the Codex CLI running on your laptop.

## First connection

Install OpenCode on the laptop and start its service with `opencode service start`. Run `opencode pair --remote` on the laptop to display a pairing QR code. In the Android app choose **Connect laptop**, allow camera access and scan the code. The laptop must remain awake and online. Install Codex separately and sign in on the laptop to use the Codex views.

The laptop needs to be awake, connected to the internet and logged in. Optional example systemd user units are in `laptop/`; ensure `opencode` is available in the user service PATH before enabling them. OpenCode's tunnel service and your phone's connection must also be available. The tunnel exposes the password-protected OpenCode server to the internet. Protect your phone and pairing links.

Notifications use a foreground service and require notification permission and an active connection. Android may stop or limit monitoring, particularly on Android 15 or when the app is force-stopped; delivery is not guaranteed. Monitoring resumes when opening a signed-in page. Event compatibility and end-to-end operation have not yet been tested on a phone.

## Terminals

**Phone terminal** runs Android's built-in shell inside this app's sandbox, with the app's permissions and app-private starting directory. It does not provide root, Termux packages, or automatic access to other apps' data. Android controls access to shared files.

**Laptop terminal** opens an interactive login shell through the paired OpenCode tunnel. It requires the laptop and OpenCode service to be running; it does not require a separate SSH app. Like Codex, the remote terminal can reconnect while its OpenCode terminal session remains available. Do not share the pairing link or your unlocked phone: this shell can run commands as your laptop user.

Both terminal views have an **Aa** control for text size. In fullscreen, tap the floating × to reveal the toolbar. The phone terminal ends when the Android app process is destroyed.

## Build

Use Android SDK 35 and a full JDK 21 — a JRE is not enough. Point Gradle at the SDK with `sdk.dir` in `local.properties` (or `ANDROID_HOME`), then run `./gradlew assembleDebug`. The debug APK is in `app/build/outputs/apk/debug/`. A different debug signing key may require uninstalling an earlier build first.

## Codex

After pairing OpenCode, choose **Open Codex** on the home screen. The app starts Codex inside an interactive terminal on your laptop using the existing OpenCode tunnel and your normal Codex account and permissions. The session starts in your home directory; use Codex commands to change projects or resume a session. The laptop, OpenCode background service and the tunnel must remain available. The terminal includes a scrollable row of special keys for mobile keyboards.

Codex notices are received through its terminal connection. They depend on the Android app remaining alive and connected; notifications cannot be guaranteed after a disconnect, app force-stop, or background termination. The running terminal can reconnect while its OpenCode PTY remains available.

## Rebuilding the phone shell

The APK includes native terminal libraries for arm64-v8a, armeabi-v7a, x86_64 and x86. To rebuild them, install Android NDK 27.2.12479018, then run `PHONE_NDK_PATH=/path/to/ndk app/src/main/jni/build-native.sh` before building the APK.

# OpenCode Phone Pairing for Windows

The desktop app generates a one-time QR code for the Android app. Install OpenCode on the computer first and ensure `opencode` is available in your PATH. Clicking Generate enables OpenCode remote access through OpenTunnel; the QR expires after five minutes. Keep the code private.

Windows build: install Qt 6 Quick with the MSVC 2022 x64 kit, open an x64 Native Tools command prompt, and run `qmake phonepair.pro CONFIG+=release`, then `nmake`. Package the executable with `windeployqt --release --qmldir src release\phonepair.exe`. The QR encoder is included under its MIT license in `src/vendor/`.

The Windows build workflow packages the executable and Qt runtime. On Windows, **Start after login** adds or removes this app’s entry in your user startup registry. It starts the OpenCode service after you sign in. Turning it off does not stop an already running service or erase pairing. Pairing sessions expire after 30 days.

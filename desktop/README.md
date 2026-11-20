# Desktop pairing app

A QR generator for Windows and Linux. Install OpenCode on the computer and add it to `PATH`; pressing Generate enables OpenCode remote access through OpenTunnel. Pairing codes expire after five minutes.

**Windows: not tested with a real Windows installation and phone.** An automated Windows build, startup check and synthetic QR test passed. [Download the Windows x64 preview](https://github.com/kkaattssuu/opencode-phone/releases/tag/desktop-v1.0.0), extract it, and run `phonepair.exe`. The optional startup switch adds or removes an entry in the current user's Windows startup registry. Pairing sessions expire after 30 days.

**Linux: not tested end to end.** The prototype compiled on Linux; no packaged Linux release is available. To build, install Qt 6 Quick/QML and a C++17 compiler, run `qmake6 phonepair.pro` and then `make`.

For a Windows build, use Qt 6 Quick with the MSVC 2022 x64 kit from an x64 Native Tools command prompt. Run `qmake phonepair.pro CONFIG+=release`, then `nmake`, and package using `windeployqt --release --qmldir src release\phonepair.exe`. The MIT-licensed QR encoder is included in `src/vendor/`.

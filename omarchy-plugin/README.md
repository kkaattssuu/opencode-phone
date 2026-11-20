# Omarchy pairing dropdown

This bar widget generates a one-time OpenCode remote pairing QR code on demand. It requires OpenCode, Python 3, `qrencode`, and systemd user services. Generating a code enables OpenCode remote access through OpenTunnel; keep the code private. The code expires after five minutes.

To install from this repository: `omarchy plugin add https://github.com/kkaattssuu/opencode-phone.git --enable`. If needed, move it with `omarchy bar move io.github.kkaattssuu.opencode-phone --section right`.

**Start after login** creates (if absent) and enables `opencode-phone.service` and `opencode-phone.timer` in your systemd user directory. It does not overwrite an existing unit. Turning the switch off disables the timer; it does not stop OpenCode, revoke the phone's pairing, or remove the files. To remove the plugin use `omarchy plugin remove io.github.kkaattssuu.opencode-phone`. To remove the optional timer and service, disable the timer and delete those two files from `~/.config/systemd/user`, then run `systemctl --user daemon-reload`.

OpenCode starts after the user logs in; the phone pairing session expires after 30 days. Reboot reconnection has not been tested end to end.

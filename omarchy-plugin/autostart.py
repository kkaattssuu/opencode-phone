#!/usr/bin/env python3
"""Manage the opt-in user timer without replacing existing configuration."""
import os
from pathlib import Path
import shutil
import subprocess
import sys

UNIT = 'opencode-phone.timer'

def install_units():
    directory = Path.home() / '.config' / 'systemd' / 'user'
    service = directory / 'opencode-phone.service'
    timer = directory / UNIT
    if service.exists() and timer.exists():
        return
    if service.exists() or timer.exists():
        raise ValueError('incomplete existing setup')
    executable = shutil.which('opencode')
    if not executable:
        raise ValueError('opencode not found on PATH')
    quoted = '"' + executable.replace('\\', '\\\\').replace('"', '\\"').replace('%', '%%') + '"'
    directory.mkdir(parents=True, exist_ok=True)
    service_text = '[Unit]\nDescription=Keep OpenCode available for remote access\nAfter=network-online.target\nWants=network-online.target\n\n[Service]\nType=oneshot\nExecStart=' + quoted + ' service start\n'
    timer_text = '[Unit]\nDescription=Start and check the OpenCode background service\n\n[Timer]\nOnBootSec=30s\nOnUnitActiveSec=1min\nUnit=opencode-phone.service\n\n[Install]\nWantedBy=timers.target\n'
    created = []
    try:
        for path, content in ((service, service_text), (timer, timer_text)):
            with path.open('x', encoding='utf-8') as out:
                created.append(path)
                out.write(content)
        subprocess.run(['systemctl', '--user', 'daemon-reload'], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=True, timeout=20)
    except (OSError, subprocess.SubprocessError):
        for path in created:
            path.unlink(missing_ok=True)
        raise

try:
    action = sys.argv[1] if len(sys.argv) == 2 else 'status'
    if action not in ('status', 'on', 'off'):
        raise ValueError('invalid action')
    if action == 'on':
        install_units()
    if action != 'status':
        subprocess.run(['systemctl', '--user', 'enable' if action == 'on' else 'disable', '--now', UNIT], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=True, timeout=20)
    status = subprocess.run(['systemctl', '--user', 'is-enabled', UNIT], stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True, timeout=10)
    if status.stdout.strip() == 'enabled':
        print('enabled')
    elif status.stdout.strip() in ('disabled', 'not-found'):
        print('disabled')
    else:
        raise ValueError('timer unavailable')
except (OSError, ValueError, subprocess.SubprocessError):
    print('error')
    sys.exit(1)

#!/usr/bin/env python3
import re
import subprocess
import sys

try:
    result = subprocess.run(['opencode', 'pair', '--remote'], stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, timeout=35, check=True)
    output = re.sub(rb'\x1b\[[0-9;?]*[A-Za-z]', b'', result.stdout)
    urls = re.findall(rb'https://[^\s\x1b]+/auth/connect/[A-Za-z0-9_-]+', output)
    if not urls or len(urls[0]) > 4096:
        raise ValueError('No pairing link')
    qr = subprocess.run(['qrencode', '-t', 'ASCII', '-m', '4', '-o', '-'], input=urls[0], stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, timeout=5, check=True).stdout.decode('ascii')
    lines = qr.splitlines()
    if not lines or len(lines)>200 or any(len(line) != len(lines)*2 or re.search(r'[^ #]', line) for line in lines):
        raise ValueError('Invalid QR matrix')
    sys.stdout.write('\n'.join(''.join('1' if line[i] == '#' else '0' for i in range(0,len(line),2)) for line in lines))
except (OSError, ValueError, subprocess.SubprocessError):
    sys.exit(1)

#!/usr/bin/env python3
"""Check publishable paths and common leaks. No network calls or secret values printed."""
from pathlib import Path
import re
import hashlib
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
SKIP = {'.git', '.gradle', 'build', '.idea', '__pycache__'}
FORBIDDEN = {'local.properties', 'keystore.properties', 'secrets.properties', '.env'}
BINARY_OK = {'.jpg', '.png', '.jar'}
PATTERNS = {
    'private key': re.compile(rb'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----'),
    'GitHub token': re.compile(rb'(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{50,})'),
    'cloud access key': re.compile(rb'AKIA[0-9A-Z]{16}'),
    'API key': re.compile(rb'\bsk-(?:proj-)?[A-Za-z0-9_-]{32,}'),
    'absolute home path': re.compile(rb'(?:/Users/|/home/)[A-Za-z0-9_.-]+/'),
    'URL password': re.compile(rb'https?://[^\s/"<>]+:[^\s/"<>]+@'),
    'IPv6 device address': re.compile(rb'\b(?:[0-9a-fA-F]{1,4}:){4,}[0-9a-fA-F:]+\b'),
}


def paths():
    if (ROOT / '.git').exists():
        result = subprocess.run(['git', 'ls-files', '-z', '--cached', '--others', '--exclude-standard'], cwd=ROOT,
                                capture_output=True, check=True)
        return [ROOT / x.decode() for x in result.stdout.split(b'\0') if x]
    return [p for p in ROOT.rglob('*') if p.is_file() and not SKIP.intersection(p.relative_to(ROOT).parts)
            and p.name not in FORBIDDEN]


def main():
    findings = []
    files = paths()
    for path in files:
        name = path.relative_to(ROOT).as_posix()
        if path.is_symlink():
            findings.append((name, 'symlink requires review'))
            continue
        if path.name in FORBIDDEN or path.suffix.lower() in {'.keystore', '.jks', '.p12', '.pem', '.key', '.apk', '.aab', '.db', '.sqlite', '.zip', '.tar', '.log'}:
            findings.append((name, 'private or generated file type'))
        if any(part in {'private', 'captures', 'backups', 'outputs', 'work'} for part in path.relative_to(ROOT).parts):
            findings.append((name, 'device/output directory'))
        data = path.read_bytes()
        # Wrapper JAR is the only allowed archive in the source tree.
        if path.suffix == '.jar':
            if name != 'gradle/wrapper/gradle-wrapper.jar':
                findings.append((name, 'unexpected archive'))
            elif hashlib.sha256(data).hexdigest() != '498495120a03b9a6ab5d155f5de3c8f0d986a449153702fb80fc80e134484f17':
                findings.append((name, 'wrapper differs from the official Gradle 8.9 JAR'))
            continue
        if path.suffix.lower() in {'.jpg', '.png'}:
            if b'Exif\0\0' in data or b'<x:xmpmeta' in data or b'eXIf' in data:
                findings.append((name, 'image metadata requires review'))
            if path.suffix == '.png':
                offset = 8
                while offset + 12 <= len(data):
                    size = int.from_bytes(data[offset:offset+4], 'big')
                    kind = data[offset+4:offset+8]
                    if kind in {b'tEXt', b'zTXt', b'iTXt'}:
                        findings.append((name, 'PNG text metadata requires review'))
                    offset += size + 12
            continue
        try:
            data.decode('utf-8')
        except UnicodeDecodeError:
            findings.append((name, 'unexpected binary'))
            continue
        if name == 'gradle/wrapper/gradle-wrapper.properties' and not re.search(rb'^distributionSha256Sum=[0-9a-f]{64}$', data, re.M):
            findings.append((name, 'distribution checksum must be exactly 64 hex characters'))
        for label, pattern in PATTERNS.items():
            # This checker contains deliberately literal examples of its own patterns.
            if name != 'scripts/check-public.py' and pattern.search(data):
                # The only URL user/password string permitted is a negative fixture.
                if label == 'URL password' and name.startswith('app/src/test/'):
                    continue
                findings.append((name, label))
        # Literal IPs are allowed only in synthetic policy/network unit tests.
        if re.search(rb'(?<![\w.])(?:\d{1,3}\.){3}\d{1,3}(?![\w.])', data) and not name.startswith('app/src/test/'):
            findings.append((name, 'literal network address requires review'))
    for name, label in findings:
        print(f'REVIEW {name}: {label}')
    print(f'Checked {len(files)} public files; {len(findings)} findings. Values are never printed.')
    print('This check complements manual review and a full-history secret scanner; it is not a guarantee.')
    return bool(findings)


if __name__ == '__main__':
    sys.exit(main())

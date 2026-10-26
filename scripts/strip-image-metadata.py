#!/usr/bin/env python3
"""Remove JPEG metadata segments without decoding or changing image pixels."""
from pathlib import Path
import sys


def strip(data):
    if not data.startswith(b'\xff\xd8'):
        raise ValueError('Expected a JPEG')
    result = bytearray(data[:2])
    i = 2
    while i < len(data):
        if data[i] != 255:
            raise ValueError('Invalid JPEG marker')
        start = i
        while data[i] == 255:
            i += 1
        marker = data[i]
        i += 1
        if marker in (0xDA, 0xD9):  # Image scan and compressed pixels stay byte-for-byte intact.
            result.extend(data[start:])
            return bytes(result)
        length = int.from_bytes(data[i:i+2], 'big')
        end = i + length
        if length < 2 or end > len(data):
            raise ValueError('Invalid JPEG segment')
        if marker not in (0xE1, 0xED, 0xFE):  # EXIF/XMP, IPTC and comments.
            result.extend(data[start:end])
        i = end
    raise ValueError('JPEG contains no image scan')


if __name__ == '__main__':
    for name in sys.argv[1:]:
        path = Path(name)
        path.write_bytes(strip(path.read_bytes()))
        print(f'Removed metadata: {path.name}')

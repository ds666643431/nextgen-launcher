#!/usr/bin/env python3
"""Decrypt a nextgenlauncher_trace.enc file (ChaCha20 / RFC 7539, counter 0).

    pip install pycryptodome
    adb -s <serial> shell run-as menu.nextgen.launcher cat files/nextgenlauncher_trace.enc > trace.enc
    # (or pull it if readable)
    python scripts/decrypt_trace.py trace.enc

Matches the mod .so's RustCrypto `chacha20::ChaCha20` (12-byte nonce, 32-bit counter=0).
"""
import sys
from Crypto.Cipher import ChaCha20  # pycryptodome

KEY = b"nextgenlauncher-trace-key-2026-A"   # 32 bytes
NONCE = b"ngl-trace001"                      # 12 bytes

def main() -> None:
    if len(sys.argv) != 2:
        print("usage: decrypt_trace.py <nextgenlauncher_trace.enc>")
        raise SystemExit(2)
    data = open(sys.argv[1], "rb").read()
    out = ChaCha20.new(key=KEY, nonce=NONCE).decrypt(data)
    sys.stdout.buffer.write(out)

if __name__ == "__main__":
    main()

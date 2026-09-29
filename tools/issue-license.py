"""QRScanner activation code reference implementation (seller-side tool).

The Java verifier in the app is a 1:1 port of this file.  Keep them in sync.

  python issue-license.py keygen                 -> new keypair (writes key files)
  python issue-license.py machine CODE...        -> device code from a raw fingerprint
  python issue-license.py issue AAAAAA-BBBBBB   -> signed activation code
  python issue-license.py verify AAAAAA-BBBBBB   -> check an activation code
  python issue-license.py selftest               -> known-answer test vector for the app

Wire format of an activation code (always 80 bytes -> 128 symbols, 8 groups of 16):

    byte 0        dataLen  = number of payload+signature+crc bytes that follow
    bytes 1..N    payload (6)  : version(1) + device hash(5)
                  signature     : DER ECDSA P-256, 70..72 bytes
                  crc8(1)       : CRC-8/ATM over payload+signature
    rest          zero padding to a fixed 80 bytes

The fixed size keeps base32 an exact round trip: 80 bytes is 128 symbols, and
128 symbols decode back to exactly 80 bytes with no trailing padding byte.
"""
import base64
import hashlib
import os
import sys

ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"  # 32 symbols, I/O/0/1 omitted
VERSION = 1
DEVICE_HASH_LEN = 5
PAYLOAD_LEN = 1 + DEVICE_HASH_LEN
MACHINE_BYTES = 15
MACHINE_GROUP = 6
CODE_GROUP = 16
BLOB_BYTES = 80                 # fixed, multiple of 5
CODE_CHARS = BLOB_BYTES * 8 // 5  # 128

# Keys live outside the repository on purpose: the private key must never be
# committed, and a temp directory can be purged at any time.
KEY_DIR = os.environ.get("QRSCANNER_KEY_DIR") or os.path.join(
    os.path.expanduser("~"), ".qrscanner-license")
PRIV_PATH = os.path.join(KEY_DIR, "license-private.key")
PUB_PATH = os.path.join(KEY_DIR, "license-public.key")


# ---------------------------------------------------------------- base32


def b32encode(data: bytes) -> str:
    """5 bits per symbol, big-endian. len(data) must be a multiple of 5."""
    if len(data) % 5 != 0:
        raise ValueError("b32encode needs a multiple of 5 bytes, got %d" % len(data))
    bits = int.from_bytes(data, "big")
    count = len(data) * 8 // 5
    return "".join(ALPHABET[(bits >> (i * 5)) & 0x1F] for i in range(count - 1, -1, -1))


def b32decode(text: str) -> bytes:
    """Inverse of b32encode. len(text) must be a multiple of 8."""
    clean = normalize(text)
    if len(clean) % 8 != 0:
        raise ValueError("b32decode needs a multiple of 8 symbols, got %d" % len(clean))
    out = bytearray(len(clean) * 5 // 8)
    buffer = 0
    bits = 0
    index = 0
    for ch in clean:
        idx = ALPHABET.find(ch)
        if idx < 0:
            raise ValueError("invalid symbol %r" % ch)
        buffer = (buffer << 5) | idx
        bits += 5
        if bits >= 8:
            bits -= 8
            out[index] = (buffer >> bits) & 0xFF
            index += 1
            buffer &= (1 << bits) - 1
    return bytes(out)


def normalize(text: str) -> str:
    return "".join(ch for ch in (text or "").upper() if ch in ALPHABET)


def ungroup(text: str) -> str:
    return text.replace("-", "").replace(" ", "").strip().upper()


def group(text: str, size: int) -> str:
    return "-".join(text[i:i + size] for i in range(0, len(text), size))


# ---------------------------------------------------------------- crc8


def crc8(data: bytes) -> int:
    crc = 0
    for byte in data:
        crc ^= byte
        for _ in range(8):
            crc = ((crc << 1) ^ 0x07) & 0xFF if crc & 0x80 else (crc << 1) & 0xFF
    return crc


# ---------------------------------------------------------------- keys


def load_private():
    from cryptography.hazmat.primitives import serialization

    if not os.path.exists(PRIV_PATH):
        raise SystemExit("private key not found: %s\nrun: python issue-license.py keygen"
                         % PRIV_PATH)
    with open(PRIV_PATH, "rb") as f:
        return serialization.load_der_private_key(f.read(), password=None)


def load_public_b64() -> str:
    with open(PUB_PATH, "r", encoding="ascii") as f:
        return f.read().strip()


def cmd_keygen():
    from cryptography.hazmat.primitives import serialization
    from cryptography.hazmat.primitives.asymmetric import ec

    if os.path.exists(PRIV_PATH):
        print("refusing to overwrite the existing key at %s" % PRIV_PATH)
        print("losing it means every code already issued becomes unverifiable.")
        return 1

    os.makedirs(KEY_DIR, exist_ok=True)
    key = ec.generate_private_key(ec.SECP256R1())
    priv = key.private_bytes(
        serialization.Encoding.DER,
        serialization.PrivateFormat.PKCS8,
        serialization.NoEncryption(),
    )
    pub = key.public_key().public_bytes(
        serialization.Encoding.DER,
        serialization.PublicFormat.SubjectPublicKeyInfo,
    )
    with open(PRIV_PATH, "wb") as f:
        f.write(priv)
    with open(PUB_PATH, "w", encoding="ascii") as f:
        f.write(base64.b64encode(pub).decode("ascii"))
    print("private key -> %s  (%d bytes, KEEP OFF GIT AND BACK IT UP)" % (PRIV_PATH, len(priv)))
    print("public key  -> %s" % PUB_PATH)
    print()
    print("paste this into LicenseManager.PUBLIC_KEY:")
    print(base64.b64encode(pub).decode("ascii"))


# ---------------------------------------------------------------- payload


def device_hash(android_id: str, manufacturer: str, model: str) -> bytes:
    raw = "|".join([(android_id or "").strip(),
                    (manufacturer or "").strip(),
                    (model or "").strip()])
    return hashlib.sha256(raw.encode("utf-8")).digest()


def device_code(android_id: str, manufacturer: str, model: str) -> str:
    return group(b32encode(device_hash(android_id, manufacturer, model)[:MACHINE_BYTES]),
                 MACHINE_GROUP)


def build_payload(dhash: bytes) -> bytes:
    return bytes([VERSION]) + dhash[:DEVICE_HASH_LEN]


def _ecdsa():
    """Deterministic ECDSA (RFC 6979) when available.

    Deterministic signing makes the same machine code always produce the same
    activation code, so re-issuing is idempotent and the app's self-test vector
    is stable. Falls back to randomized signing on older cryptography builds.
    """
    from cryptography.hazmat.primitives import hashes
    from cryptography.hazmat.primitives.asymmetric import ec

    try:
        return ec.ECDSA(hashes.SHA256(), deterministic_signing=True)
    except TypeError:
        return ec.ECDSA(hashes.SHA256())


def sign_payload(payload: bytes) -> bytes:
    return load_private().sign(payload, _ecdsa())


def verify_payload(payload: bytes, sig: bytes) -> bool:
    from cryptography.hazmat.primitives import hashes, serialization
    from cryptography.hazmat.primitives.asymmetric import ec

    pub = serialization.load_der_public_key(base64.b64decode(load_public_b64()))
    try:
        pub.verify(sig, payload, ec.ECDSA(hashes.SHA256()))
        return True
    except Exception:
        return False


def make_blob(dhash: bytes) -> bytes:
    payload = build_payload(dhash)
    sig = sign_payload(payload)
    body = payload + sig
    data = body + bytes([crc8(body)])
    if len(data) + 1 > BLOB_BYTES:
        raise ValueError("payload too large: %d" % (len(data) + 1))
    blob = bytes([len(data)]) + data
    return blob + bytes(BLOB_BYTES - len(blob))


def parse_device_code(code: str) -> bytes:
    """Turn a machine code from the app back into the 5-byte device hash.

    Strict on purpose: a malformed code would otherwise be signed into a code
    that no real handset could ever accept, which looks like a successful sale
    but silently fails on the customer's phone.
    """
    clean = ungroup(code)
    if len(clean) != MACHINE_BYTES * 8 // 5:
        raise ValueError("machine code must be %d characters, got %d"
                         % (MACHINE_BYTES * 8 // 5, len(clean)))
    return b32decode(clean)[:DEVICE_HASH_LEN]


def issue(code: str) -> str:
    dhash = parse_device_code(code)
    return group(b32encode(make_blob(dhash)), CODE_GROUP)


def verify(code: str) -> bool:
    try:
        blob = b32decode(ungroup(code))
    except ValueError:
        return False
    if len(blob) != BLOB_BYTES or blob[0] < PAYLOAD_LEN + 8:
        return False
    data_len = blob[0]
    if data_len + 1 > BLOB_BYTES:
        return False
    # the tail is zero padding: reject a non-canonical encoding so that every
    # activation code has exactly one valid spelling
    if any(blob[1 + data_len:]):
        return False
    data = blob[1:1 + data_len]
    if crc8(data[:-1]) != data[-1]:
        return False
    return verify_payload(data[:PAYLOAD_LEN], data[PAYLOAD_LEN:-1])


# ---------------------------------------------------------------- cli


def cmd_machine(argv):
    if len(argv) < 3:
        print("usage: machine <androidId> <manufacturer> <model>")
        return 1
    print(device_code(argv[0], argv[1], " ".join(argv[2:])))
    return 0


def cmd_issue(argv):
    if len(argv) < 1:
        print('usage: issue <device-code>\n'
              '  paste the 24-character machine code the customer copied from '
              'Settings > Activation.\n'
              '  paste the whole thing; dashes, spaces and lower case are fine.')
        return 1
    try:
        print(issue(argv[0]))
    except ValueError as e:
        print("error: %s" % e)
        print("no activation code was produced, nothing was charged or sent.")
        return 1
    return 0


def cmd_verify(argv):
    if len(argv) < 1:
        print("usage: verify <activation-code>")
        return 1
    ok = verify(argv[0])
    print("signature valid: %s" % ok)
    return 0 if ok else 1


def cmd_selftest():
    """Known-answer vector so the app can validate its own Java verifier on device."""
    fake = device_hash("QRSCANNER-SELFTEST", "SelfTestVendor", "SelfTestModel")
    dhash = fake[:DEVICE_HASH_LEN]
    payload = build_payload(dhash)
    sig = sign_payload(payload)
    data = payload + sig + bytes([crc8(payload + sig)])
    blob = bytes([len(data)]) + data
    blob += bytes(BLOB_BYTES - len(blob))
    code = group(b32encode(blob), CODE_GROUP)
    print("DEVICE_HASH_HEX = %s" % dhash.hex())
    print("SIGNATURE_HEX  = %s" % sig.hex())
    print("ACTIVATION     = %s" % code)
    print("CODE_CHARS     = %d" % len(ungroup(code)))
    print("self-verify    = %s" % verify(code))
    print("PUBLIC_KEY_B64 = %s" % load_public_b64())
    return 0


def main(argv):
    if len(argv) < 2:
        print(__doc__)
        return 1
    cmd, rest = argv[1], argv[2:]
    table = {
        "keygen": lambda: cmd_keygen(),
        "machine": lambda: cmd_machine(rest),
        "issue": lambda: cmd_issue(rest),
        "verify": lambda: cmd_verify(rest),
        "selftest": lambda: cmd_selftest(),
    }
    if cmd not in table:
        print(__doc__)
        return 1
    return table[cmd]()


if __name__ == "__main__":
    sys.exit(main(sys.argv))

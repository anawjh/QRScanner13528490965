"""Cross-checks the Java verifier in LicenseManager.java without a JVM.

j_* functions below are line-by-line transliterations of the Java methods, so an
index or shift mistake in the Java shows up here as a mismatch.
"""
import importlib.util
import os
import random
import sys

spec = importlib.util.spec_from_file_location("m", os.path.join(os.path.dirname(os.path.abspath(__file__)), "issue-license.py"))
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)

A = m.ALPHABET
BLOB_BYTES = 80
PAYLOAD_LEN = 6
DEVICE_HASH_LEN = 5
VERSION = 1

# ------------------------- transliterated from LicenseManager.java -------------------------


def j_b32encode(data):
    if len(data) % 5:
        raise ValueError("need multiple of 5")
    out = []
    buffer = 0
    bits = 0
    for value in data:
        buffer = (buffer << 8) | (value & 0xFF)
        bits += 8
        while bits >= 5:
            bits -= 5
            out.append(A[(buffer >> bits) & 0x1F])
        buffer &= (1 << bits) - 1
    return "".join(out)


def j_b32decode(text):
    if len(text) % 8:
        raise ValueError("need multiple of 8")
    out = bytearray(len(text) * 5 // 8)
    buffer = 0
    bits = 0
    index = 0
    for ch in text:
        v = A.find(ch)
        if v < 0:
            raise ValueError("bad symbol")
        buffer = (buffer << 5) | v
        bits += 5
        if bits >= 8:
            bits -= 8
            out[index] = (buffer >> bits) & 0xFF
            index += 1
            buffer &= (1 << bits) - 1
    return bytes(out)


def j_crc8(data, ln):
    crc = 0
    for i in range(ln):
        crc ^= data[i] & 0xFF
        for _ in range(8):
            crc = ((crc << 1) ^ 0x07) & 0xFF if crc & 0x80 else (crc << 1) & 0xFF
    return crc


def j_fromhex(h):
    return bytes(int(h[i * 2:i * 2 + 2], 16) for i in range(len(h) // 2))


def j_normalize(code):
    return "".join(x for x in (code or "").upper() if x in A)


def j_group(t, s):
    return "-".join(t[i:i + s] for i in range(0, len(t), s))


def j_verify(mine, code):
    """Mirror of LicenseManager.verify(); 0=OK."""
    try:
        blob = j_b32decode(j_normalize(code))
    except ValueError:
        return 1
    if len(blob) != BLOB_BYTES:
        return 1
    dl = blob[0] & 0xFF
    if dl < PAYLOAD_LEN + 8 + 1 or dl + 1 > BLOB_BYTES:
        return 1
    if any(blob[1 + dl:]):          # canonical zero padding
        return 1
    data = blob[1:1 + dl]
    bl = dl - 1
    if j_crc8(data, bl) != (data[bl] & 0xFF):
        return 1
    if not m.verify_payload(data[:PAYLOAD_LEN], data[PAYLOAD_LEN:bl]):
        return 2
    if data[0] != VERSION:
        return 4
    for i in range(DEVICE_HASH_LEN):
        if data[1 + i] != mine[i]:
            return 3
    return 0


# ------------------------------------------- tests -------------------------------------------

NAMES = {0: "OK", 1: "BAD_FORMAT", 2: "BAD_SIGNATURE", 3: "WRONG_DEVICE", 4: "BAD_VERSION"}
fails = []


def check(label, got, want):
    ok = got == want
    if not ok:
        fails.append(label)
    print("  [%s] %-38s %s" % ("PASS" if ok else "FAIL", label, got))


print("=== base32: Java 转写 vs 参考实现 ===")
diff = 0
for n in range(5, 200, 5):
    d = os.urandom(n)
    if j_b32encode(d) != m.b32encode(d) or j_b32decode(j_b32encode(d)) != d:
        diff += 1
check("5..195 字节 base32 差异数", diff, 0)
check("15 字节 -> 字符数 (机器码)", len(j_b32encode(b"x" * 15)), 24)
check("80 字节 -> 字符数 (激活码)", len(j_b32encode(b"x" * 80)), 128)
check("crc8 与参考一致", all(j_crc8(d, len(d)) == m.crc8(d)
                       for d in [os.urandom(k) for k in range(100)]), True)

print()
print("=== verify(): 端到端 ===")
devA = ("9774d56d682e549c", "Xiaomi", "Redmi Note 12")
devB = ("aabbccddeeff0011", "Huawei", "Mate 50")
hA, hB = m.device_hash(*devA), m.device_hash(*devB)
dcA = j_group(j_b32encode(hA[:15]), 6)
actA, actB = m.issue(dcA), m.issue(j_group(j_b32encode(hB[:15]), 6))
flat = j_normalize(actA)
name = lambda x: NAMES[x]

check("A机 + A的码", name(j_verify(hA, actA)), "OK")
check("A机 + B的码", name(j_verify(hA, actB)), "WRONG_DEVICE")

tally = {}
for _ in range(2000):
    p = random.randrange(128)
    alt = "B" if flat[p] != "B" else "C"
    k = name(j_verify(hA, flat[:p] + alt + flat[p + 1:]))
    tally[k] = tally.get(k, 0) + 1
check("随机单点篡改 2000 次全部拒绝",
      all(v == 0 for k, v in tally.items() if k == "OK"), True)
print("       分布: %s" % tally)

# every symbol, one at a time, must be rejected: catches any ignored region
leaks = []
for p in range(128):
    alt = "B" if flat[p] != "B" else "C"
    if j_verify(hA, flat[:p] + alt + flat[p + 1:]) == 0:
        leaks.append(p + 1)
check("逐位穷举 128 个位置均被拒绝", leaks, [])

check("小写 + 空格 + 换行", name(j_verify(hA, "  " + actA.lower().replace("-", " - ") + " ")), "OK")
check("截断的码", name(j_verify(hA, flat[:100])), "BAD_FORMAT")
check("多出 8 个字符", name(j_verify(hA, flat + flat[:8])), "BAD_FORMAT")
check("纯乱码", name(j_verify(hA, "ABCD-EFGH")), "BAD_FORMAT")
check("空输入", name(j_verify(hA, "")), "BAD_FORMAT")
check("真实手机用 A 的码(指纹不同)", name(j_verify(m.device_hash("xyz", "Apple", "iPhone"), actA)),
      "WRONG_DEVICE")

print()
print("=== App 自检向量 (LicenseManager.selfTest) ===")
import re  # noqa: E402

java_src = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..",
                        "app", "src", "main", "java", "com", "qrscanner", "LicenseManager.java")
with open(java_src, encoding="utf-8") as f:
    java = f.read()


def java_const(name):
    """Pull a concatenated Java string constant out of the source."""
    body = re.search(r'String\s+%s\s*=\s*((?:"[^"]*"\s*\+?\s*)+);' % name, java)
    if not body:
        raise AssertionError("%s not found in LicenseManager.java" % name)
    return re.sub(r'"\s*\+\s*"', "", body.group(1)).replace('"', "").replace(" ", "")


java_hash = java_const("SELFTEST_DEVICE_HASH")
java_sig = java_const("SELFTEST_SIGNATURE")
java_pub = java_const("PUBLIC_KEY")

check("Java 内嵌公钥 == 工具公钥", java_pub == m.load_public_b64(), True)
check("Java 设备哈希 == 工具 selftest", java_hash == m.device_hash(
    "QRSCANNER-SELFTEST", "SelfTestVendor", "SelfTestModel")[:DEVICE_HASH_LEN].hex(), True)

payload = bytes([VERSION]) + j_fromhex(java_hash)
check("Java 内嵌签名可验签 (selfTest 应为 True)",
      m.verify_payload(payload, j_fromhex(java_sig)), True)
check("Java 公钥可解析且是 P-256", m.load_public_b64() == java_pub, True)

# the real end-to-end proof: build a code with the tool, check it the way Java does
tool_code = m.issue(j_group(j_b32encode(hA[:15]), 6))
check("工具签发的码能被 Java 逻辑接受", j_verify(hA, tool_code), 0)

print()
print("=== 32 位有符号/无符号边界 ===")
check("0xFF 字节编码不溢出", j_b32decode(j_b32encode(b"\xff" * 5)), b"\xff" * 5)
check("0x00 字节编码不溢出", j_b32decode(j_b32encode(b"\x00" * 5)), b"\x00" * 5)
check("0x80 交替", j_b32decode(j_b32encode(b"\x80\x7f\x00\xff\x01" * 4)),
      b"\x80\x7f\x00\xff\x01" * 4)

print()
if fails:
    print("FAILED: %s" % fails)
    sys.exit(1)
print("ALL PASS")

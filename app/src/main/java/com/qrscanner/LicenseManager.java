package com.qrscanner;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
import android.util.Base64;

import java.nio.charset.Charset;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Locale;

/**
 * Offline activation. The activation code carries a real ECDSA signature issued by
 * the vendor, so a code can neither be forged nor reused on another handset: the
 * signed payload contains a hash of this device and is checked against it here.
 *
 * The matching issuing tool is tools/issue-license.py and must stay in sync with
 * this file. The private key never ships inside the app, only the public key below.
 */
public final class LicenseManager {

    /** Verification outcomes. */
    public static final int OK = 0;
    public static final int BAD_FORMAT = 1;
    public static final int BAD_SIGNATURE = 2;
    public static final int WRONG_DEVICE = 3;
    public static final int BAD_VERSION = 4;

    /** Free exports before activation is required. */
    public static final int FREE_TRIAL_EXPORTS = 100;

    private static final String PREF_NAME = "app_license";
    private static final String KEY_ACTIVATED = "activated";
    private static final String KEY_COUNT = "export_count";
    private static final String KEY_TEST_MODE = "dev_test_mode";

    /** 32 symbols, I/O/0/1 omitted so codes survive being read aloud. */
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final char[] ALPHABET_CHARS = ALPHABET.toCharArray();

    private static final int VERSION = 1;
    private static final int DEVICE_HASH_LEN = 5;
    private static final int PAYLOAD_LEN = 1 + DEVICE_HASH_LEN;
    private static final int MACHINE_BYTES = 15;
    private static final int MACHINE_GROUP = 6;
    private static final int CODE_GROUP = 16;

    /**
     * An activation code is always exactly 80 bytes -> 128 symbols. The fixed size
     * makes base32 an exact round trip: 128 symbols decode to exactly 80 bytes with
     * no trailing padding byte, so byte offsets below are stable.
     *
     *   byte 0        dataLen = bytes of payload+signature+crc that follow
     *   bytes 1..N    payload(6) + DER ECDSA signature(70..72) + crc8(1)
     *   rest          zero padding to 80 bytes
     */
    private static final int BLOB_BYTES = 80;
    private static final int CODE_CHARS = BLOB_BYTES * 8 / 5;

    private static final String PUBLIC_KEY =
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAET2WrgwgmmzbgDVi8GbTigYw1vBBl2S1Ngq7Naw7ay5cZsF"
            + "gWwinVgHOJQ8gZnU6r6mmjFgIweOzO883MJhZgPQ==";

    /** Known-answer vector, see tools/issue-license.py selftest. */
    private static final String SELFTEST_DEVICE_HASH =
        "3919839037";
    private static final String SELFTEST_SIGNATURE =
        "30450220763a5602c682d04bc1d5653e54ee926a48704a96bb10ec2c6b33ec55064a085e"
            + "022100a2a0387e465ae5fb737b58a60ff3c38287c81d308e93c9635f93e7241311676a";

    private LicenseManager() {}

    // ======================== 状态 ========================

    public static boolean isActivated(Context context) {
        return prefs(context).getBoolean(KEY_ACTIVATED, false);
    }

    public static int exportCount(Context context) {
        return prefs(context).getInt(KEY_COUNT, 0);
    }

    public static int remainingExports(Context context) {
        if (isActivated(context)) return -1;
        int left = FREE_TRIAL_EXPORTS - exportCount(context);
        return left < 0 ? 0 : left;
    }

    public static boolean isTestMode(Context context) {
        return prefs(context).getBoolean(KEY_TEST_MODE, false);
    }

    /**
     * Developer switch: pretending the trial is already used up, so the activation
     * prompt and the blocked export paths can be checked without 100 exports.
     */
    public static void setTestMode(Context context, boolean on) {
        SharedPreferences.Editor editor = prefs(context).edit();
        editor.putBoolean(KEY_TEST_MODE, on);
        if (on) editor.putInt(KEY_COUNT, FREE_TRIAL_EXPORTS);
        editor.apply();
    }

    public static void resetTrial(Context context) {
        prefs(context).edit().putInt(KEY_COUNT, 0).apply();
    }

    /** True while the trial still allows exporting. */
    public static boolean canExport(Context context) {
        if (isActivated(context)) return true;
        if (isTestMode(context)) return false;
        return exportCount(context) < FREE_TRIAL_EXPORTS;
    }

    /**
     * Counts one export and reports whether it may proceed. Exports 1..100 are free,
     * the 101st is refused.
     */
    public static boolean recordExport(Context context) {
        if (isActivated(context)) return true;
        if (isTestMode(context)) return false;
        int next = exportCount(context) + 1;
        prefs(context).edit().putInt(KEY_COUNT, next).apply();
        return next <= FREE_TRIAL_EXPORTS;
    }

    // ======================== 机器码 ========================

    public static String deviceCode(Context context) {
        byte[] digest = deviceDigest(context);
        byte[] slice = new byte[MACHINE_BYTES];
        System.arraycopy(digest, 0, slice, 0, MACHINE_BYTES);
        return group(b32Encode(slice), MACHINE_GROUP);
    }

    /**
     * ANDROID_ID survives a reinstall, so a code stays bound to the handset, while
     * the activation flag lives in app storage and is therefore cleared on uninstall.
     */
    private static String fingerprint(Context context) {
        String id = "";
        try {
            String value = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.ANDROID_ID);
            if (value != null) id = value;
        } catch (Exception ignored) {
        }
        return trim(id) + "|" + trim(Build.MANUFACTURER) + "|" + trim(Build.MODEL);
    }

    private static byte[] deviceDigest(Context context) {
        return sha256(fingerprint(context));
    }

    private static byte[] deviceHash(Context context) {
        byte[] digest = deviceDigest(context);
        byte[] out = new byte[DEVICE_HASH_LEN];
        System.arraycopy(digest, 0, out, 0, DEVICE_HASH_LEN);
        return out;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    // ======================== 激活 ========================

    public static int verify(Context context, String code) {
        byte[] blob;
        try {
            blob = b32Decode(normalize(code));
        } catch (RuntimeException e) {
            return BAD_FORMAT;
        }
        if (blob.length != BLOB_BYTES) return BAD_FORMAT;

        int dataLen = blob[0] & 0xFF;
        if (dataLen < PAYLOAD_LEN + 8 + 1 || dataLen + 1 > BLOB_BYTES) return BAD_FORMAT;

        // The tail is zero padding. Rejecting a non-canonical encoding keeps every
        // activation code down to exactly one valid spelling.
        for (int i = 1 + dataLen; i < BLOB_BYTES; i++) {
            if (blob[i] != 0) return BAD_FORMAT;
        }

        byte[] data = new byte[dataLen];
        System.arraycopy(blob, 1, data, 0, dataLen);

        int bodyLen = dataLen - 1;
        if (crc8(data, bodyLen) != (data[bodyLen] & 0xFF)) return BAD_FORMAT;

        byte[] payload = new byte[PAYLOAD_LEN];
        System.arraycopy(data, 0, payload, 0, PAYLOAD_LEN);
        byte[] sig = new byte[bodyLen - PAYLOAD_LEN];
        System.arraycopy(data, PAYLOAD_LEN, sig, 0, sig.length);

        if (!verifySignature(payload, sig)) return BAD_SIGNATURE;
        if ((payload[0] & 0xFF) != VERSION) return BAD_VERSION;

        byte[] mine = deviceHash(context);
        for (int i = 0; i < DEVICE_HASH_LEN; i++) {
            if (payload[1 + i] != mine[i]) return WRONG_DEVICE;
        }
        return OK;
    }

    public static boolean activate(Context context, String code) {
        if (verify(context, code) != OK) return false;
        SharedPreferences.Editor editor = prefs(context).edit();
        editor.putBoolean(KEY_ACTIVATED, true);
        editor.putInt(KEY_COUNT, 0);
        editor.putBoolean(KEY_TEST_MODE, false);
        editor.apply();
        return true;
    }

    /**
     * Checks the app's own crypto path against a vector signed by the vendor key.
     * Run it from the activation screen before trusting a rejected code.
     */
    public static boolean selfTest() {
        try {
            byte[] hash = fromHex(SELFTEST_DEVICE_HASH);
            byte[] sig = fromHex(SELFTEST_SIGNATURE);
            byte[] payload = new byte[PAYLOAD_LEN];
            payload[0] = (byte) VERSION;
            System.arraycopy(hash, 0, payload, 1, DEVICE_HASH_LEN);
            if (hash.length != DEVICE_HASH_LEN) return false;
            return verifySignature(payload, sig);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean verifySignature(byte[] payload, byte[] sig) {
        try {
            byte[] spki = Base64.decode(PUBLIC_KEY, Base64.DEFAULT);
            PublicKey key = KeyFactory.getInstance("EC")
                .generatePublic(new X509EncodedKeySpec(spki));
            Signature verifier = Signature.getInstance("SHA256withECDSA");
            verifier.initVerify(key);
            verifier.update(payload);
            return verifier.verify(sig);
        } catch (Exception e) {
            return false;
        }
    }

    // ======================== base32 ========================

    /** 5 bits per symbol, big-endian. The input length must be a multiple of 5. */
    private static String b32Encode(byte[] data) {
        if (data.length % 5 != 0) throw new IllegalArgumentException("need multiple of 5");
        StringBuilder out = new StringBuilder(data.length * 8 / 5);
        int buffer = 0;
        int bits = 0;
        for (byte value : data) {
            buffer = (buffer << 8) | (value & 0xFF);
            bits += 8;
            while (bits >= 5) {
                bits -= 5;
                out.append(ALPHABET_CHARS[(buffer >>> bits) & 0x1F]);
            }
            buffer &= (1 << bits) - 1;
        }
        return out.toString();
    }

    /** Inverse of b32Encode. The input length must be a multiple of 8. */
    private static byte[] b32Decode(String text) {
        if (text.length() % 8 != 0) throw new IllegalArgumentException("need multiple of 8");
        byte[] out = new byte[text.length() * 5 / 8];
        int buffer = 0;
        int bits = 0;
        int index = 0;
        for (int i = 0; i < text.length(); i++) {
            int value = ALPHABET.indexOf(text.charAt(i));
            if (value < 0) throw new IllegalArgumentException("bad symbol");
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8) {
                bits -= 8;
                out[index++] = (byte) ((buffer >>> bits) & 0xFF);
                buffer &= (1 << bits) - 1;
            }
        }
        return out;
    }

    private static String normalize(String code) {
        if (code == null) return "";
        StringBuilder sb = new StringBuilder();
        String upper = code.toUpperCase(Locale.US);
        for (int i = 0; i < upper.length(); i++) {
            char c = upper.charAt(i);
            if (ALPHABET.indexOf(c) >= 0) sb.append(c);
        }
        return sb.toString();
    }

    private static String group(String text, int size) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i += size) {
            if (i > 0) sb.append('-');
            sb.append(text, i, Math.min(i + size, text.length()));
        }
        return sb.toString();
    }

    // ======================== 工具 ========================

    private static int crc8(byte[] data, int len) {
        int crc = 0;
        for (int i = 0; i < len; i++) {
            crc ^= (data[i] & 0xFF);
            for (int bit = 0; bit < 8; bit++) {
                crc = ((crc & 0x80) != 0) ? (((crc << 1) ^ 0x07) & 0xFF) : ((crc << 1) & 0xFF);
            }
        }
        return crc;
    }

    private static byte[] fromHex(String hex) {
        int len = hex.length() / 2;
        byte[] out = new byte[len];
        for (int i = 0; i < len; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    private static byte[] sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(text.getBytes(Charset.forName("UTF-8")));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }
}

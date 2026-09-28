package com.qrscanner;

import android.graphics.Bitmap;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;

import java.util.EnumMap;
import java.util.Map;

public final class BarcodeFactory {

    public static final String[] BARCODE_FORMATS = {
        "CODE_128", "CODE_39", "CODE_93", "EAN_8", "EAN_13", "UPC_A", "UPC_E",
        "ITF", "CODABAR"
    };

    public static final String[] QR_FORMATS = {
        "QR_CODE", "DATA_MATRIX", "PDF_417", "AZTEC"
    };

    private BarcodeFactory() {}

    public static String[] formatsFor(boolean qr) {
        return qr ? QR_FORMATS : BARCODE_FORMATS;
    }

    public static boolean isSupported(String name) {
        for (String format : BARCODE_FORMATS) {
            if (format.equals(name)) return true;
        }
        for (String format : QR_FORMATS) {
            if (format.equals(name)) return true;
        }
        return false;
    }

    public static int defaultFormatRes(boolean qr) {
        return qr ? R.string.format_qr_code : R.string.format_code_128;
    }

    public static int labelRes(String name) {
        switch (name) {
            case "QR_CODE": return R.string.format_qr_code;
            case "CODE_39": return R.string.format_code_39;
            case "CODE_93": return R.string.format_code_93;
            case "CODE_128": return R.string.format_code_128;
            case "EAN_8": return R.string.format_ean_8;
            case "EAN_13": return R.string.format_ean_13;
            case "AZTEC": return R.string.format_aztec;
            case "CODABAR": return R.string.format_codabar;
            case "ITF": return R.string.format_itf;
            case "UPC_A": return R.string.format_upc_a;
            case "UPC_E": return R.string.format_upc_e;
            case "DATA_MATRIX": return R.string.format_data_matrix;
            case "PDF_417": return R.string.format_pdf_417;
            default: return R.string.format_unknown;
        }
    }

    public static int validate(String content, String format) {
        if (content == null || content.isEmpty()) return R.string.gen_err_empty;
        boolean digits = allDigits(content);
        switch (format) {
            case "EAN_8":
                if (!digits || content.length() != 8) return R.string.gen_err_ean8;
                return 0;
            case "EAN_13":
                if (!digits || content.length() != 13) return R.string.gen_err_ean13;
                return 0;
            case "UPC_A":
                if (!digits || content.length() != 12) return R.string.gen_err_upca;
                return 0;
            case "UPC_E":
                if (!digits || content.length() != 8) return R.string.gen_err_upce;
                return 0;
            case "ITF":
                if (!digits || content.length() < 2 || content.length() % 2 != 0) {
                    return R.string.gen_err_itf;
                }
                return 0;
            case "CODE_39":
                return matchesCode39(content) ? 0 : R.string.gen_err_code39;
            default:
                return 0;
        }
    }

    private static boolean allDigits(String content) {
        for (int i = 0; i < content.length(); i++) {
            if (!Character.isDigit(content.charAt(i))) return false;
        }
        return true;
    }

    private static boolean matchesCode39(String content) {
        String allowed = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-. $/+%";
        for (int i = 0; i < content.length(); i++) {
            if (allowed.indexOf(content.charAt(i)) < 0) return false;
        }
        return true;
    }

    public static Bitmap create(String content, String format, int width, int height)
            throws WriterException {
        BarcodeFormat barcodeFormat = BarcodeFormat.valueOf(format);

        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, 1);

        BitMatrix matrix = new MultiFormatWriter().encode(content, barcodeFormat, width, height, hints);
        return toBitmap(matrix);
    }

    public static Bitmap toBitmap(BitMatrix matrix) {
        int width = matrix.getWidth();
        int height = matrix.getHeight();
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            int offset = y * width;
            for (int x = 0; x < width; x++) {
                pixels[offset + x] = matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF;
            }
        }
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        return bitmap;
    }

    public static Bitmap scale(Bitmap source, int targetWidth) {
        int width = Math.max(1, targetWidth);
        int height = Math.max(1,
            (int) (source.getHeight() * (width / (float) source.getWidth())));
        return Bitmap.createScaledBitmap(source, width, height, true);
    }
}

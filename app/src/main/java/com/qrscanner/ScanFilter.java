package com.qrscanner;

import android.content.Context;

import com.google.zxing.BarcodeFormat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public final class ScanFilter {

    public static final String[] ALL_FORMATS = {
        "QR_CODE", "CODE_39", "CODE_93", "CODE_128", "EAN_8", "EAN_13", "AZTEC",
        "CODABAR", "ITF", "UPC_A", "UPC_E", "DATA_MATRIX", "PDF_417"
    };

    private ScanFilter() {}

    public static List<String> enabledFormats(Context context) {
        Set<String> selected = ScanSettings.getFormats(context);
        if (selected.isEmpty()) return Arrays.asList(ALL_FORMATS);
        List<String> result = new ArrayList<>();
        for (String name : ALL_FORMATS) {
            if (selected.contains(name)) result.add(name);
        }
        return result;
    }

    public static List<BarcodeFormat> enabledBarcodeFormats(Context context) {
        List<BarcodeFormat> result = new ArrayList<>();
        for (String name : enabledFormats(context)) {
            result.add(BarcodeFormat.valueOf(name));
        }
        return result;
    }

    public static boolean matches(Context context, String content, String format) {
        if (content == null || content.isEmpty()) return false;
        String trimmed = content.trim();
        if (trimmed.isEmpty()) return false;

        if (ScanSettings.isAlphaOnly(context)) {
            char first = trimmed.charAt(0);
            if (!((first >= 'A' && first <= 'Z') || (first >= 'a' && first <= 'z'))) {
                return false;
            }
        }

        if (ScanSettings.isDigitOnly(context)) {
            char first = trimmed.charAt(0);
            if (first < '0' || first > '9') return false;
        }

        int fixedLength = ScanSettings.getFixedLength(context);
        if (fixedLength > 0 && trimmed.length() != fixedLength) return false;

        Set<String> selected = ScanSettings.getFormats(context);
        if (!selected.isEmpty() && (format == null || !selected.contains(format))) {
            return false;
        }
        return true;
    }

    public static boolean hasContentFilter(Context context) {
        return ScanSettings.isAlphaOnly(context)
            || ScanSettings.isDigitOnly(context)
            || ScanSettings.getFixedLength(context) > 0
            || !ScanSettings.getFormats(context).isEmpty();
    }
}

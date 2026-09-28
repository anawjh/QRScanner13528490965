package com.qrscanner;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.ScanOptions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class ScanMode {

    private static final String PREF_NAME = "scan_settings";
    private static final String KEY_MODE = "scan_mode";

    public static final String MODE_ALL = "all";
    public static final String MODE_QR = "qr";
    public static final String MODE_BARCODE = "barcode";

    public static final String[] MODES = {MODE_ALL, MODE_QR, MODE_BARCODE};

    private static final List<String> QR_FORMATS =
        Collections.singletonList(ScanOptions.QR_CODE);

    private static final List<String> BARCODE_FORMATS = Collections.unmodifiableList(Arrays.asList(
        ScanOptions.EAN_13,
        ScanOptions.EAN_8,
        ScanOptions.UPC_A,
        ScanOptions.UPC_E,
        ScanOptions.CODE_128,
        ScanOptions.CODE_39,
        ScanOptions.CODE_93,
        ScanOptions.ITF,
        BarcodeFormat.CODABAR.name()));

    private ScanMode() {}

    public static String getMode(Context context) {
        return prefs(context).getString(KEY_MODE, MODE_ALL);
    }

    public static void setMode(Context context, String mode) {
        prefs(context).edit().putString(KEY_MODE, mode).apply();
    }

    public static List<String> formats(String mode) {
        if (MODE_QR.equals(mode)) return QR_FORMATS;
        if (MODE_BARCODE.equals(mode)) return BARCODE_FORMATS;
        List<String> all = new ArrayList<>(QR_FORMATS);
        all.addAll(BARCODE_FORMATS);
        return Collections.unmodifiableList(all);
    }

    public static List<BarcodeFormat> barcodeFormats(String mode) {
        List<BarcodeFormat> result = new ArrayList<>();
        for (String name : formats(mode)) {
            result.add(BarcodeFormat.valueOf(name));
        }
        return result;
    }

    public static int labelRes(String mode) {
        if (MODE_QR.equals(mode)) return R.string.scan_mode_qr;
        if (MODE_BARCODE.equals(mode)) return R.string.scan_mode_barcode;
        return R.string.scan_mode_all;
    }

    public static int menuRes(String mode) {
        if (MODE_QR.equals(mode)) return R.string.menu_scan_mode_qr;
        if (MODE_BARCODE.equals(mode)) return R.string.menu_scan_mode_barcode;
        return R.string.menu_scan_mode_all;
    }

    public static int promptRes(String mode) {
        if (MODE_QR.equals(mode)) return R.string.hint_scan;
        if (MODE_BARCODE.equals(mode)) return R.string.hint_scan_barcode;
        return R.string.hint_scan_all;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }
}

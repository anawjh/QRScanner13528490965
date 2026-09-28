package com.qrscanner;

import android.util.Size;

import com.journeyapps.barcodescanner.CaptureActivity;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;

public class BarcodeCaptureActivity extends CaptureActivity {

    private static final float WIDTH_RATIO = 0.92f;
    private static final float BARCODE_HEIGHT_RATIO = 0.38f;
    private static final float ALL_HEIGHT_RATIO = 0.60f;

    @Override
    protected DecoratedBarcodeView initializeContent() {
        DecoratedBarcodeView view = super.initializeContent();

        String mode = ScanMode.getMode(this);
        if (ScanMode.MODE_QR.equals(mode)) {
            return view;
        }

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int width = (int) (screenWidth * WIDTH_RATIO);
        int height = (int) (screenWidth * (ScanMode.MODE_BARCODE.equals(mode)
            ? BARCODE_HEIGHT_RATIO
            : ALL_HEIGHT_RATIO));
        view.getBarcodeView().setFramingRectSize(new Size(width, height));
        return view;
    }
}

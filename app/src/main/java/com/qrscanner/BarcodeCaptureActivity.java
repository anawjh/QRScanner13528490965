package com.qrscanner;

import com.journeyapps.barcodescanner.CaptureActivity;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;
import com.journeyapps.barcodescanner.Size;

public class BarcodeCaptureActivity extends CaptureActivity {

    private static final float WIDTH_RATIO = 0.92f;
    private static final float HEIGHT_RATIO = 0.40f;

    @Override
    protected DecoratedBarcodeView initializeContent() {
        DecoratedBarcodeView view = super.initializeContent();
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int width = (int) (screenWidth * WIDTH_RATIO);
        int height = (int) (screenWidth * HEIGHT_RATIO);
        view.getBarcodeView().setFramingRectSize(new Size(width, height));
        return view;
    }
}

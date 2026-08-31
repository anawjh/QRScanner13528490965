package com.qrscanner;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;
import com.journeyapps.barcodescanner.DefaultDecoderFactory;
import com.google.zxing.BarcodeFormat;

import java.util.Arrays;
import java.util.List;

public class CameraScanActivity extends AppCompatActivity {

    public static final String EXTRA_SCAN_RESULT = "scan_result";
    public static final String EXTRA_SCAN_COUNT = "scan_count";

    private DecoratedBarcodeView barcodeScanner;
    private ImageButton btnToggleFlash;
    private TextView tvFlashMode;
    private TextView tvScanHint;

    private String flashMode;
    private boolean isTorchOn = false;
    private int scanCount = 0;

    private final BarcodeCallback barcodeCallback = new BarcodeCallback() {
        @Override
        public void barcodeResult(BarcodeResult result) {
            if (result.getText() == null || result.getText().isEmpty()) {
                return;
            }

            String scanData = result.getText().trim();
            scanCount++;
            tvScanHint.setText(getString(R.string.scan_count, scanCount));

            if (FlashSettingsActivity.MODE_ON_SCAN.equals(flashMode)) {
                turnOffTorch();
            }

            Intent resultIntent = new Intent();
            resultIntent.putExtra(EXTRA_SCAN_RESULT, scanData);
            resultIntent.putExtra(EXTRA_SCAN_COUNT, scanCount);
            setResult(RESULT_OK, resultIntent);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_scan);

        barcodeScanner = findViewById(R.id.barcodeScanner);
        btnToggleFlash = findViewById(R.id.btnToggleFlash);
        tvFlashMode = findViewById(R.id.tvFlashMode);
        tvScanHint = findViewById(R.id.tvScanHint);
        Button btnBack = findViewById(R.id.btnBack);

        List<BarcodeFormat> formats = Arrays.asList(
            BarcodeFormat.QR_CODE,
            BarcodeFormat.CODE_128,
            BarcodeFormat.CODE_39,
            BarcodeFormat.EAN_13,
            BarcodeFormat.EAN_8,
            BarcodeFormat.UPC_A,
            BarcodeFormat.UPC_E
        );
        barcodeScanner.getBarcodeView().setDecoderFactory(new DefaultDecoderFactory(formats));

        btnBack.setOnClickListener(v -> {
            Intent resultIntent = new Intent();
            resultIntent.putExtra(EXTRA_SCAN_COUNT, scanCount);
            setResult(RESULT_CANCELED, resultIntent);
            finish();
        });

        btnToggleFlash.setOnClickListener(v -> toggleTorch());

        loadFlashMode();
        startScanning();
    }

    private void startScanning() {
        barcodeScanner.decodeContinuous(barcodeCallback);

        if (FlashSettingsActivity.MODE_ALWAYS_ON.equals(flashMode) ||
                FlashSettingsActivity.MODE_ON_SCAN.equals(flashMode)) {
            turnOnTorch();
        }

        updateFlashUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        barcodeScanner.resume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        barcodeScanner.pause();
        if (isTorchOn) {
            turnOffTorch();
        }
    }

    private void loadFlashMode() {
        SharedPreferences prefs = getSharedPreferences(
                FlashSettingsActivity.PREF_NAME, MODE_PRIVATE);
        flashMode = FlashSettingsActivity.getFlashMode(prefs);

        switch (flashMode) {
            case FlashSettingsActivity.MODE_ALWAYS_ON:
                tvFlashMode.setText(getString(R.string.flash_status_on));
                tvFlashMode.setVisibility(TextView.VISIBLE);
                btnToggleFlash.setVisibility(ImageButton.VISIBLE);
                break;
            case FlashSettingsActivity.MODE_ON_SCAN:
                tvFlashMode.setText(getString(R.string.flash_status_scan));
                tvFlashMode.setVisibility(TextView.VISIBLE);
                btnToggleFlash.setVisibility(ImageButton.VISIBLE);
                break;
            case FlashSettingsActivity.MODE_OFF:
            default:
                tvFlashMode.setVisibility(TextView.GONE);
                btnToggleFlash.setVisibility(ImageButton.GONE);
                break;
        }
    }

    private void toggleTorch() {
        if (isTorchOn) {
            turnOffTorch();
        } else {
            turnOnTorch();
        }
        updateFlashUI();
    }

    private void turnOnTorch() {
        try {
            barcodeScanner.setTorchOn();
            isTorchOn = true;
        } catch (Exception e) {
            isTorchOn = false;
        }
    }

    private void turnOffTorch() {
        try {
            barcodeScanner.setTorchOff();
            isTorchOn = false;
        } catch (Exception e) {
            isTorchOn = false;
        }
    }

    private void updateFlashUI() {
        if (isTorchOn) {
            btnToggleFlash.setImageResource(android.R.drawable.ic_lock_silent_mode_off);
        } else {
            btnToggleFlash.setImageResource(android.R.drawable.ic_lock_silent_mode);
        }
    }
}

package com.qrscanner;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;
import com.journeyapps.barcodescanner.DefaultDecoderFactory;

import java.util.Collections;

public class CameraScanActivity extends AppCompatActivity {

    public static final String EXTRA_SCAN_RESULT = "scan_result";

    private DecoratedBarcodeView barcodeScanner;
    private ImageButton btnToggleFlash;
    private TextView tvFlashMode;
    private TextView tvScanHint;

    private String flashMode;
    private boolean isTorchOn = false;

    private final BarcodeCallback barcodeCallback = new BarcodeCallback() {
        @Override
        public void barcodeResult(BarcodeResult result) {
            if (result.getText() == null || result.getText().isEmpty()) {
                return;
            }

            String scanData = result.getText().trim();

            if (flashMode.equals(FlashSettingsActivity.MODE_ON_SCAN)) {
                turnOffTorch();
            }

            Intent resultIntent = new Intent();
            resultIntent.putExtra(EXTRA_SCAN_RESULT, scanData);
            setResult(RESULT_OK, resultIntent);
            finish();
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

        barcodeScanner.getBarcodeView().setDecoderFactory(
                new DefaultDecoderFactory(Collections.emptyList()));

        btnBack.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });

        btnToggleFlash.setOnClickListener(v -> toggleTorch());

        loadFlashMode();
    }

    @Override
    protected void onResume() {
        super.onResume();
        barcodeScanner.decodeContinuous(barcodeCallback);

        if (flashMode.equals(FlashSettingsActivity.MODE_ALWAYS_ON) ||
                flashMode.equals(FlashSettingsActivity.MODE_ON_SCAN)) {
            turnOnTorch();
        }

        updateFlashUI();
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
                tvFlashMode.setText("闪光灯：常亮");
                tvFlashMode.setVisibility(View.VISIBLE);
                btnToggleFlash.setVisibility(View.VISIBLE);
                break;
            case FlashSettingsActivity.MODE_ON_SCAN:
                tvFlashMode.setText("闪光灯：扫码时亮");
                tvFlashMode.setVisibility(View.VISIBLE);
                btnToggleFlash.setVisibility(View.VISIBLE);
                break;
            case FlashSettingsActivity.MODE_OFF:
            default:
                tvFlashMode.setVisibility(View.GONE);
                btnToggleFlash.setVisibility(View.GONE);
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

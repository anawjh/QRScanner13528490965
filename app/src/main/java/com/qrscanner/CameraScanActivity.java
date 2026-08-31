package com.qrscanner;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;
import com.journeyapps.barcodescanner.DefaultDecoderFactory;
import com.google.zxing.BarcodeFormat;

import java.util.Arrays;
import java.util.List;

public class CameraScanActivity extends AppCompatActivity {

    public static final String EXTRA_SCAN_RESULT = "scan_result";
    private static final int REQ_CAMERA = 200;

    private DecoratedBarcodeView barcodeScanner;
    private ImageButton btnToggleFlash;
    private TextView tvFlashMode;

    private String flashMode;
    private boolean isTorchOn = false;
    private boolean isDecoding = false;

    private final BarcodeCallback barcodeCallback = new BarcodeCallback() {
        @Override
        public void barcodeResult(BarcodeResult result) {
            if (result.getText() == null || result.getText().isEmpty()) {
                return;
            }

            String scanData = result.getText().trim();

            if (FlashSettingsActivity.MODE_ON_SCAN.equals(flashMode)) {
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
        TextView tvScanHint = findViewById(R.id.tvScanHint);
        Button btnBack = findViewById(R.id.btnBack);

        List<BarcodeFormat> formats = Arrays.asList(
            BarcodeFormat.QR_CODE,
            BarcodeFormat.CODE_128,
            BarcodeFormat.CODE_39,
            BarcodeFormat.EAN_13,
            BarcodeFormat.EAN_8,
            BarcodeFormat.UPC_A,
            BarcodeFormat.UPC_E,
            BarcodeFormat.DATA_MATRIX,
            BarcodeFormat.ITF
        );
        barcodeScanner.getBarcodeView().setDecoderFactory(new DefaultDecoderFactory(formats));

        btnBack.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });

        btnToggleFlash.setOnClickListener(v -> toggleTorch());

        loadFlashMode();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startScanning();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScanning();
            } else {
                Toast.makeText(this, getString(R.string.camera_permission_denied),
                        Toast.LENGTH_LONG).show();
                setResult(RESULT_CANCELED);
                finish();
            }
        }
    }

    private void startScanning() {
        if (!isDecoding) {
            barcodeScanner.decodeContinuous(barcodeCallback);
            isDecoding = true;
        }

        if (FlashSettingsActivity.MODE_ALWAYS_ON.equals(flashMode) ||
                FlashSettingsActivity.MODE_ON_SCAN.equals(flashMode)) {
            turnOnTorch();
        }

        updateFlashUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startScanning();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        barcodeScanner.pause();
        isDecoding = false;
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
                tvFlashMode.setVisibility(View.VISIBLE);
                btnToggleFlash.setVisibility(View.VISIBLE);
                break;
            case FlashSettingsActivity.MODE_ON_SCAN:
                tvFlashMode.setText(getString(R.string.flash_status_scan));
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

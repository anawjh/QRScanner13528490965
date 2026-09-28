package com.qrscanner;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ScanActivity extends AppCompatActivity {

    private static final int REQ_CAMERA = 200;
    private static final long DEDUPE_MS = 1500L;

    private DecoratedBarcodeView barcodeScanner;
    private TextView tvCounter;
    private TextView tvStatus;
    private TextView tvBanner;
    private ImageButton btnFlash;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, Long> lastSeen = new HashMap<>();
    private ProjectManager projectManager;
    private ScanProject currentProject;
    private boolean isTorchOn = false;
    private int filteredCount = 0;

    private final BarcodeCallback callback = new BarcodeCallback() {
        @Override
        public void barcodeResult(BarcodeResult result) {
            if (result.getText() == null || result.getText().isEmpty()) return;
            handleResult(result);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);

        ScanSpeaker.init(this);

        barcodeScanner = findViewById(R.id.barcodeScanner);
        tvCounter = findViewById(R.id.tvCounter);
        tvStatus = findViewById(R.id.tvStatus);
        tvBanner = findViewById(R.id.tvBanner);
        btnFlash = findViewById(R.id.btnFlash);
        Button btnClose = findViewById(R.id.btnClose);

        projectManager = ProjectManager.getInstance(this);
        currentProject = projectManager.getCurrent();

        btnClose.setOnClickListener(v -> finish());
        btnFlash.setOnClickListener(v -> toggleTorch());

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
                Toast.makeText(this, R.string.camera_permission_denied, Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            barcodeScanner.resume();
            barcodeScanner.decodeContinuous(callback);
            applyFlashMode();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacksAndMessages(null);
        barcodeScanner.pause();
        if (isTorchOn) turnOffTorch();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing()) {
            ScanSpeaker.shutdown();
        }
    }

    private void startScanning() {
        barcodeScanner.getBarcodeView()
            .setDecoderFactory(new ImageCaptureDecoderFactory(
                ScanFilter.enabledBarcodeFormats(this)));
        updateCounter();
        applyFlashMode();
    }

    // ======================== 扫码处理 ========================

    private void handleResult(BarcodeResult result) {
        String content = result.getText().trim();
        String format = result.getBarcodeFormat().name();

        Long seen = lastSeen.get(content);
        long now = System.currentTimeMillis();
        if (seen != null && now - seen < DEDUPE_MS) return;
        lastSeen.put(content, now);

        Bitmap bitmap = ImageCaptureDecoderFactory.consumeBitmap();
        ImageCaptureDecoderFactory.consumeFormat();

        if (Blacklist.contains(this, content)) {
            ScanSpeaker.alertBlocked(this);
            showBanner(content);
            pauseForBlacklist(content, format, bitmap);
            return;
        }

        if (!ScanFilter.matches(this, content, format)) {
            filteredCount++;
            showStatus(getString(R.string.scan_filtered, filteredCount));
            return;
        }

        addRecord(content, format, false, savePhoto(bitmap));
        ScanSpeaker.alertSuccess(this);
        showBanner(null);
        scheduleNext();
    }

    private void pauseForBlacklist(String content, String format, Bitmap bitmap) {
        barcodeScanner.pause();

        final String savedAction = Blacklist.getAction(this);
        boolean[] choices = {
            Blacklist.ACTION_SKIP.equals(savedAction),
            Blacklist.ACTION_KEEP.equals(savedAction)
        };

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);

        RadioGroup group = new RadioGroup(this);
        RadioButton skip = radio(R.string.blacklist_action_skip, choices[0]);
        RadioButton keep = radio(R.string.blacklist_action_keep, choices[1]);
        group.addView(skip);
        group.addView(keep);
        group.setOnCheckedChangeListener((g, id) -> {
            choices[0] = id == skip.getId();
            choices[1] = id == keep.getId();
        });
        box.addView(group);

        CheckBox remember = new CheckBox(this);
        remember.setText(R.string.blacklist_remember);
        remember.setChecked(false);
        box.addView(remember);

        new AlertDialog.Builder(this)
            .setTitle(R.string.blacklist_hit_title)
            .setMessage(content)
            .setView(box)
            .setPositiveButton(R.string.confirm, (d, w) -> {
                boolean keep = choices[1];
                if (remember.isChecked()) {
                    Blacklist.setAction(this, keep
                        ? Blacklist.ACTION_KEEP
                        : Blacklist.ACTION_SKIP);
                }
                if (keep) {
                    addRecord(content, format, true, savePhoto(bitmap));
                }
                showBanner(null);
                scheduleNext();
            })
            .setCancelable(false)
            .show();
    }

    private RadioButton radio(int textRes, boolean checked) {
        RadioButton button = new RadioButton(this);
        button.setText(textRes);
        button.setChecked(checked);
        return button;
    }

    private void addRecord(String content, String format, boolean blocked, String imagePath) {
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(new Date());
        currentProject.records.add(0,
            new ScanRecord(currentProject.records.size() + 1, content, time, "", format, blocked, imagePath));
        resequence();
        projectManager.save(currentProject);
        updateCounter();
    }

    private void resequence() {
        List<ScanRecord> records = currentProject.records;
        for (int i = 0; i < records.size(); i++) {
            records.get(i).setSeq(records.size() - i);
        }
    }

    private String savePhoto(Bitmap bitmap) {
        if (bitmap == null || !ScanSettings.isSavePhoto(this)) return "";
        try {
            File pictures = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            File dir = new File(pictures != null ? pictures : getFilesDir(), "scans");
            if (!dir.exists() && !dir.mkdirs()) return "";
            String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault())
                .format(new Date());
            File file = new File(dir, stamp + ".jpg");
            try (FileOutputStream out = new FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
            }
            return file.getAbsolutePath();
        } catch (Exception e) {
            return "";
        }
    }

    // ======================== 间隔与提示 ========================

    private void scheduleNext() {
        if (!ScanSettings.isContinuousEnabled(this)) {
            showStatus(getString(R.string.scan_stopped));
            return;
        }
        int interval = ScanSettings.getIntervalMs(this);
        showStatus(getString(R.string.scan_next_in, interval / 1000f));
        barcodeScanner.pause();
        handler.postDelayed(() -> {
            if (isFinishing() || isDestroyed()) return;
            barcodeScanner.resume();
            barcodeScanner.decodeContinuous(callback);
            showStatus(getString(R.string.scan_ready));
        }, interval);
    }

    private void showStatus(String text) {
        tvStatus.setText(text);
    }

    private void showBanner(String content) {
        if (content == null) {
            tvBanner.setVisibility(View.GONE);
        } else {
            tvBanner.setVisibility(View.VISIBLE);
            tvBanner.setText(getString(R.string.blacklist_hit_banner, content));
        }
    }

    private void updateCounter() {
        tvCounter.setText(getString(R.string.scan_counter, currentProject.records.size()));
    }

    // ======================== 闪光灯 ========================

    private void applyFlashMode() {
        String mode = FlashSettingsActivity.getFlashMode(
            getSharedPreferences(FlashSettingsActivity.PREF_NAME, MODE_PRIVATE));
        btnFlash.setVisibility(View.VISIBLE);
        if (FlashSettingsActivity.MODE_ALWAYS_ON.equals(mode)
                || FlashSettingsActivity.MODE_ON_SCAN.equals(mode)) {
            turnOnTorch();
        }
    }

    private void toggleTorch() {
        if (isTorchOn) turnOffTorch(); else turnOnTorch();
    }

    private void turnOnTorch() {
        try {
            barcodeScanner.setTorchOn();
            isTorchOn = true;
            btnFlash.setImageResource(R.drawable.ic_flash_on);
        } catch (Exception e) {
            isTorchOn = false;
        }
    }

    private void turnOffTorch() {
        try {
            barcodeScanner.setTorchOff();
            isTorchOn = false;
            btnFlash.setImageResource(R.drawable.ic_flash_off);
        } catch (Exception e) {
            isTorchOn = false;
        }
    }
}

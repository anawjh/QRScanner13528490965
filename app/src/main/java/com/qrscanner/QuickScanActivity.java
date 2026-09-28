package com.qrscanner;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class QuickScanActivity extends AppCompatActivity
        implements ScanRecordAdapter.OnDeleteListener, ScanRecordAdapter.OnEditRemarkListener,
        ScanRecordAdapter.OnPhotoClickListener {

    private static final int REQ_CAMERA = 210;
    private static final long DEDUPE_MS = 1500L;

    private DecoratedBarcodeView barcodeScanner;
    private RecyclerView rvRecords;
    private TextView tvCounter;
    private TextView tvStatus;
    private TextView tvEmpty;
    private ImageButton btnFlash;
    private TextView tvPhotoToggle;
    private ScanRecordAdapter adapter;

    private final Map<String, Long> lastSeen = new HashMap<>();
    private ProjectManager projectManager;
    private ScanProject currentProject;
    private boolean isTorchOn = false;

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
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_quick_scan);

        ScanSpeaker.init(this);

        barcodeScanner = findViewById(R.id.barcodeScanner);
        rvRecords = findViewById(R.id.rvRecords);
        tvCounter = findViewById(R.id.tvCounter);
        tvStatus = findViewById(R.id.tvStatus);
        tvEmpty = findViewById(R.id.tvEmpty);
        btnFlash = findViewById(R.id.btnFlash);
        tvPhotoToggle = findViewById(R.id.tvPhotoToggle);
        Button btnClose = findViewById(R.id.btnClose);

        projectManager = ProjectManager.getInstance(this);
        currentProject = projectManager.getCurrent();

        adapter = new ScanRecordAdapter(currentProject.records, this, this, this);
        rvRecords.setLayoutManager(new LinearLayoutManager(this));
        rvRecords.setAdapter(adapter);

        btnClose.setOnClickListener(v -> finish());
        btnFlash.setOnClickListener(v -> toggleTorch());
        tvPhotoToggle.setOnClickListener(v -> togglePhotoSave());
        updatePhotoToggle();

        refreshList();

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
        currentProject = projectManager.getCurrent();
        adapter.setRecords(currentProject.records);
        refreshList();
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
        barcodeScanner.pause();
        if (isTorchOn) turnOffTorch();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing()) ScanSpeaker.shutdown();
    }

    private void startScanning() {
        barcodeScanner.getBarcodeView()
            .setDecoderFactory(new ImageCaptureDecoderFactory(
                ScanFilter.enabledBarcodeFormats(this)));
        applyFlashMode();
    }

    // ======================== 扫码 ========================

    private void handleResult(BarcodeResult result) {
        String content = result.getText().trim();
        String format = result.getBarcodeFormat().name();

        Long seen = lastSeen.get(content);
        long now = System.currentTimeMillis();
        if (seen != null && now - seen < DEDUPE_MS) return;
        lastSeen.put(content, now);

        Bitmap bitmap = ImageCaptureDecoderFactory.consumeBitmap();
        ImageCaptureDecoderFactory.consumeFormat();

        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(new Date());
        currentProject.records.add(0, new ScanRecord(
            currentProject.records.size() + 1, content, time, "", format, false,
            savePhoto(bitmap)));
        resequence();
        projectManager.save(currentProject);

        ScanSpeaker.alertSuccess(this);
        showStatus(content);
        refreshList();
        rvRecords.scrollToPosition(0);
    }

    private void resequence() {
        List<ScanRecord> records = currentProject.records;
        for (int i = 0; i < records.size(); i++) {
            records.get(i).setSeq(records.size() - i);
        }
    }

    private String savePhoto(Bitmap bitmap) {
        if (bitmap == null || !ScanSettings.isSavePhoto(this)) return "";
        return ScanPhotoStore.save(this, bitmap);
    }

    // ======================== 照片保存开关 ========================

    private void updatePhotoToggle() {
        tvPhotoToggle.setText(ScanSettings.isSavePhoto(this)
            ? R.string.photo_save_on : R.string.photo_save_off);
    }

    private void togglePhotoSave() {
        boolean next = !ScanSettings.isSavePhoto(this);
        ScanSettings.setSavePhoto(this, next);
        updatePhotoToggle();
        Toast.makeText(this,
            next ? R.string.photo_save_on : R.string.photo_save_off, Toast.LENGTH_SHORT).show();
    }

    // ======================== 列表 ========================

    private void refreshList() {
        int size = currentProject.records.size();
        tvCounter.setText(getString(R.string.scan_counter, size));
        tvEmpty.setVisibility(size == 0 ? View.VISIBLE : View.GONE);
        tvEmpty.setText(R.string.quick_scan_empty);
        adapter.notifyDataSetChanged();
    }

    private void showStatus(String content) {
        tvStatus.setText(content);
    }

    @Override
    public void onDelete(int position) {
        if (position < 0 || position >= currentProject.records.size()) return;
        currentProject.records.remove(position);
        resequence();
        projectManager.save(currentProject);
        refreshList();
    }

    @Override
    public void onEdit(int position) {
        if (position < 0 || position >= currentProject.records.size()) return;
        final ScanRecord record = currentProject.records.get(position);
        EditText input = new EditText(this);
        input.setText(record.getRemark() == null ? "" : record.getRemark());
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad / 2, pad, pad / 2);
        new AlertDialog.Builder(this)
            .setTitle(R.string.edit_remark_title)
            .setView(input)
            .setPositiveButton(R.string.confirm, (d, w) -> {
                record.setRemark(input.getText().toString().trim());
                projectManager.save(currentProject);
                refreshList();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    // ======================== 照片 ========================

    @Override
    public void onPhotoClick(String path) {
        startActivity(PhotoViewerActivity.intent(this, path));
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

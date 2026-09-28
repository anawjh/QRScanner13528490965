package com.qrscanner;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import java.util.ArrayList;
import java.util.List;

public class BlacklistActivity extends AppCompatActivity {

    private EditText input;
    private TextView tvCounter;
    private Button btnSave;

    private final ActivityResultLauncher<ScanOptions> scanLauncher =
        registerForActivityResult(new ScanContract(), result -> {
            if (result.getContents() == null || result.getContents().isEmpty()) return;
            String value = result.getContents().trim();
            if (value.isEmpty()) return;
            List<String> lines = parseLines();
            if (lines.size() >= Blacklist.MAX_LINES) {
                Toast.makeText(this,
                    getString(R.string.blacklist_full, Blacklist.MAX_LINES),
                    Toast.LENGTH_LONG).show();
                return;
            }
            if (lines.contains(value)) {
                Toast.makeText(this, R.string.blacklist_exists, Toast.LENGTH_SHORT).show();
                return;
            }
            input.append(input.getText().length() > 0 ? "\n" + value : value);
        });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blacklist);

        input = findViewById(R.id.etBlacklist);
        tvCounter = findViewById(R.id.tvCounter);
        btnSave = findViewById(R.id.btnSave);
        Button btnScan = findViewById(R.id.btnScan);
        Button btnClear = findViewById(R.id.btnClear);
        Button btnBack = findViewById(R.id.btnBack);

        input.setText(String.join("\n", Blacklist.lines(this)));
        updateCounter(input.getText().toString());

        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                updateCounter(s.toString());
            }
        });

        btnBack.setOnClickListener(v -> finish());
        btnScan.setOnClickListener(v -> startScan());
        btnClear.setOnClickListener(v -> input.setText(""));
        btnSave.setOnClickListener(v -> save());
    }

    private void startScan() {
        ScanOptions options = new ScanOptions();
        options.setDesiredBarcodeFormats(ScanFilter.enabledFormats(this));
        options.setPrompt(getString(R.string.blacklist_scan_prompt));
        options.setBeepEnabled(false);
        options.setOrientationLocked(true);
        options.setCameraId(0);
        options.setCaptureActivity(BarcodeCaptureActivity.class);
        scanLauncher.launch(options);
    }

    private void save() {
        List<String> lines = parseLines();
        if (lines.size() > Blacklist.MAX_LINES) {
            Toast.makeText(this, R.string.blacklist_too_many, Toast.LENGTH_LONG).show();
            return;
        }
        Blacklist.replaceAll(this, lines);
        Toast.makeText(this, getString(R.string.blacklist_saved, lines.size()),
            Toast.LENGTH_SHORT).show();
        finish();
    }

    private List<String> parseLines() {
        List<String> lines = new ArrayList<>();
        for (String line : input.getText().toString().split("\n")) {
            String value = line.trim();
            if (!value.isEmpty()) lines.add(value);
        }
        return lines;
    }

    private void updateCounter(String text) {
        int count = text.isEmpty() ? 0 : parseLines().size();
        tvCounter.setText(getString(R.string.blacklist_counter, count, Blacklist.MAX_LINES));
        btnSave.setEnabled(count <= Blacklist.MAX_LINES);
    }
}

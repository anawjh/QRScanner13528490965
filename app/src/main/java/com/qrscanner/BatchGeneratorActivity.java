package com.qrscanner;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

public class BatchGeneratorActivity extends AppCompatActivity {

    private Spinner spinnerFormat;
    private EditText inputList;
    private TextView tvCount;
    private Button btnExport;

    private String[] formats;
    private boolean qr;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_batch_generator);

        qr = getIntent().getBooleanExtra("qr", true);
        formats = BarcodeFactory.formatsFor(qr);

        TextView tvTitle = findViewById(R.id.tvTitle);
        tvTitle.setText(qr ? R.string.generator_qr_batch_title : R.string.generator_barcode_batch_title);

        spinnerFormat = findViewById(R.id.spinnerFormat);
        inputList = findViewById(R.id.etList);
        tvCount = findViewById(R.id.tvCount);
        btnExport = findViewById(R.id.btnExport);

        List<String> labels = new ArrayList<>();
        for (String format : formats) {
            labels.add(getString(BarcodeFactory.labelRes(format)));
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFormat.setAdapter(adapter);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnSample).setOnClickListener(v -> fillSample());
        findViewById(R.id.btnClearList).setOnClickListener(v -> inputList.setText(""));
        btnExport.setOnClickListener(v -> export());

        inputList.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { updateCount(); }
        });

        updateCount();
    }

    private void fillSample() {
        String[] samples = qr
            ? new String[]{"https://github.com/anawjh/QRScanner",
                "WIFI:T:WPA;S:MyWiFi;P:12345678;;",
                "BEGIN:VCARD\nVERSION:3.0\nN:Zhang;San\nTEL:13800138000\nEND:VCARD"}
            : new String[]{"12345678", "6901234567892", "ABC-123456", "SKU-20240501"};
        inputList.setText(String.join("\n", samples));
    }

    private List<String> parseLines() {
        List<String> lines = new ArrayList<>();
        for (String line : inputList.getText().toString().split("\n")) {
            String value = line.trim();
            if (!value.isEmpty()) lines.add(value);
        }
        return lines;
    }

    private void updateCount() {
        List<String> lines = parseLines();
        tvCount.setText(getString(R.string.batch_valid_count, lines.size()));
        btnExport.setEnabled(!lines.isEmpty());
    }

    private void export() {
        List<String> lines = parseLines();
        if (lines.isEmpty()) return;
        String format = formats[spinnerFormat.getSelectedItemPosition()];

        int error = 0;
        int errorIndex = -1;
        for (int i = 0; i < lines.size(); i++) {
            int result = BarcodeFactory.validate(lines.get(i), format);
            if (result != 0) {
                error = result;
                errorIndex = i;
                break;
            }
        }
        if (error != 0) {
            new AlertDialog.Builder(this)
                .setTitle(R.string.batch_invalid_title)
                .setMessage(getString(R.string.batch_invalid_message,
                    errorIndex + 1, lines.get(errorIndex), getString(error)))
                .setPositiveButton(android.R.string.ok, null)
                .show();
            return;
        }

        if (ExcelExporter.requiresImageConfirm(lines.size())) {
            new AlertDialog.Builder(this)
                .setTitle(R.string.export_image_confirm_title)
                .setMessage(getString(R.string.export_image_confirm_message,
                    lines.size(), ExcelExporter.MAX_IMAGES))
                .setPositiveButton(R.string.gen_export, (d, w) -> doExport(format, lines))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
            return;
        }

        doExport(format, lines);
    }

    private void doExport(String format, List<String> lines) {
        GenerateRecord record = new GenerateRecord();
        record.setQr(qr);
        record.setFormat(format);
        record.setItems(new ArrayList<>(lines));
        GenerateStore.add(this, record);
        ExcelExporter.exportCodes(this, format, lines, qr);
        Toast.makeText(this, R.string.batch_saved, Toast.LENGTH_SHORT).show();
    }
}

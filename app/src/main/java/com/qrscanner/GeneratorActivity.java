package com.qrscanner;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.zxing.WriterException;

import java.util.ArrayList;
import java.util.List;

public class GeneratorActivity extends AppCompatActivity {

    private static final int PREVIEW_WIDTH = 900;

    private Spinner spinnerFormat;
    private EditText inputContent;
    private ImageView imagePreview;
    private TextView tvListCount;
    private Button btnExport;

    private String[] formats;
    private final List<String> list = new ArrayList<>();
    private boolean qr;
    private String lockedFormat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_generator);

        qr = getIntent().getBooleanExtra("qr", true);
        formats = BarcodeFactory.formatsFor(qr);

        TextView tvTitle = findViewById(R.id.tvTitle);
        tvTitle.setText(qr ? R.string.generator_qr_title : R.string.generator_barcode_title);

        spinnerFormat = findViewById(R.id.spinnerFormat);
        inputContent = findViewById(R.id.etContent);
        imagePreview = findViewById(R.id.imagePreview);
        tvListCount = findViewById(R.id.tvListCount);
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
        findViewById(R.id.btnAdd).setOnClickListener(v -> addToList());
        findViewById(R.id.btnClearList).setOnClickListener(v -> clearList());
        btnExport.setOnClickListener(v -> export());

        inputContent.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { updatePreview(); }
        });
        spinnerFormat.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updatePreview();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        updateListCount();
    }

    private void updatePreview() {
        String content = inputContent.getText().toString().trim();
        if (content.isEmpty()) {
            imagePreview.setImageDrawable(null);
            return;
        }
        String format = selectedFormat();
        int error = BarcodeFactory.validate(content, format);
        if (error != 0) {
            imagePreview.setImageDrawable(null);
            imagePreview.setTag(getString(error));
            return;
        }
        imagePreview.setTag(null);
        try {
            Bitmap bitmap = BarcodeFactory.create(content, format,
                qr ? 500 : PREVIEW_WIDTH, qr ? 500 : 300);
            Bitmap preview = BarcodeFactory.scale(bitmap, PREVIEW_WIDTH);
            if (preview != bitmap) bitmap.recycle();
            imagePreview.setImageBitmap(preview);
        } catch (WriterException e) {
            imagePreview.setImageDrawable(null);
        }
    }

    private void addToList() {
        String content = inputContent.getText().toString().trim();
        if (content.isEmpty()) {
            Toast.makeText(this, R.string.gen_err_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        String format = selectedFormat();
        int error = BarcodeFactory.validate(content, format);
        if (error != 0) {
            Toast.makeText(this, error, Toast.LENGTH_LONG).show();
            return;
        }
        if (list.contains(content)) {
            Toast.makeText(this, R.string.gen_already_added, Toast.LENGTH_SHORT).show();
            return;
        }
        if (list.isEmpty()) lockedFormat = format;
        list.add(content);
        inputContent.setText("");
        updateListCount();
        Toast.makeText(this, R.string.gen_added, Toast.LENGTH_SHORT).show();
    }

    private void clearList() {
        if (list.isEmpty()) return;
        new AlertDialog.Builder(this)
            .setTitle(R.string.gen_clear_title)
            .setMessage(getString(R.string.gen_clear_message, list.size()))
            .setPositiveButton(R.string.btn_clear, (d, w) -> {
                list.clear();
                lockedFormat = null;
                updateListCount();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void updateListCount() {
        tvListCount.setText(getString(R.string.gen_list_count, list.size()));
        btnExport.setEnabled(!list.isEmpty());
        spinnerFormat.setEnabled(list.isEmpty());
        if (list.isEmpty()) {
            imagePreview.setImageDrawable(null);
        } else if (lockedFormat != null) {
            int index = indexOf(lockedFormat);
            if (index >= 0 && index != spinnerFormat.getSelectedItemPosition()) {
                spinnerFormat.setSelection(index);
            }
        }
    }

    private String selectedFormat() {
        int index = spinnerFormat.getSelectedItemPosition();
        if (index < 0 || index >= formats.length) return formats[0];
        return formats[index];
    }

    private int indexOf(String format) {
        for (int i = 0; i < formats.length; i++) {
            if (formats[i].equals(format)) return i;
        }
        return -1;
    }

    private void export() {
        if (list.isEmpty()) return;
        final String format = lockedFormat != null ? lockedFormat : selectedFormat();
        if (ExcelExporter.requiresImageConfirm(list.size())) {
            new AlertDialog.Builder(this)
                .setTitle(R.string.export_image_confirm_title)
                .setMessage(getString(R.string.export_image_confirm_message,
                    list.size(), ExcelExporter.MAX_IMAGES))
                .setPositiveButton(R.string.gen_export, (d, w) -> doExport(format))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
            return;
        }
        doExport(format);
    }

    private void doExport(String format) {
        GenerateRecord record = new GenerateRecord();
        record.setQr(qr);
        record.setFormat(format);
        record.setItems(new ArrayList<>(list));
        GenerateStore.add(this, record);
        ExcelExporter.exportCodes(this, format, new ArrayList<>(list), qr);
    }
}

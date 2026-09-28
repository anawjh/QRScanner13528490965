package com.qrscanner;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class PhotoViewerActivity extends AppCompatActivity {

    private static final String EXTRA_PATH = "path";

    private ImageView ivFull;
    private TextView tvPath;
    private String path;

    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_photo_viewer);

        ivFull = findViewById(R.id.ivFull);
        tvPath = findViewById(R.id.tvPath);
        Button btnDelete = findViewById(R.id.btnDelete);
        Button btnDone = findViewById(R.id.btnDone);

        path = getIntent().getStringExtra(EXTRA_PATH);
        if (path == null || path.isEmpty()) {
            finish();
            return;
        }

        btnDone.setOnClickListener(v -> finish());
        btnDelete.setOnClickListener(v -> confirmDelete());
        findViewById(R.id.root).setOnClickListener(v -> finish());
        ivFull.setOnClickListener(v -> finish());

        load();
    }

    private void load() {
        setResult(RESULT_OK);
        new Thread(() -> {
            final android.graphics.Bitmap bmp =
                ScanPhotoStore.decodeSampled(path, 2048, 2048);
            main.post(() -> {
                if (bmp == null) {
                    finish();
                    return;
                }
                ivFull.setImageBitmap(bmp);
            });
        }, "photo-view").start();
        tvPath.setText(path);
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
            .setTitle(R.string.photo_delete_title)
            .setMessage(getString(R.string.photo_delete_msg, path))
            .setPositiveButton(R.string.confirm, (d, w) -> {
                if (ScanPhotoStore.delete(path)) {
                    setResult(RESULT_FIRST_USER);
                } else {
                    setResult(RESULT_CANCELED);
                }
                finish();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    public static Intent intent(android.content.Context context, String path) {
        Intent i = new Intent(context, PhotoViewerActivity.class);
        i.putExtra(EXTRA_PATH, path);
        return i;
    }
}

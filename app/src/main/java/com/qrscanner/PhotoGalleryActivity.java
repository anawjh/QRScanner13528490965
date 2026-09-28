package com.qrscanner;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.List;

public class PhotoGalleryActivity extends AppCompatActivity
        implements PhotoAdapter.OnPhotoClick {

    private RecyclerView rvPhotos;
    private TextView tvEmpty;
    private TextView tvPhotoToggle;
    private TextView btnDeleteAll;
    private PhotoAdapter adapter;
    private androidx.activity.result.ActivityResultLauncher<android.content.Intent> viewerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_gallery);

        rvPhotos = findViewById(R.id.rvPhotos);
        tvEmpty = findViewById(R.id.tvEmpty);
        tvPhotoToggle = findViewById(R.id.tvPhotoToggle);
        Button btnBack = findViewById(R.id.btnBack);
        btnDeleteAll = findViewById(R.id.btnDeleteAll);

        ((TextView) findViewById(R.id.tvPath)).setText(
            getString(R.string.photo_path_hint, ScanPhotoStore.displayPath(this)));

        adapter = new PhotoAdapter(this);
        rvPhotos.setLayoutManager(new GridLayoutManager(this, 3));
        rvPhotos.setAdapter(adapter);

        btnBack.setOnClickListener(v -> finish());
        tvPhotoToggle.setOnClickListener(v -> toggleSave());
        btnDeleteAll.setOnClickListener(v -> confirmDeleteAll());

        viewerLauncher = registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_FIRST_USER) {
                    Toast.makeText(this, R.string.photo_deleted, Toast.LENGTH_SHORT).show();
                }
                refresh();
            });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        List<File> files = ScanPhotoStore.list(this);
        adapter.submit(files);
        tvEmpty.setVisibility(files.isEmpty() ? View.VISIBLE : View.GONE);
        tvEmpty.setText(R.string.photo_empty);
        btnDeleteAll.setText(files.isEmpty()
            ? getString(R.string.photo_delete_all)
            : getString(R.string.photo_delete_all_count, files.size()));
        btnDeleteAll.setEnabled(!files.isEmpty());
        btnDeleteAll.setAlpha(files.isEmpty() ? 0.5f : 1f);
        tvPhotoToggle.setText(ScanSettings.isSavePhoto(this)
            ? R.string.photo_save_on : R.string.photo_save_off);
    }

    private void toggleSave() {
        boolean next = !ScanSettings.isSavePhoto(this);
        ScanSettings.setSavePhoto(this, next);
        tvPhotoToggle.setText(next ? R.string.photo_save_on : R.string.photo_save_off);
        Toast.makeText(this, next ? R.string.photo_save_on : R.string.photo_save_off,
            Toast.LENGTH_SHORT).show();
    }

    private void confirmDeleteAll() {
        List<File> files = ScanPhotoStore.list(this);
        if (files.isEmpty()) {
            Toast.makeText(this, R.string.photo_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle(R.string.photo_delete_all_title)
            .setMessage(getString(R.string.photo_delete_all_msg, files.size()))
            .setPositiveButton(R.string.confirm, (d, w) -> {
                int removed = 0;
                for (File f : files) {
                    if (ScanPhotoStore.delete(f.getAbsolutePath())) removed++;
                }
                Toast.makeText(this,
                    getString(R.string.photo_delete_done, removed), Toast.LENGTH_SHORT).show();
                refresh();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    @Override
    public void onPhotoClick(File file, int position) {
        viewerLauncher.launch(PhotoViewerActivity.intent(this, file.getAbsolutePath()));
    }
}

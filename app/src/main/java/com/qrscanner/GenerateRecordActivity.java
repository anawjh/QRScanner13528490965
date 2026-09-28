package com.qrscanner;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class GenerateRecordActivity extends AppCompatActivity {

    private RecyclerView listView;
    private LinearLayout emptyView;
    private Button btnExport;
    private final List<GenerateRecord> data = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_generate_record);

        listView = findViewById(R.id.list);
        emptyView = findViewById(R.id.emptyView);
        btnExport = findViewById(R.id.btnExport);

        listView.setLayoutManager(new LinearLayoutManager(this));
        listView.setAdapter(new Adapter());

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnClearAll).setOnClickListener(v -> clearAll());
        btnExport.setOnClickListener(v -> ExcelExporter.exportGenerateRecords(this, data));
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        data.clear();
        data.addAll(GenerateStore.list(this));
        listView.getAdapter().notifyDataSetChanged();
        emptyView.setVisibility(data.isEmpty() ? View.VISIBLE : View.GONE);
        btnExport.setEnabled(!data.isEmpty());
    }

    private void clearAll() {
        if (data.isEmpty()) return;
        new AlertDialog.Builder(this)
            .setTitle(R.string.gen_clear_title)
            .setMessage(getString(R.string.gen_clear_message, data.size()))
            .setPositiveButton(R.string.btn_clear, (d, w) -> {
                GenerateStore.clear(this);
                reload();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private class Adapter extends RecyclerView.Adapter<Adapter.Holder> {

        class Holder extends RecyclerView.ViewHolder {
            final TextView tvTitle;
            final TextView tvDetail;
            final Button btnDelete;

            Holder(View view) {
                super(view);
                tvTitle = view.findViewById(R.id.tvTitle);
                tvDetail = view.findViewById(R.id.tvDetail);
                btnDelete = view.findViewById(R.id.btnDelete);
            }
        }

        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_generate_record, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            GenerateRecord record = data.get(position);
            holder.tvTitle.setText(getString(R.string.gen_record_title,
                record.isQr() ? getString(R.string.label_qr) : getString(R.string.label_barcode),
                getString(BarcodeFactory.labelRes(record.getFormat()))));
            holder.tvDetail.setText(getString(R.string.gen_record_detail,
                record.getItems().size(), record.getTime(), preview(record)));
            holder.btnDelete.setOnClickListener(v -> confirmDelete(record));
            holder.itemView.setOnClickListener(v -> showDetail(record));
        }

        @Override
        public int getItemCount() {
            return data.size();
        }
    }

    private void confirmDelete(GenerateRecord record) {
        new AlertDialog.Builder(this)
            .setTitle(R.string.gen_delete_title)
            .setMessage(R.string.gen_delete_message)
            .setPositiveButton(R.string.btn_delete, (d, w) -> {
                GenerateStore.delete(this, record.getId());
                reload();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void showDetail(GenerateRecord record) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < record.getItems().size(); i++) {
            builder.append(i + 1).append(". ").append(record.getItems().get(i)).append("\n");
        }
        new AlertDialog.Builder(this)
            .setTitle(R.string.gen_record_title)
            .setMessage(builder.toString().trim())
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(R.string.gen_export_again, (d, w) -> confirmExportAgain(record))
            .show();
    }

    private void confirmExportAgain(GenerateRecord record) {
        final List<String> items = new ArrayList<>(record.getItems());
        if (ExcelExporter.requiresImageConfirm(items.size())) {
            new AlertDialog.Builder(this)
                .setTitle(R.string.export_image_confirm_title)
                .setMessage(getString(R.string.export_image_confirm_message,
                    items.size(), ExcelExporter.MAX_IMAGES))
                .setPositiveButton(R.string.gen_export, (d, w) -> doExportAgain(record, items))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
            return;
        }
        doExportAgain(record, items);
    }

    private void doExportAgain(GenerateRecord record, List<String> items) {
        ExcelExporter.exportCodes(this, record.getFormat(), items, record.isQr());
    }

    private String preview(GenerateRecord record) {
        List<String> items = record.getItems();
        if (items.isEmpty()) return "";
        if (items.size() <= 2) return String.join(" / ", items);
        return items.get(0) + " / " + items.get(1) + " ... (+" + (items.size() - 2) + ")";
    }
}

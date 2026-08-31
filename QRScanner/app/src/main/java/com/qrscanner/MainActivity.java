package com.qrscanner;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.qrscanner.databinding.ActivityMainBinding;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private ScanRecordAdapter adapter;
    private List<ScanRecord> records = new ArrayList<>();

    // 霍盛/霍尼韦尔 PDA 广播
    private static final String ACTION1 = "com.honeywell.scan.broadcast";
    private static final String ACTION2 = "android.intent.ACTION_DECODE_DATA";
    private static final String KEY1    = "data";
    private static final String KEY2    = "barcode_string";

    private BroadcastReceiver scanReceiver;
    private StringBuilder pdaBuffer = new StringBuilder();
    private long lastKeyTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setupRecyclerView();
        setupButtons();
        updateCounter();
        // 默认 PDA 模式，更新状态栏
        binding.tvModeBar.setText("🔫 PDA 激光扫码模式  |  直接按扫码键录入");
        binding.tvModeBar.setBackgroundColor(0xFF6A1B9A);
        binding.btnScan.setText("🔫 PDA 模式  |  直接按扫码键");
        binding.btnScan.setBackgroundTintList(
            android.content.res.ColorStateList.valueOf(0xFF6A1B9A));
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerScanReceiver();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterScanReceiver();
    }

    private void registerScanReceiver() {
        if (scanReceiver != null) return;
        scanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null) return;
                String data = null;
                String action = intent.getAction();
                if (ACTION1.equals(action)) {
                    data = intent.getStringExtra(KEY1);
                } else if (ACTION2.equals(action)) {
                    data = intent.getStringExtra(KEY2);
                }
                if (data != null && !data.trim().isEmpty()) {
                    addRecord(data.trim(), "");
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION1);
        filter.addAction(ACTION2);
        registerReceiver(scanReceiver, filter);
    }

    private void unregisterScanReceiver() {
        if (scanReceiver != null) {
            try { unregisterReceiver(scanReceiver); } catch (Exception ignored) {}
            scanReceiver = null;
        }
    }

    // 备用：键盘输入模式（部分PDA用此方式）
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        long now = System.currentTimeMillis();
        if (now - lastKeyTime > 500) pdaBuffer.setLength(0);
        lastKeyTime = now;
        if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            String s = pdaBuffer.toString().trim();
            if (!s.isEmpty()) { addRecord(s, ""); pdaBuffer.setLength(0); }
            return true;
        }
        char c = (char) event.getUnicodeChar();
        if (c != 0) pdaBuffer.append(c);
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "📞 联系开发者 13528490965");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 1) {
            new AlertDialog.Builder(this)
                .setTitle("联系开发者")
                .setMessage("jala 批量扫码工具\n\n📱 13528490965\n\n如需定制开发扫码项目，欢迎联系！")
                .setPositiveButton("拨打电话", (d, w) ->
                    startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:13528490965"))))
                .setNegativeButton("关闭", null)
                .show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setupRecyclerView() {
        adapter = new ScanRecordAdapter(records, this::onDeleteRecord, this::onEditRemark);
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerView.setAdapter(adapter);
    }

    private void setupButtons() {
        binding.btnScan.setOnClickListener(v ->
            Toast.makeText(this, "🔫 直接按 PDA 扫码键即可录入", Toast.LENGTH_SHORT).show());
        binding.btnExport.setOnClickListener(v -> exportToExcel());
        binding.btnClear.setOnClickListener(v -> showClearConfirmDialog());
    }

    private void addRecord(String content, String remark) {
        runOnUiThread(() -> {
            String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                Locale.getDefault()).format(new Date());
            records.add(0, new ScanRecord(records.size() + 1, content, time, remark));
            for (int i = 0; i < records.size(); i++)
                records.get(i).setSeq(records.size() - i);
            adapter.notifyDataSetChanged();
            updateCounter();
            binding.tvEmpty.setVisibility(View.GONE);
        });
    }

    private void onDeleteRecord(int position) {
        new AlertDialog.Builder(this)
            .setTitle("删除记录")
            .setMessage("确认删除这条扫码记录？")
            .setPositiveButton("删除", (d, w) -> {
                records.remove(position);
                for (int i = 0; i < records.size(); i++)
                    records.get(i).setSeq(records.size() - i);
                adapter.notifyDataSetChanged();
                updateCounter();
                if (records.isEmpty()) binding.tvEmpty.setVisibility(View.VISIBLE);
            })
            .setNegativeButton("取消", null).show();
    }

    private void onEditRemark(int position) {
        ScanRecord record = records.get(position);
        View dv = LayoutInflater.from(this).inflate(R.layout.dialog_remark, null);
        EditText et = dv.findViewById(R.id.etRemark);
        et.setText(record.getRemark());
        new AlertDialog.Builder(this)
            .setTitle("编辑备注")
            .setView(dv)
            .setPositiveButton("保存", (d, w) -> {
                record.setRemark(et.getText().toString().trim());
                adapter.notifyItemChanged(position);
            })
            .setNegativeButton("取消", null).show();
    }

    private void showClearConfirmDialog() {
        if (records.isEmpty()) {
            Toast.makeText(this, "暂无记录", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("清空记录")
            .setMessage("确认清空全部 " + records.size() + " 条记录？")
            .setPositiveButton("清空", (d, w) -> {
                records.clear();
                adapter.notifyDataSetChanged();
                updateCounter();
                binding.tvEmpty.setVisibility(View.VISIBLE);
            })
            .setNegativeButton("取消", null).show();
    }

    private void updateCounter() {
        binding.tvCounter.setText("已扫: " + records.size() + " 条");
        binding.btnExport.setEnabled(!records.isEmpty());
        binding.btnClear.setEnabled(!records.isEmpty());
    }

    private void exportToExcel() {
        try {
            Workbook wb = new XSSFWorkbook();
            Sheet sheet = wb.createSheet("扫码记录");
            sheet.setColumnWidth(0, 8 * 256);
            sheet.setColumnWidth(1, 50 * 256);
            sheet.setColumnWidth(2, 22 * 256);
            sheet.setColumnWidth(3, 30 * 256);

            CellStyle hs = wb.createCellStyle();
            Font hf = wb.createFont();
            hf.setBold(true); hf.setColor(IndexedColors.WHITE.getIndex());
            hs.setFont(hf);
            hs.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            hs.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            hs.setAlignment(HorizontalAlignment.CENTER);
            hs.setBorderBottom(BorderStyle.THIN); hs.setBorderTop(BorderStyle.THIN);
            hs.setBorderLeft(BorderStyle.THIN); hs.setBorderRight(BorderStyle.THIN);

            CellStyle es = wb.createCellStyle();
            es.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            es.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            es.setBorderBottom(BorderStyle.THIN); es.setBorderTop(BorderStyle.THIN);
            es.setBorderLeft(BorderStyle.THIN); es.setBorderRight(BorderStyle.THIN);

            CellStyle os = wb.createCellStyle();
            os.setBorderBottom(BorderStyle.THIN); os.setBorderTop(BorderStyle.THIN);
            os.setBorderLeft(BorderStyle.THIN); os.setBorderRight(BorderStyle.THIN);

            Row hr = sheet.createRow(0);
            String[] headers = {"序号", "扫码内容", "扫描时间", "备注/标签"};
            for (int i = 0; i < headers.length; i++) {
                Cell c = hr.createCell(i); c.setCellValue(headers[i]); c.setCellStyle(hs);
            }
            hr.setHeight((short) 500);

            List<ScanRecord> sorted = new ArrayList<>(records);
            sorted.sort((a, b) -> a.getSeq() - b.getSeq());
            for (int i = 0; i < sorted.size(); i++) {
                ScanRecord r = sorted.get(i);
                Row row = sheet.createRow(i + 1);
                CellStyle s = (i % 2 == 0) ? os : es;
                Cell c0 = row.createCell(0); c0.setCellValue(r.getSeq()); c0.setCellStyle(s);
                Cell c1 = row.createCell(1); c1.setCellValue(r.getContent()); c1.setCellStyle(s);
                Cell c2 = row.createCell(2); c2.setCellValue(r.getTime()); c2.setCellStyle(s);
                Cell c3 = row.createCell(3); c3.setCellValue(r.getRemark()); c3.setCellStyle(s);
                row.setHeight((short) 400);
            }

            sheet.createRow(sorted.size() + 2).createCell(0).setCellValue(
                "导出时间: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                    Locale.getDefault()).format(new Date()) +
                "  |  共 " + sorted.size() + " 条  |  jala批量扫码 13528490965");

            String fn = "扫码记录_" + new SimpleDateFormat("yyyyMMdd_HHmmss",
                Locale.getDefault()).format(new Date()) + ".xlsx";
            File dir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
            if (dir == null) dir = getFilesDir();
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, fn);
            try (FileOutputStream fos = new FileOutputStream(file)) { wb.write(fos); }
            wb.close();

            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            new AlertDialog.Builder(this)
                .setTitle("✅ 导出成功")
                .setMessage("文件：" + fn + "\n共 " + records.size() + " 条记录")
                .setPositiveButton("分享文件", (d, w) -> {
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                    i.putExtra(Intent.EXTRA_STREAM, uri);
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(i, "分享 Excel 文件"));
                })
                .setNegativeButton("确定", null).show();

        } catch (Exception e) {
            Toast.makeText(this, "导出失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}

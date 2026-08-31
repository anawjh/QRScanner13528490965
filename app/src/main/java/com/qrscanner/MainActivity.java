package com.qrscanner;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;
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

    private static final String PREF_LANG = "app_lang";
    private static final String PREF_SCAN = "scan_settings";
    private static final String KEY_AUTO_SCAN = "auto_scan";
    private static final String LANG_ZH = "zh";
    private static final String LANG_EN = "en";

    private ActivityMainBinding binding;
    private ScanRecordAdapter adapter;
    private List<ScanRecord> records = new ArrayList<>();

    private ProjectManager projectManager;
    private ScanProject currentProject;

    private final ActivityResultLauncher<ScanOptions> scanLauncher =
        registerForActivityResult(new ScanContract(), result -> {
            if (result.getContents() != null && !result.getContents().isEmpty()) {
                addRecord(result.getContents(), "");
            }
            if (isAutoScanEnabled()) {
                binding.getRoot().postDelayed(this::startScan, 300);
            }
        });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        applySavedLocale();

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        projectManager = new ProjectManager(this);
        currentProject = projectManager.createNew();
        records = currentProject.records;
        reSequence();

        setupRecyclerView();
        setupButtons();
        updateCounter();
        updateProjectName();

        binding.getRoot().postDelayed(this::startScan, 500);
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    private boolean isAutoScanEnabled() {
        SharedPreferences prefs = getSharedPreferences(PREF_SCAN, MODE_PRIVATE);
        return prefs.getBoolean(KEY_AUTO_SCAN, true);
    }

    private void toggleAutoScan() {
        SharedPreferences prefs = getSharedPreferences(PREF_SCAN, MODE_PRIVATE);
        boolean current = prefs.getBoolean(KEY_AUTO_SCAN, true);
        prefs.edit().putBoolean(KEY_AUTO_SCAN, !current).apply();
        Toast.makeText(this, !current ? getString(R.string.auto_scan_on) : getString(R.string.auto_scan_off),
                Toast.LENGTH_SHORT).show();
    }

    private void startScan() {
        ScanOptions options = new ScanOptions();
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
        options.setPrompt(getString(R.string.hint_scan));
        options.setBeepEnabled(false);
        options.setOrientationLocked(true);
        options.setCameraId(0);

        String flashMode = getSharedPreferences(FlashSettingsActivity.PREF_NAME, MODE_PRIVATE)
                .getString(FlashSettingsActivity.KEY_FLASH_MODE, FlashSettingsActivity.MODE_ON_SCAN);

        if (flashMode.equals(FlashSettingsActivity.MODE_ALWAYS_ON) ||
                flashMode.equals(FlashSettingsActivity.MODE_ON_SCAN)) {
            options.setTorchEnabled(true);
        } else {
            options.setTorchEnabled(false);
        }

        scanLauncher.launch(options);
    }

    private void setupRecyclerView() {
        adapter = new ScanRecordAdapter(records, this::onDeleteRecord, this::onEditRemark);
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerView.setAdapter(adapter);
    }

    private void setupButtons() {
        binding.btnScan.setOnClickListener(v -> startScan());

        binding.btnExport.setOnClickListener(v -> exportToExcel());
        binding.btnClear.setOnClickListener(v -> showClearConfirmDialog());

        binding.btnMenu.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(this, v);
            popup.getMenu().add(0, 10, 0, getString(R.string.menu_new_project));
            popup.getMenu().add(0, 11, 0, getString(R.string.menu_open_project));
            popup.getMenu().add(0, 12, 0, getString(R.string.menu_flash_settings));

            boolean autoOn = isAutoScanEnabled();
            popup.getMenu().add(0, 15, 0, autoOn
                ? getString(R.string.menu_auto_scan_on)
                : getString(R.string.menu_auto_scan_off));

            popup.getMenu().add(0, 14, 0, getString(R.string.menu_switch_lang));
            popup.getMenu().add(0, 99, 0, "──────────");
            popup.getMenu().add(0, 3, 0, getString(R.string.menu_contact));

            popup.setOnMenuItemClickListener(item -> {
                switch (item.getItemId()) {
                    case 10: showNewProjectDialog(); return true;
                    case 11: showOpenProjectDialog(); return true;
                    case 12: openFlashSettings(); return true;
                    case 14: toggleLanguage(); return true;
                    case 15: toggleAutoScan(); return true;
                    case 3: showContactDialog(); return true;
                }
                return false;
            });
            popup.show();
        });
    }

    // ======================== Language ========================

    private String getCurrentLang() {
        SharedPreferences prefs = getSharedPreferences(PREF_LANG, MODE_PRIVATE);
        return prefs.getString(PREF_LANG, LANG_ZH);
    }

    private void applySavedLocale() {
        String lang = getCurrentLang();
        Locale locale = lang.equals(LANG_EN) ? Locale.ENGLISH : Locale.CHINESE;
        Resources res = getResources();
        Configuration config = res.getConfiguration();
        config.setLocale(locale);
        res.updateConfiguration(config, res.getDisplayMetrics());
    }

    private void toggleLanguage() {
        String current = getCurrentLang();
        String newLang = current.equals(LANG_ZH) ? LANG_EN : LANG_ZH;

        SharedPreferences prefs = getSharedPreferences(PREF_LANG, MODE_PRIVATE);
        prefs.edit().putString(PREF_LANG, newLang).apply();

        Locale locale = newLang.equals(LANG_EN) ? Locale.ENGLISH : Locale.CHINESE;
        Resources res = getResources();
        Configuration config = res.getConfiguration();
        config.setLocale(locale);
        res.updateConfiguration(config, res.getDisplayMetrics());

        recreate();
    }

    // ======================== Camera & Flash ========================

    private void openFlashSettings() {
        Intent intent = new Intent(this, FlashSettingsActivity.class);
        startActivity(intent);
    }

    // ======================== Project ========================

    private void updateProjectName() {
        String name = currentProject != null ? currentProject.name : "N/A";
        binding.tvProjectName.setText("\uD83D\uDCC1 " + name);
    }

    private void showNewProjectDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.menu_new_project);

        EditText input = new EditText(this);
        input.setHint("Project name (empty = auto)");
        builder.setView(input);

        builder.setPositiveButton(android.R.string.ok, (d, w) -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) {
                currentProject = projectManager.createNew();
            } else {
                currentProject = new ScanProject(name);
                records = currentProject.records;
                projectManager.save(currentProject);
            }
            records = currentProject.records;
            adapter = new ScanRecordAdapter(records, this::onDeleteRecord, this::onEditRemark);
            binding.recyclerView.setAdapter(adapter);
            reSequence();
            updateCounter();
            updateProjectName();
            binding.tvEmpty.setVisibility(records.isEmpty() ? View.VISIBLE : View.GONE);
            Toast.makeText(this, "Created: " + currentProject.name, Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }

    private void showOpenProjectDialog() {
        List<String> projects = projectManager.listProjects();
        if (projects.isEmpty()) {
            Toast.makeText(this, "No projects", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.menu_open_project);

        View view = getLayoutInflater().inflate(R.layout.dialog_project_list, null);
        ListView lv = view.findViewById(R.id.lvProjects);
        ArrayAdapter<String> adapter2 = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, projects);
        lv.setAdapter(adapter2);

        builder.setView(view);
        builder.setNegativeButton(android.R.string.cancel, null);
        AlertDialog dialog = builder.show();

        lv.setOnItemClickListener((parent, v, pos, id) -> {
            String name = projects.get(pos);
            ScanProject p = projectManager.load(name);
            if (p != null) {
                currentProject = p;
                records = currentProject.records;
                adapter = new ScanRecordAdapter(records, MainActivity.this::onDeleteRecord,
                        MainActivity.this::onEditRemark);
                binding.recyclerView.setAdapter(adapter);
                reSequence();
                updateCounter();
                updateProjectName();
                binding.tvEmpty.setVisibility(records.isEmpty() ? View.VISIBLE : View.GONE);
                Toast.makeText(MainActivity.this, "Opened: " + name, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(MainActivity.this, "Open failed", Toast.LENGTH_SHORT).show();
            }
            dialog.dismiss();
        });
    }

    private void saveCurrentProject() {
        if (currentProject != null) {
            projectManager.save(currentProject);
        }
    }

    private void reSequence() {
        for (int i = 0; i < records.size(); i++) {
            records.get(i).setSeq(records.size() - i);
        }
        saveCurrentProject();
    }

    private void showContactDialog() {
        new AlertDialog.Builder(this)
            .setTitle(R.string.contact_title)
            .setMessage(getString(R.string.contact_develop) + "\n\n\uD83D\uDCF1 13528490965")
            .setPositiveButton("\uD83D\uDCDE 13528490965", (d, w) -> {
                Intent intent = new Intent(Intent.ACTION_DIAL,
                    Uri.parse("tel:13528490965"));
                startActivity(intent);
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    // ======================== Records ========================

    private void addRecord(String content, String remark) {
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date());
        ScanRecord record = new ScanRecord(records.size() + 1, content, time, remark);
        records.add(0, record);
        reSequence();
        adapter.notifyDataSetChanged();
        updateCounter();
        binding.tvEmpty.setVisibility(View.GONE);
    }

    private void onDeleteRecord(int position) {
        new AlertDialog.Builder(this)
            .setTitle(R.string.delete_title)
            .setMessage(R.string.delete_confirm)
            .setPositiveButton(android.R.string.ok, (d, w) -> {
                records.remove(position);
                reSequence();
                adapter.notifyDataSetChanged();
                updateCounter();
                if (records.isEmpty()) binding.tvEmpty.setVisibility(View.VISIBLE);
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void onEditRemark(int position) {
        ScanRecord record = records.get(position);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_remark, null);
        EditText etRemark = dialogView.findViewById(R.id.etRemark);
        etRemark.setText(record.getRemark());
        new AlertDialog.Builder(this)
            .setTitle(R.string.edit_remark_title)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok, (d, w) -> {
                record.setRemark(etRemark.getText().toString().trim());
                adapter.notifyItemChanged(position);
                saveCurrentProject();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void showClearConfirmDialog() {
        if (records.isEmpty()) { Toast.makeText(this, R.string.no_records, Toast.LENGTH_SHORT).show(); return; }
        new AlertDialog.Builder(this)
            .setTitle(R.string.clear_all_title)
            .setMessage(getString(R.string.clear_all_msg, records.size()))
            .setPositiveButton(android.R.string.ok, (d, w) -> {
                records.clear();
                reSequence();
                adapter.notifyDataSetChanged();
                updateCounter();
                binding.tvEmpty.setVisibility(View.VISIBLE);
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void updateCounter() {
        binding.tvCounter.setText(getString(R.string.scanned_count, records.size()));
        binding.btnExport.setEnabled(!records.isEmpty());
        binding.btnClear.setEnabled(!records.isEmpty());
    }

    // ======================== Export ========================

    private void exportToExcel() {
        try {
            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("Scan Records");
            sheet.setColumnWidth(0, 8 * 256);
            sheet.setColumnWidth(1, 50 * 256);
            sheet.setColumnWidth(2, 22 * 256);
            sheet.setColumnWidth(3, 30 * 256);

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            CellStyle evenStyle = workbook.createCellStyle();
            evenStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            evenStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            evenStyle.setBorderBottom(BorderStyle.THIN); evenStyle.setBorderTop(BorderStyle.THIN);
            evenStyle.setBorderLeft(BorderStyle.THIN); evenStyle.setBorderRight(BorderStyle.THIN);

            CellStyle oddStyle = workbook.createCellStyle();
            oddStyle.setBorderBottom(BorderStyle.THIN); oddStyle.setBorderTop(BorderStyle.THIN);
            oddStyle.setBorderLeft(BorderStyle.THIN); oddStyle.setBorderRight(BorderStyle.THIN);

            Row headerRow = sheet.createRow(0);
            String[] headers = {"#", "QR Content", "Scan Time", "Remark"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            headerRow.setHeight((short) 500);

            List<ScanRecord> sorted = new ArrayList<>(records);
            sorted.sort((a, b) -> a.getSeq() - b.getSeq());
            for (int i = 0; i < sorted.size(); i++) {
                ScanRecord r = sorted.get(i);
                Row row = sheet.createRow(i + 1);
                CellStyle s = (i % 2 == 0) ? oddStyle : evenStyle;
                Cell c0 = row.createCell(0); c0.setCellValue(r.getSeq()); c0.setCellStyle(s);
                Cell c1 = row.createCell(1); c1.setCellValue(r.getContent()); c1.setCellStyle(s);
                Cell c2 = row.createCell(2); c2.setCellValue(r.getTime()); c2.setCellStyle(s);
                Cell c3 = row.createCell(3); c3.setCellValue(r.getRemark()); c3.setCellStyle(s);
                row.setHeight((short) 400);
            }

            String safeName = currentProject != null
                ? currentProject.name.replaceAll("[\\\\/:*?\"<>|]", "_")
                : "ScanRecords";
            String fileName = safeName + "_" +
                new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".xlsx";
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (dir == null) dir = getFilesDir();
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, fileName);
            try (FileOutputStream fos = new FileOutputStream(file)) { workbook.write(fos); }
            workbook.close();

            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            new AlertDialog.Builder(this)
                .setTitle(R.string.export_ok_title)
                .setMessage(fileName + "\n" + records.size() + " records")
                .setPositiveButton(R.string.share, (d, w) -> {
                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                    shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
                    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(shareIntent, getString(R.string.share)));
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();

        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.export_failed, e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }
}

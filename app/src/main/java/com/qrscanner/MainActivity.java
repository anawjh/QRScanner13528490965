package com.qrscanner;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

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

    private static final String PREF_LANG = "app_lang";
    private static final String LANG_ZH = "zh";
    private static final String LANG_EN = "en";

    private ActivityMainBinding binding;
    private ScanRecordAdapter adapter;
    private List<ScanRecord> records = new ArrayList<>();

    private StringBuilder pdaBuffer = new StringBuilder();
    private long lastKeyTime = 0;

    private ProjectManager projectManager;
    private ScanProject currentProject;

    private final Handler pdaHandler = new Handler(Looper.getMainLooper());
    private final Runnable pdaAutoSubmit = () -> {
        String s = pdaBuffer.toString().trim();
        if (!s.isEmpty()) { addRecord(s, ""); pdaBuffer.setLength(0); }
    };

    private boolean isCameraMode = true;
    private ActivityResultLauncher<Intent> cameraScanLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        applySavedLocale();

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.getRoot().setFocusable(true);
        binding.getRoot().setFocusableInTouchMode(true);

        cameraScanLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        String scanResult = result.getData().getStringExtra(
                                CameraScanActivity.EXTRA_SCAN_RESULT);
                        if (scanResult != null && !scanResult.isEmpty()) {
                            addRecord(scanResult, "");
                        }
                    }
                    binding.getRoot().requestFocus();
                });

        projectManager = new ProjectManager(this);
        currentProject = projectManager.createNew();
        records = currentProject.records;
        reSequence();

        setupRecyclerView();
        setupButtons();
        updateCounter();
        updatePdaIndicator();
        updateProjectName();

        showPdaModeGuide();

        binding.getRoot().postDelayed(() -> openCameraScan(), 500);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return true;
    }

    private void setupRecyclerView() {
        adapter = new ScanRecordAdapter(records, this::onDeleteRecord, this::onEditRemark);
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerView.setAdapter(adapter);
    }

    private void setupButtons() {
        updateScanButton();

        binding.btnExport.setOnClickListener(v -> exportToExcel());
        binding.btnClear.setOnClickListener(v -> showClearConfirmDialog());

        binding.btnMenu.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(this, v);
            popup.getMenu().add(0, 10, 0, getString(R.string.menu_new_project));
            popup.getMenu().add(0, 11, 0, getString(R.string.menu_open_project));
            popup.getMenu().add(0, 12, 0, getString(R.string.menu_flash_settings));
            popup.getMenu().add(0, 13, 0, isCameraMode
                ? getString(R.string.menu_switch_pda)
                : getString(R.string.menu_switch_camera));
            popup.getMenu().add(0, 14, 0, getString(R.string.menu_switch_lang));
            popup.getMenu().add(0, 99, 0, "──────────");
            popup.getMenu().add(0, 3, 0, getString(R.string.menu_contact));

            popup.setOnMenuItemClickListener(item -> {
                switch (item.getItemId()) {
                    case 10: showNewProjectDialog(); return true;
                    case 11: showOpenProjectDialog(); return true;
                    case 12: openFlashSettings(); return true;
                    case 13: toggleScanMode(); return true;
                    case 14: toggleLanguage(); return true;
                    case 3: showContactDialog(); return true;
                }
                return false;
            });
            popup.show();
        });

        binding.btnScan.setOnClickListener(v -> {
            if (isCameraMode) {
                openCameraScan();
            } else {
                Toast.makeText(this, "PDA mode: press scan key", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateScanButton() {
        if (isCameraMode) {
            binding.btnScan.setText(R.string.scan_mode_camera);
            binding.btnScan.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(0xFF1976D2));
        } else {
            binding.btnScan.setText(R.string.scan_mode_pda);
            binding.btnScan.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(0xFF6A1B9A));
        }
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

    private void openCameraScan() {
        Intent intent = new Intent(this, CameraScanActivity.class);
        cameraScanLauncher.launch(intent);
    }

    private void toggleScanMode() {
        isCameraMode = !isCameraMode;
        updateScanButton();
        updatePdaIndicator();
        String msg = isCameraMode ? "Camera mode" : "PDA mode";
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    // ======================== Project ========================

    private void updateProjectName() {
        String name = currentProject != null ? currentProject.name : "N/A";
        binding.tvProjectName.setText("📁 " + name);
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
            binding.getRoot().requestFocus();
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        AlertDialog dlg = builder.show();
        dlg.setOnDismissListener(d -> binding.getRoot().requestFocus());
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
        dialog.setOnDismissListener(d -> binding.getRoot().requestFocus());

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
            binding.getRoot().requestFocus();
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

    // ======================== PDA ========================

    private void updatePdaIndicator() {
        if (isCameraMode) {
            binding.tvModeBar.setText(R.string.mode_camera);
            binding.tvModeBar.setBackgroundColor(0xFF1976D2);
        } else {
            binding.tvModeBar.setText(R.string.mode_pda);
            binding.tvModeBar.setBackgroundColor(0xFF6A1B9A);
        }
    }

    private void showPdaModeGuide() {
        String title = isCameraMode ? getString(R.string.mode_camera) : getString(R.string.mode_pda);
        String msg = isCameraMode
            ? "Scan QR code with camera.\nFlash settings in menu."
            : "Press PDA scan key to scan.";
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(msg)
            .setPositiveButton(android.R.string.ok, (d, w) -> binding.getRoot().requestFocus())
            .setOnDismissListener(d -> binding.getRoot().requestFocus())
            .show();
    }

    private void showContactDialog() {
        new AlertDialog.Builder(this)
            .setTitle(R.string.contact_title)
            .setMessage(getString(R.string.contact_msg) + "\n\n" + getString(R.string.contact_develop))
            .setPositiveButton("📞 13528490965", (d, w) -> {
                Intent intent = new Intent(Intent.ACTION_DIAL,
                    Uri.parse("tel:13528490965"));
                startActivity(intent);
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_MULTIPLE) {
            String chars = event.getCharacters();
            if (chars != null && chars.length() > 0) {
                addRecord(chars, "");
                pdaBuffer.setLength(0);
                lastKeyTime = System.currentTimeMillis();
                return true;
            }
        }

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();
            long now = System.currentTimeMillis();

            if (now - lastKeyTime > 500 && pdaBuffer.length() > 0) {
                String s = pdaBuffer.toString().trim();
                if (s.length() >= 3) { addRecord(s, ""); }
                pdaBuffer.setLength(0);
            }
            lastKeyTime = now;
            pdaHandler.removeCallbacks(pdaAutoSubmit);
            pdaHandler.postDelayed(pdaAutoSubmit, 500);

            if (keyCode == KeyEvent.KEYCODE_ENTER ||
                keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
                keyCode == KeyEvent.KEYCODE_TAB) {
                String s = pdaBuffer.toString().trim();
                if (!s.isEmpty()) { addRecord(s, ""); pdaBuffer.setLength(0); }
                pdaHandler.removeCallbacks(pdaAutoSubmit);
                return true;
            }

            if (keyCode == KeyEvent.KEYCODE_DEL && pdaBuffer.length() > 0) {
                pdaBuffer.deleteCharAt(pdaBuffer.length() - 1);
                return true;
            }

            if (keyCode >= KeyEvent.KEYCODE_NUMPAD_0 && keyCode <= KeyEvent.KEYCODE_NUMPAD_9) {
                pdaBuffer.append((char) ('0' + (keyCode - KeyEvent.KEYCODE_NUMPAD_0)));
                return true;
            }

            if (keyCode == KeyEvent.KEYCODE_STAR)       { pdaBuffer.append('*'); return true; }
            if (keyCode == KeyEvent.KEYCODE_POUND)       { pdaBuffer.append('#'); return true; }
            if (keyCode == KeyEvent.KEYCODE_MINUS)       { pdaBuffer.append('-'); return true; }
            if (keyCode == KeyEvent.KEYCODE_PERIOD)      { pdaBuffer.append('.'); return true; }
            if (keyCode == KeyEvent.KEYCODE_COMMA)       { pdaBuffer.append(','); return true; }
            if (keyCode == KeyEvent.KEYCODE_SLASH)       { pdaBuffer.append('/'); return true; }

            int meta = event.getMetaState();
            char c = (char) event.getUnicodeChar(meta);
            if (c == 0) c = (char) event.getUnicodeChar();
            if (c == 0) c = (char) event.getDisplayLabel();
            if (c >= 32 && c < 127) { pdaBuffer.append(c); return true; }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            char c = (char) event.getUnicodeChar();
            if (c == 0) c = (char) event.getDisplayLabel();
            if (c >= 32 && c < 127) {
                long now = System.currentTimeMillis();
                if (now - lastKeyTime > 500 && pdaBuffer.length() > 0) {
                    String s = pdaBuffer.toString().trim();
                    if (s.length() >= 3) { addRecord(s, ""); }
                    pdaBuffer.setLength(0);
                }
                lastKeyTime = now;
                pdaBuffer.append(c);
                pdaHandler.removeCallbacks(pdaAutoSubmit);
                pdaHandler.postDelayed(pdaAutoSubmit, 500);
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
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
            .setTitle("Delete")
            .setMessage("Delete this record?")
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
            .setTitle("Edit Remark")
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
        if (records.isEmpty()) { Toast.makeText(this, "No records", Toast.LENGTH_SHORT).show(); return; }
        new AlertDialog.Builder(this)
            .setTitle("Clear All")
            .setMessage("Clear all " + records.size() + " records?")
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
        binding.tvCounter.setText("Scanned: " + records.size());
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
                .setTitle("Export OK")
                .setMessage(fileName + "\n" + records.size() + " records")
                .setPositiveButton("Share", (d, w) -> {
                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                    shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
                    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(shareIntent, "Share Excel"));
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();

        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        binding.getRoot().requestFocus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        pdaHandler.removeCallbacks(pdaAutoSubmit);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            binding.getRoot().requestFocus();
        }
    }
}

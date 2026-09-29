package com.qrscanner;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final String PREF_LANG = "app_lang";
    private static final String LANG_ZH = "zh";
    private static final String LANG_EN = "en";

    private static final int[][] MENU = {
        {0, R.string.menu_scan_now, R.string.menu_scan_now_sub},
        {0, R.string.menu_alpha, R.string.menu_alpha_sub},
        {0, R.string.menu_digit, R.string.menu_digit_sub},
        {0, R.string.menu_format, R.string.menu_format_sub},
        {0, R.string.menu_length, R.string.menu_length_sub},
        {0, R.string.menu_gen_barcode, R.string.menu_gen_sub},
        {0, R.string.menu_gen_barcode_batch, R.string.menu_gen_batch_sub},
        {0, R.string.menu_gen_qr, R.string.menu_gen_sub},
        {0, R.string.menu_gen_qr_batch, R.string.menu_gen_batch_sub},
        {0, R.string.menu_scan_records, R.string.menu_records_sub},
        {0, R.string.menu_gen_records, R.string.menu_records_sub},
        {0, R.string.menu_photos, R.string.menu_photos_sub}
    };

    private static final String[] MENU_ICONS = {
        "▣", "A", "1-9", "▤", "#", "\uD83D\uDD16", "\uD83D\uDCD3", "\uD83D\uDD16", "\uD83D\uDCD3",
        "\uD83D\uDCC4", "\uD83D\uDCC1", "\uD83D\uDDCF"
    };

    private GridLayout grid;
    private TextView tvSummary;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applySavedLocale();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ScanSpeaker.init(this);

        grid = findViewById(R.id.grid);
        tvSummary = findViewById(R.id.tvSummary);

        findViewById(R.id.btnSettings).setOnClickListener(v ->
            startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.btnLang).setOnClickListener(v -> toggleLanguage());

        buildGrid();
    }

    @Override
    protected void onResume() {
        super.onResume();
        buildGrid();
        updateSummary();
    }

    private void buildGrid() {
        grid.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < MENU.length; i++) {
            final int index = i;
            View cell = inflater.inflate(R.layout.item_menu_grid, grid, false);
            ((TextView) cell.findViewById(R.id.tvIcon)).setText(MENU_ICONS[i]);
            ((TextView) cell.findViewById(R.id.tvTitle)).setText(MENU[i][1]);
            TextView subtitle = cell.findViewById(R.id.tvSubtitle);
            subtitle.setText(subtitleFor(i));
            cell.setOnClickListener(v -> onMenuClick(index));
            grid.addView(cell);
        }
    }

    private String subtitleFor(int index) {
        switch (index) {
            case 1:
                return ScanSettings.isAlphaOnly(this)
                    ? getString(R.string.state_on) : getString(R.string.state_off);
            case 2:
                return ScanSettings.isDigitOnly(this)
                    ? getString(R.string.state_on) : getString(R.string.state_off);
            case 3: {
                Set<String> formats = ScanSettings.getFormats(this);
                return formats.isEmpty() ? getString(R.string.state_all)
                    : getString(R.string.state_selected, formats.size());
            }
            case 4: {
                int length = ScanSettings.getFixedLength(this);
                return length > 0 ? getString(R.string.state_length, length)
                    : getString(R.string.state_off);
            }
            case 11:
                return getString(R.string.state_photos, ScanPhotoStore.count(this));
            default:
                return getString(MENU[index][2]);
        }
    }

    private void updateSummary() {
            ScanProject project = ProjectManager.getInstance(this).getCurrent();
            String name = project.name == null ? "" : project.name;
            String full = getString(R.string.home_summary, name, project.records.size());
            SpannableString spannable = new SpannableString(full);
            int start = full.indexOf(name);
            if (start >= 0 && name.length() > 0) {
                spannable.setSpan(new ForegroundColorSpan(Color.RED),
                    start, start + name.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            tvSummary.setText(spannable);
    }

    // ======================== 菜单动作 ========================

    private void onMenuClick(int index) {
        switch (index) {
            case 0: startActivity(new Intent(this, QuickScanActivity.class)); break;
            case 1: pickPrefix(true); break;
            case 2: pickPrefix(false); break;
            case 3: pickFormats(); break;
            case 4: pickLength(); break;
            case 5: openGenerator(false, false); break;
            case 6: openGenerator(false, true); break;
            case 7: openGenerator(true, false); break;
            case 8: openGenerator(true, true); break;
            case 9: startActivity(new Intent(this, ScanRecordActivity.class)); break;
            case 10: startActivity(new Intent(this, GenerateRecordActivity.class)); break;
            case 11: startActivity(new Intent(this, PhotoGalleryActivity.class)); break;
        }
    }

    private void pickPrefix(boolean alpha) {
        boolean enabled = alpha ? ScanSettings.isAlphaOnly(this) : ScanSettings.isDigitOnly(this);
        new AlertDialog.Builder(this)
            .setTitle(alpha ? R.string.menu_alpha : R.string.menu_digit)
            .setMessage(alpha ? R.string.filter_alpha_hint : R.string.filter_digit_hint)
            .setPositiveButton(enabled ? R.string.filter_off : R.string.filter_on,
                (d, w) -> {
                    boolean next = !enabled;
                    if (alpha) ScanSettings.setAlphaOnly(this, next);
                    else ScanSettings.setDigitOnly(this, next);
                    buildGrid();
                    startScan();
                })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void pickFormats() {
        String[] all = ScanFilter.ALL_FORMATS;
        String[] labels = new String[all.length];
        Set<String> current = ScanSettings.getFormats(this);
        final boolean[] state = new boolean[all.length];
        for (int i = 0; i < all.length; i++) {
            labels[i] = getString(BarcodeFactory.labelRes(all[i]));
            state[i] = current.contains(all[i]);
        }

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_format_select, null);
        final ListView list = view.findViewById(R.id.lvFormats);
        list.setAdapter(new ArrayAdapter<>(this,
            android.R.layout.simple_list_item_multiple_choice, labels));
        list.post(() -> {
            for (int i = 0; i < all.length; i++) list.setItemChecked(i, state[i]);
        });

        view.findViewById(R.id.btnSelectAll).setOnClickListener(v -> {
            for (int i = 0; i < all.length; i++) list.setItemChecked(i, true);
        });
        view.findViewById(R.id.btnInvertSelect).setOnClickListener(v -> {
            for (int i = 0; i < all.length; i++) {
                list.setItemChecked(i, !list.isItemChecked(i));
            }
        });

        new AlertDialog.Builder(this)
            .setTitle(R.string.menu_format)
            .setView(view)
            .setPositiveButton(R.string.filter_start, (d, w) -> {
                Set<String> selected = new HashSet<>();
                for (int i = 0; i < all.length; i++) {
                    if (list.isItemChecked(i)) selected.add(all[i]);
                }
                if (selected.isEmpty()) selected.addAll(Arrays.asList(all));
                ScanSettings.setFormats(this, selected);
                buildGrid();
                startScan();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void pickLength() {
        EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        int current = ScanSettings.getFixedLength(this);
        if (current > 0) input.setText(String.valueOf(current));
        new AlertDialog.Builder(this)
            .setTitle(R.string.menu_length)
            .setMessage(R.string.set_filter_length_hint)
            .setView(input)
            .setPositiveButton(R.string.filter_start, (d, w) -> {
                String value = input.getText().toString().trim();
                int length = 0;
                if (!value.isEmpty()) {
                    try {
                        length = Integer.parseInt(value);
                    } catch (NumberFormatException ignored) {
                    }
                    if (length < 0) length = 0;
                }
                ScanSettings.setFixedLength(this, length);
                buildGrid();
                startScan();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void startScan() {
        startActivity(new Intent(this, ScanActivity.class));
    }

    private void openGenerator(boolean qr, boolean batch) {
        Intent intent = new Intent(this, batch ? BatchGeneratorActivity.class : GeneratorActivity.class);
        intent.putExtra("qr", qr);
        startActivity(intent);
    }

    // ======================== Language ========================

    private void toggleLanguage() {
        SharedPreferences prefs = getSharedPreferences(PREF_LANG, MODE_PRIVATE);
        String current = prefs.getString(PREF_LANG, LANG_ZH);
        String next = LANG_EN.equals(current) ? LANG_ZH : LANG_EN;
        prefs.edit().putString(PREF_LANG, next).apply();
        applyLocale(next);
    }

    private void applySavedLocale() {
        SharedPreferences prefs = getSharedPreferences(PREF_LANG, MODE_PRIVATE);
        applyLocale(prefs.getString(PREF_LANG, LANG_ZH));
    }

    private void applyLocale(String lang) {
        Locale locale = LANG_EN.equals(lang) ? Locale.ENGLISH : Locale.SIMPLIFIED_CHINESE;
        LocaleListCompat target = LocaleListCompat.forLanguageTags(locale.toLanguageTag());
        if (!target.equals(AppCompatDelegate.getApplicationLocales())) {
            AppCompatDelegate.setApplicationLocales(target);
        }
    }
}

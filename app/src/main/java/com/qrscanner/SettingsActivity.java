package com.qrscanner;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    private LinearLayout container;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        container = findViewById(R.id.settingsContainer);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        buildRows();
    }

    private void buildRows() {
        container.removeAllViews();
        addSection(R.string.section_scan);
        addRow(R.string.set_alert_mode, alertLabel(), v -> pickAlertMode());
        addRow(R.string.set_interval, intervalLabel(), v -> pickInterval());
        addRow(R.string.set_blacklist, getString(R.string.set_blacklist_value,
            Blacklist.count(this)), v -> openBlacklist());
        addSwitchRow(R.string.set_save_photo, ScanSettings.isSavePhoto(this),
            checked -> ScanSettings.setSavePhoto(this, checked));
        addSwitchRow(R.string.set_continuous, ScanSettings.isContinuousEnabled(this),
            checked -> ScanSettings.setContinuousEnabled(this, checked));
        addRow(R.string.set_flash, "", v ->
            startActivity(new Intent(this, FlashSettingsActivity.class)));
        addSection(R.string.section_filter);
        addSwitchRow(R.string.set_filter_alpha, ScanSettings.isAlphaOnly(this),
            checked -> ScanSettings.setAlphaOnly(this, checked));
        addSwitchRow(R.string.set_filter_digit, ScanSettings.isDigitOnly(this),
            checked -> ScanSettings.setDigitOnly(this, checked));
        addRow(R.string.set_filter_length, lengthLabel(), v -> promptLength());
    }

    // ======================== 交互 ========================

    private void pickAlertMode() {
        String[] labels = {
            getString(R.string.alert_sound_vibrate),
            getString(R.string.alert_sound),
            getString(R.string.alert_vibrate),
            getString(R.string.alert_none)
        };
        String current = ScanSettings.getAlertMode(this);
        int checked = 0;
        for (int i = 0; i < ScanSettings.ALERT_MODES.length; i++) {
            if (ScanSettings.ALERT_MODES[i].equals(current)) { checked = i; break; }
        }
        new AlertDialog.Builder(this)
            .setTitle(R.string.set_alert_mode)
            .setSingleChoiceItems(labels, checked, (d, w) -> {
                ScanSettings.setAlertMode(this, ScanSettings.ALERT_MODES[w]);
                d.dismiss();
                buildRows();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void pickInterval() {
        String[] labels = new String[ScanSettings.INTERVALS_MS.length];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = formatSeconds(ScanSettings.INTERVALS_MS[i]);
        }
        int current = ScanSettings.getIntervalIndex(this);
        new AlertDialog.Builder(this)
            .setTitle(R.string.set_interval)
            .setSingleChoiceItems(labels, current, (d, w) -> {
                ScanSettings.setIntervalIndex(this, w);
                d.dismiss();
                buildRows();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void openBlacklist() {
        startActivity(new Intent(this, BlacklistActivity.class));
    }

    private void promptLength() {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        int current = ScanSettings.getFixedLength(this);
        if (current > 0) input.setText(String.valueOf(current));
        new AlertDialog.Builder(this)
            .setTitle(R.string.set_filter_length)
            .setMessage(R.string.set_filter_length_hint)
            .setView(input)
            .setPositiveButton(android.R.string.ok, (d, w) -> {
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
                buildRows();
            })
            .setNegativeButton(R.string.reset, (d, w) -> {
                ScanSettings.setFixedLength(this, 0);
                buildRows();
            })
            .setNeutralButton(android.R.string.cancel, null)
            .show();
    }

    // ======================== UI 构造 ========================

    private void addSection(int titleRes) {
        TextView title = new TextView(this);
        title.setText(titleRes);
        title.setTextSize(13f);
        title.setTextColor(Color.parseColor("#1565C0"));
        title.setPadding(dp(8), dp(14), dp(8), dp(6));
        container.addView(title);
    }

    private void addRow(int titleRes, String value, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(Color.WHITE);
        row.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        row.setLayoutParams(lp);

        TextView title = new TextView(this);
        title.setText(titleRes);
        title.setTextSize(15f);
        title.setTextColor(Color.parseColor("#212121"));
        row.addView(title, new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView valueView = new TextView(this);
        valueView.setText(value);
        valueView.setTextSize(14f);
        valueView.setTextColor(Color.parseColor("#757575"));
        valueView.setGravity(Gravity.END);
        row.addView(valueView);

        row.setOnClickListener(listener);
        container.addView(row);
    }

    private void addSwitchRow(int titleRes, boolean checked, OnSwitch callback) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(Color.WHITE);
        row.setPadding(dp(14), dp(10), dp(10), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        row.setLayoutParams(lp);

        TextView title = new TextView(this);
        title.setText(titleRes);
        title.setTextSize(15f);
        title.setTextColor(Color.parseColor("#212121"));
        row.addView(title, new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        android.widget.Switch toggle = new android.widget.Switch(this);
        toggle.setChecked(checked);
        row.addView(toggle);

        row.setOnClickListener(v -> toggle.performClick());
        toggle.setOnCheckedChangeListener((v, isChecked) -> {
            callback.onChanged(isChecked);
            Toast.makeText(this, isChecked
                    ? getString(R.string.set_enabled) : getString(R.string.set_disabled),
                Toast.LENGTH_SHORT).show();
        });
        container.addView(row);
    }

    private String alertLabel() {
        String mode = ScanSettings.getAlertMode(this);
        if (ScanSettings.ALERT_SOUND.equals(mode)) return getString(R.string.alert_sound);
        if (ScanSettings.ALERT_VIBRATE.equals(mode)) return getString(R.string.alert_vibrate);
        if (ScanSettings.ALERT_NONE.equals(mode)) return getString(R.string.alert_none);
        return getString(R.string.alert_sound_vibrate);
    }

    private String intervalLabel() {
        return formatSeconds(ScanSettings.getIntervalMs(this));
    }

    private String lengthLabel() {
        int length = ScanSettings.getFixedLength(this);
        return length > 0 ? getString(R.string.set_filter_length_value, length)
            : getString(R.string.set_not_set);
    }

    private String formatSeconds(int ms) {
        if (ms % 1000 == 0) return getString(R.string.seconds_value, ms / 1000);
        return getString(R.string.millis_value, ms);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private interface OnSwitch {
        void onChanged(boolean checked);
    }
}

package com.qrscanner;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class LicenseActivity extends AppCompatActivity {

    private static final String EXTRA_BLOCKED = "blocked";

    private TextView tvStatus;
    private TextView tvTrial;
    private TextView tvModel;
    private TextView tvMachineCode;
    private TextView tvResult;
    private TextView tvSelfTest;
    private EditText etCode;
    private Button btnActivate;
    private Switch swTestMode;
    private boolean fromBlocked;

    /** Shown when an export is refused, so the user learns why in one tap. */
    public static void promptForActivation(Activity source) {
        new AlertDialog.Builder(source)
            .setTitle(R.string.license_blocked_title)
            .setMessage(source.getString(R.string.license_blocked_msg,
                LicenseManager.FREE_TRIAL_EXPORTS))
            .setPositiveButton(R.string.license_goto,
                (d, w) -> open(source, true))
            .setNegativeButton(R.string.license_cancel, null)
            .show();
    }

    public static void open(Activity source, boolean fromBlocked) {
        Intent intent = new Intent(source, LicenseActivity.class);
        intent.putExtra(EXTRA_BLOCKED, fromBlocked);
        source.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_license);
        fromBlocked = getIntent().getBooleanExtra(EXTRA_BLOCKED, false);

        tvStatus = findViewById(R.id.tvStatus);
        tvTrial = findViewById(R.id.tvTrial);
        tvModel = findViewById(R.id.tvModel);
        tvMachineCode = findViewById(R.id.tvMachineCode);
        tvResult = findViewById(R.id.tvResult);
        tvSelfTest = findViewById(R.id.tvSelfTest);
        etCode = findViewById(R.id.etCode);
        btnActivate = findViewById(R.id.btnActivate);
        swTestMode = findViewById(R.id.swTestMode);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnCopy).setOnClickListener(v -> copyMachineCode());
        findViewById(R.id.btnActivate).setOnClickListener(v -> activate());
        findViewById(R.id.btnResetTrial).setOnClickListener(v -> resetTrial());

        tvModel.setText(Build.MANUFACTURER + " " + Build.MODEL);
        tvMachineCode.setText(LicenseManager.deviceCode(this));

        boolean selfTest = LicenseManager.selfTest();
        tvSelfTest.setText(selfTest ? R.string.license_selftest_ok : R.string.license_selftest_fail);
        tvSelfTest.setTextColor(Color.parseColor(selfTest ? "#2E7D32" : "#E53935"));
        // without a working verifier every code would be rejected, so say so loudly
        btnActivate.setEnabled(selfTest);

        swTestMode.setChecked(LicenseManager.isTestMode(this));
        swTestMode.setOnCheckedChangeListener((v, checked) -> {
            LicenseManager.setTestMode(this, checked);
            refresh();
            Toast.makeText(this, checked ? R.string.license_test_on : R.string.license_test_off,
                Toast.LENGTH_SHORT).show();
        });

        etCode.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (tvResult.getVisibility() == View.VISIBLE) hideResult();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        boolean activated = LicenseManager.isActivated(this);
        tvStatus.setText(activated ? R.string.license_activated : R.string.license_not_activated);
        tvStatus.setTextColor(Color.parseColor(activated ? "#2E7D32" : "#E53935"));

        if (activated) {
            tvTrial.setText(R.string.license_activated_ok);
        } else {
            int used = LicenseManager.exportCount(this);
            tvTrial.setText(getString(R.string.license_trial_used, used,
                LicenseManager.FREE_TRIAL_EXPORTS));
        }

        btnActivate.setVisibility(activated ? View.GONE : View.VISIBLE);
        etCode.setVisibility(activated ? View.GONE : View.VISIBLE);
        findViewById(R.id.panelTest).setVisibility(activated ? View.GONE : View.VISIBLE);
    }

    private void copyMachineCode() {
        String code = LicenseManager.deviceCode(this);
        ClipboardManager clipboard =
            (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText("machine-code", code));
        Toast.makeText(this, R.string.license_code_copied, Toast.LENGTH_SHORT).show();
    }

    private void activate() {
        String code = etCode.getText().toString();
        int result = LicenseManager.verify(this, code);
        if (result == LicenseManager.OK) {
            LicenseManager.activate(this, code);
            hideResult();
            Toast.makeText(this, R.string.license_activated_ok, Toast.LENGTH_LONG).show();
            if (fromBlocked) {
                finish();
                return;
            }
        }
        showResult(result);
        refresh();
    }

    private void showResult(int result) {
        int res;
        switch (result) {
            case LicenseManager.BAD_FORMAT: res = R.string.license_err_format; break;
            case LicenseManager.BAD_SIGNATURE: res = R.string.license_err_signature; break;
            case LicenseManager.WRONG_DEVICE: res = R.string.license_err_device; break;
            case LicenseManager.BAD_VERSION: res = R.string.license_err_version; break;
            default: res = R.string.license_err_signature; break;
        }
        tvResult.setText(res);
        tvResult.setTextColor(Color.parseColor("#E53935"));
        tvResult.setVisibility(View.VISIBLE);
    }

    private void hideResult() {
        tvResult.setVisibility(View.GONE);
    }

    private void resetTrial() {
        LicenseManager.setTestMode(this, false);
        swTestMode.setChecked(false);
        LicenseManager.resetTrial(this);
        refresh();
        Toast.makeText(this, R.string.license_reset_trial, Toast.LENGTH_SHORT).show();
    }
}

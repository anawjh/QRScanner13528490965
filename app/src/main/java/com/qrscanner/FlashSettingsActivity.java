package com.qrscanner;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class FlashSettingsActivity extends AppCompatActivity {

    public static final String PREF_NAME = "flash_settings";
    public static final String KEY_FLASH_MODE = "flash_mode";

    public static final String MODE_OFF = "off";
    public static final String MODE_ALWAYS_ON = "always_on";
    public static final String MODE_ON_SCAN = "on_scan";

    private RadioGroup rgFlashMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flash_settings);

        rgFlashMode = findViewById(R.id.rgFlashMode);
        Button btnSave = findViewById(R.id.btnSave);

        loadSettings();

        btnSave.setOnClickListener(v -> {
            saveSettings();
            Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        String mode = prefs.getString(KEY_FLASH_MODE, MODE_OFF);

        switch (mode) {
            case MODE_ALWAYS_ON:
                rgFlashMode.check(R.id.rbAlwaysOn);
                break;
            case MODE_ON_SCAN:
                rgFlashMode.check(R.id.rbOnScan);
                break;
            case MODE_OFF:
            default:
                rgFlashMode.check(R.id.rbOff);
                break;
        }
    }

    private void saveSettings() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        int checkedId = rgFlashMode.getCheckedRadioButtonId();
        if (checkedId == R.id.rbAlwaysOn) {
            editor.putString(KEY_FLASH_MODE, MODE_ALWAYS_ON);
        } else if (checkedId == R.id.rbOnScan) {
            editor.putString(KEY_FLASH_MODE, MODE_ON_SCAN);
        } else {
            editor.putString(KEY_FLASH_MODE, MODE_OFF);
        }

        editor.apply();
    }

    public static String getFlashMode(SharedPreferences prefs) {
        return prefs.getString(KEY_FLASH_MODE, MODE_OFF);
    }
}

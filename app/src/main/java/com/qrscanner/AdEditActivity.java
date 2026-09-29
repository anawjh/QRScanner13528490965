package com.qrscanner;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

/**
 * 手机本地内容编辑：安装程序即载体。
 * 保存后本机内容优先、云端自动更新暂停；可随时恢复云端更新。
 */
public class AdEditActivity extends AppCompatActivity {

    private EditText etContent;
    private TextView tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ad_edit);

        etContent = findViewById(R.id.etAdEdit);
        tvStatus = findViewById(R.id.tvAdEditStatus);
        Button btnSave = findViewById(R.id.btnAdSave);
        Button btnResume = findViewById(R.id.btnAdResumeCloud);

        boolean edited = AdStore.isLocalEdited(this);
        tvStatus.setText(edited
            ? getString(R.string.ad_edit_status_edited)
            : getString(R.string.ad_edit_status_cloud));
        etContent.setText(render(AdStore.all(this)));

        btnSave.setOnClickListener(v -> save());
        btnResume.setOnClickListener(v -> resumeCloud());
    }

    private void save() {
        List<AdEntry> parsed = AdPoller.parse(etContent.getText().toString());
        if (parsed.isEmpty()) {
            Toast.makeText(this, R.string.ad_edit_alert, Toast.LENGTH_SHORT).show();
            return;
        }
        AdStore.save(this, parsed);
        Toast.makeText(this, R.string.ad_edit_saved, Toast.LENGTH_SHORT).show();
        tvStatus.setText(R.string.ad_edit_status_edited);
        finish();
    }

    private void resumeCloud() {
        AdStore.setLocalEdited(this, false);
        AdPoller.pollAsync(this, ignored -> { });
        Toast.makeText(this, R.string.ad_edit_resumed, Toast.LENGTH_SHORT).show();
        tvStatus.setText(R.string.ad_edit_status_cloud);
        finish();
    }

    private static String render(List<AdEntry> entries) {
        StringBuilder sb = new StringBuilder();
        for (AdEntry e : entries) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(e.startHour).append('-').append(e.endHour).append('|').append(e.text);
            if (e.link != null && !e.link.isEmpty()) sb.append('|').append(e.link);
        }
        return sb.toString();
    }
}
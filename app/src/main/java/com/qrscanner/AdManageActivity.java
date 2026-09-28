package com.qrscanner;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class AdManageActivity extends AppCompatActivity implements AdAdapter.OnAction {

    private RecyclerView rvAds;
    private TextView tvEmpty;
    private AdAdapter adapter;
    private final List<AdEntry> entries = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ad_manage);

        rvAds = findViewById(R.id.rvAds);
        tvEmpty = findViewById(R.id.tvEmpty);

        adapter = new AdAdapter(this);
        rvAds.setLayoutManager(new LinearLayoutManager(this));
        rvAds.setAdapter(adapter);

        findViewById(R.id.btnAdd).setOnClickListener(v -> promptEntry(-1));
        findViewById(R.id.btnRemote).setOnClickListener(v -> promptRemote());
        findViewById(R.id.btnImport).setOnClickListener(v -> promptImport());
        refresh();
    }

    private void promptImport() {
        EditText input = new EditText(this);
        input.setHint(R.string.ad_import_hint);
        input.setSingleLine(false);
        input.setMinLines(4);
        input.setText(sampleJson());
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input);

        final AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(R.string.ad_import)
            .setView(box)
            .setPositiveButton(R.string.confirm, null)
            .setNeutralButton(R.string.ad_restore_builtin, (d, w) -> {
                AdStore.save(this, AdStore.bundled(this));
                AdFooterView.refreshAll();
                refresh();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            List<AdEntry> parsed = AdPoller.parse(input.getText().toString());
            if (parsed.isEmpty()) {
                Toast.makeText(this, R.string.ad_import_bad, Toast.LENGTH_LONG).show();
                return;
            }
            AdStore.save(this, parsed);
            AdFooterView.refreshAll();
            refresh();
            dialog.dismiss();
        });
    }

    private String sampleJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        for (AdEntry e : entries) {
            sb.append("  {\"start\":").append(e.startHour)
              .append(",\"end\":").append(e.endHour)
              .append(",\"text\":\"").append(e.text)
              .append("\",\"link\":\"").append(e.link).append("\"},\n");
        }
        sb.append("  {\"start\":0,\"end\":8,\"text\":\"广告内容\",\"link\":\"https://跳转链接\"}\n]");
        return sb.toString();
    }

    private void promptRemote() {
        EditText input = new EditText(this);
        input.setHint(R.string.ad_remote_hint);
        input.setText(AdStore.getRemoteUrl(this));
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input);

        final AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(R.string.ad_remote_title)
            .setView(box)
            .setPositiveButton(R.string.confirm, null)
            .setNeutralButton(R.string.ad_remote_clear, (d, w) -> {
                AdStore.setRemoteUrl(this, "");
                AdStore.clearRemoteCache(this);
                refresh();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            AdStore.setRemoteUrl(this, input.getText().toString());
            dialog.dismiss();
            refresh();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        entries.clear();
        entries.addAll(AdStore.all(this));
        adapter.submit(entries);
        tvEmpty.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
        String url = AdStore.getRemoteUrl(this);
        ((android.widget.TextView) findViewById(R.id.tvRemoteState)).setVisibility(
            url.isEmpty() ? View.GONE : View.VISIBLE);
        if (!url.isEmpty()) {
            ((android.widget.TextView) findViewById(R.id.tvRemoteState))
                .setText(getString(R.string.ad_remote_state, url));
        }
        AdPoller.pollAsync(this, entries2 -> {
            entries.clear();
            entries.addAll(entries2);
            adapter.submit(entries);
        });
    }

    private void persist() {
        AdStore.save(this, new ArrayList<>(entries));
        AdFooterView.refreshAll();
        refresh();
    }

    @Override
    public void onEdit(int position) {
        if (position >= 0 && position < entries.size()) promptEntry(position);
    }

    @Override
    public void onDelete(int position) {
        if (position < 0 || position >= entries.size()) return;
        new AlertDialog.Builder(this)
            .setTitle(R.string.ad_delete)
            .setMessage(getString(R.string.ad_delete_msg, entries.get(position).text))
            .setPositiveButton(R.string.confirm, (d, w) -> {
                entries.remove(position);
                persist();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void promptEntry(int position) {
        final boolean isNew = position < 0;
        AdEntry existing = isNew ? new AdEntry(0, 24, "", "") : entries.get(position);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);

        EditText start = input(getString(R.string.ad_start_hour));
        start.setText(String.valueOf(existing.startHour));
        EditText end = input(getString(R.string.ad_end_hour));
        end.setText(String.valueOf(existing.endHour));
        EditText text = input(getString(R.string.ad_content));
        text.setText(existing.text);
        EditText link = input(getString(R.string.ad_link));
        link.setText(existing.link);

        box.addView(start);
        box.addView(end);
        box.addView(text);
        box.addView(link);

        final AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(isNew ? R.string.ad_add : R.string.ad_edit)
            .setView(box)
            .setPositiveButton(R.string.confirm, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int s = parseHour(start.getText().toString(), -1);
            int e = parseHour(end.getText().toString(), -1);
            String t = text.getText().toString().trim();
            String l = link.getText().toString().trim();

            if (s < 0 || s > 23) {
                toast(R.string.ad_bad_hour);
                return;
            }
            if (e < 0 || e > 24) {
                toast(R.string.ad_bad_hour_end);
                return;
            }
            if (t.isEmpty()) {
                toast(R.string.ad_empty_content);
                return;
            }
            if (isNew) {
                entries.add(new AdEntry(s, e, t, l));
            } else {
                AdEntry old = entries.get(position);
                old.startHour = s;
                old.endHour = e;
                old.text = t;
                old.link = l;
            }
            persist();
            dialog.dismiss();
        });
    }

    private EditText input(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(false);
        return e;
    }

    private int parseHour(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private void toast(int res) {
        Toast.makeText(this, res, Toast.LENGTH_SHORT).show();
    }
}

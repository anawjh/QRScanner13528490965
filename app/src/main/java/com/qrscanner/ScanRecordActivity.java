package com.qrscanner;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ScanRecordActivity extends AppCompatActivity {

    private ProjectManager projectManager;
    private ScanProject project;
    private ScanRecordAdapter adapter;
    private TextView tvHeader;
    private TextView tvEmpty;
    private Button btnExport;
    private Button btnClear;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_record);

        projectManager = ProjectManager.getInstance(this);
        tvHeader = findViewById(R.id.tvHeader);
        tvEmpty = findViewById(R.id.tvEmpty);
        btnExport = findViewById(R.id.btnExport);
        btnClear = findViewById(R.id.btnClear);

        RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ScanRecordAdapter(project().records, this::deleteRecord, this::editRemark,
            path -> startActivity(PhotoViewerActivity.intent(this, path)));
        list.setAdapter(adapter);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnProject).setOnClickListener(v -> showProjectDialog());
        findViewById(R.id.btnNewProject).setOnClickListener(v -> showNewProjectDialog());
        btnClear.setOnClickListener(v -> showClearDialog());
        btnExport.setOnClickListener(v ->
            ExcelExporter.showExportChoice(this, project().records, project().name));
    }

    @Override
    protected void onResume() {
        super.onResume();
        adapter.setRecords(project().records);
        updateHeader();
    }

    private ScanProject project() {
        if (project == null) project = projectManager.getCurrent();
        return project;
    }

    private void updateHeader() {
        ScanProject current = project();
        tvHeader.setText(getString(R.string.records_header,
            current.name, current.records.size()));
        tvEmpty.setVisibility(current.records.isEmpty() ? View.VISIBLE : View.GONE);
        btnExport.setEnabled(!current.records.isEmpty());
        btnClear.setEnabled(!current.records.isEmpty());
    }

    // ======================== 项目 ========================

    private void showProjectDialog() {
        List<String> projects = projectManager.listProjects();
        if (projects.isEmpty()) {
            showNewProjectDialog();
            return;
        }
        String currentName = project().name;
        int checked = projects.indexOf(currentName);

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_project_list, null);
        ListView listView = view.findViewById(R.id.lvProjects);
        listView.setAdapter(new ArrayAdapter<>(this,
            android.R.layout.simple_list_item_single_choice, projects));
        listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        if (checked >= 0) listView.setItemChecked(checked, true);

        final AlertDialog picker = new AlertDialog.Builder(this)
            .setTitle(R.string.menu_open_project)
            .setView(view)
            .setNeutralButton(R.string.btn_delete, (d, w) -> confirmDeleteProject(currentName))
            .setNegativeButton(android.R.string.cancel, null)
            .create();

        listView.setOnItemClickListener((parent, v, position, id) -> {
            ScanProject selected = projectManager.load(projects.get(position));
            if (selected == null) {
                Toast.makeText(this, R.string.records_open_failed, Toast.LENGTH_SHORT).show();
                return;
            }
            projectManager.setCurrent(selected.name);
            project = selected;
            adapter.setRecords(project.records);
            updateHeader();
            picker.dismiss();
        });

        picker.show();
    }

    private void confirmDeleteProject(String name) {
        new AlertDialog.Builder(this)
            .setTitle(R.string.records_delete_project)
            .setMessage(name)
            .setPositiveButton(R.string.btn_delete, (d, w) -> {
                projectManager.delete(name);
                project = projectManager.getCurrent();
                adapter.setRecords(project.records);
                updateHeader();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void showNewProjectDialog() {
        EditText input = new EditText(this);
        input.setHint(R.string.records_new_hint);
        new AlertDialog.Builder(this)
            .setTitle(R.string.menu_new_project)
            .setView(input)
            .setPositiveButton(android.R.string.ok, (d, w) -> {
                String name = input.getText().toString().trim();
                if (name.isEmpty()) projectManager.createNew();
                else projectManager.createNew(name);
                project = projectManager.getCurrent();
                adapter.setRecords(project.records);
                updateHeader();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    // ======================== 记录 ========================

    private void deleteRecord(int position) {
        if (position < 0 || position >= project().records.size()) return;
        new AlertDialog.Builder(this)
            .setTitle(R.string.delete_title)
            .setMessage(R.string.delete_confirm)
            .setPositiveButton(android.R.string.ok, (d, w) -> {
                project().records.remove(position);
                save();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void editRemark(int position) {
        if (position < 0 || position >= project().records.size()) return;
        ScanRecord record = project().records.get(position);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_remark, null);
        EditText input = view.findViewById(R.id.etRemark);
        input.setText(record.getRemark());
        new AlertDialog.Builder(this)
            .setTitle(R.string.edit_remark_title)
            .setView(view)
            .setPositiveButton(android.R.string.ok, (d, w) -> {
                record.setRemark(input.getText().toString().trim());
                save();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void showClearDialog() {
        if (project().records.isEmpty()) return;
        new AlertDialog.Builder(this)
            .setTitle(R.string.clear_all_title)
            .setMessage(getString(R.string.clear_all_msg, project().records.size()))
            .setPositiveButton(android.R.string.ok, (d, w) -> {
                project().records.clear();
                save();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void save() {
        projectManager.save(project());
        adapter.notifyDataSetChanged();
        updateHeader();
    }
}

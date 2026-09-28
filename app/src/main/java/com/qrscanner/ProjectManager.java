package com.qrscanner;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ProjectManager {

    private static final String PREFS_NAME = "scan_projects";
    private static final String KEY_PROJECTS = "project_names";
    private static final String KEY_CURRENT = "current_project";
    private final SharedPreferences prefs;

    private static ProjectManager instance;

    private ProjectManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized ProjectManager getInstance(Context context) {
        if (instance == null) {
            instance = new ProjectManager(context);
        }
        return instance;
    }

    public ScanProject getCurrent() {
        String name = prefs.getString(KEY_CURRENT, "");
        if (!name.isEmpty()) {
            ScanProject project = load(name);
            if (project != null) return project;
        }
        return createNew();
    }

    public void setCurrent(String name) {
        prefs.edit().putString(KEY_CURRENT, name).apply();
    }

    public void createNew(String name) {
        ScanProject project = new ScanProject(name);
        save(project);
        setCurrent(name);
    }

    public ScanProject createNew() {
        String name = "项目_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(new Date());
        createNew(name);
        return load(name);
    }

    public void save(ScanProject project) {
        if (project == null) return;
        project.touch();

        Set<String> names = new HashSet<>(prefs.getStringSet(KEY_PROJECTS, new HashSet<>()));
        names.add(project.name);

        String json = project.toJson().toString();
        prefs.edit()
                .putStringSet(KEY_PROJECTS, names)
                .putString("project_" + project.name, json)
                .apply();
    }

    public ScanProject load(String name) {
        String json = prefs.getString("project_" + name, null);
        if (json == null) return null;
        try {
            return ScanProject.fromJson(new JSONObject(json));
        } catch (Exception e) {
            return null;
        }
    }

    public List<String> listProjects() {
        Set<String> names = prefs.getStringSet(KEY_PROJECTS, new HashSet<>());
        List<String> list = new ArrayList<>(names);
        list.sort((a, b) -> b.compareTo(a));
        return list;
    }

    public void delete(String name) {
        Set<String> names = new HashSet<>(prefs.getStringSet(KEY_PROJECTS, new HashSet<>()));
        names.remove(name);
        prefs.edit()
                .putStringSet(KEY_PROJECTS, names)
                .remove("project_" + name)
                .apply();
    }
}

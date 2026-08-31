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
    private final SharedPreferences prefs;

    public ProjectManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public ScanProject createNew() {
        String name = "项目_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(new Date());
        ScanProject project = new ScanProject(name);
        save(project);
        return project;
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

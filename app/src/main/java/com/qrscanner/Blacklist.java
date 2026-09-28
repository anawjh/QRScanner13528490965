package com.qrscanner;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Blacklist {

    public static final int MAX_LINES = 8000;

    private static final String PREF_NAME = "scan_blacklist";
    private static final String KEY_CODES = "codes";
    private static final String KEY_ACTION = "action";

    public static final String ACTION_SKIP = "skip";
    public static final String ACTION_KEEP = "keep";

    private Blacklist() {}

    public static Set<String> codes(Context context) {
        return new HashSet<>(prefs(context).getStringSet(KEY_CODES, new HashSet<>()));
    }

    public static int count(Context context) {
        return codes(context).size();
    }

    public static boolean contains(Context context, String content) {
        if (content == null) return false;
        String trimmed = content.trim();
        if (trimmed.isEmpty()) return false;
        return codes(context).contains(trimmed);
    }

    public static void replaceAll(Context context, List<String> lines) {
        Set<String> set = new HashSet<>();
        for (String line : lines) {
            String value = line.trim();
            if (!value.isEmpty()) set.add(value);
        }
        prefs(context).edit().putStringSet(KEY_CODES, set).apply();
    }

    public static void add(Context context, String content) {
        if (content == null) return;
        String value = content.trim();
        if (value.isEmpty()) return;
        Set<String> set = codes(context);
        if (set.size() >= MAX_LINES) return;
        set.add(value);
        prefs(context).edit().putStringSet(KEY_CODES, set).apply();
    }

    public static List<String> lines(Context context) {
        List<String> list = new ArrayList<>(codes(context));
        list.sort(String::compareTo);
        return list;
    }

    public static String getAction(Context context) {
        return prefs(context).getString(KEY_ACTION, "");
    }

    public static void setAction(Context context, String action) {
        prefs(context).edit().putString(KEY_ACTION, action).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }
}

package com.qrscanner;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class GenerateStore {

    private static final String PREFS = "generate_records";
    private static final String KEY_RECORDS = "records";
    private static final int MAX_HISTORY = 100;

    private GenerateStore() {}

    public static void add(Context context, GenerateRecord record) {
        if (record.getItems().isEmpty()) return;
        record.setId(System.currentTimeMillis());
        record.setTime(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(new Date()));
        List<GenerateRecord> list = list(context);
        list.add(0, record);
        while (list.size() > MAX_HISTORY) list.remove(list.size() - 1);
        save(context, list);
    }

    public static List<GenerateRecord> list(Context context) {
        List<GenerateRecord> result = new ArrayList<>();
        String raw = prefs(context).getString(KEY_RECORDS, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                result.add(GenerateRecord.fromJson(array.getJSONObject(i)));
            }
        } catch (JSONException ignored) {
        }
        return result;
    }

    public static void delete(Context context, long id) {
        List<GenerateRecord> list = list(context);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId() == id) {
                list.remove(i);
                break;
            }
        }
        save(context, list);
    }

    public static void clear(Context context) {
        prefs(context).edit().remove(KEY_RECORDS).apply();
    }

    private static void save(Context context, List<GenerateRecord> list) {
        JSONArray array = new JSONArray();
        for (GenerateRecord record : list) {
            try {
                array.put(record.toJson());
            } catch (JSONException ignored) {
            }
        }
        prefs(context).edit().putString(KEY_RECORDS, array.toString()).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}

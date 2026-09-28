package com.qrscanner;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public final class AdStore {

    private static final String PREF_NAME = "ad_store";
    private static final String KEY_ENTRIES = "entries";
    private static final String KEY_REMOTE = "remote_url";
    private static final String KEY_REMOTE_CACHE = "remote_cache";
    private static final String KEY_REMOTE_TIME = "remote_time";
    private static final long REMOTE_TTL_MS = 30 * 60_000L;

    private AdStore() {}

    public static String getRemoteUrl(Context context) {
        return prefs(context).getString(KEY_REMOTE, "").trim();
    }

    public static void setRemoteUrl(Context context, String url) {
        prefs(context).edit().putString(KEY_REMOTE, url == null ? "" : url.trim()).apply();
    }

    public static void clearRemoteCache(Context context) {
        prefs(context).edit().remove(KEY_REMOTE_CACHE).remove(KEY_REMOTE_TIME).apply();
    }

    static void saveRemote(Context context, List<AdEntry> entries) {
        JSONArray arr = new JSONArray();
        for (AdEntry e : entries) arr.put(e.toJson());
        prefs(context).edit()
            .putString(KEY_REMOTE_CACHE, arr.toString())
            .putLong(KEY_REMOTE_TIME, System.currentTimeMillis())
            .apply();
    }

    private static List<AdEntry> remoteEntries(Context context) {
        String raw = prefs(context).getString(KEY_REMOTE_CACHE, null);
        if (raw == null) return null;
        long time = prefs(context).getLong(KEY_REMOTE_TIME, 0L);
        if (System.currentTimeMillis() - time > REMOTE_TTL_MS) return null;
        List<AdEntry> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) list.add(AdEntry.fromJson(o));
            }
        } catch (Exception ignored) {
        }
        return list.isEmpty() ? null : list;
    }

    public static List<AdEntry> all(Context context) {
        if (!getRemoteUrl(context).isEmpty()) {
            List<AdEntry> remote = remoteEntries(context);
            if (remote != null) return remote;
        }
        SharedPreferences prefs = prefs(context);
        String raw = prefs.getString(KEY_ENTRIES, null);
        List<AdEntry> list = new ArrayList<>();
        if (raw != null) {
            try {
                JSONArray arr = new JSONArray(raw);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o != null) list.add(AdEntry.fromJson(o));
                }
            } catch (Exception ignored) {
            }
        }
        if (list.isEmpty()) list.addAll(bundled(context));
        if (list.isEmpty()) list.add(defaultEntry(context));
        return list;
    }

    /**
     * 广告内容随安装包内置，首次启动时使用，完全离线，不依赖任何服务器。
     */
    public static List<AdEntry> bundled(Context context) {
        List<AdEntry> list = new ArrayList<>();
        try {
            InputStream in = context.getAssets().open("ads.json");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
            list.addAll(AdPoller.parse(out.toString("UTF-8")));
        } catch (Exception ignored) {
        }
        return list;
    }

    public static void save(Context context, List<AdEntry> entries) {
        JSONArray arr = new JSONArray();
        for (AdEntry e : entries) arr.put(e.toJson());
        prefs(context).edit().putString(KEY_ENTRIES, arr.toString()).apply();
    }

    public static AdEntry current(Context context) {
        return current(context, Calendar.getInstance().get(Calendar.HOUR_OF_DAY));
    }

    public static AdEntry current(Context context, int hour) {
        for (AdEntry e : all(context)) {
            if (e.matches(hour)) return e;
        }
        return defaultEntry(context);
    }

    public static AdEntry defaultEntry(Context context) {
        return new AdEntry(0, 24, context.getString(R.string.footer_ad), "");
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }
}

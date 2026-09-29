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
    private static final String KEY_REMOTE_ERROR = "remote_error";
    private static final String KEY_REMOTE_ACTIVE = "remote_active";

    /**
     * 广告内容由开发者设计并维护：默认内置，联网时从中转源自动更新。
     * 使用者的手机上没有任何编辑入口。
     */
    private static final String[] DEFAULT_URLS = {
        "https://raw.githubusercontent.com/anawjh/QRScanner13528490965/feature/v3/ads.json"
    };

    private AdStore() {}

    /** 实际要尝试的内容源列表。 */
    public static String[] sources(Context context) {
        String override = prefs(context).getString(KEY_REMOTE, "");
        if (override == null || override.trim().isEmpty()) return DEFAULT_URLS;
        return new String[]{override.trim()};
    }

    public static String getRemoteActive(Context context) {
        String a = prefs(context).getString(KEY_REMOTE_ACTIVE, "");
        return a == null || a.isEmpty() ? DEFAULT_URLS[0] : a;
    }

    public static String getRemoteUrl(Context context) {
        return getRemoteActive(context);
    }

    public static void setRemoteUrl(Context context, String url) {
        prefs(context).edit().putString(KEY_REMOTE, url == null ? "" : url.trim()).apply();
    }

    public static void clearRemoteCache(Context context) {
        prefs(context).edit()
            .remove(KEY_REMOTE_CACHE)
            .remove(KEY_REMOTE_TIME)
            .remove(KEY_REMOTE_ERROR)
            .remove(KEY_REMOTE_ACTIVE)
            .apply();
    }

    static void saveRemote(Context context, List<AdEntry> entries, String url) {
        JSONArray arr = new JSONArray();
        for (AdEntry e : entries) arr.put(e.toJson());
        prefs(context).edit()
            .putString(KEY_REMOTE_CACHE, arr.toString())
            .putString(KEY_REMOTE_ACTIVE, url)
            .putLong(KEY_REMOTE_TIME, System.currentTimeMillis())
            .remove(KEY_REMOTE_ERROR)
            .apply();
    }

    static void saveRemoteError(Context context, String message) {
        prefs(context).edit().putString(KEY_REMOTE_ERROR, message == null ? "" : message).apply();
    }

    public static boolean hasRemoteCache(Context context) {
        return prefs(context).getString(KEY_REMOTE_CACHE, null) != null;
    }

    public static String getRemoteError(Context context) {
        return prefs(context).getString(KEY_REMOTE_ERROR, "");
    }

    public static long getRemoteTime(Context context) {
        return prefs(context).getLong(KEY_REMOTE_TIME, 0L);
    }

    /**
     * 离线时沿用最近一次拉取到的内容，不做过期丢弃；
     * 只有真正拉取成功才会被新内容替换。
     */
    private static List<AdEntry> remoteEntries(Context context) {
        String raw = prefs(context).getString(KEY_REMOTE_CACHE, null);
        if (raw == null) return null;
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
        List<AdEntry> remote = remoteEntries(context);
        if (remote != null) return remote;
        List<AdEntry> local = localEntries(context);
        if (local.isEmpty()) local = bundled(context);
        if (local.isEmpty()) local.add(defaultEntry(context));
        return local;
    }

    /** 手机上保存过的内容（首次为空，回落到安装包内置）。 */
    private static List<AdEntry> localEntries(Context context) {
        List<AdEntry> list = new ArrayList<>();
        String raw = prefs(context).getString(KEY_ENTRIES, null);
        if (raw == null) return list;
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) list.add(AdEntry.fromJson(o));
            }
        } catch (Exception ignored) {
        }
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

    /** 保存内容到本地共享存储（预留，供开发者工具使用）。 */
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

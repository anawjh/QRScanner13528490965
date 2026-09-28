package com.qrscanner;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public final class AdPoller {

    private static final String TAG = "AdPoller";
    private static final int TIMEOUT_MS = 8000;
    private static final int MAX_BYTES = 256 * 1024;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static Thread worker;

    public interface Callback {
        void onAdLoaded(List<AdEntry> entries);
    }

    private AdPoller() {}

    /** 拉取一次并写入缓存；立即返回，不阻塞调用方。 */
    public static void pollAsync(final Context context, final Callback callback) {
        final Context app = context.getApplicationContext();
        final String url = AdStore.getRemoteUrl(app);
        if (url.isEmpty()) return;

        if (worker != null && worker.isAlive()) return;

        worker = new Thread(() -> {
            try {
                String body = fetch(url);
                List<AdEntry> parsed = parse(body);
                if (parsed.isEmpty()) {
                    AdStore.saveRemoteError(app, "内容为空或格式无法识别");
                } else {
                    AdStore.saveRemote(app, parsed);
                    MAIN.post(() -> {
                        if (callback != null) callback.onAdLoaded(parsed);
                    });
                }
            } catch (Exception e) {
                String msg = e.getMessage() == null ? e.toString() : e.getMessage();
                Log.w(TAG, "poll failed: " + msg);
                AdStore.saveRemoteError(app, msg);
            }
        });
        worker.setDaemon(true);
        worker.start();
    }

    private static String fetch(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setRequestMethod("GET");
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("Accept", "application/json, text/plain, */*");
        conn.setRequestProperty("User-Agent", "QRScanner-Android");
        try {
            int code = conn.getResponseCode();
            InputStream in = code >= 200 && code < 300
                ? conn.getInputStream() : conn.getErrorStream();
            if (in == null) throw new IllegalStateException("http " + code);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > MAX_BYTES) break;
            }
            in.close();
            return out.toString("UTF-8");
        } finally {
            conn.disconnect();
        }
    }

    /**
     * 支持三种格式：
     * 1) JSON 数组：[{"start":0,"end":8,"text":"...","link":"https://..."}]
     * 2) JSON 对象：{"entries":[...]}，即仓库里 ads.json 的格式
     * 3) 纯文本，每行一条：0-8|广告内容|https://跳转链接
     */
    static List<AdEntry> parse(String body) {
        List<AdEntry> out = new ArrayList<>();
        if (body == null) return out;
        String trimmed = body.trim();
        if (trimmed.isEmpty()) return out;

        if (trimmed.startsWith("{")) {
            try {
                JSONObject root = new JSONObject(trimmed);
                JSONArray arr = root.optJSONArray("entries");
                if (arr == null) arr = root.optJSONArray("data");
                if (arr != null) return fromJson(arr);
            } catch (Exception ignored) {
            }
        }

        if (trimmed.startsWith("[")) {
            try {
                return fromJson(new JSONArray(trimmed));
            } catch (Exception ignored) {
            }
        }

        String[] lines = trimmed.split("\\r?\\n");
        for (String line : lines) {
            String s = line.trim();
            if (s.isEmpty() || s.startsWith("#") || s.startsWith("//")) continue;
            String[] parts = s.split("\\|", 3);
            String range = parts[0].trim();
            String text = parts.length > 1 ? parts[1].trim() : "";
            String link = parts.length > 2 ? parts[2].trim() : "";
            if (text.isEmpty()) continue;
            int[] hours = parseRange(range);
            if (hours == null) continue;
            out.add(new AdEntry(hours[0], hours[1], text, link));
        }
        return out;
    }

    /** 解析 "0-8"、"0:00-8:00"、"8"（单值当作到当天结束）。 */
    private static List<AdEntry> fromJson(JSONArray arr) {
        List<AdEntry> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            String text = o.optString("text", o.optString("content", ""));
            if (text.isEmpty()) continue;
            out.add(new AdEntry(
                o.optInt("start", 0),
                o.optInt("end", 24),
                text,
                o.optString("link", o.optString("url", ""))));
        }
        return out;
    }

    static int[] parseRange(String raw) {
        try {
            String s = raw.trim();
            int sep = -1;
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '-' || c == '~' || c == '至' || c == ':') {
                    if (c == ':' && i < s.length() - 1
                        && s.charAt(i + 1) >= '0' && s.charAt(i + 1) <= '9') {
                        continue;
                    }
                    sep = i;
                    break;
                }
            }
            if (sep < 0) {
                int h = Integer.parseInt(s);
                if (h < 0 || h > 24) return null;
                return new int[]{h, 24};
            }
            String left = s.substring(0, sep).trim();
            String right = s.substring(sep + 1).trim();
            int start = Integer.parseInt(left);
            int end = Integer.parseInt(right);
            if (start < 0 || start > 23 || end < 0 || end > 24) return null;
            return new int[]{start, end};
        } catch (Exception e) {
            return null;
        }
    }
}

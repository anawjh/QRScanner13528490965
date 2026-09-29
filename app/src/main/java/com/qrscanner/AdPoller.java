package com.qrscanner;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Inflater;

public final class AdPoller {

    private static final String TAG = "AdPoller";
    private static final int TIMEOUT_MS = 8000;
    private static final int MAX_BYTES = 256 * 1024;
    private static final Pattern ROW_TEXT = Pattern.compile("[\\u4e00-\\u9fff][\\u4e00-\\u9fff0-9A-Za-z ]*");

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static Thread worker;

    public interface Callback {
        void onAdLoaded(List<AdEntry> entries);
    }

    private AdPoller() {}

    /** 按顺序拉取云端内容源，第一个成功的即生效；全部失败时汇报原因。 */
    public static void pollAsync(final Context context, final Callback callback) {
        final Context app = context.getApplicationContext();
        final String[] sources = AdStore.sources(app);
        if (sources.length == 0) return;

        if (worker != null && worker.isAlive()) return;

        worker = new Thread(() -> {
            StringBuilder errs = new StringBuilder();
            for (String url : sources) {
                try {
                    List<AdEntry> parsed;
                    if (url.startsWith("https://docs.qq.com/") || url.startsWith("http://docs.qq.com/")) {
                        parsed = fetchTencentSheet(url);
                    } else {
                        parsed = parse(fetch(url));
                    }
                    if (parsed.isEmpty()) {
                        errs.append(url).append(": 内容为空或无法识别; ");
                        continue;
                    }
                    List<AdEntry> enriched = enrichFromLinks(parsed);
                    AdStore.saveRemote(app, enriched, url);
                    MAIN.post(() -> {
                        if (callback != null) callback.onAdLoaded(enriched);
                    });
                    return;
                } catch (Exception e) {
                    String msg = e.getMessage() == null ? e.toString() : e.getMessage();
                    Log.w(TAG, "poll failed [" + url + "]: " + msg);
                    errs.append(url).append(": ").append(msg).append("; ");
                }
            }
            final String detail = errs.toString().replaceAll("; $", "");
            AdStore.saveRemoteError(app, detail);
            MAIN.post(() -> {
                if (callback != null) callback.onAdLoaded(new ArrayList<AdEntry>());
            });
        });
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * 腾讯文档内容源：先访问文档页建立会话，再请求 dop-api/opendoc，
     * 解出表格里的文字（广告内容列）与网址（跳转链接列）。
     * 返回单条 0-24 条目：文案=文档全部广告语，链接=文档里的首个 http(s) 地址。
     */
    static List<AdEntry> fetchTencentSheet(String pageUrl) throws Exception {
        java.net.URL u = new java.net.URL(pageUrl);
        String path = u.getPath();
        final String localId = path.substring(path.lastIndexOf('/') + 1);
        String tabId = "";
        String q = u.getQuery();
        if (q != null) {
            for (String p : q.split("&")) {
                if (p.startsWith("tab=") && p.length() > 4) { tabId = p.substring(4); break; }
            }
        }
        final String localTab = tabId;
        final String referer = pageUrl;

        // 1) 打开文档页，收集会话 cookie
        String cookies = "";
        HttpURLConnection pageConn = (HttpURLConnection) new URL("https://docs.qq.com/sheet/" + localId
            + (localTab.isEmpty() ? "" : "?tab=" + localTab)).openConnection();
        pageConn.setConnectTimeout(TIMEOUT_MS);
        pageConn.setReadTimeout(TIMEOUT_MS);
        pageConn.setRequestMethod("GET");
        pageConn.setInstanceFollowRedirects(true);
        pageConn.setRequestProperty("User-Agent", UA);
        pageConn.setRequestProperty("Accept", "*/*");
        pageConn.connect();
        int pageCode = pageConn.getResponseCode();
        if (pageCode >= 200 && pageCode < 400) {
            java.util.List<String> setCookies = pageConn.getHeaderFields().get("Set-Cookie");
            StringBuilder sb = new StringBuilder();
            if (setCookies != null) {
                for (String setC : setCookies) {
                    for (String part : setC.split(";")) {
                        String kv = part.trim();
                        if (kv.contains("=") && !kv.startsWith("expires") && !kv.startsWith("path")
                            && !kv.startsWith("domain") && !kv.startsWith("max-age") && !kv.startsWith("httponly")
                            && !kv.startsWith("samesite") && !kv.startsWith("secure")) {
                            if (sb.length() > 0) sb.append("; ");
                            sb.append(kv);
                        }
                    }
                }
            }
            cookies = sb.toString();
        }
        pageConn.disconnect();

        // 2) 数据接口
        StringBuilder api = new StringBuilder(
            "https://docs.qq.com/dop-api/opendoc?tab=").append(localTab)
            .append("&u=&noEscape=1&enableSmartsheetSplit=1&startrow=0&endrow=60&needSheetState=1")
            .append("&sliceStates=1&block_end_col=31&block_end_row=255&block_start_col=0&block_start_row=0")
            .append("&id=").append(localId)
            .append("&normal=1&outformat=1&wb=1&nowb=0&xsrf=")
            .append("&callback=clientVarsCallback");
        HttpURLConnection conn = (HttpURLConnection) new URL(api.toString()).openConnection();
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", UA);
        conn.setRequestProperty("Accept", "*/*");
        conn.setRequestProperty("Referer", referer);
        if (!cookies.isEmpty()) conn.setRequestProperty("Cookie", cookies);
        try {
            int code = conn.getResponseCode();
            InputStream in = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
            if (in == null) throw new IllegalStateException("http " + code);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > MAX_BYTES) break;
            }
            in.close();
            String text = out.toString("UTF-8");
            if (text.isEmpty()) {
                throw new IllegalStateException("opendoc http " + code);
            }
            List<AdEntry> list = decodeTencentJsonp(text);
            if (list.isEmpty()) throw new IllegalStateException("opendoc 无表格数据");
            return list;
        } finally {
            conn.disconnect();
        }
    }

    private static final String UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36";

    /** JSONP：可能是纯 JSON，也可能是 clientVarsCallback({...})；取首尾花括号解析。 */
    static List<AdEntry> decodeTencentJsonp(String jsonp) throws Exception {
        int start = jsonp.indexOf('{');
        if (start < 0) return new ArrayList<>();
        int depth = 0;
        int end = jsonp.length() - 1;
        for (int i = start; i < jsonp.length(); i++) {
            char ch = jsonp.charAt(i);
            if (ch == '{') depth++;
            else if (ch == '}' && --depth == 0) { end = i; break; }
        }
        String json = jsonp.substring(start, end + 1);
        JSONObject root = new JSONObject(json);
        JSONObject cv = root.optJSONObject("clientVars");
        JSONObject c = cv == null ? null : cv.optJSONObject("collab_client_vars");
        if (c == null) return new ArrayList<>();
        JSONObject att = c.optJSONObject("initialAttributedText");
        if (att == null) return new ArrayList<>();
        JSONArray arr = att.optJSONArray("text");
        StringBuilder rawCells = new StringBuilder();
        String link = "";
        for (int i = 0; i < (arr == null ? 0 : arr.length()); i++) {
            JSONObject t0 = arr.optJSONObject(i);
            if (t0 == null) continue;
            JSONArray blocks = t0.optJSONArray("block_datas");
            for (int b = 0; b < (blocks == null ? 0 : blocks.length()); b++) {
                String rs = blocks.optJSONObject(b).optString("related_sheet", "");
                if (rs.isEmpty()) continue;
                byte[] inflated = inflate(Base64.getDecoder().decode(rs));
                String s = new String(inflated, "UTF-8");
                rawCells.append(s);
                if (link.isEmpty()) {
                    Matcher lm = Pattern.compile("https?://[\\w.-]+/[\\w?=&/.%-]*").matcher(s);
                    if (lm.find()) link = lm.group();
                }
            }
        }
        List<AdEntry> list = new ArrayList<>();
        String[] segments = rawCells.toString().split(Pattern.quote(ROW_MARKER));
        String lastGood = "";
        for (int i = 0; i < 24 && i < segments.length; i++) {
            String clean = cleanSheetSegment(segments[i]);
            if (clean.isEmpty()) clean = lastGood;
            if (clean.isEmpty()) continue;
            lastGood = clean;
            list.add(new AdEntry(i, i + 1, clean, link));
        }
        return list;
    }

    private static final String ROW_MARKER = "每个小时展示不一样内容";

    private static String cleanSheetSegment(String seg) {
        StringBuilder sb = new StringBuilder();
        Matcher m = ROW_TEXT.matcher(seg);
        boolean first = true;
        while (m.find()) {
            String v = m.group().trim();
            if (v.isEmpty() || v.equals("每日小时时间段") || v.equals("广告内容") || v.equals("转跳链接")) continue;
            if (!first) sb.append('，');
            sb.append(v);
            first = false;
        }
        return sb.toString().replaceAll("\\s+", " ").trim();
    }

    private static byte[] inflate(byte[] data) throws Exception {
        Inflater inf = new Inflater(false);
        inf.setInput(data);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(data.length * 2);
        byte[] buf = new byte[4096];
        while (!inf.finished()) {
            int n = inf.inflate(buf);
            if (n > 0) out.write(buf, 0, n);
        }
        inf.end();
        return out.toByteArray();
    }

    /**
     * 以链接内容为准：如果广告条目的链接指向一个“内容页”
     * （含 marquee 的 HTML / JSON / 管道文本 / 纯文本），
     * 就把该页面上的文字抓下来替换为广告文案，跳转仍指向该链接。
     * 普通页面（视频、防爬等）保持原始文案，避免抓到噪声。
     */
    static List<AdEntry> enrichFromLinks(List<AdEntry> entries) {
        List<AdEntry> out = new ArrayList<>(entries);
        java.util.Map<String, AdEntry> byLink = new java.util.HashMap<>();
        for (int i = 0; i < out.size(); i++) {
            AdEntry e = out.get(i);
            if (e.link.isEmpty()) continue;
            AdEntry content;
            if (byLink.containsKey(e.link)) {
                content = byLink.get(e.link);
            } else {
                content = fetchLinkContent(e.link);
                byLink.put(e.link, content);
            }
            if (content != null && !content.text.isEmpty()) {
                out.set(i, new AdEntry(e.startHour, e.endHour, content.text,
                    content.link.isEmpty() ? e.link : content.link));
            }
        }
        return out;
    }

    private static AdEntry fetchLinkContent(String link) {
        try {
            String body = fetch(link);
            if (body == null || !isAdContent(body)) return null;
            List<AdEntry> parsed = parse(body);
            for (AdEntry p : parsed) {
                if (!p.text.isEmpty()) return p;
            }
        } catch (Exception e) {
            Log.w(TAG, "link content fetch failed [" + link + "]: "
                + (e.getMessage() == null ? e.toString() : e.getMessage()));
        }
        return null;
    }

    /** 只把“明确是广告内容”的响应当作内容源，避免把普通网页噪声当文案。 */
    static boolean isAdContent(String body) {
        if (body == null) return false;
        String t = body.trim();
        if (t.startsWith("{") || t.startsWith("[")) return true;
        String lower = t.toLowerCase();
        if (lower.contains("marquee")) return true;
        for (String line : t.split("\\r?\\n")) {
            String s = line.trim();
            if (s.isEmpty()) continue;
            return s.contains("|");
        }
        return false;
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
     * 支持四种格式：
     * 1) JSON 数组：[{"start":0,"end":8,"text":"...","link":"https://..."}]
     * 2) JSON 对象：{"entries":[...]}，即仓库里 ads.json 的格式
     * 3) 纯文本，每行一条：0-8|广告内容|https://跳转链接
     * 4) HTML 页面：取 marquee 广告文字作为文案、页面里 window.open() 的第一个地址作为跳转链接
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

        if (!trimmed.startsWith("<")) {
            return fromText(trimmed);
        }

        AdEntry html = fromHtml(trimmed);
        if (html != null) out.add(html);
        return out;
    }

    private static List<AdEntry> fromText(String trimmed) {
        List<AdEntry> out = new ArrayList<>();
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

    /**
     * 解析本地中转 / 任意页面：取 marquee 广告文字作为文案，
     * 页面里 window.open() 的第一个地址作为跳转链接。
     */
    static AdEntry fromHtml(String html) {
        if (html == null || html.isEmpty()) return null;

        String link = "";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
            "(?i)window\\.open\\s*\\(\\s*['\"](https?://[^'\"]+)['\"]").matcher(html);
        if (m.find()) link = m.group(1);
        if (link.isEmpty()) {
            m = java.util.regex.Pattern.compile(
                "(?i)<a[^>]+href\\s*=\\s*['\"](https?://[^'\"]+)['\"]").matcher(html);
            if (m.find()) link = m.group(1);
        }

        String text = "";
        m = java.util.regex.Pattern.compile(
            "(?is)class\\s*=\\s*['\"][^'\"]*marquee[^'\"]*['\"]\\s*>([^<]+)").matcher(html);
        if (m.find()) text = Html.fromHtml(m.group(1).trim(), Html.FROM_HTML_MODE_LEGACY).toString().trim();
        text = collapse(text);

        if (text.isEmpty()) {
            String strip = html.replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                .replaceAll("<br\\s*/?>|</p>|</div>|</tr>|</li>", "\n")
                .replaceAll("(?s)<[^>]+>", "\n");
            for (String line : strip.split("\\r?\\n")) {
                String t = collapse(line);
                if (t.length() >= 6) {
                    text = t;
                    break;
                }
            }
        }
        if (text.isEmpty()) return null;
        return new AdEntry(0, 24, text, link);
    }

    /** 去除多余空白并把页面里为对齐滚动的重复片段去重。 */
    private static String collapse(String raw) {
        if (raw == null) return "";
        String s = raw.replaceAll("\\s+", " ").trim();
        if (s.length() > 160) s = s.substring(0, 160);
        return s;
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

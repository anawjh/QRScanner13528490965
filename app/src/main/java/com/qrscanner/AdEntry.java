package com.qrscanner;

import org.json.JSONObject;

import java.util.Locale;

public class AdEntry {

    public int startHour;
    public int endHour;
    public String text;
    public String link;

    public AdEntry(int startHour, int endHour, String text, String link) {
        this.startHour = startHour;
        this.endHour = endHour;
        this.text = text == null ? "" : text;
        this.link = link == null ? "" : link;
    }

    public boolean matches(int hour) {
        int h = ((hour % 24) + 24) % 24;
        int s = ((startHour % 24) + 24) % 24;
        int e = endHour;
        if (e >= 24) {
            return h >= s;
        }
        if (e < 0) {
            e += 24;
        }
        if (s < e) {
            return h >= s && h < e;
        }
        if (s == e) {
            return true;
        }
        return h >= s || h < e;
    }

    public String rangeLabel() {
        return String.format(Locale.getDefault(), "%02d:00-%02d:00", startHour, endHour);
    }

    public JSONObject toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("start", startHour);
            o.put("end", endHour);
            o.put("text", text);
            o.put("link", link);
            return o;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static AdEntry fromJson(JSONObject o) {
        return new AdEntry(
            o.optInt("start", 0),
            o.optInt("end", 24),
            o.optString("text", ""),
            o.optString("link", ""));
    }
}

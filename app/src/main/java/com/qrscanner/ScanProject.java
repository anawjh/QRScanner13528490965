package com.qrscanner;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ScanProject {
    public String name;
    public List<ScanRecord> records;
    public String createdAt;
    public String updatedAt;

    public ScanProject(String name) {
        this.name = name;
        this.records = new ArrayList<>();
        String now = nowStr();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void touch() {
        updatedAt = nowStr();
    }

    private static String nowStr() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date());
    }

    public JSONObject toJson() {
        try {
            JSONObject obj = new JSONObject();
            obj.put("name", name);
            obj.put("createdAt", createdAt);
            obj.put("updatedAt", updatedAt);
            JSONArray arr = new JSONArray();
            for (ScanRecord r : records) {
                JSONObject ro = new JSONObject();
                ro.put("seq", r.getSeq());
                ro.put("content", r.getContent());
                ro.put("time", r.getTime());
                ro.put("remark", r.getRemark());
                arr.put(ro);
            }
            obj.put("records", arr);
            return obj;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static ScanProject fromJson(JSONObject obj) {
        ScanProject p = new ScanProject(obj.optString("name", "未命名"));
        p.createdAt = obj.optString("createdAt", nowStr());
        p.updatedAt = obj.optString("updatedAt", nowStr());
        JSONArray arr = obj.optJSONArray("records");
        if (arr != null) {
            p.records.clear();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject ro = arr.optJSONObject(i);
                if (ro != null) {
                    p.records.add(new ScanRecord(
                            ro.optInt("seq"),
                            ro.optString("content", ""),
                            ro.optString("time", ""),
                            ro.optString("remark", "")
                    ));
                }
            }
        }
        return p;
    }
}

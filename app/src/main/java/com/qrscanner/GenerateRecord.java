package com.qrscanner;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class GenerateRecord {

    private long id;
    private boolean qr;
    private String format;
    private List<String> items = new ArrayList<>();
    private String time;
    private String remark;

    public long getId() { return id; }

    public void setId(long id) { this.id = id; }

    public boolean isQr() { return qr; }

    public void setQr(boolean qr) { this.qr = qr; }

    public String getFormat() { return format; }

    public void setFormat(String format) { this.format = format; }

    public List<String> getItems() { return items; }

    public void setItems(List<String> items) { this.items = items; }

    public String getTime() { return time; }

    public void setTime(String time) { this.time = time; }

    public String getRemark() { return remark; }

    public void setRemark(String remark) { this.remark = remark; }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("qr", qr);
        json.put("format", format);
        json.put("time", time);
        json.put("remark", remark == null ? "" : remark);
        json.put("items", new JSONArray(items));
        return json;
    }

    public static GenerateRecord fromJson(JSONObject json) throws JSONException {
        GenerateRecord record = new GenerateRecord();
        record.id = json.optLong("id", System.currentTimeMillis());
        record.qr = json.optBoolean("qr", true);
        record.format = json.optString("format", "QR_CODE");
        record.time = json.optString("time", "");
        record.remark = json.optString("remark", "");
        JSONArray array = json.optJSONArray("items");
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                String value = array.optString(i, "");
                if (!value.isEmpty()) record.items.add(value);
            }
        }
        return record;
    }
}

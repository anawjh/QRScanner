package com.qrscanner;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 一个扫码项目 = 一个文件 */
public class ScanProject {
    public String name;
    public String createdAt;
    public String updatedAt;
    public List<ScanRecord> records = new ArrayList<>();

    public ScanProject(String name) {
        this.name = name;
        String now = nowStr();
        createdAt = now;
        updatedAt = now;
    }

    // ===== JSON =====
    public JSONObject toJson() {
        JSONObject obj = new JSONObject();
        try {
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
        } catch (Exception ignored) {}
        return obj;
    }

    public static ScanProject fromJson(JSONObject obj) {
        ScanProject p = new ScanProject(obj.optString("name", "未命名"));
        p.createdAt = obj.optString("createdAt", nowStr());
        p.updatedAt = obj.optString("updatedAt", nowStr());
        JSONArray arr = obj.optJSONArray("records");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject ro = arr.optJSONObject(i);
                if (ro != null) {
                    ScanRecord r = new ScanRecord(
                        ro.optInt("seq"),
                        ro.optString("content", ""),
                        ro.optString("time", ""),
                        ro.optString("remark", "")
                    );
                    p.records.add(r);
                }
            }
        }
        return p;
    }

    public void touch() {
        updatedAt = nowStr();
    }

    public static String nowStr() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date());
    }
}

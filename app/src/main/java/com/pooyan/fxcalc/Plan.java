package com.pooyan.fxcalc;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/** Builds the list of news alarms from the cached calendar plus the rules the JS side saved, and schedules it. */
public class Plan {
    static boolean impOk(String imp, String min) {
        if ("High".equals(min)) return "High".equals(imp);
        return "High".equals(imp) || "Medium".equals(imp);
    }

    static void rebuild(Context c) {
        try {
            JSONObject cfg = Store.obj(c, "cfg");
            JSONArray cal = Store.arr(c, "cal");
            JSONArray man = cfg.optJSONArray("man");
            JSONObject rule = cfg.optJSONObject("rule");
            JSONArray one = cfg.optJSONArray("one");
            long now = System.currentTimeMillis();
            SimpleDateFormat tf = new SimpleDateFormat("HH:mm", Locale.US);
            tf.setTimeZone(TimeZone.getTimeZone("Asia/Tehran"));
            Map<Integer, JSONObject> m = new LinkedHashMap<Integer, JSONObject>();

            if (rule != null && rule.optBoolean("on")) {
                int min = rule.optInt("min", 15);
                String imp = rule.optString("imp", "High");
                JSONArray ccy = rule.optJSONArray("ccy");
                for (int pass = 0; pass < 2; pass++) {
                    JSONArray src = pass == 0 ? cal : man;
                    if (src == null) continue;
                    for (int i = 0; i < src.length(); i++) {
                        JSONObject e = src.optJSONObject(i);
                        if (e == null) continue;
                        long ts = e.optLong("ts");
                        if (ts < now || !impOk(e.optString("i"), imp)) continue;
                        boolean ok = false;
                        if (ccy != null) for (int k = 0; k < ccy.length(); k++) if (ccy.optString(k).equals(e.optString("c"))) ok = true;
                        if (ok) add(m, tf, e.optString("t"), e.optString("c"), ts, min);
                    }
                }
            }
            if (one != null) {
                for (int i = 0; i < one.length(); i++) {
                    JSONObject e = one.optJSONObject(i);
                    if (e == null || e.optLong("ts") < now) continue;
                    add(m, tf, e.optString("t"), e.optString("c"), e.optLong("ts"), e.optInt("min", 15));
                }
            }
            JSONArray plan = new JSONArray();
            for (JSONObject o : m.values()) plan.put(o);
            Sched.apply(c, plan);
        } catch (Exception e) { }
    }

    static void add(Map<Integer, JSONObject> m, SimpleDateFormat tf, String title, String ccy, long ts, int min) throws Exception {
        int id = Store.hash(title + "|" + ccy + "|" + ts + ":" + min);
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("fire", ts - min * 60000L);
        o.put("t", ccy + " · " + title);
        o.put("x", min + " دقیقه دیگر · ساعت " + tf.format(new Date(ts)) + " تهران");
        m.put(id, o);
    }
}

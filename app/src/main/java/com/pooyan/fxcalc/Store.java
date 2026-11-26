package com.pooyan.fxcalc;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

public class Store {
    static SharedPreferences sp(Context c) { return c.getApplicationContext().getSharedPreferences("fx", Context.MODE_PRIVATE); }
    static String get(Context c, String k, String d) { return sp(c).getString(k, d); }
    static void put(Context c, String k, String v) { sp(c).edit().putString(k, v).apply(); }

    static JSONArray arr(Context c, String k) {
        try { return new JSONArray(get(c, k, "[]")); } catch (Exception e) { return new JSONArray(); }
    }
    static JSONObject obj(Context c, String k) {
        try { return new JSONObject(get(c, k, "{}")); } catch (Exception e) { return new JSONObject(); }
    }

    static int hash(String s) {
        int h = s.hashCode() & 0x7fffffff;
        return h == 0 ? 1 : h;
    }

    // ---- price alarm helpers (shared by the service and the JS bridge) ----
    static synchronized void savePriceAlarms(Context c, String json) { put(c, "pa", json); }

    static synchronized boolean hasActiveAlarms(Context c) {
        JSONArray a = arr(c, "pa");
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && o.optBoolean("on") && !o.optBoolean("done")) return true;
        }
        return false;
    }

    static synchronized void addFired(Context c, String title, String body) {
        JSONArray f = arr(c, "fired");
        try {
            JSONObject o = new JSONObject();
            o.put("title", title);
            o.put("body", body);
            o.put("ts", System.currentTimeMillis());
            f.put(o);
        } catch (Exception e) { }
        put(c, "fired", f.toString());
    }

    static synchronized String popFired(Context c) {
        String s = get(c, "fired", "[]");
        put(c, "fired", "[]");
        return s;
    }

    /** Apply a change to one alarm by id while keeping the rest of the list as the JS side last wrote it. */
    static synchronized void patchAlarm(Context c, int id, JSONObject patch) {
        JSONArray a = arr(c, "pa");
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && o.optInt("id") == id) {
                java.util.Iterator<String> it = patch.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    try { o.put(k, patch.get(k)); } catch (Exception e) { }
                }
            }
        }
        put(c, "pa", a.toString());
    }
}

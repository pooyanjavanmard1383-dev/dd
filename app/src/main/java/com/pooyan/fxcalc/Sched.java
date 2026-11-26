package com.pooyan.fxcalc;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;

public class Sched {
    static PendingIntent pi(Context c, int id, String t, String x) {
        Intent i = new Intent(c, AlarmReceiver.class);
        i.putExtra("id", id).putExtra("t", t).putExtra("x", x).putExtra("fs", true);
        return PendingIntent.getBroadcast(c, id, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void set(Context c, int id, long fire, String t, String x) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent p = pi(c, id, t, x);
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fire, p);
            else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fire, p);
        } catch (Exception e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fire, p);
        }
    }

    static void cancel(Context c, int id) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pi(c, id, "", ""));
    }

    /** Replace the whole stored plan: cancel old entries, schedule future new ones, remember them. */
    static synchronized void apply(Context c, JSONArray plan) {
        JSONArray old = Store.arr(c, "plan");
        for (int i = 0; i < old.length(); i++) {
            JSONObject o = old.optJSONObject(i);
            if (o != null) cancel(c, o.optInt("id"));
        }
        long now = System.currentTimeMillis();
        JSONArray keep = new JSONArray();
        for (int i = 0; i < plan.length(); i++) {
            JSONObject o = plan.optJSONObject(i);
            if (o == null) continue;
            long fire = o.optLong("fire");
            if (fire > now + 1000) {
                set(c, o.optInt("id"), fire, o.optString("t"), o.optString("x"));
                keep.put(o);
            }
        }
        Store.put(c, "plan", keep.toString());
    }

    /** One extra alarm (snooze). Kept in the stored plan so it survives a reboot. */
    static synchronized void add(Context c, int id, long fire, String t, String x) {
        set(c, id, fire, t, x);
        JSONArray p = Store.arr(c, "plan");
        try {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("fire", fire);
            o.put("t", t);
            o.put("x", x);
            p.put(o);
        } catch (Exception e) { }
        Store.put(c, "plan", p.toString());
    }

    static synchronized void restore(Context c) {
        JSONArray p = Store.arr(c, "plan");
        long now = System.currentTimeMillis();
        JSONArray keep = new JSONArray();
        for (int i = 0; i < p.length(); i++) {
            JSONObject o = p.optJSONObject(i);
            if (o != null && o.optLong("fire") > now + 1000) {
                set(c, o.optInt("id"), o.optLong("fire"), o.optString("t"), o.optString("x"));
                keep.put(o);
            }
        }
        Store.put(c, "plan", keep.toString());
    }
}

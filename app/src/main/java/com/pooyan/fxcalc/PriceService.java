package com.pooyan.fxcalc;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;

/** Foreground service: watches prices while price alarms exist, even when the app is closed. */
public class PriceService extends Service {
    static final String CH = "fx_monitor";
    static final int NID = 7001;
    private volatile boolean running = false;
    private Thread worker;

    static void sync(Context c) {
        if (Store.hasActiveAlarms(c)) {
            Intent i = new Intent(c, PriceService.class);
            try {
                if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
            } catch (Exception e) { }
        } else {
            c.stopService(new Intent(c, PriceService.class));
        }
    }

    @Override
    public IBinder onBind(Intent i) { return null; }

    private Notification note(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CH) == null) {
            nm.createNotificationChannel(new NotificationChannel(CH, "پایش قیمت", NotificationManager.IMPORTANCE_LOW));
        }
        Intent i = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CH) : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.ic_menu_view).setContentTitle("پایش قیمت فعال است").setContentText(text)
                .setOngoing(true).setContentIntent(pi);
        return b.build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification n = note("الارم‌های قیمت در حال بررسی‌اند");
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(NID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            else startForeground(NID, n);
        } catch (Exception e) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!running) {
            running = true;
            worker = new Thread(new Runnable() { public void run() { loop(); } });
            worker.start();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        if (worker != null) worker.interrupt();
        super.onDestroy();
    }

    private void loop() {
        Context c = getApplicationContext();
        while (running) {
            try {
                JSONArray list = Store.arr(c, "pa");
                Map<String, Double> q = new HashMap<String, Double>();
                for (int i = 0; i < list.length(); i++) {
                    JSONObject a = list.optJSONObject(i);
                    if (a == null || !a.optBoolean("on") || a.optBoolean("done")) continue;
                    String inst = a.optString("inst");
                    if (!q.containsKey(inst)) q.put(inst, Net.quote(inst));
                }
                if (q.isEmpty()) { stopSelf(); return; }
                long now = System.currentTimeMillis();
                StringBuilder sb = new StringBuilder();
                JSONObject qs = new JSONObject();
                for (Map.Entry<String, Double> e : q.entrySet()) {
                    if (!Double.isNaN(e.getValue())) {
                        qs.put(e.getKey(), e.getValue());
                        sb.append(e.getKey()).append(' ').append(fmt(e.getValue())).append("  ");
                    }
                }
                Store.put(c, "q", qs.toString());
                for (int i = 0; i < list.length(); i++) {
                    JSONObject a = list.optJSONObject(i);
                    if (a == null || !a.optBoolean("on") || a.optBoolean("done")) continue;
                    Double p = q.get(a.optString("inst"));
                    if (p == null || Double.isNaN(p)) continue;
                    check(c, a, p, now);
                }
                if (sb.length() > 0) {
                    NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    nm.notify(NID, note(sb.toString().trim()));
                }
            } catch (Exception e) { }
            try { Thread.sleep(20000); } catch (InterruptedException e) { return; }
        }
    }

    static String fmt(double v) { return v >= 100 ? String.format(java.util.Locale.US, "%.2f", v) : String.format(java.util.Locale.US, "%.5f", v); }

    private void check(Context c, JSONObject a, double p, long now) throws Exception {
        String type = a.optString("type", "above");
        double price = a.optDouble("price", 0);
        double pip = a.optDouble("pip", 1);
        boolean cond;
        double ref = a.optDouble("ref", 0);
        if (type.equals("above")) cond = p >= price;
        else if (type.equals("below")) cond = p <= price;
        else if (type.equals("near")) cond = Math.abs(p - price) <= a.optDouble("near", 10) * pip;
        else cond = ref > 0 && Math.abs(p - ref) / ref * 100.0 >= a.optDouble("pct", 0.5);

        boolean repeat = a.optBoolean("repeat");
        boolean armed = a.optBoolean("armed", true);
        int id = a.optInt("id");
        JSONObject patch = new JSONObject();
        if (!armed) {
            boolean away = type.equals("near") ? Math.abs(p - price) > a.optDouble("near", 10) * pip * 2 : !cond;
            if (away && repeat) { patch.put("armed", true); Store.patchAlarm(c, id, patch); }
            return;
        }
        if (!cond || now < a.optLong("cool", 0)) return;

        String name = a.optString("n", a.optString("inst"));
        String label = a.optString("label", "");
        String what;
        if (type.equals("above")) what = "بالاتر از " + price;
        else if (type.equals("below")) what = "پایین‌تر از " + price;
        else if (type.equals("near")) what = "نزدیک سطح " + price;
        else what = "حرکت " + a.optDouble("pct") + "٪ از " + fmt(ref);
        String body = (label.isEmpty() ? "" : label + " · ") + what + " · قیمت الان " + fmt(p);
        AlarmReceiver.post(c, Store.hash("pa" + id + now), "الارم قیمت · " + name, body, true);
        Store.addFired(c, "الارم قیمت · " + name, body);

        if (repeat) {
            patch.put("armed", false);
            patch.put("cool", now + 300000L);
            if (type.equals("pct")) { patch.put("ref", p); patch.put("armed", true); }
        } else {
            patch.put("done", true);
            patch.put("on", false);
        }
        Store.patchAlarm(c, id, patch);
        if (!Store.hasActiveAlarms(c)) { stopSelf(); running = false; }
    }
}

package com.pooyan.fxcalc;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import org.json.JSONArray;

/** Every few hours: refresh the news calendar in the background and re-plan the news alarms. */
public class CalReceiver extends BroadcastReceiver {
    static final long EVERY = 3L * 3600 * 1000;

    static void schedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent p = PendingIntent.getBroadcast(c, 4242, new Intent(c, CalReceiver.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + EVERY, p);
    }

    @Override
    public void onReceive(final Context c, Intent in) {
        final PendingResult pr = goAsync();
        new Thread(new Runnable() {
            public void run() {
                try {
                    JSONArray all = new JSONArray();
                    boolean ok = false;
                    String[] urls = {"https://nfs.faireconomy.media/ff_calendar_thisweek.json", "https://nfs.faireconomy.media/ff_calendar_nextweek.json"};
                    for (String u : urls) {
                        String t = Net.get(u);
                        JSONArray a = t == null ? null : Net.parseCalendar(t);
                        if (a != null) {
                            ok = true;
                            for (int i = 0; i < a.length(); i++) all.put(a.get(i));
                        }
                    }
                    if (ok) {
                        Store.put(c, "cal", all.toString());
                        Plan.rebuild(c);
                    }
                } catch (Exception e) { }
                schedule(c);
                pr.finish();
            }
        }).start();
    }
}

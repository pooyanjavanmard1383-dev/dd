package com.pooyan.fxcalc;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ActionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent in) {
        int id = in.getIntExtra("id", 1);
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(id);
        if ("snooze".equals(in.getStringExtra("do"))) {
            Sched.add(c, id, System.currentTimeMillis() + 5 * 60000L, in.getStringExtra("t"), "یادآوری مجدد");
        }
    }
}

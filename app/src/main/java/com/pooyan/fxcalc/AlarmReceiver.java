package com.pooyan.fxcalc;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

public class AlarmReceiver extends BroadcastReceiver {
    public static final String CH = "fx_alarm";

    public static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm.getNotificationChannel(CH) != null) return;
        NotificationChannel ch = new NotificationChannel(CH, "الارم", NotificationManager.IMPORTANCE_HIGH);
        Uri snd = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        ch.setSound(snd, new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build());
        ch.enableVibration(true);
        ch.setVibrationPattern(new long[]{0, 500, 200, 500, 200, 800});
        nm.createNotificationChannel(ch);
    }

    @SuppressWarnings("deprecation")
    public static void post(Context c, int id, String title, String text, boolean fullScreen) {
        ensureChannel(c);
        Intent i = new Intent(c, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, id, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CH) : new Notification.Builder(c);
        b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle(title).setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setCategory(Notification.CATEGORY_ALARM)
                .setContentIntent(pi).setAutoCancel(true);
        if (fullScreen) b.setFullScreenIntent(pi, true);

        Intent sn = new Intent(c, ActionReceiver.class).putExtra("id", id).putExtra("do", "snooze").putExtra("t", title);
        Intent ds = new Intent(c, ActionReceiver.class).putExtra("id", id).putExtra("do", "dismiss");
        sn.setAction("fx.snooze." + id);
        ds.setAction("fx.dismiss." + id);
        int fl = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        b.addAction(android.R.drawable.ic_menu_recent_history, "۵ دقیقه بعد", PendingIntent.getBroadcast(c, id + 1, sn, fl));
        b.addAction(android.R.drawable.ic_menu_close_clear_cancel, "بستن", PendingIntent.getBroadcast(c, id + 2, ds, fl));
        if (Build.VERSION.SDK_INT < 26) {
            b.setPriority(Notification.PRIORITY_MAX).setDefaults(Notification.DEFAULT_ALL);
        }
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(id, b.build());
    }

    @Override
    public void onReceive(Context c, Intent in) {
        post(c, in.getIntExtra("id", 1), in.getStringExtra("t"), in.getStringExtra("x"), in.getBooleanExtra("fs", false));
    }
}

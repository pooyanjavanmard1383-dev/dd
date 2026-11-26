package com.pooyan.fxcalc;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private WebView web;
    private ValueCallback<Uri[]> chooser;

    public class Bridge {
        @JavascriptInterface
        public void fetchText(final String cb, final String url) {
            new Thread(new Runnable() {
                public void run() {
                    String out = Net.get(url);
                    final String js = "window.__nf(" + JSONObject.quote(cb) + "," + (out != null) + "," + (out != null ? JSONObject.quote(out) : "null") + ")";
                    runOnUiThread(new Runnable() { public void run() { web.evaluateJavascript(js, null); } });
                }
            }).start();
        }

        /** Rules, per-event alarms and manual events from the page. Native re-plans and schedules. */
        @JavascriptInterface
        public void setConfig(final String json) {
            new Thread(new Runnable() {
                public void run() {
                    Store.put(MainActivity.this, "cfg", json);
                    Plan.rebuild(MainActivity.this);
                }
            }).start();
        }

        /** Latest calendar fetched by the page: [{t,c,i,ts}] */
        @JavascriptInterface
        public void setCalendar(final String json) {
            new Thread(new Runnable() {
                public void run() {
                    Store.put(MainActivity.this, "cal", json);
                    Plan.rebuild(MainActivity.this);
                }
            }).start();
        }

        @JavascriptInterface
        public void setPriceAlarms(String json) {
            Store.savePriceAlarms(MainActivity.this, json);
            PriceService.sync(MainActivity.this);
        }

        @JavascriptInterface
        public String getPriceAlarms() { return Store.get(MainActivity.this, "pa", "[]"); }

        @JavascriptInterface
        public String popFired() { return Store.popFired(MainActivity.this); }

        @JavascriptInterface
        public void schedule(double id, double millis, String title, String text) {
            Sched.add(MainActivity.this, (int) id, (long) millis, title, text);
        }

        @JavascriptInterface
        public void cancel(double id) { Sched.cancel(MainActivity.this, (int) id); }

        @JavascriptInterface
        public void notifyNow(String title, String text) {
            AlarmReceiver.post(MainActivity.this, (int) (System.currentTimeMillis() % 100000), title, text, false);
        }

        @JavascriptInterface
        public void askNotify() {
            runOnUiThread(new Runnable() { public void run() { askPerm(); } });
        }

        @JavascriptInterface
        public void vibrate(double ms) {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null) return;
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot((long) ms, VibrationEffect.DEFAULT_AMPLITUDE));
            else v.vibrate((long) ms);
        }

        @JavascriptInterface
        public boolean isIgnoringBattery() {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        }

        @JavascriptInterface
        public void openBattery() {
            runOnUiThread(new Runnable() {
                public void run() {
                    try {
                        Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName()));
                        startActivity(i);
                    } catch (Exception e) {
                        try { startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); } catch (Exception e2) { }
                    }
                }
            });
        }

        @JavascriptInterface
        public boolean canExact() {
            if (Build.VERSION.SDK_INT < 31) return true;
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            return am.canScheduleExactAlarms();
        }

        @JavascriptInterface
        public void openExact() {
            runOnUiThread(new Runnable() {
                public void run() {
                    try {
                        if (Build.VERSION.SDK_INT >= 31) startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName())));
                    } catch (Exception e) { }
                }
            });
        }

        @JavascriptInterface
        public void share(final String title, final String text) {
            runOnUiThread(new Runnable() {
                public void run() {
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType("text/plain");
                    i.putExtra(Intent.EXTRA_SUBJECT, title);
                    i.putExtra(Intent.EXTRA_TEXT, text);
                    startActivity(Intent.createChooser(i, title));
                }
            });
        }
    }

    private void askPerm() {
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        getWindow().setStatusBarColor(Color.parseColor("#07080C"));
        getWindow().setNavigationBarColor(Color.parseColor("#07080C"));
        AlarmReceiver.ensureChannel(this);
        askPerm();
        try { CalReceiver.schedule(this); } catch (Exception e) { }
        try { PriceService.sync(this); } catch (Exception e) { }
        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#07080C"));
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (chooser != null) chooser.onReceiveValue(null);
                chooser = cb;
                try {
                    startActivityForResult(p.createIntent(), 31);
                } catch (Exception e) {
                    chooser = null;
                    return false;
                }
                return true;
            }
        });
        web.addJavascriptInterface(new Bridge(), "Native");
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == 31 && chooser != null) {
            chooser.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data));
            chooser = null;
            return;
        }
        super.onActivityResult(req, res, data);
    }

    @Override
    public void onBackPressed() {
        finish();
    }
}

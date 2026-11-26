package com.pooyan.fxcalc;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Locale;

public class Net {
    static String get(String url) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(10000);
            c.setReadTimeout(15000);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) FxDesk");
            String out = null;
            if (c.getResponseCode() == 200) {
                InputStream in = c.getInputStream();
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                out = bo.toString("UTF-8");
            }
            c.disconnect();
            return out;
        } catch (Exception e) { return null; }
    }

    static String stooq(String inst) {
        switch (inst) {
            case "EURUSD": return "eurusd";
            case "GBPUSD": return "gbpusd";
            case "XAUUSD": return "xauusd";
            case "US30": return "^dji";
            case "NAS100": return "^ndx";
        }
        return null;
    }

    static String yahoo(String inst) {
        switch (inst) {
            case "EURUSD": return "EURUSD=X";
            case "GBPUSD": return "GBPUSD=X";
            case "XAUUSD": return "XAUUSD=X";
            case "US30": return "^DJI";
            case "NAS100": return "^NDX";
        }
        return null;
    }

    /** Last price for one of our five instruments, or NaN. Tries Stooq, then Yahoo. */
    static double quote(String inst) {
        try {
            String s = stooq(inst);
            if (s != null) {
                String t = get("https://stooq.com/q/l/?s=" + java.net.URLEncoder.encode(s, "UTF-8") + "&f=sd2t2c&h&e=csv");
                if (t != null) {
                    String[] lines = t.trim().split("\\r?\\n");
                    if (lines.length > 1) {
                        String[] p = lines[1].split(",");
                        double v = Double.parseDouble(p[p.length - 1]);
                        if (v > 0) return v;
                    }
                }
            }
        } catch (Exception e) { }
        try {
            String y = yahoo(inst);
            if (y != null) {
                String t = get("https://query1.finance.yahoo.com/v8/finance/chart/" + java.net.URLEncoder.encode(y, "UTF-8") + "?interval=1m&range=1d");
                if (t != null) {
                    JSONObject m = new JSONObject(t).getJSONObject("chart").getJSONArray("result").getJSONObject(0).getJSONObject("meta");
                    double v = m.getDouble("regularMarketPrice");
                    if (v > 0) return v;
                }
            }
        } catch (Exception e) { }
        return Double.NaN;
    }

    /** Parse Forex Factory style calendar JSON into [{t,c,i,ts}] or null. */
    static JSONArray parseCalendar(String txt) {
        try {
            JSONArray in = new JSONArray(txt);
            JSONArray out = new JSONArray();
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US);
            for (int i = 0; i < in.length(); i++) {
                JSONObject e = in.getJSONObject(i);
                String title = e.optString("title", "");
                String date = e.optString("date", "");
                if (title.isEmpty() || date.isEmpty()) continue;
                String imp = e.optString("impact", "Low");
                if (imp.equals("Holiday")) imp = "Low";
                long ts = f.parse(date).getTime();
                JSONObject o = new JSONObject();
                o.put("t", title);
                o.put("c", e.optString("country", ""));
                o.put("i", imp);
                o.put("ts", ts);
                out.put(o);
            }
            return out;
        } catch (Exception e) { return null; }
    }
}

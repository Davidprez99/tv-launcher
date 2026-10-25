package com.terry.home;

import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * Current temperature and conditions from Open-Meteo (free, no account or key). Fetched only while the home screen
 * is showing and at most every 30 minutes. Location is a latitude/longitude saved once over adb; it is never shown.
 *   adb shell am start -n com.terry.home/.HomeActivity --es lat 40.71 --es lon -74.01
 */
final class Weather {
    static final long MAX_AGE_MS = 30 * 60 * 1000;

    interface Listener { void onWeather(int tempF, int code, boolean day); }

    private Weather() { }

    /** Cached reading, or null if never fetched. */
    static int[] cached(SharedPreferences p) {
        if (!p.contains("w_temp")) return null;
        return new int[]{p.getInt("w_temp", 0), p.getInt("w_code", 0), p.getBoolean("w_day", true) ? 1 : 0};
    }

    static boolean due(SharedPreferences p) {
        return p.contains("lat") && System.currentTimeMillis() - p.getLong("w_at", 0) > MAX_AGE_MS;
    }

    /** Blocking: call off the main thread. */
    static boolean fetch(SharedPreferences p) {
        String lat = p.getString("lat", null), lon = p.getString("lon", null);
        if (lat == null || lon == null) return false;
        HttpURLConnection c = null;
        try {
            String url = String.format(Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%s&longitude=%s"
                            + "&current=temperature_2m,weather_code,is_day&temperature_unit=fahrenheit",
                    lat, lon);
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            if (c.getResponseCode() != 200) return false;
            JSONObject cur = new JSONObject(read(c.getInputStream())).getJSONObject("current");
            p.edit()
                    .putInt("w_temp", (int) Math.round(cur.getDouble("temperature_2m")))
                    .putInt("w_code", cur.getInt("weather_code"))
                    .putBoolean("w_day", cur.optInt("is_day", 1) == 1)
                    .putLong("w_at", System.currentTimeMillis())
                    .apply();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static String read(InputStream in) throws java.io.IOException {
        try (InputStream s = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            for (int n; (n = s.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toString("UTF-8");
        }
    }
}

package com.gathin.tvhome;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import android.util.Log;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

final class NovosibirskWeather {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final String CITY = "Новосибирск";
    private static final double LAT = 55.0302, LON = 82.9204;
    private static Runnable pendingRefresh;
    private NovosibirskWeather() {}

    static void start(Activity activity, TextView label) {
        if (label == null) return;
        if (pendingRefresh != null) MAIN.removeCallbacks(pendingRefresh);
        SharedPreferences prefs = activity.getSharedPreferences("novosibirsk_weather", Activity.MODE_PRIVATE);
        String cached = prefs.getString("last_weather", "");
        label.setText(cached.isEmpty() ? CITY + " · Загрузка погоды…" : cached + " · ранее");
        Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            new Thread(() -> {
                String result;
                try {
                    JSONObject j = get("https://api.open-meteo.com/v1/forecast?latitude=55.0302&longitude=82.9204&current=temperature_2m,weather_code,pressure_msl,wind_speed_10m&wind_speed_unit=ms&timezone=Asia%2FNovosibirsk");
                    JSONObject c = j.getJSONObject("current");
                    result = format(c.getDouble("temperature_2m"), c.getInt("weather_code"))
                            + details(c.optDouble("pressure_msl", Double.NaN), c.optDouble("wind_speed_10m", Double.NaN));
                } catch (Exception first) {
                    Log.w("NovosibirskWeather", "Open-Meteo request failed", first);
                    try {
                        JSONObject j = get("https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=55.0302&lon=82.9204");
                        JSONObject data = j.getJSONObject("properties").getJSONArray("timeseries").getJSONObject(0).getJSONObject("data");
                        double temp = data.getJSONObject("instant").getJSONObject("details").getDouble("air_temperature");
                        JSONObject next = data.optJSONObject("next_1_hours");
                        String symbol = next == null ? "" : next.getJSONObject("summary").optString("symbol_code");
                        JSONObject instant = data.getJSONObject("instant").getJSONObject("details");
                        double pressure = instant.optDouble("air_pressure_at_sea_level", Double.NaN);
                        double windMs = instant.optDouble("wind_speed", Double.NaN);
                        result = CITY + " · " + signed(temp) + "°C · " + metDescription(symbol)
                                + details(pressure, windMs);
                    } catch (Exception second) {
                        Log.w("NovosibirskWeather", "MET Norway fallback failed", second);
                        String previous = prefs.getString("last_weather", "");
                        result = previous.isEmpty() ? CITY + " · Погода недоступна" : previous + " · ранее";
                    }
                }
                if (!result.endsWith(" · ранее") && !result.endsWith("Погода недоступна")) {
                    prefs.edit().putString("last_weather", result).apply();
                }
                String display = result;
                MAIN.post(() -> {
                    if (!activity.isFinishing() && !activity.isDestroyed()) label.setText(display);
                    if (pendingRefresh == refresh[0] && !activity.isFinishing() && !activity.isDestroyed()) {
                        MAIN.postDelayed(refresh[0], 30 * 60 * 1000L);
                    }
                });
            }, "NovosibirskWeather").start();
        };
        pendingRefresh = refresh[0];
        refresh[0].run();
    }

    private static JSONObject get(String address) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestProperty("User-Agent", "CleanYourTV-Weather/1.2 (https://github.com/Serega7920/cleanyourtv)");
        try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] b = new byte[4096]; int n;
            while ((n = in.read(b)) != -1) out.write(b, 0, n);
            return new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
        } finally { c.disconnect(); }
    }

    private static String details(double pressureHpa, double windMs) {
        StringBuilder text = new StringBuilder();
        if (!Double.isNaN(pressureHpa)) text.append(" · Давление ").append(Math.round(pressureHpa * 0.750061683)).append(" мм рт. ст.");
        if (!Double.isNaN(windMs)) text.append(" · Ветер ").append(String.format(java.util.Locale.US, "%.1f", windMs)).append(" м/с");
        return text.toString();
    }

    private static String signed(double t) { long n = Math.round(t); return n > 0 ? "+" + n : Long.toString(n); }
    private static String format(double temp, int code) {
        String desc = code == 0 ? "Ясно" : code <= 3 ? "Облачно" : code <= 48 ? "Туман" : code <= 67 ? "Дождь" : code <= 77 ? "Снег" : code <= 82 ? "Ливень" : code <= 86 ? "Снег" : "Гроза";
        return CITY + " · " + signed(temp) + "°C · " + desc;
    }
    private static String metDescription(String s) {
        if (s.contains("thunder")) return "Гроза";
        if (s.contains("snow")) return "Снег";
        if (s.contains("rain") || s.contains("sleet")) return "Дождь";
        if (s.contains("fog")) return "Туман";
        if (s.contains("clear") || s.contains("fair")) return "Ясно";
        return "Облачно";
    }
}

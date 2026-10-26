package com.gathin.tvhome;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
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
    private NovosibirskWeather() {}

    static void start(Activity activity, TextView label) {
        if (label == null) return;
        label.setText(CITY + " · Загрузка погоды…");
        Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            new Thread(() -> {
                String result;
                try {
                    JSONObject j = get("https://api.open-meteo.com/v1/forecast?latitude=55.0302&longitude=82.9204&current=temperature_2m,weather_code&timezone=Asia%2FNovosibirsk");
                    JSONObject c = j.getJSONObject("current");
                    result = format(c.getDouble("temperature_2m"), c.getInt("weather_code"));
                } catch (Exception first) {
                    try {
                        JSONObject j = get("https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=55.0302&lon=82.9204");
                        JSONObject data = j.getJSONObject("properties").getJSONArray("timeseries").getJSONObject(0).getJSONObject("data");
                        double temp = data.getJSONObject("instant").getJSONObject("details").getDouble("air_temperature");
                        JSONObject next = data.optJSONObject("next_1_hours");
                        String symbol = next == null ? "" : next.getJSONObject("summary").optString("symbol_code");
                        result = CITY + " · " + signed(temp) + "°C · " + metDescription(symbol);
                    } catch (Exception second) {
                        result = CITY + " · Погода недоступна";
                    }
                }
                String display = result;
                MAIN.post(() -> {
                    if (!activity.isFinishing() && !activity.isDestroyed()) label.setText(display);
                    MAIN.postDelayed(refresh[0], 30 * 60 * 1000L);
                });
            }, "NovosibirskWeather").start();
        };
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

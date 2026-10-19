package com.cylinderradar.launcher;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Lightweight, keyless Open-Meteo lookup. No precise location permission is requested. */
final class WeatherService {
    interface Callback { void onResult(String city, String temperature, int code); void onError(); }
    private WeatherService() { }

    static void fetch(final String requestedCity, final Callback callback) {
        new Thread(new Runnable() {
            @Override public void run() {
                HttpURLConnection connection = null;
                try {
                    String city = requestedCity == null || requestedCity.trim().isEmpty() ? "Dhaka" : requestedCity.trim();
                    String geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name="
                            + URLEncoder.encode(city, "UTF-8") + "&count=1&language=en&format=json";
                    JSONObject geo = new JSONObject(readUrl(geoUrl));
                    JSONArray results = geo.optJSONArray("results");
                    if (results == null || results.length() == 0) throw new IllegalStateException("City not found");
                    JSONObject place = results.getJSONObject(0);
                    double lat = place.getDouble("latitude"), lon = place.getDouble("longitude");
                    String resolved = place.optString("name", city);
                    String forecastUrl = "https://api.open-meteo.com/v1/forecast?latitude=" + lat
                            + "&longitude=" + lon
                            + "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code&timezone=auto";
                    JSONObject forecast = new JSONObject(readUrl(forecastUrl));
                    JSONObject current = forecast.getJSONObject("current");
                    String temp = String.valueOf(Math.round(current.getDouble("temperature_2m")));
                    int code = current.optInt("weather_code", -1);
                    final String finalCity = resolved;
                    final String finalTemp = temp;
                    final int finalCode = code;
                    callback.onResult(finalCity, finalTemp, finalCode);
                } catch (Exception ex) {
                    callback.onError();
                } finally {
                    if (connection != null) connection.disconnect();
                }
            }
        }, "cylinder-weather").start();
    }

    private static String readUrl(String address) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        c.setConnectTimeout(7000);
        c.setReadTimeout(7000);
        c.setRequestMethod("GET");
        c.setRequestProperty("User-Agent", "CylinderRadarLauncher/1.3");
        try {
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);
            InputStream in = new BufferedInputStream(c.getInputStream());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096]; int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            in.close();
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally { c.disconnect(); }
    }
}

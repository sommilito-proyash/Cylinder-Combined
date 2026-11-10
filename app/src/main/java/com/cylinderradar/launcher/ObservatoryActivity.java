package com.cylinderradar.launcher;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.os.StatFs;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

/**
 * Smart Observatory: a command-centre "wall screen" (see {@link ObsHud}). Everything on it is live: clock, day/night
 * terminator, weather, world time, calendar, device vitals and the phone's own motion sensors. Touch a tile to open
 * its window; touch the Earth tile to open the real 3D Earth explorer.
 */
public final class ObservatoryActivity extends Activity implements SensorEventListener {
    private HudView view;
    private ObsHud hud;
    private Store store;
    private SensorManager sensors;
    private boolean running = false;
    private long lastVitals = 0L, lastWeather = 0L;
    private boolean weatherFetching = false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        store = new Store(this);
        hud = new ObsHud(getResources().getDisplayMetrics().density);
        hud.eco = store.observatoryBatterySaver();
        hud.city = store.weatherCity();
        hud.model = Build.MODEL == null ? "" : Build.MODEL;
        hud.os = "Android " + Build.VERSION.RELEASE;
        view = new HudView(this);
        setContentView(view);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideBars();
    }

    private void hideBars() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override protected void onResume() {
        super.onResume();
        running = true;
        hideBars();
        sensors = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensors != null) {
            Sensor a = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), g = sensors.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            if (a != null) sensors.registerListener(this, a, SensorManager.SENSOR_DELAY_GAME);
            if (g != null) sensors.registerListener(this, g, SensorManager.SENSOR_DELAY_GAME);
        }
        hud.eco = store.observatoryBatterySaver();
        lastVitals = 0L;
        view.invalidate();
    }

    @Override protected void onPause() {
        running = false;
        if (sensors != null) sensors.unregisterListener(this);
        super.onPause();
    }

    @Override public void onSensorChanged(SensorEvent e) {
        if (e.sensor.getType() == Sensor.TYPE_ACCELEROMETER) hud.pushMotion(true, e.values[0], e.values[1], e.values[2]);
        else if (e.sensor.getType() == Sensor.TYPE_GYROSCOPE) hud.pushMotion(false, e.values[0], e.values[1], e.values[2]);
    }
    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    @Override public void onBackPressed() {
        if (!hud.back(System.currentTimeMillis())) super.onBackPressed();
    }

    // ---------------------------------------------------------------- live vitals (once a second) and weather (every 10 minutes)
    private void refreshVitals(long now) {
        if (now - lastVitals >= 1000L) {
            lastVitals = now;
            try {
                Intent b = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                if (b != null) {
                    int lvl = b.getIntExtra("level", -1), sc = b.getIntExtra("scale", 100), status = b.getIntExtra("status", -1);
                    hud.batPct = sc > 0 && lvl >= 0 ? lvl * 100 / sc : -1;
                    hud.charging = status == 2 || status == 5;
                }
            } catch (Throwable ignored) { }
            try {
                ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                if (am != null) { am.getMemoryInfo(mi); hud.memTotal = mi.totalMem; hud.memUsed = mi.totalMem - mi.availMem; }
            } catch (Throwable ignored) { }
            try {
                StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
                hud.storTotal = fs.getTotalBytes(); hud.storFree = fs.getAvailableBytes();
            } catch (Throwable ignored) { }
            hud.uptimeMs = SystemClock.elapsedRealtime();
        }
        if (now - lastWeather >= 5000L) {
            lastWeather = now;
            hud.city = store.weatherCity();
            android.content.SharedPreferences sp = getSharedPreferences("cube_watch", 0);
            String fc = sp.getString("fc", "");
            long at = sp.getLong("fc_at", 0L);
            hud.packedForecast = fc;
            if (!weatherFetching && (fc.isEmpty() || System.currentTimeMillis() - at > 10 * 60_000L)) {
                weatherFetching = true;
                WeatherService.fetchDaily(hud.city, new WeatherService.DailyCallback() {
                    @Override public void onResult(String packed) {
                        getSharedPreferences("cube_watch", 0).edit().putString("fc", packed).putLong("fc_at", System.currentTimeMillis()).apply();
                        weatherFetching = false;
                    }
                    @Override public void onError() { weatherFetching = false; }
                });
            }
        }
    }

    private final class HudView extends View {
        private float downX, downY;
        private boolean moved;
        HudView(Context c) { super(c); }

        @Override protected void onDraw(Canvas canvas) {
            long now = System.currentTimeMillis();
            refreshVitals(now);
            hud.sampleMotion();
            hud.draw(canvas, getWidth(), getHeight(), now);
            if (running) postInvalidateDelayed(hud.eco ? 66 : 33);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: downX = e.getX(); downY = e.getY(); moved = false; return true;
                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(e.getX() - downX) > 24 || Math.abs(e.getY() - downY) > 24) moved = true;
                    return true;
                case MotionEvent.ACTION_UP: {
                    if (moved) return true;
                    int act = hud.tap(e.getX(), e.getY(), System.currentTimeMillis());
                    if (act == ObsHud.ACT_EARTH) startActivity(new Intent(ObservatoryActivity.this, EarthActivity.class));
                    else if (act == ObsHud.ACT_CLOSE) finish();
                    else if (act == ObsHud.ACT_ECO) { hud.eco = !hud.eco; store.setObservatoryBatterySaver(hud.eco); }
                    invalidate();
                    return true;
                }
                default: return true;
            }
        }
    }
}

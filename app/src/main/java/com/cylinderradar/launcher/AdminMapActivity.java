package com.cylinderradar.launcher;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

/**
 * Division (ADM1) / district (ADM2) map of one country from geoBoundaries gbOpen, drawn from real polygons.
 * Data order: bundled asset -> on-device cache -> download (then cached for offline use). Pinch to zoom, drag to pan, tap an area for details.
 */
public final class AdminMapActivity extends Activity {
    private MapView map;
    private TextView info, status, lv1, lv2;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private volatile boolean destroyed = false;
    private String iso3, bn, en, level = "";
    private final List<GeoShape>[] data = newSlots();
    private final String[] state = {"", ""};          // "", "load", "ok", "fail"
    private final String[] error = {"", ""};
    private boolean userChoseLevel = false;

    @SuppressWarnings("unchecked") private static List<GeoShape>[] newSlots() { return (List<GeoShape>[]) new List[2]; }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        iso3 = getIntent().getStringExtra("iso3"); bn = getIntent().getStringExtra("bn"); en = getIntent().getStringExtra("en");
        if (iso3 == null) { finish(); return; }
        if (bn == null) bn = iso3; if (en == null) en = iso3;
        FrameLayout root = new FrameLayout(this);
        map = new MapView(this);
        root.addView(map, new FrameLayout.LayoutParams(-1, -1));

        TextView back = chip("‹  EARTH");
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-2, dp(42), Gravity.TOP | Gravity.LEFT);
        bp.setMargins(dp(14), dp(24), 0, 0); root.addView(back, bp); back.setOnClickListener(v -> finish());
        TextView reset = chip("RESET VIEW");
        FrameLayout.LayoutParams rp = new FrameLayout.LayoutParams(-2, dp(42), Gravity.TOP | Gravity.RIGHT);
        rp.setMargins(0, dp(24), dp(14), 0); root.addView(reset, rp); reset.setOnClickListener(v -> map.fit());
        TextView title = new TextView(this);
        title.setText(bn.equals(en) ? en : bn + " • " + en); title.setTextColor(0xFFEAF8FF); title.setTextSize(15); title.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-2, dp(42), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        tp.setMargins(0, dp(24), 0, 0); root.addView(title, tp);

        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        FrameLayout.LayoutParams rl = new FrameLayout.LayoutParams(-1, dp(42), Gravity.TOP); rl.setMargins(dp(14), dp(76), dp(14), 0); root.addView(row, rl);
        lv1 = chip("বিভাগ (ADM1)"); lv2 = chip("জেলা (ADM2)");
        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, -1, 1); a.rightMargin = dp(6);
        row.addView(lv1, a); row.addView(lv2, new LinearLayout.LayoutParams(0, -1, 1));
        lv1.setOnClickListener(v -> { userChoseLevel = true; showLevel(0); });
        lv2.setOnClickListener(v -> { userChoseLevel = true; showLevel(1); });

        status = new TextView(this); status.setTextColor(0xFFCFE8F5); status.setTextSize(11); status.setPadding(dp(10), dp(6), dp(10), dp(6)); status.setBackgroundColor(0x99081624);
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL); sp.setMargins(dp(14), dp(124), dp(14), 0); root.addView(status, sp);
        status.setOnClickListener(v -> retryFailed());

        info = new TextView(this);
        info.setTextColor(0xFFEAF8FF); info.setTextSize(13); info.setPadding(dp(18), dp(14), dp(18), dp(14)); info.setBackgroundColor(0xCC0B1B2A);
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM); ip.setMargins(dp(14), 0, dp(14), dp(24)); root.addView(info, ip);
        info.setText("এলাকায় ট্যাপ করে নাম দেখুন; দুই আঙুলে জুম, এক আঙুলে সরান।\n\nসূত্র: geoBoundaries (CC BY 4.0) — William & Mary geoLab");
        setContentView(root);
        map.onPick = this::onPick;
        map.bdMode = "BGD".equals(iso3);

        load(0, true);
    }

    // ------------------------------------------------------------------ loading
    private void load(final int idx, final boolean prefetchOther) {
        if (state[idx].equals("load") || state[idx].equals("ok")) return;
        state[idx] = "load"; updateChips();
        final String lvl = idx == 0 ? "ADM1" : "ADM2";
        setStatus(lvl + " সীমানা লোড হচ্ছে…");
        new Thread(() -> {
            try {
                final List<GeoShape> r = GeoRepo.loadAdmin(AdminMapActivity.this, iso3, lvl, m -> ui.post(() -> { if (level.isEmpty() || (idx == 0 ? level.equals("ADM1") : level.equals("ADM2"))) setStatus(m); }));
                ui.post(() -> {
                    if (destroyed) return;
                    data[idx] = r; state[idx] = "ok"; updateChips();
                    if (level.isEmpty()) showLevel(idx);
                    if (prefetchOther) load(1, false);
                });
            } catch (Throwable t) {
                final String msg = t instanceof OutOfMemoryError ? "মেমরি কম (ফাইল বড়)" : String.valueOf(t.getMessage());
                ui.post(() -> {
                    if (destroyed) return;
                    state[idx] = "fail"; error[idx] = msg; updateChips();
                    if (idx == 0) {
                        if (prefetchOther) load(1, false);
                        if (level.isEmpty()) setStatus("বিভাগ-সীমানা মেলেনি; জেলা-সীমানা খোঁজা হচ্ছে…");
                    }
                    if (state[0].equals("fail") && state[1].equals("fail")) showOutlineFallback();
                });
            }
        }, "admin-geo-" + lvl).start();
    }
    private void retryFailed() { for (int i = 0; i < 2; i++) if (state[i].equals("fail")) { state[i] = ""; load(i, false); } }

    private void showLevel(int idx) {
        if (data[idx] == null) {
            if (state[idx].equals("fail")) { setStatus((idx == 0 ? "ADM1" : "ADM2") + " পাওয়া যায়নি — ট্যাপ করে আবার চেষ্টা করুন।\n" + shortErr(error[idx])); }
            else load(idx, false);
            return;
        }
        level = idx == 0 ? "ADM1" : "ADM2";
        map.setShapes(data[idx], idx == 1);
        String noun = idx == 0 ? "বিভাগ/প্রদেশ" : "জেলা/সমতুল্য এলাকা";
        setStatus(GeoNames.bnDigits(data[idx].size()) + "টি " + noun + " (" + level + ") · সূত্র: geoBoundaries");
        info.setText("এলাকায় ট্যাপ করে নাম দেখুন।\n\nসূত্র: geoBoundaries gbOpen (CC BY 4.0) — William & Mary geoLab");
        updateChips();
    }
    private void showOutlineFallback() {
        setStatus("এই দেশের অভ্যন্তরীণ সীমানা-ডেটা আসেনি — শুধু দেশের রূপরেখা। ট্যাপ করে আবার চেষ্টা করুন।\n" + shortErr(error[0]));
        new Thread(() -> {
            try {
                List<GeoShape> w = GeoRepo.loadWorld(AdminMapActivity.this, null);
                for (GeoShape s : w) {
                    String code = GeoNames.iso3(s.id, s.name);
                    if (iso3.equals(code)) { final List<GeoShape> one = new java.util.ArrayList<>(); one.add(s); ui.post(() -> { if (!destroyed && level.isEmpty()) map.setShapes(one, false); }); return; }
                }
            } catch (Throwable ignored) { }
        }, "admin-outline").start();
    }
    private static String shortErr(String e) { if (e == null) return ""; e = e.trim(); int n = e.indexOf('\n'); if (n > 0) e = e.substring(0, n); return e.length() > 90 ? e.substring(0, 90) : e; }

    private void updateChips() {
        lv1.setText("বিভাগ (ADM1)" + mark(0)); lv2.setText("জেলা (ADM2)" + mark(1));
        lv1.setBackgroundColor(level.equals("ADM1") ? 0xDD1F6F8B : 0xAA17465B); lv2.setBackgroundColor(level.equals("ADM2") ? 0xDD1F6F8B : 0xAA17465B);
    }
    private String mark(int i) { return state[i].equals("ok") ? "" : state[i].equals("fail") ? "  ✕" : state[i].equals("load") ? "  ⏳" : ""; }
    private void setStatus(String s) { status.setText(s); status.setVisibility(s == null || s.isEmpty() ? View.GONE : View.VISIBLE); }

    // ------------------------------------------------------------------ pick
    private void onPick(GeoShape s, int count) {
        if (s == null) { info.setText("এলাকার বাইরে।"); return; }
        String bnName = "BGD".equals(iso3) ? GeoNames.bdBn(s.name) : null;
        StringBuilder sb = new StringBuilder();
        sb.append(bnName != null ? bnName + " • " + s.name : s.name);
        sb.append("\nস্তর: ").append(level.equals("ADM1") ? "বিভাগ/প্রদেশ (ADM1)" : "জেলা/সমতুল্য (ADM2)");
        sb.append("\nদেশ: ").append(bn.equals(en) ? en : bn + " • " + en);
        if (!s.iso.isEmpty()) sb.append("\nকোড: ").append(s.iso);
        sb.append("\nএই স্তরে মোট ").append(GeoNames.bnDigits(count)).append("টি এলাকা");
        sb.append("\n\nসূত্র: geoBoundaries (CC BY 4.0)");
        info.setText(sb.toString());
    }

    private TextView chip(String s) { TextView t = new TextView(this); t.setText(s); t.setTextColor(Color.WHITE); t.setTextSize(11); t.setGravity(Gravity.CENTER); t.setPadding(dp(14), 0, dp(14), 0); t.setBackgroundColor(0xAA17465B); return t; }
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + .5f); }
    @Override protected void onDestroy() { destroyed = true; ui.removeCallbacksAndMessages(null); super.onDestroy(); }

    interface Pick { void on(GeoShape s, int count); }

    // ================================================================== map view
    static final class MapView extends View {
        Pick onPick;
        private List<GeoShape> shapes; private Path[] paths; private int[] colour; private boolean detail;
        private double kx = 1;                           // lon scale (cos of mid latitude)
        private float bx0, by0, bx1, by1;                // projected bounds
        private final Matrix m = new Matrix(), inv = new Matrix();
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final ScaleGestureDetector sg;
        private float lastX, lastY, startX, startY; private boolean moved; private int selected = -1; private float fitScale = 1;
        private static final int[] PAL = {0xFF2F8F6A, 0xFF4E9F5B, 0xFF8AA650, 0xFFB89F4A, 0xFFC4824A, 0xFF4B9AA8, 0xFF5E86C4, 0xFF8F7BC2, 0xFFC27A98, 0xFF6BB58C, 0xFFD0A45E, 0xFFB5695B};

        MapView(Context c) {
            super(c);
            fill.setStyle(Paint.Style.FILL); line.setStyle(Paint.Style.STROKE); line.setStrokeJoin(Paint.Join.ROUND);
            text.setColor(Color.WHITE); text.setTextAlign(Paint.Align.CENTER); text.setShadowLayer(4f, 0, 0, 0xFF000000);
            sg = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override public boolean onScale(ScaleGestureDetector d) {
                    float cur = m.mapRadius(1f), f = d.getScaleFactor();
                    float next = cur * f;
                    if (next < fitScale * 0.7f) f = fitScale * 0.7f / cur; else if (next > fitScale * 80f) f = fitScale * 80f / cur;
                    m.postScale(f, f, d.getFocusX(), d.getFocusY()); invalidate(); return true;
                }
            });
        }

        void setShapes(List<GeoShape> list, boolean isDetail) {
            shapes = list; detail = isDetail; selected = -1;
            float x0 = 1e9f, y0 = 1e9f, x1 = -1e9f, y1 = -1e9f;
            for (GeoShape s : list) { x0 = Math.min(x0, s.minLon); x1 = Math.max(x1, s.maxLon); y0 = Math.min(y0, s.minLat); y1 = Math.max(y1, s.maxLat); }
            kx = Math.max(0.2, Math.cos(Math.toRadians((y0 + y1) / 2.0)));
            bx0 = (float) (x0 * kx); bx1 = (float) (x1 * kx); by0 = -y1; by1 = -y0;
            paths = new Path[list.size()];
            for (int i = 0; i < paths.length; i++) {
                Path p = new Path(); p.setFillType(Path.FillType.EVEN_ODD);
                for (float[] r : list.get(i).rings) {
                    p.moveTo((float) (r[0] * kx), -r[1]);
                    for (int k = 2; k < r.length; k += 2) p.lineTo((float) (r[k] * kx), -r[k + 1]);
                    p.close();
                }
                paths[i] = p;
            }
            colour = GlobeArt.colourIndexes(list);
            fit();
        }

        void fit() {
            if (shapes == null || getWidth() == 0) { invalidate(); return; }
            float w = getWidth(), h = getHeight(), top = h * 0.24f, bottom = h * 0.26f, side = w * 0.05f;
            float bw = Math.max(1e-4f, bx1 - bx0), bh = Math.max(1e-4f, by1 - by0);
            float sc = Math.min((w - 2 * side) / bw, (h - top - bottom) / bh);
            fitScale = sc;
            m.reset(); m.postTranslate(-(bx0 + bx1) / 2f, -(by0 + by1) / 2f); m.postScale(sc, sc);
            m.postTranslate(w / 2f, top + (h - top - bottom) / 2f);
            invalidate();
        }
        @Override protected void onSizeChanged(int w, int h, int ow, int oh) { super.onSizeChanged(w, h, ow, oh); fit(); }

        @Override protected void onDraw(Canvas c) {
            c.drawColor(0xFF06121F);
            if (paths == null) return;
            float scale = Math.max(1e-6f, m.mapRadius(1f));
            c.save(); c.concat(m);
            line.setStrokeWidth((detail ? 1.1f : 1.8f) / scale); line.setColor(0xDDF2F7FA);
            for (int i = 0; i < paths.length; i++) {
                fill.setColor(i == selected ? 0xFFFFC83D : PAL[colour[i] % PAL.length]);
                c.drawPath(paths[i], fill); c.drawPath(paths[i], line);
            }
            if (selected >= 0) { line.setColor(0xFFFFFFFF); line.setStrokeWidth(3f / scale); c.drawPath(paths[selected], line); }
            c.restore();
            // labels (screen space) when an area is big enough on screen
            float ts = getResources().getDisplayMetrics().scaledDensity * (detail ? 10f : 12f); text.setTextSize(ts);
            float[] pt = new float[2];
            for (int i = 0; i < shapes.size(); i++) {
                GeoShape s = shapes.get(i);
                float wpx = (s.maxLon - s.minLon) * (float) kx * scale, hpx = (s.maxLat - s.minLat) * scale;
                String label = label(s);
                if (wpx < text.measureText(label) * 0.9f || hpx < ts * 1.5f) continue;
                pt[0] = (float) (((s.minLon + s.maxLon) / 2.0) * kx); pt[1] = -(s.minLat + s.maxLat) / 2f; m.mapPoints(pt);
                if (pt[0] < -50 || pt[0] > getWidth() + 50 || pt[1] < 0 || pt[1] > getHeight()) continue;
                c.drawText(label, pt[0], pt[1] + ts / 3f, text);
            }
        }
        private String label(GeoShape s) { String b = GeoNames.bdBn(s.name); return b != null && bdMode ? b : s.name; }
        boolean bdMode = true;

        @Override public boolean onTouchEvent(MotionEvent e) {
            sg.onTouchEvent(e);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: lastX = startX = e.getX(); lastY = startY = e.getY(); moved = false; return true;
                case MotionEvent.ACTION_POINTER_DOWN: moved = true; return true;
                case MotionEvent.ACTION_POINTER_UP: lastX = e.getX(); lastY = e.getY(); return true;
                case MotionEvent.ACTION_MOVE:
                    if (e.getPointerCount() == 1 && !sg.isInProgress()) {
                        if (Math.abs(e.getX() - startX) + Math.abs(e.getY() - startY) > 16) moved = true;
                        if (moved) { m.postTranslate(e.getX() - lastX, e.getY() - lastY); invalidate(); }
                        lastX = e.getX(); lastY = e.getY();
                    } else if (e.getPointerCount() > 1) moved = true;
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!moved) pick(e.getX(), e.getY());
                    return true;
            }
            return true;
        }

        private void pick(float x, float y) {
            if (shapes == null || !m.invert(inv)) return;
            float[] p = {x, y}; inv.mapPoints(p);
            double lon = p[0] / kx, lat = -p[1];
            int hit = -1;
            for (int i = 0; i < shapes.size(); i++) if (shapes.get(i).contains(lon, lat)) { hit = i; break; }
            selected = hit; invalidate();
            if (onPick != null) onPick.on(hit >= 0 ? shapes.get(hit) : null, shapes.size());
        }
    }
}

package com.cylinderradar.launcher;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Smart Observatory "wall screen": a sci-fi command-centre display drawn entirely with Canvas.
 * Six live tiles; touching a tile opens a large window over the screen (touch outside or the X closes it).
 * The class is plain Canvas code so it can be rendered and tested off-device.
 */
final class ObsHud {
    static final int ACT_NONE = 0, ACT_EARTH = 1, ACT_CLOSE = 2, ACT_ECO = 3;
    static final int T_EARTH = 0, T_WEATHER = 1, T_TIME = 2, T_CAL = 3, T_SYS = 4, T_MOTION = 5;
    private static final String[] TITLES = {"EARTH", "WEATHER", "WORLD TIME", "CALENDAR", "SYSTEM", "MOTION"};
    private static final int CYAN = 0xFF5BE8FF, TEAL = 0xFF2EE6A8, AMBER = 0xFFFFB84D, MAG = 0xFFFF4FD8, VIOLET = 0xFF9B7BFF,
            INK = 0xFFE7F8FF, DIM = 0xFF7FA3B5, RED = 0xFFFF5A5F, BG0 = 0xFF020A12, BG1 = 0xFF06222E;
    private static final float PI = (float) Math.PI;

    // ---------------------------------------------------------------- live data (filled by the host activity)
    String city = "Dhaka";
    String packedForecast = "";        // optional "C|temp|code|wind|hum|feels|place" row, then "date|max|min|rain" rows
    int batPct = -1; boolean charging;
    long memUsed, memTotal, storFree, storTotal, uptimeMs;
    String model = "", os = "";
    boolean eco;
    static final int HN = 150;
    final float[][] hist = new float[6][HN];
    int hpos = 0;
    final float[] ax = new float[3], gy = new float[3];
    boolean haveAccel, haveGyro;

    private final float d;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rf = new RectF();
    private final RectF[] tile = new RectF[6];
    private final RectF btnBack = new RectF(), btnEco = new RectF(), winRect = new RectF(), winClose = new RectF();
    private int open = -1;
    private long openAt = 0L, closeAt = 0L;
    private int lastW = -1, lastH = -1;
    private final Calendar cal = Calendar.getInstance();
    private static final String[] CITY = {"Dhaka", "Dubai", "London", "New York", "Tokyo"};
    private static final String[] TZ = {"Asia/Dhaka", "Asia/Dubai", "Europe/London", "America/New_York", "Asia/Tokyo"};
    private static final float[][] LL = {{23.8f, 90.4f}, {25.2f, 55.3f}, {51.5f, -0.1f}, {40.7f, -74.0f}, {35.7f, 139.7f}};

    ObsHud(float density) {
        d = density <= 0 ? 1f : density;
        for (int i = 0; i < 6; i++) tile[i] = new RectF();
        fill.setStyle(Paint.Style.FILL); stroke.setStyle(Paint.Style.STROKE);
    }

    void pushMotion(boolean accel, float x, float y, float z) {
        if (accel) { ax[0] = x; ax[1] = y; ax[2] = z; haveAccel = true; }
        else { gy[0] = x; gy[1] = y; gy[2] = z; haveGyro = true; }
    }
    /** Called a few times per second to append the latest sensor values to the scope history. */
    void sampleMotion() {
        for (int i = 0; i < 3; i++) { hist[i][hpos] = ax[i]; hist[3 + i][hpos] = gy[i]; }
        hpos = (hpos + 1) % HN;
    }

    // ================================================================== layout
    private void layout(int w, int h) {
        if (w == lastW && h == lastH) return;
        lastW = w; lastH = h;
        float pad = w * 0.04f, gap = w * 0.025f, top = pad + w * 0.13f, bottom = h - pad - w * 0.12f;
        float hc = bottom - top, cw = w - 2 * pad;
        float hE = hc * 0.33f, hB = hc * 0.19f, hC = hc * 0.19f, hM = hc - hE - hB - hC - 3 * gap;
        float half = (cw - gap) / 2f;
        float y = top;
        tile[T_EARTH].set(pad, y, pad + cw, y + hE); y += hE + gap;
        tile[T_WEATHER].set(pad, y, pad + half, y + hB); tile[T_TIME].set(pad + half + gap, y, pad + cw, y + hB); y += hB + gap;
        tile[T_CAL].set(pad, y, pad + half, y + hC); tile[T_SYS].set(pad + half + gap, y, pad + cw, y + hC); y += hC + gap;
        tile[T_MOTION].set(pad, y, pad + cw, y + hM);
        btnBack.set(pad, h - pad - w * 0.1f, pad + w * 0.34f, h - pad);
        btnEco.set(w - pad - w * 0.34f, h - pad - w * 0.1f, w - pad, h - pad);
        winRect.set(pad, top - w * 0.01f, w - pad, bottom + w * 0.02f);
        lastTop = winRect.top; lastBottom = winRect.bottom;
    }
    private float lastTop, lastBottom;
    private static final float[] WIN_H = {1f, 1f, 0.66f, 0.86f, 0.70f, 0.78f};
    private void winFor(int wi, RectF out) {
        out.set(winRect.left, lastTop, winRect.right, lastTop + (lastBottom - lastTop) * WIN_H[wi]);
    }

    // ================================================================== input
    int tap(float x, float y, long now) {
        if (open >= 0) {
            float p = winProgress(now);
            winFor(open, winRect2);
            if (p > 0.5f && (winClose.contains(x, y) || !winRect2.contains(x, y))) { closeAt = now; openAt = 0L; int o = open; open = -(o + 2); return ACT_NONE; }
            if (p > 0.5f && open == T_EARTH && openEarthBtn.contains(x, y)) return ACT_EARTH;
            return ACT_NONE;
        }
        if (btnBack.contains(x, y)) return ACT_CLOSE;
        if (btnEco.contains(x, y)) return ACT_ECO;
        for (int i = 0; i < 6; i++) if (tile[i].contains(x, y)) {
            if (i == T_EARTH) return ACT_EARTH;
            open = i; openAt = now; return ACT_NONE;
        }
        return ACT_NONE;
    }
    /** Back key: closes an open window. Returns true when consumed. */
    boolean back(long now) {
        if (open >= 0) { int o = open; closeAt = now; open = -(o + 2); return true; }
        return false;
    }
    private final RectF openEarthBtn = new RectF(), winRect2 = new RectF();

    private float winProgress(long now) {
        if (open >= 0) return ease(Math.min(1f, (now - openAt) / 380f));
        if (open <= -2) return 1f - ease(Math.min(1f, (now - closeAt) / 300f));
        return 0f;
    }
    private static float ease(float t) { float u = 1f - t; return 1f - u * u * u; }

    // ================================================================== frame
    void draw(Canvas c, int w, int h, long now) {
        layout(w, h);
        cal.setTimeInMillis(now);
        background(c, w, h, now);
        header(c, w, now);
        for (int i = 0; i < 6; i++) drawTile(c, i, now);
        button(c, btnBack, "‹  EXIT", CYAN);
        button(c, btnEco, eco ? "ECO  ON" : "ECO  OFF", eco ? TEAL : DIM);
        // window
        if (open <= -2 && winProgress(now) <= 0.001f) open = -1;
        int wi = open >= 0 ? open : open <= -2 ? -(open + 2) : -1;
        float p = wi >= 0 ? winProgress(now) : 0f;
        if (wi >= 0 && p > 0.001f) drawWindow(c, wi, p, now, w, h);
        scanline(c, w, h, now);
    }

    private void background(Canvas c, int w, int h, long now) {
        fill.setShader(new LinearGradient(0, 0, 0, h, new int[]{BG1, BG0, BG0}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, fill);
        fill.setShader(null);
        // perspective-less blueprint grid
        stroke.setShader(null); stroke.setStrokeWidth(1f); stroke.setColor(0x0F5BE8FF);
        float step = w / 12f;
        for (float x = 0; x <= w; x += step) c.drawLine(x, 0, x, h, stroke);
        for (float y = 0; y <= h; y += step) c.drawLine(0, y, w, y, stroke);
    }
    private void scanline(Canvas c, int w, int h, long now) {
        if (eco) return;
        float y = (now % 5200L) / 5200f * (h + 160f) - 80f;
        fill.setShader(new LinearGradient(0, y - 60, 0, y + 60, new int[]{0x005BE8FF, 0x185BE8FF, 0x005BE8FF}, null, Shader.TileMode.CLAMP));
        c.drawRect(0, y - 60, w, y + 60, fill);
        fill.setShader(null);
    }

    private void header(Canvas c, int w, long now) {
        float pad = w * 0.04f, y = pad + w * 0.035f;
        label(c, "CYLINDER RADAR", pad, y, w * 0.045f, INK, true, Paint.Align.LEFT);
        label(c, "OBSERVATORY · LIVE TELEMETRY", pad, y + w * 0.05f, w * 0.026f, DIM, false, Paint.Align.LEFT);
        String t = two(cal.get(Calendar.HOUR_OF_DAY)) + ":" + two(cal.get(Calendar.MINUTE)) + ":" + two(cal.get(Calendar.SECOND));
        mono(c, t, w - pad, y + w * 0.008f, w * 0.07f, CYAN, Paint.Align.RIGHT);
        boolean on = (now / 500L) % 2L == 0L;
        fill.setColor(on ? TEAL : 0x552EE6A8); c.drawCircle(w - pad - w * 0.135f, y + w * 0.039f, w * 0.009f, fill);
        label(c, "ONLINE", w - pad, y + w * 0.047f, w * 0.026f, TEAL, true, Paint.Align.RIGHT);
        stroke.setShader(null); stroke.setStrokeWidth(1.5f); stroke.setColor(0x665BE8FF);
        c.drawLine(pad, pad + w * 0.1f, w - pad, pad + w * 0.1f, stroke);
    }

    private void button(Canvas c, RectF r, String s, int col) {
        fill.setColor(0x33081C28); c.drawRoundRect(r, 10 * d, 10 * d, fill);
        stroke.setShader(null); stroke.setStrokeWidth(1.5f); stroke.setColor(al(col, 0xB0)); c.drawRoundRect(r, 10 * d, 10 * d, stroke);
        label(c, s, r.centerX(), r.centerY() + r.height() * 0.17f, r.height() * 0.34f, col, true, Paint.Align.CENTER);
    }

    // ---------------------------------------------------------------- tiles
    private void frame(Canvas c, RectF r, String title, int col, boolean glow) {
        fill.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom, 0x66103848, 0x33061822, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, 8 * d, 8 * d, fill); fill.setShader(null);
        stroke.setShader(null); stroke.setStrokeWidth(1.2f); stroke.setColor(al(col, glow ? 0x90 : 0x55));
        c.drawRoundRect(r, 8 * d, 8 * d, stroke);
        // corner brackets
        float k = Math.min(r.width(), r.height()) * 0.12f;
        stroke.setStrokeWidth(2.6f); stroke.setColor(al(col, 0xF0));
        c.drawLine(r.left, r.top + k, r.left, r.top, stroke); c.drawLine(r.left, r.top, r.left + k, r.top, stroke);
        c.drawLine(r.right - k, r.top, r.right, r.top, stroke); c.drawLine(r.right, r.top, r.right, r.top + k, stroke);
        c.drawLine(r.left, r.bottom - k, r.left, r.bottom, stroke); c.drawLine(r.left, r.bottom, r.left + k, r.bottom, stroke);
        c.drawLine(r.right - k, r.bottom, r.right, r.bottom, stroke); c.drawLine(r.right, r.bottom, r.right, r.bottom - k, stroke);
        label(c, title, r.left + r.width() * 0.05f, r.top + r.width() * 0.055f, Math.min(r.width() * 0.05f, r.height() * 0.13f), col, true, Paint.Align.LEFT);
    }

    private void drawTile(Canvas c, int i, long now) {
        RectF r = tile[i];
        int col = i == T_EARTH ? CYAN : i == T_WEATHER ? AMBER : i == T_TIME ? VIOLET : i == T_CAL ? MAG : i == T_SYS ? TEAL : 0xFF7BC4FF;
        frame(c, r, TITLES[i], col, true);
        c.save();
        c.clipRect(r.left + 2, r.top + 2, r.right - 2, r.bottom - 2);
        switch (i) {
            case T_EARTH: globe(c, r, now, false); break;
            case T_WEATHER: weatherMini(c, r); break;
            case T_TIME: timeMini(c, r); break;
            case T_CAL: calMini(c, r); break;
            case T_SYS: sysMini(c, r, now); break;
            default: scope(c, r, 0, 3, "ACCEL  m/s²", 20f, now); break;
        }
        c.restore();
        // "touch" hint
        label(c, i == T_EARTH ? "TOUCH · OPEN 3D EARTH" : "TOUCH · OPEN", r.right - r.width() * 0.04f, r.top + r.width() * 0.055f, Math.min(r.width() * 0.032f, r.height() * 0.08f), al(col, 0xA0), false, Paint.Align.RIGHT);
    }

    // ---------------------------------------------------------------- globe with live day/night terminator
    private void globe(Canvas c, RectF r, long now, boolean big) {
        float R = Math.min(r.width() * 0.5f, r.height() * 0.5f) * (big ? 0.80f : 0.84f);
        float gx = r.centerX(), gy = r.centerY() + (big ? 0 : r.height() * 0.03f);
        float lonV = (float) Math.toRadians((now / 1000.0 * 6.0) % 360.0 + 60.0);
        float tilt = (float) Math.toRadians(22);
        // sun position (subsolar point) from UTC
        Calendar u = Calendar.getInstance(TimeZone.getTimeZone("UTC")); u.setTimeInMillis(now);
        double hrs = u.get(Calendar.HOUR_OF_DAY) + u.get(Calendar.MINUTE) / 60.0 + u.get(Calendar.SECOND) / 3600.0;
        double decl = Math.toRadians(-23.44 * Math.cos(2 * Math.PI * (u.get(Calendar.DAY_OF_YEAR) + 10) / 365.0));
        double sunLon = Math.toRadians(-(hrs - 12.0) * 15.0);
        float sx = (float) (Math.cos(decl) * Math.cos(sunLon)), sy = (float) Math.sin(decl), sz = (float) (Math.cos(decl) * Math.sin(sunLon));
        // body
        fill.setShader(new RadialGradient(gx - R * 0.3f, gy - R * 0.3f, R * 1.4f, new int[]{0xFF0B3A52, 0xFF041622, 0xFF020A12}, new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(gx, gy, R, fill); fill.setShader(null);
        fill.setShader(new RadialGradient(gx, gy, R * 1.6f, new int[]{al(CYAN, 0x30), al(CYAN, 0x00)}, null, Shader.TileMode.CLAMP));
        c.drawCircle(gx, gy, R * 1.6f, fill); fill.setShader(null);
        float[] sp0 = proj(decl, sunLon, lonV, tilt);
        float spx = sp0[0], spy = sp0[1];
        path.reset(); path.addCircle(gx, gy, R, Path.Direction.CW);
        c.save(); c.clipPath(path);
        fill.setShader(new RadialGradient(gx - R * spx * 0.75f, gy + R * spy * 0.75f, R * 1.35f, new int[]{0xB0000000, 0x60000000, 0x00000000}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(gx, gy, R, fill); fill.setShader(null);
        c.restore();
        stroke.setShader(null); stroke.setStrokeWidth(2f); stroke.setColor(al(CYAN, 0xC0)); c.drawCircle(gx, gy, R, stroke);
        // graticule, coloured by daylight
        stroke.setStrokeWidth(Math.max(1f, 1.3f * d * 0.7f));
        int latSteps = 8, lonSteps = 18;
        for (int a = -(latSteps / 2) + 1; a < latSteps / 2 + (latSteps % 2 == 0 ? 0 : 1); a++) {
            double lat = Math.toRadians(a * 180.0 / latSteps);
            strip(c, gx, gy, R, lat, 0, 0, true, 72, lonV, tilt, sx, sy, sz);
        }
        for (int m = 0; m < lonSteps; m++) strip(c, gx, gy, R, 0, Math.toRadians(m * 360.0 / lonSteps), 0, false, 36, lonV, tilt, sx, sy, sz);
        // city markers
        for (int i = 0; i < 5; i++) {
            double lat = Math.toRadians(LL[i][0]), lon = Math.toRadians(LL[i][1]);
            float[] p = proj(lat, lon, lonV, tilt);
            if (p[2] <= 0f) continue;
            float px = gx + R * p[0], py = gy - R * p[1];
            boolean day = day(lat, lon, sx, sy, sz) > 0;
            fill.setColor(al(day ? AMBER : CYAN, 0x40)); c.drawCircle(px, py, R * 0.075f, fill);
            fill.setColor(day ? AMBER : CYAN); c.drawCircle(px, py, R * 0.028f, fill);
            if (big || i == 0) label(c, CITY[i].toUpperCase(Locale.US), px + R * 0.06f, py - R * 0.04f, big ? R * 0.075f : R * 0.09f, INK, false, Paint.Align.LEFT);
        }
        // sun marker + satellite ring
        float[] sp = proj(decl, sunLon, lonV, tilt);
        if (sp[2] > 0) { fill.setColor(al(0xFFFFE08A, 0xB0)); c.drawCircle(gx + R * sp[0], gy - R * sp[1], R * 0.06f, fill); }
        if (!eco) {
            float a = (now % 9000L) / 9000f * 2f * PI;
            stroke.setStrokeWidth(1.2f); stroke.setColor(al(MAG, 0x60));
            rf.set(gx - R * 1.18f, gy - R * 0.32f, gx + R * 1.18f, gy + R * 0.32f);
            c.save(); c.rotate(-18f, gx, gy); c.drawOval(rf, stroke);
            fill.setColor(MAG); c.drawCircle(gx + R * 1.18f * (float) Math.cos(a), gy + R * 0.32f * (float) Math.sin(a), R * 0.03f, fill);
            c.restore();
        }
        // readout
        String s = "SUBSOLAR " + fmt1((float) Math.toDegrees(decl)) + "°N  " + fmt1((float) Math.toDegrees(sunLon)) + "°E";
        label(c, s, r.left + r.width() * 0.05f, r.bottom - r.height() * 0.06f, Math.min(r.width() * 0.032f, r.height() * 0.08f), DIM, false, Paint.Align.LEFT);
        label(c, "DAY / NIGHT LIVE", r.right - r.width() * 0.04f, r.bottom - r.height() * 0.06f, Math.min(r.width() * 0.032f, r.height() * 0.08f), TEAL, true, Paint.Align.RIGHT);
    }
    private static float[] pr = new float[3];
    private static float[] proj(double lat, double lon, float lonV, float tilt) {
        double dl = lon - lonV;
        double x = Math.cos(lat) * Math.sin(dl), yy = Math.sin(lat), zz = Math.cos(lat) * Math.cos(dl);
        double ct = Math.cos(tilt), st = Math.sin(tilt);
        pr[0] = (float) x; pr[1] = (float) (yy * ct - zz * st); pr[2] = (float) (yy * st + zz * ct);
        return pr;
    }
    private static float day(double lat, double lon, float sx, float sy, float sz) {
        return (float) (Math.cos(lat) * Math.cos(lon) * sx + Math.sin(lat) * sy + Math.cos(lat) * Math.sin(lon) * sz);
    }
    /** One graticule line (a parallel when parallel==true, otherwise a meridian), drawn in short day/night coloured segments. */
    private void strip(Canvas c, float gx, float gy, float R, double lat0, double lon0, int unused, boolean parallel, int n, float lonV, float tilt, float sx, float sy, float sz) {
        float px = 0, py = 0, pz = 0; boolean has = false;
        for (int k = 0; k <= n; k++) {
            double lat, lon;
            if (parallel) { lat = lat0; lon = 2 * Math.PI * k / n; }
            else { lat = -Math.PI / 2 + Math.PI * k / n; lon = lon0; }
            float[] p = proj(lat, lon, lonV, tilt);
            float x = gx + R * p[0], y = gy - R * p[1], z = p[2];
            if (has && z > 0 && pz > 0) {
                float dd = day(lat, lon, sx, sy, sz);
                int a = dd > 0.05f ? 0xC0 : dd > -0.05f ? 0x70 : 0x34;
                stroke.setColor(al(dd > 0 ? 0xFF8FF1FF : 0xFF2A8FB8, a));
                c.drawLine(px, py, x, y, stroke);
            }
            px = x; py = y; pz = z; has = true;
        }
    }

    // ---------------------------------------------------------------- weather
    private double[] cur() {
        if (!packedForecast.startsWith("C|")) return null;
        String row = packedForecast.substring(0, packedForecast.indexOf(';') < 0 ? packedForecast.length() : packedForecast.indexOf(';'));
        String[] f = row.split("\\|");
        try { return new double[]{Double.parseDouble(f[1]), Integer.parseInt(f[2]), Double.parseDouble(f[3]), Integer.parseInt(f[4]), Double.parseDouble(f[5])}; } catch (Exception e) { return null; }
    }
    private String[][] days() {
        String s = packedForecast;
        if (s.startsWith("C|")) { int i = s.indexOf(';'); s = i < 0 ? "" : s.substring(i + 1); }
        if (s.isEmpty()) return new String[0][];
        String[] rows = s.split(";");
        String[][] o = new String[rows.length][];
        for (int i = 0; i < rows.length; i++) o[i] = rows[i].split("\\|");
        return o;
    }
    static String cond(int code) {
        if (code < 0) return "—"; if (code == 0) return "Clear"; if (code == 1) return "Mostly clear"; if (code <= 3) return "Cloudy";
        if (code == 45 || code == 48) return "Fog"; if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) return "Rain";
        if (code >= 71 && code <= 77) return "Snow"; if (code >= 95) return "Thunderstorm"; return "—";
    }
    private void weatherMini(Canvas c, RectF r) {
        double[] cu = cur();
        float u = r.width();
        if (cu == null) { label(c, "Waiting for live data…", r.left + u * 0.05f, r.centerY(), u * 0.06f, DIM, false, Paint.Align.LEFT); return; }
        label(c, fmt1((float) cu[0]) + "°", r.left + u * 0.05f, r.top + r.height() * 0.66f, Math.min(u * 0.26f, r.height() * 0.42f), INK, true, Paint.Align.LEFT);
        label(c, cond((int) cu[1]) + " · " + city, r.left + u * 0.05f, r.top + r.height() * 0.84f, u * 0.052f, DIM, false, Paint.Align.LEFT);
        label(c, "Feels " + fmt1((float) cu[4]) + "°  ·  " + (int) cu[3] + "% hum", r.left + u * 0.05f, r.top + r.height() * 0.94f, u * 0.044f, AMBER, false, Paint.Align.LEFT);
    }
    private void weatherBig(Canvas c, RectF r, long now) {
        double[] cu = cur();
        float u = r.width(), x = r.left + u * 0.06f, y = r.top + u * 0.16f;
        if (cu != null) {
            label(c, fmt1((float) cu[0]) + "°C", x, y + u * 0.16f, u * 0.2f, INK, true, Paint.Align.LEFT);
            label(c, cond((int) cu[1]) + "  ·  " + city, x, y + u * 0.235f, u * 0.05f, DIM, false, Paint.Align.LEFT);
            label(c, "Feels like " + fmt1((float) cu[4]) + "°    Humidity " + (int) cu[3] + "%", x, y + u * 0.30f, u * 0.042f, AMBER, false, Paint.Align.LEFT);
            label(c, "Wind " + fmt1((float) cu[2]) + " km/h", x, y + u * 0.355f, u * 0.042f, AMBER, false, Paint.Align.LEFT);
        } else label(c, "Waiting for live data…", x, y + u * 0.1f, u * 0.06f, DIM, false, Paint.Align.LEFT);
        String[][] dd = days();
        if (dd.length == 0) return;
        float lo = 99, hi = -99;
        for (String[] f : dd) { if (f.length < 4) continue; lo = Math.min(lo, Float.parseFloat(f[2])); hi = Math.max(hi, Float.parseFloat(f[1])); }
        lo = (float) Math.floor(lo - 1); hi = (float) Math.ceil(hi + 1); if (hi - lo < 6) hi = lo + 6;
        float rowH = (r.bottom - (y + u * 0.40f) - u * 0.04f) / Math.max(1, dd.length), bx = r.left + u * 0.40f, bw = u * 0.30f;
        String[] dn = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
        for (int i = 0; i < dd.length; i++) {
            String[] f = dd[i]; if (f.length < 4) continue;
            float yy = y + u * 0.40f + rowH * (i + 0.65f);
            String dayName = f[0];
            try { String[] p = f[0].split("-"); Calendar k = Calendar.getInstance(); k.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2])); dayName = dn[k.get(Calendar.DAY_OF_WEEK) - 1] + " " + p[2]; } catch (Exception ignored) { }
            float mn = Float.parseFloat(f[2]), mx = Float.parseFloat(f[1]);
            float fs = Math.min(rowH * 0.42f, u * 0.045f);
            label(c, dayName, r.left + u * 0.06f, yy, fs, INK, true, Paint.Align.LEFT);
            label(c, fmt1(mn) + "°", bx - u * 0.02f, yy, fs * 0.9f, DIM, false, Paint.Align.RIGHT);
            fill.setShader(null); fill.setColor(0x26C4D2E0); rf.set(bx, yy - rowH * 0.34f, bx + bw, yy - rowH * 0.12f); c.drawRoundRect(rf, 6, 6, fill);
            float a0 = bx + bw * (mn - lo) / (hi - lo), a1 = bx + bw * (mx - lo) / (hi - lo);
            fill.setShader(new LinearGradient(a0, 0, Math.max(a1, a0 + 10), 0, 0xFF3D8BFF, AMBER, Shader.TileMode.CLAMP));
            rf.set(a0, yy - rowH * 0.34f, Math.max(a1, a0 + 8), yy - rowH * 0.12f); c.drawRoundRect(rf, 6, 6, fill); fill.setShader(null);
            label(c, fmt1(mx) + "°", bx + bw + u * 0.02f, yy, fs * 0.9f, INK, true, Paint.Align.LEFT);
            int rain = Integer.parseInt(f[3]);
            label(c, rain + "%", r.right - u * 0.06f, yy, fs * 0.85f, rain >= 30 ? 0xFF7BC4FF : DIM, false, Paint.Align.RIGHT);
        }
    }

    // ---------------------------------------------------------------- world time
    private void timeMini(Canvas c, RectF r) {
        float u = r.width(), rowH = (r.height() - u * 0.10f) / 5f;
        for (int i = 0; i < 5; i++) {
            Calendar t = Calendar.getInstance(TimeZone.getTimeZone(TZ[i])); t.setTimeInMillis(cal.getTimeInMillis());
            float y = r.top + u * 0.10f + rowH * (i + 0.8f);
            boolean dayT = t.get(Calendar.HOUR_OF_DAY) >= 6 && t.get(Calendar.HOUR_OF_DAY) < 18;
            fill.setColor(dayT ? AMBER : VIOLET); c.drawCircle(r.left + u * 0.07f, y - rowH * 0.28f, rowH * 0.14f, fill);
            label(c, CITY[i].toUpperCase(Locale.US), r.left + u * 0.12f, y, rowH * 0.46f, INK, false, Paint.Align.LEFT);
            mono(c, two(t.get(Calendar.HOUR_OF_DAY)) + ":" + two(t.get(Calendar.MINUTE)), r.right - u * 0.05f, y, rowH * 0.56f, VIOLET, Paint.Align.RIGHT);
        }
    }
    private void timeBig(Canvas c, RectF r) {
        float u = r.width(), rowH = (r.height() - u * 0.2f) / 5f;
        int baseOff = TimeZone.getTimeZone("Asia/Dhaka").getOffset(cal.getTimeInMillis());
        for (int i = 0; i < 5; i++) {
            TimeZone tz = TimeZone.getTimeZone(TZ[i]);
            Calendar t = Calendar.getInstance(tz); t.setTimeInMillis(cal.getTimeInMillis());
            float y = r.top + u * 0.17f + rowH * (i + 0.62f);
            boolean dayT = t.get(Calendar.HOUR_OF_DAY) >= 6 && t.get(Calendar.HOUR_OF_DAY) < 18;
            fill.setColor(dayT ? AMBER : 0xFF9FB4C8); c.drawCircle(r.left + u * 0.09f, y - rowH * 0.18f, rowH * 0.2f, fill);
            if (!dayT) { fill.setColor(BG0); c.drawCircle(r.left + u * 0.09f + rowH * 0.09f, y - rowH * 0.24f, rowH * 0.17f, fill); }
            label(c, CITY[i], r.left + u * 0.16f, y - rowH * 0.12f, rowH * 0.34f, INK, true, Paint.Align.LEFT);
            int off = (tz.getOffset(cal.getTimeInMillis()) - baseOff) / 60000;
            label(c, off == 0 ? "your time zone" : fmt1(Math.abs(off) / 60f) + " h " + (off > 0 ? "ahead of" : "behind") + " Dhaka", r.left + u * 0.16f, y + rowH * 0.2f, rowH * 0.22f, DIM, false, Paint.Align.LEFT);
            mono(c, two(t.get(Calendar.HOUR_OF_DAY)) + ":" + two(t.get(Calendar.MINUTE)) + ":" + two(t.get(Calendar.SECOND)), r.right - u * 0.06f, y + rowH * 0.08f, rowH * 0.46f, VIOLET, Paint.Align.RIGHT);
        }
    }

    // ---------------------------------------------------------------- calendar (week starts on Saturday)
    private void calMini(Canvas c, RectF r) {
        String[] mn = {"JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"};
        String[] dn = {"SUNDAY", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY"};
        float u = r.width();
        label(c, String.valueOf(cal.get(Calendar.DAY_OF_MONTH)), r.left + u * 0.06f, r.top + r.height() * 0.68f, Math.min(u * 0.3f, r.height() * 0.5f), INK, true, Paint.Align.LEFT);
        label(c, mn[cal.get(Calendar.MONTH)] + " " + cal.get(Calendar.YEAR), r.left + u * 0.06f, r.top + r.height() * 0.84f, u * 0.06f, MAG, true, Paint.Align.LEFT);
        label(c, dn[cal.get(Calendar.DAY_OF_WEEK) - 1], r.left + u * 0.06f, r.top + r.height() * 0.95f, u * 0.045f, DIM, false, Paint.Align.LEFT);
    }
    private void calBig(Canvas c, RectF r) {
        String[] mn = {"January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"};
        float u = r.width();
        label(c, mn[cal.get(Calendar.MONTH)] + " " + cal.get(Calendar.YEAR), r.left + u * 0.06f, r.top + u * 0.2f, u * 0.075f, INK, true, Paint.Align.LEFT);
        label(c, "Week " + cal.get(Calendar.WEEK_OF_YEAR) + "  ·  day " + cal.get(Calendar.DAY_OF_YEAR), r.left + u * 0.06f, r.top + u * 0.26f, u * 0.04f, DIM, false, Paint.Align.LEFT);
        Calendar f = (Calendar) cal.clone(); f.set(Calendar.DAY_OF_MONTH, 1);
        int first = f.get(Calendar.DAY_OF_WEEK) % 7, days = cal.getActualMaximum(Calendar.DAY_OF_MONTH), today = cal.get(Calendar.DAY_OF_MONTH);
        float cw = (u * 0.88f) / 7f, gx = r.left + u * 0.06f, gy0 = r.top + u * 0.36f, ch = Math.min(cw * 0.95f, (r.bottom - gy0 - u * 0.1f) / 6f);
        String[] wd = {"Sat", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri"};
        for (int i = 0; i < 7; i++) label(c, wd[i], gx + cw * (i + 0.5f), r.top + u * 0.33f, cw * 0.3f, (i == 0 || i == 6) ? MAG : DIM, true, Paint.Align.CENTER);
        for (int n = 1; n <= days; n++) {
            int idx = first + n - 1, col = idx % 7, row = idx / 7;
            float x = gx + cw * (col + 0.5f), y = gy0 + ch * (row + 0.7f);
            int color = (col == 0 || col == 6) ? MAG : INK;
            if (n == today) { fill.setColor(0xFFE3B48B); c.drawCircle(x, y - ch * 0.17f, ch * 0.4f, fill); color = 0xFF1A0F08; }
            label(c, String.valueOf(n), x, y, ch * 0.42f, color, true, Paint.Align.CENTER);
        }
        label(c, "Weekend: Friday and Saturday", r.left + u * 0.06f, r.bottom - u * 0.04f, u * 0.036f, MAG, false, Paint.Align.LEFT);
    }

    // ---------------------------------------------------------------- system
    private void sysMini(Canvas c, RectF r, long now) {
        float u = r.width(), x = r.left + u * 0.06f, bw = u * 0.88f, y = r.top + r.height() * 0.30f, step = r.height() * 0.22f;
        bar(c, x, y, bw, step * 0.32f, "BATTERY", batPct < 0 ? 0f : batPct / 100f, batPct < 0 ? "—" : batPct + "%" + (charging ? " ⚡" : ""), batPct >= 0 && batPct <= 20 ? RED : TEAL, u);
        bar(c, x, y + step, bw, step * 0.32f, "MEMORY", memTotal > 0 ? (float) memUsed / memTotal : 0f, memTotal > 0 ? (memUsed * 100 / memTotal) + "%" : "—", CYAN, u);
        bar(c, x, y + step * 2, bw, step * 0.32f, "STORAGE", storTotal > 0 ? 1f - (float) storFree / storTotal : 0f, storTotal > 0 ? ((storTotal - storFree) * 100 / storTotal) + "%" : "—", AMBER, u);
    }
    private void bar(Canvas c, float x, float y, float w, float h, String name, float v, String val, int col, float u) {
        label(c, name, x, y - h * 0.35f, u * 0.036f, DIM, false, Paint.Align.LEFT);
        label(c, val, x + w, y - h * 0.35f, u * 0.04f, col, true, Paint.Align.RIGHT);
        fill.setColor(0x22FFFFFF); rf.set(x, y, x + w, y + h); c.drawRoundRect(rf, h / 2, h / 2, fill);
        fill.setShader(new LinearGradient(x, 0, x + w, 0, al(col, 0x70), col, Shader.TileMode.CLAMP));
        rf.set(x, y, x + Math.max(h, w * Math.max(0f, Math.min(1f, v))), y + h); c.drawRoundRect(rf, h / 2, h / 2, fill); fill.setShader(null);
    }
    private void sysBig(Canvas c, RectF r, long now) {
        float u = r.width(), x = r.left + u * 0.06f, bw = u * 0.88f, y = r.top + u * 0.2f, step = u * 0.2f;
        bar(c, x, y, bw, u * 0.045f, "BATTERY", batPct < 0 ? 0f : batPct / 100f, batPct < 0 ? "—" : batPct + "%" + (charging ? "  charging" : ""), batPct >= 0 && batPct <= 20 ? RED : TEAL, u * 1.4f);
        bar(c, x, y + step, bw, u * 0.045f, "MEMORY  " + mb(memUsed) + " / " + mb(memTotal), memTotal > 0 ? (float) memUsed / memTotal : 0f, memTotal > 0 ? (memUsed * 100 / memTotal) + "%" : "—", CYAN, u * 1.4f);
        bar(c, x, y + step * 2, bw, u * 0.045f, "STORAGE  " + mb(storTotal - storFree) + " / " + mb(storTotal), storTotal > 0 ? 1f - (float) storFree / storTotal : 0f, storTotal > 0 ? ((storTotal - storFree) * 100 / storTotal) + "%" : "—", AMBER, u * 1.4f);
        long s = uptimeMs / 1000L;
        String[][] rows = {{"UPTIME", (s / 86400L) + "d " + two((int) (s / 3600L % 24)) + ":" + two((int) (s / 60L % 60)) + ":" + two((int) (s % 60))}, {"DEVICE", model}, {"ANDROID", os}, {"CPU CORES", String.valueOf(Runtime.getRuntime().availableProcessors())}};
        for (int i = 0; i < rows.length; i++) {
            float yy = y + step * 3.05f + i * u * 0.085f;
            label(c, rows[i][0], x, yy, u * 0.04f, DIM, false, Paint.Align.LEFT);
            mono(c, rows[i][1], x + bw, yy, u * 0.05f, INK, Paint.Align.RIGHT);
        }
    }
    private static String mb(long b) { return b >= 1L << 30 ? fmt1(b / (float) (1L << 30)) + " GB" : (b >> 20) + " MB"; }

    // ---------------------------------------------------------------- motion scope
    private void scope(Canvas c, RectF r, int first, int count, String name, float range, long now) {
        float u = r.width(), x0 = r.left + u * 0.05f, x1 = r.right - u * 0.05f, y0 = r.top + r.height() * 0.24f, y1 = r.bottom - r.height() * 0.16f, yc = (y0 + y1) / 2f;
        stroke.setShader(null); stroke.setStrokeWidth(1f); stroke.setColor(0x305BE8FF);
        for (int i = 0; i <= 4; i++) { float yy = y0 + (y1 - y0) * i / 4f; c.drawLine(x0, yy, x1, yy, stroke); }
        for (int i = 0; i <= 10; i++) { float xx = x0 + (x1 - x0) * i / 10f; c.drawLine(xx, y0, xx, y1, stroke); }
        int[] cols = {RED, TEAL, CYAN};
        stroke.setStrokeWidth(Math.max(1.6f, 2f * d * 0.7f));
        for (int k = 0; k < count; k++) {
            path.reset();
            for (int i = 0; i < HN; i++) {
                float v = hist[first + k][(hpos + i) % HN];
                float xx = x0 + (x1 - x0) * i / (HN - 1f), yy = yc - (y1 - y0) / 2f * Math.max(-1f, Math.min(1f, v / range));
                if (i == 0) path.moveTo(xx, yy); else path.lineTo(xx, yy);
            }
            stroke.setColor(cols[k]); c.drawPath(path, stroke);
        }
        boolean ok = first == 0 ? haveAccel : haveGyro;
        label(c, ok ? name + "   X  Y  Z" : name + "   (sensor not available)", x0, r.bottom - r.height() * 0.04f, Math.min(u * 0.034f, r.height() * 0.07f), ok ? DIM : AMBER, false, Paint.Align.LEFT);
    }

    // ---------------------------------------------------------------- window
    private void drawWindow(Canvas c, int wi, float p, long now, int w, int h) {
        // dim the wall behind the window
        fill.setColor(al(0xFF000000, (int) (0xAA * p))); c.drawRect(0, 0, w, h, fill);
        winFor(wi, winRect2);
        RectF t = tile[wi], W = winRect2, r = new RectF(t.left + (W.left - t.left) * p, t.top + (W.top - t.top) * p,
                t.right + (W.right - t.right) * p, t.bottom + (W.bottom - t.bottom) * p);
        int col = wi == T_WEATHER ? AMBER : wi == T_TIME ? VIOLET : wi == T_CAL ? MAG : wi == T_SYS ? TEAL : wi == T_EARTH ? CYAN : 0xFF7BC4FF;
        fill.setColor(0xF2041018); c.drawRoundRect(r, 12 * d, 12 * d, fill);
        frame(c, r, TITLES[wi] + "  ·  LIVE", col, true);
        float k = Math.min(r.width(), r.height()) * 0.07f;
        winClose.set(r.right - k * 1.5f, r.top + k * 0.2f, r.right - k * 0.2f, r.top + k * 1.5f);
        label(c, "✕", winClose.centerX(), winClose.centerY() + k * 0.3f, k * 0.9f, INK, true, Paint.Align.CENTER);
        if (p < 0.75f) return;
        c.save(); c.clipRect(r.left + 2, r.top + 2, r.right - 2, r.bottom - 2);
        RectF body = new RectF(r.left, r.top + k * 0.8f, r.right, r.bottom);
        switch (wi) {
            case T_WEATHER: weatherBig(c, body, now); break;
            case T_TIME: timeBig(c, body); break;
            case T_CAL: calBig(c, body); break;
            case T_SYS: sysBig(c, body, now); break;
            case T_MOTION: {
                RectF a = new RectF(body.left, body.top, body.right, body.top + body.height() * 0.48f), g = new RectF(body.left, a.bottom, body.right, body.bottom);
                scope(c, a, 0, 3, "ACCELEROMETER  m/s²", 20f, now);
                scope(c, g, 3, 3, "GYROSCOPE  rad/s", 8f, now);
                break;
            }
            default: break;
        }
        c.restore();
    }

    // ================================================================== text helpers
    private void label(Canvas c, String s, float x, float y, float size, int color, boolean bold, Paint.Align al) {
        text.setShader(null); text.setTextAlign(al); text.setTextSize(Math.max(6f, size)); text.setColor(color);
        text.setTypeface(Typeface.create("sans-serif-medium", bold ? Typeface.BOLD : Typeface.NORMAL));
        c.drawText(s, x, y, text);
    }
    private void mono(Canvas c, String s, float x, float y, float size, int color, Paint.Align al) {
        text.setShader(null); text.setTextAlign(al); text.setTextSize(Math.max(6f, size)); text.setColor(color); text.setTypeface(Typeface.MONOSPACE);
        c.drawText(s, x, y, text);
    }
    private static String two(int n) { return n < 10 ? "0" + n : String.valueOf(n); }
    private static String fmt1(float v) { float r = Math.round(v * 10f) / 10f; return r == (long) r ? String.valueOf((long) r) : String.valueOf(r); }
    private static int al(int c, int a) { return (Math.max(0, Math.min(255, a)) << 24) | (c & 0xFFFFFF); }
}

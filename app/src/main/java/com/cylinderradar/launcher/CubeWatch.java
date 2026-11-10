package com.cylinderradar.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.Matrix;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Glass cube with a fully animated 3D mechanical skeleton watch inside (home-screen centrepiece).
 *
 * Pure Canvas "software 3D": every part lives in the watch frame (HTML Glass_Cube_Watch units, y-down), is rotated by the
 * cube orientation and projected with perspective. Flat parts (gears, bridges, jewels...) use one cached local Path and a
 * per-part affine matrix (weak perspective); large shapes (case rings, dial track, hands) are projected vertex by vertex so
 * they stay exactly on the perspective cube. Painter's order is exact for stacked parallel planes (sorted by z, reversed
 * when the watch is seen from behind). The six cube faces carry live information panels (see panel*() methods).
 */
final class CubeWatch {
    // ------------------------------------------------------------------ units & palette
    private static final float K = 1.15f, M = 1.6f * K, C = 320f, HALF = 380f;
    private static final float WS = 0.95f, WZ = -18f * 0.95f, CAMD = 2050f;
    private static final float PI = (float) Math.PI, TAU = PI * 2f, DEG = PI / 180f;
    private static final int PW = 512;

    static final int BARREL = 0xFF3D8BFF, CENTER = 0xFFFFD23F, THIRD = 0xFF2EE6A8, FOURTH = 0xFFFF6B5A, ESCAPE = 0xFFFF9F1C,
            FORK = 0xFFFF4FD8, BALANCE = 0xFF9B7BFF, STEEL = 0xFFC4D2E0, RUBY = 0xFFE0364F, LUME = 0xFF9FE8C8,
            BEZEL = 0xFFD9A574, GOLDB = 0xFFFFD15C, DIAL = 0xFF08246E, GOLD = 0xFFE3B48B, INK = 0xFFE7EDF3, DIM = 0xFF8FA0B2;

    // ------------------------------------------------------------------ movement layout (W-space, y down)
    private static final float aBar = 200 * DEG, aT = 30 * DEG, aF = 80 * DEG, aE = -30 * DEG, phi = 50 * DEG;
    private static final float[] A = {C, C}, BAR, T, F, E, P, B;
    private static final float dirx = (float) Math.cos(phi), diry = (float) Math.sin(phi);
    static {
        BAR = place(A, aBar, 60, 64); T = place(A, aT, 60, 8); F = place(T, aF, 64, 8); E = place(F, aE, 60, 8);
        P = new float[]{E[0] + 40 * K * dirx, E[1] + 40 * K * diry};
        B = new float[]{P[0] + 48 * K * dirx, P[1] + 48 * K * diry};
    }
    private static float[] place(float[] p, float a, int n1, int n2) {
        float d = M * (n1 + n2) / 2f;
        return new float[]{p[0] + d * (float) Math.cos(a), p[1] + d * (float) Math.sin(a)};
    }
    private static float mesh(float th1, int n1, int n2, float a) {
        float r = (float) n1 / n2;
        return -r * th1 + (1f + r) * a + PI - PI / n2;
    }

    // ------------------------------------------------------------------ geometry on screen
    private float cx, cy, half, unit, focal, d = 1f;
    private final float[] R = new float[9], Rw = new float[9];
    private float[] RA = R;                               // matrix used by the projection helpers (cube frame or watch frame)
    private final float[] qw = new float[4];
    private float cx0, cy0;
    private final float[] q = {0, 0, 0, 1};
    private int accent = 0xFF39FF88;
    private boolean eco = false;

    // ------------------------------------------------------------------ interaction state
    private static final int ST_SWAY = 0, ST_TWEEN = 1, ST_HOLD = 2, ST_DRAG = 3, ST_COAST = 4, ST_TUMBLE = 5;
    private int st = ST_SWAY;
    private long stSince = 0L;
    private final float[] tweenFrom = new float[4], tweenTo = new float[4];
    private float velX = 0f, velY = 0f;
    private int faceIdx = -1;
    private long lastNow = 0L;
    private long phase0 = 0L;

    // ------------------------------------------------------------------ drawing helpers
    private final Paint fillP = new Paint(Paint.ANTI_ALIAS_FLAG), strokeP = new Paint(Paint.ANTI_ALIAS_FLAG), textP = new Paint(Paint.ANTI_ALIAS_FLAG), bmpP = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Path tmp = new Path(), tmp2 = new Path(), winPath = new Path();
    private final Matrix mat = new Matrix();
    private final float[] mv = new float[9];
    private float prx, pry, prs, prz;                  // last exact projection
    private float shadeAll = 1f;                        // global lighting factor for the watch plane
    private boolean frontView = true;

    // ------------------------------------------------------------------ time-driven state
    private float thA, thBar, thT, thF, thE, forkRot, balRot, hourA, minA, secA;
    private double sRaw, tReal;
    private final Calendar cal = Calendar.getInstance();

    // ------------------------------------------------------------------ parts
    private interface Drawer { void draw(Canvas c); }
    private static final class Part { final float z; final Drawer fn; Part(float z, Drawer fn) { this.z = z; this.fn = fn; } }
    private final ArrayList<Part> parts = new ArrayList<>();

    // cached local-space paths
    private Path pBarrel, pCentre, pThird8, pThird64, pFourth8, pFourth60, pEsc8, pStar, pBarrelSpring, pHair;

    // ------------------------------------------------------------------ panels
    private final Bitmap[] panel = new Bitmap[6];
    private final Canvas[] panelCv = new Canvas[6];
    private final boolean[] panelDirty = new boolean[6];
    private int lastSecPanel = -1, lastMinPanel = -1, lastDayPanel = -1;
    private static final int F_FRONT = 0, F_BACK = 1, F_RIGHT = 2, F_LEFT = 3, F_TOP = 4, F_BOTTOM = 5;
    // face basis (u = texture right, v = texture up, n = outward normal) in cube space
    private static final float[][] FU = new float[6][3], FV = new float[6][3], FN = new float[6][3];
    private static final float[][] FACE_EULER = {{0, 0}, {0, PI}, {0, PI / 2}, {0, -PI / 2}, {-PI / 2, 0}, {PI / 2, 0}};   // rx, ry
    private static final float[][] FACE_VIEW = {{0, 0, 0}, {0, PI, 0}, {0, -PI / 2, 0}, {0, PI / 2, 0}, {PI / 2, 0, 0}, {-PI / 2, 0, 0}};
    static {
        for (int f = 0; f < 6; f++) {
            float rx = FACE_EULER[f][0], ry = FACE_EULER[f][1];
            float cxr = (float) Math.cos(rx), sxr = (float) Math.sin(rx), cyr = (float) Math.cos(ry), syr = (float) Math.sin(ry);
            // R = Rx * Ry applied to the basis vectors
            float[][] b = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
            for (int k = 0; k < 3; k++) {
                float x = b[k][0], y = b[k][1], z = b[k][2];
                float x1 = x * cyr + z * syr, y1 = y, z1 = -x * syr + z * cyr;           // Ry
                float x2 = x1, y2 = y1 * cxr - z1 * sxr, z2 = y1 * sxr + z1 * cxr;       // Rx
                float[] t = k == 0 ? FU[f] : k == 1 ? FV[f] : FN[f];
                t[0] = round(x2); t[1] = round(y2); t[2] = round(z2);
            }
        }
    }
    private static float round(float v) { return Math.abs(v) < 1e-5f ? 0f : v; }

    // ------------------------------------------------------------------ live data (weather etc.)
    private String city = "Dhaka", temp = "";
    private int wCode = -1;
    private long wAt = 0L;
    private volatile String forecastPacked = "";
    private volatile long forecastAt = 0L;
    private long forecastTry = 0L;
    private final Context ctx;

    private static final String[] CITY_NAMES = {"Dhaka", "Dubai", "London", "New York", "Tokyo"};
    private static final String[] CITY_TZ = {"Asia/Dhaka", "Asia/Dubai", "Europe/London", "America/New_York", "Asia/Tokyo"};

    CubeWatch(Context ctx, float density) {
        this.ctx = ctx;
        this.d = density <= 0 ? 1f : density;
        buildPaths();
        buildParts();
        buildEngine();
        for (int i = 0; i < 6; i++) {
            panel[i] = Bitmap.createBitmap(PW, PW, Bitmap.Config.ARGB_8888);
            panelCv[i] = new Canvas(panel[i]);
            panelDirty[i] = true;
        }
        fillP.setStyle(Paint.Style.FILL);
        strokeP.setStyle(Paint.Style.STROKE);
        try {
            SharedPreferences sp = ctx.getSharedPreferences("cube_watch", 0);
            String cached = sp.getString("fc", "");
            long cachedAt = sp.getLong("fc_at", 0L);
            if (!cached.isEmpty()) { applyLive(cached); forecastAt = cachedAt; }
        } catch (Throwable ignored) { }
        setOrientation(swayQuat(0.0));
    }

    // =================================================================================================
    // Public API
    // =================================================================================================
    void setGeometry(float cx, float cy, float half) {
        this.cx0 = cx; this.cy0 = cy; this.cx = cx; this.cy = cy; this.half = half;
        this.unit = half / HALF;
        this.focal = CAMD * unit;
    }
    void setAccent(int acc) { accent = acc; }
    void setWeather(String city, String temp, int code, long at) {
        boolean ch = !safe(city).equals(this.city) || !safe(temp).equals(this.temp) || code != wCode;
        this.city = city == null || city.isEmpty() ? "Dhaka" : city; this.temp = safe(temp); this.wCode = code; this.wAt = at;
        if (ch) { panelDirty[F_BACK] = true; panelDirty[F_FRONT] = true; }
    }
    private static String safe(String s) { return s == null ? "" : s; }

    boolean contains(float x, float y) {
        float dx = x - hitCx, dy = y - hitCy;
        return dx * dx + dy * dy <= hitR * hitR;
    }
    /** Where the focused (opaque) face should come to rest on screen. */
    void setFocusCenter(float x, float y) { focusCx = x; focusCy = y; }
    boolean focused() { return focus > 0.02f; }
    float focusAmount() { return smooth(focus); }
    /** Finger down on the cube. */
    void beginDrag(long now) { st = ST_DRAG; stSince = now; velX = velY = 0f; faceIdx = -1; }
    void dragBy(float dx, float dy) {
        if (st != ST_DRAG) return;
        float k = 1.25f / Math.max(1f, half);
        spin(dx * k, dy * k);
        velX = dx * k; velY = dy * k;
    }
    void endDrag(float vxPxPerSec, float vyPxPerSec, long now) {
        float k = 1.25f / Math.max(1f, half);
        velX = Math.max(-9f, Math.min(9f, vxPxPerSec * k)); velY = Math.max(-9f, Math.min(9f, vyPxPerSec * k));
        st = ST_COAST; stSince = now;
    }
    /** A tap rolls the cube like a die; it lands on a random face, which turns opaque and comes to the front. */
    void tap(long now) {
        if (st == ST_TUMBLE && now - stSince < 600L) return;
        int f;
        do { f = rnd.nextInt(6); } while (f == focusFace && focus > 0.5f);
        faceIdx = f; focusFace = f;
        tweenFrom[0] = q[0]; tweenFrom[1] = q[1]; tweenFrom[2] = q[2]; tweenFrom[3] = q[3];
        float[] fv = FACE_VIEW[f];
        eulerYXZ(fv[0], fv[1], fv[2], tweenTo);
        float ax = rnd.nextFloat() * 2f - 1f, ay = rnd.nextFloat() * 2f - 1f, az = rnd.nextFloat() * 2f - 1f;
        float n = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        if (n < 0.2f) { ax = 1f; ay = 0.6f; az = 0.3f; n = (float) Math.sqrt(1.45f); }
        tumbleAxis[0] = ax / n; tumbleAxis[1] = ay / n; tumbleAxis[2] = az / n;
        tumbleN = 2 + rnd.nextInt(2);
        returning = false;
        st = ST_TUMBLE; stSince = now;
    }
    /** Leave the focused face and resume the slow spin. */
    void dismiss(long now) {
        if (st == ST_HOLD || st == ST_TUMBLE) startReturn(now);
    }
    private void startReturn(long now) {
        double t = (now - phase0) / 1000.0;
        tweenFrom[0] = q[0]; tweenFrom[1] = q[1]; tweenFrom[2] = q[2]; tweenFrom[3] = q[3];
        st = ST_TWEEN; stSince = now; faceIdx = -1;
        float[] sq = swayQuat(t + 1.0);
        tweenTo[0] = sq[0]; tweenTo[1] = sq[1]; tweenTo[2] = sq[2]; tweenTo[3] = sq[3];
        returning = true;
    }
    boolean interacting() { return st != ST_SWAY; }

    // =================================================================================================
    // Orientation
    // =================================================================================================
    private static void eulerYXZ(float x, float y, float z, float[] o) {
        float c1 = (float) Math.cos(x / 2), c2 = (float) Math.cos(y / 2), c3 = (float) Math.cos(z / 2);
        float s1 = (float) Math.sin(x / 2), s2 = (float) Math.sin(y / 2), s3 = (float) Math.sin(z / 2);
        o[0] = s1 * c2 * c3 + c1 * s2 * s3;
        o[1] = c1 * s2 * c3 - s1 * c2 * s3;
        o[2] = c1 * c2 * s3 - s1 * s2 * c3;
        o[3] = c1 * c2 * c3 + s1 * s2 * s3;
    }
    private float tiltX = 0f, tiltY = 0f, tgtX = 0f, tgtY = 0f, avgAx = 0f, avgAy = 0f;
    private boolean avgInit = false;
    private static final float[] QALIGN = alignQuat();
    private final float[] swq = new float[4], qy = new float[4], qwob = new float[4], qtmp = new float[4];
    /** Rotation taking the cube diagonal (1,1,1) to screen-up, so the cube balances on its (-1,-1,-1) corner. */
    private static float[] alignQuat() {
        float th = (float) Math.acos(1.0 / Math.sqrt(3.0)), s = (float) Math.sin(th / 2), k = (float) (1.0 / Math.sqrt(2.0));
        return new float[]{-k * s, 0f, k * s, (float) Math.cos(th / 2)};
    }
    /** Spinning-top pose: slow spin about the vertical diagonal plus a faint precession wobble. */
    private float[] swayQuat(double t) {
        float psi = (float) ((t * 0.30) % (double) TAU);
        qy[0] = 0f; qy[1] = (float) Math.sin(psi / 2); qy[2] = 0f; qy[3] = (float) Math.cos(psi / 2);
        eulerYXZ(0.05f * (float) Math.sin(t * 0.9) + tiltX, 0f, 0.05f * (float) Math.cos(t * 0.7) + tiltY, qwob);
        qmul(qy, QALIGN, qtmp);
        qmul(qwob, qtmp, swq);
        normalize(swq);
        return swq;
    }
    /** Accelerometer sample (m/s2, device axes). The slow average is the holding pose, so only changes tilt the cube. */
    void onAccel(float ax, float ay) {
        if (!avgInit) { avgAx = ax; avgAy = ay; avgInit = true; }
        avgAx += (ax - avgAx) * 0.02f; avgAy += (ay - avgAy) * 0.02f;
        tgtY = Math.max(-0.6f, Math.min(0.6f, (avgAx - ax) / 9.81f * 1.1f));
        tgtX = Math.max(-0.45f, Math.min(0.45f, (ay - avgAy) / 9.81f * 0.9f));
    }
    void clearTilt() { tgtX = tgtY = 0f; avgInit = false; }
    private void setOrientation(float[] qq) { q[0] = qq[0]; q[1] = qq[1]; q[2] = qq[2]; q[3] = qq[3]; normalize(q); }
    private static void normalize(float[] a) {
        float n = (float) Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2] + a[3] * a[3]);
        if (n < 1e-6f) { a[0] = a[1] = a[2] = 0; a[3] = 1; return; }
        a[0] /= n; a[1] /= n; a[2] /= n; a[3] /= n;
    }
    private static void qmul(float[] a, float[] b, float[] o) {
        float ax = a[0], ay = a[1], az = a[2], aw = a[3], bx = b[0], by = b[1], bz = b[2], bw = b[3];
        o[0] = aw * bx + ax * bw + ay * bz - az * by;
        o[1] = aw * by - ax * bz + ay * bw + az * bx;
        o[2] = aw * bz + ax * by - ay * bx + az * bw;
        o[3] = aw * bw - ax * bx - ay * by - az * bz;
    }
    private final float[] qa = new float[4], qb = new float[4], qt = new float[4];
    /** Rotate about the SCREEN Y axis by ay and the screen X axis by ax (pre-multiplied, like a trackball). */
    private void spin(float ay, float ax) {
        float sy = (float) Math.sin(ay / 2), cyy = (float) Math.cos(ay / 2), sx = (float) Math.sin(ax / 2), cxx = (float) Math.cos(ax / 2);
        qa[0] = 0; qa[1] = sy; qa[2] = 0; qa[3] = cyy;
        qb[0] = sx; qb[1] = 0; qb[2] = 0; qb[3] = cxx;
        qmul(qa, q, qt); qmul(qb, qt, q); normalize(q);
    }
    private static void slerp(float[] a, float[] b, float t, float[] o) {
        float dot = a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3];
        float bx = b[0], by = b[1], bz = b[2], bw = b[3];
        if (dot < 0) { dot = -dot; bx = -bx; by = -by; bz = -bz; bw = -bw; }
        float k0, k1;
        if (dot > 0.9995f) { k0 = 1 - t; k1 = t; }
        else {
            float th = (float) Math.acos(dot), sn = (float) Math.sin(th);
            k0 = (float) Math.sin((1 - t) * th) / sn; k1 = (float) Math.sin(t * th) / sn;
        }
        o[0] = a[0] * k0 + bx * k1; o[1] = a[1] * k0 + by * k1; o[2] = a[2] * k0 + bz * k1; o[3] = a[3] * k0 + bw * k1;
        normalize(o);
    }
    private static void qToM(float[] qq, float[] m) {
        float x = qq[0], y = qq[1], z = qq[2], w = qq[3];
        m[0] = 1 - 2 * (y * y + z * z); m[1] = 2 * (x * y - z * w); m[2] = 2 * (x * z + y * w);
        m[3] = 2 * (x * y + z * w); m[4] = 1 - 2 * (x * x + z * z); m[5] = 2 * (y * z - x * w);
        m[6] = 2 * (x * z - y * w); m[7] = 2 * (y * z + x * w); m[8] = 1 - 2 * (x * x + y * y);
    }
    private static float smooth(float x) { x = Math.max(0f, Math.min(1f, x)); return x * x * (3f - 2f * x); }

    private void updateOrientation(long now, float dt) {
        float secs = (now - stSince) / 1000f;
        double t = (now - phase0) / 1000.0;
        float lp = Math.min(1f, dt * 6f);
        tiltX += (tgtX - tiltX) * lp; tiltY += (tgtY - tiltY) * lp;
        switch (st) {
            case ST_SWAY:
                setOrientation(swayQuat(t));
                break;
            case ST_TWEEN: {
                float k = smooth(secs / 0.9f);
                slerp(tweenFrom, tweenTo, k, qt);
                setOrientation(qt);
                if (secs >= 0.9f) {
                    if (returning) { st = ST_SWAY; returning = false; }      // back to the slow spin
                    else { st = ST_HOLD; stSince = now; }
                }
                break;
            }
            case ST_TUMBLE: {
                float k = Math.min(1f, secs / TUMBLE_SECS), inv = 1f - k, e = 1f - inv * inv * inv;
                slerp(tweenFrom, tweenTo, e, qt);
                float a = TAU * tumbleN * e, sa = (float) Math.sin(a / 2), ca = (float) Math.cos(a / 2);
                qa[0] = tumbleAxis[0] * sa; qa[1] = tumbleAxis[1] * sa; qa[2] = tumbleAxis[2] * sa; qa[3] = ca;
                qmul(qa, qt, qb);
                setOrientation(qb);
                if (k >= 1f) { st = ST_HOLD; stSince = now; }
                break;
            }
            case ST_HOLD:
                if (secs > (focusFace >= 0 ? HOLD_FOCUS_SECS : HOLD_COAST_SECS)) startReturn(now);
                break;
            case ST_COAST: {
                spin(velX * dt, velY * dt);
                float damp = (float) Math.exp(-2.2f * dt);
                velX *= damp; velY *= damp;
                if (Math.abs(velX) < 0.05f && Math.abs(velY) < 0.05f) { st = ST_HOLD; stSince = now; }
                break;
            }
            default: break;
        }
        if (st == ST_TWEEN && returning) {
            // the sway pose keeps moving: aim at where it will be
            float[] sq = swayQuat(t + Math.max(0f, 0.9f - secs));      // aim where the spin will be when the glide ends
            tweenTo[0] = sq[0]; tweenTo[1] = sq[1]; tweenTo[2] = sq[2]; tweenTo[3] = sq[3];
        } else if (st != ST_TWEEN) returning = false;
        // the landed face fades in as the die comes to rest, and fades out when the spin resumes
        float tgt = (st == ST_HOLD && focusFace >= 0) ? 1f : (st == ST_TUMBLE && secs > 1.15f) ? 1f : 0f;
        focus += (tgt - focus) * Math.min(1f, dt * (tgt > focus ? 5f : 4f));
        if (focus < 0.01f && tgt == 0f && st != ST_TUMBLE) { focus = 0f; if (st != ST_HOLD) focusFace = -1; }
    }
    private boolean returning = false;
    // dice-roll + focus
    private final java.util.Random rnd = new java.util.Random();
    private final float[] tumbleAxis = new float[3];
    private int tumbleN = 2, focusFace = -1;
    private float focus = 0f, focusCx, focusCy;
    private float hitCx, hitCy, hitR;
    private static final float TUMBLE_SECS = 1.8f, HOLD_FOCUS_SECS = 10f, HOLD_COAST_SECS = 3f;

    // =================================================================================================
    // Projection
    // =================================================================================================
    /** W-space (x right, y down, z toward viewer, watch units) -> screen; stores prx, pry, prs (scale), prz (camera z). */
    private void proj(float x, float y, float z) {
        float ox = (x - C) * WS, oy = (C - y) * WS, oz = z * WS + WZ;
        cubeProj(ox, oy, oz);
    }
    /** Cube-space point -> screen. */
    private void cubeProj(float ox, float oy, float oz) {
        float X = RA[0] * ox + RA[1] * oy + RA[2] * oz, Y = RA[3] * ox + RA[4] * oy + RA[5] * oz, Z = RA[6] * ox + RA[7] * oy + RA[8] * oz;
        float s = focal / (CAMD - Z);
        prx = cx + s * X; pry = cy - s * Y; prs = s; prz = Z;
    }
    /** Affine matrix for a flat part centred at (px,py,pz) rotated by th (in the y-down plane frame). */
    private void affine(float px, float py, float pz, float th) {
        float a = (float) Math.cos(th), b = (float) Math.sin(th);
        float ox = (px - C) * WS, oy = (C - py) * WS, oz = pz * WS + WZ;
        float ux = a * WS, uy = -b * WS, vx = -b * WS, vy = -a * WS;
        float Ox = RA[0] * ox + RA[1] * oy + RA[2] * oz, Oy = RA[3] * ox + RA[4] * oy + RA[5] * oz, Oz = RA[6] * ox + RA[7] * oy + RA[8] * oz;
        float Ux = RA[0] * ux + RA[1] * uy, Uy = RA[3] * ux + RA[4] * uy, Vx = RA[0] * vx + RA[1] * vy, Vy = RA[3] * vx + RA[4] * vy;
        float s = focal / (CAMD - Oz);
        mv[0] = s * Ux; mv[1] = s * Vx; mv[2] = cx + s * Ox;
        mv[3] = -s * Uy; mv[4] = -s * Vy; mv[5] = cy - s * Oy;
        mv[6] = 0; mv[7] = 0; mv[8] = 1;
        mat.setValues(mv);
        prs = s;
    }

    private static int shade(int c, float f) {
        int r = Math.min(255, (int) (((c >> 16) & 255) * f)), g = Math.min(255, (int) (((c >> 8) & 255) * f)), b = Math.min(255, (int) ((c & 255) * f));
        return (c & 0xFF000000) | (r << 16) | (g << 8) | b;
    }
    private static int al(int c, int a) { return (Math.max(0, Math.min(255, a)) << 24) | (c & 0xFFFFFF); }

    // =================================================================================================
    // Geometry builders
    // =================================================================================================
    private static void sectorHoles(Path pa, int S, float hub, float hole, float sw) {
        for (int i = 0; i < S; i++) {
            float a0 = i * TAU / S + sw / hole, a1 = (i + 1) * TAU / S - sw / hole;
            for (int t = 0; t <= 6; t++) {
                float a = a0 + (a1 - a0) * t / 6f;
                float x = hole * (float) Math.cos(a), y = hole * (float) Math.sin(a);
                if (t == 0) pa.moveTo(x, y); else pa.lineTo(x, y);
            }
            for (int t = 6; t >= 0; t--) {
                float a = a0 + (a1 - a0) * t / 6f;
                pa.lineTo(hub * (float) Math.cos(a), hub * (float) Math.sin(a));
            }
            pa.close();
        }
    }
    private static Path gearPath(int N, int spokes, float hub, float holeF, float sw) {
        float r = M * N / 2f, ra = r + M, rr = r - 1.25f * M, p = TAU / N;
        float[] off = {-0.26f, -0.13f, 0.13f, 0.26f}, rad = {rr, ra, ra, rr};
        Path pa = new Path();
        pa.setFillType(Path.FillType.EVEN_ODD);
        boolean first = true;
        for (int i = 0; i < N; i++) for (int k = 0; k < 4; k++) {
            float an = i * p + off[k] * p, x = rad[k] * (float) Math.cos(an), y = rad[k] * (float) Math.sin(an);
            if (first) { pa.moveTo(x, y); first = false; } else pa.lineTo(x, y);
        }
        pa.close();
        if (spokes > 0) sectorHoles(pa, spokes, hub, rr * holeF, sw);
        return pa;
    }
    private static Path spiral(float r0, float r1, int turns, int perTurn) {
        Path pa = new Path();
        int n = turns * perTurn;
        for (int i = 0; i <= n; i++) {
            float a = (float) i / n * turns * TAU, r = r0 + (r1 - r0) * i / n;
            float x = r * (float) Math.cos(a), y = r * (float) Math.sin(a);
            if (i == 0) pa.moveTo(x, y); else pa.lineTo(x, y);
        }
        return pa;
    }
    private void buildPaths() {
        pBarrel = gearPath(64, 3, 17 * K, 0.92f, 3.5f * K);
        pCentre = gearPath(60, 6, 10 * K, 0.80f, 3f * K);
        pThird8 = gearPath(8, 0, 0, 0, 0);
        pThird64 = gearPath(64, 6, 10 * K, 0.82f, 3f * K);
        pFourth8 = pThird8;
        pFourth60 = gearPath(60, 5, 10 * K, 0.82f, 3f * K);
        pEsc8 = pThird8;
        pStar = new Path();
        pStar.setFillType(Path.FillType.EVEN_ODD);
        float p = TAU / 32f;
        for (int j = 0; j < 32; j++) {
            float a0 = j * p;
            float ax = 17 * K * (float) Math.cos(a0), ay = 17 * K * (float) Math.sin(a0);
            if (j == 0) pStar.moveTo(ax, ay); else pStar.lineTo(ax, ay);
            pStar.lineTo(24 * K * (float) Math.cos(a0 + p * 0.32f), 24 * K * (float) Math.sin(a0 + p * 0.32f));
        }
        pStar.close();
        sectorHoles(pStar, 4, 5 * K, 13 * K, 2.2f * K);
        pBarrelSpring = spiral(10 * K, 43 * K, 7, 26);
        pHair = spiral(5 * K, 26 * K, 8, 22);
    }

    // =================================================================================================
    // Parts (everything inside the cube)
    // =================================================================================================
    private void add(float z, Drawer fn) { parts.add(new Part(z, fn)); }

    private void buildParts() {
        // ---- case, glass, plate (back to front) -------------------------------------------------
        add(-15f, c -> caseRing(c, -15f));
        add(-14f, c -> disc(c, C, C, -14f, 300f, al(0xFFBCD8F0, 0x30), 0));
        add(-6f, c -> disc(c, C, C, -6f, 296f, al(0xFFBCD8F0, 0x1C), al(BEZEL, 0x40)));
        add(18f, this::caseBand);

        // ---- movement: gears ---------------------------------------------------------------------
        add(0f, c -> { gear(c, pBarrel, BAR[0], BAR[1], 0f, thBar, 3f, BARREL, 1.0f);
                       spiralPart(c, pBarrelSpring, BAR[0], BAR[1], 0.2f, thBar, STEEL, 1.3f); });
        add(0.1f, c -> shaft(c, BAR[0], BAR[1], -3f, 7f, 2.2f));
        add(0.2f, c -> gear(c, pCentre, A[0], A[1], 0f, thA, 3f, CENTER, 1.0f));
        add(0.3f, c -> shaft(c, A[0], A[1], -3f, 12f, 2.4f));
        add(0f, c -> gear(c, pThird8, T[0], T[1], 0f, thT, 4f, THIRD, 1.0f));
        add(7f, c -> gear(c, pThird64, T[0], T[1], 7f, thT, 3f, THIRD, 1.0f));
        add(7.1f, c -> shaft(c, T[0], T[1], -3f, 11f, 1.3f));
        add(7f, c -> gear(c, pFourth8, F[0], F[1], 7f, thF, 4f, FOURTH, 1.0f));
        add(14f, c -> gear(c, pFourth60, F[0], F[1], 14f, thF, 3f, FOURTH, 1.0f));
        add(14.1f, c -> shaft(c, F[0], F[1], 3f, 18f, 1.3f));
        add(14f, c -> gear(c, pEsc8, E[0], E[1], 14f, thE, 4f, ESCAPE, 1.0f));
        add(21f, c -> gear(c, pStar, E[0], E[1], 21f, thE, 2.4f, ESCAPE, 1.0f));
        add(21.1f, c -> shaft(c, E[0], E[1], 11f, 25f, 1.2f));

        // ---- pallet fork, balance ---------------------------------------------------------------
        add(21f, this::forkPart);
        add(21f, this::rollerPart);
        add(25f, this::balanceRim);
        add(27.5f, this::hairspring);

        // ---- bridges + screws + jewels -----------------------------------------------------------
        add(10f, c -> bridge(c, BAR[0], BAR[1], A[0], A[1], 16 * K, 10f));
        add(20f, c -> bridge(c, T[0], T[1], F[0], F[1], 14 * K, 20f));
        add(10.6f, c -> { screw(c, BAR[0], BAR[1], 10.5f); jewel(c, BAR[0], BAR[1], 8.5f); });
        add(13.6f, c -> screw(c, A[0], A[1], 13.5f));
        add(12.6f, c -> { screw(c, T[0], T[1], 12.5f); jewel(c, T[0], T[1], 11.5f); });
        add(21.6f, c -> { screw(c, F[0], F[1], 21.5f); jewel(c, F[0], F[1], 19f); });
        add(26.6f, c -> { jewel(c, E[0], E[1], 26.5f); jewel(c, P[0], P[1], 26f); });
        add(31.6f, c -> jewel(c, B[0], B[1], 31.5f));

        // ---- dial print, hands -------------------------------------------------------------------
        add(31f, this::dialPrint);
        add(33f, this::secondHand);
        add(36f, c -> hand(c, 165f, 16f, 28f, 36f, hourA));
        add(39f, c -> hand(c, 272f, 11f, 32f, 39f, minA));
        add(41.5f, c -> { disc(c, A[0], A[1], 41.5f, 7f, GOLD, al(GOLDB, 0xC0)); });
        add(44.5f, c -> jewel(c, A[0], A[1], 44.5f));

        // ---- bezel + dome -----------------------------------------------------------------------
        add(50f, c -> caseRing(c, 50f));
        add(60f, this::dome);

        Collections.sort(parts, new Comparator<Part>() { @Override public int compare(Part a, Part b) { return Float.compare(a.z, b.z); } });
    }

    // ---- primitives ----------------------------------------------------------------------------
    private void gear(Canvas c, Path local, float px, float py, float zc, float th, float thick, int color, float tone) {
        int n = eco ? 2 : Math.max(2, Math.round(thick / 1.3f) + 1);
        float dummy;
        for (int j = 0; j < n; j++) {
            int k = frontView ? j : n - 1 - j;                // k=0 deepest z ... k=n-1 shallowest-to-camera depends on view
            float zz = zc - thick / 2f + thick * k / (n - 1);
            boolean nearest = (j == n - 1);
            affine(px, py, zz, th);
            c.save();
            c.concat(mat);
            float f = shadeAll * (0.50f + 0.50f * (float) j / (n - 1));
            fillP.setStyle(Paint.Style.FILL);
            fillP.setShader(null);
            fillP.setColor(shade(color, f * tone));
            c.drawPath(local, fillP);
            if (nearest) {
                strokeP.setShader(null);
                strokeP.setStrokeWidth(Math.max(0.8f, 1.1f / Math.max(0.2f, prs)));
                strokeP.setColor(al(0xFFFFFFFF, 0x70));
                c.drawPath(local, strokeP);
            }
            c.restore();
        }
    }
    private void spiralPart(Canvas c, Path local, float px, float py, float zz, float th, int color, float width) {
        affine(px, py, zz + 0.6f, th);
        c.save(); c.concat(mat);
        strokeP.setShader(null);
        strokeP.setStrokeWidth(Math.max(width, 1.3f / Math.max(0.2f, prs)));
        strokeP.setColor(shade(color, 0.9f * shadeAll + 0.1f));
        c.drawPath(local, strokeP);
        c.restore();
    }
    private void shaft(Canvas c, float x, float y, float z0, float z1, float r) {
        proj(x, y, z0); float x0 = prx, y0 = pry, s0 = prs;
        proj(x, y, z1); float x1 = prx, y1 = pry, s1 = prs;
        strokeP.setShader(null);
        strokeP.setStrokeCap(Paint.Cap.ROUND);
        strokeP.setStrokeWidth(Math.max(1.2f, 2f * r * (s0 + s1) / 2f * 0.9f));
        strokeP.setColor(shade(STEEL, 0.55f + 0.35f * shadeAll));
        c.drawLine(x0, y0, x1, y1, strokeP);
        strokeP.setStrokeCap(Paint.Cap.BUTT);
        fillP.setShader(null); fillP.setStyle(Paint.Style.FILL);
        float rEnd = Math.max(1.1f, r * s1);
        fillP.setColor(shade(STEEL, 0.95f)); c.drawCircle(frontView ? x1 : x0, frontView ? y1 : y0, frontView ? rEnd : Math.max(1.1f, r * s0), fillP);
        fillP.setColor(al(0xFFFFFFFF, 0xA0)); c.drawCircle(frontView ? x1 : x0, frontView ? y1 : y0, Math.max(0.6f, rEnd * 0.4f), fillP);
    }
    private void jewel(Canvas c, float x, float y, float z) {
        affine(x, y, z, 0f);
        c.save(); c.concat(mat);
        fillP.setShader(null); fillP.setStyle(Paint.Style.FILL);
        fillP.setColor(shade(RUBY, 0.7f + 0.3f * shadeAll));
        c.drawCircle(0, 0, 3.2f, fillP);
        fillP.setColor(al(0xFFFFFFFF, 0x90));
        c.drawCircle(-0.9f, -0.9f, 1.0f, fillP);
        strokeP.setShader(null); strokeP.setStrokeWidth(Math.max(1.0f, 1.0f / Math.max(0.2f, prs)));
        strokeP.setColor(shade(GOLD, 0.8f + 0.2f * shadeAll));
        c.drawCircle(0, 0, 3.9f, strokeP);
        c.restore();
    }
    private void screw(Canvas c, float x, float y, float z) {
        affine(x, y, z, 0f);
        c.save(); c.concat(mat);
        fillP.setShader(null); fillP.setStyle(Paint.Style.FILL);
        fillP.setColor(shade(STEEL, 0.6f + 0.4f * shadeAll));
        c.drawCircle(0, 0, 3.2f, fillP);
        strokeP.setShader(null); strokeP.setStrokeWidth(0.9f);
        strokeP.setColor(al(0xFF1A2530, 0xB0));
        c.drawLine(-2.2f, 0, 2.2f, 0, strokeP);
        c.restore();
    }
    private void disc(Canvas c, float x, float y, float z, float r, int fill, int edge) {
        if (r > 60f) {          // big discs follow the perspective exactly
            tmp.reset();
            circle3(tmp, x, y, z, r, 56);
            if (fill != 0) { fillP.setShader(null); fillP.setStyle(Paint.Style.FILL); fillP.setColor(fill); c.drawPath(tmp, fillP); }
            if (edge != 0) { strokeP.setShader(null); strokeP.setStrokeWidth(Math.max(1f, 1.4f * prs * 2.0f)); strokeP.setColor(edge); c.drawPath(tmp, strokeP); }
        } else {
            affine(x, y, z, 0f);
            c.save(); c.concat(mat);
            if (fill != 0) { fillP.setShader(null); fillP.setStyle(Paint.Style.FILL); fillP.setColor(shade(fill, 0.75f + 0.25f * shadeAll)); c.drawCircle(0, 0, r, fillP); }
            if (edge != 0) { strokeP.setShader(null); strokeP.setStrokeWidth(0.9f); strokeP.setColor(edge); c.drawCircle(0, 0, r, strokeP); }
            c.restore();
        }
    }
    private void circle3(Path pa, float x, float y, float z, float r, int n) {
        for (int i = 0; i <= n; i++) {
            float a = TAU * i / n;
            proj(x + r * (float) Math.cos(a), y + r * (float) Math.sin(a), z);
            if (i == 0) pa.moveTo(prx, pry); else pa.lineTo(prx, pry);
        }
        pa.close();
        proj(x, y, z);      // leaves prs at the centre scale
    }
    private void bridge(Canvas c, float x0, float y0, float x1, float y1, float w, float z) {
        float dx = x1 - x0, dy = y1 - y0, len = (float) Math.sqrt(dx * dx + dy * dy), th = (float) Math.atan2(dy, dx);
        affine((x0 + x1) / 2f, (y0 + y1) / 2f, z, th);
        c.save(); c.concat(mat);
        fillP.setShader(null); fillP.setStyle(Paint.Style.FILL);
        fillP.setColor(al(0xFFCFE0F2, 0x34));
        c.drawRect(-len / 2f - w * 0.35f, -w / 2f, len / 2f + w * 0.35f, w / 2f, fillP);
        strokeP.setShader(null); strokeP.setStrokeWidth(0.9f); strokeP.setColor(al(0xFFEAF4FF, 0x80));
        c.drawRect(-len / 2f - w * 0.35f, -w / 2f, len / 2f + w * 0.35f, w / 2f, strokeP);
        c.restore();
    }

    // ---- case --------------------------------------------------------------------------------
    private void caseRing(Canvas c, float z) {
        tmp.reset();
        circle3(tmp, C, C, z, 308f, 72);
        float s = prs;
        strokeP.setShader(null); strokeP.setStyle(Paint.Style.STROKE);
        strokeP.setStrokeWidth(Math.max(2f, 22f * s * WS));
        strokeP.setColor(al(0xFFCFE6FF, 0x22));
        c.drawPath(tmp, strokeP);
        for (int k = 0; k < 2; k++) {
            tmp.reset();
            circle3(tmp, C, C, z, k == 0 ? 319f : 297f, 72);
            strokeP.setStrokeWidth(Math.max(1.2f, 3.2f * s * WS));
            strokeP.setColor(shade(BEZEL, 0.7f + 0.3f * shadeAll));
            c.drawPath(tmp, strokeP);
        }
    }
    private void caseBand(Canvas c) {
        strokeP.setShader(null); strokeP.setStyle(Paint.Style.STROKE);
        strokeP.setStrokeWidth(Math.max(1f, 1.3f * unit * WS * 2f));
        strokeP.setColor(al(0xFFCFE6FF, 0x26));
        int n = eco ? 18 : 36;
        for (int i = 0; i < n; i++) {
            float a = TAU * i / n, x = C + 316f * (float) Math.cos(a), y = C + 316f * (float) Math.sin(a);
            proj(x, y, -15f); float ax = prx, ay = pry;
            proj(x, y, 51f);
            c.drawLine(ax, ay, prx, pry, strokeP);
        }
    }
    private void dome(Canvas c) {
        tmp.reset();
        circle3(tmp, C, C, 50f, 308f, 64);
        fillP.setShader(null); fillP.setStyle(Paint.Style.FILL);
        fillP.setColor(al(0xFFFFFFFF, 0x0C));
        c.drawPath(tmp, fillP);
        // specular streak on the glass
        tmp.reset();
        float s = 0f;
        for (int i = 0; i <= 18; i++) {
            float a = (200f + 60f * i / 18f) * DEG;
            proj(C + 262f * (float) Math.cos(a), C + 262f * (float) Math.sin(a), 92f);
            if (i == 0) tmp.moveTo(prx, pry); else tmp.lineTo(prx, pry);
            s = prs;
        }
        strokeP.setShader(null); strokeP.setStyle(Paint.Style.STROKE); strokeP.setStrokeCap(Paint.Cap.ROUND);
        strokeP.setStrokeWidth(Math.max(2f, 9f * s * WS));
        strokeP.setColor(al(0xFFFFFFFF, 0x34));
        c.drawPath(tmp, strokeP);
        strokeP.setStrokeCap(Paint.Cap.BUTT);
    }

    // ---- escapement ------------------------------------------------------------------------
    private void forkPart(Canvas c) {
        affine(P[0], P[1], 21f, forkRot);
        c.save(); c.concat(mat);
        float ex = E[0] - P[0], ey = E[1] - P[1];
        float[] p1 = {ex + 23 * K * (float) Math.cos(phi + 0.62f), ey + 23 * K * (float) Math.sin(phi + 0.62f)};
        float[] p2 = {ex + 23 * K * (float) Math.cos(phi - 0.62f), ey + 23 * K * (float) Math.sin(phi - 0.62f)};
        float[] tip = {39 * K * dirx, 39 * K * diry};
        strokeP.setShader(null); strokeP.setStrokeCap(Paint.Cap.ROUND);
        strokeP.setStrokeWidth(5f);
        strokeP.setColor(shade(FORK, 0.55f + 0.45f * shadeAll));
        c.drawLine(0, 0, p1[0], p1[1], strokeP); c.drawLine(0, 0, p2[0], p2[1], strokeP); c.drawLine(0, 0, tip[0], tip[1], strokeP);
        strokeP.setStrokeWidth(2f);
        float nx = -diry, ny = dirx;
        c.drawLine(tip[0] + nx * 5, tip[1] + ny * 5, tip[0] - nx * 5, tip[1] - ny * 5, strokeP);
        strokeP.setStrokeCap(Paint.Cap.BUTT);
        fillP.setShader(null); fillP.setStyle(Paint.Style.FILL);
        fillP.setColor(shade(RUBY, 0.8f + 0.2f * shadeAll));
        c.save(); c.translate(p1[0], p1[1]); c.rotate(phi / DEG); c.drawRect(-2.5f, -2f, 2.5f, 2f, fillP); c.restore();
        c.save(); c.translate(p2[0], p2[1]); c.rotate(phi / DEG); c.drawRect(-2.5f, -2f, 2.5f, 2f, fillP); c.restore();
        fillP.setColor(shade(STEEL, 0.9f)); c.drawCircle(0, 0, 2.4f, fillP);
        c.restore();
    }
    private void rollerPart(Canvas c) {
        affine(B[0], B[1], 21f, balRot);
        c.save(); c.concat(mat);
        fillP.setShader(null); fillP.setStyle(Paint.Style.FILL);
        fillP.setColor(shade(BALANCE, 0.55f + 0.4f * shadeAll));
        c.drawCircle(0, 0, 8 * K, fillP);
        fillP.setColor(shade(RUBY, 0.9f));
        c.drawCircle(-7 * K * dirx, -7 * K * diry, 1.9f, fillP);
        c.restore();
    }
    private void balanceRim(Canvas c) {
        affine(B[0], B[1], 25f, balRot);
        c.save(); c.concat(mat);
        float rr = 38 * K;
        strokeP.setShader(null);
        strokeP.setStrokeWidth(4.8f);
        strokeP.setColor(shade(BALANCE, 0.6f + 0.4f * shadeAll));
        c.drawCircle(0, 0, rr, strokeP);
        strokeP.setStrokeWidth(3f);
        for (int k = 0; k < 4; k++) {
            float za = k * PI / 2f + 0.4f;
            c.drawLine(0, 0, rr * (float) Math.cos(za), rr * (float) Math.sin(za), strokeP);
        }
        fillP.setShader(null); fillP.setStyle(Paint.Style.FILL);
        fillP.setColor(shade(STEEL, 0.65f + 0.35f * shadeAll));
        for (int k = 0; k < 8; k++) {
            float wa = k * TAU / 8f;
            c.drawCircle(rr * (float) Math.cos(wa), rr * (float) Math.sin(wa), 2.8f, fillP);
        }
        c.restore();
    }
    private void hairspring(Canvas c) {
        if (eco) return;
        float br = 1f + 0.05f * (float) Math.sin(balRot);
        affine(B[0], B[1], 27.5f, balRot);
        c.save(); c.concat(mat); c.scale(br, br);
        strokeP.setShader(null);
        strokeP.setStrokeWidth(Math.max(0.9f, 1.2f / Math.max(0.2f, prs)));
        strokeP.setColor(al(shade(LUME, 0.7f + 0.3f * shadeAll), 0xE0));
        c.drawPath(pHair, strokeP);
        c.restore();
    }

    // ---- hands -------------------------------------------------------------------------------
    private void hand(Canvas c, float len, float w, float tail, float z, float th) {
        float cs = (float) Math.cos(th), sn = (float) Math.sin(th);
        float[][] body = {{-tail, 0}, {0, w / 2}, {len * 0.8f, w / 3}, {len, 0}, {len * 0.8f, -w / 3}, {0, -w / 2}};
        float l0 = len * 0.14f, l1 = len * 0.86f, hw = w * 0.14f;
        float[][] hole = {{l0, -hw}, {l1, -hw * 0.5f}, {l1, hw * 0.5f}, {l0, hw}};
        for (int layer = 0; layer < 2; layer++) {
            boolean near = (frontView ? layer == 1 : layer == 0);
            float zz = z + (layer == 1 ? 1.6f : 0f);
            if (frontView ? layer == 0 : layer == 1) {
                // deeper layer first (darker)
            }
            tmp.reset(); tmp.setFillType(Path.FillType.EVEN_ODD);
            for (int i = 0; i < body.length; i++) {
                proj(A[0] + body[i][0] * cs - body[i][1] * sn, A[1] + body[i][0] * sn + body[i][1] * cs, zz);
                if (i == 0) tmp.moveTo(prx, pry); else tmp.lineTo(prx, pry);
            }
            tmp.close();
            for (int i = 0; i < hole.length; i++) {
                proj(A[0] + hole[i][0] * cs - hole[i][1] * sn, A[1] + hole[i][0] * sn + hole[i][1] * cs, zz);
                if (i == 0) tmp.moveTo(prx, pry); else tmp.lineTo(prx, pry);
            }
            tmp.close();
            fillP.setShader(null); fillP.setStyle(Paint.Style.FILL);
            fillP.setColor(shade(STEEL, (near ? 1.0f : 0.5f) * (0.7f + 0.3f * shadeAll)));
            if (layer == (frontView ? 0 : 1)) fillP.setColor(shade(STEEL, 0.45f));
            c.drawPath(tmp, fillP);
            if (layer == (frontView ? 1 : 0)) {
                strokeP.setShader(null); strokeP.setStrokeWidth(Math.max(0.8f, 0.9f * prs * 2f));
                strokeP.setColor(al(0xFFFFFFFF, 0x90));
                c.drawPath(tmp, strokeP);
            }
        }
        // lume strip
        tmp.reset();
        float[][] strip = {{l0, -w * 0.1f}, {l1, -w * 0.1f}, {l1, w * 0.1f}, {l0, w * 0.1f}};
        for (int i = 0; i < 4; i++) {
            proj(A[0] + strip[i][0] * cs - strip[i][1] * sn, A[1] + strip[i][0] * sn + strip[i][1] * cs, z + 1.7f);
            if (i == 0) tmp.moveTo(prx, pry); else tmp.lineTo(prx, pry);
        }
        tmp.close();
        fillP.setColor(al(LUME, 0xE0)); c.drawPath(tmp, fillP);
    }
    private void secondHand(Canvas c) {
        affine(F[0], F[1], 33f, secA);
        c.save(); c.concat(mat);
        strokeP.setShader(null); strokeP.setStrokeCap(Paint.Cap.ROUND);
        strokeP.setStrokeWidth(1.9f);
        strokeP.setColor(al(RUBY, 0xF0));
        c.drawLine(-10f, 0f, 40f, 0f, strokeP);
        strokeP.setStrokeCap(Paint.Cap.BUTT);
        fillP.setShader(null); fillP.setStyle(Paint.Style.FILL);
        fillP.setColor(RUBY);
        c.drawCircle(-10f, 0f, 3.2f, fillP);
        fillP.setColor(GOLD); c.drawCircle(0, 0, 2.6f, fillP);
        c.restore();
    }

    // ---- dial printing ----------------------------------------------------------------------------
    private static final String[] DAYS = {"SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"};
    private void dialPrint(Canvas c) {
        final float z = 31f;
        // translucent deep-blue tint over the movement
        tmp.reset(); circle3(tmp, C, C, z, 240f, 64);
        fillP.setShader(null); fillP.setStyle(Paint.Style.FILL); fillP.setColor(al(0xFF0A3296, 0x2C));
        c.drawPath(tmp, fillP);
        // track ring (with an open window for the balance wheel, like an open-heart watch)
        tmp.reset(); tmp.setFillType(Path.FillType.EVEN_ODD);
        circle3(tmp, C, C, z, 300f, 72);
        circle3(tmp, C, C, z, 240f, 72);
        winPath.reset();
        circle3(winPath, B[0], B[1], z, 50f, 36);
        float sBal = prs;
        c.save();
        c.clipOutPath(winPath);
        fillP.setColor(al(DIAL, 0xEE));
        c.drawPath(tmp, fillP);
        c.restore();
        strokeP.setShader(null); strokeP.setStyle(Paint.Style.STROKE);
        strokeP.setStrokeWidth(Math.max(1.2f, 2.4f * sBal * WS));
        strokeP.setColor(al(GOLDB, 0xC0));
        c.drawPath(winPath, strokeP);
        tmp.reset(); circle3(tmp, C, C, z, 240f, 64);
        strokeP.setStrokeWidth(Math.max(1f, 1.2f * prs * WS * 2f)); strokeP.setColor(al(GOLDB, 0x8C));
        c.drawPath(tmp, strokeP);
        // minute track: 60 gold indices
        strokeP.setStrokeCap(Paint.Cap.BUTT);
        for (int i = 0; i < 60; i++) {
            float a = i * TAU / 60f - PI / 2f, ca = (float) Math.cos(a), sa = (float) Math.sin(a);
            boolean big = i % 5 == 0;
            proj(C + (big ? 252f : 280f) * ca, C + (big ? 252f : 280f) * sa, z); float x0 = prx, y0 = pry;
            proj(C + 294f * ca, C + 294f * sa, z);
            strokeP.setStrokeWidth(Math.max(big ? 2.4f : 1.2f, (big ? 5f : 1.6f) * prs * WS));
            strokeP.setColor(al(GOLDB, 0xF0));
            c.drawLine(x0, y0, prx, pry, strokeP);
        }
        // numerals, signature, date window
        c.save();
        affine(C, C - 214f, z, 0f); c.save(); c.concat(mat); drawNumeral(c, "12", 0, 0); c.restore();
        affine(C, C + 214f, z, 0f); c.save(); c.concat(mat); drawNumeral(c, "6", 0, 0); c.restore();
        affine(C - 214f, C, z, 0f); c.save(); c.concat(mat); drawNumeral(c, "9", 0, 0); c.restore();
        affine(C, C - 164f, z, 0f); c.save(); c.concat(mat);
        textP.setShader(null); textP.setTextAlign(Paint.Align.CENTER); textP.setTypeface(Typeface.create("serif", Typeface.ITALIC));
        textP.setTextSize(30f); textP.setColor(GOLDB);
        c.drawText("nahiaR's", 0, 10f, textP);
        c.restore();
        affine(C, C - 138f, z, 0f); c.save(); c.concat(mat);
        textP.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); textP.setTextSize(10f); textP.setColor(DIM);
        c.drawText("SKELETON · 14,400 A/h", 0, 3.5f, textP);
        c.restore();
        // date window at 3 o'clock
        affine(C + 232f, C, z, 0f); c.save(); c.concat(mat);
        fillP.setColor(al(0xFF080D13, 0xDA)); c.drawRect(-30f, -16f, 30f, 16f, fillP);
        strokeP.setStrokeWidth(1.4f); strokeP.setColor(GOLD); c.drawRect(-30f, -16f, 30f, 16f, strokeP);
        textP.setTextAlign(Paint.Align.CENTER); textP.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        textP.setTextSize(15f); textP.setColor(INK); c.drawText(DAYS[cal.get(Calendar.DAY_OF_WEEK) - 1], -13f, 5.5f, textP);
        textP.setTextSize(17f); textP.setColor(GOLD); c.drawText(String.valueOf(cal.get(Calendar.DAY_OF_MONTH)), 16f, 6f, textP);
        c.restore();
        // small-seconds subdial ticks
        affine(F[0], F[1], z, 0f); c.save(); c.concat(mat);
        strokeP.setStrokeCap(Paint.Cap.BUTT);
        for (int i = 0; i < 60; i++) {
            float a = i * TAU / 60f - PI / 2f, ca = (float) Math.cos(a), sa = (float) Math.sin(a);
            boolean five = i % 5 == 0;
            strokeP.setStrokeWidth(five ? 2.4f : 1f); strokeP.setColor(al(GOLDB, five ? 0xF0 : 0xB0));
            float r0 = five ? 38f : 43f;
            c.drawLine(r0 * ca, r0 * sa, 48f * ca, 48f * sa, strokeP);
        }
        c.restore();
        c.restore();
    }
    private void drawNumeral(Canvas c, String s, float x, float y) {
        textP.setShader(null); textP.setTextAlign(Paint.Align.CENTER);
        textP.setTypeface(Typeface.create("serif", Typeface.BOLD)); textP.setTextSize(38f); textP.setColor(GOLDB);
        c.drawText(s, x, y + 13f, textP);
    }


    // =================================================================================================
    // Full-cube engine: 3-axis gyroscope behind the watch, shafts in five directions, gear pairs on the walls
    // =================================================================================================
    private static final float[] G = {0f, 0f, -190f};
    private static final float GSC = 2.4f, GD = 150f;
    private static final int METAL_GOLD = 0xFFC9A24B, METAL_TI = 0xFF93A5B8, METAL_DARK = 0xFF46515E, GLOW = 0xFF63E5FF;
    private static final float[][] WO = {{0, 0, -340}, {-340, 0, 0}, {340, 0, 0}, {0, 340, 0}, {0, -340, 0}, {0, 0, 340}};
    private static final float[][] WUX = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}, {1, 0, 0}, {0, 0, 1}, {0, 1, 0}};
    private static final float[][] WUY = {{0, 1, 0}, {0, 0, 1}, {0, 1, 0}, {0, 0, 1}, {1, 0, 0}, {1, 0, 0}};
    private static final float[] WANG = {0.62f, 0.30f, 2.55f, -0.45f, 2.35f, 1.1f};
    private static final float[] WRATE = {2f, -1.5f, 1.5f, -2f, 1f, -1f};
    private static final int WN1 = 48, WN2 = 20;
    private Path pWallBig, pWallSmall, pRotor;
    private final float[] wTh1 = new float[6], wTh2 = new float[6];
    private float gyA1, gyA2, gyA3, gyRot;
    private final float[] m1 = new float[9], m2 = new float[9], m3 = new float[9], mt = new float[9], mr = new float[9];
    private final float[] entDepth = new float[16];
    private final int[] entKind = new int[16];
    private final float[] ptA = new float[3];
    private float ga = 1f;                                 // fade of the current engine part (walls in front of the dial fade out)

    private void buildEngine() {
        pWallBig = gearPath(WN1, 5, 14 * K, 0.84f, 3f * K);
        pWallSmall = gearPath(WN2, 4, 7 * K, 0.78f, 2.4f * K);
        pRotor = gearPath(18, 6, 5 * K, 0.78f, 2f * K);
    }
    private static void rotM(float[] m, int axis, float a) {
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        java.util.Arrays.fill(m, 0f); m[0] = m[4] = m[8] = 1f;
        if (axis == 0) { m[4] = c; m[5] = -s; m[7] = s; m[8] = c; }
        else if (axis == 1) { m[0] = c; m[2] = s; m[6] = -s; m[8] = c; }
        else { m[0] = c; m[1] = -s; m[3] = s; m[4] = c; }
    }
    private static void mulM(float[] a, float[] b, float[] o) {
        for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++)
            o[i * 3 + j] = a[i * 3] * b[j] + a[i * 3 + 1] * b[3 + j] + a[i * 3 + 2] * b[6 + j];
    }
    private float camZ(float x, float y, float z) { return RA[6] * x + RA[7] * y + RA[8] * z; }

    /** Flat gear lying in the plane (ux, uy) of cube space, rotated by th about its normal, with a few depth slices. */
    private void gearPlane(Canvas c, Path local, float scale, float ox, float oy, float oz, float[] ux, float[] uy, float th,
                           float thick, int color, boolean rim) {
        float nx = ux[1] * uy[2] - ux[2] * uy[1], ny = ux[2] * uy[0] - ux[0] * uy[2], nz = ux[0] * uy[1] - ux[1] * uy[0];
        float ncz = RA[6] * nx + RA[7] * ny + RA[8] * nz;
        float ncx = RA[0] * nx + RA[1] * ny + RA[2] * nz, ncy = RA[3] * nx + RA[4] * ny + RA[5] * nz;
        float light = 0.58f + 0.42f * Math.min(1f, Math.abs(ncx * -0.30f + ncy * 0.42f + ncz * 0.85f));
        float ca = (float) Math.cos(th), sa = (float) Math.sin(th);
        float ax = ux[0] * ca + uy[0] * sa, ay = ux[1] * ca + uy[1] * sa, az = ux[2] * ca + uy[2] * sa;
        float bx = -ux[0] * sa + uy[0] * ca, by = -ux[1] * sa + uy[1] * ca, bz = -ux[2] * sa + uy[2] * ca;
        float Ux = (RA[0] * ax + RA[1] * ay + RA[2] * az) * scale, Uy = (RA[3] * ax + RA[4] * ay + RA[5] * az) * scale;
        float Vx = (RA[0] * bx + RA[1] * by + RA[2] * bz) * scale, Vy = (RA[3] * bx + RA[4] * by + RA[5] * bz) * scale;
        int n = eco ? 2 : 4;
        for (int j = 0; j < n; j++) {
            float off = (-0.5f + (float) j / (n - 1)) * thick;
            if (ncz < 0f) off = -off;                       // nearest slice is drawn last
            float X = ox + nx * off, Y = oy + ny * off, Z = oz + nz * off;
            float Ox = RA[0] * X + RA[1] * Y + RA[2] * Z, Oy = RA[3] * X + RA[4] * Y + RA[5] * Z, Oz = RA[6] * X + RA[7] * Y + RA[8] * Z;
            float sc = focal / (CAMD - Oz);
            mv[0] = sc * Ux; mv[1] = sc * Vx; mv[2] = cx + sc * Ox;
            mv[3] = -sc * Uy; mv[4] = -sc * Vy; mv[5] = cy - sc * Oy;
            mv[6] = 0; mv[7] = 0; mv[8] = 1;
            mat.setValues(mv);
            c.save(); c.concat(mat);
            fillP.setShader(null); fillP.setStyle(Paint.Style.FILL);
            fillP.setColor(al(shade(color, light * (0.55f + 0.45f * (float) j / (n - 1))), (int) (255 * ga)));
            c.drawPath(local, fillP);
            if (j == n - 1 && rim) {
                strokeP.setShader(null); strokeP.setStyle(Paint.Style.STROKE);
                strokeP.setStrokeWidth(Math.max(0.6f, 1.2f / Math.max(0.05f, sc * scale)));
                strokeP.setColor(al(GLOW, (int) (0x88 * ga)));
                c.drawPath(local, strokeP);
            }
            c.restore();
        }
    }
    private void shaft3(Canvas c, float x0, float y0, float z0, float x1, float y1, float z1, float r, int color) {
        cubeProj(x0, y0, z0); float ax = prx, ay = pry, s0 = prs;
        cubeProj(x1, y1, z1); float bx = prx, by = pry, s1 = prs;
        float w = Math.max(1.4f, 2f * r * (s0 + s1) / 2f);
        strokeP.setShader(null); strokeP.setStyle(Paint.Style.STROKE); strokeP.setStrokeCap(Paint.Cap.ROUND);
        strokeP.setStrokeWidth(w); strokeP.setColor(al(shade(color, 0.55f + 0.35f * shadeAll), (int) (255 * ga)));
        c.drawLine(ax, ay, bx, by, strokeP);
        strokeP.setStrokeWidth(Math.max(0.8f, w * 0.28f)); strokeP.setColor(al(0xFFFFFFFF, (int) (0x70 * ga)));
        c.drawLine(ax, ay, bx, by, strokeP);
        strokeP.setStrokeCap(Paint.Cap.BUTT);
    }
    private void wallDraw(Canvas c, int w) {
        float[] o = WO[w], ux = WUX[w], uy = WUY[w];
        int big = (w & 1) == 0 ? METAL_GOLD : METAL_TI, small = (w & 1) == 0 ? METAL_TI : METAL_GOLD;
        float gx = o[0] + GD * ((float) Math.cos(WANG[w]) * ux[0] + (float) Math.sin(WANG[w]) * uy[0]);
        float gy = o[1] + GD * ((float) Math.cos(WANG[w]) * ux[1] + (float) Math.sin(WANG[w]) * uy[1]);
        float gz = o[2] + GD * ((float) Math.cos(WANG[w]) * ux[2] + (float) Math.sin(WANG[w]) * uy[2]);
        gearPlane(c, pWallBig, GSC, o[0], o[1], o[2], ux, uy, wTh1[w], 9f, big, true);
        gearPlane(c, pWallSmall, GSC, gx, gy, gz, ux, uy, wTh2[w], 9f, small, true);
        // boss + pivot caps
        cubeProj(o[0], o[1], o[2]);
        fillP.setShader(null); fillP.setStyle(Paint.Style.FILL);
        fillP.setColor(al(shade(METAL_DARK, 0.7f + 0.3f * shadeAll), (int) (255 * ga))); c.drawCircle(prx, pry, 13f * prs, fillP);
        fillP.setColor(al(GLOW, (int) (0xB0 * ga))); c.drawCircle(prx, pry, 4.5f * prs, fillP);
        cubeProj(gx, gy, gz);
        fillP.setColor(al(shade(METAL_DARK, 0.7f + 0.3f * shadeAll), (int) (255 * ga))); c.drawCircle(prx, pry, 8f * prs, fillP);
    }
    private void ring3(Canvas c, float[] e1, float[] e2, float r, float width, int color, int alpha) {
        tmp.reset();
        int n = eco ? 28 : 44;
        for (int i = 0; i <= n; i++) {
            float a = TAU * i / n, ca = (float) Math.cos(a) * r, sa = (float) Math.sin(a) * r;
            cubeProj(G[0] + e1[0] * ca + e2[0] * sa, G[1] + e1[1] * ca + e2[1] * sa, G[2] + e1[2] * ca + e2[2] * sa);
            if (i == 0) tmp.moveTo(prx, pry); else tmp.lineTo(prx, pry);
        }
        cubeProj(G[0], G[1], G[2]);
        float s = prs;
        strokeP.setShader(null); strokeP.setStyle(Paint.Style.STROKE);
        strokeP.setStrokeWidth(Math.max(2.5f, width * s * 2.4f)); strokeP.setColor(al(GLOW, alpha / 4));
        c.drawPath(tmp, strokeP);
        strokeP.setStrokeWidth(Math.max(1.6f, width * s)); strokeP.setColor(al(color, alpha));
        c.drawPath(tmp, strokeP);
        strokeP.setStrokeWidth(Math.max(0.7f, width * s * 0.25f)); strokeP.setColor(al(0xFFFFFFFF, alpha / 2));
        c.drawPath(tmp, strokeP);
    }
    private final float[] ax1 = new float[3], ax2 = new float[3], ax3 = new float[3], axz = new float[3];
    private static void col(float[] m, int j, float[] o) { o[0] = m[j]; o[1] = m[3 + j]; o[2] = m[6 + j]; }
    private void gyroDraw(Canvas c) {
        rotM(m1, 0, gyA1);
        rotM(mt, 2, gyA2); mulM(m1, mt, m2);
        rotM(mt, 0, gyA3); mulM(m2, mt, m3);
        // outer ring: plane (M1 x, M1 z); middle ring: (M2 z, M2 x); inner ring: (M3 x, M3 y)
        float rs = 100f;
        col(m1, 0, ax1); col(m1, 2, axz);
        ring3(c, ax1, axz, rs, 7f, METAL_GOLD, 0xF0);
        col(m2, 2, ax2); col(m2, 0, ax1);
        ring3(c, ax2, ax1, rs * 0.82f, 6f, METAL_TI, 0xF0);
        col(m3, 0, ax3); col(m3, 1, ax2);
        ring3(c, ax3, ax2, rs * 0.64f, 5f, GLOW, 0xE0);
        // rotor: disc spanning (M3 x, M3 z), axle along M3 y
        col(m3, 0, ax1); col(m3, 2, axz); col(m3, 1, ax2);
        shaft3(c, G[0] - ax2[0] * 70f, G[1] - ax2[1] * 70f, G[2] - ax2[2] * 70f, G[0] + ax2[0] * 70f, G[1] + ax2[1] * 70f, G[2] + ax2[2] * 70f, 3f, STEEL);
        gearPlane(c, pRotor, 3.1f, G[0], G[1], G[2], ax1, axz, gyRot, 7f, BARREL, true);
        // pivot pins of the three rings
        fillP.setShader(null); fillP.setStyle(Paint.Style.FILL);
        for (int k = -1; k <= 1; k += 2) {
            col(m1, 0, ax1);
            cubeProj(G[0] + k * ax1[0] * rs, G[1] + k * ax1[1] * rs, G[2] + k * ax1[2] * rs);
            fillP.setColor(shade(METAL_GOLD, 1f)); c.drawCircle(prx, pry, 6f * prs, fillP);
            col(m2, 2, ax1);
            cubeProj(G[0] + k * ax1[0] * rs * 0.82f, G[1] + k * ax1[1] * rs * 0.82f, G[2] + k * ax1[2] * rs * 0.82f);
            fillP.setColor(shade(METAL_TI, 1f)); c.drawCircle(prx, pry, 5f * prs, fillP);
        }
        cubeProj(G[0], G[1], G[2]);
        fillP.setColor(al(0xFFFFFFFF, 0xC0)); c.drawCircle(prx, pry, 5f * prs, fillP);
        fillP.setColor(al(GLOW, 0x30)); c.drawCircle(prx, pry, rs * 1.15f * prs, fillP);
    }
    private void shaftDraw(Canvas c, int w) {
        float[] o = WO[w];
        shaft3(c, o[0] * 0.918f, o[1] * 0.918f, o[2] * 0.918f, o[0], o[1], o[2], 5f, STEEL);
    }
    private void drawWatchGroup(Canvas c) {
        int n = parts.size();
        if (frontView) for (int i = 0; i < n; i++) parts.get(i).fn.draw(c);
        else for (int i = n - 1; i >= 0; i--) parts.get(i).fn.draw(c);
    }
    /** Painter's sort of the engine groups (far to near) and drawing. */
    private void drawEngine(Canvas c) {
        int ne = 0;
        RA = R;
        for (int w = 0; w < 6; w++) { entDepth[ne] = camZ(WO[w][0], WO[w][1], WO[w][2]); entKind[ne++] = w; }
        for (int w = 0; w < 6; w++) { entDepth[ne] = camZ(WO[w][0] * 0.96f, WO[w][1] * 0.96f, WO[w][2] * 0.96f); entKind[ne++] = 10 + w; }
        RA = Rw;
        entDepth[ne] = camZ(G[0], G[1], G[2]); entKind[ne++] = 20;
        entDepth[ne] = camZ(0f, 0f, -17f); entKind[ne++] = 30;
        for (int i = 1; i < ne; i++) {
            float dv = entDepth[i]; int kv = entKind[i]; int j = i - 1;
            while (j >= 0 && entDepth[j] > dv) { entDepth[j + 1] = entDepth[j]; entKind[j + 1] = entKind[j]; j--; }
            entDepth[j + 1] = dv; entKind[j + 1] = kv;
        }
        for (int i = 0; i < ne; i++) {
            int k = entKind[i];
            if (k < 20) {
                RA = R;
                int w = k < 10 ? k : k - 10;
                RA = R;
                ga = Math.max(0f, Math.min(1f, (-camZ(WO[w][0], WO[w][1], WO[w][2]) - 40f) / 150f));
                if (ga > 0.02f) { if (k < 10) wallDraw(c, w); else shaftDraw(c, w); }
                ga = 1f;
            } else if (k == 20) { RA = Rw; ga = 1f; gyroDraw(c); }
            else { RA = Rw; drawWatchGroup(c); }
        }
        RA = R;
    }

    // =================================================================================================
    // Frame
    // =================================================================================================
    void draw(Canvas c, long now, boolean ecoMode) {
        if (half <= 4f) return;
        if (phase0 == 0L) phase0 = now;
        float dt = lastNow == 0L ? 0.016f : Math.min(0.06f, (now - lastNow) / 1000f);
        lastNow = now;
        eco = ecoMode;
        updateTime(now);
        updateOrientation(now, dt);
        qToM(q, R);
        // the watch is gimbal-mounted: it ignores the cube's spin and always faces the viewer, upright
        double wt = (now - phase0) / 1000.0;
        eulerYXZ(0.10f * (float) Math.sin(wt * 0.31) + 0.5f * tiltX, -0.22f * (float) Math.sin(wt * 0.23) - 0.5f * tiltY, 0f, qw);
        qToM(qw, Rw);
        // keep the contact corner exactly on the cylinder lid whatever the pose
        RA = R; cx = cx0; cy = cy0; focal = CAMD * unit;
        cubeProj(-HALF, -HALF, -HALF);
        // the contact-corner pin only applies while the cube stands (near) on its corner; a rolling cube stays centred in the sphere
        float down = (R[3] + R[4] + R[5]) / 1.7320508f;
        float pin = smooth((down - 0.825f) / (0.989f - 0.825f));
        float ccx = cx0 + pin * (cx0 - prx), ccy = cy0 + pin * (cy0 + 1.7320508f * half - pry);
        float fz = smooth(focus);
        cx = ccx + (focusCx - ccx) * fz; cy = ccy + (focusCy - ccy) * fz;
        focal = CAMD * unit * (1f + 0.5f * fz);
        float sphereR = SPH * unit;
        hitCx = fz > 0.5f ? cx : cx0; hitCy = fz > 0.5f ? cy : cy0;
        hitR = fz > 0.5f ? half * 1.9f : sphereR;
        frontView = Rw[8] >= 0f;
        float nx = Rw[2], ny = Rw[5], nz = Rw[8];
        float l = Math.abs(nx * -0.30f + ny * 0.42f + nz * 0.85f);
        shadeAll = 0.62f + 0.48f * Math.min(1f, l);
        refreshPanels(now);

        // cube corners (cube space) -> screen
        for (int i = 0; i < 8; i++) {
            float x = ((i & 1) == 0 ? -1 : 1) * HALF, y = ((i & 2) == 0 ? -1 : 1) * HALF, z = ((i & 4) == 0 ? -1 : 1) * HALF;
            cubeProj(x, y, z);
            cX[i] = prx; cY[i] = pry; cS[i] = prs; cZ[i] = prz;
        }
        drawSphere(c, false, 1f - fz, sphereR);
        drawFaces(c, false);
        drawEdges(c, false);
        // watch parts (painter's order)
        drawEngine(c);
        drawEdges(c, true);
        drawFaces(c, true);
        drawSphere(c, true, 1f - fz, sphereR);
    }

    // ---- glass sphere around the cube ------------------------------------------------------------------
    private static final float SPH = 678f;
    private android.graphics.RadialGradient sphBody, sphRim;
    private float sphKey = -1f;
    private final android.graphics.RectF sphRect = new android.graphics.RectF();
    private void drawSphere(Canvas c, boolean front, float sa, float r) {
        if (sa <= 0.02f) return;
        float key = r + cx0 * 0.001f + cy0 * 0.0001f;
        if (key != sphKey) {
            sphKey = key;
            sphBody = new android.graphics.RadialGradient(cx0, cy0, r, new int[]{al(0xFF9FD8FF, 0x03), al(0xFF9FD8FF, 0x10), al(0xFFBFE8FF, 0x2A)}, new float[]{0f, 0.78f, 1f}, Shader.TileMode.CLAMP);
            sphRim = new android.graphics.RadialGradient(cx0, cy0, r, new int[]{0x00FFFFFF, al(0xFFDDF4FF, 0x16), al(0xFFDDF4FF, 0x70)}, new float[]{0.82f, 0.94f, 1f}, Shader.TileMode.CLAMP);
        }
        fillP.setStyle(Paint.Style.FILL);
        if (!front) {
            fillP.setShader(sphBody); fillP.setAlpha((int) (255 * sa));
            c.drawCircle(cx0, cy0, r, fillP);
            // soft pool of light where the sphere rests on the cylinder lid
            sphRect.set(cx0 - r * 0.55f, cy0 + r * 0.93f, cx0 + r * 0.55f, cy0 + r * 1.07f);
            fillP.setShader(null); fillP.setColor(al(accent, (int) (0x30 * sa)));
            c.drawOval(sphRect, fillP);
            fillP.setAlpha(255);
            return;
        }
        fillP.setShader(sphRim); fillP.setAlpha((int) (255 * sa));
        c.drawCircle(cx0, cy0, r, fillP);
        fillP.setShader(null); fillP.setAlpha(255);
        strokeP.setShader(null); strokeP.setStyle(Paint.Style.STROKE);
        strokeP.setStrokeWidth(Math.max(1.4f, r * 0.006f));
        strokeP.setColor(al(0xFFE8F8FF, (int) (0x90 * sa)));
        c.drawCircle(cx0, cy0, r, strokeP);
        // specular highlights
        fillP.setStyle(Paint.Style.FILL);
        c.save(); c.rotate(-38f, cx0 - r * 0.46f, cy0 - r * 0.58f);
        sphRect.set(cx0 - r * 0.72f, cy0 - r * 0.70f, cx0 - r * 0.20f, cy0 - r * 0.46f);
        fillP.setColor(al(0xFFFFFFFF, (int) (0x30 * sa))); c.drawOval(sphRect, fillP);
        sphRect.set(cx0 - r * 0.62f, cy0 - r * 0.64f, cx0 - r * 0.34f, cy0 - r * 0.54f);
        fillP.setColor(al(0xFFFFFFFF, (int) (0x58 * sa))); c.drawOval(sphRect, fillP);
        c.restore();
        // faint reflection along the lower right
        strokeP.setStrokeWidth(Math.max(2f, r * 0.02f)); strokeP.setStrokeCap(Paint.Cap.ROUND);
        strokeP.setColor(al(0xFFFFFFFF, (int) (0x22 * sa)));
        sphRect.set(cx0 - r * 0.9f, cy0 - r * 0.9f, cx0 + r * 0.9f, cy0 + r * 0.9f);
        c.drawArc(sphRect, 18f, 38f, false, strokeP);
        strokeP.setStrokeCap(Paint.Cap.BUTT);
    }
    private final float[] cX = new float[8], cY = new float[8], cS = new float[8], cZ = new float[8];

    private void updateTime(long now) {
        long local = now + TimeZone.getDefault().getOffset(now);
        sRaw = (((local / 1000.0) % 43200.0) + 43200.0) % 43200.0;
        tReal = now / 1000.0;
        cal.setTimeInMillis(now);
        float sq;
        if (eco) sq = (float) Math.floor(sRaw);
        else {
            double beat = (tReal * 4.0) % 1.0;
            sq = (float) ((Math.floor(sRaw * 4.0) + smooth((float) (beat / 0.3))) / 4.0);
        }
        float b = eco ? 0f : 4f * (float) Math.sin(TAU * 2.0 * (tReal % 1000.0));
        thA = sq / 3600f * TAU;
        thBar = mesh(thA, 60, 64, aBar); thT = mesh(thA, 60, 8, aT); thF = mesh(thT, 64, 8, aF); thE = mesh(thF, 60, 8, aE);
        forkRot = 9f * DEG * (float) Math.tanh(b * 3f);
        balRot = b;
        float base = sq / 60f * TAU;
        for (int w = 0; w < 6; w++) { wTh1[w] = base * WRATE[w]; wTh2[w] = mesh(wTh1[w], WN1, WN2, WANG[w]); }
        float tt = (float) (tReal % 1000.0);
        if (eco) { gyA1 = base * 0.6f; gyA2 = base * 0.9f; gyA3 = base * 0.5f; gyRot = base * 6f; }
        else { gyA1 = tt * 0.37f; gyA2 = tt * 0.53f; gyA3 = tt * 0.29f; gyRot = tt * 7.5f; }
        hourA = sq / 43200f * TAU - PI / 2f; minA = sq / 3600f * TAU - PI / 2f; secA = sq / 60f * TAU - PI / 2f;
    }

    // ---- cube edges + corner knobs -----------------------------------------------------------------
    private static final int[][] EDGES = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
    private void drawEdges(Canvas c, boolean front) {
        strokeP.setShader(null); strokeP.setStyle(Paint.Style.STROKE); strokeP.setStrokeCap(Paint.Cap.ROUND);
        for (int[] e : EDGES) {
            float mz = (cZ[e[0]] + cZ[e[1]]) / 2f;
            if ((mz >= 0f) != front) continue;
            float s = (cS[e[0]] + cS[e[1]]) / 2f;
            float w = 10f * s;
            float depth = front ? 1f : 0.55f;
            strokeP.setStrokeWidth(w * 2.4f); strokeP.setColor(al(accent, (int) (0x2A * depth)));
            c.drawLine(cX[e[0]], cY[e[0]], cX[e[1]], cY[e[1]], strokeP);
            strokeP.setStrokeWidth(w); strokeP.setColor(shade(BEZEL, (0.55f + 0.45f * depth)));
            c.drawLine(cX[e[0]], cY[e[0]], cX[e[1]], cY[e[1]], strokeP);
            strokeP.setStrokeWidth(Math.max(1f, w * 0.22f)); strokeP.setColor(al(0xFFFFF1D8, (int) (0xB0 * depth)));
            c.drawLine(cX[e[0]], cY[e[0]], cX[e[1]], cY[e[1]], strokeP);
        }
        strokeP.setStrokeCap(Paint.Cap.BUTT);
        fillP.setShader(null); fillP.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 8; i++) {
            if ((cZ[i] >= 0f) != front) continue;
            float r = 11f * cS[i];
            fillP.setColor(al(accent, 0x30)); c.drawCircle(cX[i], cY[i], r * 1.9f, fillP);
            fillP.setColor(shade(BEZEL, front ? 1f : 0.6f)); c.drawCircle(cX[i], cY[i], r, fillP);
            fillP.setColor(al(0xFFFFFFFF, front ? 0xA0 : 0x50)); c.drawCircle(cX[i] - r * 0.3f, cY[i] - r * 0.3f, r * 0.38f, fillP);
        }
    }

    // ---- faces ---------------------------------------------------------------------------------------
    private final float[] fq = new float[8];
    private final float[] meshV = new float[(MESH + 1) * (MESH + 1) * 2];
    private static final int MESH = 8;

    private float faceCamZ(int f) {
        return R[6] * FN[f][0] + R[7] * FN[f][1] + R[8] * FN[f][2];
    }
    private void drawFaces(Canvas c, boolean front) {
        for (int pass = 0; pass < 7; pass++) {
            int f;
            if (pass < 6) { f = pass; if (front && f == focusFace) continue; }       // the focused face is drawn last, on top
            else { if (!front || focusFace < 0) break; f = focusFace; }
            float nzc = faceCamZ(f);
            boolean facing = nzc > 0f;
            if (facing != front) continue;
            // glass quad at the cube surface
            float hx = HALF;
            for (int k = 0; k < 4; k++) {
                float su = (k == 0 || k == 3) ? -1f : 1f, sv = (k < 2) ? 1f : -1f;
                float X = FN[f][0] * hx + FU[f][0] * su * hx + FV[f][0] * sv * hx;
                float Y = FN[f][1] * hx + FU[f][1] * su * hx + FV[f][1] * sv * hx;
                float Z = FN[f][2] * hx + FU[f][2] * su * hx + FV[f][2] * sv * hx;
                cubeProj(X, Y, Z);
                fq[k * 2] = prx; fq[k * 2 + 1] = pry;
            }
            tmp2.reset();
            tmp2.moveTo(fq[0], fq[1]); tmp2.lineTo(fq[2], fq[3]); tmp2.lineTo(fq[4], fq[5]); tmp2.lineTo(fq[6], fq[7]); tmp2.close();
            fillP.setStyle(Paint.Style.FILL);
            if (front) {
                float k = Math.min(1f, nzc * 1.6f);
                fillP.setShader(new LinearGradient(fq[0], fq[1], fq[4], fq[5],
                        new int[]{al(0xFFFFFFFF, (int) (0x3A * k)), al(0xFFBFD8FF, 0x0A), al(0xFFFFFFFF, (int) (0x22 * k))}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
            } else fillP.setShader(null);
            fillP.setColor(al(0xFFCFE6FF, front ? 0x10 : 0x0C));
            c.drawPath(tmp2, fillP);
            fillP.setShader(null);
            if (front) drawPanel(c, f, nzc);
        }
    }
    private void drawPanel(Canvas c, int f, float nzc) {
        float L = 2f * HALF - 30f, off = HALF - 4f;
        int idx = 0;
        for (int j = 0; j <= MESH; j++) for (int i = 0; i <= MESH; i++) {
            float s = (float) i / MESH - 0.5f, t = (float) j / MESH - 0.5f;
            float X = FN[f][0] * off + FU[f][0] * s * L - FV[f][0] * t * L;
            float Y = FN[f][1] * off + FU[f][1] * s * L - FV[f][1] * t * L;
            float Z = FN[f][2] * off + FU[f][2] * s * L - FV[f][2] * t * L;
            cubeProj(X, Y, Z);
            meshV[idx++] = prx; meshV[idx++] = pry;
        }
        float fa = (f == focusFace) ? smooth(focus) : 0f;
        bmpP.setAlpha((int) (255 * Math.min(1f, nzc * 2.4f) * (0.5f + 0.5f * fa)));
        c.drawBitmapMesh(panel[f], MESH, MESH, meshV, 0, null, 0, bmpP);
    }

    // =================================================================================================
    // Panels (information on the cube faces)
    // =================================================================================================
    private void refreshPanels(long now) {
        int sec = (int) (now / 1000L), min = (int) (now / 60000L), day = cal.get(Calendar.DAY_OF_YEAR) + cal.get(Calendar.YEAR) * 400;
        int tick4 = (int) (now / 250L);
        if (day != lastDayPanel) { lastDayPanel = day; panelDirty[F_LEFT] = true; panelDirty[F_FRONT] = true; }
        if (min != lastMinPanel) { lastMinPanel = min; panelDirty[F_BACK] = true; panelDirty[F_RIGHT] = true; panelDirty[F_FRONT] = true; panelDirty[F_LEFT] = true; }
        if (sec != lastSecPanel) { lastSecPanel = sec; panelDirty[F_TOP] = true; panelDirty[F_FRONT] = true; }
        if (tick4 != lastTick4) { lastTick4 = tick4; panelDirty[F_BOTTOM] = true; }
        if (now - batteryAt > 30000L) readBattery(now);
        for (int f = 0; f < 6; f++) {
            if (!panelDirty[f]) continue;
            if (faceCamZ(f) <= 0f && everDrawn[f]) continue;      // not visible: redraw when it turns toward the viewer
            panelDirty[f] = false; everDrawn[f] = true;
            Canvas pc = panelCv[f];
            pc.save();
            pc.scale(PW / 1024f, PW / 1024f);
            switch (f) {
                case F_FRONT: panelFront(pc); break;
                case F_BACK: panelWeather(pc); break;
                case F_RIGHT: panelForecast(pc); break;
                case F_LEFT: panelCalendar(pc); break;
                case F_TOP: panelWorld(pc); break;
                default: panelMovement(pc); break;
            }
            pc.restore();
        }
        maybeFetchForecast(now);
    }
    private int lastTick4 = -1;
    private int batPct = -1; private boolean batCharging = false; private long batteryAt = 0L;
    private void readBattery(long now) {
        batteryAt = now;
        try {
            android.content.Intent i = ctx.registerReceiver(null, new android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED));
            if (i != null) {
                int lvl = i.getIntExtra("level", -1), sc = i.getIntExtra("scale", 100), status = i.getIntExtra("status", -1);
                batPct = sc > 0 && lvl >= 0 ? lvl * 100 / sc : -1;
                batCharging = status == 2 || status == 5;
                panelDirty[F_FRONT] = true;
            }
        } catch (Throwable ignored) { }
    }
    private final boolean[] everDrawn = new boolean[6];
    private boolean topDrawnOnce = false;

    private void clearPanel(Canvas pc) {
        // wipe to transparent: draw with CLEAR-like approach via full-size transparent bitmap erase
        fillP.setShader(null); fillP.setStyle(Paint.Style.FILL);
    }
    private void erase(int f) { panel[f].eraseColor(0); }

    private void frameCard(Canvas pc, int f, String title, int accentCol, String sub) {
        erase(f);
        RectF r = new RectF(28, 28, 1024 - 28, 1024 - 28);
        fillP.setShader(null); fillP.setStyle(Paint.Style.FILL); fillP.setColor(0xF2090F16);
        pc.drawRoundRect(r, 44, 44, fillP);
        strokeP.setShader(null); strokeP.setStrokeWidth(3f); strokeP.setColor(accentCol); strokeP.setStyle(Paint.Style.STROKE);
        pc.drawRoundRect(r, 44, 44, strokeP);
        textP.setShader(null); textP.setTextAlign(Paint.Align.LEFT);
        textP.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); textP.setTextSize(64f); textP.setColor(accentCol);
        pc.drawText(title, 80, 138, textP);
        if (sub != null) { textP.setTypeface(Typeface.DEFAULT); textP.setTextSize(30f); textP.setColor(DIM); pc.drawText(sub, 80, 184, textP); }
        strokeP.setStrokeWidth(2f); strokeP.setColor(0x40C4D2E0);
        pc.drawLine(80, 214, 1024 - 80, 214, strokeP);
    }
    private void text(Canvas pc, String s, float x, float y, float size, int color, boolean medium, Paint.Align al) {
        textP.setShader(null); textP.setTextAlign(al);
        textP.setTypeface(medium ? Typeface.create("sans-serif-medium", Typeface.NORMAL) : Typeface.DEFAULT);
        textP.setTextSize(size); textP.setColor(color);
        pc.drawText(s, x, y, textP);
    }
    private void mono(Canvas pc, String s, float x, float y, float size, int color, Paint.Align al) {
        textP.setShader(null); textP.setTextAlign(al); textP.setTypeface(Typeface.MONOSPACE); textP.setTextSize(size); textP.setColor(color);
        pc.drawText(s, x, y, textP);
    }
    private static String two(int n) { return n < 10 ? "0" + n : String.valueOf(n); }
    private String ageText(long at) {
        if (at <= 0L) return "not updated yet";
        long m = Math.max(0L, (System.currentTimeMillis() - at) / 60000L);
        if (m < 1) return "just now"; if (m < 60) return m + " min ago"; if (m < 2880) return (m / 60) + " h ago"; return (m / 1440) + " days ago";
    }
    private static String condText(int code) {
        if (code < 0) return "—"; if (code == 0) return "Clear"; if (code <= 3) return code == 1 ? "Mostly clear" : "Cloudy";
        if (code == 45 || code == 48) return "Fog"; if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) return "Rain";
        if (code >= 71 && code <= 77) return "Snow"; if (code >= 95) return "Thunderstorm"; return "—";
    }

    private void panelFront(Canvas pc) {
        erase(F_FRONT);
        String[] dn = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
        String[] mn = {"January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"};
        chip(pc, 44, 44, 330, dn[cal.get(Calendar.DAY_OF_WEEK) - 1], cal.get(Calendar.DAY_OF_MONTH) + " " + mn[cal.get(Calendar.MONTH)], GOLD);
        String t = !Double.isNaN(curTemp) ? fmt1(curTemp) + "\u00B0 " + condText(curCode) : temp.isEmpty() ? "\u2014" : temp + "\u00B0 " + condText(wCode);
        chip(pc, 1024 - 44 - 330, 44, 330, t, curPlace.isEmpty() ? city : curPlace, BARREL);
        chip(pc, 44, 1024 - 44 - 104, 330, batPct >= 0 ? batPct + "%" + (batCharging ? "  \u26A1" : "") : "\u2014", "Battery", batPct >= 0 && batPct <= 20 ? RUBY : THIRD);
        chip(pc, 1024 - 44 - 330, 1024 - 44 - 104, 330, two(cal.get(Calendar.HOUR_OF_DAY)) + ":" + two(cal.get(Calendar.MINUTE)) + ":" + two(cal.get(Calendar.SECOND)), "Local time \u00B7 live", BALANCE);
    }
    private void chip(Canvas pc, float a, float b, float w, String l1, String l2, int col) {
        RectF r = new RectF(a, b, a + w, b + 104);
        fillP.setShader(null); fillP.setStyle(Paint.Style.FILL); fillP.setColor(0xB8090F16); pc.drawRoundRect(r, 26, 26, fillP);
        strokeP.setShader(null); strokeP.setStyle(Paint.Style.STROKE); strokeP.setStrokeWidth(2f); strokeP.setColor(col); pc.drawRoundRect(r, 26, 26, strokeP);
        text(pc, l1, a + 24, b + 48, 38f, INK, true, Paint.Align.LEFT);
        text(pc, l2, a + 24, b + 86, 28f, DIM, false, Paint.Align.LEFT);
    }

    private void liveDot(Canvas pc, int col) {
        boolean on = (System.currentTimeMillis() / 500L) % 2L == 0L;
        fillP.setShader(null); fillP.setStyle(Paint.Style.FILL);
        fillP.setColor(al(col, on ? 0xFF : 0x55)); pc.drawCircle(1024 - 150, 108, 13, fillP);
        text(pc, "LIVE", 1024 - 124, 118, 30f, col, true, Paint.Align.LEFT);
    }
    private void panelWeather(Canvas pc) {
        frameCard(pc, F_BACK, "Weather", BARREL, (curPlace.isEmpty() ? city : curPlace) + " \u00B7 now");
        liveDot(pc, THIRD);
        boolean night = cal.get(Calendar.HOUR_OF_DAY) >= 18 || cal.get(Calendar.HOUR_OF_DAY) < 6;
        int code = Double.isNaN(curTemp) ? wCode : curCode;
        String tt = !Double.isNaN(curTemp) ? fmt1(curTemp) : temp;
        fillP.setStyle(Paint.Style.FILL); fillP.setShader(null);
        if (night && code <= 1) {
            fillP.setColor(0xFFFFE9A8); pc.drawCircle(320, 440, 112, fillP);
            fillP.setColor(0xFF090F16); pc.drawCircle(368, 410, 98, fillP);
            fillP.setColor(0xFFFFE9A8);
            pc.drawCircle(430, 360, 5, fillP); pc.drawCircle(470, 450, 4, fillP); pc.drawCircle(400, 520, 3, fillP);
        } else if (code <= 1) {
            fillP.setColor(0xFFFFC83D); pc.drawCircle(320, 440, 90, fillP);
            strokeP.setStyle(Paint.Style.STROKE); strokeP.setStrokeWidth(10f); strokeP.setColor(0xFFFFC83D); strokeP.setStrokeCap(Paint.Cap.ROUND);
            for (int i = 0; i < 12; i++) { double a = i * Math.PI / 6; pc.drawLine(320 + (float) Math.cos(a) * 112, 440 + (float) Math.sin(a) * 112, 320 + (float) Math.cos(a) * 140, 440 + (float) Math.sin(a) * 140, strokeP); }
            strokeP.setStrokeCap(Paint.Cap.BUTT);
        } else {
            fillP.setColor(0xE6C4D2E0);
            pc.drawCircle(220, 470, 70, fillP); pc.drawCircle(310, 430, 95, fillP); pc.drawCircle(410, 480, 72, fillP);
            pc.drawRoundRect(new RectF(190, 470, 460, 546), 38, 38, fillP);
        }
        text(pc, tt.isEmpty() ? "\u2014" : tt + "\u00B0", 520, 540, 250f, INK, true, Paint.Align.LEFT);
        text(pc, "Celsius", 540, 590, 34f, DIM, false, Paint.Align.LEFT);
        text(pc, condText(code), 80, 690, 70f, INK, true, Paint.Align.LEFT);
        if (!Double.isNaN(curTemp)) {
            text(pc, "Feels like " + fmt1(curFeels) + "\u00B0", 80, 760, 40f, INK, true, Paint.Align.LEFT);
            text(pc, "Humidity " + curHum + "%", 80, 818, 40f, INK, true, Paint.Align.LEFT);
            text(pc, "Wind " + fmt1(curWind) + " km/h", 520, 818, 40f, INK, true, Paint.Align.LEFT);
        }
        double[] fc = forecastRow(0);
        if (fc != null) {
            text(pc, "Today  High " + fmt1(fc[0]) + "\u00B0   Low " + fmt1(fc[1]) + "\u00B0   Rain " + (int) fc[2] + "%", 80, 886, 36f, GOLD, true, Paint.Align.LEFT);
        } else text(pc, "Waiting for the first live update\u2026", 80, 886, 36f, DIM, false, Paint.Align.LEFT);
        text(pc, "Updated " + ageText(forecastAt > 0 ? forecastAt : wAt), 80, 948, 28f, DIM, false, Paint.Align.LEFT);
    }
    private static String fmt1(double v) { double r = Math.round(v * 10.0) / 10.0; return r == Math.rint(r) ? String.valueOf((long) r) : String.valueOf(r); }

    // forecast row i -> {hi, lo, rain}; null if missing
    private double[] forecastRow(int i) {
        String s = forecastPacked;
        if (s == null || s.isEmpty()) return null;
        String[] rows = s.split(";");
        if (i >= rows.length) return null;
        String[] f = rows[i].split("\\|");
        if (f.length < 4) return null;
        try { return new double[]{Double.parseDouble(f[1]), Double.parseDouble(f[2]), Double.parseDouble(f[3])}; } catch (Exception e) { return null; }
    }
    private String forecastDay(int i) {
        String[] rows = forecastPacked.split(";");
        if (i >= rows.length) return "";
        String[] f = rows[i].split("\\|");
        try {
            Calendar c = Calendar.getInstance();
            String[] ymd = f[0].split("-");
            c.set(Integer.parseInt(ymd[0]), Integer.parseInt(ymd[1]) - 1, Integer.parseInt(ymd[2]));
            String[] n = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
            return n[c.get(Calendar.DAY_OF_WEEK) - 1];
        } catch (Exception e) { return f.length > 0 ? f[0] : ""; }
    }
    private void panelForecast(Canvas pc) {
        frameCard(pc, F_RIGHT, "8-day forecast", ESCAPE, city + " · daily high and low");
        int rows = forecastPacked.isEmpty() ? 0 : Math.min(8, forecastPacked.split(";").length);
        if (rows == 0) { text(pc, "Forecast not loaded yet", 80, 330, 44f, DIM, false, Paint.Align.LEFT); text(pc, "It loads when the launcher is online.", 80, 392, 32f, DIM, false, Paint.Align.LEFT); return; }
        float lo = 99, hi = -99;
        for (int i = 0; i < rows; i++) { double[] r = forecastRow(i); if (r == null) continue; lo = Math.min(lo, (float) r[1]); hi = Math.max(hi, (float) r[0]); }
        lo = (float) Math.floor(lo - 1); hi = (float) Math.ceil(hi + 1); if (hi - lo < 6) hi = lo + 6;
        float bx = 280, bw = 400;
        for (int i = 0; i < rows; i++) {
            double[] r = forecastRow(i); if (r == null) continue;
            float y = 300 + i * 82;
            text(pc, forecastDay(i), 80, y, 40f, INK, true, Paint.Align.LEFT);
            text(pc, fmt1(r[1]) + "°", bx - 18, y, 36f, DIM, true, Paint.Align.RIGHT);
            fillP.setShader(null); fillP.setStyle(Paint.Style.FILL); fillP.setColor(0x26C4D2E0);
            pc.drawRoundRect(new RectF(bx, y - 26, bx + bw, y - 10), 8, 8, fillP);
            float a0 = bx + bw * ((float) r[1] - lo) / (hi - lo), a1 = bx + bw * ((float) r[0] - lo) / (hi - lo);
            fillP.setShader(new LinearGradient(a0, 0, Math.max(a1, a0 + 20), 0, BARREL, ESCAPE, Shader.TileMode.CLAMP));
            pc.drawRoundRect(new RectF(a0, y - 26, Math.max(a1, a0 + 16), y - 10), 8, 8, fillP);
            fillP.setShader(null);
            text(pc, fmt1(r[0]) + "°", bx + bw + 18, y, 36f, INK, true, Paint.Align.LEFT);
            text(pc, "Rain " + (int) r[2] + "%", 835, y, 28f, r[2] >= 10 ? BARREL : DIM, false, Paint.Align.LEFT);
        }
        text(pc, "Updated " + ageText(forecastAt), 80, 948, 28f, DIM, false, Paint.Align.LEFT);
    }

    private void panelCalendar(Canvas pc) {
        String[] mn = {"January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"};
        frameCard(pc, F_LEFT, "Calendar", THIRD, mn[cal.get(Calendar.MONTH)] + " " + cal.get(Calendar.YEAR));
        Calendar f = (Calendar) cal.clone();
        f.set(Calendar.DAY_OF_MONTH, 1);
        int first = (f.get(Calendar.DAY_OF_WEEK) % 7);             // week starts on Saturday: Sat=0, Sun=1 ... Fri=6
        int days = cal.getActualMaximum(Calendar.DAY_OF_MONTH), today = cal.get(Calendar.DAY_OF_MONTH);
        float cw = 124, gx = 78;
        fillP.setShader(null); fillP.setStyle(Paint.Style.FILL); fillP.setColor(0x22FF4FD8);
        for (int cI : new int[]{0, 6}) pc.drawRoundRect(new RectF(gx + cw * cI + 8, 252, gx + cw * cI + cw - 8, 892), 24, 24, fillP);
        String[] wd = {"Sat", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri"};
        for (int i = 0; i < 7; i++) text(pc, wd[i], gx + cw * i + cw / 2, 290, 34f, (i == 0 || i == 6) ? FORK : DIM, true, Paint.Align.CENTER);
        for (int n = 1; n <= days; n++) {
            int idx = first + n - 1, col = idx % 7, row = idx / 7;
            float x = gx + cw * col + cw / 2, y = 380 + row * 96;
            int color = (col == 0 || col == 6) ? FORK : INK;
            if (n == today) { fillP.setColor(GOLD); pc.drawCircle(x, y - 14, 40, fillP); color = 0xFF1A0F08; }
            text(pc, String.valueOf(n), x, y, 46f, color, true, Paint.Align.CENTER);
        }
        fillP.setColor(FORK); pc.drawRoundRect(new RectF(80, 926, 106, 952), 8, 8, fillP);
        text(pc, "Weekend: every Friday and Saturday", 122, 948, 28f, INK, false, Paint.Align.LEFT);
    }

    private void panelWorld(Canvas pc) {
        frameCard(pc, F_TOP, "World time", BALANCE, "Live from your device clock");
        long now = System.currentTimeMillis();
        int baseOff = TimeZone.getTimeZone("Asia/Dhaka").getOffset(now);
        for (int i = 0; i < 5; i++) {
            TimeZone tz = TimeZone.getTimeZone(CITY_TZ[i]);
            Calendar t = Calendar.getInstance(tz); t.setTimeInMillis(now);
            int offMin = (tz.getOffset(now) - baseOff) / 60000;
            int h = t.get(Calendar.HOUR_OF_DAY);
            boolean day = h >= 6 && h < 18;
            float y = 330 + i * 124, cx0 = 116, cy0 = y - 26;
            fillP.setShader(null); fillP.setStyle(Paint.Style.FILL);
            fillP.setColor(day ? ESCAPE : STEEL); pc.drawCircle(cx0, cy0, 24, fillP);
            if (!day) { fillP.setColor(0xFF090F16); pc.drawCircle(cx0 + 10, cy0 - 6, 21, fillP); }
            text(pc, CITY_NAMES[i], 170, y - 14, 50f, INK, true, Paint.Align.LEFT);
            float ah = Math.abs(offMin) / 60f;
            String lab = offMin == 0 ? "Your time zone" : fmt1(ah) + " h " + (offMin > 0 ? "ahead of" : "behind") + " Dhaka";
            text(pc, lab, 170, y + 24, 28f, DIM, false, Paint.Align.LEFT);
            mono(pc, two(h) + ":" + two(t.get(Calendar.MINUTE)) + ":" + two(t.get(Calendar.SECOND)), 1024 - 80, y + 8, 62f, GOLD, Paint.Align.RIGHT);
        }
    }

    private void panelMovement(Canvas pc) {
        frameCard(pc, F_BOTTOM, "Movement", FORK, "Live readings of the running calibre");
        liveDot(pc, FORK);
        long msDay = (System.currentTimeMillis() + TimeZone.getDefault().getOffset(System.currentTimeMillis())) % 86400000L;
        long beats = msDay * 4L / 1000L;
        float swing = balRot * 180f / PI;                       // balance angle in degrees
        int tooth = (int) (((thE / TAU) % 1f + 1f) % 1f * 32f);
        String[][] rows = {
                {"Beat count today", String.valueOf(beats), String.valueOf(BARREL)},
                {"Balance angle", (swing >= 0 ? "+" : "") + Math.round(swing) + "\u00B0", String.valueOf(BALANCE)},
                {"Escape wheel tooth", (tooth + 1) + " / 32", String.valueOf(ESCAPE)},
                {"Fork angle", fmt1(forkRot / DEG) + "\u00B0", String.valueOf(FORK)},
                {"Seconds hand", two(cal.get(Calendar.SECOND)) + "." + (int) ((System.currentTimeMillis() % 1000L) / 100L) + " s", String.valueOf(FOURTH)},
                {"Centre wheel", fmt1((float) ((sRaw % 3600.0) / 10.0)) + "\u00B0 / h", String.valueOf(CENTER)}};
        for (int i = 0; i < rows.length; i++) {
            float y = 300 + i * 74;
            fillP.setShader(null); fillP.setStyle(Paint.Style.FILL); fillP.setColor(Integer.parseInt(rows[i][2]));
            pc.drawCircle(94, y - 14, 15, fillP);
            text(pc, rows[i][0], 130, y, 38f, INK, true, Paint.Align.LEFT);
            mono(pc, rows[i][1], 1024 - 80, y, 42f, GOLD, Paint.Align.RIGHT);
        }
        // live balance-wheel oscillation trace (2 Hz)
        float x0 = 80, x1 = 944, yc = 800, amp = 70;
        strokeP.setShader(null); strokeP.setStyle(Paint.Style.STROKE); strokeP.setStrokeWidth(2f); strokeP.setColor(0x40C4D2E0);
        pc.drawLine(x0, yc, x1, yc, strokeP);
        tmp.reset();
        double tNow = System.currentTimeMillis() / 1000.0;
        for (int i = 0; i <= 120; i++) {
            double tt = tNow - (120 - i) * 0.0125;
            float y = yc - amp * (float) Math.sin(TAU * 2.0 * (tt % 1000.0));
            float x = x0 + (x1 - x0) * i / 120f;
            if (i == 0) tmp.moveTo(x, y); else tmp.lineTo(x, y);
        }
        strokeP.setStrokeWidth(4f); strokeP.setColor(BALANCE); pc.drawPath(tmp, strokeP);
        fillP.setColor(0xFFFFFFFF); pc.drawCircle(x1, yc - amp * (float) Math.sin(TAU * 2.0 * (tNow % 1000.0)), 9, fillP);
        text(pc, "Balance wheel \u00B7 4 beats per second \u00B7 14,400 A/h", 80, 948, 28f, DIM, false, Paint.Align.LEFT);
    }

    // =================================================================================================
    // Forecast download (Open-Meteo, keyless) — cached on the device
    // =================================================================================================
    private void maybeFetchForecast(long now) {
        long wall = System.currentTimeMillis();
        boolean cityChanged = !city.equals(fetchedCity);
        long gap = cityChanged ? 20_000L : (forecastAt > 0L && wall - forecastAt < 3600_000L ? 10 * 60_000L : 2 * 60_000L);
        if (fetching || wall - forecastTry < gap) return;
        forecastTry = wall; fetching = true;
        final String cityNow = city;
        WeatherService.fetchDaily(cityNow, new WeatherService.DailyCallback() {
            @Override public void onResult(String packed) {
                applyLive(packed); fetchedCity = cityNow; fetching = false;
                try { ctx.getSharedPreferences("cube_watch", 0).edit().putString("fc", packed).putLong("fc_at", forecastAt).apply(); } catch (Throwable ignored) { }
            }
            @Override public void onError() { fetching = false; }
        });
    }
    private volatile boolean fetching = false;
    private String fetchedCity = "";
    private double curTemp = Double.NaN, curWind, curFeels;
    private int curCode = -1, curHum;
    private String curPlace = "";
    /** packed = optional "C|temp|code|wind|humidity|feels|place" row followed by daily rows. */
    private void applyLive(String packed) {
        String days = packed;
        if (packed.startsWith("C|")) {
            int semi = packed.indexOf(';');
            String row = semi < 0 ? packed : packed.substring(0, semi);
            days = semi < 0 ? "" : packed.substring(semi + 1);
            String[] f = row.split("\\|");
            try {
                curTemp = Double.parseDouble(f[1]); curCode = Integer.parseInt(f[2]); curWind = Double.parseDouble(f[3]);
                curHum = Integer.parseInt(f[4]); curFeels = Double.parseDouble(f[5]); curPlace = f.length > 6 ? f[6] : "";
            } catch (Exception ignored) { }
        }
        forecastPacked = days; forecastAt = System.currentTimeMillis();
        panelDirty[F_BACK] = true; panelDirty[F_RIGHT] = true; panelDirty[F_FRONT] = true;
    }
}

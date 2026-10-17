package com.cylinderradar.launcher;

import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.app.WallpaperManager;
import android.provider.MediaStore;
import android.provider.Settings;
import android.net.Uri;
import android.text.format.DateFormat;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * The whole launcher UI: a glowing 3D cylinder carrying the app icons, a floating light-orbit clock
 * (digital or analog with a sweeping second hand), three cylinders (HOME / ALL APPS / VAULT) and gestures.
 */
public class LauncherView extends View {
    static final int HOME = 0, ALL = 1, VAULT = 2;
    static final int[] COLORS = {0xFF39FF88, 0xFF00BEFF, 0xFFD24BFF, 0xFFFF414B, 0xFFFF9123, 0xFF00EBE1, 0xFFFF4FA3, 0xFFFFD60A};
    static final String[] COLOR_NAMES = {"Radar Green", "Electric Blue", "Violet", "Red", "Amber", "Cyan", "Hot Pink", "Gold"};

    private static final int MODE_NONE = 0, MODE_ROT = 1, MODE_VERT = 2, MODE_NAV = 3, MODE_REVEAL = 4;

    /** One cylinder worth of apps and its rotation state. */
    static final class Page {
        final ArrayList<AppItem> items = new ArrayList<>();
        float rot, vel;
        int rows = 1, per = 1, slots = 6;
        float grid = (float) (Math.PI / 3);
        float[] px = new float[0], py = new float[0], pz = new float[0], ps = new float[0];

        void layout() {
            int n = items.size();
            rows = Math.max(1, Math.min(5, (n + 8) / 9));
            per = Math.max(1, (n + rows - 1) / rows);
            slots = Math.max(per, 6);
            grid = (float) (2 * Math.PI / slots / rows);
            if (px.length < n) {
                px = new float[n]; py = new float[n]; pz = new float[n]; ps = new float[n];
            }
        }
    }

    final MainActivity act;
    final Store store;
    final float d;
    final ArrayList<AppItem> allApps = new ArrayList<>();
    final Page[] pages = {new Page(), new Page(), new Page()};

    /** 0 = wallpaper home (live wallpaper visible, launcher hidden), 1 = launcher fully shown. */
    float reveal = 0f, revealTarget = 0f;
    int insetTop = 0, insetBottom = 0;
    long wallSince = 0L;
    int builtDim = -1, lastSlot = 0;
    private static final float[] DIM_ALPHA = {0.55f, 0.82f, 1f};

    int cur = HOME, prev = -1, transDir = 1;
    float trans = 1f;
    boolean vaultOpen = false;
    private static final long VAULT_IDLE_TIMEOUT_MS = 60_000L;
    private long vaultExpiresAt = 0L;

    // geometry
    int w, h;
    float cx, rw, rh, ty, by, top, bot, orbCy, orbRx, orbRy, faceR, faceCy, iconSz;

    // paints & caches
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint bmpP = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    final Paint textP = new Paint(Paint.ANTI_ALIAS_FLAG);
    final RectF rf = new RectF();
    final RectF dst = new RectF();
    final Path path = new Path();
    final Calendar cal = Calendar.getInstance();
    Bitmap layerBack, layerFront;
    int builtAccent = -1;
    Shader faceShader;
    float[] tickMinor, tickMajor;
    Typeface thin, medium;
    long lastNs = 0;

    // clock caches
    int timeKey = -1;
    String timeStr = "", ampm = "", dateStr = "";
    int dateKey = -1;
    int secKey = -1;
    String secStr = "00";

    // touch state
    VelocityTracker vt;
    float downX, downY, lastX;
    int mode = MODE_NONE, zone = 0, pressed = -1;
    boolean touching = false, lpFired = false;
    long lastRight = 0;
    final RectF[] labelRects = {new RectF(), new RectF(), new RectF()};
    final Runnable lpRun = new Runnable() {
        @Override public void run() {
            if (!touching || mode != MODE_NONE) return;
            lpFired = true;
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            if (pressed >= 0 && pressed < pages[cur].items.size()) act.showAppMenu(pages[cur].items.get(pressed), cur);
            else act.showSettings();
        }
    };

    public LauncherView(MainActivity a, Store s) {
        super(a);
        act = a;
        store = s;
        d = a.getResources().getDisplayMetrics().density;
        thin = Typeface.create("sans-serif-thin", Typeface.NORMAL);
        medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        wallSince = System.currentTimeMillis();
        reload();
    }

    // =============================================================================================
    // Data
    // =============================================================================================
    void reload() {
        final int px = (int) (72 * d);
        new Thread(new Runnable() {
            @Override public void run() {
                PackageManager pm = act.getPackageManager();
                Intent q = new Intent(Intent.ACTION_MAIN, null);
                q.addCategory(Intent.CATEGORY_LAUNCHER);
                List<ResolveInfo> list = pm.queryIntentActivities(q, 0);
                final ArrayList<AppItem> res = new ArrayList<>();
                for (ResolveInfo r : list) {
                    if (r.activityInfo.packageName.equals(act.getPackageName())) continue;
                    try {
                        ComponentName cn = new ComponentName(r.activityInfo.packageName, r.activityInfo.name);
                        res.add(new AppItem(cn, String.valueOf(r.loadLabel(pm)), toBitmap(r.loadIcon(pm), px)));
                    } catch (Throwable ignored) { }
                }
                Collections.sort(res, new java.util.Comparator<AppItem>() {
                    @Override public int compare(AppItem a, AppItem b) { return a.label.compareToIgnoreCase(b.label); }
                });
                post(new Runnable() {
                    @Override public void run() {
                        allApps.clear();
                        allApps.addAll(res);
                        store.migrateLegacyAppLists(allApps);
                        normalizeHiddenPackages();
                        firstRunDefaults();
                        rebuildPages();
                    }
                });
            }
        }).start();
    }

    static Bitmap toBitmap(Drawable dr, int s) {
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Path clip = new Path();
        clip.addRoundRect(new RectF(0, 0, s, s), s * 0.24f, s * 0.24f, Path.Direction.CW);
        c.clipPath(clip);
        if (dr instanceof AdaptiveIconDrawable) {
            int pad = (int) (s * 0.25f);   // adaptive layers are 108dp, visible area is the inner 72dp
            dr.setBounds(-pad, -pad, s + pad, s + pad);
        } else {
            dr.setBounds(0, 0, s, s);
        }
        dr.draw(c);
        return b;
    }

    /** First launch: pin the usual suspects (phone, messages, browser, camera, gallery, settings). */
    void firstRunDefaults() {
        if (store.initDone() || allApps.isEmpty()) return;
        PackageManager pm = act.getPackageManager();
        ArrayList<Intent> probes = new ArrayList<>();
        probes.add(new Intent(Intent.ACTION_DIAL));
        probes.add(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING));
        probes.add(new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")));
        probes.add(new Intent(MediaStore.ACTION_IMAGE_CAPTURE));
        probes.add(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_GALLERY));
        probes.add(new Intent(Settings.ACTION_SETTINGS));
        ArrayList<String> pins = new ArrayList<>();
        for (Intent in : probes) {
            try {
                ResolveInfo ri = pm.resolveActivity(in, PackageManager.MATCH_DEFAULT_ONLY);
                if (ri == null || ri.activityInfo == null) continue;
                String pkg = ri.activityInfo.packageName;
                for (AppItem it : allApps) {
                    if (it.pkg.equals(pkg)) {
                        if (!pins.contains(it.key)) pins.add(it.key);
                        break;
                    }
                }
            } catch (Throwable ignored) { }
        }
        for (int i = 0; pins.size() < 6 && i < allApps.size(); i++) {
            if (!pins.contains(allApps.get(i).key)) pins.add(allApps.get(i).key);
        }
        store.saveList("pinned", pins);
        store.setInitDone();
    }

    void rebuildPages() {
        List<String> hidden = store.list("hidden"), pinned = store.list("pinned");
        HashMap<String, AppItem> map = new HashMap<>();
        for (AppItem a : allApps) map.put(a.key, a);
        Page all = pages[ALL], home = pages[HOME], vault = pages[VAULT];
        all.items.clear(); home.items.clear(); vault.items.clear();
        for (AppItem a : allApps) if (!hidden.contains(a.key)) all.items.add(a);
        for (String k : pinned) {
            AppItem a = map.get(k);
            if (a != null && !hidden.contains(k) && !home.items.contains(a)) home.items.add(a);
        }
        if (vaultOpen) for (String k : hidden) { AppItem a = map.get(k); if (a != null) vault.items.add(a); }
        for (Page pg : pages) pg.layout();
        invalidate();
    }

    void pin(AppItem a) { store.add("pinned", a.key); rebuildPages(); }
    void unpin(AppItem a) { store.remove("pinned", a.key); rebuildPages(); }

    /** Vault represents apps, not individual launcher shortcuts: hide every shortcut for this package. */
    void hide(AppItem a) {
        for (AppItem item : allApps) {
            if (item.pkg.equals(a.pkg)) {
                store.add("hidden", item.key);
                store.remove("pinned", item.key);
            }
        }
        rebuildPages();
    }

    void unhide(AppItem a) {
        for (AppItem item : allApps) if (item.pkg.equals(a.pkg)) store.remove("hidden", item.key);
        rebuildPages();
    }

    boolean isPinned(AppItem a) { return store.contains("pinned", a.key); }

    /** Normalize old data where only one activity of a multi-shortcut app was hidden. */
    private void normalizeHiddenPackages() {
        ArrayList<String> hiddenList = new ArrayList<>(store.list("hidden"));
        Set<String> hiddenKeys = new HashSet<>(hiddenList);
        Set<String> hiddenPackages = new HashSet<>();
        for (AppItem item : allApps) if (hiddenKeys.contains(item.key)) hiddenPackages.add(item.pkg);
        if (hiddenPackages.isEmpty()) return;
        ArrayList<String> pinned = new ArrayList<>(store.list("pinned"));
        boolean hiddenChanged = false, pinsChanged = false;
        for (AppItem item : allApps) {
            if (hiddenPackages.contains(item.pkg)) {
                if (hiddenKeys.add(item.key)) { hiddenList.add(item.key); hiddenChanged = true; }
                if (pinned.remove(item.key)) pinsChanged = true;
            }
        }
        if (hiddenChanged) store.saveList("hidden", hiddenList);
        if (pinsChanged) store.saveList("pinned", pinned);
    }

    // =============================================================================================
    // Navigation / vault
    // =============================================================================================
    void goTo(int t) {
        int max = vaultOpen ? VAULT : ALL;
        t = Math.max(0, Math.min(max, t));
        if (t == cur) return;
        prev = cur;
        transDir = t > cur ? 1 : -1;
        cur = t;
        trans = 0f;
        if (prev == VAULT) closeVault(false); // keep the transition screen protected until vault icons leave
        invalidate();
    }

    void closeVault() { closeVault(true); }

    private void closeVault(boolean clearSecureFlag) {
        vaultOpen = false;
        vaultExpiresAt = 0L;
        if (clearSecureFlag) act.setSecure(false);
    }

    /** Re-lock immediately (home pressed, app launched, screen off...). */
    void lockNow() {
        lastRight = 0L;
        if (!vaultOpen && cur != VAULT && prev != VAULT) return;
        closeVault();
        pages[VAULT].items.clear();
        pages[VAULT].layout();
        if (cur == VAULT) cur = HOME;
        prev = -1;
        trans = 1f;
        invalidate();
    }

    void onHome() {
        lockNow();
        goTo(HOME);
        hideToWall();
    }

    boolean onBack() {
        if (reveal <= 0.01f && revealTarget == 0f) return false;
        if (cur != HOME) { goTo(HOME); return true; }
        hideToWall();
        return true;
    }

    /** Slide the cylinder launcher away and show the bare (live) wallpaper. */
    void hideToWall() {
        lockNow();
        revealTarget = 0f;
        wallSince = System.currentTimeMillis();
        invalidate();
    }

    void showLauncher() {
        revealTarget = 1f;
        invalidate();
    }

    void setInsets(int top, int bottom) {
        insetTop = top; insetBottom = bottom;
        if (w > 0) onSizeChanged(w, h, w, h);
        invalidate();
    }

    private void sendWallTap(float x, float y) {
        try {
            WallpaperManager.getInstance(getContext()).sendWallpaperCommand(getWindowToken(),
                    WallpaperManager.COMMAND_TAP, (int) x, (int) y, 0, null);
        } catch (Throwable ignored) { }
    }

    private boolean registerRightSwipeForVault() {
        long n = System.currentTimeMillis();
        boolean dbl = lastRight > 0L && n - lastRight < 1100L && !vaultOpen;
        lastRight = n;
        if (dbl) {
            lastRight = 0L;
            requestVault();
            return true;
        }
        return false;
    }

    void onRightSwipe() {
        if (registerRightSwipeForVault()) return;
        if (cur > HOME) goTo(cur - 1);
    }

    void requestVault() {
        if (vaultOpen) return;
        act.authenticate(new Runnable() {
            @Override public void run() {
                vaultOpen = true;
                vaultExpiresAt = System.currentTimeMillis() + VAULT_IDLE_TIMEOUT_MS;
                act.setSecure(true);
                rebuildPages();
                goTo(VAULT);
            }
        });
    }

    void resetClock() { lastNs = 0; invalidate(); }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        cal.setTimeZone(TimeZone.getDefault());
        lastNs = 0;
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(lpRun);
        super.onDetachedFromWindow();
    }

    // =============================================================================================
    // Geometry & static layers
    // =============================================================================================
    @Override protected void onSizeChanged(int nw, int nh, int ow, int oh) {
        super.onSizeChanged(nw, nh, ow, oh);
        w = nw; h = nh;
        cx = w / 2f;
        rw = Math.min(w * 0.36f, h * 0.20f);
        rh = rw * 0.20f;
        ty = h * 0.40f;
        by = h * 0.79f;
        top = ty + rh * 0.55f;
        bot = by - rh * 0.55f;
        orbCy = h * 0.31f;
        orbRx = rw * 0.80f;
        orbRy = orbRx * 0.20f;
        faceR = Math.min(w * 0.17f, h * 0.085f);
        faceCy = Math.max(orbCy - orbRy - faceR - 22 * d, faceR + 64 * d + insetTop);
        layerBack = null;
        layerFront = null;
    }

    static int ac(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0xFFFFFF);
    }

    private void glowArc(Canvas c, RectF r, float st, float sw, int col, float width) {
        Paint g = new Paint(Paint.ANTI_ALIAS_FLAG);
        g.setStyle(Paint.Style.STROKE);
        g.setStrokeCap(Paint.Cap.ROUND);
        g.setStrokeWidth(width * 3f);
        g.setColor(ac(col, 0x80));
        g.setMaskFilter(new BlurMaskFilter(width * 4f, BlurMaskFilter.Blur.NORMAL));
        c.drawArc(r, st, sw, false, g);
        g.setMaskFilter(null);
        g.setStrokeWidth(width);
        g.setColor(ac(col, 0xF0));
        c.drawArc(r, st, sw, false, g);
    }

    private void glowLine(Canvas c, float x1, float y1, float x2, float y2, int col, float width) {
        Paint g = new Paint(Paint.ANTI_ALIAS_FLAG);
        g.setStyle(Paint.Style.STROKE);
        g.setStrokeWidth(width * 3f);
        g.setColor(ac(col, 0x70));
        g.setMaskFilter(new BlurMaskFilter(width * 4f, BlurMaskFilter.Blur.NORMAL));
        c.drawLine(x1, y1, x2, y2, g);
        g.setMaskFilter(null);
        g.setStrokeWidth(width);
        g.setColor(ac(col, 0xE0));
        c.drawLine(x1, y1, x2, y2, g);
    }

    /** Pre-renders everything that never changes frame to frame (background, glass, beam, rims). */
    void buildLayers(int acc) {
        builtAccent = acc;
        builtDim = store.dim();
        float da = DIM_ALPHA[Math.max(0, Math.min(2, builtDim))];
        layerBack = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        layerFront = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas b = new Canvas(layerBack);
        Paint q = new Paint(Paint.ANTI_ALIAS_FLAG);

        // background
        q.setShader(new LinearGradient(0, 0, 0, h, new int[]{ac(0x010607, (int) (255 * da)), ac(0x03130F, (int) (255 * da)), ac(0x010807, (int) (255 * da))}, null, Shader.TileMode.CLAMP));
        b.drawRect(0, 0, w, h, q);
        q.setShader(new RadialGradient(cx, (ty + by) / 2f, h * 0.55f,
                new int[]{ac(acc, 0x3A), ac(acc, 0x10), ac(acc, 0x00)}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        b.drawRect(0, 0, w, h, q);
        q.setShader(null);

        // radar floor rings
        q.setStyle(Paint.Style.STROKE);
        q.setStrokeWidth(1f * d);
        for (int k = 1; k <= 7; k++) {
            float rx = rw * (1.05f + 0.40f * k);
            q.setColor(ac(acc, 0x34 - k * 4));
            rf.set(cx - rx, by - rx * 0.20f, cx + rx, by + rx * 0.20f);
            b.drawOval(rf, q);
        }
        // spokes on the floor
        q.setColor(ac(acc, 0x12));
        for (int k = 0; k < 12; k++) {
            double a = k * Math.PI / 6;
            float rx = rw * 3.6f;
            b.drawLine(cx, by, cx + (float) Math.sin(a) * rx, by - (float) Math.cos(a) * rx * 0.20f, q);
        }

        // light beam from the floating clock down onto the cylinder lid
        path.reset();
        path.moveTo(cx - orbRx * 0.9f, orbCy);
        path.lineTo(cx + orbRx * 0.9f, orbCy);
        path.lineTo(cx + rw * 0.98f, ty);
        path.lineTo(cx - rw * 0.98f, ty);
        path.close();
        q.setStyle(Paint.Style.FILL);
        q.setShader(new LinearGradient(0, orbCy, 0, ty, ac(acc, 0x34), ac(acc, 0x05), Shader.TileMode.CLAMP));
        b.drawPath(path, q);
        q.setShader(null);

        // clock halo disc + rings
        q.setShader(new RadialGradient(cx, orbCy, orbRx, new int[]{ac(acc, 0x30), ac(acc, 0x08)}, null, Shader.TileMode.CLAMP));
        rf.set(cx - orbRx, orbCy - orbRy, cx + orbRx, orbCy + orbRy);
        b.drawOval(rf, q);
        q.setShader(null);
        q.setStyle(Paint.Style.STROKE);
        q.setStrokeWidth(1f * d);
        q.setColor(ac(acc, 0x50));
        b.drawOval(rf, q);
        q.setColor(ac(acc, 0x22));
        rf.set(cx - orbRx * 0.86f, orbCy - orbRy * 0.86f, cx + orbRx * 0.86f, orbCy + orbRy * 0.86f);
        b.drawOval(rf, q);

        // cylinder interior
        q.setStyle(Paint.Style.FILL);
        path.reset();
        path.addRect(cx - rw, ty, cx + rw, by, Path.Direction.CW);
        rf.set(cx - rw, by - rh, cx + rw, by + rh);
        path.addOval(rf, Path.Direction.CW);
        q.setShader(new LinearGradient(cx - rw, 0, cx + rw, 0,
                new int[]{0xFF010907, ac(acc, 0x30), 0xFF03100C, ac(acc, 0x30), 0xFF010907}, null, Shader.TileMode.CLAMP));
        b.drawPath(path, q);
        q.setShader(null);
        // floor glow
        q.setShader(new RadialGradient(cx, by, rw, new int[]{ac(acc, 0x70), ac(acc, 0x00)}, null, Shader.TileMode.CLAMP));
        rf.set(cx - rw, by - rh, cx + rw, by + rh);
        b.drawOval(rf, q);
        q.setShader(null);
        // lid (opening)
        q.setColor(0xFF020B09);
        rf.set(cx - rw, ty - rh, cx + rw, ty + rh);
        b.drawOval(rf, q);
        q.setShader(new RadialGradient(cx, ty, rw, new int[]{ac(acc, 0x40), ac(acc, 0x06)}, null, Shader.TileMode.CLAMP));
        b.drawOval(rf, q);
        q.setShader(null);
        // back rims
        glowArc(b, rf, 180, 180, acc, 1.6f * d);
        rf.set(cx - rw, by - rh, cx + rw, by + rh);
        glowArc(b, rf, 180, 180, acc, 1.0f * d);

        // ---- front layer: glass, edges and front rims
        Canvas f = new Canvas(layerFront);
        path.reset();
        path.moveTo(cx - rw, ty);
        path.lineTo(cx + rw, ty);
        path.lineTo(cx + rw, by);
        rf.set(cx - rw, by - rh, cx + rw, by + rh);
        path.arcTo(rf, 0, 180, false);
        path.lineTo(cx - rw, ty);
        path.close();
        q.setStyle(Paint.Style.FILL);
        q.setShader(new LinearGradient(cx - rw, 0, cx + rw, 0,
                new int[]{ac(acc, 0x46), ac(acc, 0x0C), ac(acc, 0x00), ac(acc, 0x12), ac(acc, 0x00), ac(acc, 0x0C), ac(acc, 0x46)},
                new float[]{0f, 0.1f, 0.3f, 0.42f, 0.55f, 0.9f, 1f}, Shader.TileMode.CLAMP));
        f.drawPath(path, q);
        q.setShader(new LinearGradient(0, ty, 0, by, new int[]{ac(acc, 0x22), ac(acc, 0x00), ac(acc, 0x18)}, null, Shader.TileMode.CLAMP));
        f.drawPath(path, q);
        q.setShader(null);
        // faint ring lines on the glass
        for (int k = 1; k <= 3; k++) {
            float y = ty + (by - ty) * k / 4f;
            rf.set(cx - rw, y - rh, cx + rw, y + rh);
            Paint g = new Paint(Paint.ANTI_ALIAS_FLAG);
            g.setStyle(Paint.Style.STROKE);
            g.setStrokeWidth(1f * d);
            g.setColor(ac(acc, 0x1E));
            f.drawArc(rf, 0, 180, false, g);
        }
        glowLine(f, cx - rw, ty, cx - rw, by, acc, 1.4f * d);
        glowLine(f, cx + rw, ty, cx + rw, by, acc, 1.4f * d);
        rf.set(cx - rw, ty - rh, cx + rw, ty + rh);
        glowArc(f, rf, 0, 180, acc, 2.0f * d);
        rf.set(cx - rw, by - rh, cx + rw, by + rh);
        glowArc(f, rf, 0, 180, acc, 2.0f * d);

        // analog face assets
        faceShader = new RadialGradient(cx, faceCy, faceR, new int[]{0xD8072019, 0xE6020807}, null, Shader.TileMode.CLAMP);
        tickMinor = new float[4 * 48];
        tickMajor = new float[4 * 12];
        int mi = 0, ma = 0;
        for (int i = 0; i < 60; i++) {
            double a = Math.toRadians(i * 6);
            float s = (float) Math.sin(a), co = (float) Math.cos(a);
            boolean major = i % 5 == 0;
            float r1 = faceR * 0.93f, r2 = faceR * (major ? 0.80f : 0.87f);
            float[] arr = major ? tickMajor : tickMinor;
            int idx = major ? ma : mi;
            arr[idx] = cx + s * r1; arr[idx + 1] = faceCy - co * r1;
            arr[idx + 2] = cx + s * r2; arr[idx + 3] = faceCy - co * r2;
            if (major) ma += 4; else mi += 4;
        }
    }

    // =============================================================================================
    // Drawing
    // =============================================================================================
    static float ease(float t) { return t * t * (3f - 2f * t); }

    @Override protected void onDraw(Canvas c) {
        if (w == 0 || h == 0) return;
        long ns = System.nanoTime();
        float dt = lastNs == 0 ? 0.016f : Math.min(0.05f, (ns - lastNs) / 1e9f);
        lastNs = ns;
        long now = System.currentTimeMillis();
        stepReveal(dt);
        float rv = ease(reveal);
        if (rv > 0.003f) {
            int acc = COLORS[Math.max(0, Math.min(COLORS.length - 1, store.accent()))];
            if (layerBack == null || acc != builtAccent || store.dim() != builtDim) buildLayers(acc);
            step(dt);
            int sc = -1;
            if (rv < 0.999f) {
                sc = c.saveLayerAlpha(0, 0, w, h, (int) (255 * rv));
                c.translate(0, (1f - rv) * h * 0.06f);
            }
            drawLauncher(c, now, acc);
            if (sc >= 0) c.restoreToCount(sc);
        }
        if (rv < 0.999f) {
            int acc = COLORS[Math.max(0, Math.min(COLORS.length - 1, store.accent()))];
            drawWall(c, now, acc, 1f - rv);
        }
        if (rv > 0.003f || reveal != revealTarget || touching) postInvalidateOnAnimation();
        else if (now - wallSince < 7000L) postInvalidateOnAnimation();      // hint pulse
        else postInvalidateDelayed(1000);                                 // idle on wallpaper: 1 redraw/sec (clock)
    }

    void stepReveal(float dt) {
        if (mode == MODE_REVEAL && touching) return;
        float sp = dt / 0.36f;
        if (reveal < revealTarget) reveal = Math.min(revealTarget, reveal + sp);
        else if (reveal > revealTarget) reveal = Math.max(revealTarget, reveal - sp);
    }

    void drawLauncher(Canvas c, long now, int acc) {
        c.drawBitmap(layerBack, 0, 0, null);
        drawClock(c, now, acc);

        float e = ease(trans);
        boolean tr = prev >= 0;
        // back halves of icons
        if (tr) drawPage(c, pages[prev], 1f - e, -transDir * e * 1.1f, false, acc);
        drawPage(c, pages[cur], tr ? e : 1f, tr ? transDir * (1f - e) * 1.1f : 0f, false, acc);
        c.drawBitmap(layerFront, 0, 0, null);
        // front halves
        if (tr) drawPage(c, pages[prev], 1f - e, -transDir * e * 1.1f, true, acc);
        drawPage(c, pages[cur], tr ? e : 1f, tr ? transDir * (1f - e) * 1.1f : 0f, true, acc);

        drawRadar(c, now, acc);
        drawChrome(c, now, acc, tr ? e : 1f);
    }

    /** Wallpaper screen: nothing but a small clock and a swipe-up hint, so the live wallpaper stays the star. */
    void drawWall(Canvas c, long now, int acc, float alpha) {
        if (alpha <= 0.01f) return;
        cal.setTimeInMillis(now);
        if (store.wallClock()) {
            boolean is24 = DateFormat.is24HourFormat(getContext());
            int H = cal.get(Calendar.HOUR_OF_DAY), M = cal.get(Calendar.MINUTE);
            int hh = is24 ? H : ((H + 11) % 12 + 1);
            String t = is24 ? String.format(Locale.US, "%02d:%02d", hh, M) : String.format(Locale.US, "%d:%02d", hh, M);
            String ds = new SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(cal.getTime()).toUpperCase(Locale.getDefault());
            textP.setTextAlign(Paint.Align.CENTER);
            textP.setTypeface(thin);
            textP.setTextSize(64 * d);
            textP.setLetterSpacing(0.02f);
            textP.setColor(ac(0xEFFFF6, (int) (235 * alpha)));
            textP.setShadowLayer(16 * d, 0, 0, ac(acc, (int) (200 * alpha)));
            c.drawText(t, cx, insetTop + 96 * d, textP);
            textP.setTypeface(medium);
            textP.setTextSize(12 * d);
            textP.setLetterSpacing(0.2f);
            textP.setColor(ac(0xFFFFFF, (int) (190 * alpha)));
            c.drawText(ds, cx, insetTop + 122 * d, textP);
            textP.clearShadowLayer();
            textP.setLetterSpacing(0f);
        }
        // swipe-up hint: two chevrons that pulse upward, then settle
        boolean pulse = now - wallSince < 7000L;
        float ph = pulse ? (now % 1500L) / 1500f : 0.5f;
        float base = h - insetBottom - 58 * d - reveal * 40 * d;
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(2.2f * d);
        for (int i = 0; i < 2; i++) {
            float f = pulse ? Math.max(0f, 1f - Math.abs(ph - i * 0.28f - 0.2f) * 2.6f) : 0.35f;
            p.setColor(ac(acc, (int) ((70 + 185 * f) * alpha)));
            float y = base - i * 11 * d - (pulse ? ph * 6 * d : 0);
            path.reset();
            path.moveTo(cx - 11 * d, y + 5 * d);
            path.lineTo(cx, y - 3 * d);
            path.lineTo(cx + 11 * d, y + 5 * d);
            c.drawPath(path, p);
        }
        p.setStrokeCap(Paint.Cap.BUTT);
        textP.setTextAlign(Paint.Align.CENTER);
        textP.setTypeface(medium);
        textP.setTextSize(9.5f * d);
        textP.setLetterSpacing(0.3f);
        textP.setColor(ac(0xFFFFFF, (int) (110 * alpha)));
        c.drawText("SWIPE UP", cx, base + 22 * d, textP);
        textP.setLetterSpacing(0f);
    }

    void step(float dt) {
        if (vaultOpen && vaultExpiresAt > 0L && System.currentTimeMillis() >= vaultExpiresAt) {
            // Keep the private cylinder session short even if the user leaves the launcher open.
            lockNow();
        }
        if (trans < 1f) {
            trans = Math.min(1f, trans + dt / 0.6f);
            if (trans >= 1f) {
                if (prev == VAULT && !vaultOpen) {
                    pages[VAULT].items.clear();
                    pages[VAULT].layout();
                    act.setSecure(false);
                }
                prev = -1;
            }
        }
        advance(pages[cur], dt, touching && mode == MODE_ROT);
        if (prev >= 0) advance(pages[prev], dt, false);
    }

    void advance(Page pg, float dt, boolean dragging) {
        if (dragging) return;
        if (Math.abs(pg.vel) > 0.8f) {
            pg.rot += pg.vel * dt;
            pg.vel *= (float) Math.exp(-2.6f * dt);
        } else {
            pg.vel = 0f;
            float t = Math.round(pg.rot / pg.grid) * pg.grid;
            pg.rot += (t - pg.rot) * (1f - (float) Math.exp(-10f * dt));
        }
        if (pg.rot > 60f || pg.rot < -60f) pg.rot %= (float) (2 * Math.PI);
    }

    void layoutPositions(Page pg, float extra) {
        int n = pg.items.size();
        float rowH = (bot - top) / pg.rows;
        float sz = Math.min(Math.min(rowH * 0.56f, rw * 0.30f), 62 * d);
        sz = Math.max(sz, 30 * d);
        iconSz = sz;
        for (int i = 0; i < n; i++) {
            double a = i * pg.grid + pg.rot + extra;
            float s = (float) Math.sin(a), co = (float) Math.cos(a);
            int row = i % pg.rows;
            float yc = top + (bot - top) * (row + 0.5f) / pg.rows;
            pg.px[i] = cx + s * rw * 0.93f;
            pg.py[i] = yc + co * rh * 0.93f;
            pg.pz[i] = co;
            pg.ps[i] = sz * (0.66f + 0.34f * (co + 1f) / 2f);
        }
    }

    void drawPage(Canvas c, Page pg, float alpha, float extra, boolean front, int acc) {
        if (alpha <= 0.01f) return;
        if (!front) layoutPositions(pg, extra);
        int n = pg.items.size();
        boolean isCur = pg == pages[cur];
        for (int i = 0; i < n; i++) {
            boolean isFront = pg.pz[i] >= 0f;
            if (isFront != front) continue;
            drawItem(c, pg, i, alpha, isCur && i == pressed && prev < 0, acc);
        }
        if (front && n == 0 && isCur) drawEmptyHint(c, alpha, acc);
    }

    void drawEmptyHint(Canvas c, float alpha, int acc) {
        String l1, l2;
        if (cur == HOME) { l1 = "HOME IS EMPTY"; l2 = "Open ALL APPS and long-press an app to pin it"; }
        else if (cur == VAULT) { l1 = "VAULT IS EMPTY"; l2 = "Long-press an app in ALL APPS and choose Hide"; }
        else { l1 = "LOADING APPS…"; l2 = ""; }
        textP.setTextAlign(Paint.Align.CENTER);
        textP.setTypeface(medium);
        textP.setTextSize(15 * d);
        textP.setLetterSpacing(0.16f);
        textP.setColor(ac(acc, (int) (230 * alpha)));
        c.drawText(l1, cx, (top + bot) / 2f, textP);
        textP.setTypeface(Typeface.DEFAULT);
        textP.setTextSize(12 * d);
        textP.setLetterSpacing(0.02f);
        textP.setColor(ac(0xFFFFFF, (int) (150 * alpha)));
        c.drawText(l2, cx, (top + bot) / 2f + 24 * d, textP);
        textP.setLetterSpacing(0f);
    }

    void drawItem(Canvas c, Page pg, int i, float alpha, boolean press, int acc) {
        AppItem it = pg.items.get(i);
        float z = pg.pz[i];
        float a = (z >= 0f ? 0.72f + 0.28f * z : 0.07f + 0.22f * (z + 1f)) * alpha;
        if (a <= 0.01f) return;
        float sz = pg.ps[i] * (press ? 1.14f : 1f);
        float sx = 0.28f + 0.72f * Math.abs(z);
        float x = pg.px[i], y = pg.py[i];
        float tw = sz * sx;
        if (z > 0.05f) {
            rf.set(x - tw / 2f - 3 * d * sx, y - sz / 2f - 3 * d, x + tw / 2f + 3 * d * sx, y + sz / 2f + 3 * d);
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(ac(acc, (int) ((press ? 0x48 : 0x1C) * a)));
            c.drawRoundRect(rf, sz * 0.27f, sz * 0.27f, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1f * d);
            p.setColor(ac(acc, (int) ((press ? 0xFF : 0x70) * a)));
            c.drawRoundRect(rf, sz * 0.27f, sz * 0.27f, p);
        }
        bmpP.setAlpha((int) (255 * a));
        dst.set(x - tw / 2f, y - sz / 2f, x + tw / 2f, y + sz / 2f);
        c.drawBitmap(it.icon, null, dst, bmpP);
        if (z > 0.4f) {
            String lab = it.label.length() > 11 ? it.label.substring(0, 10) + "…" : it.label;
            textP.setTextAlign(Paint.Align.CENTER);
            textP.setTypeface(Typeface.DEFAULT);
            textP.setTextSize(10.5f * d * (0.8f + 0.2f * z));
            textP.setLetterSpacing(0f);
            textP.setColor(ac(0xFFFFFF, (int) (255 * a * Math.min(1f, (z - 0.4f) * 2.5f))));
            textP.setShadowLayer(3 * d, 0, 1 * d, 0xFF000000);
            c.drawText(lab, x, y + sz / 2f + 14 * d * (0.8f + 0.2f * z), textP);
            textP.clearShadowLayer();
        }
    }

    void drawRadar(Canvas c, long now, int acc) {
        float ang = (now % 5000L) / 5000f * 360f;
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(2.2f * d);
        rf.set(cx - rw * 1.14f, by - rh * 1.14f, cx + rw * 1.14f, by + rh * 1.14f);
        for (int k = 0; k < 12; k++) {
            p.setColor(ac(acc, (int) (190 * (1f - k / 12f))));
            c.drawArc(rf, ang - k * 6f, 6.5f, false, p);
        }
        float ang2 = 360f - ang;
        rf.set(cx - rw * 0.99f, ty - rh * 0.99f, cx + rw * 0.99f, ty + rh * 0.99f);
        for (int k = 0; k < 12; k++) {
            p.setColor(ac(acc, (int) (150 * (1f - k / 12f))));
            c.drawArc(rf, ang2 + k * 6f, 6.5f, false, p);
        }
        p.setStrokeCap(Paint.Cap.BUTT);
    }

    // ---- clock -------------------------------------------------------------------------------
    void drawClock(Canvas c, long now, int acc) {
        cal.setTimeInMillis(now);
        int H = cal.get(Calendar.HOUR_OF_DAY), M = cal.get(Calendar.MINUTE), S = cal.get(Calendar.SECOND), ms = cal.get(Calendar.MILLISECOND);
        float sec = S + ms / 1000f;
        float bob = (float) Math.sin(now / 1000.0 * 1.3) * 3.5f * d;
        drawOrbit(c, sec, acc);
        if (store.analog()) drawAnalog(c, H, M, sec, acc, bob);
        else drawDigital(c, H, M, S, ms, acc, bob);
        int dk = cal.get(Calendar.YEAR) * 400 + cal.get(Calendar.DAY_OF_YEAR);
        if (dk != dateKey) {
            dateKey = dk;
            dateStr = new SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(cal.getTime()).toUpperCase(Locale.getDefault());
        }
    }

    /** 60 ticks laid on a perspective ring; elapsed seconds stay lit and a light orb glides around. */
    void drawOrbit(Canvas c, float sec, int acc) {
        float k = orbRy / orbRx;
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < 60; i++) {
            double a = Math.toRadians(i * 6);
            float s = (float) Math.sin(a), co = (float) Math.cos(a);
            float front = (1f - co) / 2f;
            boolean major = i % 5 == 0;
            float r1 = orbRx * 0.97f, r2 = orbRx * (major ? 0.86f : 0.92f);
            float delta = sec - i;
            float br;
            if (delta >= 0f) br = 0.28f + 0.72f * Math.max(0f, 1f - delta / 12f);
            else br = 0.10f + 0.90f * Math.max(0f, 1f - (delta + 60f) / 12f);
            p.setColor(ac(acc, (int) (255 * Math.min(1f, br) * (0.40f + 0.60f * front))));
            p.setStrokeWidth((major ? 2.6f : 1.6f) * d * (0.7f + 0.5f * front));
            c.drawLine(cx + r1 * s, orbCy - r1 * k * co, cx + r2 * s, orbCy - r2 * k * co, p);
        }
        // orb
        double a = Math.toRadians(sec * 6.0);
        float s = (float) Math.sin(a), co = (float) Math.cos(a);
        float front = (1f - co) / 2f;
        float ox = cx + orbRx * 0.97f * s, oy = orbCy - orbRy * 0.97f * co;
        float r = (4.5f + 3f * front) * d;
        p.setStyle(Paint.Style.FILL);
        p.setColor(ac(acc, 0x16)); c.drawCircle(ox, oy, r * 4.6f, p);
        p.setColor(ac(acc, 0x30)); c.drawCircle(ox, oy, r * 3.0f, p);
        p.setColor(ac(acc, 0x70)); c.drawCircle(ox, oy, r * 1.8f, p);
        p.setColor(ac(acc, 0xE0)); c.drawCircle(ox, oy, r * 1.15f, p);
        p.setColor(0xFFFFFFFF); c.drawCircle(ox, oy, r * 0.62f, p);
        p.setStrokeCap(Paint.Cap.BUTT);
    }

    void drawDigital(Canvas c, int H, int M, int S, int ms, int acc, float bob) {
        boolean is24 = DateFormat.is24HourFormat(getContext());
        int key = H * 60 + M + (is24 ? 100000 : 0);
        if (key != timeKey) {
            timeKey = key;
            int hh = is24 ? H : ((H + 11) % 12 + 1);
            timeStr = is24 ? String.format(Locale.US, "%02d:%02d", hh, M) : String.format(Locale.US, "%d:%02d", hh, M);
            ampm = is24 ? "" : (H < 12 ? "AM" : "PM");
        }
        if (S != secKey) { secKey = S; secStr = String.format(Locale.US, "%02d", S); }
        textP.setTypeface(thin);
        textP.setTextSize(78 * d);
        textP.setLetterSpacing(0.02f);
        textP.setTextAlign(Paint.Align.LEFT);
        float tw = textP.measureText(timeStr);
        float base = orbCy - orbRy - 32 * d + bob;
        textP.setColor(0xFFEFFFF6);
        textP.setShadowLayer(20 * d, 0, 0, ac(acc, 0xE0));
        c.drawText(timeStr, cx - tw / 2f, base, textP);
        // seconds count, sliding in every tick
        float e = ease(Math.min(1f, ms / 280f));
        textP.setTypeface(medium);
        textP.setTextSize(27 * d);
        textP.setLetterSpacing(0.04f);
        textP.setColor(ac(acc, (int) (110 + 145 * e)));
        textP.setShadowLayer(12 * d, 0, 0, ac(acc, 0xD0));
        float sx = cx + tw / 2f + 10 * d;
        c.drawText(secStr, sx, base - 3 * d - (1f - e) * 9 * d, textP);
        textP.clearShadowLayer();
        if (!ampm.isEmpty()) {
            textP.setTextSize(11 * d);
            textP.setLetterSpacing(0.16f);
            textP.setColor(ac(0xFFFFFF, 170));
            c.drawText(ampm, sx + 1 * d, base - 38 * d, textP);
        }
        textP.setLetterSpacing(0f);
    }

    void drawAnalog(Canvas c, int H, int M, float sec, int acc, float bob) {
        float fx = cx, fy = faceCy, R = faceR;
        c.save();
        c.translate(0, bob);
        // soft glow under the face
        p.setStyle(Paint.Style.FILL);
        p.setShader(null);
        p.setColor(ac(acc, 0x12)); c.drawCircle(fx, fy, R * 1.22f, p);
        p.setColor(ac(acc, 0x1C)); c.drawCircle(fx, fy, R * 1.10f, p);
        p.setShader(faceShader);
        c.drawCircle(fx, fy, R, p);
        p.setShader(null);
        // rim
        p.setStyle(Paint.Style.STROKE);
        for (int k = 3; k >= 1; k--) {
            p.setStrokeWidth((1.6f + k * 3.2f) * d);
            p.setColor(ac(acc, 0x16));
            c.drawCircle(fx, fy, R, p);
        }
        p.setStrokeWidth(1.8f * d);
        p.setColor(ac(acc, 0xF0));
        c.drawCircle(fx, fy, R, p);
        p.setStrokeWidth(1f * d);
        p.setColor(ac(acc, 0x30));
        c.drawCircle(fx, fy, R * 0.60f, p);
        // ticks
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(1.1f * d);
        p.setColor(ac(0xFFFFFF, 0x66));
        c.drawLines(tickMinor, p);
        p.setStrokeWidth(2.6f * d);
        p.setColor(ac(acc, 0xFF));
        c.drawLines(tickMajor, p);
        // numerals
        textP.setTypeface(medium);
        textP.setTextAlign(Paint.Align.CENTER);
        textP.setTextSize(R * 0.15f);
        textP.setLetterSpacing(0f);
        textP.setColor(ac(0xFFFFFF, 0xD0));
        float nr = R * 0.66f, off = R * 0.15f * 0.36f;
        c.drawText("12", fx, fy - nr + off, textP);
        c.drawText("3", fx + nr, fy + off, textP);
        c.drawText("6", fx, fy + nr + off, textP);
        c.drawText("9", fx - nr, fy + off, textP);

        float hourA = ((H % 12) + (M + sec / 60f) / 60f) * 30f;
        float minA = (M + sec / 60f) * 6f;
        float secA = sec * 6f;
        // second hand sweep trail
        p.setStyle(Paint.Style.FILL);
        rf.set(fx - R * 0.9f, fy - R * 0.9f, fx + R * 0.9f, fy + R * 0.9f);
        for (int k = 0; k < 14; k++) {
            p.setColor(ac(acc, (int) (70 * (1f - k / 14f))));
            c.drawArc(rf, secA - 90f - (k + 1) * 4f, 4.3f, true, p);
        }
        p.setStyle(Paint.Style.STROKE);
        // hour & minute
        c.save(); c.rotate(hourA, fx, fy);
        p.setStrokeWidth(6.5f * d); p.setColor(ac(acc, 0x30)); c.drawLine(fx, fy + R * 0.08f, fx, fy - R * 0.50f, p);
        p.setStrokeWidth(4.2f * d); p.setColor(0xFFF2FFF8); c.drawLine(fx, fy + R * 0.08f, fx, fy - R * 0.50f, p);
        c.restore();
        c.save(); c.rotate(minA, fx, fy);
        p.setStrokeWidth(5.4f * d); p.setColor(ac(acc, 0x30)); c.drawLine(fx, fy + R * 0.10f, fx, fy - R * 0.76f, p);
        p.setStrokeWidth(3f * d); p.setColor(0xFFF2FFF8); c.drawLine(fx, fy + R * 0.10f, fx, fy - R * 0.76f, p);
        c.restore();
        // second hand (continuous, per-millisecond)
        c.save(); c.rotate(secA, fx, fy);
        p.setStrokeWidth(7f * d); p.setColor(ac(acc, 0x28)); c.drawLine(fx, fy + R * 0.20f, fx, fy - R * 0.90f, p);
        p.setStrokeWidth(3.6f * d); p.setColor(ac(acc, 0x70)); c.drawLine(fx, fy + R * 0.20f, fx, fy - R * 0.90f, p);
        p.setStrokeWidth(1.5f * d); p.setColor(ac(acc, 0xFF)); c.drawLine(fx, fy + R * 0.20f, fx, fy - R * 0.90f, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(ac(acc, 0x60)); c.drawCircle(fx, fy - R * 0.90f, 6 * d, p);
        p.setColor(0xFFFFFFFF); c.drawCircle(fx, fy - R * 0.90f, 2.4f * d, p);
        c.restore();
        // hub
        p.setStyle(Paint.Style.FILL);
        p.setColor(ac(acc, 0x50)); c.drawCircle(fx, fy, 9 * d, p);
        p.setColor(ac(acc, 0xFF)); c.drawCircle(fx, fy, 4.6f * d, p);
        p.setColor(0xFF021210); c.drawCircle(fx, fy, 1.8f * d, p);
        p.setStrokeCap(Paint.Cap.BUTT);
        c.restore();
    }

    // ---- chrome (date, gear, page labels) ----------------------------------------------------
    void drawChrome(Canvas c, long now, int acc, float e) {
        textP.setTypeface(medium);
        textP.setTextAlign(Paint.Align.LEFT);
        textP.setTextSize(12 * d);
        textP.setLetterSpacing(0.18f);
        textP.setColor(ac(0xFFFFFF, 0xC0));
        c.drawText(dateStr, 46 * d, insetTop + 34 * d, textP);
        // search glass (tap = app search)
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(1.8f * d);
        p.setColor(ac(acc, 0xE0));
        c.drawCircle(27 * d, insetTop + 29.5f * d, 5.5f * d, p);
        c.drawLine(31 * d, insetTop + 33.5f * d, 35 * d, insetTop + 37.5f * d, p);
        p.setStrokeCap(Paint.Cap.BUTT);
        // gear (three dots)
        p.setStyle(Paint.Style.FILL);
        p.setColor(ac(acc, 0xE0));
        for (int i = 0; i < 3; i++) c.drawCircle(w - 26 * d, insetTop + (22 + i * 10) * d, 2.4f * d, p);

        // page labels
        int count = vaultOpen || cur == VAULT || prev == VAULT ? 3 : 2;
        String[] names = {"HOME", "ALL APPS", "VAULT"};
        float ly = Math.min(by + rh + 54 * d, h - insetBottom - 40 * d);
        textP.setTextSize(12.5f * d);
        textP.setLetterSpacing(0.16f);
        float gap = 26 * d, total = 0;
        float[] ws = new float[3];
        for (int i = 0; i < count; i++) { ws[i] = textP.measureText(names[i]); total += ws[i]; }
        total += gap * (count - 1);
        float x = cx - total / 2f;
        for (int i = 0; i < count; i++) {
            boolean act1 = i == cur;
            boolean vault = i == VAULT;
            int col = vault ? 0xFFFF5566 : acc;
            textP.setColor(act1 ? ac(col, 0xFF) : ac(0xFFFFFF, 0x66));
            if (act1) textP.setShadowLayer(10 * d, 0, 0, ac(col, 0xC0));
            c.drawText(names[i], x, ly, textP);
            textP.clearShadowLayer();
            if (act1) {
                p.setColor(ac(col, 0xFF));
                rf.set(x + ws[i] / 2f - 14 * d, ly + 9 * d, x + ws[i] / 2f + 14 * d, ly + 11 * d);
                c.drawRoundRect(rf, d, d, p);
            }
            labelRects[i].set(x - 10 * d, ly - 24 * d, x + ws[i] + 10 * d, ly + 20 * d);
            x += ws[i] + gap;
        }
        // count
        textP.setTypeface(Typeface.DEFAULT);
        textP.setTextSize(10.5f * d);
        textP.setLetterSpacing(0.1f);
        textP.setColor(ac(0xFFFFFF, 0x70));
        textP.setTextAlign(Paint.Align.CENTER);
        c.drawText(pages[cur].items.size() + (pages[cur].items.size() == 1 ? " APP" : " APPS"), cx, ly + 30 * d, textP);
        textP.setLetterSpacing(0f);
    }

    // =============================================================================================
    // Touch
    // =============================================================================================
    int hit(float x, float y) {
        Page pg = pages[cur];
        int best = -1;
        float bd = Float.MAX_VALUE;
        int n = Math.min(pg.items.size(), pg.px.length);
        for (int i = 0; i < n; i++) {
            if (pg.pz[i] < 0.05f) continue;
            float s = pg.ps[i];
            float dx = Math.abs(x - pg.px[i]);
            float dy = y - (pg.py[i] + s * 0.12f);
            if (dx < s * 0.62f && Math.abs(dy) < s * 0.80f) {
                float dd = dx * dx + dy * dy;
                if (dd < bd) { bd = dd; best = i; }
            }
        }
        return best;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        if (vaultOpen && e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            vaultExpiresAt = System.currentTimeMillis() + VAULT_IDLE_TIMEOUT_MS;
        }
        if (vt == null) vt = VelocityTracker.obtain();
        vt.addMovement(e);
        float slop = 8 * d;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = x;
                downY = y;
                mode = MODE_NONE;
                lpFired = false;
                touching = true;
                zone = (y > ty - rh * 1.1f && y < by + rh * 0.9f) ? 1 : 0;
                pressed = (prev < 0 && revealTarget >= 1f && reveal > 0.95f) ? hit(x, y) : -1;
                pages[cur].vel = 0f;
                removeCallbacks(lpRun);
                postDelayed(lpRun, 480);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (mode == MODE_NONE) {
                    float ax = Math.abs(x - downX), ay = Math.abs(y - downY);
                    if (Math.max(ax, ay) > slop) {
                        removeCallbacks(lpRun);
                        pressed = -1;
                        if (revealTarget < 1f && reveal < 0.95f) {
                            // wallpaper screen: only a vertical drag (swipe up) does anything
                            mode = (ay > ax && y < downY) ? MODE_REVEAL : MODE_NAV;
                            if (mode == MODE_NAV) mode = MODE_NONE - 1; // ignored sideways drag
                        } else {
                            mode = ay > ax * 1.2f ? MODE_VERT : (zone == 1 ? MODE_ROT : MODE_NAV);
                        }
                    }
                }
                if (mode == MODE_REVEAL) reveal = Math.max(0f, Math.min(1f, (downY - y) / (h * 0.32f)));
                if (mode == MODE_ROT) {
                    pages[cur].rot += (x - lastX) / (rw * 0.93f);
                    int slot = Math.round(pages[cur].rot / pages[cur].grid);
                    if (slot != lastSlot) { lastSlot = slot; performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); }
                }
                if (mode != MODE_NONE) lastX = x;
                return true;
            case MotionEvent.ACTION_UP: {
                removeCallbacks(lpRun);
                touching = false;
                vt.computeCurrentVelocity(1000);
                float vx = vt.getXVelocity(), vy = vt.getYVelocity();
                vt.recycle();
                vt = null;
                if (lpFired) { pressed = -1; return true; }
                float dx = x - downX, dy = y - downY;
                if (mode == MODE_REVEAL) {
                    revealTarget = (reveal > 0.28f || vy < -900 * d) && vy < 1200 * d ? 1f : 0f;
                    if (revealTarget == 0f) wallSince = System.currentTimeMillis();
                    pressed = -1;
                    return true;
                }
                if (mode < MODE_NONE) { pressed = -1; return true; }
                if (mode == MODE_NONE && revealTarget < 1f && reveal < 0.95f) {
                    sendWallTap(x, y);   // let the live wallpaper react to taps
                    return true;
                }
                if (mode == MODE_NONE) {
                    lastRight = 0L;
                    if (pressed >= 0 && pressed < pages[cur].items.size()) {
                        Page pg = pages[cur];
                        act.launch(pg.items.get(pressed), pg.px[pressed], pg.py[pressed]);
                    } else {
                        tapChrome(x, y);
                    }
                } else if (mode == MODE_ROT) {
                    if (dx < 0f || vx < -800 * d) {
                        lastRight = 0L;
                    } else if (dx > 60 * d || vx > 800 * d) {
                        if (registerRightSwipeForVault()) {
                            pages[cur].vel = 0f;
                            pressed = -1;
                            return true;
                        }
                    }
                    float v = vx / (rw * 0.93f);
                    pages[cur].vel = Math.max(-16f, Math.min(16f, v));
                } else if (mode == MODE_VERT) {
                    lastRight = 0L;
                    if (dy < -50 * d || vy < -900 * d) goTo(cur + 1);
                    else if (dy > 50 * d || vy > 900 * d) { if (cur == HOME) hideToWall(); else goTo(cur - 1); }
                } else if (mode == MODE_NAV) {
                    if (dx < -60 * d || vx < -800 * d) { lastRight = 0L; goTo(cur + 1); }
                    else if (dx > 60 * d || vx > 800 * d) onRightSwipe();
                    else lastRight = 0L;
                }
                pressed = -1;
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(lpRun);
                touching = false;
                if (mode == MODE_REVEAL) revealTarget = reveal > 0.5f ? 1f : 0f;
                if (vt != null) { vt.recycle(); vt = null; }
                pressed = -1;
                return true;
            default:
                return true;
        }
    }

    void tapChrome(float x, float y) {
        if (x > w - 64 * d && y < insetTop + 64 * d) { act.showSettings(); return; }
        if (x < 90 * d && y < insetTop + 64 * d) { act.showSearch(); return; }
        int count = vaultOpen ? 3 : 2;
        for (int i = 0; i < count; i++) {
            if (labelRects[i].contains(x, y)) { goTo(i); return; }
        }
    }
}

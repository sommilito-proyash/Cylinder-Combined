package com.cylinderradar.launcher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;

import java.util.List;

/** Draws equirectangular textures (lon -180..180 -> x, lat 90..-90 -> y) from real country polygons. */
final class GlobeArt {
    private GlobeArt() {}

    private static final int[] PALETTE = {
        0xFF2F8F6A, 0xFF4E9F5B, 0xFF8AA650, 0xFFB89F4A, 0xFFC4824A, 0xFF4B9AA8,
        0xFF5E86C4, 0xFF8F7BC2, 0xFFC27A98, 0xFF6BB58C, 0xFFD0A45E, 0xFFB5695B
    };

    private static Path pathOf(GeoShape s, int w, int h) {
        Path p = new Path();
        p.setFillType(Path.FillType.EVEN_ODD);
        for (float[] r : s.rings) {
            p.moveTo((r[0] + 180f) / 360f * w, (90f - r[1]) / 180f * h);
            for (int i = 2; i < r.length; i += 2) p.lineTo((r[i] + 180f) / 360f * w, (90f - r[i + 1]) / 180f * h);
            p.close();
        }
        return p;
    }

    /** Greedy colouring so neighbouring countries (overlapping bounding boxes) get different colours. */
    static int[] colourIndexes(List<GeoShape> w) {
        int n = w.size(); int[] col = new int[n];
        for (int i = 0; i < n; i++) {
            GeoShape a = w.get(i); boolean[] used = new boolean[PALETTE.length];
            for (int j = 0; j < i; j++) {
                GeoShape b = w.get(j);
                if (a.minLon <= b.maxLon + .3f && a.maxLon >= b.minLon - .3f && a.minLat <= b.maxLat + .3f && a.maxLat >= b.minLat - .3f) used[col[j]] = true;
            }
            int pick = -1;
            for (int k = 0; k < PALETTE.length; k++) { int c = (i * 5 + k) % PALETTE.length; if (!used[c]) { pick = c; break; } }
            col[i] = pick >= 0 ? pick : i % PALETTE.length;
        }
        return col;
    }

    static Bitmap buildWorldBitmap(List<GeoShape> world, int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint ocean = new Paint(Paint.ANTI_ALIAS_FLAG);
        ocean.setShader(new LinearGradient(0, 0, 0, h, new int[]{0xFF0A2C52, 0xFF0B3A66, 0xFF0A2C52}, null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, ocean);
        Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
        grid.setStyle(Paint.Style.STROKE); grid.setStrokeWidth(1f); grid.setColor(0x2278C8E8);
        for (int lon = -180; lon <= 180; lon += 30) { float x = (lon + 180f) / 360f * w; c.drawLine(x, 0, x, h, grid); }
        for (int lat = -60; lat <= 60; lat += 30) { float y = (90f - lat) / 180f * h; c.drawLine(0, y, w, y, grid); }
        int[] ci = colourIndexes(world);
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG); fill.setStyle(Paint.Style.FILL);
        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG); line.setStyle(Paint.Style.STROKE); line.setStrokeWidth(w / 1400f + 0.6f);
        line.setColor(0xCCF2F7FA); line.setStrokeJoin(Paint.Join.ROUND);
        for (int i = 0; i < world.size(); i++) {
            GeoShape s = world.get(i);
            if ("Antarctica".equals(s.name)) fill.setColor(0xFFDDE8EE); else fill.setColor(PALETTE[ci[i]]);
            Path p = pathOf(s, w, h);
            c.drawPath(p, fill); c.drawPath(p, line);
        }
        return b;
    }

    /** Transparent texture holding only the selected country in opaque gold (the GPU draws it slightly raised). */
    static Bitmap buildHighlightBitmap(GeoShape s, int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        b.eraseColor(Color.TRANSPARENT);
        Canvas c = new Canvas(b);
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG); fill.setStyle(Paint.Style.FILL); fill.setColor(0xFFFFC83D);
        Path p = pathOf(s, w, h);
        c.drawPath(p, fill);
        Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG); edge.setStyle(Paint.Style.STROKE); edge.setStrokeWidth(2.5f); edge.setColor(0xFFFFF3C4);
        c.drawPath(p, edge);
        return b;
    }
}

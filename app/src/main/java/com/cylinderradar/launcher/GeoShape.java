package com.cylinderradar.launcher;

import java.util.ArrayList;
import java.util.List;

/** One administrative shape (country / division / district): all rings of all polygons, even-odd filled. */
final class GeoShape {
    String id = "", name = "", iso = "", group = "";
    /** Each ring is interleaved lon,lat,lon,lat... (closed or open). */
    final List<float[]> rings = new ArrayList<>();
    float minLon = 1e9f, minLat = 1e9f, maxLon = -1e9f, maxLat = -1e9f;

    void addRing(float[] r) {
        if (r == null || r.length < 6) return;
        rings.add(r);
        for (int i = 0; i < r.length; i += 2) {
            float x = r[i], y = r[i + 1];
            if (x < minLon) minLon = x; if (x > maxLon) maxLon = x;
            if (y < minLat) minLat = y; if (y > maxLat) maxLat = y;
        }
    }
    boolean isEmpty() { return rings.isEmpty(); }
    float bboxArea() { return (maxLon - minLon) * (maxLat - minLat); }
    int pointCount() { int n = 0; for (float[] r : rings) n += r.length / 2; return n; }

    /** Even-odd point-in-polygon over all rings (holes handled automatically). */
    boolean contains(double lon, double lat) {
        if (lon < minLon || lon > maxLon || lat < minLat || lat > maxLat) return false;
        boolean inside = false;
        for (float[] r : rings) {
            int n = r.length / 2;
            for (int i = 0, j = n - 1; i < n; j = i++) {
                double xi = r[2 * i], yi = r[2 * i + 1], xj = r[2 * j], yj = r[2 * j + 1];
                if ((yi > lat) != (yj > lat) && lon < (xj - xi) * (lat - yi) / (yj - yi) + xi) inside = !inside;
            }
        }
        return inside;
    }

    /** Approximate centroid (bbox centre of the largest ring) used to focus the camera. */
    double[] focusPoint() {
        float[] best = null; double bestA = -1;
        for (float[] r : rings) {
            double a = ringBoxArea(r);
            if (a > bestA) { bestA = a; best = r; }
        }
        if (best == null) return new double[]{0, 0};
        double x0 = 1e9, x1 = -1e9, y0 = 1e9, y1 = -1e9;
        for (int i = 0; i < best.length; i += 2) { x0 = Math.min(x0, best[i]); x1 = Math.max(x1, best[i]); y0 = Math.min(y0, best[i + 1]); y1 = Math.max(y1, best[i + 1]); }
        return new double[]{(y0 + y1) / 2, (x0 + x1) / 2};
    }
    private static double ringBoxArea(float[] r) {
        double x0 = 1e9, x1 = -1e9, y0 = 1e9, y1 = -1e9;
        for (int i = 0; i < r.length; i += 2) { x0 = Math.min(x0, r[i]); x1 = Math.max(x1, r[i]); y0 = Math.min(y0, r[i + 1]); y1 = Math.max(y1, r[i + 1]); }
        return (x1 - x0) * (y1 - y0);
    }
}

package com.cylinderradar.launcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Decodes TopoJSON (world-atlas) and GeoJSON (geoBoundaries) into {@link GeoShape} lists. No Android dependencies. */
final class GeoCodec {
    private GeoCodec() {}

    /** Auto-detects Topology vs FeatureCollection. */
    static List<GeoShape> decode(String json, double simplifyDeg) {
        Map<String, Object> root = MiniJson.map(MiniJson.parse(json));
        if (root == null) throw new IllegalArgumentException("root is not an object");
        String type = MiniJson.str(root.get("type"));
        if ("Topology".equals(type)) return decodeTopology(root, simplifyDeg);
        if ("FeatureCollection".equals(type) || "Feature".equals(type)) return decodeGeoJson(root, simplifyDeg);
        throw new IllegalArgumentException("unsupported type: " + type);
    }

    // ------------------------------------------------------------------ TopoJSON
    @SuppressWarnings("unchecked")
    private static List<GeoShape> decodeTopology(Map<String, Object> root, double eps) {
        double sx = 1, sy = 1, tx = 0, ty = 0;
        Map<String, Object> tr = MiniJson.map(root.get("transform"));
        if (tr != null) {
            double[] sc = (double[]) tr.get("scale"), tl = (double[]) tr.get("translate");
            sx = sc[0]; sy = sc[1]; tx = tl[0]; ty = tl[1];
        }
        List<Object> arcsRaw = MiniJson.list(root.get("arcs"));
        if (arcsRaw == null) throw new IllegalArgumentException("topology has no arcs");
        double[][] arcs = new double[arcsRaw.size()][];
        boolean quantized = tr != null;
        for (int i = 0; i < arcs.length; i++) {
            List<Object> pts = MiniJson.list(arcsRaw.get(i));
            double[] a = new double[pts.size() * 2];
            double x = 0, y = 0;
            for (int k = 0; k < pts.size(); k++) {
                double[] pt = (double[]) pts.get(k);
                if (quantized) { x += pt[0]; y += pt[1]; a[2 * k] = x * sx + tx; a[2 * k + 1] = y * sy + ty; }
                else { a[2 * k] = pt[0]; a[2 * k + 1] = pt[1]; }
            }
            arcs[i] = a;
        }
        arcsRaw = null;

        Map<String, Object> objects = MiniJson.map(root.get("objects"));
        Map<String, Object> obj = null;
        if (objects != null) {
            obj = MiniJson.map(objects.get("countries"));
            if (obj == null && !objects.isEmpty()) obj = MiniJson.map(objects.values().iterator().next());
        }
        if (obj == null) throw new IllegalArgumentException("topology has no objects");
        List<Object> geoms = MiniJson.list(obj.get("geometries"));
        List<GeoShape> out = new ArrayList<>();
        if (geoms == null) return out;
        for (Object go : geoms) {
            Map<String, Object> g = MiniJson.map(go);
            if (g == null) continue;
            GeoShape sh = new GeoShape();
            sh.id = MiniJson.str(g.get("id")) == null ? "" : MiniJson.str(g.get("id"));
            Map<String, Object> props = MiniJson.map(g.get("properties"));
            if (props != null && props.get("name") != null) sh.name = MiniJson.str(props.get("name"));
            String t = MiniJson.str(g.get("type"));
            Object a = g.get("arcs");
            if ("Polygon".equals(t)) {
                for (Object ring : MiniJson.list(a)) sh.addRing(buildRing(arcs, ring, eps));
            } else if ("MultiPolygon".equals(t)) {
                for (Object poly : MiniJson.list(a)) for (Object ring : MiniJson.list(poly)) sh.addRing(buildRing(arcs, ring, eps));
            } else if ("GeometryCollection".equals(t)) {
                continue;
            }
            if (!sh.isEmpty()) out.add(sh);
        }
        return out;
    }

    /** ring = double[] (or empty List) of arc indices; negative index n means arc ~n reversed. */
    private static float[] buildRing(double[][] arcs, Object ringObj, double eps) {
        double[] idx;
        if (ringObj instanceof double[]) idx = (double[]) ringObj; else return null;
        int total = 0;
        for (double d : idx) { int i = (int) d; double[] a = arcs[i < 0 ? ~i : i]; total += a.length / 2; }
        double[] pts = new double[total * 2]; int n = 0;
        for (int q = 0; q < idx.length; q++) {
            int i = (int) idx[q]; boolean rev = i < 0; double[] a = arcs[rev ? ~i : i];
            int cnt = a.length / 2;
            for (int k = 0; k < cnt; k++) {
                int src = rev ? cnt - 1 - k : k;
                if (q > 0 && k == 0) continue;              // shared end-point with previous arc
                pts[2 * n] = a[2 * src]; pts[2 * n + 1] = a[2 * src + 1]; n++;
            }
        }
        return simplify(pts, n, eps);
    }

    // ------------------------------------------------------------------ GeoJSON
    private static List<GeoShape> decodeGeoJson(Map<String, Object> root, double eps) {
        List<GeoShape> out = new ArrayList<>();
        List<Object> feats;
        if ("Feature".equals(MiniJson.str(root.get("type")))) { feats = new ArrayList<>(); feats.add(root); }
        else feats = MiniJson.list(root.get("features"));
        if (feats == null) return out;
        for (Object fo : feats) {
            Map<String, Object> f = MiniJson.map(fo);
            if (f == null) continue;
            Map<String, Object> g = MiniJson.map(f.get("geometry"));
            if (g == null) continue;
            GeoShape sh = new GeoShape();
            Map<String, Object> pr = MiniJson.map(f.get("properties"));
            if (pr != null) {
                sh.name = nz(MiniJson.str(pr.get("shapeName")), MiniJson.str(pr.get("name")));
                sh.id = nz(MiniJson.str(pr.get("shapeID")), MiniJson.str(pr.get("id")));
                sh.iso = nz(MiniJson.str(pr.get("shapeISO")), "");
                sh.group = nz(MiniJson.str(pr.get("shapeGroup")), "");
            }
            String t = MiniJson.str(g.get("type"));
            List<Object> coords = MiniJson.list(g.get("coordinates"));
            if (coords == null) continue;
            if ("Polygon".equals(t)) addPolygon(sh, coords, eps);
            else if ("MultiPolygon".equals(t)) for (Object poly : coords) addPolygon(sh, MiniJson.list(poly), eps);
            if (!sh.isEmpty()) out.add(sh);
        }
        return out;
    }
    private static String nz(String a, String b) { return a != null && !a.isEmpty() ? a : (b == null ? "" : b); }

    private static void addPolygon(GeoShape sh, List<Object> rings, double eps) {
        if (rings == null) return;
        for (Object ro : rings) {
            List<Object> pts = MiniJson.list(ro);
            if (pts == null) continue;
            double[] a = new double[pts.size() * 2]; int n = 0;
            for (Object po : pts) { double[] pt = (double[]) po; if (pt.length < 2) continue; a[2 * n] = pt[0]; a[2 * n + 1] = pt[1]; n++; }
            sh.addRing(simplify(a, n, eps));
        }
    }

    /** Radial-distance decimation (keeps first/last); eps in degrees, 0 = keep all. */
    static float[] simplify(double[] pts, int n, double eps) {
        if (n < 3) return null;
        float[] out = new float[n * 2]; int m = 0;
        double lx = pts[0], ly = pts[1];
        out[m++] = (float) lx; out[m++] = (float) ly;
        double e2 = eps * eps;
        for (int i = 1; i < n - 1; i++) {
            double x = pts[2 * i], y = pts[2 * i + 1];
            if (eps <= 0 || (x - lx) * (x - lx) + (y - ly) * (y - ly) >= e2) { out[m++] = (float) x; out[m++] = (float) y; lx = x; ly = y; }
        }
        out[m++] = (float) pts[2 * (n - 1)]; out[m++] = (float) pts[2 * (n - 1) + 1];
        if (m < 6) return null;
        return m == out.length ? out : java.util.Arrays.copyOf(out, m);
    }
}

package com.cylinderradar.launcher;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Geographic data loader. Order for every dataset: 1) bundled asset (assets/geo/..., filled by the CI "data pack" step or
 * tools/fetch_geo_data.sh)  2) on-device cache (filesDir/geo/..., written after the first successful download)
 * 3) network race between several mirrors. After the first success the data works fully offline.
 * Sources: Natural Earth via world-atlas (public domain); geoBoundaries gbOpen (CC BY 4.0, (c) William & Mary geoLab).
 */
final class GeoRepo {
    private GeoRepo() {}
    interface Progress { void say(String msg); }

    static final String[] WORLD_URLS = {
        "https://cdn.jsdelivr.net/npm/world-atlas@2.0.2/countries-50m.json",
        "https://unpkg.com/world-atlas@2.0.2/countries-50m.json",
        "https://cdn.jsdelivr.net/npm/world-atlas@2/countries-50m.json"
    };
    static final String[] WORLD_LOW_URLS = {
        "https://cdn.jsdelivr.net/npm/world-atlas@2.0.2/countries-110m.json",
        "https://unpkg.com/world-atlas@2.0.2/countries-110m.json"
    };
    private static final int MAX_BYTES = 48 * 1024 * 1024;

    // ------------------------------------------------------------------ public API
    static List<GeoShape> loadWorld(Context c, Progress p) throws Exception {
        String json = readLocal(c, "world-50m.json");
        if (json != null) { try { List<GeoShape> r = GeoCodec.decode(json, 0); if (!r.isEmpty()) { say(p, "ডেটা-প্যাক থেকে ৫০ মি. বিশ্বমানচিত্র"); return r; } } catch (Exception ignored) { } }
        say(p, "বিশ্বের দেশসীমানা ডাউনলোড হচ্ছে… (একবারই লাগবে)");
        String body = null;
        try { body = race(valid(), WORLD_URLS, 30000, null); save(c, "world-50m.json", body); }
        catch (Exception e) {
            json = readLocal(c, "world-110m.json");
            if (json != null) body = json;
            else { say(p, "৫০ মি. পাওয়া যায়নি, হালকা সংস্করণ চেষ্টা চলছে…"); body = race(valid(), WORLD_LOW_URLS, 30000, null); save(c, "world-110m.json", body); }
        }
        List<GeoShape> r = GeoCodec.decode(body, 0);
        if (r.isEmpty()) throw new IOException("খালি ডেটা");
        return r;
    }

    /** level = "ADM1" or "ADM2". */
    static List<GeoShape> loadAdmin(Context c, String iso3, String level, Progress p) throws Exception {
        String file = iso3 + "_" + level + ".geojson";
        String json = readLocal(c, file);
        if (json != null) {
            try { List<GeoShape> r = GeoCodec.decode(json, epsFor(json.length())); if (!r.isEmpty()) { say(p, "সংরক্ষিত ডেটা থেকে (অফলাইন)"); return r; } } catch (Exception ignored) { }
        }
        say(p, level + " সীমানা ডাউনলোড হচ্ছে…");
        String rel = "releaseData/gbOpen/" + iso3 + "/" + level + "/geoBoundaries-" + iso3 + "-" + level;
        String api = "https://www.geoboundaries.org/api/current/gbOpen/" + iso3 + "/" + level + "/";
        String[] urls = {
            "https://cdn.jsdelivr.net/gh/wmgeolab/geoBoundaries@main/" + rel + "_simplified.geojson",
            "https://raw.githubusercontent.com/wmgeolab/geoBoundaries/main/" + rel + "_simplified.geojson",
            "https://media.githubusercontent.com/media/wmgeolab/geoBoundaries/main/" + rel + "_simplified.geojson",
            "API:" + api
        };
        String body = race(validGeo(), urls, 40000, null);
        save(c, file, body);
        List<GeoShape> r = GeoCodec.decode(body, epsFor(body.length()));
        if (r.isEmpty()) throw new IOException("এই দেশের জন্য " + level + " সীমানা পাওয়া যায়নি");
        return r;
    }

    static boolean hasLocal(Context c, String name) { return new File(new File(c.getFilesDir(), "geo"), name).isFile() || assetExists(c, name); }

    // ------------------------------------------------------------------ helpers
    private static double epsFor(int chars) { return chars > 12_000_000 ? 0.02 : chars > 5_000_000 ? 0.008 : chars > 1_500_000 ? 0.003 : 0.0008; }
    private static void say(Progress p, String m) { if (p != null) p.say(m); }

    interface Validator { boolean ok(String body); }
    private static Validator valid() { return b -> b.startsWith("{") && b.contains("\"Topology\""); }
    private static Validator validGeo() { return b -> b.startsWith("{") && b.contains("\"features\"") && !b.startsWith("version https://git-lfs"); }

    private static boolean assetExists(Context c, String name) {
        try (InputStream in = c.getAssets().open("geo/" + name)) { return in != null; } catch (IOException e) { return false; }
    }
    private static String readLocal(Context c, String name) {
        try (InputStream in = c.getAssets().open("geo/" + name)) { return new String(readAll(in), StandardCharsets.UTF_8); } catch (Throwable ignored) { }
        File f = new File(new File(c.getFilesDir(), "geo"), name);
        if (f.isFile() && f.length() > 100) {
            try (InputStream in = new java.io.FileInputStream(f)) { return new String(readAll(in), StandardCharsets.UTF_8); } catch (Throwable ignored) { }
        }
        return null;
    }
    private static void save(Context c, String name, String body) {
        try {
            File dir = new File(c.getFilesDir(), "geo"); dir.mkdirs();
            File tmp = new File(dir, name + ".tmp"), dst = new File(dir, name);
            try (FileOutputStream o = new FileOutputStream(tmp)) { o.write(body.getBytes(StandardCharsets.UTF_8)); }
            if (!tmp.renameTo(dst)) { dst.delete(); tmp.renameTo(dst); }
        } catch (Throwable ignored) { }
    }
    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream(1 << 16);
        byte[] buf = new byte[16384]; int n;
        while ((n = in.read(buf)) > 0) { bo.write(buf, 0, n); if (bo.size() > MAX_BYTES) throw new IOException("ফাইল খুব বড়"); }
        return bo.toByteArray();
    }

    /** Fetches all URLs in parallel; the first body that passes {@code v} wins. A url starting with "API:" is a geoBoundaries metadata endpoint. */
    private static String race(final Validator v, String[] urls, final int timeoutMs, Progress p) throws Exception {
        ExecutorService ex = Executors.newFixedThreadPool(urls.length, r -> { Thread t = new Thread(r, "geo-net"); t.setDaemon(true); return t; });
        CompletionService<String> cs = new ExecutorCompletionService<>(ex);
        List<Future<String>> fs = new ArrayList<>();
        for (final String u : urls) fs.add(cs.submit((Callable<String>) () -> {
            String target = u;
            if (u.startsWith("API:")) {
                String meta = http(u.substring(4), 15000);
                int i = meta.indexOf("\"simplifiedGeometryGeoJSON\"");
                if (i < 0) throw new IOException("API: simplified URL নেই");
                int q1 = meta.indexOf('"', meta.indexOf(':', i) + 1), q2 = meta.indexOf('"', q1 + 1);
                target = meta.substring(q1 + 1, q2).replace("\\/", "/");
            }
            String body = http(target, timeoutMs);
            if (!v.ok(body)) throw new IOException("অবৈধ ডেটা");
            return body;
        }));
        StringBuilder errs = new StringBuilder();
        try {
            for (int i = 0; i < urls.length; i++) {
                try { return cs.take().get(); }
                catch (ExecutionException e) { errs.append(e.getCause() == null ? e : e.getCause()).append('\n'); }
            }
        } finally { for (Future<String> f : fs) f.cancel(true); ex.shutdownNow(); }
        throw new IOException("সব উৎস ব্যর্থ। ইন্টারনেট সংযোগ দেখুন।\n" + errs);
    }

    private static String http(String url, int timeoutMs) throws IOException {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(12000); c.setReadTimeout(timeoutMs); c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", "CylinderRadarLauncher/2.5 (geo data)");
            c.setRequestProperty("Accept", "application/json, */*");
            int code = c.getResponseCode();
            if (code / 100 != 2) throw new IOException("HTTP " + code);
            try (InputStream in = c.getInputStream()) { return new String(readAll(in), StandardCharsets.UTF_8); }
        } finally { if (c != null) c.disconnect(); }
    }
}

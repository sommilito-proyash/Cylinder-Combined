package com.cylinderradar.launcher;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * v2.5.0 — Real 3D Earth. OpenGL ES 2.0 globe with REAL country polygons (Natural Earth 1:50m via world-atlas),
 * exact polygon hit-testing, gold raised highlight + crisp outline for the selected country, and a hand-off to the
 * Bangladesh/any-country division & district map (geoBoundaries). Falls back to the old illustrative globe + bounding
 * boxes when the data pack is not available yet.
 */
public final class EarthActivity extends Activity {
    private EarthSurface surface;
    private TextView info, targetMarker, rotateButton, statusChip, adminButton;
    private AutoCompleteTextView countrySearch;
    private Store store;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private volatile boolean destroyed = false;

    private final Country[] countries = new Country[] {
        new Country("বাংলাদেশ", "Bangladesh", 20, 27, 88, 93, "ঢাকা", "প্রায় ১,৪৮,৪৬০ বর্গকিমি", "দক্ষিণ এশিয়া"),
        new Country("ভারত", "India", 7, 36, 68, 97, "নয়াদিল্লি", "প্রায় ৩২.৮৭ লাখ বর্গকিমি", "দক্ষিণ এশিয়া"),
        new Country("চীন", "China", 18, 54, 73, 135, "বেইজিং", "প্রায় ৯৫.৯৬ লাখ বর্গকিমি", "পূর্ব এশিয়া"),
        new Country("যুক্তরাষ্ট্র", "United States", 24, 50, -125, -66, "ওয়াশিংটন, ডি.সি.", "প্রায় ৯৮.৩ লাখ বর্গকিমি", "উত্তর আমেরিকা"),
        new Country("ব্রাজিল", "Brazil", -34, 6, -74, -34, "ব্রাসিলিয়া", "প্রায় ৮৫.১৬ লাখ বর্গকিমি", "দক্ষিণ আমেরিকা"),
        new Country("অস্ট্রেলিয়া", "Australia", -44, -10, 112, 154, "ক্যানবেরা", "প্রায় ৭৬.৯২ লাখ বর্গকিমি", "ওশেনিয়া"),
        new Country("মিশর", "Egypt", 22, 32, 24, 36, "কায়রো", "প্রায় ১০.০১ লাখ বর্গকিমি", "উত্তর আফ্রিকা"),
        new Country("যুক্তরাজ্য", "United Kingdom", 50, 59, -8, 2, "লন্ডন", "প্রায় ২.৪৩ লাখ বর্গকিমি", "ইউরোপ"),
        new Country("জাপান", "Japan", 30, 46, 129, 146, "টোকিও", "প্রায় ৩.৭৮ লাখ বর্গকিমি", "পূর্ব এশিয়া"),
        new Country("ফ্রান্স", "France", 42, 51, -5, 9, "প্যারিস", "প্রায় ৫.৫২ লাখ বর্গকিমি", "ইউরোপ"),
        new Country("পাকিস্তান", "Pakistan", 23, 37, 60, 77, "ইসলামাবাদ", "প্রায় ৮.৮২ লাখ বর্গকিমি", "দক্ষিণ এশিয়া"),
        new Country("নেপাল", "Nepal", 26, 31, 80, 89, "কাঠমান্ডু", "প্রায় ১.৪৭ লাখ বর্গকিমি", "দক্ষিণ এশিয়া"),
        new Country("ভুটান", "Bhutan", 26.5, 28.5, 88.7, 92.2, "থিম্পু", "প্রায় ৩৮,৩৯৪ বর্গকিমি", "দক্ষিণ এশিয়া"),
        new Country("শ্রীলঙ্কা", "Sri Lanka", 5.8, 10, 79.5, 82, "শ্রী জয়বর্ধনেপুরা কোট্টে", "প্রায় ৬৫,৬১০ বর্গকিমি", "দক্ষিণ এশিয়া"),
        new Country("ইন্দোনেশিয়া", "Indonesia", -11, 6, 95, 141, "জাকার্তা", "প্রায় ১৯.০৫ লাখ বর্গকিমি", "দক্ষিণ-পূর্ব এশিয়া"),
        new Country("মালয়েশিয়া", "Malaysia", 0.8, 7.5, 99, 119, "কুয়ালালামপুর", "প্রায় ৩.৩০ লাখ বর্গকিমি", "দক্ষিণ-পূর্ব এশিয়া"),
        new Country("থাইল্যান্ড", "Thailand", 5.5, 20.5, 97, 106, "ব্যাংকক", "প্রায় ৫.১৩ লাখ বর্গকিমি", "দক্ষিণ-পূর্ব এশিয়া"),
        new Country("ভিয়েতনাম", "Vietnam", 8, 24, 102, 110, "হ্যানয়", "প্রায় ৩.৩১ লাখ বর্গকিমি", "দক্ষিণ-পূর্ব এশিয়া"),
        new Country("দক্ষিণ কোরিয়া", "South Korea", 33, 39, 124, 130, "সিউল", "প্রায় ১.০০ লাখ বর্গকিমি", "পূর্ব এশিয়া"),
        new Country("সৌদি আরব", "Saudi Arabia", 16, 33, 34, 56, "রিয়াদ", "প্রায় ২১.৫০ লাখ বর্গকিমি", "পশ্চিম এশিয়া"),
        new Country("সংযুক্ত আরব আমিরাত", "United Arab Emirates", 22.5, 26.5, 51, 56.5, "আবুধাবি", "প্রায় ৮৩,৬০০ বর্গকিমি", "পশ্চিম এশিয়া"),
        new Country("তুরস্ক", "Turkey", 35, 43, 25, 45, "আঙ্কারা", "প্রায় ৭.৮৪ লাখ বর্গকিমি", "ইউরোপ ও এশিয়া"),
        new Country("জার্মানি", "Germany", 47, 55, 5, 16, "বার্লিন", "প্রায় ৩.৫৮ লাখ বর্গকিমি", "ইউরোপ"),
        new Country("ইতালি", "Italy", 36, 47, 6, 19, "রোম", "প্রায় ৩.০১ লাখ বর্গকিমি", "ইউরোপ"),
        new Country("স্পেন", "Spain", 35, 44, -10, 5, "মাদ্রিদ", "প্রায় ৫.০৬ লাখ বর্গকিমি", "ইউরোপ"),
        new Country("নরওয়ে", "Norway", 57, 71, 4, 32, "অসলো", "প্রায় ৩.৮৫ লাখ বর্গকিমি", "ইউরোপ"),
        new Country("সুইডেন", "Sweden", 55, 69, 11, 24, "স্টকহোম", "প্রায় ৪.৫০ লাখ বর্গকিমি", "ইউরোপ"),
        new Country("রাশিয়া", "Russia", 41, 82, 28, 180, "মস্কো", "প্রায় ১.৭১ কোটি বর্গকিমি", "ইউরোপ ও এশিয়া"),
        new Country("মেক্সিকো", "Mexico", 14, 33, -118, -86, "মেক্সিকো সিটি", "প্রায় ১৯.৬৪ লাখ বর্গকিমি", "উত্তর আমেরিকা"),
        new Country("কানাডা", "Canada", 42, 84, -141, -52, "অটোয়া", "প্রায় ৯৯.৮৪ লাখ বর্গকিমি", "উত্তর আমেরিকা"),
        new Country("আর্জেন্টিনা", "Argentina", -55, -21, -74, -53, "বুয়েনস আয়ার্স", "প্রায় ২৭.৮০ লাখ বর্গকিমি", "দক্ষিণ আমেরিকা"),
        new Country("চিলি", "Chile", -56, -17, -76, -66, "সান্তিয়াগো", "প্রায় ৭.৫৬ লাখ বর্গকিমি", "দক্ষিণ আমেরিকা"),
        new Country("দক্ষিণ আফ্রিকা", "South Africa", -35, -22, 16, 33, "প্রিটোরিয়া", "প্রায় ১২.২১ লাখ বর্গকিমি", "দক্ষিণ আফ্রিকা"),
        new Country("নাইজেরিয়া", "Nigeria", 4, 14, 2, 15, "আবুজা", "প্রায় ৯.২৪ লাখ বর্গকিমি", "পশ্চিম আফ্রিকা"),
        new Country("কেনিয়া", "Kenya", -5, 5, 33, 42, "নাইরোবি", "প্রায় ৫.৮০ লাখ বর্গকিমি", "পূর্ব আফ্রিকা"),
        new Country("নিউজিল্যান্ড", "New Zealand", -48, -34, 166, 179, "ওয়েলিংটন", "প্রায় ২.৬৮ লাখ বর্গকিমি", "ওশেনিয়া")
    };

    /** A selectable country: either backed by a real polygon (shape != null) or only by the bounding-box fallback. */
    static final class Entry {
        String bn, en, iso3; GeoShape shape; Country info; double lat, lon, zoom = 1.65;
        String label() { return bn.equals(en) ? en : bn + " • " + en; }
    }
    private List<Entry> entries = new ArrayList<>();
    private List<GeoShape> world;
    private Entry selected;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        store = new Store(this);
        FrameLayout root = new FrameLayout(this);
        surface = new EarthSurface(this);
        root.addView(surface, new FrameLayout.LayoutParams(-1, -1));
        TextView back = chip("‹  OBSERVATORY");
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-2, dp(42), Gravity.TOP|Gravity.LEFT);
        bp.setMargins(dp(14), dp(24), 0, 0); root.addView(back, bp); back.setOnClickListener(v -> finish());
        TextView reset = chip("RESET VIEW");
        FrameLayout.LayoutParams rp = new FrameLayout.LayoutParams(-2, dp(42), Gravity.TOP|Gravity.RIGHT);
        rp.setMargins(0, dp(24), dp(14), 0); root.addView(reset, rp);
        reset.setOnClickListener(v -> { surface.resetView(); rotateButtonLabelOnReset(); clearSelection(); });
        TextView eco = chip(store.observatoryBatterySaver() ? "ECO ON" : "ECO OFF");
        FrameLayout.LayoutParams ep = new FrameLayout.LayoutParams(-2, dp(42), Gravity.TOP|Gravity.CENTER_HORIZONTAL);
        ep.setMargins(0, dp(24), 0, 0); root.addView(eco, ep);
        eco.setOnClickListener(v -> { boolean on=!store.observatoryBatterySaver(); store.setObservatoryBatterySaver(on); surface.setBatterySaver(on); eco.setText(on?"ECO ON":"ECO OFF"); });
        surface.setBatterySaver(store.observatoryBatterySaver());

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setPadding(dp(8), dp(5), dp(8), dp(5));
        searchRow.setBackgroundColor(0xCC0B1B2A);
        FrameLayout.LayoutParams searchLp = new FrameLayout.LayoutParams(-1, dp(52), Gravity.TOP);
        searchLp.setMargins(dp(14), dp(76), dp(14), 0); root.addView(searchRow, searchLp);
        countrySearch = new AutoCompleteTextView(this);
        countrySearch.setSingleLine(true); countrySearch.setTextSize(14);
        countrySearch.setTextColor(0xFFEAF8FF); countrySearch.setHintTextColor(0xFF9BB8C9);
        countrySearch.setHint("দেশের নাম লিখুন / Search country");
        countrySearch.setPadding(dp(10), 0, dp(6), 0);
        searchRow.addView(countrySearch, new LinearLayout.LayoutParams(0, -1, 1));
        TextView go = chip("GO");
        searchRow.addView(go, new LinearLayout.LayoutParams(dp(58), -1));
        countrySearch.setOnItemClickListener((parent, view, position, id) -> {
            String chosen=String.valueOf(parent.getItemAtPosition(position));
            for(int i=0;i<entries.size();i++) if(chosen.equals(entries.get(i).label())) { selectEntry(entries.get(i)); break; }
        });
        go.setOnClickListener(v -> searchCountry());
        countrySearch.setOnEditorActionListener((v, actionId, event) -> { searchCountry(); return true; });

        rotateButton = chip("AUTO ROTATE  •  ON");
        FrameLayout.LayoutParams rotateLp = new FrameLayout.LayoutParams(-2, dp(38), Gravity.TOP|Gravity.CENTER_HORIZONTAL);
        rotateLp.setMargins(0, dp(136), 0, 0); root.addView(rotateButton, rotateLp);
        rotateButton.setOnClickListener(v -> { boolean on=surface.toggleAutoRotate(); rotateButton.setText(on?"AUTO ROTATE  •  ON":"AUTO ROTATE  •  OFF"); });

        statusChip = chip("");
        statusChip.setTextSize(10); statusChip.setVisibility(View.GONE);
        FrameLayout.LayoutParams stLp = new FrameLayout.LayoutParams(-2, dp(30), Gravity.TOP|Gravity.CENTER_HORIZONTAL);
        stLp.setMargins(0, dp(180), 0, 0); root.addView(statusChip, stLp);
        statusChip.setOnClickListener(v -> { if (world == null) startWorldLoad(); });

        targetMarker = new TextView(this);
        targetMarker.setText("◎"); targetMarker.setTextColor(0xFFFFD76A); targetMarker.setTextSize(30);
        targetMarker.setGravity(Gravity.CENTER); targetMarker.setVisibility(View.GONE);
        root.addView(targetMarker, new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);
        bl.setMargins(dp(14),0,dp(14),dp(24)); root.addView(bottom, bl);
        adminButton = chip("বিভাগ ও জেলা মানচিত্র  ›");
        adminButton.setTextSize(12); adminButton.setBackgroundColor(0xDD1F6F8B); adminButton.setVisibility(View.GONE);
        bottom.addView(adminButton, new LinearLayout.LayoutParams(-1, dp(46)));
        adminButton.setOnClickListener(v -> openAdminMap());
        info = new TextView(this);
        info.setTextColor(0xFFEAF8FF); info.setTextSize(13); info.setPadding(dp(18),dp(14),dp(18),dp(14));
        info.setBackgroundColor(0xCC0B1B2A);
        info.setText("EARTH • OPENGL ES 2.0\nআঙুল দিয়ে পৃথিবী ঘোরান, দুই আঙুলে জুম করুন।\nদেশে ট্যাপ করে তথ্য দেখুন বা উপরে খুঁজুন।");
        bottom.addView(info, new LinearLayout.LayoutParams(-1,-2));

        buildFallbackEntries();
        surface.setOnGlobeTapListener((lat,lon) -> { targetMarker.setVisibility(View.GONE); onGlobeTap(lat,lon); });
        setContentView(root);
        startWorldLoad();
    }

    // ------------------------------------------------------------------ data loading
    private void startWorldLoad() {
        showStatus("বাস্তব দেশসীমানা লোড হচ্ছে…", false);
        new Thread(() -> {
            try {
                final List<GeoShape> w = GeoRepo.loadWorld(EarthActivity.this, m -> ui.post(() -> showStatus(m, false)));
                final Bitmap base = GlobeArt.buildWorldBitmap(w, 2048, 1024);
                if (destroyed) { base.recycle(); return; }
                ui.post(() -> { if (destroyed) { base.recycle(); return; } onWorldReady(w, base); });
            } catch (Throwable t) {
                final String msg = (t instanceof OutOfMemoryError) ? "মেমরি কম — পুরোনো গ্লোব চলছে" : "ডেটা আসেনি — ট্যাপ করে আবার চেষ্টা করুন";
                ui.post(() -> showStatus(msg, true));
            }
        }, "earth-geo").start();
    }
    private void onWorldReady(List<GeoShape> w, Bitmap base) {
        world = w;
        surface.setBaseBitmap(base);
        buildWorldEntries(w);
        showStatus("✔ " + GeoNames.bnDigits(w.size()) + "টি দেশের প্রকৃত সীমানা লোড হয়েছে", false);
        ui.postDelayed(() -> { if (!destroyed && statusChip.getText().toString().startsWith("✔")) statusChip.setVisibility(View.GONE); }, 3500);
        info.setText("বাস্তব সীমানা প্রস্তুত ✔\nযেকোনো দেশে ট্যাপ করুন বা নাম খুঁজুন।\n\nসূত্র: Natural Earth (পাবলিক ডোমেইন) · world-atlas");
        if (selected != null) { Entry e = findByEn(selected.en); if (e != null) selectEntry(e); }
    }
    private void showStatus(String m, boolean error) {
        if (statusChip == null) return;
        statusChip.setText(m); statusChip.setVisibility(View.VISIBLE);
        statusChip.setBackgroundColor(error ? 0xCC8A2B2B : 0xAA17465B);
    }

    private void buildFallbackEntries() {
        List<Entry> l = new ArrayList<>();
        for (Country c : countries) { Entry e = new Entry(); e.bn=c.bn; e.en=c.en; e.info=c; e.lat=c.centerLat; e.lon=c.centerLon; e.iso3=GeoNames.iso3(null,c.en); l.add(e); }
        setEntries(l);
    }
    private void buildWorldEntries(List<GeoShape> w) {
        List<Entry> l = new ArrayList<>();
        for (GeoShape s : w) {
            if (s.name == null || s.name.isEmpty()) continue;
            Entry e = new Entry(); e.shape = s; e.en = s.name;
            e.iso3 = GeoNames.iso3(s.id, s.name);
            e.info = infoFor(s);
            e.bn = e.info != null ? e.info.bn : (GeoNames.countryBn(s.name) != null ? GeoNames.countryBn(s.name) : s.name);
            double[] f = s.focusPoint(); e.lat = f[0]; e.lon = f[1];
            double spanLon = 0, spanLat = 0;
            for (float[] r : s.rings) { double a=1e9,b=-1e9,c=1e9,d=-1e9; for (int i=0;i<r.length;i+=2){a=Math.min(a,r[i]);b=Math.max(b,r[i]);c=Math.min(c,r[i+1]);d=Math.max(d,r[i+1]);} if ((b-a)*(d-c) >= spanLon*spanLat) { spanLon=b-a; spanLat=d-c; } }
            double th = Math.toRadians(Math.max(spanLon * Math.cos(Math.toRadians(e.lat)), spanLat));
            e.zoom = Math.max(1.0, Math.min(5.0, 1.4 / Math.max(0.05, th)));
            l.add(e);
        }
        Collections.sort(l, new Comparator<Entry>() { @Override public int compare(Entry a, Entry b) { return a.en.compareToIgnoreCase(b.en); } });
        setEntries(l);
    }
    private Country infoFor(GeoShape s) {
        String iso3 = GeoNames.iso3(s.id, s.name);
        for (Country c : countries) { String ci = GeoNames.iso3(null, c.en); if (iso3 != null && iso3.equals(ci)) return c; }
        return null;
    }
    private void setEntries(List<Entry> l) {
        entries = l;
        String[] labels = new String[l.size()];
        for (int i=0;i<l.size();i++) labels[i]=l.get(i).label();
        countrySearch.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, labels));
    }
    private Entry findByEn(String en) { for (Entry e : entries) if (e.en.equals(en)) return e; return null; }

    // ------------------------------------------------------------------ selection
    private void selectEntry(Entry e) {
        if (e == null) return;
        selected = e;
        countrySearch.setText(e.label(), false);
        surface.focusCountry(e.lat, e.lon, (float) e.zoom);
        rotateButton.setText("AUTO ROTATE  •  OFF");
        if (e.shape != null) {
            surface.setHighlight(e.shape, GlobeArt.buildHighlightBitmap(e.shape, 2048, 1024));
            targetMarker.setVisibility(View.GONE);
        } else { surface.setHighlight(null, null); targetMarker.setVisibility(View.VISIBLE); }
        showEntry(e);
    }
    private void clearSelection() { selected = null; surface.setHighlight(null, null); targetMarker.setVisibility(View.GONE); adminButton.setVisibility(View.GONE); }

    private void searchCountry() {
        String q=countrySearch.getText().toString().trim().toLowerCase(Locale.ROOT);
        if(q.isEmpty()) { info.setText("দেশের নাম লিখে Search করুন।"); return; }
        Entry best = null;
        for (Entry e : entries) { String bn=e.bn.toLowerCase(Locale.ROOT), en=e.en.toLowerCase(Locale.ROOT); if (bn.equals(q)||en.equals(q)) { best=e; break; } }
        if (best == null) for (Entry e : entries) { if (e.bn.toLowerCase(Locale.ROOT).startsWith(q)||e.en.toLowerCase(Locale.ROOT).startsWith(q)) { best=e; break; } }
        if (best == null) for (Entry e : entries) { if (e.bn.toLowerCase(Locale.ROOT).contains(q)||e.en.toLowerCase(Locale.ROOT).contains(q)) { best=e; break; } }
        if (best != null) selectEntry(best);
        else info.setText(world == null ? "বাস্তব ডেটা এখনো আসেনি; আপাতত ৩৬টি দেশে খোঁজা যাচ্ছে।" : "এই নামে কোনো দেশ পাওয়া যায়নি।");
    }

    private void onGlobeTap(double lat, double lon) {
        if (world != null) {
            Entry hit = null;
            for (Entry e : entries) if (e.shape != null && e.shape.contains(lon, lat)) { if (hit == null || e.shape.bboxArea() < hit.shape.bboxArea()) hit = e; }
            if (hit != null) { selected = hit; countrySearch.setText(hit.label(), false); surface.setHighlight(hit.shape, GlobeArt.buildHighlightBitmap(hit.shape, 2048, 1024)); surface.setAutoRotate(false); rotateButton.setText("AUTO ROTATE  •  OFF"); showEntry(hit); return; }
            surface.setHighlight(null, null); selected = null; adminButton.setVisibility(View.GONE);
            info.setText(String.format(Locale.US,"সমুদ্র / দেশের বাইরের এলাকা\nঅক্ষাংশ: %.2f°   দ্রাঘিমাংশ: %.2f°", lat, lon));
            return;
        }
        // Fallback: smallest matching bounding box.
        Entry found=null; double bestArea=Double.MAX_VALUE;
        for (Entry e : entries) { Country c=e.info; if (c!=null && lat>=c.south&&lat<=c.north&&lon>=c.west&&lon<=c.east){ double a=(c.north-c.south)*(c.east-c.west); if(a<bestArea){bestArea=a;found=e;} } }
        if (found == null) info.setText(String.format(Locale.US,"EARTH COORDINATES\nঅক্ষাংশ: %.1f°   দ্রাঘিমাংশ: %.1f°\nবাস্তব সীমানার ডেটা এখনো আসেনি; ওপরের স্ট্যাটাসে ট্যাপ করে আবার চেষ্টা করুন।",lat,lon));
        else { selected = found; showEntry(found); }
    }

    private void showEntry(Entry e) {
        StringBuilder sb = new StringBuilder(e.label());
        if (e.info != null) sb.append("\nরাজধানী: ").append(e.info.capital).append("\nআয়তন: ").append(e.info.area).append("\nঅঞ্চল: ").append(e.info.region);
        else sb.append("\n(এই দেশের বিস্তারিত তথ্য এখনো যুক্ত হয়নি)");
        if (e.shape != null) sb.append("\n\nসীমানা: বাস্তব ভৌগোলিক পলিগন (Natural Earth ১:৫০ মি.)");
        else sb.append("\n\nতথ্যগুলো সংক্ষিপ্ত পরিচিতি; এটি আনুমানিক অবস্থান।");
        info.setText(sb.toString());
        adminButton.setVisibility(e.iso3 != null ? View.VISIBLE : View.GONE);
    }

    private void openAdminMap() {
        if (selected == null || selected.iso3 == null) return;
        Intent i = new Intent(this, AdminMapActivity.class);
        i.putExtra("iso3", selected.iso3); i.putExtra("bn", selected.bn); i.putExtra("en", selected.en);
        startActivity(i);
    }

    private void rotateButtonLabelOnReset() { if(rotateButton!=null) rotateButton.setText("AUTO ROTATE  •  ON"); }
    private TextView chip(String s) { TextView t=new TextView(this); t.setText(s);t.setTextColor(Color.WHITE);t.setTextSize(11);t.setGravity(Gravity.CENTER);t.setPadding(dp(14),0,dp(14),0);t.setBackgroundColor(0xAA17465B);return t; }
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    @Override protected void onResume(){super.onResume();if(surface!=null){surface.onResume();surface.setActive(true);}}
    @Override protected void onPause(){if(surface!=null){surface.setActive(false);surface.onPause();}super.onPause();}
    @Override protected void onDestroy(){ destroyed = true; ui.removeCallbacksAndMessages(null); super.onDestroy(); }

    interface GlobeTap { void onTap(double lat,double lon); }
    static final class Country {
        final String bn,en; final double south,north,west,east,centerLat,centerLon; final String capital,area,region;
        Country(String b,String e,double s,double n,double w,double x,String c,String a,String r){bn=b;en=e;south=s;north=n;west=w;east=x;centerLat=(s+n)/2.0;centerLon=(w+x)/2.0;capital=c;area=a;region=r;}
    }

    // ================================================================== GL surface
    static final class EarthSurface extends GLSurfaceView {
        private EarthRenderer renderer;
        private final ScaleGestureDetector scale;
        private final Handler handler=new Handler(Looper.getMainLooper());
        private boolean active=false,ecoMode=false;
        private final Runnable tick=new Runnable(){@Override public void run(){if(!active||!renderer.autoRotate)return;requestRender();handler.postDelayed(this,ecoMode?1000L:66L);}};
        private float downX,downY,startX,startY; private boolean moved;
        private GlobeTap tap;
        EarthSurface(Activity a){
            super(a); setEGLContextClientVersion(2); renderer=new EarthRenderer(); setRenderer(renderer); setRenderMode(RENDERMODE_WHEN_DIRTY);
            scale=new ScaleGestureDetector(a,new ScaleGestureDetector.SimpleOnScaleGestureListener(){@Override public boolean onScale(ScaleGestureDetector d){renderer.zoom=Math.max(1.0f,Math.min(5.0f,renderer.zoom*d.getScaleFactor()));requestRender();return true;}});
        }
        void setOnGlobeTapListener(GlobeTap l){tap=l;}
        void setBatterySaver(boolean on){ecoMode=on;restartLoop();}
        void setActive(boolean on){active=on;restartLoop();}
        private void restartLoop(){handler.removeCallbacks(tick);if(active&&renderer.autoRotate)handler.post(tick);}
        void resetView(){renderer.yaw=0;renderer.pitch=-.15f;renderer.zoom=1;renderer.autoRotate=true;restartLoop();requestRender();}
        void focusCountry(double lat,double lon,float zoom){renderer.yaw=(float)-Math.toRadians(lon);renderer.pitch=(float)Math.toRadians(lat);renderer.zoom=Math.max(1f,Math.min(5f,zoom));renderer.autoRotate=false;restartLoop();requestRender();}
        boolean toggleAutoRotate(){renderer.autoRotate=!renderer.autoRotate;restartLoop();requestRender();return renderer.autoRotate;}
        void setAutoRotate(boolean on){renderer.autoRotate=on;restartLoop();}
        void setBaseBitmap(Bitmap b){renderer.setBase(b);requestRender();}
        void setHighlight(GeoShape s, Bitmap overlay){renderer.setHighlight(s,overlay);requestRender();}
        @Override public boolean onTouchEvent(MotionEvent e){
            scale.onTouchEvent(e);
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN: downX=startX=e.getX();downY=startY=e.getY();moved=false;return true;
                case MotionEvent.ACTION_POINTER_DOWN: moved=true;return true;
                case MotionEvent.ACTION_MOVE:
                    if(e.getPointerCount()==1){
                        float dx=e.getX()-downX,dy=e.getY()-downY;
                        if(Math.abs(e.getX()-startX)+Math.abs(e.getY()-startY)>16)moved=true;
                        if(moved) renderer.drag(dx,dy,getWidth());
                        downX=e.getX();downY=e.getY();requestRender();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if(!moved&&tap!=null){
                        double[] ll=renderer.screenToLatLon(e.getX(),e.getY(),getWidth(),getHeight());
                        if(ll!=null)tap.onTap(ll[0],ll[1]);
                    }
                    requestRender();return true;
            }
            return true;
        }
    }

    // ================================================================== renderer
    static final class EarthRenderer implements GLSurfaceView.Renderer {
        volatile float yaw=0,pitch=-.15f,zoom=1; volatile boolean autoRotate=true;
        private int program,baseTex,overTex,moonTex,viewW=1,viewH=1,count;
        private FloatBuffer sphere;
        private long last=System.nanoTime();
        private final float[] mvp=new float[16], rot=new float[9];
        // texture hand-off (UI thread -> GL thread)
        private final Object lock=new Object();
        private Bitmap pendingBase,curBase,pendingOver,curOver; private boolean overDirty,baseDirty,hasOverlay,fallbackBase=true;
        private FloatBuffer outline; private int[] ringStart=new int[0],ringLen=new int[0]; private GeoShape pendingShape;
        private boolean outlineDirty;
        private float dim=0f;

        private static final String VS="attribute vec3 aPos; attribute vec2 aUv; uniform mat4 uMvp; uniform mat3 uRot; uniform float uScale; varying vec2 vUv; varying vec3 vN; void main(){vUv=aUv;vN=uRot*aPos;gl_Position=uMvp*vec4(aPos*uScale,1.0);}";
        private static final String FS="precision mediump float; varying vec2 vUv; varying vec3 vN; uniform sampler2D uTex; uniform float uMode; uniform float uDim; uniform vec3 uColor;"
            +"void main(){vec3 n=normalize(vN);float d=max(dot(n,normalize(vec3(-0.45,0.35,0.85))),0.0);"
            +"if(uMode>2.5){gl_FragColor=vec4(uColor,1.0);return;}"
            +"vec4 t=texture2D(uTex,vUv);"
            +"if(uMode>0.5&&uMode<1.5){if(t.a<0.05)discard;gl_FragColor=vec4(t.rgb*(0.8+0.2*d),1.0);return;}"
            +"vec3 col;"
            +"if(uMode>1.5){col=t.rgb*(0.30+0.70*d);}else{col=t.rgb*(0.45+0.55*d)*(1.0-0.38*uDim);float rim=pow(1.0-max(n.z,0.0),3.0);col+=vec3(0.15,0.35,0.7)*rim*0.55;}"
            +"gl_FragColor=vec4(col,1.0);}";

        void setBase(Bitmap b){ synchronized(lock){ pendingBase=b; baseDirty=true; } }
        void setHighlight(GeoShape s, Bitmap overlay){
            synchronized(lock){ pendingShape=s; outlineDirty=true; if(pendingOver!=null&&pendingOver!=overlay&&overDirty&&!pendingOver.isRecycled())pendingOver.recycle(); pendingOver=overlay; overDirty=true; dim = s!=null ? 1f : 0f; }
        }

        @Override public void onSurfaceCreated(GL10 gl,EGLConfig cfg){
            GLES20.glClearColor(.015f,.035f,.07f,1);
            program=program(VS,FS);
            baseTex=newTexture(true); overTex=newTexture(false); moonTex=newTexture(false);
            buildSphere();
            synchronized(lock){
                // (re)upload everything after a context (re)creation
                Bitmap fb=makeFallbackBitmap(); uploadTex(baseTex,fb,true); fb.recycle();
                if(curBase!=null&&!curBase.isRecycled()){ uploadTex(baseTex,curBase,true); fallbackBase=false; } else fallbackBase=true;
                if(curOver!=null&&!curOver.isRecycled()){ uploadTex(overTex,curOver,false); hasOverlay=true; } else hasOverlay=false;
                outlineDirty=true;
            }
            Bitmap mb=makeMoonBitmap(); uploadTex(moonTex,mb,false); mb.recycle();
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);
            GLES20.glDisable(GLES20.GL_CULL_FACE);
        }
        @Override public void onSurfaceChanged(GL10 gl,int w,int h){viewW=Math.max(1,w);viewH=Math.max(1,h);GLES20.glViewport(0,0,w,h);}

        @Override public void onDrawFrame(GL10 gl){
            syncTextures();
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT);
            long now=System.nanoTime();float dt=Math.min(.12f,(now-last)/1e9f);last=now;
            if(autoRotate) yaw+=dt*.12f;
            GLES20.glUseProgram(program);
            int pos=GLES20.glGetAttribLocation(program,"aPos"),uv=GLES20.glGetAttribLocation(program,"aUv");
            int uMvp=GLES20.glGetUniformLocation(program,"uMvp"),uRot=GLES20.glGetUniformLocation(program,"uRot"),uScale=GLES20.glGetUniformLocation(program,"uScale");
            int uMode=GLES20.glGetUniformLocation(program,"uMode"),uDim=GLES20.glGetUniformLocation(program,"uDim"),uColor=GLES20.glGetUniformLocation(program,"uColor"),uTex=GLES20.glGetUniformLocation(program,"uTex");
            float r=0.86f*zoom, rx=r, ry=r*viewW/(float)viewH;
            matrix(mvp,rot,yaw,pitch,rx,ry,0.45f,0,0,0);
            GLES20.glUniformMatrix4fv(uMvp,1,false,mvp,0); GLES20.glUniformMatrix3fv(uRot,1,false,rot,0);
            GLES20.glUniform1i(uTex,0); GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glEnableVertexAttribArray(pos);GLES20.glEnableVertexAttribArray(uv);
            // --- base globe
            GLES20.glUniform1f(uScale,1f);GLES20.glUniform1f(uMode,0f);GLES20.glUniform1f(uDim,dim);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,baseTex);
            sphere.position(0);GLES20.glVertexAttribPointer(pos,3,GLES20.GL_FLOAT,false,20,sphere);sphere.position(3);GLES20.glVertexAttribPointer(uv,2,GLES20.GL_FLOAT,false,20,sphere);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,count);
            // --- raised gold highlight (same mesh, slightly larger radius, transparent texels discarded)
            if(hasOverlay){
                GLES20.glUniform1f(uScale,1.02f);GLES20.glUniform1f(uMode,1f);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,overTex);
                GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,count);
            }
            // --- crisp outline (vector, stays sharp at any zoom)
            if(outline!=null&&ringLen.length>0){
                GLES20.glUniform1f(uScale,1.024f);GLES20.glUniform1f(uMode,3f);GLES20.glUniform3f(uColor,1f,1f,1f);
                GLES20.glLineWidth(3f);
                outline.position(0);GLES20.glVertexAttribPointer(pos,3,GLES20.GL_FLOAT,false,20,outline);outline.position(3);GLES20.glVertexAttribPointer(uv,2,GLES20.GL_FLOAT,false,20,outline);
                for(int i=0;i<ringLen.length;i++) GLES20.glDrawArrays(GLES20.GL_LINE_STRIP,ringStart[i],ringLen[i]);
                // re-bind sphere pointers for the moon
                sphere.position(0);GLES20.glVertexAttribPointer(pos,3,GLES20.GL_FLOAT,false,20,sphere);sphere.position(3);GLES20.glVertexAttribPointer(uv,2,GLES20.GL_FLOAT,false,20,sphere);
            }
            // --- moon
            float orbit=(now/1.0e9f)*0.35f; float[] mm=new float[16], mr=new float[9];
            matrix(mm,mr,yaw*2f+1.1f,.4f,0.17f,0.17f*viewW/(float)viewH,0.07f,(0.86f)*(float)Math.cos(orbit),0.30f*(float)Math.sin(orbit),0.9f*(float)Math.sin(orbit));
            GLES20.glUniformMatrix4fv(uMvp,1,false,mm,0);GLES20.glUniformMatrix3fv(uRot,1,false,mr,0);
            GLES20.glUniform1f(uScale,1f);GLES20.glUniform1f(uMode,2f);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,moonTex);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,count);
            GLES20.glDisableVertexAttribArray(pos);GLES20.glDisableVertexAttribArray(uv);
        }

        /** Column-major MVP = T * S * Rx(pitch) * Ry(yaw); also the pure 3x3 rotation for lighting. Orthographic, y up, z toward viewer. */
        static void matrix(float[] m,float[] r3,float yaw,float pitch,float sx,float sy,float sz,float tx,float ty,float tz){
            float c=(float)Math.cos(yaw),s=(float)Math.sin(yaw),cp=(float)Math.cos(pitch),sp=(float)Math.sin(pitch);
            // rows of R = Rx*Ry
            float r00=c,r01=0,r02=s, r10=sp*s,r11=cp,r12=-sp*c, r20=-cp*s,r21=sp,r22=cp*c;
            r3[0]=r00;r3[1]=r10;r3[2]=r20; r3[3]=r01;r3[4]=r11;r3[5]=r21; r3[6]=r02;r3[7]=r12;r3[8]=r22;
            m[0]=sx*r00;m[1]=sy*r10;m[2]=-sz*r20;m[3]=0;
            m[4]=sx*r01;m[5]=sy*r11;m[6]=-sz*r21;m[7]=0;
            m[8]=sx*r02;m[9]=sy*r12;m[10]=-sz*r22;m[11]=0;
            m[12]=tx;m[13]=ty;m[14]=tz;m[15]=1;
        }

        /** Inverse of the globe projection: screen px -> lat/lon degrees, or null when the tap misses the globe. */
        double[] screenToLatLon(float px,float py,int w,int h){
            double R=w*0.43*zoom; if(R<=0) return null;
            double nx=(px-w/2.0)/R, ny=(h/2.0-py)/R, q=nx*nx+ny*ny;
            if(q>1) return null;
            double zc=Math.sqrt(1-q), c=Math.cos(yaw),s=Math.sin(yaw),cp=Math.cos(pitch),sp=Math.sin(pitch);
            double y1=ny*cp+zc*sp, z1=-ny*sp+zc*cp;
            double x=nx*c-z1*s, z=nx*s+z1*c;
            double lat=Math.toDegrees(Math.asin(Math.max(-1,Math.min(1,y1)))), lon=Math.toDegrees(Math.atan2(x,z));
            return new double[]{lat,lon};
        }
        void drag(float dx,float dy,int w){ double R=Math.max(1,w*0.43*zoom); yaw+=(float)(dx/R); pitch=Math.max(-1.4f,Math.min(1.4f,pitch+(float)(dy/R))); }

        // ---- GL-thread texture/outline sync
        private void syncTextures(){
            synchronized(lock){
                if(baseDirty&&pendingBase!=null){ uploadTex(baseTex,pendingBase,true); Bitmap old=curBase; curBase=pendingBase; pendingBase=null; baseDirty=false; fallbackBase=false; if(old!=null&&old!=curBase&&!old.isRecycled())old.recycle(); }
                if(overDirty){
                    if(pendingOver!=null){ uploadTex(overTex,pendingOver,false); hasOverlay=true; } else hasOverlay=false;
                    Bitmap old=curOver; curOver=pendingOver; pendingOver=null; overDirty=false; if(old!=null&&old!=curOver&&!old.isRecycled())old.recycle();
                }
                if(outlineDirty){ buildOutline(pendingShape); outlineDirty=false; }
            }
        }
        private void buildOutline(GeoShape s){
            if(s==null){ outline=null; ringStart=new int[0]; ringLen=new int[0]; return; }
            int total=0; for(float[] r:s.rings) total+=r.length/2;
            float[] d=new float[total*5]; ringStart=new int[s.rings.size()]; ringLen=new int[s.rings.size()]; int k=0,vi=0,ri=0;
            for(float[] r:s.rings){
                ringStart[ri]=vi; ringLen[ri]=r.length/2; ri++;
                for(int i=0;i<r.length;i+=2){ double lon=Math.toRadians(r[i]),lat=Math.toRadians(r[i+1]);
                    d[k++]=(float)(Math.cos(lat)*Math.sin(lon)); d[k++]=(float)Math.sin(lat); d[k++]=(float)(Math.cos(lat)*Math.cos(lon)); d[k++]=0; d[k++]=0; vi++; }
            }
            outline=ByteBuffer.allocateDirect(k*4).order(ByteOrder.nativeOrder()).asFloatBuffer(); outline.put(d,0,k).position(0);
        }

        private int newTexture(boolean mip){
            int[] id=new int[1]; GLES20.glGenTextures(1,id,0); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,id[0]);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,mip?GLES20.GL_LINEAR_MIPMAP_LINEAR:GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_REPEAT);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
            return id[0];
        }
        private void uploadTex(int id,Bitmap b,boolean mip){
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,id); GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,b,0);
            if(mip) GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);
        }

        private void buildSphere(){
            int lat=64,lon=128; float[] data=new float[lat*lon*6*5]; int k=0;
            for(int a=0;a<lat;a++){ float v0=(float)a/lat,v1=(float)(a+1)/lat;
                for(int b=0;b<lon;b++){ float u0=(float)b/lon,u1=(float)(b+1)/lon;
                    k=vertex(data,k,u0,v0);k=vertex(data,k,u1,v1);k=vertex(data,k,u1,v0);k=vertex(data,k,u0,v0);k=vertex(data,k,u0,v1);k=vertex(data,k,u1,v1); } }
            count=k/5; sphere=ByteBuffer.allocateDirect(k*4).order(ByteOrder.nativeOrder()).asFloatBuffer(); sphere.put(data,0,k).position(0);
        }
        private int vertex(float[] d,int k,float u,float v){
            double lon=(u-.5)*Math.PI*2,lat=(.5-v)*Math.PI;
            d[k++]=(float)(Math.cos(lat)*Math.sin(lon)); d[k++]=(float)Math.sin(lat); d[k++]=(float)(Math.cos(lat)*Math.cos(lon)); d[k++]=u; d[k++]=v; return k;
        }

        /** Old illustrative globe, shown only until the real polygon texture is ready. */
        private Bitmap makeFallbackBitmap(){
            Bitmap b=Bitmap.createBitmap(1024,512,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);c.drawColor(Color.rgb(8,35,65));Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(Color.rgb(26,115,91));p.setStyle(Paint.Style.FILL);
            polygon(c,p,new float[][]{{90,95},{130,65},{180,75},{205,110},{185,150},{160,175},{150,220},{120,200},{105,155}});
            polygon(c,p,new float[][]{{205,225},{245,245},{260,300},{245,365},{220,405},{200,350},{190,290}});
            polygon(c,p,new float[][]{{410,95},{450,70},{500,85},{525,120},{500,150},{455,155},{430,130}});
            polygon(c,p,new float[][]{{455,155},{520,150},{565,185},{555,250},{520,300},{490,275},{470,225}});
            polygon(c,p,new float[][]{{510,85},{585,55},{680,70},{760,95},{820,140},{790,195},{720,210},{670,180},{610,195},{565,155}});
            polygon(c,p,new float[][]{{690,270},{760,255},{825,285},{845,335},{810,375},{750,365},{705,330}});
            polygon(c,p,new float[][]{{820,365},{865,350},{900,370},{880,395},{840,390}});
            p.setColor(0x558DE3D0);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);for(int x=0;x<=1024;x+=64)c.drawLine(x,0,x,512,p);for(int y=0;y<=512;y+=64)c.drawLine(0,y,1024,y,p);
            return b;
        }
        private Bitmap makeMoonBitmap(){
            Bitmap b=Bitmap.createBitmap(512,256,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);c.drawColor(Color.rgb(112,119,126));Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);java.util.Random r=new java.util.Random(17);
            for(int i=0;i<180;i++){float x=r.nextInt(512),y=r.nextInt(256),rad=2+r.nextInt(15);p.setStyle(Paint.Style.FILL);p.setColor(i%2==0?0x55727A83:0x5599A0A7);c.drawCircle(x,y,rad,p);p.setColor(0x66747B83);p.setStyle(Paint.Style.STROKE);c.drawCircle(x,y,rad*.72f,p);}
            return b;
        }
        private void polygon(Canvas c,Paint p,float[][] pts){Path path=new Path();path.moveTo(pts[0][0],pts[0][1]);for(int i=1;i<pts.length;i++)path.lineTo(pts[i][0],pts[i][1]);path.close();c.drawPath(path,p);}
        private int program(String vs,String fs){int v=shader(GLES20.GL_VERTEX_SHADER,vs),f=shader(GLES20.GL_FRAGMENT_SHADER,fs),p=GLES20.glCreateProgram();GLES20.glAttachShader(p,v);GLES20.glAttachShader(p,f);GLES20.glBindAttribLocation(p,0,"aPos");GLES20.glBindAttribLocation(p,1,"aUv");GLES20.glLinkProgram(p);return p;}
        private int shader(int type,String src){int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,src);GLES20.glCompileShader(s);return s;}
    }
}

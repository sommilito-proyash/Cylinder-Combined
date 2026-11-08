#!/usr/bin/env python3
"""Cylinder Radar Launcher — geographic data-pack fetcher (stdlib only).

Downloads real boundary data into app/src/main/assets/geo so the APK works fully offline:
  * world-50m.json          Natural Earth 1:50m countries (TopoJSON via world-atlas; public domain)
  * <ISO3>_ADM1.geojson     geoBoundaries gbOpen divisions  (CC BY 4.0, (c) William & Mary geoLab)
  * <ISO3>_ADM2.geojson     geoBoundaries gbOpen districts

Usage:  python3 tools/fetch_geo_data.py [--out DIR] [--countries "BGD IND NPL"] [--levels "ADM1 ADM2"]
Missing files are fine: the app downloads and caches anything absent the first time it is needed.
"""
import argparse, json, os, sys, urllib.request, urllib.error

UA = {"User-Agent": "CylinderRadarLauncher-datapack/2.5"}
WORLD = [
    "https://cdn.jsdelivr.net/npm/world-atlas@2.0.2/countries-50m.json",
    "https://unpkg.com/world-atlas@2.0.2/countries-50m.json",
]

def get(url, timeout=90):
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.read()

def first_ok(urls, check):
    last = None
    for u in urls:
        try:
            b = get(u)
            if b.startswith(b"version https://git-lfs"):
                raise ValueError("git-lfs pointer, not data")
            check(json.loads(b.decode("utf-8")))
            return b, u
        except Exception as e:  # try the next mirror
            last = e
            print("  x %s -> %s" % (u, e))
    raise RuntimeError("all mirrors failed: %s" % last)

def check_topo(j):
    assert j.get("type") == "Topology" and j.get("arcs"), "not a TopoJSON world file"

def check_gj(j):
    assert j.get("type") == "FeatureCollection" and j.get("features"), "no features"

def admin_urls(iso, lvl):
    rel = "releaseData/gbOpen/%s/%s/geoBoundaries-%s-%s" % (iso, lvl, iso, lvl)
    urls = [
        "https://cdn.jsdelivr.net/gh/wmgeolab/geoBoundaries@main/%s_simplified.geojson" % rel,
        "https://raw.githubusercontent.com/wmgeolab/geoBoundaries/main/%s_simplified.geojson" % rel,
        "https://media.githubusercontent.com/media/wmgeolab/geoBoundaries/main/%s_simplified.geojson" % rel,
    ]
    try:  # official API gives the canonical simplified URL
        meta = json.loads(get("https://www.geoboundaries.org/api/current/gbOpen/%s/%s/" % (iso, lvl), 30).decode())
        if isinstance(meta, list): meta = meta[0]
        if meta.get("simplifiedGeometryGeoJSON"): urls.append(meta["simplifiedGeometryGeoJSON"])
    except Exception as e:
        print("  (api) %s" % e)
    return urls

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="app/src/main/assets/geo")
    ap.add_argument("--countries", default=os.environ.get("GEO_PACK", "BGD"))
    ap.add_argument("--levels", default="ADM1 ADM2")
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    ok = fail = 0
    print("== world countries (Natural Earth 1:50m)")
    try:
        b, u = first_ok(WORLD, check_topo)
        open(os.path.join(a.out, "world-50m.json"), "wb").write(b); ok += 1
        print("  OK %s (%d KB)" % (u, len(b) // 1024))
    except Exception as e:
        fail += 1; print("  FAILED: %s" % e)
    for iso in a.countries.replace(",", " ").split():
        for lvl in a.levels.split():
            print("== %s %s (geoBoundaries)" % (iso.upper(), lvl))
            try:
                b, u = first_ok(admin_urls(iso.upper(), lvl), check_gj)
                open(os.path.join(a.out, "%s_%s.geojson" % (iso.upper(), lvl)), "wb").write(b); ok += 1
                print("  OK %s (%d KB)" % (u, len(b) // 1024))
            except Exception as e:
                fail += 1; print("  FAILED: %s" % e)
    print("data pack: %d ok, %d failed" % (ok, fail))
    return 0  # never fail the build: the app falls back to on-device download

if __name__ == "__main__":
    sys.exit(main())

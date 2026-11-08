# Cylinder Radar Launcher — Smart Observatory Roadmap

## 1.6.0 — Foundation Preview
- Native Canvas Glass Cube / Mechanical Watch proof-of-concept.
- Android BiometricPrompt Vault option with PIN/Pattern fallback.
- Separate Activity to protect the existing cylinder renderer.

## 1.7 — Interactive Cube
- Six independently selectable cube faces.
- Improved 3D perspective, inertial rotation and touch hit regions.
- Front mechanical watch; back Earth panel; side weather and calendar panels; top world clock; bottom system/observatory status.

## Future — Earth & Space (after v1.9 prototype)
- Real 3D Earth rendering using a dedicated, performance-tested rendering module.
- Day/night terminator, country selection and offline cached base textures.
- Moon phase and basic solar-system visualization.

## Future — Cylinder Integration
- Smooth Observatory ↔ Cylinder transition.
- Preserve existing HOME / ALL APPS / VAULT state, icon burst, search, gestures and customization.
- Add optional Observatory as startup/home mode, not a forced replacement.

## Long-term — Smart Observatory Edition
- User-customizable cube faces and widgets.
- Battery/performance profiles and offline behavior.
- Accessibility, responsive layout and broad device testing.

## Engineering rules
- Do not replace the current LauncherView or cylinder geometry engine without a separate migration and regression test.
- Keep Observatory in its own Activity/module until interaction and performance are proven.
- Use Android BiometricPrompt; never store biometric templates. Preserve PIN/Pattern fallback.
- Avoid continuous high-frame-rate rendering when the scene is idle. Use event-driven invalidation and modest clock refresh.
- Build through GitHub Actions and test on the user's Xiaomi/Redmi/POCO device before declaring a release verified.


## অগ্রগতি: v1.8.0
- ছয়টি selectable panel ও drag interaction বজায় রেখে প্রতিটি panel-এর content আরও বাস্তবসম্মত করা হয়েছে।
- EARTH: offline stylized globe, latitude/longitude lines এবং reference-date-ভিত্তিক Moon age।
- CALENDAR: বর্তমান মাসের calendar grid ও আজকের তারিখ highlight।
- WORLD CLOCK: London, Dhaka, New York-এর timezone-aware clock।
- WEATHER: live provider না থাকায় কোনো ভুয়া forecast দেখানো হয় না।
- MOTION: cube-এর বর্তমান X/Y orientation দেখায়।
- Redraw 250ms; এতে watch seconds ও gear animation আগের তুলনায় মসৃণ, তবে battery/performance ফোনে যাচাই বাকি।
- পরবর্তী লক্ষ্য: বাস্তব 3D Earth texture/country picking এবং Glass Cube-এর face rendering উন্নয়ন।


## অগ্রগতি: v1.9.0
- EARTH panel-এ native Canvas-ভিত্তিক projected rotating sphere যোগ হয়েছে।
- আনুমানিক continent silhouettes, latitude/longitude grid, limb shading এবং UTC-hour-driven illustrative night-side overlay যুক্ত।
- সম্পূর্ণ offline; satellite texture/GIS-accurate boundary/country selection এখনো নেই।
- Version code 11; version name 1.9.0-earth-observatory।

## পরবর্তী উন্নয়ন
- Earth-এর জন্য OpenGL ES textured sphere বা নির্ভরযোগ্য offline texture/data source মূল্যায়ন।
- Country picking, zoom/orbit interaction এবং Moon-এর phase visualization।
- Observatory থেকে Cylinder Launcher-এ state-preserving transition।
- Android SDK-সহ GitHub Actions build এবং বাস্তব ফোনে performance/security regression test।


## অগ্রগতি: v2.0.0 — Moon Phase Observatory
- EARTH panel-এ reference new-moon epoch ও synodic month দিয়ে Moon age হিসাব UTC ভিত্তিতে normalize করা হয়েছে।
- আটটি প্রধান lunar phase label এবং offline illuminated-disc preview যোগ হয়েছে।
- Observatory refresh interval 100ms; battery/performance measurement এখনো বাকি।
- Main Launcher ও Vault security code অপরিবর্তিত রাখা হয়েছে।

## পরবর্তী ধাপ
- OpenGL ES-ভিত্তিক textured Earth prototype ও touch orbit/zoom।
- Country hit-testing-এর জন্য বাস্তব geographic dataset ও offline asset strategy।
- Cube face-এর দৃশ্যমান 3D mapping এবং Observatory ↔ Cylinder state-preserving navigation।
- GitHub Actions build + Xiaomi/Redmi/POCO-তে performance, battery ও security regression test।


## অগ্রগতি: v2.1.0 — Battery-Aware Observatory
- Observatory-তে persistent ECO toggle যুক্ত হয়েছে।
- Standard refresh 250ms (প্রায় 4 FPS), ECO refresh 1000ms (প্রায় 1 FPS)।
- Activity pause/background, view detach এবং window visibility অনুযায়ী animation callback lifecycle নিয়ন্ত্রিত।
- Main Launcher / Cylinder / Vault security source অপরিবর্তিত; biometric prompt ও PIN/Pattern fallback বজায়।
- Battery consumption-এর বাস্তব পরিমাপ এখনো Xiaomi/Redmi/POCO ফোনে করা হয়নি।

## পরবর্তী ধাপ
- OpenGL ES textured Earth prototype, touch orbit/zoom এবং offline asset strategy।
- Country selection-এর জন্য geographic dataset ও country information panel।
- Earth/Moon relationship visualization এবং Observatory ↔ Cylinder state-preserving transition।
- Android SDK-সহ GitHub Actions build এবং ফোনে performance/battery/security regression test।


## v2.2.0 — Earth & Moon Explorer prototype (2026-11-07)
- Added a separate native `EarthActivity` using OpenGL ES 2.0 and `GLSurfaceView`; no WebView dependency.
- Added local procedural/illustrative Earth and Moon textures, sphere mesh, drag rotation, pinch zoom and reset.
- Added approximate tap-based country information for 10 countries. This is not a political-boundary/GIS map and not satellite imagery.
- Added ECO render cadence (250ms Standard / 1000ms ECO) and stop/resume render scheduling with Activity lifecycle.
- Preserved MainActivity, LauncherView and LockUi byte-for-byte against the v1.9.0 baseline; Store only contains the earlier ECO preference addition.
- Remaining: proper geospatial country polygons and selected-country highlighting, search + camera flight, all-country dataset, realistic imagery, physically meaningful Earth-Moon orbit, live weather, and device runtime validation.
- Build limitation: Android SDK is not installed in this environment; source/ZIP/XML checks do not establish a successful APK build.


## v2.3.0 — Smart Earth Navigation
- বাংলা/ইংরেজি country search for 10 bundled countries.
- Search-to-country approximate camera orientation and zoom.
- Auto-rotation pauses on selection; user can resume or reset.
- Center target marker.
- Remaining: global authoritative country polygons, district/province boundary layers, reliable tap picking, country dataset expansion, real-device QA.


## v2.3.0 — Smart Earth Navigation (2026-11-07)
- Earth screen-এ বাংলা/ইংরেজি AutoComplete country search (১০টি bundled country)।
- Search selection আনুমানিক country-centre-এ globe orientation ও zoom করে।
- Country focus-এর পর auto-rotation pause; manual toggle ও Reset View support।
- Center target marker এবং selected-country summary।
- Next: authoritative country polygons, all-country dataset, district/province boundaries, robust 3D picking, offline geographic data packs, device QA।


## v2.4.0 — Country Navigation Expansion (2026-11-07)
- বাংলা/ইংরেজি Search dataset ১০ থেকে ৩৬টি দেশে সম্প্রসারণ; প্রতিটি দেশের জন্য আনুমানিক bounding box, রাজধানী, আয়তন ও অঞ্চল।
- overlapping bounding box-এ tap lookup-এর জন্য smallest-containing-box priority যোগ।
- Search focus, auto-rotation pause/resume, reset ও ECO lifecycle behavior বজায়।
- MainActivity, LauncherView, Store, LockUi ও Manifest অপরিবর্তিত; Vault biometric + PIN/Pattern fallback-এ কোনো পরিবর্তন নেই।
- Limitation: bounding-box lookup প্রকৃত country polygon নয়; globe texture illustrative; 3D political boundary/highlight এবং ADM1/ADM2 layer এখনো বাকি।
- EarthExplorer_v4_0 reference থেকে TopoJSON/geoBoundaries fetching, simplification, caching architecture নোট করা হয়েছে; browser CDN code সরাসরি Native Android-এ port করা হয়নি।
- Next: offline Natural Earth country polygon asset + GLES line/polygon rendering prototype; এরপর Bangladesh ADM1/ADM2 data pack feasibility।

## v2.5.0 — Real 3D Earth (সম্পন্ন কোড; ডিভাইসে যাচাই বাকি)
- ✅ প্রকৃত দেশ-পলিগন গ্লোব (Natural Earth ১:৫০ মি.), নির্বাচিত দেশের সোনালি raised হাইলাইট, নির্ভুল polygon ট্যাপ।
- ✅ বাংলাদেশসহ ৮৩টি দেশের ADM1/ADM2 (geoBoundaries) — Native Canvas মানচিত্র, জুম/প্যান/ট্যাপ।
- ✅ অফলাইন ডেটা-প্যাক (asset → cache → network) ও CI-তে স্বয়ংক্রিয় ডেটা ডাউনলোড।
- পরবর্তী: v2.6.0 — সব দেশের বাংলা নাম/রাজধানী/আয়তন ডেটা-প্যাক; ADM2 ম্যাপে অনুসন্ধান; বাংলাদেশ উপজেলা (ADM3); জেলার তথ্য কার্ড।

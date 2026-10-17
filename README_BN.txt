Cylinder Radar Launcher — Combined Edition 1.1.0
================================================

এই সংস্করণটি আপনার দেওয়া CylinderRadarLauncher-v1.zip ও v0.2.0 ফাইলগুলো পর্যালোচনা করে তৈরি করা একক Android Studio প্রজেক্ট।

কেন v1 ইঞ্জিন রাখা হয়েছে
-----------------------
- v1-এ আলাদা Store, AppItem, LauncherView ও MainActivity আছে; এতে 3D cylinder rendering, perspective/depth, 60fps animation, inertia/snap, digital/analog clock এবং app long-press actions অনেক বেশি পূর্ণাঙ্গ।
- v0.2.0-এর পৃথক canvas/gesture engine সরাসরি মেশানো হয়নি—দুটি UI ইঞ্জিন একত্র করলে touch-state, navigation ও clock rendering পরস্পরের সঙ্গে সংঘাত করতে পারত।
- v0.2.0 থেকে ব্যবহারযোগ্য bulk selection সুবিধা যুক্ত হয়েছে: Settings থেকে Home cylinder-এর অ্যাপ একসঙ্গে বেছে নেওয়া যায় এবং পাসওয়ার্ড যাচাই করে Vault app list ম্যানেজ করা যায়।
- পুরোনো v0.2.0 build-এর প্যাকেজ-ভিত্তিক Home/Hidden তালিকা ও PIN hash পাওয়া গেলে স্বয়ংক্রিয়ভাবে রূপান্তর করা হয়; প্রথম সফল পুরোনো PIN ব্যবহারে সেটি PBKDF2 hash-এ আপগ্রেড হয়।
- v1-এর PBKDF2 password hashing, failed-attempt throttling, vault auto-lock ও FLAG_SECURE আচরণ রাখা/সংশোধন করা হয়েছে।

মূল ফিচার
---------
1. HOME cylinder-এ কেবল নির্বাচিত/pinned অ্যাপ দেখা যায়। প্রথমবার কয়েকটি সাধারণ অ্যাপ ডিফল্টভাবে pin হয়; পরে Settings থেকে পুরো তালিকা বেছে নিতে পারবেন।
2. ALL APPS cylinder-এ Vault-এ লুকানো নয় এমন অ্যাপ দেখা যায়।
3. ঘড়ি বা নিচের page-label অঞ্চলে দ্রুত পরপর দুইবার ডানদিকে সোয়াইপ করলে Vault password screen খুলবে। প্রথমবার শক্তিশালী password সেট করতে হবে।
4. Vault app manager-এর নামতালিকা দেখতে password যাচাই আবশ্যক। ভুল password ৫ বার দিলে ক্রমবর্ধমান সময়ের জন্য সাময়িক লক হয়।
5. Vault খোলা থাকলে Android FLAG_SECURE ব্যবহার হয়। ৬০ সেকেন্ড নিষ্ক্রিয় থাকলে, Home/Back চাপলে, অ্যাপ খুললে বা launcher ছাড়লে Vault lock হয়; Vault-এ স্পর্শ করলে idle timer নতুন করে শুরু হয়। VAULT থেকে অন্য cylinder-এ transition সম্পূর্ণ না হওয়া পর্যন্ত screenshot protection থাকে।
6. Digital clock-এ seconds counter ও 60-tick floating light orbit; Analog mode-এ প্রতি millisecond অনুযায়ী second hand sweep ও light trail।
7. সিলিন্ডার drag করলে rotation, ছাড়লে inertia/snap; page transition-এ layered cylinder motion।
8. অ্যাপে long-press: Pin/Unpin, Hide in Vault, App info, Uninstall। Vault-এ long-press: Unhide, Open, App info।
9. Accent color বদলানো এবং Android Default Home app settings খোলা যায়।

Gesture
-------
- সিলিন্ডার আইকন-এলাকায় ডানে/বামে drag: সিলিন্ডার ঘোরানো।
- যেকোনো খালি উপরের/নিচের এলাকায় ওপর/নিচে swipe: HOME ↔ ALL APPS।
- ঘড়ি বা নিচের page label অংশে বামে swipe: পরের cylinder।
- যেকোনো অঞ্চলে দ্রুত পরপর ডানদিকে দুইবার swipe: Vault password (দুই swipe-এর মধ্যে প্রায় ১.১ সেকেন্ডের কম বিরতি)।
- App icon long-press: app actions।
- খালি জায়গায় long-press অথবা উপরের ডানদিকের ⋮: Settings।

সিকিউরিটি ও বাস্তব সীমাবদ্ধতা
----------------------------
- MIUI/HyperOS সিস্টেম লঞ্চারের নিজস্ব Hidden Apps তালিকা বা তার password তৃতীয় পক্ষের launcher পড়তে পারে না। পুরোনো hidden app-গুলো ALL APPS-এ দেখা গেলে এই লঞ্চারের Settings থেকে সেগুলো Vault-এ আলাদাভাবে নির্বাচন করুন।
- Vault লঞ্চারের ভেতরে আইকন লুকায়; অ্যাপ uninstall বা encrypt করে না এবং অন্য লঞ্চার/Android Settings থেকে লুকায় না। ব্যাংকিং বা সংবেদনশীল অ্যাপের জন্য Xiaomi/Android App Lock চালু রাখুন।
- নতুন CI runner থেকে তৈরি Debug APK-এর signing key ভিন্ন হলে পুরোনো debug APK-এর ওপর update install নাও হতে পারে। এমন হলে পুরোনো test build uninstall করতে হতে পারে; uninstall করলে launcher-এর local settings/password মুছে যাবে। ডিফল্ট launcher পরিবর্তনের আগে এটি বিবেচনা করুন।
- এখানে Android SDK নেই; তাই APK compile করা হয়নি। GitHub Actions workflow দিয়ে প্রকৃত ক্লাউড বিল্ড করে APK artifact যাচাই করতে হবে।

APK তৈরি — Android Studio
-------------------------
1. ZIP extract করুন। Android Studio-তে এই প্রজেক্টের root খুলুন (যে ফোল্ডারে settings.gradle, build.gradle ও gradlew আছে)।
2. Java/JDK 17 ও Android SDK Platform 35 ইনস্টল/নির্বাচন করুন।
3. Gradle Sync শেষ হলে Build > Build Bundle(s) / APK(s) > Build APK(s) চালান।
4. APK: app/build/outputs/apk/debug/app-debug.apk

GitHub Actions দিয়ে APK (ফোন থেকে)
---------------------------------
সবচেয়ে ভালো: ZIP extract করে প্রজেক্টের ফাইলগুলো GitHub repository root-এ রাখুন। `.github/workflows/build.yml` ফাইলটিও root-এর অধীনে থাকতে হবে। Commit/push করার পর Actions > Build APK খুলুন। বিল্ড সফল হলে run-এর নিচের Artifacts থেকে `CylinderRadarLauncher-APK` ZIP নামিয়ে ভেতর থেকে `app-debug.apk` নিন।

যদি মোবাইলের GitHub UI-তে folder upload করা না যায়:
1. repository root-এ এই complete ZIP-টি `CylinderRadarLauncher-Combined-v1.1.0.zip` নামে upload করুন।
2. তারপর GitHub-এ `Add file > Create new file` নির্বাচন করে `.github/workflows/build-from-zip.yml` বানান।
3. `GITHUB-CLOUD-BUILD.txt` ফাইল থেকে workflow snippet কপি করুন। এটি ZIP extract করে build চালাবে।
4. Commit করুন, Actions-এ রান সফল হলে Artifact ডাউনলোড করুন।

Install করার পরে
----------------
Settings > Apps > Default apps > Home app থেকে Cylinder Radar Launcher নির্বাচন করুন। Xiaomi/Redmi/POCO MIUI/HyperOS-এ menu label সামান্য আলাদা হতে পারে। প্রথমে সাধারণ অ্যাপ, HOME selection, clock, swipe ও Vault পরীক্ষা করুন; পরীক্ষা শেষ না হওয়া পর্যন্ত পুরোনো launcher remove করবেন না।

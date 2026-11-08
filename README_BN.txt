Cylinder Radar Launcher 2.5.0 — Real 3D Earth
=============================================
Smart Observatory → EARTH প্যানেলে এখন বাস্তব দেশসীমানা, নির্বাচিত দেশের সোনালি 3D হাইলাইট এবং বিভাগ/জেলা মানচিত্র।

APK কীভাবে বানাবেন (সবচেয়ে সহজ)
1. GitHub repository-তে ZIP-এর সব ফাইল (settings.gradle, app/, tools/, .github/ সহ) আপলোড করুন।
2. Actions → "Build APK" → Run workflow। চাইলে geo_pack-এ দেশের কোড দিন, যেমন: BGD IND NPL (ডিফল্ট BGD)।
3. শেষ হলে Artifacts → CylinderRadarLauncher-APK ডাউনলোড করুন।
   workflow নিজেই বাস্তব ভূগোল-ডেটা নামিয়ে APK-র ভেতরে রাখে, ফলে প্রথম থেকেই অফলাইনে চলে।
   (ডেটা নামাতে না পারলেও build সফল হয়; অ্যাপ প্রথম চালুতে নিজে ডাউনলোড ও সংরক্ষণ করবে।)

ব্যবহার
- Observatory → EARTH খুলুন। প্রথমবার ডেটা না থাকলে ওপরে "লোড হচ্ছে…" দেখাবে; ব্যর্থ হলে ওই লেখায় ট্যাপ করুন।
- দেশে ট্যাপ করুন বা নাম (বাংলা/English) লিখে খুঁজুন → দেশটি সোনালি হয়ে উঠবে, গ্লোব সেদিকে ঘুরে জুম করবে।
- নিচের "বিভাগ ও জেলা মানচিত্র ›" চাপলে ADM1/ADM2 মানচিত্র খুলবে। বাংলাদেশ: ৮ বিভাগ, ৬৪ জেলা (বাংলা নামসহ)।
- AUTO ROTATE, RESET VIEW, ECO আগের মতো।

ডেটা-প্যাক নিজে বানাতে চাইলে: python3 tools/fetch_geo_data.py --countries "BGD IND"   (Windows: tools\fetch_geo_data.bat)
ফাইলগুলো app/src/main/assets/geo/-তে যায়।

ক্রেডিট ও লাইসেন্স
- Natural Earth (পাবলিক ডোমেইন), world-atlas প্যাকেজের মাধ্যমে।
- geoBoundaries gbOpen — CC BY 4.0, © William & Mary geoLab। ব্যবহারে উৎসের স্বীকৃতি আবশ্যক (অ্যাপের তথ্য-প্যানেলে দেওয়া আছে)।

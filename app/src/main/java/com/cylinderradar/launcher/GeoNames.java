package com.cylinderradar.launcher;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Static name tables: ISO codes for admin-map lookup, Bangla country names, Bangladesh division/district names. No Android dependencies. */
final class GeoNames {
    private GeoNames() {}

    /** {ISO numeric (world-atlas id), ISO alpha-3 (geoBoundaries), world-atlas English name} */
    static final String[][] IDS = {
        {"050","BGD","Bangladesh"},{"356","IND","India"},{"156","CHN","China"},{"840","USA","United States of America"},
        {"076","BRA","Brazil"},{"036","AUS","Australia"},{"818","EGY","Egypt"},{"826","GBR","United Kingdom"},
        {"392","JPN","Japan"},{"250","FRA","France"},{"586","PAK","Pakistan"},{"524","NPL","Nepal"},{"064","BTN","Bhutan"},
        {"144","LKA","Sri Lanka"},{"360","IDN","Indonesia"},{"458","MYS","Malaysia"},{"764","THA","Thailand"},
        {"704","VNM","Vietnam"},{"410","KOR","South Korea"},{"682","SAU","Saudi Arabia"},{"784","ARE","United Arab Emirates"},
        {"792","TUR","Turkey"},{"276","DEU","Germany"},{"380","ITA","Italy"},{"724","ESP","Spain"},{"578","NOR","Norway"},
        {"752","SWE","Sweden"},{"643","RUS","Russia"},{"484","MEX","Mexico"},{"124","CAN","Canada"},{"032","ARG","Argentina"},
        {"152","CHL","Chile"},{"710","ZAF","South Africa"},{"566","NGA","Nigeria"},{"404","KEN","Kenya"},{"554","NZL","New Zealand"},
        {"004","AFG","Afghanistan"},{"364","IRN","Iran"},{"368","IRQ","Iraq"},{"376","ISR","Israel"},{"400","JOR","Jordan"},
        {"422","LBN","Lebanon"},{"760","SYR","Syria"},{"887","YEM","Yemen"},{"512","OMN","Oman"},{"634","QAT","Qatar"},
        {"414","KWT","Kuwait"},{"104","MMR","Myanmar"},{"116","KHM","Cambodia"},{"418","LAO","Laos"},{"608","PHL","Philippines"},
        {"702","SGP","Singapore"},{"496","MNG","Mongolia"},{"398","KAZ","Kazakhstan"},{"860","UZB","Uzbekistan"},
        {"408","PRK","North Korea"},{"462","MDV","Maldives"},{"804","UKR","Ukraine"},{"616","POL","Poland"},
        {"528","NLD","Netherlands"},{"056","BEL","Belgium"},{"756","CHE","Switzerland"},{"040","AUT","Austria"},
        {"620","PRT","Portugal"},{"300","GRC","Greece"},{"372","IRL","Ireland"},{"208","DNK","Denmark"},{"246","FIN","Finland"},
        {"203","CZE","Czechia"},{"348","HUN","Hungary"},{"642","ROU","Romania"},{"231","ETH","Ethiopia"},{"834","TZA","Tanzania"},
        {"800","UGA","Uganda"},{"288","GHA","Ghana"},{"504","MAR","Morocco"},{"012","DZA","Algeria"},{"434","LBY","Libya"},
        {"729","SDN","Sudan"},{"170","COL","Colombia"},{"604","PER","Peru"},{"862","VEN","Venezuela"},{"192","CUB","Cuba"}
    };
    private static final Map<String, String> ISO3_BY_NUM = new HashMap<>(), ISO3_BY_NAME = new HashMap<>();
    static {
        for (String[] r : IDS) { ISO3_BY_NUM.put(r[0], r[1]); ISO3_BY_NAME.put(norm(r[2]), r[1]); }
        ISO3_BY_NAME.put(norm("USA"), "USA"); ISO3_BY_NAME.put(norm("United States"), "USA"); ISO3_BY_NAME.put(norm("Czech Republic"), "CZE");
    }
    /** world-atlas ids are ISO-numeric strings (possibly without leading zeros). */
    static String iso3(String numericId, String englishName) {
        if (numericId != null && !numericId.isEmpty()) {
            String id = numericId;
            while (id.length() < 3) id = "0" + id;
            String r = ISO3_BY_NUM.get(id);
            if (r != null) return r;
        }
        return englishName == null ? null : ISO3_BY_NAME.get(norm(englishName));
    }

    static String norm(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) { char c = Character.toLowerCase(s.charAt(i)); if (Character.isLetterOrDigit(c)) b.append(c); }
        return b.toString();
    }

    // ---------------------------------------------------------------- Bangla country names
    private static final String[] WORLD_BN = {
        "Afghanistan","আফগানিস্তান","Albania","আলবেনিয়া","Algeria","আলজেরিয়া","Angola","অ্যাঙ্গোলা","Argentina","আর্জেন্টিনা","Armenia","আর্মেনিয়া",
        "Australia","অস্ট্রেলিয়া","Austria","অস্ট্রিয়া","Azerbaijan","আজারবাইজান","Bahamas","বাহামা","Bahrain","বাহরাইন","Bangladesh","বাংলাদেশ",
        "Belarus","বেলারুশ","Belgium","বেলজিয়াম","Belize","বেলিজ","Benin","বেনিন","Bhutan","ভুটান","Bolivia","বলিভিয়া",
        "Bosnia and Herz.","বসনিয়া ও হার্জেগোভিনা","Botswana","বতসোয়ানা","Brazil","ব্রাজিল","Brunei","ব্রুনেই","Bulgaria","বুলগেরিয়া",
        "Burkina Faso","বুরকিনা ফাসো","Burundi","বুরুন্ডি","Cambodia","কম্বোডিয়া","Cameroon","ক্যামেরুন","Canada","কানাডা",
        "Central African Rep.","মধ্য আফ্রিকান প্রজাতন্ত্র","Chad","চাদ","Chile","চিলি","China","চীন","Colombia","কলম্বিয়া","Congo","কঙ্গো",
        "Costa Rica","কোস্টারিকা","Croatia","ক্রোয়েশিয়া","Cuba","কিউবা","Cyprus","সাইপ্রাস","Czechia","চেকিয়া",
        "Dem. Rep. Congo","গণতান্ত্রিক কঙ্গো প্রজাতন্ত্র","Denmark","ডেনমার্ক","Djibouti","জিবুতি","Dominican Rep.","ডমিনিকান প্রজাতন্ত্র",
        "Ecuador","ইকুয়েডর","Egypt","মিশর","El Salvador","এল সালভাদর","Eq. Guinea","নিরক্ষীয় গিনি","Eritrea","ইরিত্রিয়া","Estonia","এস্তোনিয়া",
        "eSwatini","ইসোয়াতিনি","Ethiopia","ইথিওপিয়া","Fiji","ফিজি","Finland","ফিনল্যান্ড","France","ফ্রান্স","Gabon","গ্যাবন","Gambia","গাম্বিয়া",
        "Georgia","জর্জিয়া","Germany","জার্মানি","Ghana","ঘানা","Greece","গ্রিস","Greenland","গ্রিনল্যান্ড","Guatemala","গুয়াতেমালা",
        "Guinea","গিনি","Guinea-Bissau","গিনি-বিসাউ","Guyana","গায়ানা","Haiti","হাইতি","Honduras","হন্ডুরাস","Hungary","হাঙ্গেরি",
        "Iceland","আইসল্যান্ড","India","ভারত","Indonesia","ইন্দোনেশিয়া","Iran","ইরান","Iraq","ইরাক","Ireland","আয়ারল্যান্ড","Israel","ইসরায়েল",
        "Italy","ইতালি","Ivory Coast","আইভরি কোস্ট","Côte d'Ivoire","আইভরি কোস্ট","Jamaica","জামাইকা","Japan","জাপান","Jordan","জর্ডান",
        "Kazakhstan","কাজাখস্তান","Kenya","কেনিয়া","Kosovo","কসোভো","Kuwait","কুয়েত","Kyrgyzstan","কিরগিজস্তান","Laos","লাওস","Latvia","লাটভিয়া",
        "Lebanon","লেবানন","Lesotho","লেসোথো","Liberia","লাইবেরিয়া","Libya","লিবিয়া","Lithuania","লিথুয়ানিয়া","Luxembourg","লুক্সেমবার্গ",
        "Madagascar","মাদাগাস্কার","Malawi","মালাউই","Malaysia","মালয়েশিয়া","Maldives","মালদ্বীপ","Mali","মালি","Malta","মাল্টা",
        "Mauritania","মৌরিতানিয়া","Mexico","মেক্সিকো","Moldova","মলদোভা","Mongolia","মঙ্গোলিয়া","Montenegro","মন্টিনিগ্রো","Morocco","মরক্কো",
        "Mozambique","মোজাম্বিক","Myanmar","মিয়ানমার","Namibia","নামিবিয়া","Nepal","নেপাল","Netherlands","নেদারল্যান্ডস","New Caledonia","নিউ ক্যালেডোনিয়া",
        "New Zealand","নিউজিল্যান্ড","Nicaragua","নিকারাগুয়া","Niger","নাইজার","Nigeria","নাইজেরিয়া","North Korea","উত্তর কোরিয়া",
        "North Macedonia","উত্তর ম্যাসেডোনিয়া","Macedonia","উত্তর ম্যাসেডোনিয়া","Norway","নরওয়ে","Oman","ওমান","Pakistan","পাকিস্তান","Palestine","ফিলিস্তিন",
        "Panama","পানামা","Papua New Guinea","পাপুয়া নিউ গিনি","Paraguay","প্যারাগুয়ে","Peru","পেরু","Philippines","ফিলিপাইন","Poland","পোল্যান্ড",
        "Portugal","পর্তুগাল","Qatar","কাতার","Romania","রোমানিয়া","Russia","রাশিয়া","Rwanda","রুয়ান্ডা","Saudi Arabia","সৌদি আরব","Senegal","সেনেগাল",
        "Serbia","সার্বিয়া","Sierra Leone","সিয়েরা লিওন","Singapore","সিঙ্গাপুর","Slovakia","স্লোভাকিয়া","Slovenia","স্লোভেনিয়া","Solomon Is.","সলোমন দ্বীপপুঞ্জ",
        "Somalia","সোমালিয়া","Somaliland","সোমালিল্যান্ড","South Africa","দক্ষিণ আফ্রিকা","South Korea","দক্ষিণ কোরিয়া","S. Sudan","দক্ষিণ সুদান",
        "Spain","স্পেন","Sri Lanka","শ্রীলঙ্কা","Sudan","সুদান","Suriname","সুরিনাম","Sweden","সুইডেন","Switzerland","সুইজারল্যান্ড","Syria","সিরিয়া",
        "Taiwan","তাইওয়ান","Tajikistan","তাজিকিস্তান","Tanzania","তানজানিয়া","Thailand","থাইল্যান্ড","Timor-Leste","পূর্ব তিমুর","Togo","টোগো",
        "Trinidad and Tobago","ত্রিনিদাদ ও টোবাগো","Tunisia","তিউনিসিয়া","Turkey","তুরস্ক","Turkmenistan","তুর্কমেনিস্তান","Uganda","উগান্ডা",
        "Ukraine","ইউক্রেন","United Arab Emirates","সংযুক্ত আরব আমিরাত","United Kingdom","যুক্তরাজ্য","United States of America","যুক্তরাষ্ট্র",
        "Uruguay","উরুগুয়ে","Uzbekistan","উজবেকিস্তান","Vanuatu","ভানুয়াতু","Venezuela","ভেনেজুয়েলা","Vietnam","ভিয়েতনাম","W. Sahara","পশ্চিম সাহারা",
        "Yemen","ইয়েমেন","Zambia","জাম্বিয়া","Zimbabwe","জিম্বাবুয়ে","Antarctica","অ্যান্টার্কটিকা","Falkland Is.","ফকল্যান্ড দ্বীপপুঞ্জ","N. Cyprus","উত্তর সাইপ্রাস"
    };
    private static final Map<String, String> BN = new HashMap<>();
    static { for (int i = 0; i + 1 < WORLD_BN.length; i += 2) BN.put(norm(WORLD_BN[i]), WORLD_BN[i + 1]); }
    /** Bangla name for a world-atlas English name, or null if unknown. */
    static String countryBn(String en) { return en == null ? null : BN.get(norm(en)); }

    // ---------------------------------------------------------------- Bangladesh divisions / districts
    private static final String[] BD = {
        // divisions
        "Dhaka|Dhaka Division","ঢাকা","Chittagong|Chattogram|Chattogram Division|Chittagong Division","চট্টগ্রাম","Rajshahi|Rajshahi Division","রাজশাহী",
        "Khulna|Khulna Division","খুলনা","Barisal|Barishal|Barisal Division|Barishal Division","বরিশাল","Sylhet|Sylhet Division","সিলেট",
        "Rangpur|Rangpur Division","রংপুর","Mymensingh|Mymensingh Division","ময়মনসিংহ",
        // Dhaka division districts
        "Faridpur","ফরিদপুর","Gazipur","গাজীপুর","Gopalganj","গোপালগঞ্জ","Kishoreganj|Kishorganj","কিশোরগঞ্জ","Madaripur","মাদারীপুর","Manikganj|Manikgonj","মানিকগঞ্জ",
        "Munshiganj|Munshigonj","মুন্সিগঞ্জ","Narayanganj|Narayangonj","নারায়ণগঞ্জ","Narsingdi|Narsinghdi","নরসিংদী","Rajbari","রাজবাড়ী","Shariatpur","শরীয়তপুর","Tangail","টাঙ্গাইল",
        // Chattogram division
        "Bandarban","বান্দরবান","Brahmanbaria|Brahamanbaria|Brahmanbariya","ব্রাহ্মণবাড়িয়া","Chandpur","চাঁদপুর","Comilla|Cumilla","কুমিল্লা","Cox's Bazar|Coxs Bazar|Cox's Bazaar","কক্সবাজার",
        "Feni","ফেনী","Khagrachari|Khagrachhari|Khagrachhari","খাগড়াছড়ি","Lakshmipur|Laxmipur|Lakshipur","লক্ষ্মীপুর","Noakhali","নোয়াখালী","Rangamati","রাঙ্গামাটি",
        // Rajshahi division
        "Bogra|Bogura","বগুড়া","Joypurhat|Jaipurhat","জয়পুরহাট","Naogaon","নওগাঁ","Natore","নাটোর","Nawabganj|Chapai Nawabganj|Chapainawabganj|Chapai Nababganj|Nawabgonj","চাঁপাইনবাবগঞ্জ",
        "Pabna","পাবনা","Sirajganj|Sirajgonj","সিরাজগঞ্জ",
        // Khulna division
        "Bagerhat","বাগেরহাট","Chuadanga","চুয়াডাঙ্গা","Jessore|Jashore","যশোর","Jhenaidah|Jhenidah","ঝিনাইদহ","Kushtia","কুষ্টিয়া","Magura","মাগুরা","Meherpur","মেহেরপুর",
        "Narail","নড়াইল","Satkhira","সাতক্ষীরা",
        // Barishal division
        "Barguna","বরগুনা","Bhola","ভোলা","Jhalokati|Jhalakati|Jhalokathi","ঝালকাঠি","Patuakhali","পটুয়াখালী","Pirojpur","পিরোজপুর",
        // Sylhet division
        "Habiganj|Hobiganj","হবিগঞ্জ","Maulvibazar|Moulvibazar|Moulvi Bazar|Maulavibazar","মৌলভীবাজার","Sunamganj|Sunamgonj","সুনামগঞ্জ",
        // Rangpur division
        "Dinajpur","দিনাজপুর","Gaibandha","গাইবান্ধা","Kurigram","কুড়িগ্রাম","Lalmonirhat|Lalmanirhat","লালমনিরহাট","Nilphamari","নীলফামারী","Panchagarh|Panchagar","পঞ্চগড়","Thakurgaon","ঠাকুরগাঁও",
        // Mymensingh division
        "Jamalpur","জামালপুর","Netrakona|Netrokona","নেত্রকোণা","Sherpur","শেরপুর"
    };
    private static final Map<String, String> BDMAP = new HashMap<>();
    static {
        for (int i = 0; i + 1 < BD.length; i += 2) for (String v : BD[i].split("\\|")) BDMAP.put(norm(v), BD[i + 1]);
        // names that are both a division and a district in English (e.g. "Dhaka") share the same Bangla spelling.
    }
    static String bdBn(String en) { String r = en == null ? null : BDMAP.get(norm(en)); if (r == null && en != null) { String n = norm(en).replace("division", "").replace("district", ""); r = BDMAP.get(n); } return r; }

    /** Western digits -> Bangla digits. */
    static String bnDigits(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) { char c = s.charAt(i); b.append(c >= '0' && c <= '9' ? (char) ('০' + (c - '0')) : c); }
        return b.toString();
    }
    static String bnDigits(long n) { return bnDigits(String.valueOf(n)); }
    static String lower(String s) { return s == null ? "" : s.toLowerCase(Locale.ROOT); }
}

package com.cylinderradar.launcher;

import android.graphics.Bitmap;

public class AppItem {
    public String title;
    public String packageName;
    public String className;
    public String key; // স্টোর এবং পিন করার জন্য ইউনিক কি
    public String pkg; // প্যাকেজ নামের শর্ট রূপ বা রেফারেন্স
    public Bitmap icon;

    public float angle;
    public float y;

    public boolean isFavorite = false;
    public String category = "General";

    public float scatterX, scatterY, scatterZ;
    public float animProgress = 1f;

    public AppItem(String title, String packageName, String className, Bitmap icon) {
        this.title = title;
        this.packageName = packageName;
        this.pkg = packageName; // দুটো ফিল্ড সেইম রাখা হলো যাতে এরর না আসে
        this.className = className;
        this.key = packageName + "/" + className;
        this.icon = icon;
    }
}
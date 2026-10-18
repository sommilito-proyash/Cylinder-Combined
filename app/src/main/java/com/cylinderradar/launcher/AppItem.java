package com.cylinderradar.launcher;

import android.graphics.Bitmap;

public class AppItem {
    public String title;
    public String packageName;
    public String className;
    public Bitmap icon;

    // সিলিন্ডারে পজিশন
    public float angle;
    public float y;

    // নতুন ফিচার: ফেভারিট এবং ক্যাটাগরি
    public boolean isFavorite = false;
    public String category = "General";

    // অ্যানিমেশন প্রোপার্টি (স্ক্যাটারড বা বিক্ষিপ্ত অবস্থা থেকে আসার জন্য)
    public float scatterX, scatterY, scatterZ;
    public float animProgress = 1f;

    public AppItem(String title, String packageName, String className, Bitmap icon) {
        this.title = title;
        this.packageName = packageName;
        this.className = className;
        this.icon = icon;
    }
}
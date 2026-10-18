package com.cylinderradar.launcher;

import android.content.Context;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.LinearLayout;

public class LockUi {
    private MainActivity a;

    public LockUi(MainActivity activity) {
        this.a = activity;
    }

    // ডিপি থেকে পিক্সেল কনভার্ট করার মেথড (যা missing ছিল)
    public int dp(int dpValue) {
        return (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dpValue,
            a.getResources().getDisplayMetrics()
        );
    }
}
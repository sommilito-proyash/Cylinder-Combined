package com.cylinderradar.launcher;

import android.content.ComponentName;
import android.graphics.Bitmap;

final class AppItem {
    final String key;          // flattened ComponentName
    final String label;
    final String pkg;
    final ComponentName cn;
    final Bitmap icon;

    AppItem(ComponentName cn, String label, Bitmap icon) {
        this.cn = cn;
        this.key = cn.flattenToString();
        this.pkg = cn.getPackageName();
        this.label = label;
        this.icon = icon;
    }
}

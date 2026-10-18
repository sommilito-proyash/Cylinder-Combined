package com.cylinderradar.launcher;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.widget.FrameLayout;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private List<AppItem> appList = new ArrayList<>();
    private LauncherView launcherView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        loadInstalledApps();

        FrameLayout layout = new FrameLayout(this);
        launcherView = new LauncherView(this, appList);
        layout.addView(launcherView);

        setContentView(layout);
    }

    private void loadInstalledApps() {
        PackageManager pm = getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> resolveInfos = pm.queryIntentActivities(intent, 0);
        for (ResolveInfo info : resolveInfos) {
            String title = info.loadLabel(pm).toString();
            String packageName = info.activityInfo.packageName;
            String className = info.activityInfo.name;
            Drawable iconDrawable = info.loadIcon(pm);
            Bitmap icon = drawableToBitmap(iconDrawable);

            appList.add(new AppItem(title, packageName, className, icon));
        }
    }

    private Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        }
        Bitmap bitmap = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    @Override
    public void onBackPressed() {
        // হোম লঞ্চার হওয়ায় ব্যাক বাটন নিষ্ক্রিয় রাখা হয়েছে
    }
}
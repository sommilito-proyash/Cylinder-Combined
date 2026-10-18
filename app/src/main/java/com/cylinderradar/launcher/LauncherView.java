package com.cylinderradar.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class LauncherView extends View {
    private List<AppItem> appList;
    private List<AppItem> filteredList;
    private float cylinderRotation = 0f;
    private float scrollY = 0f;
    private float lastTouchY;
    private float lastTouchX;
    
    private float cylinderOpenProgress = 0f; 
    private boolean isAnimatingOpen = false;

    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Random random = new Random();
    private String searchQuery = "";

    public LauncherView(Context context, List<AppItem> appList) {
        super(context);
        this.appList = appList;
        this.filteredList = appList;
        initRandomScatter();
    }

    private void initRandomScatter() {
        for (AppItem item : appList) {
            item.scatterX = (random.nextFloat() - 0.5f) * 800f;
            item.scatterY = (random.nextFloat() - 0.5f) * 800f;
            item.scatterZ = (random.nextFloat() - 0.5f) * 800f;
            item.animProgress = 0f;
        }
    }

    public void startOpenAnimation() {
        isAnimatingOpen = true;
        cylinderOpenProgress = 0f;
        for (AppItem item : filteredList) {
            item.scatterX = (random.nextFloat() - 0.5f) * 1000f;
            item.scatterY = (random.nextFloat() - 0.5f) * 1000f;
            item.animProgress = 0f;
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        float centerX = width / 2f;
        float centerY = height / 2f;

        paint.setTextSize(64f);
        paint.setARGB(255, 255, 255, 255);
        paint.setTextAlign(Paint.Align.CENTER);
        String currentTime = new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date());
        canvas.drawText(currentTime, centerX, 150f, paint);

        if (isAnimatingOpen && cylinderOpenProgress < 1f) {
            cylinderOpenProgress += 0.04f;
            if (cylinderOpenProgress > 1f) cylinderOpenProgress = 1f;
            invalidate();
        }

        float radius = 300f;
        int totalApps = filteredList.size();
        if (totalApps == 0) return;

        for (int i = 0; i < totalApps; i++) {
            AppItem item = filteredList.get(i);
            
            float angle = (i * (2 * (float) Math.PI / totalApps)) + cylinderRotation;
            float targetX = centerX + (float) Math.sin(angle) * radius;
            float targetZ = (float) Math.cos(angle) * radius;
            float targetY = centerY + ((i - totalApps / 2f) * 120f) + scrollY;

            if (item.animProgress < cylinderOpenProgress) {
                item.animProgress += 0.05f;
                if (item.animProgress > 1f) item.animProgress = 1f;
            }

            float currentX = targetX + item.scatterX * (1f - item.animProgress);
            float currentY = targetY + item.scatterY * (1f - item.animProgress);
            
            float scale = (targetZ + radius + 200f) / (2 * radius + 200f);
            if (scale < 0.3f) scale = 0.3f;

            canvas.save();
            canvas.translate(currentX, currentY);
            canvas.scale(scale, scale);

            if (item.icon != null) {
                canvas.drawBitmap(item.icon, -60, -60, paint);
            }

            paint.setTextSize(32f);
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setARGB(255, 255, 255, 255);
            canvas.drawText(item.title, -60, 90, paint);

            canvas.restore();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float currentX = event.getX();
        float currentY = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = currentX;
                lastTouchY = currentY;
                break;

            case MotionEvent.ACTION_MOVE:
                float deltaX = currentX - lastTouchX;
                float deltaY = currentY - lastTouchY;

                if (Math.abs(deltaY) > Math.abs(deltaX)) {
                    if (deltaY < -50 && !isAnimatingOpen) {
                        startOpenAnimation();
                    } else {
                        scrollY += deltaY * 0.5f;
                    }
                } else {
                    cylinderRotation += deltaX * 0.01f;
                }

                lastTouchX = currentX;
                lastTouchY = currentY;
                invalidate();
                break;
        }
        return true;
    }
}
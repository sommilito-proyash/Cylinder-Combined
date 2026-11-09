package com.cylinderradar.launcher;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Native Canvas Smart Observatory prototype with six selectable cube panels. */
public final class ObservatoryActivity extends Activity {
    private ObservatoryView scene;
    private Store store;
    private TextView ecoButton;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        store = new Store(this);
        FrameLayout root = new FrameLayout(this);
        scene = new ObservatoryView(this);
        scene.setBatterySaver(store.observatoryBatterySaver());
        root.addView(scene, new FrameLayout.LayoutParams(-1, -1));
        TextView back = new TextView(this);
        back.setText("‹  CYLINDER RADAR");
        back.setTextColor(0xFFE7F8FF); back.setTextSize(12); back.setLetterSpacing(.12f);
        back.setGravity(Gravity.CENTER); back.setPadding(dp(16), dp(9), dp(16), dp(9));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(0x66304758); bg.setCornerRadius(dp(22)); bg.setStroke(dp(1), 0x665BE8FF);
        back.setBackground(bg);
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-2, dp(42), Gravity.TOP | Gravity.LEFT);
        bp.setMargins(dp(16), dp(22), 0, 0); root.addView(back, bp);
        back.setOnClickListener(v -> finish());

        ecoButton = new TextView(this);
        ecoButton.setTextColor(0xFFE7F8FF); ecoButton.setTextSize(10); ecoButton.setLetterSpacing(.06f);
        ecoButton.setGravity(Gravity.CENTER); ecoButton.setPadding(dp(12), dp(8), dp(12), dp(8));
        android.graphics.drawable.GradientDrawable ecoBg = new android.graphics.drawable.GradientDrawable();
        ecoBg.setColor(0x66304758); ecoBg.setCornerRadius(dp(22)); ecoBg.setStroke(dp(1), 0x665BE8FF);
        ecoButton.setBackground(ecoBg);
        FrameLayout.LayoutParams ep = new FrameLayout.LayoutParams(-2, dp(42), Gravity.TOP | Gravity.RIGHT);
        ep.setMargins(0, dp(22), dp(16), 0); root.addView(ecoButton, ep);
        updateEcoButton();
        TextView earthButton = new TextView(this);
        earthButton.setText("EXPLORE 3D EARTH  ↗"); earthButton.setTextColor(0xFFE7F8FF);
        earthButton.setTextSize(10); earthButton.setLetterSpacing(.06f); earthButton.setGravity(Gravity.CENTER);
        earthButton.setPadding(dp(14), dp(8), dp(14), dp(8));
        android.graphics.drawable.GradientDrawable earthBg = new android.graphics.drawable.GradientDrawable();
        earthBg.setColor(0xAA14506A); earthBg.setCornerRadius(dp(22)); earthBg.setStroke(dp(1), 0xAA63E5FF);
        earthButton.setBackground(earthBg);
        FrameLayout.LayoutParams earthLp = new FrameLayout.LayoutParams(-2, dp(38), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        earthLp.setMargins(0, dp(120), 0, 0); root.addView(earthButton, earthLp);
        earthButton.setOnClickListener(v -> startActivity(new android.content.Intent(ObservatoryActivity.this, EarthActivity.class)));
        ecoButton.setOnClickListener(v -> {
            boolean enabled = !store.observatoryBatterySaver();
            store.setObservatoryBatterySaver(enabled);
            scene.setBatterySaver(enabled);
            updateEcoButton();
        });
        setContentView(root);
    }
    private void updateEcoButton() {
        if (ecoButton != null) ecoButton.setText(store.observatoryBatterySaver() ? "ECO  •  ON" : "ECO  •  OFF");
    }
    @Override protected void onResume() {
        super.onResume();
        if (scene != null) scene.setActive(true);
    }
    @Override protected void onPause() {
        if (scene != null) scene.setActive(false);
        super.onPause();
    }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }

    static final class ObservatoryView extends View {
        private static final String[] NAMES = {"WATCH", "EARTH", "WEATHER", "CALENDAR", "WORLD CLOCK", "MOTION"};
        private static final String[] BANGLA = {"যান্ত্রিক ঘড়ি", "পৃথিবী পর্যবেক্ষণ", "আবহাওয়া", "ক্যালেন্ডার", "বিশ্বঘড়ি", "ডিভাইস / মোশন"};
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final float density;
        final float[] stars = new float[240];
        float rotX = -.24f, rotY = -.42f, downX, downY, startX, startY;
        int selected = 0;
        boolean dragging = false;
        private final Handler frameHandler = new Handler(Looper.getMainLooper());
        private boolean active = false;
        private boolean batterySaver = false;
        private Store wst;
        private final Runnable frameTick = new Runnable() {
            @Override public void run() {
                if (!active || getWindowVisibility() != VISIBLE || !isShown()) return;
                invalidate();
                frameHandler.postDelayed(this, batterySaver ? 1000L : 250L);
            }
        };

        ObservatoryView(android.content.Context c) {
            super(c); density = getResources().getDisplayMetrics().density;
            java.util.Random r = new java.util.Random(31);
            for (int i=0;i<stars.length;i++) stars[i]=r.nextFloat();
            setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }
        float dp(float v) { return v*density; }
        void setBatterySaver(boolean enabled) {
            batterySaver = enabled;
            restartFrameLoop();
        }
        void setActive(boolean enabled) {
            active = enabled;
            restartFrameLoop();
        }
        private void restartFrameLoop() {
            frameHandler.removeCallbacks(frameTick);
            if (active && isShown()) frameHandler.post(frameTick);
        }
        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            restartFrameLoop();
        }
        @Override protected void onDetachedFromWindow() {
            frameHandler.removeCallbacks(frameTick);
            active = false;
            super.onDetachedFromWindow();
        }
        @Override protected void onWindowVisibilityChanged(int visibility) {
            super.onWindowVisibilityChanged(visibility);
            if (visibility == VISIBLE) restartFrameLoop();
            else frameHandler.removeCallbacks(frameTick);
        }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c); float w=getWidth(), h=getHeight();
            p.setShader(new LinearGradient(0,0,w,h, new int[]{0xFF07121E,0xFF101A2D,0xFF03080F},null,Shader.TileMode.CLAMP));
            c.drawRect(0,0,w,h,p); p.setShader(null);
            for(int i=0;i<stars.length-1;i+=2){ p.setColor(i%8==0?0x99BCEEFF:0x557EA5C8); c.drawCircle(stars[i]*w,stars[i+1]*h,dp(i%8==0?1.35f:.65f),p); }

            drawHeader(c,w);
            float cubeSize=Math.min(w*.39f,h*.175f);
            float cubeY=h*.285f;
            drawGlow(c,w*.5f,cubeY,cubeSize*1.45f);
            drawCube(c,w*.5f,cubeY,cubeSize);
            drawClockReadout(c,w*.5f,h);
            drawFaceSelectors(c,w,h);
            drawSelectedPanel(c,w,h);
            drawFooter(c,w,h);
        }
        void drawHeader(Canvas c,float w){
            p.setTextAlign(Paint.Align.CENTER); p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
            p.setTextSize(dp(12)); p.setColor(0xFF72DFFF); c.drawText("CYLINDER RADAR  /  SMART OBSERVATORY",w*.5f,dp(88),p);
            p.setTextSize(dp(9)); p.setColor(0xFF7397AC); c.drawText("PERSONAL CONTROL CENTER",w*.5f,dp(105),p);
        }
        void drawGlow(Canvas c,float x,float y,float r){ p.setShader(new RadialGradient(x,y,r,new int[]{0x4431DFFF,0x1821A9D0,0x00000000},null,Shader.TileMode.CLAMP)); c.drawCircle(x,y,r,p); p.setShader(null); }
        float[][] vertices(float s){ return new float[][]{{-s,-s,-s},{s,-s,-s},{s,s,-s},{-s,s,-s},{-s,-s,s},{s,-s,s},{s,s,s},{-s,s,s}}; }
        float[] project(float x,float y,float z,float cx,float cy,float s){
            double cyy=Math.cos(rotY), sy=Math.sin(rotY), cxx=Math.cos(rotX), sx=Math.sin(rotX);
            double x1=x*cyy+z*sy, z1=-x*sy+z*cyy, y1=y*cxx-z1*sx, z2=y*sx+z1*cxx;
            float k=3.6f/(float)(3.6+z2); return new float[]{cx+(float)x1*s*k,cy+(float)y1*s*k};
        }
        void drawCube(Canvas c,float cx,float cy,float s){
            float[][] v=vertices(1); float[][] q=new float[8][]; for(int i=0;i<8;i++)q[i]=project(v[i][0],v[i][1],v[i][2],cx,cy,s);
            int[][] edges={{0,1},{1,2},{2,3},{3,0},{4,5},{5,6},{6,7},{7,4},{0,4},{1,5},{2,6},{3,7}};
            Path face=new Path(); face.moveTo(q[4][0],q[4][1]); face.lineTo(q[5][0],q[5][1]); face.lineTo(q[6][0],q[6][1]); face.lineTo(q[7][0],q[7][1]); face.close();
            p.setShader(new LinearGradient(cx-s,cy-s,cx+s,cy+s,0x6639DFFF,0x222A6E91,Shader.TileMode.CLAMP)); c.drawPath(face,p); p.setShader(null);
            p.setColor(0xCC70E9FF); p.setStrokeWidth(dp(1.5f)); p.setStyle(Paint.Style.STROKE);
            for(int[] e:edges) c.drawLine(q[e[0]][0],q[e[0]][1],q[e[1]][0],q[e[1]][1],p);
            p.setStyle(Paint.Style.FILL);
            float mx=(q[4][0]+q[5][0]+q[6][0]+q[7][0])*.25f, my=(q[4][1]+q[5][1]+q[6][1]+q[7][1])*.25f;
            float radius=s*.43f;
            p.setColor(0xBB07111B); c.drawCircle(mx,my,radius,p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(2)); p.setColor(0xCCB8E9F6); c.drawCircle(mx,my,radius,p);
            p.setStrokeWidth(dp(1)); p.setColor(0x8865DFFF);
            for(int i=0;i<60;i++){ double a=i*Math.PI/30; float r1=radius*(i%5==0?.84f:.91f),r2=radius*.97f; c.drawLine(mx+(float)Math.sin(a)*r1,my-(float)Math.cos(a)*r1,mx+(float)Math.sin(a)*r2,my-(float)Math.cos(a)*r2,p); }
            p.setStyle(Paint.Style.FILL);
            gear(c,mx-radius*.40f,my+radius*.23f,radius*.17f,0xAAE7B96C);
            gear(c,mx+radius*.38f,my-radius*.26f,radius*.13f,0xAA79DDF4);
            Calendar cal=Calendar.getInstance();
            double sec=cal.get(Calendar.SECOND)*Math.PI/30, min=(cal.get(Calendar.MINUTE)+cal.get(Calendar.SECOND)/60.0)*Math.PI/30, hr=(cal.get(Calendar.HOUR)*Math.PI/6)+min/12;
            hand(c,mx,my,radius*.40f,hr,dp(3),0xFFE4F4FF); hand(c,mx,my,radius*.62f,min,dp(2),0xFF9DEBFF); hand(c,mx,my,radius*.70f,sec,dp(1),0xFFFF8E8E);
            p.setColor(0xFFFFD58C); c.drawCircle(mx,my,dp(3.5f),p);
            p.setTextAlign(Paint.Align.CENTER); p.setTextSize(dp(8)); p.setColor(0xFFBDEEFF); c.drawText(NAMES[selected],cx,cy+s*1.48f,p);
        }
        void gear(Canvas c,float x,float y,float r,int color){ p.setColor(color); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(2)); c.drawCircle(x,y,r,p); c.drawCircle(x,y,r*.42f,p); c.save(); c.rotate((System.currentTimeMillis()%12000L)*.03f,x,y); for(int i=0;i<12;i++){double a=i*Math.PI/6;c.drawLine(x+(float)Math.cos(a)*r*.78f,y+(float)Math.sin(a)*r*.78f,x+(float)Math.cos(a)*r*1.12f,y+(float)Math.sin(a)*r*1.12f,p);} c.restore(); p.setStyle(Paint.Style.FILL); }
        void hand(Canvas c,float x,float y,float r,double a,float sw,int color){ p.setColor(color); p.setStrokeWidth(sw); p.setStrokeCap(Paint.Cap.ROUND); c.drawLine(x,y,x+(float)Math.sin(a)*r,y-(float)Math.cos(a)*r,p); }
        void drawClockReadout(Canvas c,float cx,float h){
            SimpleDateFormat tm=new SimpleDateFormat("HH:mm:ss",Locale.getDefault()), dt=new SimpleDateFormat("EEEE, d MMMM",Locale.getDefault());
            p.setTextAlign(Paint.Align.CENTER); p.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL)); p.setTextSize(dp(29)); p.setColor(0xFFE7F7FF); c.drawText(tm.format(new Date()),cx,h*.465f,p);
            p.setTextSize(dp(11)); p.setColor(0xFF8EB8CE); c.drawText(dt.format(new Date()),cx,h*.49f,p);
        }
        void drawFaceSelectors(Canvas c,float w,float h){
            float gap=dp(7), pad=dp(14), total=w-pad*2, cell=(total-gap*2)/3, top=h*.535f, bh=dp(38);
            for(int i=0;i<6;i++){
                int row=i/3,col=i%3; float x=pad+col*(cell+gap), y=top+row*(bh+dp(8));
                android.graphics.RectF r=new android.graphics.RectF(x,y,x+cell,y+bh);
                p.setColor(i==selected?0xCC174B64:0x66304758); c.drawRoundRect(r,dp(11),dp(11),p);
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(1)); p.setColor(i==selected?0xFF63E5FF:0x665A9BB5); c.drawRoundRect(r,dp(11),dp(11),p); p.setStyle(Paint.Style.FILL);
                p.setTextAlign(Paint.Align.CENTER); p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL)); p.setTextSize(dp(9)); p.setColor(i==selected?0xFFE7FBFF:0xFF8FB2C5); c.drawText(NAMES[i],x+cell/2,y+bh*.62f,p);
            }
        }
        void drawSelectedPanel(Canvas c,float w,float h){
            float left=dp(16), right=w-dp(16), top=h*.665f, bottom=Math.min(h-dp(62),h*.855f);
            android.graphics.RectF r=new android.graphics.RectF(left,top,right,bottom);
            p.setShader(new LinearGradient(left,top,right,bottom,0xCC10283A,0xAA071522,Shader.TileMode.CLAMP));
            c.drawRoundRect(r,dp(18),dp(18),p); p.setShader(null);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(1)); p.setColor(0x7755CFEF); c.drawRoundRect(r,dp(18),dp(18),p); p.setStyle(Paint.Style.FILL);
            float x=left+dp(18), y=top+dp(27);
            p.setTextAlign(Paint.Align.LEFT); p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL)); p.setTextSize(dp(12)); p.setColor(0xFF72DFFF); c.drawText(BANGLA[selected],x,y,p);
            switch(selected){
                case 0: drawWatchDetails(c,x,y,w,right,bottom); break;
                case 1: drawEarthDetails(c,x,y,w,right,bottom); break;
                case 2: drawWeatherDetails(c,x,y,w,right,bottom); break;
                case 3: drawCalendarDetails(c,x,y,w,right,bottom); break;
                case 4: drawWorldClockDetails(c,x,y,w,right,bottom); break;
                default: drawMotionDetails(c,x,y,w,right,bottom); break;
            }
        }
        void panelText(Canvas c,String text,float x,float y,float size,int color){
            p.setTextAlign(Paint.Align.LEFT); p.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL)); p.setTextSize(dp(size)); p.setColor(color); c.drawText(text,x,y,p);
        }
        void drawWatchDetails(Canvas c,float x,float y,float w,float right,float bottom){
            SimpleDateFormat f=new SimpleDateFormat("EEEE  •  d MMMM yyyy",Locale.getDefault());
            panelText(c,"LIVE MECHANICAL TIMEPIECE",x,y+dp(24),10,0xFFD0E7F2);
            panelText(c,f.format(new Date()),x,y+dp(43),10,0xFF8EB8CE);
            panelText(c,"Gear train synchronized with device time",x,y+dp(61),9,0xFF78A8BF);
        }
        void drawEarthDetails(Canvas c,float x,float y,float w,float right,float bottom){
            // A small, genuinely projected sphere: longitude/latitude grid, rotating land silhouettes,
            // limb shading and a time-derived day/night terminator. All geometry is local/offline.
            float cx=right-dp(66), cy=y+dp(39), radius=dp(34);
            drawProjectedEarth(c,cx,cy,radius);
            panelText(c,"ROTATING EARTH • OFFLINE 3D",x,y+dp(24),10,0xFFD0E7F2);
            Calendar utc=Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            double age=moonAge(utc);
            panelText(c,String.format(Locale.US,"Moon age  %.1f / 29.5 days",age),x,y+dp(43),10,0xFF8EB8CE);
            panelText(c,moonPhaseName(age)+"  •  offline ephemeris",x,y+dp(61),9,0xFF78A8BF);
            drawMoonPhase(c,x+dp(205),y+dp(42),dp(13),age);
        }
        void drawProjectedEarth(Canvas c,float cx,float cy,float r){
            float spin=(System.currentTimeMillis()%60000L)/60000f*(float)(Math.PI*2);
            p.setShader(new RadialGradient(cx-r*.32f,cy-r*.38f,r*1.65f,
                    new int[]{0xFF69C9F2,0xFF176B9D,0xFF06213F,0xFF010A18},
                    new float[]{0f,.58f,.88f,1f},Shader.TileMode.CLAMP));
            c.drawCircle(cx,cy,r,p); p.setShader(null);
            // Approximate continent silhouettes in latitude/longitude coordinates.
            double[][][] land={
                {{72,-165},{62,-145},{55,-128},{48,-124},{40,-118},{32,-112},{24,-106},{18,-98},{10,-84},{8,-78},{18,-66},{28,-80},{38,-74},{48,-60},{55,-58},{62,-75},{70,-95},{74,-125}},
                {{12,-81},{5,-77},{-5,-78},{-15,-74},{-25,-70},{-40,-72},{-55,-66},{-50,-50},{-35,-45},{-20,-40},{-5,-50},{5,-60}},
                {{70,-10},{60,-8},{52,2},{45,-8},{36,-9},{30,-5},{22,12},{12,10},{5,0},{-5,12},{-20,15},{-34,20},{-35,32},{-25,42},{-10,50},{5,42},{15,48},{25,35},{35,30},{42,40},{50,30},{58,25},{65,15}},
                {{72,35},{68,60},{60,80},{55,105},{48,130},{40,145},{30,140},{22,120},{10,110},{5,95},{12,80},{20,70},{28,60},{38,50},{48,45},{58,40}},
                {{-12,112},{-18,122},{-25,132},{-35,138},{-40,150},{-30,154},{-20,148},{-12,132}},
                {{60,-50},{70,-42},{78,-30},{82,-45},{75,-58},{66,-60}}
            };
            p.setColor(0xFF3AAE83); p.setStyle(Paint.Style.FILL);
            for(double[][] poly:land){
                Path path=new Path(); boolean started=false;
                for(double[] ll:poly){
                    float[] pt=earthPoint(ll[0],ll[1],spin,cx,cy,r);
                    if(pt==null){started=false;continue;}
                    if(!started){path.moveTo(pt[0],pt[1]);started=true;} else path.lineTo(pt[0],pt[1]);
                }
                if(started){path.close();c.drawPath(path,p);}
            }
            // Latitude curves and longitude meridians, clipped visually by front-hemisphere projection.
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(.65f)); p.setColor(0x668DEBFF);
            for(int lat=-60;lat<=60;lat+=30){
                Path line=new Path(); boolean started=false;
                for(int lon=-90;lon<=90;lon+=4){float[] pt=earthPoint(lat,lon,spin,cx,cy,r);if(pt==null)continue;if(!started){line.moveTo(pt[0],pt[1]);started=true;}else line.lineTo(pt[0],pt[1]);}
                if(started)c.drawPath(line,p);
            }
            for(int lon=-60;lon<=60;lon+=30){
                Path line=new Path();boolean started=false;
                for(int lat=-88;lat<=88;lat+=4){float[] pt=earthPoint(lat,lon,spin,cx,cy,r);if(pt==null)continue;if(!started){line.moveTo(pt[0],pt[1]);started=true;}else line.lineTo(pt[0],pt[1]);}
                if(started)c.drawPath(line,p);
            }
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1));p.setColor(0xAA9CEBFF);c.drawCircle(cx,cy,r,p);p.setStyle(Paint.Style.FILL);
            // Soft night-side overlay, offset from the lit center to suggest the terminator.
            Calendar utc=Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            float nightAngle=(utc.get(Calendar.HOUR_OF_DAY)-12)*15f;
            c.save(); c.rotate(nightAngle,cx,cy);
            p.setShader(new LinearGradient(cx-r*.05f,cy-r,cx+r,cy+r, new int[]{0x00000000,0x22304060,0xAA020817},null,Shader.TileMode.CLAMP));
            c.drawCircle(cx,cy,r*.995f,p);p.setShader(null); c.restore();
        }
        float[] earthPoint(double latitude,double longitude,float spin,float cx,float cy,float r){
            double lat=Math.toRadians(latitude), lon=Math.toRadians(longitude)+spin;
            double x=Math.cos(lat)*Math.sin(lon), y=-Math.sin(lat), z=Math.cos(lat)*Math.cos(lon);
            if(z<-.04)return null;
            float perspective=(float)(.88+.12*z);
            return new float[]{cx+(float)x*r*perspective,cy+(float)y*r*perspective};
        }
        double moonAge(Calendar date){
            // Known new-moon reference: 2000-01-06 18:14 UTC; synodic month 29.530588853 days.
            double days=(date.getTimeInMillis()-946728840000L)/86400000.0;
            double age=days-Math.floor(days/29.530588853)*29.530588853;
            return age<0?age+29.530588853:age;
        }
        String moonPhaseName(double age){
            if(age<1.85 || age>=27.68) return "New Moon";
            if(age<5.54) return "Waxing Crescent";
            if(age<9.22) return "First Quarter";
            if(age<12.91) return "Waxing Gibbous";
            if(age<16.60) return "Full Moon";
            if(age<20.29) return "Waning Gibbous";
            if(age<23.98) return "Last Quarter";
            return "Waning Crescent";
        }
        void drawMoonPhase(Canvas c,float cx,float cy,float r,double age){
            float illumination=(1f-(float)Math.cos(age/29.530588853*Math.PI*2f))*.5f;
            p.setColor(0xFF17283A); c.drawCircle(cx,cy,r,p);
            c.save(); Path clip=new Path(); clip.addCircle(cx,cy,r,Path.Direction.CW); c.clipPath(clip);
            p.setColor(0xFFE6F1F5);
            Path lit=new Path();
            boolean waxing=age<14.765;
            float edge= (waxing?1f:-1f)*(1f-2f*illumination);
            lit.moveTo(cx,cy-r); lit.lineTo(cx,cy+r);
            lit.cubicTo(cx+edge*r,cy+r*.72f,cx+edge*r,cy-r*.72f,cx,cy-r);
            lit.close();
            if(illumination>.5f){
                p.setColor(0xFFE6F1F5); c.drawCircle(cx,cy,r,p);
                p.setColor(0xFF17283A);
                Path shadow=new Path(); shadow.moveTo(cx,cy-r); shadow.lineTo(cx,cy+r);
                shadow.cubicTo(cx-edge*r,cy+r*.72f,cx-edge*r,cy-r*.72f,cx,cy-r); shadow.close(); c.drawPath(shadow,p);
            } else { c.drawPath(lit,p); }
            c.restore(); p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(.8f));p.setColor(0xFF9CCCE0);c.drawCircle(cx,cy,r,p);p.setStyle(Paint.Style.FILL);
        }
        void drawWeatherDetails(Canvas c,float x,float y,float w,float right,float bottom){
            if (wst == null) wst = new Store(getContext());
            Store st = wst;
            String temp = st.weatherTemperature(), city = st.weatherResolvedCity().isEmpty() ? st.weatherCity() : st.weatherResolvedCity();
            if (temp.isEmpty()) {
                panelText(c,"WEATHER NOT LOADED YET",x,y+dp(24),10,0xFFFFD58C);
                panelText(c,"Open the home screen once while online.",x,y+dp(43),9,0xFFD0E7F2);
                return;
            }
            panelText(c,city.toUpperCase(Locale.getDefault())+"  "+temp+"\u00B0C",x,y+dp(24),10,0xFFD0E7F2);
            String fc = getContext().getSharedPreferences("cube_watch", 0).getString("fc", "");
            String[] rows = fc.isEmpty() ? new String[0] : fc.split(";");
            for (int i = 0; i < Math.min(4, rows.length); i++) {
                String[] f = rows[i].split("\\|");
                if (f.length < 4) continue;
                String day = f[0].length() >= 10 ? f[0].substring(5) : f[0];
                panelText(c,day+"   "+f[2]+"\u00B0 / "+f[1]+"\u00B0   rain "+f[3]+"%",x,y+dp(43+i*14),9,0xFF8EB8CE);
            }
        }
        void drawCalendarDetails(Canvas c,float x,float y,float w,float right,float bottom){
            Calendar now=Calendar.getInstance();
            SimpleDateFormat month=new SimpleDateFormat("MMMM yyyy",Locale.getDefault());
            panelText(c,month.format(now.getTime()).toUpperCase(Locale.getDefault()),x,y+dp(24),10,0xFFD0E7F2);
            String[] days={"SUN","MON","TUE","WED","THU","FRI","SAT"};
            float cell=dp(24), base=x, yy=y+dp(47);
            p.setTextAlign(Paint.Align.CENTER); p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL)); p.setTextSize(dp(8));
            for(int i=0;i<7;i++){p.setColor(0xFF78A8BF);c.drawText(days[i],base+cell*(i+.5f),yy,p);}
            int first=now.getActualMinimum(Calendar.DAY_OF_MONTH), max=now.getActualMaximum(Calendar.DAY_OF_MONTH);
            Calendar firstDay=(Calendar)now.clone(); firstDay.set(Calendar.DAY_OF_MONTH,1);
            int offset=firstDay.get(Calendar.DAY_OF_WEEK)-1, today=now.get(Calendar.DAY_OF_MONTH);
            for(int d=1;d<=max;d++){
                int index=offset+d-1, row=index/7, col=index%7; float xx=base+cell*(col+.5f), yyy=yy+dp(16+row*15);
                if(d==today){p.setColor(0xFF1C829F);c.drawCircle(xx,yyy-dp(3),dp(9),p);}
                p.setColor(d==today?Color.WHITE:0xFFD0E7F2);p.setTextSize(dp(9));c.drawText(String.valueOf(d),xx,yyy,p);
            }
        }
        void drawWorldClockDetails(Canvas c,float x,float y,float w,float right,float bottom){
            String[][] cities={{"LONDON","Europe/London"},{"DHAKA","Asia/Dhaka"},{"NEW YORK","America/New_York"}};
            float row=y+dp(24);
            for(String[] city:cities){
                SimpleDateFormat fmt=new SimpleDateFormat("HH:mm",Locale.US); fmt.setTimeZone(TimeZone.getTimeZone(city[1]));
                panelText(c,city[0],x,row,9,0xFF8EB8CE);
                p.setTextAlign(Paint.Align.RIGHT);p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));p.setTextSize(dp(12));p.setColor(0xFFE7F7FF);
                c.drawText(fmt.format(new Date()),right-dp(18),row,p); row+=dp(17);
            }
        }
        void drawMotionDetails(Canvas c,float x,float y,float w,float right,float bottom){
            panelText(c,"TOUCH + DRAG",x,y+dp(24),10,0xFFD0E7F2);
            panelText(c,String.format(Locale.US,"Cube angle  X %+.2f   Y %+.2f",rotX,rotY),x,y+dp(43),9,0xFF8EB8CE);
            panelText(c,"Sensor-based orientation is not enabled.",x,y+dp(61),9,0xFF78A8BF);
        }
        void drawFooter(Canvas c,float w,float h){ p.setTextAlign(Paint.Align.CENTER); p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL)); p.setTextSize(dp(9)); p.setColor(0xFF6F9CB5); c.drawText(batterySaver ? "SMART OBSERVATORY  /  ECO MODE • 1 FPS" : "SMART OBSERVATORY  /  STANDARD • 4 FPS",w/2,h-dp(28),p); }
        @Override public boolean onTouchEvent(MotionEvent e){
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN: downX=e.getX(); downY=e.getY(); startX=downX; startY=downY; dragging=false; return true;
                case MotionEvent.ACTION_MOVE:
                    float dx=e.getX()-downX,dy=e.getY()-downY;
                    if(Math.abs(e.getX()-startX)+Math.abs(e.getY()-startY)>dp(8)) dragging=true;
                    if(dragging){ rotY+=dx/dp(420); rotX+=dy/dp(420); rotX=Math.max(-1.0f,Math.min(1.0f,rotX)); downX=e.getX();downY=e.getY(); invalidate(); }
                    return true;
                case MotionEvent.ACTION_UP:
                    if(!dragging){
                        float w=getWidth(),h=getHeight(),pad=dp(14),gap=dp(7),cell=(w-pad*2-gap*2)/3,bh=dp(38),top=h*.535f;
                        float tx=e.getX(),ty=e.getY();
                        for(int i=0;i<6;i++){
                            int row=i/3,col=i%3; float x=pad+col*(cell+gap), y=top+row*(bh+dp(8));
                            if(tx>=x&&tx<=x+cell&&ty>=y&&ty<=y+bh){ selected=i; float[] targetX={-.24f,-.18f,-.05f,.35f,.75f,-.7f}; float[] targetY={-.42f,1.15f,-1.15f,2.35f,0f,3.05f}; rotX=targetX[i]; rotY=targetY[i]; invalidate(); break; }
                        }
                        if(Math.abs(e.getX()-startX)+Math.abs(e.getY()-startY)<dp(10) && ty<getHeight()*.50f){ selected=(selected+1)%6; invalidate(); }
                    }
                    performClick(); return true;
            }
            return true;
        }
        @Override public boolean performClick(){super.performClick();return true;}
    }
}

package com.cylinderradar.launcher;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;

/**
 * Vault lock screens. Nothing the user enters is ever rendered:
 *  - PIN: only the NUMBER of digits is shown as filled dots, key presses have no highlight,
 *    and the key layout can optionally be shuffled.
 *  - Pattern: no trail, no node highlight – the grid looks identical before, during and after drawing.
 * Every lock dialog also carries FLAG_SECURE so screenshots / screen recording show black.
 */
final class LockUi {
    private LockUi() { }

    interface Listener { void onEntry(Entry e, String secret); void onCancel(); }

    /** Handle to a visible lock dialog. */
    static final class Entry {
        final Dialog dialog;
        final TextView title, status;
        final PinPad pin;
        final PatternPad pattern;

        Entry(Dialog d, TextView t, TextView s, PinPad pp, PatternPad pt) {
            dialog = d; title = t; status = s; pin = pp; pattern = pt;
        }

        void setTitle(String s) { title.setText(s); }
        void info(String s) { status.setTextColor(0xB0FFFFFF); status.setText(s); }
        void error(String s) {
            status.setTextColor(0xFFFF5566);
            status.setText(s);
            View v = pin != null ? pin : pattern;
            if (v != null) v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        }
        void reset() { if (pin != null) pin.clear(); }
        void dismiss() { try { dialog.dismiss(); } catch (Exception ignored) { } }
    }

    static Entry show(final MainActivity a, boolean pattern, int pinLen, boolean shuffle, int accent,
                      String titleText, String hint, final Listener l) {
        final Dialog dlg = new Dialog(a, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar);
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window win = dlg.getWindow();
        win.addFlags(WindowManager.LayoutParams.FLAG_SECURE | WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        win.setDimAmount(0.78f);
        win.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));

        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = a.dp(20);
        card.setPadding(pad, a.dp(22), pad, a.dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF2040D0B);
        bg.setCornerRadius(a.dp(26));
        bg.setStroke(a.dp(1), (accent & 0xFFFFFF) | 0x80000000);
        card.setBackground(bg);

        TextView title = new TextView(a);
        title.setText(titleText);
        title.setTextColor(accent);
        title.setTextSize(17);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setLetterSpacing(0.12f);
        title.setGravity(Gravity.CENTER);
        card.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView status = new TextView(a);
        status.setText(hint);
        status.setTextColor(0xB0FFFFFF);
        status.setTextSize(13);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = a.dp(6);
        sp.bottomMargin = a.dp(4);
        card.addView(status, sp);

        PinPad pp = null;
        PatternPad pt = null;
        final Entry[] holder = new Entry[1];
        if (pattern) {
            pt = new PatternPad(a, accent);
            pt.onDone = new PatternPad.Done() {
                @Override public void done(String seq) { l.onEntry(holder[0], "p:" + seq); }
            };
            card.addView(pt, new LinearLayout.LayoutParams(a.dp(260), a.dp(260)));
        } else {
            pp = new PinPad(a, accent, pinLen, shuffle);
            pp.onSubmit = new PinPad.Submit() {
                @Override public void submit(String pin) { l.onEntry(holder[0], pin); }
            };
            card.addView(pp, new LinearLayout.LayoutParams(a.dp(280), ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        TextView cancel = new TextView(a);
        cancel.setText("বাতিল");
        cancel.setTextColor(0x99FFFFFF);
        cancel.setTextSize(14);
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(a.dp(24), a.dp(12), a.dp(24), a.dp(8));
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dlg.cancel(); }
        });
        card.addView(cancel);

        dlg.setContentView(card);
        dlg.setOnCancelListener(new android.content.DialogInterface.OnCancelListener() {
            @Override public void onCancel(android.content.DialogInterface d) { l.onCancel(); }
        });
        Entry e = new Entry(dlg, title, status, pp, pt);
        holder[0] = e;
        dlg.show();
        return e;
    }

    // =============================================================================================
    /** Numeric keypad. Draws only dots for the digits typed; keys never light up. */
    static final class PinPad extends View {
        interface Submit { void submit(String pin); }
        Submit onSubmit;
        private final StringBuilder buf = new StringBuilder();
        private final int accent, targetLen, maxLen = 8;
        private final int[] digitAt = new int[10]; // digit shown in slot 0..9 (slot 9 = bottom middle)
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF[] keyRect = new RectF[12];
        private final float d;
        private float dotsH, keyH;

        PinPad(Context c, int accent, int targetLen, boolean shuffle) {
            super(c);
            this.accent = accent;
            this.targetLen = targetLen;
            d = c.getResources().getDisplayMetrics().density;
            ArrayList<Integer> nums = new ArrayList<>();
            for (int i = 0; i < 10; i++) nums.add(i);
            if (shuffle) Collections.shuffle(nums);
            else { nums.clear(); for (int i = 1; i <= 9; i++) nums.add(i); nums.add(0); }
            for (int i = 0; i < 10; i++) digitAt[i] = nums.get(i);
            for (int i = 0; i < 12; i++) keyRect[i] = new RectF();
            dotsH = 34 * d;
            keyH = 62 * d;
            setHapticFeedbackEnabled(true);
        }

        void clear() { buf.setLength(0); invalidate(); }

        @Override protected void onMeasure(int wms, int hms) {
            int w = MeasureSpec.getSize(wms);
            setMeasuredDimension(w, (int) (dotsH + keyH * 4 + 6 * d));
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            float cw = w / 3f;
            for (int i = 0; i < 12; i++) {
                int r = i / 3, col = i % 3;
                keyRect[i].set(col * cw, dotsH + r * keyH, (col + 1) * cw, dotsH + (r + 1) * keyH);
            }
        }

        /** Maps grid cell 0..11 to: 0-8 digit slots, 9 = back, 10 = last digit slot, 11 = OK. */
        private String label(int cell) {
            if (cell < 9) return String.valueOf(digitAt[cell]);
            if (cell == 9) return "⌫";
            if (cell == 10) return String.valueOf(digitAt[9]);
            return "✓";
        }

        @Override protected void onDraw(Canvas c) {
            int w = getWidth();
            // progress dots (count only)
            int total = targetLen > 0 ? targetLen : Math.max(4, buf.length());
            float gap = 22 * d, r = 6 * d;
            float x0 = w / 2f - gap * (total - 1) / 2f;
            for (int i = 0; i < total; i++) {
                boolean on = i < buf.length();
                p.setStyle(on ? Paint.Style.FILL : Paint.Style.STROKE);
                p.setStrokeWidth(1.6f * d);
                p.setColor(on ? accent : 0x88FFFFFF);
                c.drawCircle(x0 + i * gap, dotsH / 2f, r, p);
            }
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            for (int i = 0; i < 12; i++) {
                RectF k = keyRect[i];
                String t = label(i);
                boolean special = i == 9 || i == 11;
                p.setTextSize((special ? 24 : 30) * d);
                p.setColor(special ? accent : 0xFFEFFFF6);
                float cy = k.centerY() - (p.ascent() + p.descent()) / 2f;
                c.drawText(t, k.centerX(), cy, p);
            }
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                for (int i = 0; i < 12; i++) {
                    if (keyRect[i].contains(e.getX(), e.getY())) { press(i); break; }
                }
            }
            return true;
        }

        private void press(int cell) {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            if (cell == 9) { if (buf.length() > 0) buf.deleteCharAt(buf.length() - 1); }
            else if (cell == 11) { submit(); return; }
            else if (buf.length() < maxLen) {
                buf.append(cell == 10 ? digitAt[9] : digitAt[cell]);
                if (targetLen > 0 && buf.length() >= targetLen) { invalidate(); submit(); return; }
            }
            invalidate();
        }

        private void submit() {
            if (buf.length() == 0) return;
            String s = buf.toString();
            buf.setLength(0);
            invalidate();
            if (onSubmit != null) onSubmit.submit(s);
        }
    }

    // =============================================================================================
    /** 3x3 pattern grid with a deliberately invisible trace. */
    static final class PatternPad extends View {
        interface Done { void done(String seq); }
        Done onDone;
        private final int accent;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final StringBuilder seq = new StringBuilder();
        private final boolean[] used = new boolean[9];
        private final float d;
        private float tx, ty;
        private boolean down;
        private int lastNode = -1;

        PatternPad(Context c, int accent) {
            super(c);
            this.accent = accent;
            d = c.getResources().getDisplayMetrics().density;
            setHapticFeedbackEnabled(true);
        }

        private float nx(int i) { return getWidth() * (0.18f + 0.32f * (i % 3)); }
        private float ny(int i) { return getHeight() * (0.18f + 0.32f * (i / 3)); }

        @Override protected void onDraw(Canvas c) {
            // Identical for every state: dim dots only. A faint halo follows the finger (not the pattern).
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x66FFFFFF);
            for (int i = 0; i < 9; i++) c.drawCircle(nx(i), ny(i), 5.5f * d, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.2f * d);
            p.setColor(0x33FFFFFF);
            for (int i = 0; i < 9; i++) c.drawCircle(nx(i), ny(i), 17 * d, p);
            if (down) {
                p.setStyle(Paint.Style.FILL);
                p.setColor((accent & 0xFFFFFF) | 0x22000000);
                c.drawCircle(tx, ty, 26 * d, p);
            }
        }

        private int nodeAt(float x, float y) {
            float rr = getWidth() * 0.13f;
            for (int i = 0; i < 9; i++) {
                float dx = x - nx(i), dy = y - ny(i);
                if (dx * dx + dy * dy < rr * rr) return i;
            }
            return -1;
        }

        private void add(int n) {
            if (n < 0 || used[n]) return;
            if (lastNode >= 0) {
                int r1 = lastNode / 3, c1 = lastNode % 3, r2 = n / 3, c2 = n % 3;
                if ((r1 + r2) % 2 == 0 && (c1 + c2) % 2 == 0) {
                    int mid = ((r1 + r2) / 2) * 3 + (c1 + c2) / 2;
                    if (!used[mid]) { used[mid] = true; seq.append(mid); }
                }
            }
            used[n] = true;
            seq.append(n);
            lastNode = n;
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            tx = e.getX(); ty = e.getY();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    getParent().requestDisallowInterceptTouchEvent(true);
                    seq.setLength(0);
                    java.util.Arrays.fill(used, false);
                    lastNode = -1;
                    down = true;
                    add(nodeAt(tx, ty));
                    invalidate();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    add(nodeAt(tx, ty));
                    invalidate();
                    return true;
                case MotionEvent.ACTION_UP: {
                    down = false;
                    String s = seq.toString();
                    seq.setLength(0);
                    java.util.Arrays.fill(used, false);
                    lastNode = -1;
                    invalidate();
                    if (!s.isEmpty() && onDone != null) onDone.done(s);
                    return true;
                }
                case MotionEvent.ACTION_CANCEL:
                    down = false;
                    seq.setLength(0);
                    java.util.Arrays.fill(used, false);
                    lastNode = -1;
                    invalidate();
                    return true;
                default:
                    return true;
            }
        }
    }
}

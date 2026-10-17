package com.cylinderradar.launcher;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.app.WallpaperManager;
import android.graphics.Color;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {
    Store store;
    LauncherView view;
    BroadcastReceiver pkgReceiver;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        store = new Store(this);
        view = new LauncherView(this, store);
        setContentView(view);
        setupWindow();

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addAction(Intent.ACTION_PACKAGE_REPLACED);
        f.addDataScheme("package");
        pkgReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent i) { view.reload(); }
        };
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(pkgReceiver, f, Context.RECEIVER_EXPORTED);
        else registerReceiver(pkgReceiver, f);
    }

    /** Transparent, edge-to-edge window so the live wallpaper (Matrix-Radar) shows behind the launcher. */
    @SuppressWarnings("deprecation")
    private void setupWindow() {
        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 29) {
            w.setStatusBarContrastEnforced(false);
            w.setNavigationBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);
        } else {
            view.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
        view.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public WindowInsets onApplyWindowInsets(View v, WindowInsets in) {
                view.setInsets(in.getSystemWindowInsetTop(), in.getSystemWindowInsetBottom());
                return in;
            }
        });
        view.requestApplyInsets();
    }

    @Override protected void onDestroy() {
        try { unregisterReceiver(pkgReceiver); } catch (Exception ignored) { }
        super.onDestroy();
    }

    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (i != null && Intent.ACTION_MAIN.equals(i.getAction())) view.onHome();
    }

    @Override protected void onResume() {
        super.onResume();
        // The vault was already locked before returning to the launcher.
        setSecure(false);
        view.resetClock();
    }

    @Override protected void onPause() {
        // If the private cylinder was visible, keep the task preview protected while it leaves.
        boolean wasVaultVisible = view != null && (view.vaultOpen || view.cur == LauncherView.VAULT || view.prev == LauncherView.VAULT);
        if (view != null) view.lockNow();
        if (wasVaultVisible) setSecure(true);
        super.onPause();
    }

    @Override public void onBackPressed() {
        if (!view.onBack()) { /* we are the home screen: nothing to do */ }
    }

    void setSecure(boolean on) {
        if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }

    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    AlertDialog.Builder builder() {
        return new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
    }

    // ---- launching ---------------------------------------------------------------------------
    void launch(AppItem it, float x, float y) {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_LAUNCHER);
            i.setComponent(it.cn);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            ActivityOptions o = ActivityOptions.makeScaleUpAnimation(view, (int) x, (int) y, 1, 1);
            startActivity(i, o.toBundle());
        } catch (Exception e) {
            toast("Could not open " + it.label);
        }
    }

    void appInfo(AppItem it) {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", it.pkg, null)));
        } catch (Exception e) { toast("Not available"); }
    }

    void uninstall(AppItem it) {
        try {
            startActivity(new Intent(Intent.ACTION_DELETE, Uri.fromParts("package", it.pkg, null)));
        } catch (Exception e) { toast("Cannot uninstall this app"); }
    }

    // ---- menus -------------------------------------------------------------------------------
    void showAppMenu(final AppItem it, final int page) {
        final String[] items;
        if (page == LauncherView.VAULT) {
            items = new String[]{"Unhide (remove from vault)", "Open", "App info"};
        } else {
            boolean pinned = view.isPinned(it);
            items = new String[]{pinned ? "Unpin from Home" : "Pin to Home", "Hide in Vault", "App info", "Uninstall"};
        }
        builder().setTitle(it.label).setItems(items, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dlg, int which) {
                if (page == LauncherView.VAULT) {
                    if (which == 0) view.unhide(it);
                    else if (which == 1) launch(it, view.getWidth() / 2f, view.getHeight() / 2f);
                    else appInfo(it);
                } else {
                    if (which == 0) { if (view.isPinned(it)) view.unpin(it); else view.pin(it); }
                    else if (which == 1) {
                        ensurePassword(new Runnable() { @Override public void run() { view.hide(it); toast(it.label + " hidden in vault"); } });
                    }
                    else if (which == 2) appInfo(it);
                    else uninstall(it);
                }
            }
        }).show();
    }

    private static final String[] DIM_NAMES = {"স্বচ্ছ (ওয়ালপেপার বেশি দেখা যাবে)", "ভারসাম্য", "গাঢ় (সলিড)"};

    void showSettings() {
        final boolean analog = store.analog();
        String[] items = {
                "হোম স্ক্রিনের অ্যাপ নির্বাচন",
                "হিডেন অ্যাপ ম্যানেজ করুন (লক প্রয়োজন)",
                "রাডার / সিলিন্ডারের রং",
                analog ? "ঘড়ি: অ্যানালগ → ডিজিটাল" : "ঘড়ি: ডিজিটাল → অ্যানালগ",
                store.hasPassword() ? "ভল্ট লক পরিবর্তন (PIN / প্যাটার্ন)" : "ভল্ট লক সেট করুন (PIN / প্যাটার্ন)",
                "লঞ্চার ব্যাকগ্রাউন্ড: " + DIM_NAMES[store.dim()],
                "ওয়ালপেপার-স্ক্রিনে ঘড়ি: " + (store.wallClock() ? "চালু" : "বন্ধ"),
                "PIN প্যাড এলোমেলো করা: " + (store.randomKeypad() ? "চালু" : "বন্ধ"),
                "ওয়ালপেপার বদলান",
                "ডিফল্ট লঞ্চার সেটিংস",
                "জেসচার ও ব্যবহারবিধি"
        };
        builder().setTitle("Cylinder Radar • Controls").setItems(items, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dlg, int which) {
                if (which == 0) chooseHomeApps();
                else if (which == 1) manageVaultApps();
                else if (which == 2) chooseColor();
                else if (which == 3) { store.setAnalog(!analog); view.invalidate(); }
                else if (which == 4) changePassword();
                else if (which == 5) { store.setDim((store.dim() + 1) % 3); toast(DIM_NAMES[store.dim()]); view.invalidate(); }
                else if (which == 6) { store.setWallClock(!store.wallClock()); view.invalidate(); }
                else if (which == 7) { store.setRandomKeypad(!store.randomKeypad()); toast(store.randomKeypad() ? "PIN প্যাড এলোমেলো হবে" : "PIN প্যাড সাধারণ থাকবে"); }
                else if (which == 8) chooseWallpaper();
                else if (which == 9) {
                    try { startActivity(new Intent(Settings.ACTION_HOME_SETTINGS)); }
                    catch (Exception e) { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
                } else showHelp();
            }
        }).setNegativeButton("বন্ধ", null).show();
    }

    private void chooseWallpaper() {
        builder().setTitle("ওয়ালপেপার")
                .setItems(new String[]{"লাইভ ওয়ালপেপার (Matrix-Radar ইত্যাদি)", "ছবির ওয়ালপেপার"}, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        try {
                            if (which == 0) startActivity(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER));
                            else startActivity(Intent.createChooser(new Intent(Intent.ACTION_SET_WALLPAPER), "Wallpaper"));
                        } catch (Exception e) { toast("এই ফোনে খোলা যাচ্ছে না"); }
                    }
                }).setNegativeButton("বন্ধ", null).show();
    }

    // ---- app search ----------------------------------------------------------------------------
    void showSearch() {
        final ArrayList<AppItem> pool = new ArrayList<>();
        Set<String> hidden = new HashSet<>(store.list("hidden"));
        for (AppItem it : view.allApps) if (!hidden.contains(it.key)) pool.add(it);   // vault apps never appear here
        final ArrayList<AppItem> shown = new ArrayList<>(pool);
        final EditText et = new EditText(this);
        et.setHint("অ্যাপ খুঁজুন…");
        et.setSingleLine(true);
        et.setImeOptions(EditorInfo.IME_ACTION_GO);
        final ListView lv = new ListView(this);
        final ArrayAdapter<AppItem> ad = new ArrayAdapter<AppItem>(this, 0, shown) {
            @Override public View getView(int pos, View cv, ViewGroup parent) {
                LinearLayout row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                row.setPadding(dp(8), dp(6), dp(8), dp(6));
                ImageView iv = new ImageView(MainActivity.this);
                AppItem it = getItem(pos);
                iv.setImageBitmap(it.icon);
                row.addView(iv, new LinearLayout.LayoutParams(dp(36), dp(36)));
                TextView tv = new TextView(MainActivity.this);
                tv.setText(it.label);
                tv.setTextColor(Color.WHITE);
                tv.setTextSize(16);
                tv.setPadding(dp(14), 0, 0, 0);
                row.addView(tv);
                return row;
            }
        };
        lv.setAdapter(ad);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(8), dp(16), 0);
        box.addView(et);
        box.addView(lv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(320)));
        final AlertDialog dlg = builder().setTitle("সার্চ").setView(box).setNegativeButton("বন্ধ", null).create();
        et.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                String q = s.toString().trim().toLowerCase();
                shown.clear();
                for (AppItem it : pool) if (q.isEmpty() || it.label.toLowerCase().contains(q)) shown.add(it);
                ad.notifyDataSetChanged();
            }
        });
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                dlg.dismiss();
                launch(shown.get(pos), view.getWidth() / 2f, view.getHeight() / 2f);
            }
        });
        et.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int action, KeyEvent ev) {
                if (!shown.isEmpty()) { dlg.dismiss(); launch(shown.get(0), view.getWidth() / 2f, view.getHeight() / 2f); }
                return true;
            }
        });
        dlg.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dlg.show();
    }

    /** Bulk-select favourite apps for the first cylinder. Hidden apps are never offered here. */
    private void chooseHomeApps() {
        if (view.allApps.isEmpty()) {
            toast("অ্যাপের তালিকা প্রস্তুত হচ্ছে—একটু পরে আবার চেষ্টা করুন।");
            return;
        }
        final ArrayList<AppItem> candidates = new ArrayList<>();
        final Set<String> hiddenNow = new HashSet<>(store.list("hidden"));
        final Set<String> seenPackages = new HashSet<>();
        final Set<String> pinnedPackages = new HashSet<>();
        for (AppItem it : view.allApps) if (store.contains("pinned", it.key)) pinnedPackages.add(it.pkg);
        for (AppItem it : view.allApps) {
            if (!hiddenNow.contains(it.key) && seenPackages.add(it.pkg)) candidates.add(it);
        }
        if (candidates.isEmpty()) {
            toast("কোনো সাধারণ অ্যাপ পাওয়া যায়নি।");
            return;
        }
        final String[] labels = new String[candidates.size()];
        final boolean[] checked = new boolean[candidates.size()];
        final Set<String> chosen = new HashSet<>();
        for (int i = 0; i < candidates.size(); i++) {
            AppItem it = candidates.get(i);
            labels[i] = it.label;
            checked[i] = pinnedPackages.contains(it.pkg);
            if (checked[i]) chosen.add(it.key);
        }
        builder().setTitle("হোম স্ক্রিনে কোন অ্যাপ থাকবে?")
                .setMultiChoiceItems(labels, checked, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                        String key = candidates.get(which).key;
                        if (isChecked) chosen.add(key); else chosen.remove(key);
                    }
                })
                .setPositiveButton("সংরক্ষণ", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        ArrayList<String> selected = new ArrayList<>();
                        for (AppItem it : candidates) if (chosen.contains(it.key)) selected.add(it.key);
                        store.saveList("pinned", selected);
                        view.rebuildPages();
                        toast("হোম স্ক্রিনের অ্যাপ সংরক্ষণ হয়েছে");
                    }
                })
                .setNegativeButton("বাতিল", null).show();
    }

    /** Vault inventory is only shown after authentication; the existing PBKDF2 and lockout stay in use. */
    private void manageVaultApps() {
        if (view.vaultOpen) showVaultAppsDialog();
        else authenticate(new Runnable() {
            @Override public void run() { showVaultAppsDialog(); }
        });
    }

    private void showVaultAppsDialog() {
        if (view.allApps.isEmpty()) {
            toast("অ্যাপের তালিকা প্রস্তুত হচ্ছে—একটু পরে আবার চেষ্টা করুন।");
            return;
        }
        final ArrayList<AppItem> candidates = new ArrayList<>();
        final Set<String> seenPackages = new HashSet<>();
        final Set<String> chosenPackages = new HashSet<>();
        final Set<String> savedHidden = new HashSet<>(store.list("hidden"));
        for (AppItem it : view.allApps) {
            if (seenPackages.add(it.pkg)) candidates.add(it);
            if (savedHidden.contains(it.key)) chosenPackages.add(it.pkg);
        }
        final String[] labels = new String[candidates.size()];
        final boolean[] checked = new boolean[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            AppItem it = candidates.get(i);
            labels[i] = it.label;
            checked[i] = chosenPackages.contains(it.pkg);
        }
        builder().setTitle("হিডেন অ্যাপ নির্বাচন • ব্যক্তিগত")
                .setMultiChoiceItems(labels, checked, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                        String pkg = candidates.get(which).pkg;
                        if (isChecked) chosenPackages.add(pkg); else chosenPackages.remove(pkg);
                    }
                })
                .setPositiveButton("সংরক্ষণ", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        ArrayList<String> hidden = new ArrayList<>();
                        for (AppItem it : view.allApps) if (chosenPackages.contains(it.pkg)) hidden.add(it.key);
                        store.saveList("hidden", hidden);
                        ArrayList<String> pinned = new ArrayList<>(store.list("pinned"));
                        for (AppItem it : view.allApps) if (chosenPackages.contains(it.pkg)) pinned.remove(it.key);
                        store.saveList("pinned", pinned);
                        view.rebuildPages();
                        toast("হিডেন অ্যাপের তালিকা সংরক্ষণ হয়েছে");
                    }
                })
                .setNegativeButton("বাতিল", null).show();
    }

    void chooseColor() {
        builder().setTitle("Accent color")
                .setSingleChoiceItems(LauncherView.COLOR_NAMES, store.accent(), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dlg, int which) {
                        store.setAccent(which);
                        view.invalidate();
                        dlg.dismiss();
                    }
                }).setNegativeButton("Close", null).show();
    }

    void showHelp() {
        builder().setTitle("Gestures")
                .setMessage("• ওয়ালপেপার-স্ক্রিনে ওপরে সোয়াইপ করলে লঞ্চার ভেসে উঠবে; HOME সিলিন্ডারে নিচে সোয়াইপ/Back/Home দিলে ওয়ালপেপারে ফেরা যাবে।\n\n"
                        + "• সিলিন্ডারের ওপর ডানে/বামে টেনে ঘোরান; ছেড়ে দিলে ইনর্শিয়া ও স্ন্যাপ কাজ করবে।\n\n"
                        + "• ওপর/নিচে সোয়াইপ করলে HOME ও ALL APPS সিলিন্ডার বদলাবে।\n\n"
                        + "• ঘড়ি বা নিচের পেজ-লেবেলের অংশে বামে সোয়াইপ করলে পরের সিলিন্ডার আসবে।\n\n"
                        + "• ঘড়ি বা নিচের পেজ-লেবেলের অংশে দ্রুত পরপর ডানে দুইবার সোয়াইপ করলে VAULT-এর পাসওয়ার্ড চাইবে।\n\n"
                        + "• অ্যাপ আইকনে লং-প্রেস করলে Pin/Unpin, Hide, App info ও Uninstall পাওয়া যাবে।\n\n"
                        + "• উপরে বাঁদিকের ম্যাগনিফায়ারে ট্যাপ করলে অ্যাপ সার্চ (ভল্টের অ্যাপ সার্চে আসে না)।\n\n"
                        + "• Settings থেকে একসঙ্গে Home apps বাছাই, Vault apps ম্যানেজ এবং Digital/Analog clock বদলানো যায়।\n\n"
                        + "• VAULT অ্যাপ খুললে, Back/Home চাপলে বা লঞ্চার ছাড়লে লক হবে; ভুল পাসওয়ার্ড বারবার দিলে সাময়িক বিরতি থাকবে।\n\n"
                        + "• সিস্টেম/MIUI Hidden Apps তালিকা আলাদাভাবে এখানে বেছে নিতে হবে; এই Vault ফোনের অন্য লঞ্চার থেকে অ্যাপ লুকায় না।")
                .setPositiveButton("OK", null).show();
    }

    // ---- vault lock (PIN / pattern; nothing typed or drawn is ever shown) ---------------------------
    private static final class Mask extends PasswordTransformationMethod {
        @Override public CharSequence getTransformation(final CharSequence src, View v) {
            return new CharSequence() {
                @Override public int length() { return src.length(); }
                @Override public char charAt(int i) { return '\u2022'; }
                @Override public CharSequence subSequence(int s, int e) {
                    StringBuilder b = new StringBuilder();
                    for (int i = s; i < e; i++) b.append('\u2022');
                    return b;
                }
                @Override public String toString() { return subSequence(0, length()).toString(); }
            };
        }
    }

    private View padded(View v) {
        FrameLayout f = new FrameLayout(this);
        f.setPadding(dp(24), dp(8), dp(24), dp(4));
        f.addView(v);
        return f;
    }

    int accentColor() { return LauncherView.COLORS[Math.max(0, Math.min(LauncherView.COLORS.length - 1, store.accent()))]; }

    /** Runs {@code ok} only after the correct vault lock was entered (or created on first use). */
    void authenticate(final Runnable ok) {
        if (!store.hasPassword()) { createPassword("ভল্ট লক তৈরি করুন", ok); return; }
        long wait = store.lockRemaining();
        if (wait > 0) { toast("অনেকবার ভুল। " + (wait / 1000 + 1) + " সেকেন্ড পরে চেষ্টা করুন"); return; }
        String type = store.credType();
        if ("text".equals(type)) { authenticateLegacyText(ok); return; }
        final boolean pattern = "pattern".equals(type);
        LockUi.show(this, pattern, store.pinLength(), store.randomKeypad(), accentColor(), "VAULT",
                pattern ? "প্যাটার্ন আঁকুন" : "PIN দিন", new LockUi.Listener() {
            @Override public void onCancel() { }
            @Override public void onEntry(LockUi.Entry e, String secret) {
                if (store.check(secret)) {
                    store.resetFails();
                    e.dismiss();
                    ok.run();
                } else {
                    store.registerFail();
                    e.reset();
                    long w = store.lockRemaining();
                    if (w > 0) { e.dismiss(); toast("অনেকবার ভুল। " + (w / 1000 + 1) + " সেকেন্ড পরে চেষ্টা করুন"); }
                    else e.error(pattern ? "ভুল প্যাটার্ন" : "ভুল PIN");
                }
            }
        });
    }

    /** Old text password: typed into an always-masked field, then the user is offered a PIN / pattern. */
    private void authenticateLegacyText(final Runnable ok) {
        final EditText et = new EditText(this);
        et.setHint("পুরোনো পাসওয়ার্ড");
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        et.setTransformationMethod(new Mask());
        et.setSingleLine(true);
        final AlertDialog dlg = builder().setTitle("Vault").setView(padded(et))
                .setPositiveButton("Unlock", null).setNegativeButton("Cancel", null).create();
        dlg.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        dlg.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dlg.show();
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (store.check(et.getText().toString())) {
                    store.resetFails();
                    dlg.dismiss();
                    offerUpgrade(ok);
                } else {
                    store.registerFail();
                    et.setText("");
                    long w = store.lockRemaining();
                    if (w > 0) { dlg.dismiss(); toast("অনেকবার ভুল। " + (w / 1000 + 1) + " সেকেন্ড পরে চেষ্টা করুন"); }
                    else et.setError("Wrong password");
                }
            }
        });
    }

    private boolean suppressUpgrade;

    private void offerUpgrade(final Runnable ok) {
        if (suppressUpgrade) { suppressUpgrade = false; ok.run(); return; }
        builder().setTitle("ভল্ট লক আপগ্রেড")
                .setMessage("আরও নিরাপদ PIN বা প্যাটার্ন লক সেট করবেন? (এগুলো স্ক্রিনে কখনও দেখা যায় না)")
                .setPositiveButton("সেট করুন", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { chooseLockType("নতুন ভল্ট লক", ok); }
                })
                .setNegativeButton("পরে", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { ok.run(); }
                }).setCancelable(false).show();
    }

    void ensurePassword(final Runnable then) {
        if (store.hasPassword()) then.run();
        else createPassword("ভল্ট লক তৈরি করুন", then);
    }

    void createPassword(String title, final Runnable then) { chooseLockType(title, then); }

    private void chooseLockType(final String title, final Runnable then) {
        builder().setTitle(title)
                .setItems(new String[]{"PIN (৪–৮ সংখ্যা)", "প্যাটার্ন (কমপক্ষে ৪ ডট)"}, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) { setupLock(which == 1, then); }
                }).setNegativeButton("বাতিল", null).show();
    }

    private void setupLock(final boolean pattern, final Runnable then) {
        final String[] first = {null};
        LockUi.show(this, pattern, 0, false, accentColor(), pattern ? "নতুন প্যাটার্ন" : "নতুন PIN",
                pattern ? "প্যাটার্ন আঁকুন (কমপক্ষে ৪ ডট)" : "৪–৮ সংখ্যার PIN দিয়ে ✓ চাপুন", new LockUi.Listener() {
            @Override public void onCancel() { }
            @Override public void onEntry(LockUi.Entry e, String secret) {
                int n = pattern ? secret.length() - 2 : secret.length();
                if (first[0] == null) {
                    if (n < 4) { e.error(pattern ? "কমপক্ষে ৪টি ডট যোগ করুন" : "কমপক্ষে ৪ সংখ্যা দিন"); e.reset(); return; }
                    first[0] = secret;
                    e.reset();
                    e.setTitle("নিশ্চিত করুন");
                    e.info(pattern ? "একই প্যাটার্ন আবার আঁকুন" : "একই PIN আবার দিন");
                } else if (first[0].equals(secret)) {
                    store.setCredential(pattern ? "pattern" : "pin", secret);
                    e.dismiss();
                    toast("ভল্ট লক সংরক্ষিত হয়েছে");
                    if (then != null) then.run();
                } else {
                    first[0] = null;
                    e.reset();
                    e.setTitle(pattern ? "নতুন প্যাটার্ন" : "নতুন PIN");
                    e.error("মেলেনি, আবার প্রথম থেকে দিন");
                }
            }
        });
    }

    void changePassword() {
        if (!store.hasPassword()) { createPassword("ভল্ট লক তৈরি করুন", null); return; }
        suppressUpgrade = true;
        authenticate(new Runnable() {
            @Override public void run() { suppressUpgrade = false; chooseLockType("নতুন ভল্ট লক", null); }
        });
    }
}
